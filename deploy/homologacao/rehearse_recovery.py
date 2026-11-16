#!/usr/bin/env python3
"""Ensaio recuperável próprio: nunca acessa serviços live ou um canal externo."""
import argparse
from contextlib import contextmanager
import json
import os
from pathlib import Path
import re
import signal
import sys
from subprocess import TimeoutExpired
import time
import uuid
from zipfile import BadZipFile, ZipFile
import io

sys.dont_write_bytecode = True
from rehearse_migration import (Blocked, Docker, MAX_DUMP, MAX_JAR, deploy_lock, encode,
                               host_identity, new_directory, object_json, read, require, sha, write_new)
import recovery_fixture as fixture
import recovery_privacy as privacy

OWNER = 'dev.vanrides.recovery-rehearsal'
LABELS = (OWNER, 'dev.vanrides.operation', 'dev.vanrides.environment')
API_REPOSITORY = 'ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api'


def projected_labels(expression):
    return '{' + ','.join(json.dumps(k) + ':{{json (index ' + expression + ' ' + json.dumps(k) + ')}}' for k in LABELS) + '}'


def manifest(path, expected):
    raw = read(path, 128000)
    require(re.fullmatch('[a-f0-9]{64}', expected or '') and sha(raw) == expected, 'REC_MANIFEST_CHANGED')
    value = object_json(raw)
    require(set(value) == {'schemaVersion', 'environment', 'candidate', 'images', 'sourceFiles', 'workflows', 'privacy'}
            and type(value['schemaVersion']) is int and value['schemaVersion'] in (1, 2)
            and value['environment'] == fixture.ENVIRONMENT, 'REC_MANIFEST_INVALID')
    candidate, images = value['candidate'], value['images']
    require(isinstance(candidate, dict) and set(candidate) == {'jar', 'jarSha256', 'revision', 'tree', 'flywayVersion'}
            and all(isinstance(candidate[k], str) and re.fullmatch('[a-f0-9]{40}', candidate[k]) for k in ('revision', 'tree'))
            and isinstance(candidate['jarSha256'], str) and re.fullmatch('[a-f0-9]{64}', candidate['jarSha256'])
            and type(candidate['flywayVersion']) is int and 43 <= candidate['flywayVersion'] <= 99, 'REC_CANDIDATE_INVALID')
    require(isinstance(images, dict) and set(images) == {'apiRuntime', 'node', 'postgres', 'redis', 'n8n'}
            and images['node'] == fixture.NODE and images['postgres'] == fixture.POSTGRES
            and images['redis'] == fixture.REDIS and images['n8n'] == fixture.N8N
            and isinstance(images['apiRuntime'], str)
            and re.fullmatch(re.escape(API_REPOSITORY) + '@sha256:[a-f0-9]{64}', images['apiRuntime']), 'REC_IMAGES_INVALID')
    require(value['sourceFiles'] == fixture.assets(), 'REC_SOURCE_CHANGED')
    bundle, hashes, workflows = fixture.bundle()
    require(value['workflows'] == hashes, 'REC_WORKFLOWS_CHANGED')
    jar = read(candidate['jar'], MAX_JAR)
    require(sha(jar) == candidate['jarSha256'], 'REC_JAR_CHANGED')
    with ZipFile(io.BytesIO(jar)) as archive:
        names = archive.namelist()
        require(len(names) <= 30000 and len(set(names)) == len(names), 'REC_JAR_INVENTORY_INVALID')
        versions = [int(m.group(1)) for name in names
                    if (m := re.fullmatch(r'BOOT-INF/classes/db/migration/V([1-9][0-9]*)__[A-Za-z0-9_]+\.sql', name))]
    require(versions and max(versions) == candidate['flywayVersion'], 'REC_SCHEMA_ANCHOR_INVALID')
    privacy.validate_manifest(value, jar)
    return value, jar, raw, bundle, workflows


