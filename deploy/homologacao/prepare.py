#!/usr/bin/env python3
"""Local packaging/validation. No build, push, pull, SSH, login or deployment."""
import argparse
import copy
import datetime as dt
import hashlib
import ipaddress
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys
import tempfile

sys.dont_write_bytecode = True
HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
LIVE = ROOT / 'src/whatsapp/lab/live-runtime'
sys.path.insert(0, str(LIVE))
import monorepo_receipt as M
from plan import Blocked, require
from owned_container import OwnedContainer, Terminated
from docker_client import local_docker

HOST = 'vanrides-inovacao-api.matheusduarte.dev.br'
ENVIRONMENT = 'vanrides-team-hml'
STACK = 'vanrides-hml-api'
API_REPOSITORY = 'ghcr.io/mathwidu/vanrides-projeto-inovacao-team-api'
PROFILE = 'VANRIDES_TEAM_API_ARTIFACT_V1'
UUID_PATTERN = '[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}'
TEMPLATES = ('Dockerfile', 'entrypoint.sh')
BUNDLE_FILES = {'backend.jar', 'candidate.json', 'gate.json', 'smoke.json'}


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def encode(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':'), ensure_ascii=True) + '\n').encode()


def read(file, maximum=128 * 1024 * 1024, private=False):
    file = Path(file)
    require(file.is_absolute() and file.parent.resolve() == file.parent, 'HML_INPUT_PATH_INVALID')
    fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= maximum, 'HML_INPUT_INVALID')
        if private:
            require(before.st_uid == os.getuid() and stat.S_IMODE(before.st_mode) == 0o600, 'HML_PRIVATE_FILE_REQUIRED')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            raw = stream.read(maximum + 1)
        after = os.fstat(fd)
        require(len(raw) == before.st_size and all(getattr(before, field) == getattr(after, field) for field in
                ('st_dev', 'st_ino', 'st_uid', 'st_mode', 'st_size', 'st_mtime_ns', 'st_ctime_ns')), 'HML_INPUT_CHANGED')
        return raw
    finally:
        os.close(fd)


def obj(file):
    return M.object_json(read(file, M.MAX_JSON_BYTES))


def new_directory(output):
    output = Path(output)
    require(output.is_absolute() and output.parent.resolve() == output.parent and output.parent.is_dir()
            and not output.exists() and not output.is_symlink(), 'HML_OUTPUT_INVALID')
    require(not any((parent / '.git').exists() for parent in (output.parent, *output.parent.parents)), 'HML_OUTPUT_INSIDE_GIT')
    output.mkdir(mode=0o700)
    return output


def write_new(path, raw):
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())


def image(value, repository=None):
    require(isinstance(value, str) and re.fullmatch(r'[a-z0-9][a-z0-9._:/-]*@sha256:[a-f0-9]{64}', value)
            and '..' not in value and '//' not in value, 'HML_IMAGE_DIGEST_REQUIRED')
    if repository:
        require(value.split('@')[0] == repository, 'HML_IMAGE_REPOSITORY_INVALID')
    return value


def validate_inputs(candidate_raw, gate_raw, smoke_raw, jar, expected_candidate, expected_sha, expected_tree):
    require(M.sha(expected_candidate) and M.sha(expected_sha, 40) and M.sha(expected_tree, 40), 'HML_ANCHOR_REQUIRED')
    require(sha(candidate_raw) == expected_candidate, 'HML_CANDIDATE_CHANGED')
    candidate, gate, smoke = map(M.object_json, (candidate_raw, gate_raw, smoke_raw))
    M.candidate(candidate, gate, smoke)
    require(candidate['source']['sha'] == expected_sha and candidate['source']['tree'] == expected_tree,
            'HML_SOURCE_CHANGED')
    require(sha(gate_raw) == candidate['gateSha256'] and sha(smoke_raw) == candidate['smokeSha256'],
            'HML_RECEIPT_CHANGED')
    require(candidate['jar'] == {'sha256': sha(jar), 'bytes': len(jar)}, 'HML_JAR_CHANGED')
    return candidate


