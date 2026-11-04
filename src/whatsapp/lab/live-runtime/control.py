#!/usr/bin/env python3
"""One foreground, bounded delivery120 lab. No SSH, QR, pairing or automatic dispatch."""
import argparse
import datetime as dt
import json
import os
from pathlib import Path
import platform
import pwd
import re
import secrets
import signal
import socket
import stat
import subprocess
import sys
import time
import uuid

sys.dont_write_bytecode = True
import canonical110
import evidence
import plan
import private_files as private
import receipt
import stage

TECHNICAL = {'CHANNEL': 'channelToken', 'CONVERSATION_WORKER': 'conversationToken',
             'DELIVERY_WORKER': 'deliveryToken', 'SYSTEM_JOB': 'jobToken'}
GUARDS = canonical110.guards()
NETNS = "const fs=require('node:fs');const s=fs.statSync('/proc/self/ns/net');console.log(JSON.stringify({dev:s.dev,ino:s.ino}))"
PROBE = """const u=process.argv[1];fetch(u,{redirect:'error',signal:AbortSignal.timeout(2000)})
.then(async r=>{const t=await r.text();if(t.length>8192)throw 0;let s=null;try{s=JSON.parse(t).status}catch{}
console.log(JSON.stringify({http:r.status,status:typeof s==='string'?s:null}))}).catch(()=>process.exit(2))"""
WORKER_ONCE = """const fs=require('node:fs'),route=process.argv[1];
const routes={'dispatch-one':['status','claimed','submitted','unknown','failed'],
'process-inbox':['status','claimed','processed','retried','paused'],'reconcile-delivery':['status','processed']};
if(!Object.hasOwn(routes,route))process.exit(2);
const token=fs.readFileSync(process.env.WA070_CONTROL_TOKEN_FILE,'utf8').trim();
fetch('http://127.0.0.1:7080/v1/'+route,{method:'POST',headers:{authorization:'Bearer '+token,'content-type':'application/json'},
body:'{}',redirect:'error',signal:AbortSignal.timeout(26000)}).then(async r=>{if(r.status!==200)throw 0;
const text=await r.text();if(text.length>4096)throw 0;const v=JSON.parse(text),keys=routes[route];
if(Object.keys(v).length!==keys.length||keys.some(k=>!Object.hasOwn(v,k))||!(/^[A-Z_]{1,32}$/).test(v.status)||
keys.slice(1).some(k=>!Number.isInteger(v[k])||v[k]<0||v[k]>200))throw 0;
console.log(JSON.stringify(Object.fromEntries(keys.map(k=>[k,v[k]]))))}).catch(()=>process.exit(2))"""


def verify_package(base):
    private.directory(base)
    manifest = receipt.object_json((base / 'source-manifest.json').read_bytes())
    seal = receipt.object_json((base / 'SHA256SUMS.json').read_bytes())
    files = {**seal, 'SHA256SUMS.json': plan.hash_file(base / 'SHA256SUMS.json')}
    for name, digest in files.items():
        path = base / name
        plan.require(path.resolve().is_relative_to(base) and path.is_file() and not path.is_symlink()
                     and plan.hash_file(path) == digest, 'PACKAGE_FILE_CHANGED')
    captured = {name: (base / name).read_bytes() for name in ('contract.json', 'source-manifest.json', 'SHA256SUMS.json')}
    contract = stage.verify_bindings(files, captured, files['source-manifest.json'])
    evidence.validate_envelope(contract, (base / 'inputs/verified-ci-receipt.json').read_bytes(), base / 'inputs/backend.jar')
    return contract, files


def read_session(path):
    raw, _ = private.read_owned(path.parent, path.name, maximum=16384)
    value = receipt.object_json(raw)
    plan.require(set(value) == {'instance', 'startsAt', 'endsAt', 'contacts'}, 'SESSION_CONFIG_INVALID')
    value['secrets'] = {key: secrets.token_urlsafe(32) for key in plan.SECRETS - plan.ISSUED_SECRETS}
    plan.validate_private(value, issued=False)
    now = dt.datetime.now(dt.timezone.utc)
    plan.require(plan.timestamp(value['startsAt']) <= now < plan.timestamp(value['endsAt'])
                 and (plan.timestamp(value['endsAt']) - now).total_seconds() <= 1800, 'SESSION_WINDOW_NOT_CURRENT')
    return value