class Owned:
    """Fixed generated names and labels; recover lost create ACKs. Volumes are retained."""
    def __init__(self, run, nonce=None, clock=time.monotonic):
        self.execute, self.nonce, self.clock = run, nonce or uuid.uuid4().hex, clock
        self.deadline = None
        self.containers, self.networks, self.volumes = {}, {}, {}

    def remaining(self, maximum):
        remaining = maximum if self.deadline is None else min(maximum, self.deadline - self.clock())
        require(remaining > 0, 'REC_DEADLINE_EXPIRED'); return remaining

    @contextmanager
    def budget(self, seconds):
        previous = self.deadline
        self.deadline = min(self.clock() + seconds, previous if previous is not None else float('inf'))
        try: yield self.deadline
        finally: self.deadline = previous

    def run(self, args, data=None, **limits):
        limits['timeout'] = self.remaining(limits.get('timeout', 120))
        try: return self.execute(args, data, **limits)
        except TimeoutExpired: raise Blocked('REC_DOCKER_OPERATION_TIMEOUT') from None

    def name(self, role): return 'vanrides-recovery-' + self.nonce + '-' + role

    def labels(self, role):
        return [part for key, value in ((OWNER, self.nonce), (LABELS[1], role), (LABELS[2], fixture.ENVIRONMENT))
                for part in ('--label', key + '=' + value)]

    def network(self, role):
        name = self.name(role)
        require(not self.run(['network', 'ls', '--filter', 'name=^' + name + '$', '--format', '{{.ID}}']).strip(), 'REC_NETWORK_COLLISION')
        self.networks[role] = name
        self.run(['network', 'create', '--driver', 'bridge', '--internal', *self.labels(role), name])
        self.check_network(role); return name

    def check_network(self, role):
        value = json.loads(self.run(['network', 'inspect', '--format', '{"name":{{json .Name}},"driver":{{json .Driver}},"internal":{{json .Internal}},"labels":' + projected_labels('.Labels') + '}', self.networks[role]]))
        require(value['name'] == self.networks[role] and value['internal'] is True and value['driver'] == 'bridge'
                and value['labels'] == {OWNER: self.nonce, LABELS[1]: role, LABELS[2]: fixture.ENVIRONMENT}, 'REC_NETWORK_NOT_OWNED')

    def volume(self, role):
        name = self.name(role)
        require(not self.run(['volume', 'ls', '--filter', 'name=^' + name + '$', '--format', '{{.Name}}']).strip(), 'REC_VOLUME_COLLISION')
        self.volumes[role] = name
        self.run(['volume', 'create', *self.labels(role), name]); self.check_volume(role); return name

    def check_volume(self, role):
        value = json.loads(self.run(['volume', 'inspect', '--format', '{"name":{{json .Name}},"driver":{{json .Driver}},"labels":' + projected_labels('.Labels') + '}', self.volumes[role]]))
        require(value['name'] == self.volumes[role] and value['driver'] == 'local'
                and value['labels'] == {OWNER: self.nonce, LABELS[1]: role, LABELS[2]: fixture.ENVIRONMENT}, 'REC_VOLUME_NOT_OWNED')

    def create(self, role, image, options, command=()):
        name = self.name(role)
        require(role not in self.containers and not self.run(['ps', '-a', '--filter', 'name=^/' + name + '$', '--format', '{{.ID}}']).strip(), 'REC_CONTAINER_COLLISION')
        require(options.count('--network') == 1, 'REC_NETWORK_REQUIRED')
        network = options[options.index('--network') + 1]
        self.containers[role] = {'name': name, 'image': image, 'id': None, 'network': network}
        cid = self.run(['create', '--name', name, *self.labels(role), '--pull', 'never', '--cap-drop', 'ALL',
                        '--security-opt', 'no-new-privileges', '--pids-limit', '192', '--cpus', '1',
                        '--log-driver', 'local', '--log-opt', 'max-size=1m', '--log-opt', 'max-file=1',
                        '--log-opt', 'compress=false', *options, image, *command]).decode().strip()
        require(re.fullmatch('[a-f0-9]{64}', cid), 'REC_CONTAINER_ID_INVALID')
        self.check(role, cid); self.containers[role]['id'] = cid
        self.run(['start', cid]); return cid

    def check(self, role, cid=None):
        require(role in self.containers, 'REC_CONTAINER_NOT_OWNED')
        item = self.containers[role]; cid = cid or item['id']
        value = json.loads(self.run(['inspect', '--format', '{"id":{{json .Id}},"name":{{json .Name}},"image":{{json .Config.Image}},"network":{{json .HostConfig.NetworkMode}},"networks":{{json .NetworkSettings.Networks}},"ports":{{json .HostConfig.PortBindings}},"privileged":{{json .HostConfig.Privileged}},"labels":' + projected_labels('.Config.Labels') + '}', cid]))
        require(value['id'] == cid and value['name'] == '/' + item['name'] and value['image'] == item['image']
                and value['labels'] == {OWNER: self.nonce, LABELS[1]: role, LABELS[2]: fixture.ENVIRONMENT}
                and value['network'] == item['network'] and not value['ports'] and value['privileged'] is False
                and set(value['networks']) <= ({item['network']} if not item['network'].startswith('container:') else set())
                and (item['id'] is None or item['id'] == cid), 'REC_CONTAINER_NOT_OWNED')
        return cid

    def action(self, role, operation):
        require(operation in ('start', 'stop', 'restart'), 'REC_OPERATION_INVALID')
        cid = self.check(role)
        self.run([operation, *(['--time', '30'] if operation != 'start' else []), cid], timeout=90)

    def exec(self, role, command, data=None, **limits):
        cid = self.check(role)
        if callable(data): data = data()  # Timeout payloads consume the ownership inspection's elapsed time too.
        return self.run(['exec', *(['-i'] if data is not None else []), cid, *command], data, **limits)

    def cleanup(self):
        with self.budget(120): self.cleanup_owned()

    def diagnostics(self, role, cid=None, capacity=False, logs=False):
        try:
            with self.budget(2):
                cid = self.check(role, cid)
                commands = [['inspect', '--format', '{"running":{{json .State.Running}},"oomKilled":{{json .State.OOMKilled}},"exitCode":{{json .State.ExitCode}}}', cid]]
                if capacity: commands.append(['exec', cid, 'df', '-k', '/tmp', '/home/node/.n8n', '/home/node/.cache'])
                if logs: commands.append(['logs', '--tail', '2000', cid])
                for command in commands:
                    try: self.run(command, maximum=2 * 1024 * 1024, timeout=2)
                    except (Blocked, OSError) as error:
                        if str(error) == 'REC_CANCELLED': raise
        except (Blocked, OSError) as error:
            if str(error) == 'REC_CANCELLED': raise

    def cleanup_owned(self):
        ids = self.run(['ps', '-a', '--no-trunc', '--filter', 'label=' + OWNER + '=' + self.nonce, '--format', '{{.ID}}']).decode().splitlines()
        require(len(ids) <= len(self.containers) and all(re.fullmatch('[a-f0-9]{64}', x) for x in ids), 'REC_CLEANUP_QUERY_INVALID')
        # Validate every candidate before stopping/removing any; never discover by substring.
        found = []
        for cid in ids:
            matches = [role for role, item in self.containers.items() if item['id'] == cid or item['id'] is None]
            valid = []
            for role in matches:
                try: self.check(role, cid); valid.append(role)
                except Blocked: pass
            require(len(valid) == 1, 'REC_CLEANUP_NOT_OWNED'); found.append((cid, valid[0]))
        for role in self.volumes: self.check_volume(role)
        for role in self.networks: self.check_network(role)
        for cid, role in found:
            self.diagnostics(role, cid, capacity=self.containers[role]['image'] == fixture.N8N, logs=True)
            self.run(['rm', '-f', cid])  # No -v; exclusive volumes remain recoverable.
        require(not self.run(['ps', '-a', '--filter', 'label=' + OWNER + '=' + self.nonce, '--format', '{{.ID}}']).strip(), 'REC_CONTAINER_REMAINS')
        for name in self.networks.values(): self.run(['network', 'rm', name])