def source_checkout(expected_sha, expected_tree):
    env = {'PATH': os.environ.get('PATH', os.defpath), 'HOME': '/nonexistent', 'LC_ALL': 'C',
           'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_CONFIG_SYSTEM': os.devnull, 'GIT_CONFIG_NOSYSTEM': '1',
           'GIT_OPTIONAL_LOCKS': '0', 'GIT_NO_REPLACE_OBJECTS': '1'}
    def git(*args):
        result = subprocess.run(['git', '--no-replace-objects', '-c', 'core.fsmonitor=false', '-C', str(ROOT), *args],
                                env=env, capture_output=True, timeout=20)
        require(result.returncode == 0, 'HML_SOURCE_CHECK_FAILED')
        return result.stdout
    require(git('rev-parse', 'HEAD').decode().strip() == expected_sha
            and git('rev-parse', 'HEAD^{tree}').decode().strip() == expected_tree
            and git('status', '--porcelain', '--untracked-files=all') == b'', 'HML_FROZEN_SOURCE_REQUIRED')


def artifact(bundle, expected_candidate, expected_sha, expected_tree, java_image, output):
    bundle = Path(bundle)
    require(bundle.is_absolute() and bundle.resolve() == bundle and bundle.is_dir()
            and {p.name for p in bundle.iterdir()} == BUNDLE_FILES, 'HML_EXACT_BUNDLE_REQUIRED')
    inputs = {name: read(bundle / name) for name in BUNDLE_FILES}
    candidate = validate_inputs(inputs['candidate.json'], inputs['gate.json'], inputs['smoke.json'],
                                inputs['backend.jar'], expected_candidate, expected_sha, expected_tree)
    image(java_image, 'eclipse-temurin')
    source_checkout(expected_sha, expected_tree)
    payload = {'context/backend.jar': inputs['backend.jar']}
    for name in TEMPLATES:
        payload['context/' + name] = read(HERE / name, 64000)
    for name in ('stack.json', 'db-init.sh'):
        payload['recipe/' + name] = read(HERE / name, 64000)
    payload.update({'evidence/' + name: inputs[name] for name in BUNDLE_FILES - {'backend.jar'}})
    manifest = {'schemaVersion': 1, 'profile': PROFILE, 'status': 'PREPARED_FOR_REVIEW', 'releaseEligible': False,
                'ciProvenanceExternallyVerified': False, 'source': {'sha': expected_sha, 'tree': expected_tree},
                'sourceMatrixSha256': sha(encode(candidate['source']['files'])), 'javaImage': java_image,
                'jar': candidate['jar'], 'candidateSha256': expected_candidate,
                'files': {name: {'sha256': sha(raw), 'bytes': len(raw)} for name, raw in payload.items()}}
    output = new_directory(output)
    try:
        (output / 'context').mkdir(mode=0o700); (output / 'evidence').mkdir(mode=0o700); (output / 'recipe').mkdir(mode=0o700)
        for name, raw in payload.items():
            write_new(output / name, raw)
        write_new(output / 'artifact.json', encode(manifest))
        source_checkout(expected_sha, expected_tree)
    except Exception:
        shutil.rmtree(output)
        raise
    return manifest


