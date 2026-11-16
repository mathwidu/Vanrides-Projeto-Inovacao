#!/usr/bin/env python3
"""Ensaio privado de uma migração aditiva: nunca publica nem altera serviços live."""
import argparse
from contextlib import contextmanager
import fcntl
import hashlib
import io
import json
import os
from pathlib import Path
import pwd
import re
import resource
import secrets
import signal
import socket
import stat
import subprocess
import sys
import time
import uuid
from zipfile import ZipFile

sys.dont_write_bytecode = True

API_REPOSITORY = 'ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api'
SOURCE = 'https://github.com/mathwidu/Vanrides-Projeto-Inovacao'
ENVIRONMENT = 'vanrides-mvp-hml'
STACK = 'vanrides-hml-api'
API, DB = STACK + '_api', STACK + '_db'
DATABASE = 'vanrides_hml_api'
DOCKER = ('docker', '--host', 'unix:///var/run/docker.sock')
MAX_DUMP, MAX_JAR = 64 * 1024 * 1024, 128 * 1024 * 1024
MIGRATIONS = 'BOOT-INF/classes/db/migration/'
OWNER = 'dev.vanrides.migration-rehearsal'
FINGERPRINT_SQL = r"""
BEGIN ISOLATION LEVEL REPEATABLE READ READ ONLY;
SET LOCAL TIME ZONE 'UTC';
SET LOCAL DateStyle = 'ISO, YMD';
SET LOCAL extra_float_digits = 3;
SET LOCAL client_encoding = 'UTF8';
SELECT 'scope|' || count(*) || '|' || count(*) FILTER (WHERE environment <> 'vanrides-mvp-hml') || '|' || count(*) FILTER(WHERE NOT synthetic) || '|' || count(*) FILTER(WHERE synthetic) FROM wa.test_cohort;
SELECT 'credentials|' || count(*) || '|' || count(*) FILTER (WHERE environment <> 'vanrides-mvp-hml') FROM wa.service_credential;
SELECT 'unsupported|' || ((SELECT count(*) FROM pg_namespace WHERE nspname NOT IN ('wa','public','information_schema') AND nspname !~ '^pg_') + (SELECT count(*) FROM pg_largeobject_metadata));
SELECT format('SELECT %L || ''|'' || count(*) || ''|'' || md5(COALESCE(string_agg(md5(row_to_json(t)::text), '','' ORDER BY md5(row_to_json(t)::text)), '''')) FROM %I.%I t;',
  schemaname || '.' || tablename, schemaname, tablename)
FROM pg_tables WHERE schemaname IN ('wa','public') ORDER BY schemaname,tablename
\gexec
SELECT format('SELECT %L || ''|1|'' || md5(last_value::text || ''|'' || is_called::text) FROM %I.%I;',
  schemaname || '.' || sequencename, schemaname, sequencename)
FROM pg_sequences WHERE schemaname IN ('wa','public') ORDER BY schemaname,sequencename
\gexec
COMMIT;
"""
FLYWAY_SQL = "SELECT COALESCE(max(version::integer),0)||'|'||count(*) FILTER(WHERE NOT success) FROM public.flyway_schema_history;"


class Blocked(Exception):
    pass


def require(condition, reason):
    if not condition: raise Blocked(reason)


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encode(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n').encode()


def object_json(raw):
    def unique(pairs):
        result = {}
        for key, value in pairs:
            require(key not in result, 'MIG_DUPLICATE_JSON_KEY'); result[key] = value
        return result
    value = json.loads(raw, object_pairs_hook=unique)
    require(isinstance(value, dict), 'MIG_MANIFEST_INVALID'); return value


def image(reference, repository):
    require(isinstance(reference, str) and re.fullmatch(re.escape(repository) + r'@sha256:[a-f0-9]{64}', reference), 'MIG_IMMUTABLE_IMAGE_REQUIRED')


def read(path, maximum):
    path = Path(path)
    require(path.is_absolute() and path.parent.resolve() == path.parent, 'MIG_INPUT_PATH_INVALID')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    with os.fdopen(fd, 'rb') as stream:
        before = os.fstat(stream.fileno())
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= maximum, 'MIG_INPUT_INVALID')
        raw = stream.read(maximum + 1); after = os.fstat(stream.fileno())
        require(len(raw) == before.st_size and all(getattr(before, field) == getattr(after, field)
                for field in ('st_dev','st_ino','st_uid','st_mode','st_size','st_mtime_ns','st_ctime_ns')), 'MIG_INPUT_CHANGED')
        return raw