def mount(source, target, readonly=True):
    require(not any(c in str(source) for c in ',\r\n\0'), 'REC_MOUNT_PATH_INVALID')
    return ['--mount', 'type=bind,source=' + str(source) + ',target=' + target + (',readonly' if readonly else '')]


def first_date():
    from datetime import datetime, timezone
    return datetime.now(timezone.utc).isoformat().replace('+00:00', 'Z')


class Session:
    def __init__(self, owned, value, output, workflows, pause=time.sleep):
        self.owned, self.value, self.output, self.pause = owned, value, output, pause
        self.keys = fixture.secrets_for_run()
        self.flows, self.credential = fixture.workflows(workflows, owned.nonce)
        self.identity, self.instance = None, 'wa070_synthetic_' + owned.nonce
        self.uid = str(os.getuid()) + ':' + str(os.getgid())
        self.source = output / 'source'; self.source.mkdir(mode=0o700)
        for name in value['sourceFiles']:
            if name.startswith(('src/whatsapp/worker/', 'src/whatsapp/agent/', 'src/whatsapp/transport/')):
                path = self.source / name.removeprefix('src/whatsapp/')
                path.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
                raw = read(fixture.ROOT / name, 2 * 1024 * 1024)
                require(sha(raw) == value['sourceFiles'][name], 'REC_SOURCE_CHANGED'); write_new(path, raw)
        channel = output / 'channel'; channel.mkdir(mode=0o700)
        write_new(channel / 'server.cjs', fixture.CHANNEL.encode())
        write_new(channel / 'config.json', encode({'control': self.keys['control'], 'apiKey': self.keys['provider'], 'instance': self.instance, 'address': fixture.ADDRESS}))
        write_new(output / 'workflows.json', encode([self.flows[key] for key in fixture.bundle()[0]['importOrder']]))
        write_new(output / 'credential.json', encode([{'id': self.credential, 'name': 'Synthetic Recovery Control', 'type': 'httpHeaderAuth',
                                                     'data': {'name': 'Authorization', 'value': 'Bearer ' + self.keys['control']}}]))

    def sql(self, pg, database, statement):
        require(database in fixture.DATABASES or database == 'postgres', 'REC_DATABASE_INVALID')
        return self.owned.exec(pg, ['psql', '-X', '-w', '-U', 'postgres', '-d', database, '-Atq', '-v', 'ON_ERROR_STOP=1'],
                               lambda: (f"SET statement_timeout='{max(1, int(self.owned.remaining(10) * 1000))}ms';\n" + statement).encode()).decode().strip()

    def wait(self, check, code, seconds=120):
        with self.owned.budget(seconds) as deadline:
            while self.owned.clock() < deadline:
                try:
                    complete = check()
                    if self.owned.clock() < deadline and complete: return
                except (Blocked, ValueError): pass
                remaining = deadline - self.owned.clock()
                if remaining > 0: self.pause(min(1, remaining))
        raise Blocked(code)

    def http(self, channel, host, route, body=None, token=None):
        require(host in ('api', 'main', 'worker') and route.startswith('/'), 'REC_HTTP_SCOPE_INVALID')
        port = {'api': 8080, 'main': 5678, 'worker': 7081}[host]
        request = {'url': f'http://{host}:{port}' + route}
        if body is not None: request.update(method='POST', body=body, headers={'content-type': 'application/json'})
        if token: request.setdefault('headers', {})['authorization'] = 'Bearer ' + token
        def payload():
            request['timeoutMs'] = max(1, int(self.owned.remaining(30) * 1000)); return encode(request)
        return json.loads(self.owned.exec(channel, ['node', '-e', fixture.HTTP], payload, timeout=40))

    def channel_state(self, prefix):
        value = json.loads(self.owned.exec(prefix + '-channel', ['node', '-e', "process.stdout.write(require('node:fs').readFileSync('/fixture/state.json'))"]))
        require(set(value) == {'control', 'accepted', 'duplicates', 'invalid', 'hashes'}
                and set(value['control']) == {'process', 'dispatch', 'attendance', 'reconcile'}
                and all(type(x) is int and 0 <= x < 10000 for x in [value['accepted'], value['duplicates'], value['invalid'], *value['control'].values()])
                and value['duplicates'] == value['invalid'] == 0, 'REC_CHANNEL_INVALID_OR_DUPLICATED_EFFECT')
        return value

    def counts(self, pg):
        return self.sql(pg, fixture.DATABASES[0], f"SELECT (SELECT count(*) FROM wa.command_receipt WHERE identity_id='{self.identity}')||'|'||"
                        f"(SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id='{self.identity}' AND state='SUBMITTED')||'|'||"
                        f"(SELECT count(*) FROM wa.message_attempt WHERE instance_id='{self.instance}' AND state='SUBMITTED');")

    def services(self, prefix, pg, network, restored=False):
        images, keys = self.value['images'], self.keys
        redis_volume = self.owned.volume(prefix + '-redis-data')
        self.owned.create(prefix + '-redis', images['redis'], ['--network', network, '--network-alias', 'redis', '--memory', '128m', '--memory-swap', '128m',
                          '--cap-add', 'CHOWN', '--cap-add', 'DAC_OVERRIDE', '--cap-add', 'SETGID', '--cap-add', 'SETUID',
                          '--mount', 'type=volume,source=' + redis_volume + ',target=/data'], ['redis-server', '--appendonly', 'yes', '--appendfsync', 'always', '--save', ''])
        api_config = fixture.api_env('postgres', keys)
        api_config['VANRIDES_JAR_SHA256'] = self.value['candidate']['jarSha256']
        fixture.env_file(self.output / (prefix + '-api.env'), api_config)
        self.owned.create(prefix + '-api', images['apiRuntime'], ['--network', network, '--network-alias', 'api', '--memory', '640m', '--memory-swap', '640m',
                          '--user', self.uid, '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m,mode=1777', '--env-file', str(self.output / (prefix + '-api.env')),
                          *mount(self.output / 'candidate.jar', '/app/app.jar'), '--entrypoint', 'java'],
                          ['-Xms24m', '-Xmx384m', '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', '/app/app.jar'])
        channel = self.owned.create(prefix + '-channel', images['node'], ['--network', network, '--network-alias', 'worker', '--memory', '96m', '--memory-swap', '96m',
                                    '--user', self.uid, '--read-only', *mount(self.output / 'channel', '/fixture', False)], ['node', '/fixture/server.cjs'])
        self.wait(lambda: self.http(prefix + '-channel', 'api', '/actuator/health')['body'].get('status') == 'UP', 'REC_API_NOT_READY')
        require(self.sql(pg, fixture.DATABASES[0], "SELECT max(version::integer)||'|'||count(*) FILTER(WHERE NOT success) FROM public.flyway_schema_history;")
                == str(self.value['candidate']['flywayVersion']) + '|0', 'REC_API_SCHEMA_CHANGED')
        if not restored:
            statement, self.identity, instance = fixture.seed(self.owned.nonce, keys)
            require(instance == self.instance, 'REC_INSTANCE_INVALID'); self.sql(pg, fixture.DATABASES[0], statement)
        worker_env = {'WA070_ENVIRONMENT': fixture.ENVIRONMENT, 'WA070_LOCAL_MODE': 'true', 'WA070_HTTP_HOSTS': 'api,127.0.0.1',
                      'WA070_BACKEND_BASE_URL': 'http://api:8080', 'WA070_EVOLUTION_BASE_URL': 'http://127.0.0.1:7081', 'WA070_INSTANCE': self.instance,
                      'WA070_HOST': '0.0.0.0', 'WA070_PORT': '7080', 'WA070_MODEL_MODE': 'disabled'}
        secrets_dir = self.output / (prefix + '-worker-secrets'); secrets_dir.mkdir(mode=0o700)
        for variable, key in {'CONVERSATION_TOKEN': 'CONVERSATION_WORKER', 'DELIVERY_TOKEN': 'DELIVERY_WORKER',
                              'CONTROL_TOKEN': 'control', 'EVOLUTION_API_KEY': 'provider'}.items():
            write_new(secrets_dir / variable, (keys[key] + '\n').encode()); worker_env['WA070_' + variable + '_FILE'] = '/secrets/' + variable
        write_new(secrets_dir / 'JOB_TOKEN', (keys['SYSTEM_JOB'] + '\n').encode()); worker_env['WA100_JOB_TOKEN_FILE'] = '/secrets/JOB_TOKEN'
        fixture.env_file(self.output / (prefix + '-worker.env'), worker_env)
        self.owned.create(prefix + '-wa', images['node'], ['--network', 'container:' + channel, '--memory', '192m', '--memory-swap', '192m', '--user', self.uid,
                          '--read-only', '--env-file', str(self.output / (prefix + '-worker.env')), *mount(self.source, '/app'), *mount(secrets_dir, '/secrets')], ['node', '/app/worker/main.mjs'])
        fixture.env_file(self.output / (prefix + '-n8n.env'), fixture.n8n_env('postgres', 'redis', keys))
        for role, command in (('main', ['start']), ('n8n-worker', ['worker', '--concurrency=1'])):
            self.owned.create(prefix + '-' + role, images['n8n'], ['--network', network, '--network-alias', role, '--memory', '1024m', '--memory-swap', '1024m',
                              '--user', self.uid, '--read-only', '--tmpfs', '/tmp:rw,nosuid,nodev,size=64m,mode=1777',
                              '--tmpfs', f'/home/node/.n8n:rw,nosuid,nodev,size=32m,mode=700,uid={os.getuid()},gid={os.getgid()}',
                              '--tmpfs', f'/home/node/.cache:rw,nosuid,nodev,size=128m,mode=700,uid={os.getuid()},gid={os.getgid()}',
                              '--env-file', str(self.output / (prefix + '-n8n.env')), *mount(self.output / 'workflows.json', '/fixture/workflows.json'),
                              *mount(self.output / 'credential.json', '/fixture/credential.json')], command)
        self.wait(lambda: self.http(prefix + '-channel', 'main', '/healthz/readiness')['status'] == 200, 'REC_N8N_NOT_READY')

    def postgres(self, prefix, network):
        role = prefix + '-pg'; volume = self.owned.volume(role + '-data')
        fixture.env_file(self.output / (role + '.env'), {'POSTGRES_DB': fixture.DATABASES[0], 'POSTGRES_PASSWORD': self.keys['postgres']})
        self.owned.create(role, self.value['images']['postgres'], ['--network', network, '--network-alias', 'postgres', '--memory', '512m', '--memory-swap', '512m',
                          '--cap-add', 'CHOWN', '--cap-add', 'DAC_OVERRIDE', '--cap-add', 'FOWNER', '--cap-add', 'SETGID', '--cap-add', 'SETUID',
                          '--env-file', str(self.output / (role + '.env')), '--mount', 'type=volume,source=' + volume + ',target=/var/lib/postgresql/data'])
        self.wait(lambda: self.owned.exec(role, ['pg_isready', '-h', '127.0.0.1', '-U', 'postgres', '-d', fixture.DATABASES[0]]).strip().endswith(b'accepting connections'), 'REC_POSTGRES_NOT_READY')
        self.sql(role, 'postgres', 'CREATE DATABASE ' + fixture.DATABASES[1] + ';'); return role

    def n8n_cli(self, prefix, *command):
        self.owned.diagnostics(prefix + '-main', capacity=True)
        return self.owned.exec(prefix + '-main', ['n8n', *command], timeout=120, maximum=2 * 1024 * 1024)

    def verify_exports(self, prefix):
        self.n8n_cli(prefix, 'export:workflow', '--all', '--output=/tmp/export.json')
        rows = json.loads(self.owned.exec(prefix + '-main', ['node', '-e', "process.stdout.write(require('node:fs').readFileSync('/tmp/export.json'))"]))
        require(isinstance(rows, list) and len(rows) == 8 and len({w['id'] for w in rows}) == 8, 'REC_EXPORT_COUNT_INVALID')
        by_id = {w['id']: w for w in rows}
        for key, original in self.flows.items():
            actual = by_id.get(original['id'], {})
            require(all(actual.get(field) == original[field] for field in ('nodes', 'connections', 'settings'))
                    and actual.get('active') is (key in fixture.SCHEDULES), 'REC_EXPORTED_WORKFLOW_CHANGED')
        return sha(encode([{k: w[k] for k in ('id', 'nodes', 'connections', 'settings', 'active')} for w in sorted(rows, key=lambda w: w['id'])]))

    def pending_queue(self, prefix, state='wait'):
        require(state in ('wait', 'active'), 'REC_REDIS_QUEUE_INVALID')
        keys = self.owned.exec(prefix + '-redis', ['redis-cli', '--raw', '--scan', '--pattern', 'bull:*:' + state]).decode().splitlines()
        require(len(keys) <= 20 and all(re.fullmatch('bull:[A-Za-z0-9:_-]+:' + state, key) for key in keys), 'REC_REDIS_QUEUE_INVALID')
        counts = [int(self.owned.exec(prefix + '-redis', ['redis-cli', 'LLEN', key]).strip()) for key in keys]
        require(all(0 <= count <= 10000 for count in counts), 'REC_REDIS_QUEUE_INVALID'); return sum(counts)

    def send(self, prefix, body, status='PERSISTED'):
        value = self.http(prefix + '-channel', 'api', '/integrations/whatsapp/v1/events', body, self.keys['CHANNEL'])
        require(value['status'] == 202 and value['body']['status'] == status, 'REC_EVENT_NOT_DURABLE')

    def quiesce(self, prefix):
        for role in ('main', 'n8n-worker', 'wa', 'api'): self.owned.action(prefix + '-' + role, 'stop')

    def backup_restore(self, source, destination, receipt, after_backup=None):
        require(source == 'source-pg' and destination == 'restored-pg', 'REC_RESTORE_TARGET_INVALID')
        scope = self.sql(source, fixture.DATABASES[0], "SELECT count(*)||'|'||count(*) FILTER(WHERE NOT synthetic OR environment<>'wa070-local') FROM wa.test_cohort;")
        require(scope == ('2|0' if self.value['schemaVersion'] == 2 else '1|0'), 'REC_SYNTHETIC_SCOPE_INVALID')
        receipt['backups'] = []
        backups = []
        for database in fixture.DATABASES:
            before = self.sql(source, database, fixture.FINGERPRINT)
            require(before and re.fullmatch(r'[A-Za-z0-9_.|\n]+', before), 'REC_FINGERPRINT_INVALID')
            path = self.output / (database + '.dump')
            dump = self.owned.exec(source, ['pg_dump', '-w', '-U', 'postgres', '-d', database, '-Fc', '--no-owner', '--no-privileges'], maximum=MAX_DUMP, sink=path)
            require(dump.startswith(b'PGDMP') and 5 < len(dump) <= MAX_DUMP
                    and self.sql(source, database, fixture.FINGERPRINT) == before, 'REC_BACKUP_UNSTABLE')
            backups.append((database, dump, before))
            receipt['backups'].append({'database': database, 'sha256': sha(dump), 'bytes': len(dump), 'contentSha256': sha(before.encode())})
        if after_backup is not None: after_backup()
        receipt['stage'] = 'RESTORE_TO_DIFFERENT_DATABASE'
        for database, dump, before in backups:
            self.owned.exec(destination, ['pg_restore', '-w', '-U', 'postgres', '-d', database, '--exit-on-error', '--single-transaction', '--no-owner', '--no-privileges'], dump)
            require(self.sql(destination, database, fixture.FINGERPRINT) == before, 'REC_RESTORE_CONTENT_CHANGED')


