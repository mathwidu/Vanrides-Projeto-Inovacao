#!/usr/bin/env python3
"""Offline review only: no subprocess, sockets, HTTP, Docker or secret discovery."""
import argparse
import datetime as dt
import importlib.util
import json
import os
from pathlib import Path
import re
import stat
import sys
import uuid

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE / 'inputs/source' if (HERE / 'source-manifest.json').is_file() else HERE.parents[3]
# Reuse the pure protocol helpers of 110; importing its controller would expose
# live operations unnecessarily. Pressure/cgroup enforcement remains its job.
_spec = importlib.util.spec_from_file_location(
    'wa120_110_protocol', ROOT / 'src/whatsapp/scripts/vps110/server/protocol.py')
_protocol = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(_protocol)
Blocked, require, token = _protocol.Blocked, _protocol.require, _protocol.token
hash_file, receipt_bytes = _protocol.hash_file, _protocol.receipt_bytes

CARD = 'VR-WA-120-DELIVERY-RECOVERY'
SECRETS = frozenset(('webhookToken', 'channelToken', 'conversationToken', 'deliveryToken', 'jobToken',
                     'controlToken', 'evolutionApiKey', 'operatorToken', 'databasePassword',
                     'evolutionDatabasePassword', 'redisPassword', 'jwtSecret', 'hashKey', 'responseKey'))
ISSUED_SECRETS = frozenset(('channelToken', 'conversationToken', 'deliveryToken', 'jobToken'))
LIMITS = {'app-db': (512, 1, 128), 'evo-db': (512, 1, 128), 'redis': (128, .5, 64),
          'evolution': (768, 1, 256), 'api': (1280, 2, 128), 'ingress': (128, .25, 64),
          'worker': (256, .5, 64), 'operator': (1280, 2, 128)}
SECRET_FILES = ('app-db-password', 'evo-db-password', 'application.properties', 'evolution.env',
                'redis.conf', 'ingress.json', 'conversation-token', 'delivery-token', 'job-token',
                'control-token', 'evolution-api-key')


def strict_object(pairs):
    out = {}
    for key, value in pairs:
        require(key not in out, 'JSON_DUPLICATE_KEY')
        out[key] = value
    return out