class Runtime:
    def __init__(self, base, attempt, owner, command=None):
        self.base, self.attempt, self.owner = Path(base), attempt, owner
        self.plan = plan.build_plan(attempt, owner, os.getuid(), os.getgid())
        self.directory = self.base / 'private'
        self.command_override = command  # unit tests only; CLI never supplies one.
        self.state, self.state_identity = None, None
        self.deadline, self.cleanup_deadline = float('inf'), None
        self.memory, self.pressure, self.interrupted = {}, GUARDS.PressureGuard(), False
        self.env = {'PATH': '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
                    'HOME': '/home/mathwidu', 'LANG': 'C.UTF-8', 'DOCKER_CONFIG': str(self.directory / 'docker-config')}

    def identity(self):
        plan.require(sys.platform == 'linux' and platform.machine() == 'x86_64' and os.getuid() > 0 and os.getgid() > 0
                     and pwd.getpwuid(os.getuid()).pw_name == 'mathwidu' and socket.gethostname() == 'srv1397065'
                     and Path.home() == Path('/home/mathwidu') and str(self.base) == self.plan['root'], 'DESTINATION_IDENTITY_MISMATCH')
        private.directory(self.base)
        plan.require(stat.S_ISSOCK(Path('/var/run/docker.sock').stat().st_mode), 'LOCAL_DOCKER_SOCKET_REQUIRED')

    def save(self):
        raw = plan.receipt_bytes(self.state)
        if self.state_identity is None:
            self.state_identity = private.write_new(self.directory, 'state.json', raw)
        else:
            self.state_identity = private.replace_owned(self.directory, self.state_identity, raw)

    def load(self):
        raw, self.state_identity = private.read_owned(self.directory, 'state.json')
        self.state = receipt.object_json(raw)
        plan.require(self.state['attempt'] == self.attempt and self.state['ownerId'] == self.owner
                     and self.state['packageFiles']['contract.json'] == plan.hash_file(self.base / 'contract.json'), 'RUNTIME_OWNER_CHANGED')
        self.deadline = time.monotonic() + max(0, self.state['expiresAtEpoch'] - time.time())
        if 'operatorKeepaliveSeconds' in self.state or 'operator' in self.state['registry']['containers']:
            self.operator_keepalive(self.state.get('operatorKeepaliveSeconds'))

    def operator_keepalive(self, seconds):
        plan.require(type(seconds) is int and 0 < seconds <= 1800, 'OPERATOR_KEEPALIVE_INVALID')
        self.plan['containers']['operator']['command'] = ['/bin/sleep', str(seconds)]

    def check(self):
        plan.require(not self.interrupted and time.monotonic() < self.deadline, 'RUNTIME_INTERRUPTED_OR_EXPIRED')
        self.pressure.sample(enforce_warning=False)
        available = self.pressure.current_mib
        for item in self.memory.values():
            item.sample(available)

    def command(self, args, input_bytes=None, timeout=15, guarded=True):
        plan.require(isinstance(args, list) and all(isinstance(part, str) for part in args), 'DOCKER_ARGUMENT_INVALID')
        if self.command_override:
            return self.command_override(args, input_bytes=input_bytes, timeout=timeout, guarded=guarded)
        if guarded:
            self.check()
        limit = min(time.monotonic() + timeout,
                    self.deadline if guarded else (self.cleanup_deadline or time.monotonic() + timeout))
        process = subprocess.Popen(['/usr/bin/docker', '--host', 'unix:///var/run/docker.sock', *args],
            stdin=subprocess.PIPE if input_bytes is not None else subprocess.DEVNULL,
            stdout=subprocess.PIPE, stderr=subprocess.PIPE, env=self.env, cwd=self.base)
        first = True
        try:
            while True:
                if guarded:
                    self.check()
                plan.require(time.monotonic() < limit, 'DOCKER_COMMAND_TIMEOUT')
                try:
                    stdout, _ = process.communicate(input_bytes if first else None, timeout=min(.2, max(.001, limit-time.monotonic())))
                    break
                except subprocess.TimeoutExpired:
                    first = False
            plan.require(process.returncode == 0 and len(stdout) <= 4194304, 'DOCKER_COMMAND_FAILED')
            return stdout.decode('utf-8').strip()
        finally:
            if process.poll() is None:
                process.terminate()
                try:
                    process.wait(timeout=1)
                except subprocess.TimeoutExpired:
                    process.kill(); process.wait(timeout=1)

    def inspect(self, kind, identity, guarded=True):
        prefix = [] if kind == 'containers' else ['network' if kind == 'networks' else 'volume']
        value = receipt.object_json(('{' + '"rows":' + self.command([*prefix, 'inspect', identity], guarded=guarded) + '}').encode())['rows']
        plan.require(isinstance(value, list) and len(value) == 1, 'DOCKER_INSPECT_INVALID')
        return value[0]

    def snapshot(self, guarded=True):
        output = {'containers': [], 'networks': [], 'volumes': [], 'volumeConsumers': {}}
        registry = self.state['registry']
        for kind in registry:
            for created in registry[kind].values():
                output[kind].append(self.inspect(kind, created['id'], guarded))
        for row in output['volumes']:
            output['volumeConsumers'][row['Name']] = self.command(['ps', '-aq', '--no-trunc', '--filter', 'volume=' + row['Name']], guarded=guarded).split()
        # Also detect an unregistered container after a partial/abrupt creation.
        observed = set(self.command(['ps', '-aq', '--no-trunc', '--filter', 'label=wa120.owner=' + self.owner], guarded=guarded).split())
        plan.require(observed == {row['Id'] for row in output['containers']}, 'UNREGISTERED_OWNED_CONTAINER_REQUIRES_PRINCIPAL')
        for kind, argv in (('networks', ['network', 'ls', '-q', '--no-trunc']), ('volumes', ['volume', 'ls', '-q'])):
            observed = set(self.command([*argv, '--filter', 'label=wa120.owner=' + self.owner], guarded=guarded).split())
            plan.require(observed == {item['id'] for item in registry[kind].values()}, 'UNREGISTERED_OWNED_RESOURCE_REQUIRES_PRINCIPAL')
        plan.teardown_plan(self.plan, registry, output)
        return output

    def register(self, kind, key, identity):
        # Save the creation intent/ID before any further mutations or observations.
        self.state['pending'] = {'kind': kind, 'key': key, 'id': identity}
        self.save()
        row = self.inspect(kind, identity)
        labels = row['Config']['Labels'] if kind == 'containers' else row['Labels']
        self.state['registry'][kind][key] = {'id': identity, 'createdAt': row['CreatedAt' if kind == 'volumes' else 'Created'], 'labels': labels}
        self.state['pending'] = None
        self.save()

    def preflight(self, package_files):
        self.pressure.sample()
        plan.require(not self.directory.exists() and not self.directory.is_symlink(), 'RUNTIME_ALREADY_PREPARED')
        for key, spec in self.plan['containers'].items():
            plan.require(not self.command(['ps', '-aq', '--filter', 'name=^/' + spec['name'] + '$'], guarded=False), 'CONTAINER_NAME_COLLISION')
        for kind, command in (('networks', ['network', 'ls', '-q']), ('volumes', ['volume', 'ls', '-q'])):
            for spec in self.plan[kind].values():
                plan.require(not self.command([*command, '--filter', 'name=^' + spec['name'] + '$'], guarded=False), 'RESOURCE_NAME_COLLISION')
        # A pull is a separate principal operation. Inspect the exact digest already present.
        for image in sorted({spec['image'] for spec in self.plan['containers'].values()}):
            rows = json.loads(self.command(['image', 'inspect', image], guarded=False))
            plan.require(len(rows) == 1 and rows[0]['Architecture'] == 'amd64' and rows[0]['Os'] == 'linux'
                         and image.split('@')[1] in {item.split('@')[1] for item in rows[0].get('RepoDigests', [])}, 'IMAGE_DIGEST_OR_PLATFORM_CHANGED')

    def write_private(self, name, raw):
        self.state['files'][name] = private.write_new(self.directory, name, raw)
        self.save()

    def bootstrap(self, session, package_files):
        private.create_directory(self.base, 'private')
        self.state = {'schemaVersion': 1, 'attempt': self.attempt, 'ownerId': self.owner, 'status': 'PREPARING',
            'expiresAtEpoch': plan.timestamp(session['endsAt']).timestamp(), 'packageFiles': package_files,
            'registry': {'containers': {}, 'networks': {}, 'volumes': {}}, 'pending': None,
            'files': {}, 'directories': {}, 'credentialIds': [], 'pid': os.getpid(), 'unresolved': None}
        self.deadline = time.monotonic() + max(0, self.state['expiresAtEpoch'] - time.time())
        self.save()
        for name in ('operator', 'docker-config'):
            self.state['directories'][name] = private.create_directory(self.directory, name)
        self.save()
        self.write_private('bootstrap.json', plan.receipt_bytes(session))
        for name, raw in plan.render_private_files(session, issued=False).items():
            self.write_private(name, raw)
        uid, gid = os.getuid(), os.getgid()
        self.write_private('passwd', f'root:x:0:0:root:/root:/usr/sbin/nologin\nwa120:x:{uid}:{gid}:lab:/nonexistent:/usr/sbin/nologin\n'.encode())
        self.write_private('group', f'root:x:0:\nwa120:x:{gid}:\n'.encode())
        for service, database, secret in (('app-db', 'vanrides_wa120', 'databasePassword'),
                                          ('evo-db', 'evolution_wa120', 'evolutionDatabasePassword')):
            self.write_private(service + '-pgpass', f"*:*:{database}:{database}:{session['secrets'][secret]}\n".encode())

    def create_infrastructure(self):
        for argv in plan.docker_templates(self.plan):
            if argv[1:3] == ['network', 'create']:
                key = next(key for key, spec in self.plan['networks'].items() if spec['name'] == argv[-1])
                self.register('networks', key, self.command(argv[1:]))
            elif argv[1:3] == ['volume', 'create']:
                key = next(key for key, spec in self.plan['volumes'].items() if spec['name'] == argv[-1])
                self.register('volumes', key, self.command(argv[1:]))

    def create_container(self, service, instance):
        if service == 'operator':
            seconds = min(1800, int(self.deadline - time.monotonic()))
            self.operator_keepalive(seconds)
            self.state['operatorKeepaliveSeconds'] = seconds
            self.save()
        rows = plan.docker_templates(self.plan)
        spec = self.plan['containers'][service]
        argv = next(item for item in rows if item[1:3] == ['create', '--name'] and item[3] == spec['name'])
        resolved = []
        for part in argv[1:]:
            if isinstance(part, str):
                resolved.append(part)
            elif 'containerIdFrom' in part:
                resolved.append(part['prefix'] + self.state['registry']['containers'][part['containerIdFrom']]['id'])
            elif 'privateField' in part:
                plan.require(part['privateField'] == 'instance', 'PRIVATE_FIELD_UNSUPPORTED')
                resolved.append(part['env'] + '=' + instance)
            else:
                raise plan.Blocked('UNRESOLVED_PIN')
        identity = self.command(resolved)
        plan.require(len(identity) == 64 and all(ch in '0123456789abcdef' for ch in identity), 'CONTAINER_ID_INVALID')
        self.register('containers', service, identity)
        for net in spec['networks'][1:]:
            self.command(['network', 'connect', '--alias', 'wa120-' + service, self.plan['networks'][net]['name'], identity])
        return identity

    def start_container(self, service):
        identity = self.state['registry']['containers'][service]['id']
        self.command(['start', identity])
        row = self.inspect('containers', identity)
        if service == 'operator':
            self.operator_generation = {'id': identity, 'startedAt': row['State']['StartedAt'], 'pid': row['State']['Pid']}
            self.operator_observation(row, self.operator_generation)
        self.memory[service] = GUARDS.OwnedMemoryGuard(row, self.plan['containers'][service]['memoryBytes'], self.pressure.current_mib)
        return identity

    def wait_postgres(self, service):
        until = min(time.monotonic() + 60, self.deadline)
        identity = self.state['registry']['containers'][service]['id']
        while True:
            self.check()
            plan.require(time.monotonic() < until, 'POSTGRES_READINESS_TIMEOUT')
            try:
                self.command(['exec', identity, 'pg_isready', '-h', '127.0.0.1'], timeout=3)
                return
            except plan.Blocked as error:
                if str(error) != 'DOCKER_COMMAND_FAILED':
                    raise
            time.sleep(.2)

    def operator_observation(self, row, generation):
        """The CLI execs must leave their owned PID1/cgroup alive and unchanged."""
        registered = self.state['registry']['containers']['operator']
        plan.teardown_plan(self.plan,
            {'containers': {'operator': registered}, 'networks': {}, 'volumes': {}},
            {'containers': [row], 'networks': [], 'volumes': [], 'volumeConsumers': {}})
        state = row['State']
        plan.require(row['Id'] == generation['id'] == registered['id']
                     and isinstance(generation['startedAt'], str) and generation['startedAt']
                     and state['StartedAt'] == generation['startedAt']
                     and type(row.get('RestartCount')) is int and row['RestartCount'] == 0
                     and state.get('OOMKilled') is False and state.get('Restarting') is False
                     and state.get('Dead') is False and state.get('Running') is True,
                     'OPERATOR_GENERATION_OR_RUNTIME_CHANGED')
        plan.require(state.get('Status') == 'running' and state.get('Pid') == generation['pid']
                     and generation['pid'] > 0, 'OPERATOR_PROCESS_CHANGED')

    def operate(self, session, operation, fields):
        plan.require(operation in ('create-test-cohort', 'provision-service-credential'), 'OPERATOR_OPERATION_FORBIDDEN')
        request = {'environment': 'wa070-local', 'operatorCredential': session['secrets']['operatorToken'],
                   'operation': operation, 'batchId': self.state['batchId'], **fields}
        folder = self.directory / 'operator'
        identity = private.write_new(folder, 'request.json', plan.receipt_bytes(request))
        self.state['operatorRequest'] = identity; self.save()
        container = self.state['registry']['containers']['operator']['id']
        generation = self.operator_generation
        self.operator_observation(self.inspect('containers', container), generation)
        # docker exec returns the Java exit status; command() rejects nonzero and
        # samples the unchanged container cgroup throughout this bounded call.
        self.command(['exec', container, *self.plan['containers']['operator']['cliCommand']], timeout=60)
        self.operator_observation(self.inspect('containers', container), generation)
        self.memory['operator'].sample(self.pressure.current_mib)
        raw, result_identity = private.read_owned(folder, 'result.json', maximum=32768)
        self.state['operatorResult'] = result_identity; self.save()
        result = receipt.object_json(raw)
        common = {'status', 'operation', 'environment', 'batchId', 'serverTime', 'resourceId'}
        extra = set() if operation == 'create-test-cohort' else {'credentialId', 'principal', 'allowedInstanceId', 'expiresAt', 'credential'}
        plan.require(set(result) == common | extra and result['status'] == 'COMMITTED'
                     and result['operation'] == operation and result['environment'] == 'wa070-local'
                     and result['batchId'] == self.state['batchId'], 'OPERATOR_RESULT_NOT_COMMITTED')
        if operation == 'provision-service-credential':
            plan.require(result['principal'] == fields['principal'] and result['allowedInstanceId'] == session['instance']
                         and plan.timestamp(result['expiresAt']) == plan.timestamp(fields['expiresAt']) and plan.token(result['credential'])
                         and str(uuid.UUID(result['credentialId'])) == result['resourceId'] == result['credentialId'], 'ISSUED_CREDENTIAL_INVALID')
            self.state['credentialIds'].append({'id': result['credentialId'], 'principal': result['principal']})
        else:
            plan.require(result['resourceId'] == self.state['batchId'], 'COHORT_BINDING_CHANGED')
        self.operator_observation(self.inspect('containers', container), generation)
        self.memory['operator'].sample(self.pressure.current_mib)
        proof = {'operation': operation, 'exitCode': 0, 'status': 'COMMITTED',
                 'containerId': container, 'startedAt': generation['startedAt'],
                 'memorySamples': self.memory['operator'].samples, 'resourceId': result['resourceId']}
        if operation == 'provision-service-credential':
            proof['principal'] = fields['principal']
        self.state.setdefault('operatorCalls', []).append(proof)
        self.save()
        private.remove_owned(folder, identity); private.remove_owned(folder, result_identity)
        self.state.pop('operatorRequest'); self.state.pop('operatorResult'); self.save()
        return result

    def sql(self, statement, guarded=True, service='app-db'):
        identity = self.state['registry']['containers'][service]['id']
        database = self.plan['containers'][service]['env']['POSTGRES_DB']
        return self.command(['exec', '-i', '--user', f'{os.getuid()}:{os.getgid()}', identity,
            'psql', '-XAtq', '-h', '127.0.0.1', '-U', database, '-d', database,
            '-v', 'ON_ERROR_STOP=1'], input_bytes=("SET statement_timeout='5s';\n" + statement + '\n').encode(), guarded=guarded)

    def postgres_authentication(self, service):
        identity = self.state['registry']['containers'][service]['id']
        process = self.command(['exec', '--user', '0', identity, 'cat', '/proc/1/status'])
        uid = re.search(r'^Uid:\s+(\d+)', process, re.M)
        plan.require(uid and int(uid[1]) != 0, 'POSTGRES_SERVER_DID_NOT_DROP_UID')
        # Same query and equality as the canonical110 infra proof.
        query = "SELECT jsonb_build_object('localScram',EXISTS(SELECT 1 FROM pg_hba_file_rules WHERE type='local' AND auth_method='scram-sha-256'),'hostScram',EXISTS(SELECT 1 FROM pg_hba_file_rules WHERE type LIKE 'host%' AND auth_method='scram-sha-256'),'insecureRules',(SELECT count(*) FROM pg_hba_file_rules WHERE auth_method IN ('trust','peer')),'encryption',current_setting('password_encryption'))"
        result = receipt.object_json(self.sql(query, service=service).splitlines()[-1].encode())
        plan.require(result == {'localScram': True, 'hostScram': True, 'insecureRules': 0, 'encryption': 'scram-sha-256'}, 'POSTGRES_SCRAM_NOT_PROVEN')

    def provision(self, session):
        self.state['batchId'] = str(uuid.uuid4()); self.save()
        self.create_container('operator', session['instance'])
        self.start_container('operator')
        self.operate(session, 'create-test-cohort', {'synthetic': True})
        expiry = plan.timestamp(session['endsAt']).astimezone(dt.timezone.utc).isoformat().replace('+00:00', 'Z')
        final = {**session, 'secrets': dict(session['secrets'])}
        for role, field in TECHNICAL.items():
            result = self.operate(session, 'provision-service-credential',
                {'principal': role, 'allowedInstanceId': session['instance'], 'expiresAt': expiry})
            final['secrets'][field] = result['credential']
        plan.validate_private(final)
        # Product tables remain completely empty before the real ingress opens.
        count = self.sql("SELECT (SELECT count(*) FROM wa.identity)+(SELECT count(*) FROM wa.profile)+(SELECT count(*) FROM wa.message_outbox)")
        plan.require(count.splitlines()[-1] == '0', 'TECHNICAL_COHORT_CONTAINS_PEOPLE_OR_MESSAGES')
        operator = self.state['registry']['containers']['operator']['id']
        calls = self.state.get('operatorCalls', [])
        plan.require(len(calls) == 5 and calls[0]['operation'] == 'create-test-cohort'
                     and {call.get('principal') for call in calls[1:]} == set(TECHNICAL), 'OPERATOR_CALLS_NOT_PROVED')
        self.snapshot()
        self.operator_observation(self.inspect('containers', operator), self.operator_generation)
        self.memory['operator'].sample(self.pressure.current_mib)
        # Only this planned stop retires its guard, after all five CLI results
        # and the final live cgroup sample. Other services stay monitored.
        self.memory.pop('operator')
        self.command(['stop', '--time', '1', operator], timeout=5)
        self.snapshot()
        stopped = self.inspect('containers', operator)
        plan.require(stopped['State']['Running'] is False and stopped['State']['OOMKilled'] is False
                     and stopped['State']['StartedAt'] == self.operator_generation['startedAt']
                     and stopped['RestartCount'] == 0, 'OPERATOR_STOP_NOT_PROVED')
        self.command(['rm', operator])
        del self.state['registry']['containers']['operator']; self.save()
        for name, raw in plan.render_private_files(final).items():
            if name in self.state['files']:
                current, _ = private.read_owned(self.directory, name, self.state['files'][name])
                plan.require(current == raw, 'BOOTSTRAP_CONFIGURATION_CHANGED')
            else:
                self.write_private(name, raw)
        self.write_private('final-config.json', plan.receipt_bytes(final))
        self.state['status'] = 'TECHNICAL_CREDENTIALS_ISSUED_EMPTY_COHORT'; self.save()
        return final

    def probe(self, service, url, expected_status, http=200):
        identity = self.state['registry']['containers'][service]['id']
        until = min(time.monotonic() + 60, self.deadline)
        while True:
            plan.require(time.monotonic() < until, 'HTTP_READINESS_TIMEOUT')
            try:
                raw = self.command(['exec', identity, 'node', '-e', PROBE, url], timeout=4)
                value = receipt.object_json(raw.encode())
                if value == {'http': http, 'status': expected_status}:
                    return
            except plan.Blocked as error:
                if str(error) != 'DOCKER_COMMAND_FAILED':
                    raise
            time.sleep(.2)

    def namespace(self):
        values = {}
        for service in ('evolution', 'worker'):
            identity = self.state['registry']['containers'][service]['id']
            row = self.inspect('containers', identity)
            raw = self.command(['exec', identity, 'node', '-e', NETNS], timeout=3)
            ns = receipt.object_json(raw.encode())
            plan.require(set(ns) == {'dev', 'ino'} and all(type(item) is int and item > 0 for item in ns.values()), 'NETNS_OBSERVATION_INVALID')
            values[service] = row, ns
        evo, ens = values['evolution']; worker, wns = values['worker']
        lease = {'evolutionId': evo['Id'], 'evolutionStartedAt': evo['State']['StartedAt'],
            'sandboxKey': evo['NetworkSettings']['SandboxKey'], 'netnsDevice': ens['dev'], 'netnsInode': ens['ino'],
            'workerId': worker['Id'], 'workerNetworkMode': worker['HostConfig']['NetworkMode'],
            'workerNetnsDevice': wns['dev'], 'workerNetnsInode': wns['ino']}
        plan.check_namespace_lease(lease, lease)
        return lease

    def run(self, session_file):
        self.identity()
        contract, files = verify_package(self.base)
        session = read_session(session_file)
        self.preflight(files)
        try:
            self.bootstrap(session, files)
            self.create_infrastructure()
            for service in ('app-db', 'evo-db', 'redis', 'evolution'):
                self.create_container(service, session['instance']); self.start_container(service)
                if service.endswith('-db'):
                    self.wait_postgres(service)
                    self.postgres_authentication(service)
            final = self.provision(session)
            for service in ('api', 'ingress', 'worker'):
                self.create_container(service, final['instance']); self.start_container(service)
            self.probe('evolution', 'http://wa120-api:8080/actuator/health', 'UP')
            self.probe('evolution', 'http://127.0.0.1:8080/', None)
            self.probe('worker', 'http://127.0.0.1:7080/healthz', 'UP')
            self.probe('evolution', 'http://wa120-ingress:7180/evolution/webhook', None, 401)
            self.snapshot()
            self.state['namespaceLease'] = self.namespace()
            self.state['status'] = 'READY_WITHOUT_QR_OR_DISPATCH'; self.save()
            print(json.dumps({'status': self.state['status'], 'attempt': self.attempt, 'expiresAtEpoch': self.state['expiresAtEpoch']}), flush=True)
            while not self.interrupted and time.monotonic() < self.deadline:
                self.check()
                plan.check_namespace_lease(self.state['namespaceLease'], self.namespace())
                time.sleep(.5)
        finally:
            if self.state is not None:
                self.stop()

    def stop(self):
        if self.state is None:
            self.load()
        self.cleanup_deadline = time.monotonic() + 180
        self.memory.clear()
        pending = self.state.get('pending')
        if pending:
            # The creator saved the exact returned ID before this observation.
            row = self.inspect(pending['kind'], pending['id'], guarded=False)
            labels = row['Config']['Labels'] if pending['kind'] == 'containers' else row['Labels']
            self.state['registry'][pending['kind']][pending['key']] = {'id': pending['id'],
                'createdAt': row['CreatedAt' if pending['kind'] == 'volumes' else 'Created'], 'labels': labels}
            self.state['pending'] = None; self.save()
        # Ownership is checked for the complete set before any stop/remove.
        snapshot = self.snapshot(guarded=False)
        actions = plan.teardown_plan(self.plan, self.state['registry'], snapshot)['actions']
        previous_hold = self.state['status'] == 'STOPPED_DATA_HELD_FOR_RECONCILIATION' or self.state.get('holdData') is True
        unresolved = self.state.get('unresolved') if previous_hold else (0 if 'worker' not in self.state['registry']['containers'] else None)
        for service in ('worker', 'ingress', 'api'):
            if service in self.state['registry']['containers']:
                self.command(['stop', '--time', '30', self.state['registry']['containers'][service]['id']], timeout=35, guarded=False)
        if 'app-db' in self.state['registry']['containers']:
            try:
                text = self.sql("SELECT count(*) FROM wa.message_attempt WHERE state IN ('UNKNOWN','SUBMITTING','SUBMITTED')", guarded=False)
                unresolved = int(text.splitlines()[-1])
            except Exception:
                unresolved = None
        hold = previous_hold or unresolved is None or unresolved > 0
        self.state['unresolved'] = unresolved
        self.state['holdData'] = hold
        self.save()  # Durable before removing any container that can read these data.
        for argv in actions:
            if argv[1:3] == ['volume', 'rm'] and hold:
                continue
            self.command(argv[1:], timeout=35 if argv[1] == 'stop' else 15, guarded=False)
            if argv[1] == 'rm':
                key = next(key for key, item in self.state['registry']['containers'].items() if item['id'] == argv[2])
                del self.state['registry']['containers'][key]
            elif argv[1:3] in (['network', 'rm'], ['volume', 'rm']):
                kind = 'networks' if argv[1] == 'network' else 'volumes'
                key = next(key for key, item in self.state['registry'][kind].items() if item['id'] == argv[3])
                del self.state['registry'][kind][key]
            self.save()
        self.snapshot(guarded=False)
        self.state['status'] = 'STOPPED_DATA_HELD_FOR_RECONCILIATION' if hold else 'STOPPED_CLEAN'
        self.save()
        print(json.dumps({'status': self.state['status'], 'unresolvedAttempts': unresolved, 'privateDataRemoved': False}), flush=True)

    def worker_once(self, action):
        """One explicit principal action; no retry or background ticking."""
        plan.require(action in ('dispatch-one', 'process-inbox', 'reconcile-delivery'), 'WORKER_ACTION_INVALID')
        verify_package(self.base)
        self.load()
        plan.require(self.state['status'] == 'READY_WITHOUT_QR_OR_DISPATCH'
                     and time.time() < self.state['expiresAtEpoch'] and Path('/proc/' + str(self.state['pid'])).exists(), 'FOREGROUND_SUPERVISOR_REQUIRED')
        self.snapshot()
        plan.check_namespace_lease(self.state['namespaceLease'], self.namespace())
        identity = self.state['registry']['containers']['worker']['id']
        # The file is read inside the registered worker; no token is in argv.
        raw = self.command(['exec', identity, 'node', '-e', WORKER_ONCE, action], timeout=29)
        result = receipt.object_json(raw.encode())
        print(json.dumps({'action': action, 'result': result}), flush=True)

    def remove_private(self):
        self.load()
        plan.require(self.state['status'] == 'STOPPED_CLEAN' and not self.state['pending'], 'PRIVATE_REMOVAL_REQUIRES_CLEAN_STOP')
        self.snapshot(guarded=False)
        known_files = set(self.state['files']) | {'state.json'}
        known_dirs = set(self.state['directories'])
        plan.require({str(path.relative_to(self.directory)) for path in self.directory.rglob('*')} == known_files | known_dirs,
                     'PRIVATE_TREE_HAS_UNRECOGNIZED_CONTENTS')
        identities = {**self.state['files'], 'state.json': self.state_identity}
        for name, identity in identities.items():
            plan.require(private.R.matches(self.directory / name, identity, False), 'PRIVATE_FILE_CHANGED')
        for name, identity in self.state['directories'].items():
            plan.require(private.R.identity(self.directory / name, '', True) == identity, 'PRIVATE_DIRECTORY_CHANGED')
        root_identity = private.directory(self.directory)
        for name, identity in identities.items():
            private.remove_owned(self.directory, identity)
        for name in sorted(known_dirs):
            plan.require(private.R.identity(self.directory / name, '', True) == self.state['directories'][name], 'PRIVATE_DIRECTORY_CHANGED')
            (self.directory / name).rmdir()
        plan.require(private.directory(self.directory) == root_identity, 'PRIVATE_DIRECTORY_CHANGED')
        self.directory.rmdir()
        print(json.dumps({'status': 'PRIVATE_RUNTIME_REMOVED', 'publicPackageRetained': True}))


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('run', 'status', 'stop', 'remove-private',
                                         'dispatch-one', 'process-inbox', 'reconcile-delivery'))
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--owner-id', required=True)
    parser.add_argument('--session-config', type=Path)
    args = parser.parse_args()
    runtime = Runtime(plan.HERE, args.attempt, args.owner_id)
    def interrupted(*_):
        runtime.interrupted = True
    signal.signal(signal.SIGTERM, interrupted); signal.signal(signal.SIGINT, interrupted)
    try:
        runtime.identity()
        if args.action == 'run':
            plan.require(args.session_config is not None, 'SESSION_CONFIG_REQUIRED')
            runtime.run(args.session_config)
        else:
            runtime.load()
            if args.action in ('stop', 'remove-private'):
                plan.require(runtime.state['pid'] == os.getpid() or not Path('/proc/' + str(runtime.state['pid'])).exists(), 'FOREGROUND_RUN_ACTIVE_INTERRUPT_IT')
            if args.action == 'stop':
                runtime.stop()
            elif args.action == 'remove-private':
                runtime.remove_private()
            elif args.action in ('dispatch-one', 'process-inbox', 'reconcile-delivery'):
                runtime.worker_once(args.action)
            else:
                runtime.snapshot(guarded=False)
                print(json.dumps({'status': runtime.state['status'], 'attempt': args.attempt, 'unresolvedAttempts': runtime.state['unresolved']}))
        return 0
    except Exception as error:
        print(json.dumps({'status': 'BLOCKED', 'code': str(error) if isinstance(error, plan.Blocked) else 'RUNTIME_OPERATION_UNCONFIRMED'}), flush=True)
        return 2


if __name__ == '__main__':
    sys.exit(main())