def write_new(path, raw):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())


def new_directory(path):
    path = Path(path)
    require(path.is_absolute() and path.parent.resolve() == path.parent and path.parent.is_dir()
            and not path.exists() and not path.is_symlink(), 'MIG_OUTPUT_INVALID')
    require(not any((parent / '.git').exists() for parent in (path.parent, *path.parent.parents)), 'MIG_OUTPUT_INSIDE_GIT')
    path.mkdir(mode=0o700); return path


def manifest(path, expected):
    raw = read(Path(path), 64000)
    require(re.fullmatch('[a-f0-9]{64}', expected or '') and sha(raw) == expected, 'MIG_MANIFEST_CHANGED')
    value = object_json(raw)
    require(set(value) == {'schemaVersion', 'environment', 'previous', 'candidate', 'migration'}
            and type(value['schemaVersion']) is int and value['schemaVersion'] == 1 and value['environment'] == ENVIRONMENT, 'MIG_MANIFEST_INVALID')
    old, candidate, migration = value['previous'], value['candidate'], value['migration']
    require(set(old) == {'apiImage', 'postgresImage', 'revision', 'tree', 'jarSha256'}
            and set(candidate) == {'jar', 'jarSha256', 'revision', 'tree'} and set(migration) == {'path', 'sha256'}, 'MIG_MANIFEST_INVALID')
    image(old['apiImage'], API_REPOSITORY)
    image(old['postgresImage'], 'postgres')
    require(all(re.fullmatch('[a-f0-9]{40}', x) for x in (old['revision'], old['tree'], candidate['revision'], candidate['tree']))
            and all(re.fullmatch('[a-f0-9]{64}', x) for x in (old['jarSha256'], candidate['jarSha256'], migration['sha256']))
            and re.fullmatch(re.escape(MIGRATIONS) + r'V[1-9][0-9]*__[A-Za-z0-9_]+\.sql', migration['path']), 'MIG_ANCHOR_INVALID')
    jar = read(Path(candidate['jar']), MAX_JAR)
    require(sha(jar) == candidate['jarSha256'], 'MIG_CANDIDATE_CHANGED')
    return value, jar


def inventory(jar):
    result = {}
    with ZipFile(io.BytesIO(jar)) as archive:
        entries = archive.infolist()
        require(len(entries) <= 30000 and len({x.filename for x in entries}) == len(entries)
                and sum(x.file_size for x in entries) <= 512 * 1024 * 1024, 'MIG_JAR_INVALID')
        for entry in entries:
            name = entry.filename
            require(not name.startswith('/') and '..' not in name.split('/') and entry.file_size <= MAX_JAR, 'MIG_JAR_INVALID')
            config = name.startswith('BOOT-INF/classes/') and name.endswith(('.properties', '.yaml', '.yml', '.xml'))
            if not entry.is_dir() and (name.startswith((MIGRATIONS, 'BOOT-INF/lib/')) or config):
                result[name] = sha(archive.read(entry))
    require(any(x.startswith(MIGRATIONS) for x in result)
            and any(x.startswith('BOOT-INF/lib/') for x in result)
            and 'BOOT-INF/classes/application.properties' in result, 'MIG_INVENTORY_INCOMPLETE')
    return result


def migration_versions(old, new, reviewed):
    require(reviewed['path'] not in old and new == {**old, reviewed['path']: reviewed['sha256']}, 'MIG_UNREVIEWED_CHANGE')
    matches = [re.fullmatch(re.escape(MIGRATIONS) + r'V([0-9]+)__[A-Za-z0-9_]+\.sql', name) for name in old if name.startswith(MIGRATIONS)]
    require(matches and all(matches), 'MIG_VERSION_INVALID')
    versions = [int(match.group(1)) for match in matches]
    after = int(re.search(r'/V([0-9]+)__', reviewed['path']).group(1))
    require(len(set(versions)) == len(versions) and after == max(versions) + 1, 'MIG_VERSION_INVALID')
    return max(versions), after