def validated_artifact(directory, expected):
    directory = Path(directory)
    require(directory.is_absolute() and directory.resolve() == directory and directory.is_dir()
            and {p.name for p in directory.iterdir()} == {'artifact.json', 'context', 'evidence', 'recipe'}, 'HML_ARTIFACT_INVALID')
    raw = read(directory / 'artifact.json', M.MAX_JSON_BYTES)
    require(M.sha(expected) and sha(raw) == expected, 'HML_ARTIFACT_CHANGED')
    value = M.object_json(raw)
    require(M.exact(value, ('schemaVersion', 'profile', 'status', 'releaseEligible', 'ciProvenanceExternallyVerified',
                           'source', 'sourceMatrixSha256', 'javaImage', 'jar', 'candidateSha256', 'files'))
            and value['schemaVersion'] == 1 and value['profile'] == PROFILE and value['status'] == 'PREPARED_FOR_REVIEW'
            and value['releaseEligible'] is False and value['ciProvenanceExternallyVerified'] is False,
            'HML_ARTIFACT_INVALID')
    require(M.exact(value['source'], ('sha', 'tree')), 'HML_ARTIFACT_INVALID')
    image(value['javaImage'], 'eclipse-temurin')
    names = {'context/backend.jar', 'context/Dockerfile', 'context/entrypoint.sh',
             'evidence/candidate.json', 'evidence/gate.json', 'evidence/smoke.json', 'recipe/stack.json', 'recipe/db-init.sh'}
    require(isinstance(value['files'], dict) and set(value['files']) == names
            and {p.name for p in (directory / 'context').iterdir()} == {'backend.jar', *TEMPLATES}
            and {p.name for p in (directory / 'evidence').iterdir()} == BUNDLE_FILES - {'backend.jar'},
            'HML_ARTIFACT_INVALID')
    contents = {}
    for name in names:
        raw = read(directory / name)
        require(value['files'][name] == {'sha256': sha(raw), 'bytes': len(raw)}, 'HML_ARTIFACT_FILE_CHANGED')
        contents[name] = raw
    candidate = validate_inputs(contents['evidence/candidate.json'], contents['evidence/gate.json'],
                               contents['evidence/smoke.json'], contents['context/backend.jar'],
                               value['candidateSha256'], value['source']['sha'], value['source']['tree'])
    require(candidate['jar'] == value['jar'] and value['sourceMatrixSha256'] == sha(encode(candidate['source']['files'])),
            'HML_ARTIFACT_CHANGED')
    source_checkout(value['source']['sha'], value['source']['tree'])
    for name in ('stack.json', 'db-init.sh'):
        require(contents['recipe/' + name] == read(HERE / name, 64000), 'HML_STACK_RECIPE_CHANGED')
    require({p.name for p in (directory / 'recipe').iterdir()} == {'stack.json', 'db-init.sh'}, 'HML_ARTIFACT_INVALID')
    for name in TEMPLATES:
        require(contents['context/' + name] == read(HERE / name, 64000), 'HML_RUNTIME_RECIPE_CHANGED')
    return value


def verify_image(directory, expected, api_image, output, run=None):
    value = validated_artifact(directory, expected); image(api_image, API_REPOSITORY)
    run = local_docker() if run is None else run
    # Deliberately inspect selected fields only, never the image/container environment.
    template = '{"id":{{json .Id}},"digests":{{json .RepoDigests}},"os":{{json .Os}},"arch":{{json .Architecture}},' \
               '"user":{{json .Config.User}},"entrypoint":{{json .Config.Entrypoint}},"labels":{{json .Config.Labels}}}'
    observed = M.object_json(run(['image', 'inspect', '--format', template, api_image]))
    require(api_image in observed['digests'] and observed['os'] == 'linux' and observed['arch'] == 'amd64'
            and observed['user'] == '10001:10001' and observed['entrypoint'] == ['/app/entrypoint.sh'], 'HML_IMAGE_POLICY_CHANGED')
    require(isinstance(observed['id'], str) and re.fullmatch('sha256:[a-f0-9]{64}', observed['id']), 'HML_IMAGE_ID_INVALID')
    labels = observed['labels']
    require(labels.get('org.opencontainers.image.revision') == value['source']['sha']
            and labels.get('dev.vanrides.source-tree') == value['source']['tree']
            and labels.get('dev.vanrides.jar-sha256') == value['jar']['sha256']
            and labels.get('dev.vanrides.environment') == ENVIRONMENT, 'HML_IMAGE_LABELS_CHANGED')
    with tempfile.TemporaryDirectory(prefix='vanrides-hml-image-') as folder:
        with OwnedContainer(run, 'image-check', api_image) as owned:
            container = owned.create(['--network', 'none', '--pull', 'never', '--entrypoint', '/bin/true'])
            for source, name, expected_hash in (('/app/app.jar', 'app.jar', value['jar']['sha256']),
                    ('/app/entrypoint.sh', 'entrypoint.sh', value['files']['context/entrypoint.sh']['sha256'])):
                target = Path(folder).resolve() / name
                run(['cp', container + ':' + source, str(target)])
                extracted = read(target)
                require(sha(extracted) == expected_hash, 'HML_IMAGE_CONTENT_CHANGED')
                if name == 'app.jar':
                    require(len(extracted) == value['jar']['bytes'], 'HML_IMAGE_CONTENT_CHANGED')
    receipt = {'schemaVersion': 1, 'profile': 'VANRIDES_TEAM_API_IMAGE_V1', 'artifactSha256': expected,
               'apiImage': api_image, 'imageId': observed['id'], 'source': value['source'], 'jar': value['jar'],
               'platform': 'linux/amd64', 'filesystemVerified': True, 'releaseEligible': False}
    output = Path(output)
    parent = new_directory(output)
    write_new(parent / 'image.json', encode(receipt))
    return receipt


