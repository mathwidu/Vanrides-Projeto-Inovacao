#!/usr/bin/env python3
"""Guarded local Docker backup/restore rehearsal. Never scales, deploys or connects SSH."""
import argparse
import json
import os
from pathlib import Path
import re
import stat
import subprocess
import sys
import tempfile
import time
import uuid

sys.dont_write_bytecode = True
import prepare as P
from plan import Blocked, require
from owned_container import OwnedContainer, Terminated
from docker_client import local_docker

DATABASE = 'vanrides_hml_api'
DB_SERVICE = 'vanrides-hml-api_db'
API_SERVICE = 'vanrides-hml-api_api'
MAX_BACKUP = 64 * 1024 * 1024
PSQL = ['psql', '--no-psqlrc', '--set', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', DATABASE, '-At']
# No bodies, names, tokens or addresses leave PostgreSQL: hashes and counts only.
FINGERPRINT_SQL = r"""
SELECT 'scope|' || count(*) || '|' ||
       count(*) FILTER (WHERE environment <> 'vanrides-team-hml' OR synthetic IS DISTINCT FROM true)
FROM wa.test_cohort;
SELECT 'credentials|' || count(*) FILTER (WHERE environment <> 'vanrides-team-hml')
FROM wa.service_credential;
SELECT format(
  'SELECT %L || ''|'' || count(*) || ''|'' || md5(COALESCE(string_agg(md5(row_to_json(t)::text), '','' ORDER BY md5(row_to_json(t)::text)), '''')) FROM %I.%I t;',
  schemaname || '.' || tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname IN ('wa', 'public') ORDER BY schemaname, tablename
\gexec
"""


def metadata(path, expected):
    path = Path(path)
    raw = P.read(path, 64000)
    require(P.M.sha(expected) and P.sha(raw) == expected, 'HML_PLAN_CHANGED')
    value = P.M.object_json(raw)
    require(value.get('schemaVersion') == 1 and value.get('profile') == 'VANRIDES_TEAM_API_STACK_V1'
            and value.get('stack') == P.STACK and value.get('releaseEligible') is False
            and value.get('host') == P.HOST, 'HML_PLAN_INVALID')
    P.image(value['apiImage'], P.API_REPOSITORY); P.image(value['postgresImage'], 'postgres')
    for name in ('stack.json', 'db-init.sh'):
        require(P.sha(P.read(path.parent / name)) == value['files'][name], 'HML_PLAN_FILE_CHANGED')
    stack = P.obj(path.parent / 'stack.json')
    require(set(stack.get('services', {})) == {'api', 'db'}
            and stack['services']['api']['image'] == value['apiImage']
            and stack['services']['db']['image'] == value['postgresImage']
            and stack['services']['api']['environment']['WA_TEAM_API_ONLY'] == 'true'
            and stack['services']['api']['environment']['WA_ENVIRONMENT'] == P.ENVIRONMENT
            and all(not service.get('ports') for service in stack['services'].values()), 'HML_STACK_INVALID')
    P.validate_stack(stack, value['ingress'], value['allowCidrs'], value['operatorExpiresAt'],
                     value['secretVersion'], value['source']['sha'][:12], path.parent / 'db-init.sh')
    return value


def owned_source(container, plan, run):
    require(isinstance(container, str) and re.fullmatch('[a-f0-9]{64}', container), 'HML_CONTAINER_ID_INVALID')
    replicas = run(['service', 'inspect', '--format', '{{.Spec.Mode.Replicated.Replicas}}', API_SERVICE]).decode().strip()
    require(replicas == '0', 'HML_STOP_API_BEFORE_BACKUP')
    template = '{"labels":{{json .Config.Labels}},"image":{{json .Config.Image}},"running":{{json .State.Running}}}'
    value = P.M.object_json(run(['inspect', '--format', template, container]))
    labels = value['labels']
    require(value['running'] is True and value['image'] == plan['postgresImage']
            and labels.get('com.docker.stack.namespace') == P.STACK
            and labels.get('com.docker.swarm.service.name') == DB_SERVICE
            and labels.get('dev.vanrides.environment') == P.ENVIRONMENT, 'HML_DATABASE_NOT_OWNED')


def fingerprint(container, run):
    raw = run(['exec', '-i', '--user', 'postgres', container, *PSQL], FINGERPRINT_SQL.encode())
    try:
        lines = raw.decode('ascii').splitlines()
        require(len(lines) >= 3 and re.fullmatch(r'scope\|[1-9][0-9]*\|0', lines[0])
                and lines[1] == 'credentials|0', 'HML_SYNTHETIC_SCOPE_REQUIRED')
        rows = []
        for line in lines[2:]:
            name, count, digest = line.split('|')
            require(re.fullmatch(r'(wa|public)\.[a-z][a-z0-9_]*', name)
                    and re.fullmatch('[0-9]+', count) and re.fullmatch('[a-f0-9]{32}', digest), 'HML_FINGERPRINT_INVALID')
            rows.append({'relation': name, 'rows': int(count), 'contentMd5': digest})
        require(len({r['relation'] for r in rows}) == len(rows)
                and {'wa.profile', 'wa.command_receipt', 'wa.audit_event', 'public.flyway_schema_history'} <= {r['relation'] for r in rows}
                and next(r['rows'] for r in rows if r['relation'] == 'wa.command_receipt') >= 1, 'HML_SCENARIO_REQUIRED')
        return {'sha256': P.sha(P.encode(rows)), 'relations': len(rows), 'scope': 'SYNTHETIC_TEAM_ONLY'}
    except (UnicodeError, ValueError, StopIteration):
        raise Blocked('HML_FINGERPRINT_INVALID') from None


def backup(plan_file, expected_plan, container, output, run=None):
    plan = metadata(plan_file, expected_plan)
    run = local_docker() if run is None else run
    owned_source(container, plan, run)
    before = fingerprint(container, run)
    # At most 64MiB, memory only until the owned private file is created.
    dump = run(['exec', '--user', 'postgres', container, 'pg_dump', '-U', 'postgres', '-d', DATABASE,
                '--format=custom', '--no-owner', '--no-privileges'], maximum=MAX_BACKUP)
    require(0 < len(dump) <= MAX_BACKUP and dump.startswith(b'PGDMP'), 'HML_BACKUP_INVALID')
    owned_source(container, plan, run)
    require(fingerprint(container, run) == before, 'HML_DATABASE_CHANGED_DURING_BACKUP')
    output = P.new_directory(output)
    try:
        P.write_new(output / 'database.dump', dump)
        receipt = {'schemaVersion': 1, 'profile': 'VANRIDES_TEAM_API_BACKUP_V1', 'database': DATABASE,
                   'environment': P.ENVIRONMENT, 'postgresImage': plan['postgresImage'], 'apiImage': plan['apiImage'],
                   'source': plan['source'], 'planSha256': expected_plan,
                   'dump': {'sha256': P.sha(dump), 'bytes': len(dump)}, 'fingerprint': before,
                   'status': 'BACKED_UP_RESTORE_NOT_PROVEN', 'releaseEligible': False}
        P.write_new(output / 'backup.json', P.encode(receipt))
    except Exception:
        import shutil
        shutil.rmtree(output)
        raise
    return receipt


def rehearse_restore(dump, expected_fingerprint, postgres_image, run=None, pause=time.sleep):
    run = local_docker() if run is None else run
    P.image(postgres_image, 'postgres')
    require(isinstance(dump, bytes) and 0 < len(dump) <= MAX_BACKUP and dump.startswith(b'PGDMP'), 'HML_BACKUP_INVALID')
    passed = False
    with OwnedContainer(run, 'restore', postgres_image) as owned:
        # No network/port: trust is ONLY inside this disposable isolated container.
        # No source volume is mounted. Data is bounded tmpfs, removed during cleanup.
        container = owned.create(['--network', 'none', '--pull', 'never', '--log-driver', 'none',
                         '--memory', '512m', '--memory-swap', '512m', '--cpus', '1', '--pids-limit', '128',
                         '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,size=256m',
                         '-e', 'POSTGRES_DB=' + DATABASE, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust'])
        run(['start', container])
        for attempt in range(30):
            try:
                run(['exec', '--user', 'postgres', container, 'pg_isready', '-U', 'postgres', '-d', DATABASE])
                break
            except Blocked:
                if attempt == 29:
                    raise
                pause(1)
        run(['exec', '-i', '--user', 'postgres', container, 'pg_restore', '-U', 'postgres', '-d', DATABASE,
             '--exit-on-error', '--no-owner', '--no-privileges'], dump, maximum=1000000)
        actual = fingerprint(container, run)
        require(actual == expected_fingerprint, 'HML_RESTORE_CONTENT_CHANGED')
        passed = True
    require(passed, 'HML_RESTORE_FAILED')
    return actual


def restore(backup_dir, expected_receipt, output, run=None, pause=time.sleep):
    run = local_docker() if run is None else run
    directory = Path(backup_dir)
    require(directory.is_absolute() and directory.resolve() == directory and directory.is_dir()
            and {p.name for p in directory.iterdir()} == {'database.dump', 'backup.json'}, 'HML_BACKUP_DIRECTORY_INVALID')
    raw = P.read(directory / 'backup.json', 64000, private=True)
    require(P.M.sha(expected_receipt) and P.sha(raw) == expected_receipt, 'HML_BACKUP_RECEIPT_CHANGED')
    receipt = P.M.object_json(raw)
    require(P.M.exact(receipt, ('schemaVersion', 'profile', 'database', 'environment', 'postgresImage', 'apiImage',
                              'source', 'planSha256', 'dump', 'fingerprint', 'status', 'releaseEligible'))
            and receipt['schemaVersion'] == 1 and receipt['profile'] == 'VANRIDES_TEAM_API_BACKUP_V1'
            and receipt['database'] == DATABASE and receipt['environment'] == P.ENVIRONMENT
            and receipt['status'] == 'BACKED_UP_RESTORE_NOT_PROVEN' and receipt['releaseEligible'] is False,
            'HML_BACKUP_RECEIPT_INVALID')
    P.image(receipt['postgresImage'], 'postgres')
    P.image(receipt['apiImage'], P.API_REPOSITORY)
    dump = P.read(directory / 'database.dump', MAX_BACKUP, private=True)
    require(receipt['dump'] == {'sha256': P.sha(dump), 'bytes': len(dump)} and dump.startswith(b'PGDMP'), 'HML_BACKUP_CHANGED')
    # Reserve output BEFORE any Docker mutation. No overwrite/no Git checkout output.
    output = P.new_directory(output)
    rehearse_restore(dump, receipt['fingerprint'], receipt['postgresImage'], run, pause)
    result = {'schemaVersion': 1, 'profile': 'VANRIDES_TEAM_API_RESTORE_V1',
              'status': 'RESTORED_DATA_VERIFIED_OWNED_CONTAINER_REMOVED', 'backupReceiptSha256': expected_receipt,
              'dumpSha256': receipt['dump']['sha256'], 'postgresImage': receipt['postgresImage'],
              'fingerprint': receipt['fingerprint'], 'network': 'none', 'apiFunctionalTested': False,
              'releaseEligible': False}
    P.write_new(output / 'restore.json', P.encode(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-local-docker', action='store_true', help='Explicitly operate this Docker endpoint; no SSH')
    subs = parser.add_subparsers(dest='action', required=True)
    p = subs.add_parser('backup')
    for arg in ('plan', 'expected-plan-sha256', 'container-id', 'output'):
        p.add_argument('--' + arg, required=True)
    p = subs.add_parser('restore')
    for arg in ('backup', 'expected-backup-receipt-sha256', 'output'):
        p.add_argument('--' + arg, required=True)
    args = parser.parse_args()
    try:
        require(args.execute_local_docker, 'HML_EXPLICIT_LOCAL_EXECUTION_REQUIRED')
        run = local_docker()
        if args.action == 'backup':
            backup(args.plan, args.expected_plan_sha256, args.container_id, args.output, run)
        else:
            restore(args.backup, args.expected_backup_receipt_sha256, args.output, run)
        print('HML_BACKUP_OPERATION_FINISHED_NO_RELEASE_APPROVAL')
        return 0
    except (Terminated, KeyboardInterrupt):
        print('HML_BACKUP_OPERATION_CANCELLED', file=sys.stderr)
        return 130
    except (Blocked, OSError, ValueError, TypeError, KeyError, subprocess.SubprocessError):
        print('HML_BACKUP_OPERATION_BLOCKED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