def host_identity():
    require(pwd.getpwuid(os.getuid()).pw_name == 'mathwidu'
            and socket.gethostname().split('.')[0] == 'srv1397065', 'MIG_VPS_IDENTITY_REQUIRED')


@contextmanager
def deploy_lock():
    directory = Path(pwd.getpwuid(os.getuid()).pw_dir) / '.local/share/vanrides/cd'
    require(directory.resolve() == directory and directory.is_dir(), 'MIG_LOCK_DIRECTORY_INVALID')
    fd = os.open(directory / 'deploy.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW, 0o600)
    try:
        info = os.fstat(fd)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid()
                and stat.S_IMODE(info.st_mode) == 0o600, 'MIG_LOCK_NOT_PRIVATE')
        fcntl.flock(fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield
    finally:
        os.close(fd)


class Docker:
    """Pinned Unix daemon, empty CLI config, bounded private output, no inherited secrets."""
    def __init__(self, output, execute=subprocess.run):
        self.output, self.execute, self.number = output, execute, 0
        (output / 'docker-config').mkdir(mode=0o700)
        self.env = {'PATH': '/usr/local/bin:/usr/bin:/bin', 'HOME': pwd.getpwuid(os.getuid()).pw_dir,
                    'LANG': 'C.UTF-8', 'DOCKER_CONFIG': str(output / 'docker-config')}

    def __call__(self, args, data=None, maximum=1000000, sink=None, timeout=120):
        self.number += 1
        target = sink or self.output / f'{self.number:03}.stdout.private'
        error = self.output / f'{self.number:03}.stderr.private'
        def bound_files():
            resource.setrlimit(resource.RLIMIT_FSIZE, (maximum, maximum))
        outfd = os.open(target, os.O_RDWR | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        try:
            errfd = os.open(error, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        except BaseException:
            os.close(outfd); raise
        with os.fdopen(outfd, 'w+b') as stdout, os.fdopen(errfd, 'wb') as stderr:
            result = self.execute([*DOCKER, *args], input=data, stdout=stdout, stderr=stderr,
                                  timeout=timeout, env=self.env, preexec_fn=bound_files)
            stdout.flush()
            require(result.returncode == 0 and os.fstat(stdout.fileno()).st_size <= maximum, 'MIG_DOCKER_OPERATION_FAILED')
            stdout.seek(0)
            return stdout.read(maximum + 1)


def selected_env(name):
    # Only a known nonsecret field is projected, never Env, Args or Command wholesale.
    prefix = name + '='; size = str(len(prefix))
    return ('{{range .Spec.TaskTemplate.ContainerSpec.Env}}{{if and (ge (len .) ' + size
            + ') (eq (slice . 0 ' + size + ') ' + json.dumps(prefix) + ')}}{{.}}{{end}}{{end}}')


def projected_labels(keys=('com.docker.swarm.service.name', 'com.docker.stack.namespace', 'dev.vanrides.environment')):
    return '{' + ','.join(json.dumps(key) + ':{{json (index .Config.Labels ' + json.dumps(key) + ')}}' for key in keys) + '}'


def live_state(run, old):
    state = {}
    for service, image in ((API, old['apiImage']), (DB, old['postgresImage'])):
        template = '{"image":{{json .Spec.TaskTemplate.ContainerSpec.Image}},"version":{{.Version.Index}},"replicas":{{.Spec.Mode.Replicated.Replicas}},"namespace":{{json (index .Spec.Labels "com.docker.stack.namespace")}},"environment":{{json (index .Spec.Labels "dev.vanrides.environment")}}}'
        value = json.loads(run(['service', 'inspect', '--format', template, service]))
        require(value['image'] == image and value['replicas'] == 1 and value['namespace'] == STACK
                and value['environment'] == ENVIRONMENT and type(value['version']) is int, 'MIG_LIVE_SERVICE_CHANGED')
        if service == API:
            for name, expected in (('WA_ENVIRONMENT', ENVIRONMENT), ('VANRIDES_JAR_SHA256', old['jarSha256'])):
                require(run(['service', 'inspect', '--format', selected_env(name), API]).decode().strip() == name + '=' + expected,
                        'MIG_LIVE_API_CHANGED')
        ids = run(['ps', '--no-trunc', '--filter', 'label=com.docker.swarm.service.name=' + service, '--format', '{{.ID}}']).decode().splitlines()
        require(len(ids) == 1 and re.fullmatch('[a-f0-9]{64}', ids[0]), 'MIG_LIVE_TASK_COUNT_INVALID')
        task = json.loads(run(['inspect', '--format', '{"image":{{json .Config.Image}},"running":{{.State.Running}},"labels":' + projected_labels() + '}', ids[0]]))
        require(task['image'] == image and task['running'] is True and task['labels'].get('com.docker.swarm.service.name') == service
                and task['labels'].get('com.docker.stack.namespace') == STACK
                and task['labels'].get('dev.vanrides.environment') == ENVIRONMENT, 'MIG_LIVE_TASK_NOT_OWNED')
        if service == API:
            require(run(['exec', ids[0], 'sha256sum', '/app/app.jar']).decode().strip()
                    == old['jarSha256'] + '  /app/app.jar', 'MIG_RUNNING_JAR_CHANGED')
        state[service] = {**value, 'container': ids[0]}
    labels = json.loads(run(['image', 'inspect', '--format', projected_labels(('org.opencontainers.image.source', 'org.opencontainers.image.revision', 'dev.vanrides.source-tree', 'dev.vanrides.jar-sha256')), old['apiImage']]))
    require(labels.get('org.opencontainers.image.source') == SOURCE
            and labels.get('org.opencontainers.image.revision') == old['revision']
            and labels.get('dev.vanrides.source-tree') == old['tree']
            and labels.get('dev.vanrides.jar-sha256') == old['jarSha256'], 'MIG_PREVIOUS_IMAGE_CHANGED')
    return state


def sql(run, container, statement):
    return run(['exec', '-i', '--user', 'postgres', container, 'psql', '--no-psqlrc', '-w', '-qAt',
                '--set', 'ON_ERROR_STOP=1', '-U', 'postgres', '-d', DATABASE], statement.encode()).decode().strip()


def fingerprint(run, container):
    lines = sql(run, container, FINGERPRINT_SQL).splitlines()
    cohort = re.fullmatch(r'scope\|([1-9][0-9]*)\|0\|([0-9]+)\|([0-9]+)', lines[0]) if lines else None
    credentials = re.fullmatch(r'credentials\|([0-9]+)\|0', lines[1]) if len(lines)>1 else None
    require(len(lines) >= 4 and cohort and credentials and lines[2] == 'unsupported|0'
            and int(cohort.group(1)) == int(cohort.group(2)) + int(cohort.group(3)), 'MIG_DATABASE_SCOPE_INVALID')
    names = []
    for row in lines[3:]:
        match = re.fullmatch(r'((?:wa|public)\.[a-z][a-z0-9_]*)\|[0-9]+\|[a-f0-9]{32}', row)
        require(match, 'MIG_FINGERPRINT_INVALID'); names.append(match.group(1))
    require(len(set(names)) == len(names) and {'wa.profile', 'public.flyway_schema_history'} <= set(names), 'MIG_FINGERPRINT_INVALID')
    return {'sha256': sha(('\n'.join(lines) + '\n').encode()), 'relations': len(names),
            'cohorts': {'real': int(cohort.group(2)), 'synthetic': int(cohort.group(3))},
            'serviceCredentials': int(credentials.group(1))}


class Owned:
    """Reconcile a lost create response by nonce; remove only after all ownership fields match."""
    def __init__(self, run):
        self.run, self.nonce, self.expected = run, uuid.uuid4().hex, {}

    def create(self, kind, image, options, command=()):
        name = 'vanrides-migration-' + self.nonce + '-' + kind
        require(not self.run(['ps', '-a', '--filter', 'name=^/' + name + '$', '--format', '{{.ID}}']).strip(), 'MIG_NAME_COLLISION')
        self.expected[kind] = (name, image)  # Before create: a lost response still has a recoverable owner.
        cid = self.run(['create', '--name', name, '--label', OWNER + '=' + self.nonce,
                        '--label', 'dev.vanrides.operation=' + kind, '--label', 'dev.vanrides.environment=' + ENVIRONMENT,
                        '--pull', 'never', *options, image, *command]).decode().strip()
        require(re.fullmatch('[a-f0-9]{64}', cid), 'MIG_CONTAINER_ID_INVALID')
        self.check(cid); return cid

    def check(self, cid):
        value = json.loads(self.run(['inspect', '--format', '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"labels":' + projected_labels((OWNER, 'dev.vanrides.operation', 'dev.vanrides.environment')) + '}', cid]))
        labels = value['labels']; kind = labels.get('dev.vanrides.operation')
        require(kind in self.expected and value['id'] == cid and value['name'] == '/' + self.expected[kind][0]
                and value['image'] == self.expected[kind][1] and labels.get(OWNER) == self.nonce
                and labels.get('dev.vanrides.environment') == ENVIRONMENT, 'MIG_CONTAINER_NOT_OWNED')

    def remove(self, cid):
        self.check(cid); self.run(['rm', '-f', '-v', cid])

    def cleanup(self):
        ids = self.run(['ps', '-a', '--no-trunc', '--filter', 'label=' + OWNER + '=' + self.nonce, '--format', '{{.ID}}']).decode().splitlines()
        require(len(ids) <= 5 and all(re.fullmatch('[a-f0-9]{64}', x) for x in ids), 'MIG_OWNER_QUERY_INVALID')
        for cid in ids: self.check(cid)
        for cid in ids: self.run(['rm', '-f', '-v', cid])
        require(not self.run(['ps', '-a', '--no-trunc', '--filter', 'label=' + OWNER + '=' + self.nonce, '--format', '{{.ID}}']).strip(), 'MIG_OWNED_CONTAINER_REMAINS')


def synthetic_env():
    env = {'SPRING_PROFILES_ACTIVE': 'homolog', 'SPRING_DATASOURCE_URL': 'jdbc:postgresql://127.0.0.1:5432/' + DATABASE,
           'SPRING_DATASOURCE_USERNAME': 'postgres', 'SPRING_DATASOURCE_PASSWORD': 'synthetic-only',
           'SPRING_JPA_HIBERNATE_DDL_AUTO': 'validate', 'SPRING_JPA_SHOW_SQL': 'false',
           'WA_MVP_ONLINE': 'true', 'WA_TEAM_API_ONLY': 'false', 'WA_MVP_ENABLED': 'true',
           'WA_NEW_STARTS_ENABLED': 'true', 'WA_APPROACH_ENABLED': 'false', 'WA_AUTO_FINISH_ENABLED': 'false',
           'WA_MVP_REHEARSAL_ROUTINES': 'false', 'WA_TEST_COHORT_ONLY': 'true', 'WA_ENVIRONMENT': ENVIRONMENT,
           'WA_PUBLIC_BASE_URL': 'https://vanrides-inovacao-api.matheusduarte.dev.br',
           'WA_START_LINK_BASE_URL': 'https://vanrides-inovacao-api.matheusduarte.dev.br',
           'WA_OPERATOR_EXPIRES_AT': '2099-01-01T00:00:00Z', 'VANRIDES_EMAIL_PROVIDER': 'disabled',
           'VANRIDES_AUTH_BASIC_ENABLED': 'false', 'VANRIDES_WA_TEAM_WEB_ENABLED': 'false',
           'VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL': '', 'VANRIDES_RATE_LIMIT_ENABLED': 'true',
           'VANRIDES_ROUTING_PROVIDER': 'static', 'VANRIDES_PUSH_PROVIDER': 'none', 'VANRIDES_MAPS_PLATFORM_ENABLED': 'false'}
    for name in ('WA_SECRET_HASH_KEY', 'WA_RESPONSE_ENCRYPTION_KEY', 'WA_OPERATOR_CREDENTIAL', 'VANRIDES_JWT_SECRET'):
        env[name] = secrets.token_urlsafe(32)
    return ''.join(name + '=' + value + '\n' for name, value in sorted(env.items())).encode()


def api_probe(owned, image, database, jar, env_file, expected_version, name, pause):
    jar_hash = sha(read(jar, MAX_JAR))
    options = ['--network', 'container:' + database, '--log-driver', 'local', '--log-opt', 'max-size=1m', '--log-opt', 'max-file=1',
               '--memory', '640m', '--memory-swap', '640m', '--cpus', '1', '--pids-limit', '192', '--read-only',
               '--tmpfs', '/tmp:rw,nosuid,nodev,size=32m,mode=1777', '--user', f'{os.getuid()}:{os.getgid()}',
               '--env-file', str(env_file), '-e', 'VANRIDES_JAR_SHA256=' + jar_hash, '--mount', 'type=bind,source=' + str(jar) + ',target=/app/app.jar,readonly', '--entrypoint', 'java']
    cid = owned.create(name, image, options, ['-Xms24m', '-Xmx384m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', '/app/app.jar'])
    try:
        owned.run(['start', cid])
        for attempt in range(75):
            require(owned.run(['inspect', '--format', '{{.State.Running}}', cid]).strip() == b'true', 'MIG_API_EXITED')
            try:
                raw = owned.run(['exec', database, '/bin/busybox', 'wget', '-T', '3', '-q', '-O', '-', 'http://127.0.0.1:8080/actuator/health'])
                if json.loads(raw).get('status') == 'UP': break
            except (Blocked, ValueError): pass
            require(attempt < 74, 'MIG_API_NOT_READY'); pause(2)
        require(sql(owned.run, database, FLYWAY_SQL) == str(expected_version) + '|0', 'MIG_SCHEMA_VERSION_CHANGED')
        return {'scenario': name, 'health': 'UP', 'schemaVersion': expected_version, 'runtimeImage': image, 'jarSha256': jar_hash}
    finally:
        owned.run(['logs', cid], maximum=2 * 1024 * 1024)
        owned.remove(cid)


def rehearse(value, candidate, expected, output, run, pause=time.sleep):
    receipt = {'schemaVersion': 1, 'environment': ENVIRONMENT, 'manifestSha256': expected,
               'status': 'BLOCKED', 'releaseEligible': False, 'candidateRevision': value['candidate']['revision'], 'candidateTree': value['candidate']['tree'], 'stage': 'LIVE_SNAPSHOT', 'scope': 'POSTGRES_MIGRATION_AND_IMAGE_RETURN_ONLY'}
    owned = Owned(run)
    try:
        before = live_state(run, value['previous']); source = before[DB]['container']
        write_new(output / 'live-before.json', encode(before))
        receipt['liveStateSha256'] = sha(encode(before)); receipt['stage'] = 'JAR_INVENTORY'
        extract = owned.create('extract', value['previous']['apiImage'], ['--network', 'none', '--entrypoint', 'java'])
        previous_path = output / 'previous.jar'
        run(['cp', extract + ':/app/app.jar', str(previous_path)], maximum=MAX_JAR)
        previous = read(previous_path, MAX_JAR); os.chmod(previous_path, 0o600)
        require(sha(previous) == value['previous']['jarSha256'], 'MIG_PREVIOUS_JAR_CHANGED')
        old_inventory, new_inventory = inventory(previous), inventory(candidate)
        prior_version, next_version = migration_versions(old_inventory, new_inventory, value['migration'])
        receipt['inventorySha256'] = sha(encode(new_inventory))
        require(sql(run, source, FLYWAY_SQL) == str(prior_version) + '|0', 'MIG_SOURCE_SCHEMA_CHANGED')
        fingerprint_before = fingerprint(run, source); receipt['stage'] = 'BACKUP'
        dump = run(['exec', '--user', 'postgres', source, 'pg_dump', '-w', '-U', 'postgres', '-d', DATABASE,
                    '--format=custom', '--no-owner', '--no-privileges'], maximum=MAX_DUMP, sink=output / 'database.dump')
        require(0 < len(dump) <= MAX_DUMP and dump.startswith(b'PGDMP'), 'MIG_BACKUP_INVALID')
        receipt['backup'] = {'sha256': sha(dump), 'bytes': len(dump), 'fingerprint': fingerprint_before}
        require(live_state(run, value['previous']) == before and fingerprint(run, source) == fingerprint_before, 'MIG_SOURCE_CHANGED_DURING_BACKUP')
        receipt['stage'] = 'RESTORE'
        pg = owned.create('postgres', value['previous']['postgresImage'], ['--network', 'none', '--log-driver', 'none',
            '--memory', '512m', '--memory-swap', '512m', '--cpus', '1', '--pids-limit', '128',
            '--tmpfs', '/var/lib/postgresql/data:rw,nosuid,nodev,size=512m', '-e', 'POSTGRES_DB=' + DATABASE, '-e', 'POSTGRES_HOST_AUTH_METHOD=trust'])
        run(['start', pg])
        for attempt in range(40):
            try:
                ready = run(['exec', '--user', 'postgres', pg, 'psql', '--no-psqlrc', '-w', '-qAt',
                    '--host', '127.0.0.1', '--port', '5432', '-U', 'postgres', '-d', DATABASE,
                    '--set', 'ON_ERROR_STOP=1', '-c', 'SELECT 1'])
                require(ready.strip() == b'1', 'MIG_POSTGRES_NOT_READY'); break
            except Blocked:
                require(attempt < 39, 'MIG_POSTGRES_NOT_READY'); pause(1)
        run(['exec', '-i', '--user', 'postgres', pg, 'pg_restore', '-w', '-U', 'postgres', '-d', DATABASE,
             '--exit-on-error', '--single-transaction', '--no-owner', '--no-privileges'], dump)
        require(fingerprint(run, pg) == fingerprint_before, 'MIG_RESTORE_CONTENT_CHANGED')
        receipt['restoreContentVerified'] = True
        write_new(output / 'candidate.jar', candidate); write_new(output / 'api.synthetic.env', synthetic_env())
        receipt['scenarios'] = []
        for name, jar, version in (('previous-on-previous', previous_path, prior_version),
                                   ('candidate-on-new', output / 'candidate.jar', next_version), ('previous-on-new', previous_path, next_version)):
            receipt['stage'] = name
            receipt['scenarios'].append(api_probe(owned, value['previous']['apiImage'], pg, jar, output / 'api.synthetic.env', version, name, pause))
        require(live_state(run, value['previous']) == before, 'MIG_LIVE_STATE_CHANGED')
        receipt['status'] = 'RESTORE_AND_MIGRATION_RETURN_VERIFIED'; receipt['stage'] = 'FINISHED'
    except BaseException as error:
        receipt['reason'] = str(error) if isinstance(error, Blocked) else type(error).__name__
        raise
    finally:
        blocked = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
        try:
            try:
                owned.cleanup(); receipt['ownedCleanup'] = 'VERIFIED'
            except BaseException:
                receipt['status'] = 'BLOCKED'; receipt['ownedCleanup'] = 'BLOCKED'
                raise
            finally:
                write_new(output / 'receipt.json', encode(receipt))
        finally:
            signal.pthread_sigmask(signal.SIG_SETMASK, blocked)
    return receipt


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--execute-on-vps', action='store_true')
    parser.add_argument('--manifest', required=True); parser.add_argument('--expected-manifest-sha256', required=True)
    parser.add_argument('--output', required=True)
    args = parser.parse_args()
    output = None
    try:
        require(args.execute_on_vps, 'MIG_EXPLICIT_EXECUTION_REQUIRED'); host_identity()
        value, candidate = manifest(args.manifest, args.expected_manifest_sha256)
        with deploy_lock():
            require(re.fullmatch(r'/[A-Za-z0-9._/-]+', args.output), 'MIG_OUTPUT_PATH_INVALID')
            output = new_directory(args.output)
            previous_handler = signal.getsignal(signal.SIGTERM)
            def cancelled(signum, frame):
                raise KeyboardInterrupt()
            signal.signal(signal.SIGTERM, cancelled)
            try: rehearse(value, candidate, args.expected_manifest_sha256, output, Docker(output))
            finally: signal.signal(signal.SIGTERM, previous_handler)
        print('MIG_REHEARSAL_FINISHED_NO_PUBLICATION'); return 0
    except (Exception, KeyboardInterrupt):
        code = 'MIG_REHEARSAL_BLOCKED_SEE_PRIVATE_RECEIPT' if output and (output / 'receipt.json').exists() else 'MIG_REHEARSAL_PREFLIGHT_BLOCKED_NO_DOCKER'
        print(code, file=sys.stderr); return 2


if __name__ == '__main__':
    raise SystemExit(main())