def timestamp(value):
    try:
        require(isinstance(value, str) and re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ', value), 'HML_EXPIRY_INVALID')
        parsed = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
        require(dt.datetime.now(dt.timezone.utc) < parsed <= dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=30),
                'HML_EXPIRY_INVALID')
    except (ValueError, TypeError):
        raise Blocked('HML_EXPIRY_INVALID') from None
    return value


def cidrs(values):
    result = []
    for item in values:
        try:
            network = ipaddress.ip_network(item, strict=True)
        except ValueError:
            raise Blocked('HML_CIDR_INVALID') from None
        require(network.prefixlen >= (24 if network.version == 4 else 64)
                and not network.is_unspecified and not network.is_multicast, 'HML_CIDR_TOO_BROAD')
        result.append(str(network))
    require(result and len(result) == len(set(result)) and len(result) <= 16, 'HML_CIDRS_REQUIRED')
    return sorted(result)


def ingress_labels(mode, ranges):
    require(mode in ('closed', 'health', 'team'), 'HML_INGRESS_INVALID')
    labels = {'dev.vanrides.environment': ENVIRONMENT, 'dev.vanrides.stack': STACK, 'traefik.enable': 'false'}
    if mode == 'closed':
        require(not ranges, 'HML_UNEXPECTED_CIDR')
        return labels
    labels.update({'traefik.enable': 'true', 'traefik.docker.network': 'network_swarm_public',
                   'traefik.http.services.vanrides-hml-api-http.loadbalancer.server.port': '8080'})
    tick = chr(96)
    host = 'Host(' + tick + HOST + tick + ')'
    def route(name, rule):
        key = 'traefik.http.routers.vanrides-hml-api-' + name
        labels.update({key + '.rule': host + ' && (' + rule + ')', key + '.entrypoints': 'websecure',
                       key + '.tls': 'true', key + '.tls.certresolver': 'letsencryptresolver',
                       key + '.service': 'vanrides-hml-api-http'})
    def arg(name, value):
        return name + '(' + tick + value + tick + ')'
    route('health', arg('Method', 'GET') + ' && ' + arg('Path', '/actuator/health'))
    if mode == 'team':
        paths = ' || '.join(arg('Path', '/integrations/whatsapp/v1/' + suffix)
                           for suffix in ('inbox/claim', 'commands'))
        result = arg('PathRegexp', '^/integrations/whatsapp/v1/inbox/' + UUID_PATTERN + '/result$')
        rules = '(' + arg('Method', 'POST') + ' && (' + paths + ' || ' + result + ')) || (' \
                + arg('Method', 'GET') + ' && ' + arg('Path', '/integrations/whatsapp/v1/context') + ')'
        route('team', rules)
        labels['traefik.http.routers.vanrides-hml-api-team.middlewares'] = 'vanrides-hml-api-team-ips'
        labels['traefik.http.middlewares.vanrides-hml-api-team-ips.ipallowlist.sourcerange'] = ','.join(cidrs(ranges))
    else:
        require(not ranges, 'HML_UNEXPECTED_CIDR')
    return labels