class RestoredDatabase:
    """V44 extension point: owns a single RESTORED target; cannot select the source/live DB."""
    def __init__(self, session, role):
        require(role == 'restored-pg', 'REC_PRIVACY_TARGET_INVALID'); self.session, self.role = session, role

    def sql(self, statement): return self.session.sql(self.role, fixture.DATABASES[0], statement)


def privacy_after_restore(target, declaration):
    require(isinstance(target, RestoredDatabase) and declaration == {'state': 'PENDING_V44'}, 'REC_PRIVACY_PORT_INVALID')
    return {'state': 'PENDING_V44', 'reapplied': False, 'absenceVerified': False, 'target': 'RESTORED_DATABASE_ONLY'}


def rehearse(value, jar, output, run, original, pause=time.sleep, manifest_sha=None):
    owned = Owned(run)
    receipt = {'schemaVersion': 1, 'status': 'BLOCKED', 'scope': 'SYNTHETIC_QUEUE_RESTART_AND_RESTORE', 'nonce': owned.nonce,
               'realWhatsapp': False, 'openai': False, 'liveServicesChanged': False, 'releaseEligible': False,
               'privacy': {'state': 'NOT_REACHED'}, 'checks': [], 'images': value['images'], 'candidate': {k: v for k, v in value['candidate'].items() if k != 'jar'},
               'sourceFiles': value['sourceFiles'], 'workflows': value['workflows'], 'stage': 'PREPARE', 'manifestSha256': manifest_sha}
    try:
        for image in value['images'].values(): run(['image', 'inspect', '--format', '{{.Id}}', image])
        write_new(output / 'candidate.jar', jar)
        session = Session(owned, value, output, original, pause)
        network = owned.network('source-network'); pg = session.postgres('source', network)
        session.services('source', pg, network)
        if value['schemaVersion'] == 2: receipt['privacyOffProbe'] = privacy.probe_disabled(session)
        owned.action('source-n8n-worker', 'stop')
        session.n8n_cli('source', 'import:workflow', '--input=/fixture/workflows.json')
        session.n8n_cli('source', 'import:credentials', '--input=/fixture/credential.json')
        for key in fixture.SCHEDULES: session.n8n_cli('source', 'publish:workflow', '--id=' + session.flows[key]['id'])
        receipt['copiedWorkflowSha256'] = session.verify_exports('source')
        connection = {'event': 'connection.update', 'instance': session.instance, 'date_time': first_date(), 'data': {'state': 'open'}}
        session.send('source', connection)
        first = fixture.event(session.instance, 'oi'); session.send('source', first)
        receipt['stage'] = 'QUEUE_PENDING'
        session.wait(lambda: session.pending_queue('source') > 0, 'REC_AGENDA_DID_NOT_ENQUEUE')
        require(session.counts(pg) == '0|0|0' and session.channel_state('source')['accepted'] == 0, 'REC_WORK_RAN_WITHOUT_QUEUE_WORKER')
        require(session.sql(pg, fixture.DATABASES[0], "SELECT count(*) FROM wa.integration_inbox WHERE category='MESSAGE' AND state='PENDING';") == '1', 'REC_PENDING_INBOX_MISSING')
        receipt['checks'].append('published_schedule_enqueued_real_redis_work_while_worker_stopped')
        owned.action('source-n8n-worker', 'start')
        session.wait(lambda: session.counts(pg) == '1|1|1' and session.channel_state('source')['accepted'] == 1
                     and all(session.channel_state('source')['control'].values()), 'REC_QUEUE_DID_NOT_RECOVER', 180)
        receipt['checks'].append('queue_worker_restart_processed_pending_once_and_all_four_schedules_fired')
        receipt['stage'] = 'SAME_VOLUME_RESTART'; session.quiesce('source')
        for role in ('source-pg', 'source-redis'): owned.action(role, 'restart')
        session.wait(lambda: session.sql(pg, fixture.DATABASES[0], 'SELECT 1') == '1', 'REC_DATABASE_RESTART_FAILED')
        for role in ('api', 'wa', 'main', 'n8n-worker'): owned.action('source-' + role, 'start')
        session.wait(lambda: session.http('source-channel', 'api', '/actuator/health')['status'] == 200, 'REC_RESTART_API_UNHEALTHY')
        before = session.channel_state('source')['control']['dispatch']
        session.send('source', first, 'DUPLICATE')
        session.wait(lambda: session.channel_state('source')['control']['dispatch'] > before, 'REC_SCHEDULE_DID_NOT_RESUME', 90)
        require(session.counts(pg) == '1|1|1' and session.channel_state('source')['accepted'] == 1, 'REC_RESTART_DUPLICATED_EFFECT')
        receipt['checks'].append('postgres_redis_api_workers_restarted_same_ids_volumes_without_resend')
        # A second event remains API-durable at backup, without depending on a Redis in-flight job.
        for role in ('main', 'n8n-worker', 'wa'): owned.action('source-' + role, 'stop')
        receipt['queueAtBackup'] = {'waiting': session.pending_queue('source'), 'active': session.pending_queue('source', 'active')}
        require(receipt['queueAtBackup'] == {'waiting': 0, 'active': 0}, 'REC_QUEUE_NOT_QUIESCENT_FOR_BACKUP')
        second = fixture.event(session.instance, 'Pessoa Sintética Recuperação ' + owned.nonce)
        session.send('source', second); owned.action('source-api', 'stop')
        privacy_run = privacy.PrivacyRecovery(session, lambda stage: receipt.update(stage='PRIVACY_' + stage)); privacy_run.prepare()
        receipt['stage'] = 'RESTORE_TO_DIFFERENT_DATABASE'
        restored_network = owned.network('restored-network'); restored = session.postgres('restored', restored_network)
        require(owned.containers[pg]['id'] != owned.containers[restored]['id'], 'REC_RESTORE_TARGET_IS_SOURCE')
        session.backup_restore(pg, restored, receipt, after_backup=privacy_run.close_purge) if privacy_run.enabled else session.backup_restore(pg, restored, receipt)
        receipt['privacy'] = privacy_run.reapply() if privacy_run.enabled else privacy_after_restore(RestoredDatabase(session, restored), value['privacy'])
        if privacy_run.enabled: receipt['stage'] = 'RESTORED_CORE_RESUME'
        owned.action('source-channel', 'stop')
        session.services('restored', restored, restored_network, restored=True)
        require(session.verify_exports('restored') == receipt['copiedWorkflowSha256'], 'REC_RESTORED_WORKFLOW_CHANGED')
        session.wait(lambda: session.counts(restored) == '2|2|2' and session.channel_state('restored')['accepted'] == 2, 'REC_RESTORED_BACKLOG_NOT_RECOVERED', 180)
        for event in (first, second): session.send('restored', event, 'DUPLICATE')
        before = session.channel_state('restored')['control']['dispatch']
        session.wait(lambda: session.channel_state('restored')['control']['dispatch'] > before, 'REC_RESTORED_SCHEDULE_MISSING')
        require(session.counts(restored) == '2|2|2' and session.channel_state('restored')['accepted'] == 2, 'REC_RESTORE_DUPLICATED_EFFECT')
        receipt['checks'].append('two_real_pg_dumps_restored_elsewhere_pending_event_completed_replays_had_no_effect')
        require(fixture.assets() == value['sourceFiles'] and fixture.bundle()[1] == value['workflows'], 'REC_SOURCE_CHANGED')
        receipt.update(status='CORE_AND_PRIVACY_VERIFIED' if privacy_run.enabled else 'CORE_VERIFIED_PRIVACY_PENDING', stage='FINISHED', totals={'syntheticEvents': 2, 'providerEffects': 2, 'duplicateEffects': 0})
    except BaseException as error:
        receipt['reason'] = str(error) if isinstance(error, Blocked) else type(error).__name__
        raise
    finally:
        previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGINT, signal.SIGTERM})
        try:
            try: owned.cleanup(); receipt['ownedCleanup'] = 'VERIFIED_CONTAINERS_REMOVED_VOLUMES_RETAINED'
            except BaseException:
                receipt['ownedCleanup'] = 'BLOCKED'; receipt['status'] = 'BLOCKED'; raise
            finally:
                receipt['retainedVolumes'] = owned.volumes
                receipt['ownedResources'] = {'containers': owned.containers, 'networks': owned.networks}
                if value['schemaVersion'] == 2:
                    try: receipt['artifactInventory'] = privacy.artifact_inventory(output)
                    except (Blocked, OSError): receipt['artifactInventory'] = {'status': 'UNAVAILABLE', 'deletionImplemented': False}
                write_new(output / 'receipt.json', encode(receipt))
        finally: signal.pthread_sigmask(signal.SIG_SETMASK, previous)
    return receipt


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--inventory', action='store_true'); parser.add_argument('--manifest'); parser.add_argument('--sha256'); parser.add_argument('--output')
    parser.add_argument('--privacy-jar')
    args = parser.parse_args(argv)
    try:
        if args.inventory:
            require(not any((args.manifest, args.sha256, args.output)), 'REC_ARGUMENTS_INVALID')
            inventory = {'sourceFiles': fixture.assets(), 'workflows': fixture.bundle()[1]}
            if args.privacy_jar: inventory['privacy'] = privacy.class_inventory(read(Path(args.privacy_jar), MAX_JAR))
            print(encode(inventory).decode(), end=''); return 0
        require(not args.privacy_jar, 'REC_ARGUMENTS_INVALID')
        require(all((args.manifest, args.sha256, args.output)), 'REC_ARGUMENTS_REQUIRED')
        value, jar, raw, bundle, workflows = manifest(Path(args.manifest), args.sha256)
        host_identity()
        with deploy_lock():
            require(not any(c in args.output for c in ',\r\n\0'), 'REC_OUTPUT_PATH_INVALID')
            output = new_directory(Path(args.output))
            require(read(Path(args.manifest), 128000) == raw, 'REC_MANIFEST_CHANGED')
            write_new(output / 'manifest.json', raw)
            def cancelled(signum, frame): raise Blocked('REC_CANCELLED')
            previous = signal.signal(signal.SIGTERM, cancelled)
            try:
                receipt = rehearse(value, jar, output, Docker(output), workflows, manifest_sha=args.sha256)
                print(encode({k: receipt[k] for k in ('status', 'checks', 'privacy', 'releaseEligible')}).decode(), end='')
            finally: signal.signal(signal.SIGTERM, previous)
        return 0
    except (Blocked, OSError, ValueError, TypeError, KeyError, BadZipFile) as error:
        print(encode({'status': 'BLOCKED', 'reason': str(error) if isinstance(error, Blocked) else type(error).__name__, 'releaseEligible': False}).decode(), end='')
        return 2


if __name__ == '__main__': raise SystemExit(main())
