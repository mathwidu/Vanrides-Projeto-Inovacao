#!/usr/bin/env python3
"""Opt-in local PostgreSQL restore smoke. Never creates a Swarm or published port."""
import argparse
import json
import os
import subprocess
import sys

sys.dont_write_bytecode = True
import prepare as P
import backup_restore as B
from owned_container import OwnedContainer, Terminated

FIXTURE_SQL = b"""
CREATE SCHEMA wa;
CREATE TABLE wa.test_cohort(id uuid PRIMARY KEY, environment text, synthetic boolean);
CREATE TABLE wa.service_credential(id uuid PRIMARY KEY, environment text);
CREATE TABLE wa.profile(identity_id uuid PRIMARY KEY, role text, status text);
CREATE TABLE wa.command_receipt(id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY, outcome text);
CREATE TABLE wa.audit_event(id integer GENERATED ALWAYS AS IDENTITY PRIMARY KEY, action text);
CREATE TABLE public.flyway_schema_history(installed_rank integer PRIMARY KEY, version text, checksum integer, success boolean);
INSERT INTO wa.test_cohort VALUES ('00000000-0000-4000-8000-000000000001','vanrides-team-hml',true);
INSERT INTO wa.service_credential VALUES ('00000000-0000-4000-8000-000000000002','vanrides-team-hml');
INSERT INTO wa.profile VALUES ('00000000-0000-4000-8000-000000000003','UNREGISTERED','ONBOARDING');
INSERT INTO wa.command_receipt(outcome) VALUES ('SYNTHETIC_RESTORE_FIXTURE');
INSERT INTO wa.audit_event(action) VALUES ('LOCAL_SYNTHETIC_FIXTURE');
INSERT INTO public.flyway_schema_history VALUES(1,'LOCAL_FIXTURE_ONLY',1,true);
"""


def smoke(postgres_image, output):
    P.image(postgres_image, 'postgres')
    run = B.local_docker()
    platform = run(['image', 'inspect', '--format', '{{.Os}}/{{.Architecture}}', postgres_image]).decode().strip()
    P.require(platform in ('linux/arm64', 'linux/amd64'), 'HML_PLATFORM_INVALID')
    output = P.new_directory(output)
    with OwnedContainer(run, 'restore', postgres_image) as source:
        container = source.create(['--network', 'none', '--pull', 'never', '--log-driver', 'none',
            '--memory', '512m', '--memory-swap', '512m', '--cpus', '1', '--pids-limit', '128',
            '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,size=256m',
            '-e', 'POSTGRES_DB=' + B.DATABASE, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust'])
        run(['start', container])
        for attempt in range(30):
            try:
                run(['exec', '--user', 'postgres', container, 'pg_isready', '-U', 'postgres', '-d', B.DATABASE])
                break
            except P.Blocked:
                if attempt == 29:
                    raise
                B.time.sleep(1)
        run(['exec', '-i', '--user', 'postgres', container, *B.PSQL], FIXTURE_SQL)
        fingerprint = B.fingerprint(container, run)
        dump = run(['exec', '--user', 'postgres', container, 'pg_dump', '-U', 'postgres', '-d', B.DATABASE,
                         '--format=custom', '--no-owner', '--no-privileges'], maximum=B.MAX_BACKUP)
        run(['stop', '--time', '5', container])
        B.rehearse_restore(dump, fingerprint, postgres_image, run)
    result = {'schemaVersion': 1, 'profile': 'VANRIDES_TEAM_API_LOCAL_RESTORE_SMOKE_V1',
              'status': 'RESTORE_DATA_VERIFIED_OWNED_CONTAINERS_REMOVED',
              'source': 'LOCAL_SYNTHETIC_SQL_FIXTURE_NOT_APPLICATION_SCHEMA',
              'postgresImage': postgres_image, 'platform': platform, 'network': 'none',
              'dump': {'sha256': P.sha(dump), 'bytes': len(dump)}, 'fingerprint': fingerprint,
              'backupSourceGuardTested': False, 'apiFunctionalTested': False, 'releaseEligible': False}
    P.write_new(output / 'local-restore.json', P.encode(result))
    return result


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-local-docker', action='store_true', required=True)
    parser.add_argument('--postgres-image', required=True); parser.add_argument('--output', required=True)
    args = parser.parse_args()
    try:
        smoke(args.postgres_image, args.output)
        print('HML_LOCAL_RESTORE_SMOKE_PASS_NO_RELEASE_APPROVAL')
        return 0
    except (Terminated, KeyboardInterrupt):
        print('HML_LOCAL_RESTORE_SMOKE_CANCELLED', file=sys.stderr)
        return 130
    except (P.Blocked, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print('HML_LOCAL_RESTORE_SMOKE_BLOCKED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