def validate_stack(stack, mode, ranges, expires, secret_version, release, config_file):
    base = obj(HERE / 'stack.json')
    require(set(stack) == set(base) and set(stack['services']) == {'api', 'db'}, 'HML_STACK_INVALID')
    for service in ('api', 'db'):
        expected = copy.deepcopy(base['services'][service])
        actual = stack['services'][service]
        expected['image'] = actual['image']
        image(actual['image'], API_REPOSITORY if service == 'api' else 'postgres')
        if service == 'api':
            expected['environment']['WA_OPERATOR_EXPIRES_AT'] = expires
            expected['deploy']['labels'] = ingress_labels(mode, ranges)
            if mode != 'closed':
                expected['networks'].append('public')
        require(actual == expected, 'HML_SERVICE_POLICY_CHANGED')
    networks = base['networks']
    if mode != 'closed':
        networks['public'] = {'external': True, 'name': 'network_swarm_public'}
    require(stack['networks'] == networks and stack['volumes'] == base['volumes'], 'HML_RESOURCE_POLICY_CHANGED')
    secrets = base['secrets']
    for item in secrets.values():
        item['name'] = re.sub('-v1$', '-' + secret_version, item['name'])
    require(stack['secrets'] == secrets, 'HML_SECRET_SCOPE_CHANGED')
    base['configs']['db_init'].update(name='vanrides-hml-api-db-init-' + release, file=str(config_file))
    require(stack['configs'] == base['configs'], 'HML_CONFIG_SCOPE_CHANGED')