def private_input(file, issued=True):
    """One explicit file; errors never interpolate a path, value or parser message."""
    fd = None
    try:
        require(isinstance(file, str) and Path(file).is_absolute(), 'PRIVATE_CONFIG_INVALID')
        fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
        info = os.fstat(fd)
        require(stat.S_ISREG(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600
                and info.st_uid == os.getuid() and 0 < info.st_size <= 16384, 'PRIVATE_CONFIG_INVALID')
        raw = os.read(fd, 16385)
        require(len(raw) <= 16384, 'PRIVATE_CONFIG_INVALID')
        value = json.loads(raw.decode('utf-8'), object_pairs_hook=strict_object,
                           parse_constant=lambda _: (_ for _ in ()).throw(Blocked('PRIVATE_CONFIG_INVALID')))
        return validate_private(value, issued=issued)
    except Exception:
        raise Blocked('PRIVATE_CONFIG_INVALID') from None
    finally:
        if fd is not None:
            os.close(fd)


def timestamp(value):
    require(isinstance(value, str) and re.fullmatch(
        r'\d{4}-\d{2}-\d{2}T\d{2}:\d{2}:\d{2}(?:\.\d{1,6})?(?:Z|[+-]\d{2}:\d{2})', value), 'WINDOW_INVALID')
    try:
        result = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        require(result.utcoffset() is not None and abs(result.utcoffset().total_seconds()) < 86400, 'WINDOW_INVALID')
        return result
    except ValueError:
        raise Blocked('WINDOW_INVALID') from None


def validate_private(value, issued=True):
    require(isinstance(value, dict) and set(value) == {'instance', 'startsAt', 'endsAt', 'contacts', 'secrets'}, 'PRIVATE_CONFIG_INVALID')
    require(isinstance(value['instance'], str) and re.fullmatch('[A-Za-z0-9_-]{1,80}', value['instance']), 'INSTANCE_INVALID')
    require(0 < (timestamp(value['endsAt']) - timestamp(value['startsAt'])).total_seconds() <= 1800, 'WINDOW_INVALID')
    secrets = value['secrets']
    keys = SECRETS if issued else SECRETS - ISSUED_SECRETS
    require(isinstance(secrets, dict) and set(secrets) == keys
            and all(token(item) for item in secrets.values()) and len(set(secrets.values())) == len(keys), 'TOKENS_NOT_DISTINCT')
    contacts = value['contacts']
    require(isinstance(contacts, list) and 1 <= len(contacts) <= 2, 'CONTACT_SCOPE_INVALID')
    aliases, labels = set(), set()
    for item in contacts:
        require(isinstance(item, dict) and set(item) in ({'label', 'pn'}, {'label', 'pn', 'lid'})
                and item['label'] in ('A', 'B') and item['label'] not in labels, 'CONTACT_SCOPE_INVALID')
        labels.add(item['label'])
        for kind, suffix in (('pn', r's\.whatsapp\.net'), ('lid', 'lid')):
            if kind not in item:
                continue
            alias = item[kind]
            require(isinstance(alias, str) and re.fullmatch(r'[0-9]{1,32}@' + suffix, alias)
                    and alias not in aliases, 'CONTACT_SCOPE_INVALID')
            aliases.add(alias)
    return value


def load_contract(file=HERE / 'contract.json'):
    value = json.loads(Path(file).read_bytes(), object_pairs_hook=strict_object)
    require(value['schemaVersion'] == 1 and value['cardId'] == CARD
            and value['policy'] == {'maximumWindowSeconds': 1800, 'minimumHostAvailableMiB': 2048,
                                   'publicPorts': [], 'testCohortOnly': False, 'modelMode': 'disabled',
                                   'automaticMessagePostRetry': False, 'fixturePeople': False,
                                   'driverBootstrap': False, 'dockerExecutionImplemented': True,
                                   'newStartsEnabled': False}, 'POLICY_CHANGED')
    require(value['images110'] == 'src/whatsapp/scripts/vps110/physical/contract.json', 'PIN_SOURCE_CHANGED')
    source = json.loads((ROOT / value['images110']).read_bytes())
    images = {key: source['images'][key] for key in ('java', 'node', 'postgres')}
    images.update(value['images'])
    require(set(images) == {'java', 'node', 'postgres', 'evolution', 'redis'}, 'IMAGE_SET_CHANGED')
    for image in images.values():
        require(image is None or isinstance(image, str) and re.fullmatch(r'[A-Za-z0-9_./:-]+@sha256:[a-f0-9]{64}', image), 'IMAGE_NOT_PINNED')
    return value, images


def source_hashes():
    files = []
    for directory in ('worker', 'agent', 'transport', 'lab/live-ingress'):
        base = ROOT / 'src/whatsapp' / directory
        for path in sorted(base.glob('*.mjs')):
            if not path.name.endswith('.test.mjs'):
                require(path.is_file() and not path.is_symlink(), 'SOURCE_INVALID')
                files.append(path)
    require(files, 'SOURCES_MISSING')
    return {str(path.relative_to(ROOT)): hash_file(path) for path in files}


def build_plan(attempt, owner_id, uid, gid, contract=None):
    require(isinstance(attempt, str) and re.fullmatch('[a-z0-9][a-z0-9-]{0,31}', attempt), 'ATTEMPT_INVALID')
    try:
        require(str(uuid.UUID(owner_id)) == owner_id, 'OWNER_INVALID')
    except (ValueError, TypeError, AttributeError):
        raise Blocked('OWNER_INVALID') from None
    require(type(uid) is int and type(gid) is int and uid > 0 and gid > 0, 'UID_GID_INVALID')
    config, images = load_contract() if contract is None else contract
    prefix = 'wa120-live-' + attempt
    base = '/home/mathwidu/.local/share/vanrides/wa120-live/' + attempt
    labels = {'vanrides.card': CARD, 'wa120.owner': owner_id, 'wa120.attempt': attempt, 'traefik.enable': 'false',
              'wa120.backend': config['backend']['sourceSha'], 'wa120.jar': config['backend']['jarSha256'] or 'UNPINNED'}
    networks = {key: {'name': prefix + '-' + key, 'internal': key != 'egress', 'labels': labels}
                for key in ('appdb', 'evodb', 'control', 'egress')}
    volumes = {key: {'name': prefix + '-' + key, 'labels': labels}
               for key in ('appdb-data', 'evodb-data', 'sessions')}
    specs = {}
    for service, (memory, cpus, pids) in LIMITS.items():
        image = images['postgres' if service in ('app-db', 'evo-db') else
                       'node' if service in ('worker', 'ingress') else 'java' if service in ('api', 'operator') else service]
        specs[service] = {'name': prefix + '-' + service, 'image': image, 'labels': {**labels, 'wa120.service': service},
                          'memoryBytes': memory * 1024**2, 'nanoCpus': int(cpus * 10**9), 'pidsLimit': pids,
                          'user': '0' if service in ('app-db', 'evo-db', 'evolution') else f'{uid}:{gid}',
                          'readOnly': service in ('redis', 'api', 'ingress', 'worker', 'operator'), 'mounts': [],
                          'networks': [], 'env': {}, 'command': [], 'envFile': None}
    def bind(service, source, target):
        specs[service]['mounts'].append({'type': 'bind', 'source': base + '/' + source, 'target': target, 'readOnly': True})
    def volume(service, key, target):
        specs[service]['mounts'].append({'type': 'volume', 'source': volumes[key]['name'], 'target': target, 'readOnly': False})
    for service, net, key, secret, database in (
        ('app-db', 'appdb', 'appdb-data', 'app-db-password', 'vanrides_wa120'),
        ('evo-db', 'evodb', 'evodb-data', 'evo-db-password', 'evolution_wa120')):
        specs[service]['networks'] = [net]
        specs[service]['env'] = {'POSTGRES_DB': database, 'POSTGRES_USER': database,
                                 'POSTGRES_PASSWORD_FILE': '/run/wa120/db-password',
                                 'POSTGRES_INITDB_ARGS': '--auth-local=scram-sha-256 --auth-host=scram-sha-256'}
        bind(service, 'private/' + secret, '/run/wa120/db-password')
        volume(service, key, '/var/lib/postgresql/data')
        specs[service]['command'] = ['postgres', '-c', 'log_statement=none', '-c', 'log_min_error_statement=panic', '-c', 'log_min_messages=panic']
    for service in ('app-db', 'evo-db'):
        specs[service]['env']['PGPASSFILE'] = '/run/wa120/pgpass'
        bind(service, 'private/' + service + '-pgpass', '/run/wa120/pgpass')
    specs['redis']['networks'] = ['evodb']
    bind('redis', 'private/redis.conf', '/run/wa120/redis.conf')
    specs['redis']['command'] = ['redis-server', '/run/wa120/redis.conf']
    specs['evolution']['networks'] = ['evodb', 'control', 'egress']
    specs['evolution']['envFile'] = base + '/private/evolution.env'
    volume('evolution', 'sessions', '/evolution/instances')
    specs['api']['networks'] = ['appdb', 'control']
    bind('api', 'inputs/backend.jar', '/opt/wa120/backend.jar')
    bind('api', 'private/application.properties', '/run/wa120/application.properties')
    specs['api']['env'] = {'SPRING_CONFIG_IMPORT': 'file:/run/wa120/application.properties[.properties]'}
    specs['api']['command'] = ['java', '-Xms24m', '-Xmx192m', '-XX:MaxMetaspaceSize=192m',
                               '-XX:+UseSerialGC', '-XX:ActiveProcessorCount=2', '-jar', '/opt/wa120/backend.jar']
    specs['ingress']['networks'] = ['control']
    bind('ingress', 'inputs/source/integrations/whatsapp', '/opt/whatsapp')
    bind('ingress', 'private/ingress.json', '/run/wa120/ingress.json')
    specs['ingress']['env'] = {'WA120_INGRESS_CONFIG_FILE': '/run/wa120/ingress.json'}
    specs['ingress']['command'] = ['node', '--max-old-space-size=64', '/opt/whatsapp/lab/live-ingress/main.mjs']
    specs['worker']['networkMode'] = {'containerIdFrom': 'evolution', 'generationLeaseRequired': True}
    bind('worker', 'inputs/source/integrations/whatsapp', '/opt/whatsapp')
    for name in ('conversation-token', 'delivery-token', 'job-token', 'control-token', 'evolution-api-key'):
        bind('worker', 'private/' + name, '/run/wa120/' + name)
    specs['worker']['env'] = {'WA070_ENVIRONMENT': 'wa070-local', 'WA070_LOCAL_MODE': 'true',
        'WA070_HTTP_HOSTS': '127.0.0.1,wa120-api', 'WA070_BACKEND_BASE_URL': 'http://wa120-api:8080',
        'WA070_EVOLUTION_BASE_URL': 'http://127.0.0.1:8080', 'WA070_HOST': '127.0.0.1', 'WA070_PORT': '7080',
        'WA070_MODEL_MODE': 'disabled', 'WA070_INSTANCE': {'privateField': 'instance'},
        **{'WA070_' + key + '_FILE': '/run/wa120/' + name for key, name in
           (('CONVERSATION_TOKEN', 'conversation-token'), ('DELIVERY_TOKEN', 'delivery-token'),
            ('CONTROL_TOKEN', 'control-token'), ('EVOLUTION_API_KEY', 'evolution-api-key'))},
        'WA100_JOB_TOKEN_FILE': '/run/wa120/job-token'}
    specs['worker']['command'] = ['node', '--max-old-space-size=128', '/opt/whatsapp/worker/main.mjs']
    specs['operator']['networks'] = ['appdb']
    for source, target in (('inputs/backend.jar', '/opt/wa120/backend.jar'),
                           ('private/application.properties', '/run/wa120/application.properties'),
                           ('private/passwd', '/etc/passwd'), ('private/group', '/etc/group')):
        bind('operator', source, target)
    specs['operator']['mounts'].append({'type': 'bind', 'source': base + '/private/operator',
                                         'target': '/run/wa120/operator', 'readOnly': False})
    specs['operator']['env'] = specs['api']['env'].copy()
    specs['operator']['command'] = ['/bin/sleep', '1800']
    specs['operator']['cliCommand'] = [*specs['api']['command'], '--spring.main.web-application-type=none',
        '--vanrides.wa.operator.request-file=/run/wa120/operator/request.json',
        '--vanrides.wa.operator.output-file=/run/wa120/operator/result.json']
    specs['operator']['phase'] = 'BOOTSTRAP_ONLY_REMOVED_BEFORE_API_START'
    return {'schemaVersion': 1, 'status': 'PREPARADO_NAO_EXECUTAVEL', 'reviewOnly': True,
            'attempt': attempt, 'ownerId': owner_id, 'root': base, 'backend': config['backend'],
            'networks': networks, 'volumes': volumes, 'containers': specs, 'sourceHashes': source_hashes(),
            'maximumWindowSeconds': 1800, 'maximumMemoryMiB': sum(row[0] for key, row in LIMITS.items() if key != 'operator'),
            'blockers': ['INDEPENDENT_REVIEW_AND_REMOTE_PREFLIGHT_NOT_RUN',
                         'TECHNICAL_CREDENTIALS_AND_EMPTY_COHORT_NOT_VERIFIED',
                         'PRIVATE_CONFIG_NOT_SUPPLIED', 'BACKEND_GATE_SMOKE_JAR_RECEIPT_PENDING']
                        + (['REDIS_DIGEST_NOT_VERIFIED'] if images['redis'] is None else [])}


def docker_templates(plan):
    """Structured argv with typed unresolved references; never a shell script."""
    output = []
    def label_args(labels):
        return [part for key, value in sorted(labels.items()) for part in ('--label', key + '=' + value)]
    for item in plan['networks'].values():
        output.append(['docker', 'network', 'create', '--driver', 'bridge',
                       *(['--internal'] if item['internal'] else []), *label_args(item['labels']), item['name']])
    for item in plan['volumes'].values():
        output.append(['docker', 'volume', 'create', *label_args(item['labels']), item['name']])
    for service, item in plan['containers'].items():
        argv = ['docker', 'create', '--name', item['name'], '--platform', 'linux/amd64', '--restart', 'no',
                '--log-driver', 'none', '--memory', str(item['memoryBytes']), '--memory-swap', str(item['memoryBytes']),
                '--cpus', str(item['nanoCpus'] / 10**9), '--pids-limit', str(item['pidsLimit']),
                '--security-opt', 'no-new-privileges:true', *label_args(item['labels'])]
        if item['user']:
            argv.extend(['--user', item['user']])
        if item['readOnly']:
            argv.append('--read-only')
        if service == 'worker':
            argv.extend(['--network', {'containerIdFrom': 'evolution', 'prefix': 'container:'}])
        else:
            argv.extend(['--network', plan['networks'][item['networks'][0]]['name'], '--network-alias', 'wa120-' + service])
        if item['envFile']:
            argv.extend(['--env-file', item['envFile']])
        for key, value in sorted(item['env'].items()):
            argv.extend(['--env', key + '=' + value if isinstance(value, str) else {'env': key, **value}])
        for mount in item['mounts']:
            argv.extend(['--mount', 'type=' + mount['type'] + ',source=' + mount['source'] + ',target='
                         + mount['target'] + (',readonly' if mount['readOnly'] else '')])
        argv.extend(['--tmpfs', '/tmp:rw,nosuid,nodev,noexec,size=67108864'])
        if service == 'redis':
            uid, gid = item['user'].split(':')
            argv.extend(['--tmpfs', f'/data:rw,nosuid,nodev,noexec,size=67108864,uid={uid},gid={gid},mode=0700'])
        argv.extend([item['image'] or {'imagePinRequired': service}, *item['command']])
        output.append(argv)
        for net in item['networks'][1:]:
            output.append(['docker', 'network', 'connect', '--alias', 'wa120-' + service,
                           plan['networks'][net]['name'], {'createdContainerIdFrom': service}])
    return output


def render_private_files(value, issued=True):
    """Returns secrets in memory to a future private writer. Never called by CLI."""
    validate_private(value, issued=issued)
    s = value['secrets']
    props = {'server.address': '0.0.0.0', 'server.port': '8080', 'spring.profiles.active': 'local',
        'spring.datasource.url': 'jdbc:postgresql://wa120-app-db:5432/vanrides_wa120',
        'spring.datasource.username': 'vanrides_wa120', 'spring.datasource.password': s['databasePassword'],
        'spring.jpa.hibernate.ddl-auto': 'validate', 'spring.jpa.show-sql': 'false',
        'spring.flyway.baseline-on-migrate': 'false', 'vanrides.auth.jwt-secret': s['jwtSecret'],
        'vanrides.routing.provider': 'static', 'vanrides.maps-platform.enabled': 'false',
        'vanrides.push.provider': 'none', 'vanrides.email.provider': 'log', 'vanrides.wa.enabled': 'true',
        'vanrides.wa.new-starts-enabled': 'false', 'vanrides.wa.test-cohort-only': 'false',
        'vanrides.wa.environment': 'wa070-local', 'vanrides.wa.public-base-url': 'https://localhost:18443',
        'vanrides.wa.start-link-base-url': 'https://vanrides.invalid', 'vanrides.wa.hash-key': s['hashKey'],
        'vanrides.wa.response-key': s['responseKey'], 'vanrides.wa.operator-credential': s['operatorToken'],
        'vanrides.wa.operator-expires-at': timestamp(value['endsAt']).astimezone(dt.timezone.utc).isoformat().replace('+00:00', 'Z'), 'logging.level.root': 'OFF',
        'server.tomcat.accesslog.enabled': 'false'}
    evo = {'SERVER_TYPE': 'http', 'SERVER_PORT': '8080', 'SERVER_URL': 'http://127.0.0.1:8080',
        'DATABASE_PROVIDER': 'postgresql', 'DATABASE_CONNECTION_CLIENT_NAME': 'wa120',
        'DATABASE_CONNECTION_URI': 'postgresql://evolution_wa120:' + s['evolutionDatabasePassword']
                                    + '@wa120-evo-db:5432/evolution_wa120?schema=public',
        'AUTHENTICATION_API_KEY': s['evolutionApiKey'], 'AUTHENTICATION_EXPOSE_IN_FETCH_INSTANCES': 'false',
        'CACHE_REDIS_ENABLED': 'true', 'CACHE_REDIS_URI': 'redis://:' + s['redisPassword'] + '@wa120-redis:6379/0',
        'CACHE_REDIS_PREFIX_KEY': 'wa120', 'CACHE_REDIS_TTL': '1800', 'CACHE_REDIS_SAVE_INSTANCES': 'false',
        'CACHE_LOCAL_ENABLED': 'false', 'DATABASE_SAVE_DATA_INSTANCE': 'true',
        'LOG_LEVEL': 'ERROR,WARN', 'LOG_BAILEYS': 'error', 'WEBHOOK_GLOBAL_ENABLED': 'false'}
    for key in ('DATABASE_SAVE_DATA_NEW_MESSAGE', 'DATABASE_SAVE_MESSAGE_UPDATE', 'DATABASE_SAVE_DATA_CONTACTS',
                'DATABASE_SAVE_DATA_CHATS', 'DATABASE_SAVE_DATA_HISTORIC', 'DATABASE_SAVE_DATA_LABELS',
                'DATABASE_SAVE_IS_ON_WHATSAPP', 'DATABASE_DELETE_MESSAGE', 'WEBSOCKET_ENABLED',
                'TELEMETRY_ENABLED', 'RABBITMQ_ENABLED', 'SQS_ENABLED', 'NATS_ENABLED', 'KAFKA_ENABLED', 'PUSHER_ENABLED',
                'TYPEBOT_ENABLED', 'CHATWOOT_ENABLED',
                'OPENAI_ENABLED', 'DIFY_ENABLED', 'FLOWISE_ENABLED', 'N8N_ENABLED', 'EVOAI_ENABLED', 'S3_ENABLED'):
        evo[key] = 'false'
    lines = lambda values: ''.join(key + '=' + item + '\n' for key, item in sorted(values.items())).encode()
    files = {'app-db-password': (s['databasePassword'] + '\n').encode(),
             'evo-db-password': (s['evolutionDatabasePassword'] + '\n').encode(),
             'application.properties': lines(props), 'evolution.env': lines(evo),
             'redis.conf': ('bind 0.0.0.0\nprotected-mode yes\nport 6379\nsave ""\nappendonly no\n'
                            'maxmemory 64mb\nmaxmemory-policy noeviction\nloglevel warning\nlogfile ""\nrequirepass '
                            + s['redisPassword'] + '\n').encode()}
    for name, key in (('conversation-token', 'conversationToken'), ('delivery-token', 'deliveryToken'),
                      ('job-token', 'jobToken'), ('control-token', 'controlToken'), ('evolution-api-key', 'evolutionApiKey')):
        if key in s:
            files[name] = (s[key] + '\n').encode()
    if issued:
        ingress = {key: value[key] for key in ('instance', 'startsAt', 'endsAt', 'contacts')}
        ingress.update(webhookToken=s['webhookToken'], channelToken=s['channelToken'],
                       apiOrigin='http://wa120-api:8080', listenHost='0.0.0.0', listenPort=7180)
        files['ingress.json'] = receipt_bytes(ingress)
    missing = set() if issued else {'ingress.json', 'conversation-token', 'delivery-token', 'job-token'}
    require(set(files) == set(SECRET_FILES) - missing, 'PRIVATE_FILE_SET_CHANGED')
    return files


def check_namespace_lease(expected, observed):
    """Executor must read kernel netns and current StartedAt before EACH dispatch."""
    fields = {'evolutionId', 'evolutionStartedAt', 'sandboxKey', 'netnsDevice', 'netnsInode',
              'workerId', 'workerNetworkMode', 'workerNetnsDevice', 'workerNetnsInode'}
    require(isinstance(expected, dict) and isinstance(observed, dict) and set(expected) == set(observed) == fields,
            'NAMESPACE_LEASE_INVALID')
    require(expected == observed and re.fullmatch('[a-f0-9]{64}', expected['evolutionId'])
            and re.fullmatch('[a-f0-9]{64}', expected['workerId'])
            and expected['workerNetworkMode'] == 'container:' + expected['evolutionId']
            and isinstance(expected['sandboxKey'], str) and expected['sandboxKey'].startswith('/var/run/docker/netns/')
            and type(expected['netnsDevice']) is int and type(expected['netnsInode']) is int
            and expected['netnsDevice'] > 0 and expected['netnsInode'] > 0
            and type(expected['workerNetnsDevice']) is int and type(expected['workerNetnsInode']) is int
            and expected['workerNetnsDevice'] == expected['netnsDevice']
            and expected['workerNetnsInode'] == expected['netnsInode']
            and isinstance(expected['evolutionStartedAt'], str) and expected['evolutionStartedAt'],
            'EVOLUTION_GENERATION_CHANGED_STOP_AND_RECREATE_WORKER')
    return True


def teardown_plan(plan, registry, snapshot):
    """Validate every resource first; return exact-ID actions, never execute them.

    registry is the private creation receipt (IDs/CreatedAt). snapshot contains
    full Docker inspect rows and volumeConsumers from a fresh all-container query.
    Never use labels alone as authority or prune resources by a name prefix.
    """
    require(set(snapshot) == {'containers', 'networks', 'volumes', 'volumeConsumers'}
            and set(registry) == {'containers', 'networks', 'volumes'}, 'SNAPSHOT_INVALID')
    actions, known_ids, actual = [], set(), {}
    for kind in ('containers', 'networks', 'volumes'):
        expected_specs = plan[kind]
        require(set(registry[kind]) <= set(expected_specs), 'REGISTRY_SCOPE_INVALID')
        rows = snapshot[kind]
        require(isinstance(rows, list) and len(rows) == len(registry[kind]), 'RESOURCE_SET_CHANGED')
        actual[kind] = {}
        for row in rows:
            key = next((key for key, spec in expected_specs.items()
                        if row.get('Name', '').lstrip('/') == spec['name']), None)
            require(key in registry[kind] and key not in actual[kind], 'FOREIGN_RESOURCE')
            spec, created = expected_specs[key], registry[kind][key]
            labels = row.get('Config', {}).get('Labels') if kind == 'containers' else row.get('Labels')
            require(labels == created['labels'] and isinstance(labels, dict)
                    and all(labels.get(name) == value for name, value in spec['labels'].items())
                    and row.get('CreatedAt' if kind == 'volumes' else 'Created') == created['createdAt'], 'OWNER_BINDING_CHANGED')
            identity = row.get('Name') if kind == 'volumes' else row.get('Id')
            require(identity == created['id'] and (kind == 'volumes' or re.fullmatch('[a-f0-9]{64}', identity)), 'RESOURCE_ID_CHANGED')
            if kind == 'containers':
                host, conf = row['HostConfig'], row['Config']
                require(conf['Image'] == spec['image'] and conf['User'] == spec['user']
                        and (not spec['command'] or conf.get('Cmd') == spec['command'])
                        and not host.get('PortBindings') and not host['Privileged']
                        and not host.get('CapAdd') and not host.get('Devices') and not host.get('DeviceRequests')
                        and host.get('PidMode', '') == '' and host.get('UTSMode', '') == ''
                        and host.get('IpcMode') == 'private'
                        and host.get('SecurityOpt') == ['no-new-privileges:true']
                        and host['LogConfig']['Type'] == 'none' and host['RestartPolicy']['Name'] == 'no'
                        and host['Memory'] == spec['memoryBytes'] and host['MemorySwap'] == spec['memoryBytes']
                        and host['NanoCpus'] == spec['nanoCpus'] and host['PidsLimit'] == spec['pidsLimit']
                        and host['ReadonlyRootfs'] == spec['readOnly'], 'CONTAINER_POLICY_CHANGED')
                mode = host['NetworkMode']
                if key == 'worker':
                    require('evolution' in registry['containers']
                            and mode == 'container:' + registry['containers']['evolution']['id'], 'WORKER_NAMESPACE_CHANGED')
                else:
                    require(mode == plan['networks'][spec['networks'][0]]['name']
                            and set(row['NetworkSettings']['Networks']) <= {plan['networks'][key]['name'] for key in spec['networks']}, 'NETWORK_SCOPE_CHANGED')
                require(not any(bindings for bindings in row['NetworkSettings'].get('Ports', {}).values()), 'PUBLIC_PORT_DETECTED')
                mounts = [mount for mount in row['Mounts'] if mount['Type'] != 'tmpfs']
                require(len(mounts) == len(spec['mounts']), 'MOUNT_SCOPE_CHANGED')
                for mount in spec['mounts']:
                    matches = [item for item in mounts if item['Destination'] == mount['target']]
                    require(len(matches) == 1 and matches[0]['Type'] == mount['type'] and matches[0]['RW'] is not mount['readOnly']
                            and matches[0]['Source' if mount['type'] == 'bind' else 'Name'] == mount['source'], 'MOUNT_SCOPE_CHANGED')
                known_ids.add(identity)
            elif kind == 'networks':
                require(row['Driver'] == 'bridge' and row['Scope'] == 'local' and row['Internal'] is spec['internal'], 'NETWORK_POLICY_CHANGED')
            else:
                require(row['Driver'] == 'local' and row['Scope'] == 'local'
                        and not row.get('Options'), 'VOLUME_POLICY_CHANGED')
            actual[kind][key] = row
    for key, row in actual['networks'].items():
        require(set(row.get('Containers', {})) <= known_ids, 'NETWORK_HAS_FOREIGN_MEMBER')
    require(set(snapshot['volumeConsumers']) == {item['name'] for item in plan['volumes'].values()
                                               if item['name'] in {row['Name'] for row in actual['volumes'].values()}}, 'VOLUME_USAGE_NOT_PROVED')
    for users in snapshot['volumeConsumers'].values():
        require(isinstance(users, list) and set(users) <= known_ids, 'VOLUME_HAS_FOREIGN_MEMBER')
    for service in ('worker', 'ingress', 'api', 'operator', 'evolution', 'redis', 'app-db', 'evo-db'):
        if service in actual['containers']:
            identity = actual['containers'][service]['Id']
            actions.extend([['docker', 'stop', '--time', '30', identity], ['docker', 'rm', identity]])
    for kind, command in (('networks', ['docker', 'network', 'rm']), ('volumes', ['docker', 'volume', 'rm'])):
        for key in sorted(actual[kind]):
            actions.append(command + [registry[kind][key]['id']])
    return {'status': 'TEARDOWN_REVIEW_ONLY', 'actions': actions, 'privateDirectoryRemoval': 'NOT_IMPLEMENTED',
            'unknownAttempts': 'PRESERVE_AND_RECONCILE_NO_AUTOMATIC_POST'}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--owner-id', required=True)
    parser.add_argument('--uid', type=int, required=True)
    parser.add_argument('--gid', type=int, required=True)
    parser.add_argument('--private-config')
    args = parser.parse_args()
    try:
        plan = build_plan(args.attempt, args.owner_id, args.uid, args.gid)
        if args.private_config:
            private_input(args.private_config)
            plan['blockers'].remove('PRIVATE_CONFIG_NOT_SUPPLIED')
        plan['dockerArgvTemplates'] = docker_templates(plan)
        print(json.dumps(plan, sort_keys=True, separators=(',', ':')))
        return 2  # An offline plan must never be mistaken for runtime readiness.
    except Exception as error:
        code = str(error) if isinstance(error, Blocked) else 'PLAN_INPUT_INVALID'
        print(json.dumps({'status': 'PREPARADO_NAO_EXECUTAVEL', 'code': code}))
        return 2


if __name__ == '__main__':
    sys.exit(main())


def resolve_source(relative):
    """Relocate an original sealed helper without rewriting its pinned bytes."""
    original = ROOT / relative
    migrated = ROOT / relative.replace('integrations/whatsapp/', 'src/whatsapp/', 1)
    return original if original.is_file() else migrated