def render(directory, expected, api_image, postgres_image, expires, output, mode='closed', ranges=(),
           image_receipt=None, expected_image_receipt=None, secret_version='v1'):
    artifact_value = validated_artifact(directory, expected)
    image(api_image, API_REPOSITORY); image(postgres_image, 'postgres'); timestamp(expires)
    require(re.fullmatch('v[1-9][0-9]{0,3}', secret_version), 'HML_SECRET_VERSION_INVALID')
    require((image_receipt is None) == (expected_image_receipt is None), 'HML_IMAGE_RECEIPT_REQUIRED')
    if image_receipt is not None:
        raw = read(image_receipt, 32000)
        require(M.sha(expected_image_receipt) and sha(raw) == expected_image_receipt, 'HML_IMAGE_RECEIPT_CHANGED')
        receipt = M.object_json(raw)
        require(M.exact(receipt, ('schemaVersion', 'profile', 'artifactSha256', 'apiImage', 'imageId', 'source', 'jar',
                                 'platform', 'filesystemVerified', 'releaseEligible'))
                and receipt['schemaVersion'] == 1 and receipt['profile'] == 'VANRIDES_TEAM_API_IMAGE_V1'
                and receipt['artifactSha256'] == expected and receipt['apiImage'] == api_image
                and receipt['source'] == artifact_value['source'] and receipt['jar'] == artifact_value['jar']
                and receipt['platform'] == 'linux/amd64' and receipt['filesystemVerified'] is True
                and receipt['releaseEligible'] is False and re.fullmatch('sha256:[a-f0-9]{64}', receipt['imageId']),
                'HML_IMAGE_RECEIPT_INVALID')
    require(mode == 'closed' or image_receipt is not None, 'HML_IMAGE_PROOF_REQUIRED_BEFORE_INGRESS')
    stack = obj(HERE / 'stack.json')
    stack['services']['api']['image'] = api_image
    stack['services']['db']['image'] = postgres_image
    stack['services']['api']['environment']['WA_OPERATOR_EXPIRES_AT'] = expires
    stack['services']['api']['deploy']['labels'] = ingress_labels(mode, ranges)
    if mode != 'closed':
        stack['networks']['public'] = {'external': True, 'name': 'network_swarm_public'}
        stack['services']['api']['networks'].append('public')
    for value in stack['secrets'].values():
        value['name'] = re.sub('-v1$', '-' + secret_version, value['name'])
    release = artifact_value['source']['sha'][:12]
    stack['configs']['db_init']['name'] = 'vanrides-hml-api-db-init-' + release
    output = new_directory(output)
    try:
        # Absolute config input avoids depending on the caller's working directory.
        stack['configs']['db_init']['file'] = str(output / 'db-init.sh')
        validate_stack(stack, mode, ranges, expires, secret_version, release, output / 'db-init.sh')
        raw = read(HERE / 'db-init.sh', 64000)
        write_new(output / 'db-init.sh', raw)
        write_new(output / 'stack.json', encode(stack))
        plan = {'schemaVersion': 1, 'profile': 'VANRIDES_TEAM_API_STACK_V1', 'status': 'PREPARED_FOR_REVIEW',
                'releaseEligible': False, 'artifactSha256': expected, 'source': artifact_value['source'],
                'jar': artifact_value['jar'], 'apiImage': api_image, 'postgresImage': postgres_image,
                'host': HOST, 'stack': STACK, 'ingress': mode, 'imageFilesystemVerified': image_receipt is not None,
                'imageReceiptSha256': expected_image_receipt, 'operatorExpiresAt': expires,
                'secretVersion': secret_version, 'allowCidrs': cidrs(ranges) if mode == 'team' else [],
                'files': {'stack.json': sha(encode(stack)), 'db-init.sh': sha(raw)}}
        write_new(output / 'plan.json', encode(plan))
    except Exception:
        shutil.rmtree(output)
        raise
    return plan


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='action', required=True)
    p = subs.add_parser('artifact')
    for arg in ('bundle', 'expected-candidate-sha256', 'expected-source-sha', 'expected-source-tree', 'java-image', 'output'):
        p.add_argument('--' + arg, required=True)
    for action in ('image', 'render'):
        p = subs.add_parser(action)
        p.add_argument('--artifact', required=True); p.add_argument('--expected-artifact-sha256', required=True)
        p.add_argument('--api-image', required=True); p.add_argument('--output', required=True)
        if action == 'render':
            p.add_argument('--postgres-image', required=True); p.add_argument('--operator-expires-at', required=True)
            p.add_argument('--ingress', choices=('closed', 'health', 'team'), default='closed')
            p.add_argument('--allow-cidr', action='append', default=[])
            p.add_argument('--image-receipt'); p.add_argument('--expected-image-receipt-sha256')
            p.add_argument('--secret-version', default='v1')
    args = parser.parse_args()
    try:
        if args.action == 'artifact':
            artifact(args.bundle, args.expected_candidate_sha256, args.expected_source_sha,
                     args.expected_source_tree, args.java_image, args.output)
        elif args.action == 'image':
            verify_image(args.artifact, args.expected_artifact_sha256, args.api_image, args.output)
        else:
            render(args.artifact, args.expected_artifact_sha256, args.api_image, args.postgres_image,
                   args.operator_expires_at, args.output, args.ingress, args.allow_cidr,
                   args.image_receipt, args.expected_image_receipt_sha256, args.secret_version)
        print('HML_PREPARED_NO_RELEASE_APPROVAL')
        return 0
    except (Terminated, KeyboardInterrupt):
        print('HML_PREPARATION_CANCELLED', file=sys.stderr)
        return 130
    except (Blocked, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        print('HML_PREPARATION_BLOCKED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
