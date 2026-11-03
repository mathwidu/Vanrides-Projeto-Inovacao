#!/usr/bin/env python3
"""Prepare an honest single-node preview from docker-save; never build, load, push or deploy."""
import argparse
import datetime as dt
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import re
import shutil
import stat
import subprocess
import sys
import tarfile
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
ROOT = HERE.parents[1]
HOST = 'vanrides-inovacao-api.matheusduarte.dev.br'
ENVIRONMENT = 'vanrides-mvp-hml'
STACK = 'vanrides-hml-api'
PROFILE = 'ONLINE_PREVIEW_FROM_LOCAL_IMAGE'
MAX_ARCHIVE = 3 * 1024**3


def require(condition, code):
    if not condition:
        raise ValueError(code)


def digest(raw):
    return hashlib.sha256(raw).hexdigest()


def encode(value):
    return (json.dumps(value, sort_keys=True, indent=2, ensure_ascii=True) + '\n').encode()


def strict(pairs):
    result = {}
    for key, value in pairs:
        require(key not in result, 'DUPLICATE_JSON_KEY')
        result[key] = value
    return result


def json_bytes(raw):
    return json.loads(raw, object_pairs_hook=strict,
                      parse_constant=lambda value: (_ for _ in ()).throw(ValueError('INVALID_JSON')))


def path(file):
    file = Path(file)
    require(file.is_absolute() and file.parent.resolve() == file.parent
            and not file.is_symlink() and file.is_file(), 'REGULAR_ABSOLUTE_INPUT_REQUIRED')
    return file


def read(file, maximum=2 * 1024**2):
    file = path(file)
    fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        before = os.fstat(fd)
        require(stat.S_ISREG(before.st_mode) and 0 < before.st_size <= maximum, 'INPUT_SIZE_INVALID')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            raw = stream.read(maximum + 1)
        after = os.fstat(fd)
        require(len(raw) == before.st_size and all(getattr(before, key) == getattr(after, key)
                for key in ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')), 'INPUT_CHANGED')
        return raw
    finally:
        os.close(fd)


def file_digest(file, maximum=MAX_ARCHIVE):
    file = path(file)
    require(0 < file.stat().st_size <= maximum, 'INPUT_SIZE_INVALID')
    h = hashlib.sha256()
    before = file.stat()
    with file.open('rb') as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b''):
            h.update(block)
    after = file.stat()
    require(all(getattr(before, key) == getattr(after, key)
                for key in ('st_dev', 'st_ino', 'st_size', 'st_mtime_ns', 'st_ctime_ns')), 'INPUT_CHANGED')
    return h.hexdigest(), before.st_size


def sha(value, length=64):
    require(isinstance(value, str) and re.fullmatch('[a-f0-9]{' + str(length) + '}', value), 'HASH_INVALID')
    return value


def image_id(value):
    require(isinstance(value, str) and re.fullmatch('sha256:[a-f0-9]{64}', value), 'IMAGE_ID_REQUIRED')
    return value


def image_ref(value):
    require(isinstance(value, str) and '..' not in value and '//' not in value, 'NOMINAL_IMAGE_REFERENCE_REQUIRED')
    by_digest = re.fullmatch('[a-z0-9][a-z0-9._/-]*@sha256:[a-f0-9]{64}', value)
    by_tag = re.fullmatch('[a-z0-9][a-z0-9._/-]*:[a-z0-9][a-z0-9._-]{7,127}', value)
    require(by_digest or by_tag and not value.startswith('sha256:'), 'NOMINAL_IMAGE_REFERENCE_REQUIRED')
    require(not value.endswith((':latest', ':main', ':dev')), 'EXCLUSIVE_LOCAL_REFERENCE_REQUIRED')
    return 'NOMINAL_DIGEST_PENDING_TARGET_INSPECTION' if by_digest else 'EXCLUSIVE_LOCAL_TAG_VERIFIED_BY_IMAGE_ID'


def safe_tar_name(name):
    require(isinstance(name, str) and not name.startswith('/') and '\\' not in name
            and '..' not in PurePosixPath(name).parts, 'ARCHIVE_PATH_INVALID')
    while name.startswith('./'):
        name = name[2:]
    return name.rstrip('/')


def inspect_archive(archive, expected_archive, expected_id, reference, jar_hash, jar_bytes, source_sha, source_tree):
    sha(expected_archive); image_id(expected_id)
    actual_hash, archive_bytes = file_digest(archive)
    require(actual_hash == expected_archive, 'ARCHIVE_CHANGED')
    with tarfile.open(path(archive), 'r:*') as outer:
        members = {}
        for member in outer:
            name = safe_tar_name(member.name)
            require(name not in members and (member.isfile() or member.isdir()), 'ARCHIVE_MEMBER_INVALID')
            members[name] = member
        require('manifest.json' in members and members['manifest.json'].size <= 64000, 'DOCKER_SAVE_MANIFEST_REQUIRED')
        manifest = json_bytes(outer.extractfile(members['manifest.json']).read())
        require(isinstance(manifest, list) and len(manifest) == 1, 'ONE_IMAGE_PER_ARCHIVE_REQUIRED')
        row = manifest[0]
        config_name = safe_tar_name(row['Config'])
        require(config_name in members and members[config_name].size <= 2 * 1024**2, 'IMAGE_CONFIG_INVALID')
        config_raw = outer.extractfile(members[config_name]).read()
        config_digest = 'sha256:' + digest(config_raw)
        # With the containerd image store Docker may identify an image by its
        # manifest digest. Verify that manifest binds this exact config and layers.
        if expected_id != config_digest:
            index = json_bytes(outer.extractfile(members['index.json']).read())
            descriptors = index['manifests']
            require(len(descriptors) == 1 and descriptors[0]['digest'] == expected_id,
                    'ARCHIVE_IMAGE_ID_CHANGED')
            oci_raw = outer.extractfile(members['blobs/sha256/' + expected_id[7:]]).read()
            require('sha256:' + digest(oci_raw) == expected_id, 'ARCHIVE_MANIFEST_CHANGED')
            oci = json_bytes(oci_raw)
            require(oci['config']['digest'] == config_digest and
                    ['blobs/' + layer['digest'].replace(':', '/') for layer in oci['layers']] == row['Layers'],
                    'ARCHIVE_MANIFEST_CONTENT_CHANGED')
        config = json_bytes(config_raw)
        require(config.get('os') == 'linux' and config.get('architecture') == 'amd64'
                and config.get('config', {}).get('User') == '10001:10001', 'IMAGE_PLATFORM_OR_USER_INVALID')
        labels = config.get('config', {}).get('Labels', {})
        require(labels.get('org.opencontainers.image.revision') == source_sha
                and labels.get('dev.vanrides.source-tree') == source_tree
                and labels.get('dev.vanrides.jar-sha256') == jar_hash, 'IMAGE_SOURCE_LABELS_CHANGED')
        if '@sha256:' not in reference:
            require(reference in (row.get('RepoTags') or []), 'LOCAL_TAG_NOT_SAVED_IN_ARCHIVE')
        require(isinstance(row.get('Layers'), list) and row['Layers'], 'IMAGE_LAYERS_INVALID')
        jar_found = False
        # The newest rootfs layer wins. Never extract archive members onto the host.
        for layer_name in reversed(row['Layers']):
            layer_name = safe_tar_name(layer_name)
            require(layer_name in members and members[layer_name].isfile(), 'IMAGE_LAYER_MISSING')
            opaque = False
            with outer.extractfile(members[layer_name]) as raw_layer, tarfile.open(fileobj=raw_layer, mode='r|*') as layer:
                for member in layer:
                    name = safe_tar_name(member.name)
                    if name in ('app/.wh.app.jar', '.wh.app', 'app/.wh..wh..opq'):
                        opaque = True
                    if name == 'app' and (member.issym() or member.islnk()):
                        raise ValueError('IMAGE_APP_PATH_INDIRECT')
                    if name != 'app/app.jar':
                        continue
                    require(member.isfile() and member.size == jar_bytes, 'IMAGE_JAR_CHANGED')
                    h = hashlib.sha256()
                    with layer.extractfile(member) as stream:
                        for chunk in iter(lambda: stream.read(1024 * 1024), b''):
                            h.update(chunk)
                    require(h.hexdigest() == jar_hash, 'IMAGE_JAR_CHANGED')
                    jar_found = True
            if jar_found:
                break
            require(not opaque, 'IMAGE_JAR_REMOVED')
        require(jar_found, 'IMAGE_JAR_NOT_FOUND')
    require(file_digest(archive) == (actual_hash, archive_bytes), 'ARCHIVE_CHANGED')
    return archive_bytes


def source_snapshot(expected_sha, expected_tree):
    def git(*args):
        result = subprocess.run(['git', '-c', 'core.fsmonitor=false', '-C', str(ROOT), *args],
                                capture_output=True, timeout=20,
                                env={**os.environ, 'GIT_OPTIONAL_LOCKS': '0', 'GIT_NO_REPLACE_OBJECTS': '1'})
        require(result.returncode == 0, 'SOURCE_GIT_FAILED')
        return result.stdout
    require(git('rev-parse', 'HEAD').decode().strip() == expected_sha
            and git('rev-parse', 'HEAD^{tree}').decode().strip() == expected_tree, 'SOURCE_HEAD_CHANGED')
    status = git('status', '--porcelain', '--untracked-files=all')
    diff = git('diff', '--binary', 'HEAD', '--', 'src/backend', 'deploy/homologacao')
    return {'gitSha': expected_sha, 'gitTree': expected_tree, 'workingTreeDirty': bool(status),
            'trackedBackendAndRecipeDiffSha256': digest(diff),
            'binaryBuildAttestation': 'OPERATOR_DECLARATION_AND_IMAGE_LABELS_NOT_CI'}


def utc_expiry(value):
    require(isinstance(value, str) and re.fullmatch(r'\d{4}-\d\d-\d\dT\d\d:\d\d:\d\dZ', value), 'EXPIRY_INVALID')
    expires = dt.datetime.fromisoformat(value.replace('Z', '+00:00'))
    require(dt.datetime.now(dt.timezone.utc) < expires <= dt.datetime.now(dt.timezone.utc) + dt.timedelta(days=30),
            'EXPIRY_INVALID')
    return value


def output_directory(output):
    output = Path(output)
    require(output.is_absolute() and output.parent.resolve() == output.parent and output.parent.is_dir()
            and not output.exists() and not output.is_symlink(), 'NEW_OUTPUT_REQUIRED')
    require(not any((parent / '.git').exists() for parent in (output.parent, *output.parent.parents)), 'OUTPUT_MUST_BE_OUTSIDE_GIT')
    output.mkdir(mode=0o700)
    return output


def write_new(file, raw):
    fd = os.open(file, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    with os.fdopen(fd, 'wb') as stream:
        stream.write(raw); stream.flush(); os.fsync(stream.fileno())


def online_entrypoint():
    raw = read(HERE / 'entrypoint.sh', 64000).decode()
    require(raw.count('WA_TEAM_API_ONLY') == 1 and raw.count('vanrides-team-hml') == 1, 'BASE_ENTRYPOINT_CHANGED')
    lines = []
    for line in raw.splitlines():
        if 'WA_TEAM_API_ONLY' in line:
            line = line.replace('= true', '= false')
            lines.append(line)
            lines.append('[ "' + chr(36) + '{WA_MVP_ONLINE:-}" = true ] || fail')
            continue
        lines.append(line.replace('vanrides-team-hml', ENVIRONMENT))
    return ('\n'.join(lines) + '\n').encode()


def render(args):
    sha(args.source_sha, 40); sha(args.source_tree, 40); sha(args.jar_sha256)
    reference_kind = image_ref(args.api_image_ref); image_id(args.api_image_id); utc_expiry(args.expires_at)
    require(re.fullmatch('postgres@sha256:[a-f0-9]{64}', args.postgres_image), 'POSTGRES_DIGEST_REQUIRED')
    jar_hash, jar_bytes = file_digest(args.jar, 128 * 1024**2)
    require(jar_hash == args.jar_sha256, 'JAR_INPUT_CHANGED')
    source = source_snapshot(args.source_sha, args.source_tree)
    archive_bytes = inspect_archive(args.api_archive, args.api_archive_sha256, args.api_image_id,
                                   args.api_image_ref, jar_hash, jar_bytes, args.source_sha, args.source_tree)
    target = Path(args.target_directory)
    require(target.is_absolute() and '..' not in target.parts and str(target).startswith('/home/mathwidu/.local/share/vanrides/')
            and re.fullmatch('[A-Za-z0-9_./-]+', str(target)), 'TARGET_DIRECTORY_INVALID')
    stack_raw, init_raw = read(HERE / 'stack.json'), read(HERE / 'db-init.sh', 64000)
    stack = json_bytes(stack_raw)
    require(set(stack['services']) == {'api', 'db'}, 'BASE_STACK_CHANGED')
    api, db = stack['services']['api'], stack['services']['db']
    api['image'], db['image'] = args.api_image_ref, args.postgres_image
    api['entrypoint'] = ['/bin/sh', '/run/online/entrypoint.sh']
    api['environment'].update({
        'WA_MVP_ONLINE': 'true', 'WA_TEAM_API_ONLY': 'false', 'WA_MVP_ENABLED': 'true',
        'WA_NEW_STARTS_ENABLED': 'true', 'WA_APPROACH_ENABLED': 'false', 'WA_TEST_COHORT_ONLY': 'true',
        'WA_ENVIRONMENT': ENVIRONMENT, 'WA_PUBLIC_BASE_URL': 'https://' + HOST,
        'WA_START_LINK_BASE_URL': 'https://' + HOST, 'WA_APPROACH_REVIEWED_ROUTINE_IDS': '',
        'WA_OPERATOR_EXPIRES_AT': args.expires_at, 'VANRIDES_JAR_SHA256': jar_hash,
        'SPRING_PROFILES_ACTIVE': 'homolog', 'VANRIDES_EMAIL_PROVIDER': 'disabled',
        'VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL': '', 'VANRIDES_EMAIL_RESEND_API_KEY': '',
        'GOOGLE_API_KEY': '', 'OPENAI_API_KEY': '', 'SPRING_MAIL_HOST': '', 'SPRING_MAIL_USERNAME': '', 'SPRING_MAIL_PASSWORD': ''})
    # Exact Host takes every path/method so unknown requests cannot fall through
    # to the catch-all of an unrelated project. The backend's online allowlist denies them.
    tick = chr(96)
    api['deploy']['labels'] = {
        'dev.vanrides.environment': ENVIRONMENT, 'dev.vanrides.stack': STACK,
        'traefik.enable': 'true', 'traefik.docker.network': 'network_swarm_public',
        'traefik.http.services.vanrides-hml-api-http.loadbalancer.server.port': '8080',
        'traefik.http.routers.vanrides-hml-api-online.rule': 'Host(' + tick + HOST + tick + ')',
        'traefik.http.routers.vanrides-hml-api-online.priority': '10000',
        'traefik.http.routers.vanrides-hml-api-online.entrypoints': 'websecure',
        'traefik.http.routers.vanrides-hml-api-online.tls': 'true',
        'traefik.http.routers.vanrides-hml-api-online.tls.certresolver': 'letsencryptresolver',
        'traefik.http.routers.vanrides-hml-api-online.service': 'vanrides-hml-api-http'}
    api['networks'] = ['private', 'public']
    stack['networks']['public'] = {'external': True, 'name': 'network_swarm_public'}
    for service in (api, db):
        service['labels']['dev.vanrides.environment'] = ENVIRONMENT
        service['deploy']['labels']['dev.vanrides.environment'] = ENVIRONMENT
        require(not service.get('ports') and service['deploy']['replicas'] == 1
                and service['deploy']['placement']['constraints'] == ['node.hostname == srv1397065'], 'SINGLE_NODE_STACK_REQUIRED')
    release = args.source_sha[:12] + '-' + jar_hash[:12]
    stack['configs']['db_init'] = {'name': STACK + '-db-init-' + release, 'file': str(target / 'db-init.sh')}
    stack['configs']['online_entrypoint'] = {'name': STACK + '-online-entrypoint-' + release,
                                           'file': str(target / 'entrypoint-online.sh')}
    api['configs'] = [{'source': 'online_entrypoint', 'target': '/run/online/entrypoint.sh', 'uid': '10001', 'gid': '10001', 'mode': 365}]
    require(db['networks'] == ['private'] and api['environment']['SPRING_DATASOURCE_URL']
            == 'jdbc:postgresql://db:5432/vanrides_hml_api', 'DATABASE_ISOLATION_CHANGED')
    output = output_directory(args.output)
    payload = {'stack.online.json': encode(stack), 'entrypoint-online.sh': online_entrypoint(),
               'db-init.sh': init_raw, 'online.py': read(HERE / 'online.py')}
    payload['api.env'] = ''.join(name + '=' + value + '\n' for name, value in sorted(api['environment'].items())).encode()
    try:
        for name, raw in payload.items():
            write_new(output / name, raw)
        with path(args.api_archive).open('rb') as original:
            fd = os.open(output / 'api-image.tar', os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
            with os.fdopen(fd, 'wb') as destination:
                shutil.copyfileobj(original, destination, 1024 * 1024); destination.flush(); os.fsync(destination.fileno())
        require(file_digest(output / 'api-image.tar') == (args.api_archive_sha256, archive_bytes), 'ARCHIVE_COPY_CHANGED')
        require(source_snapshot(args.source_sha, args.source_tree) == source, 'SOURCE_CHANGED_DURING_RENDER')
        manifest = {
            'schemaVersion': 1, 'profile': PROFILE, 'status': 'PREPARED_NOT_PUBLISHED',
            'releaseEligible': False, 'registryPublished': False, 'ciReexecuted': False,
            'testsRunByThisTool': False, 'source': source, 'jar': {'sha256': jar_hash, 'bytes': jar_bytes},
            'apiImage': {'reference': args.api_image_ref, 'imageId': args.api_image_id, 'referenceKind': reference_kind,
                         'archive': {'file': 'api-image.tar', 'sha256': args.api_archive_sha256, 'bytes': archive_bytes}},
            'postgresImage': args.postgres_image, 'platform': 'linux/amd64', 'distribution': 'SINGLE_NODE_DOCKER_LOAD',
            'environment': ENVIRONMENT, 'stack': STACK, 'host': HOST, 'targetDirectory': str(target),
            'expiresAt': args.expires_at, 'expiryAutomaticallyStopsService': False,
            'recipe': {'baseStackSha256': digest(stack_raw), 'dbInitSha256': digest(init_raw),
                       'onlineRendererSha256': digest(payload['online.py'])},
            'files': {name: {'sha256': digest(raw), 'bytes': len(raw)} for name, raw in payload.items()},
            'limits': ['NO_NEW_CI_OR_REGISTRY_PUBLICATION', 'SINGLE_NODE_LOCAL_IMAGE_REQUIRED',
                       'HTTP_TLS_AUTH_AND_RUNTIME_IMAGE_VERIFICATION_PENDING', 'REAL_WHATSAPP_NOT_CONNECTED',
                       'FRESH_TEST_COHORT_AND_DEVICE_PAIRING_REQUIRED']}
        manifest['files']['api-image.tar'] = manifest['apiImage']['archive'].copy()
        manifest['files']['api-image.tar'].pop('file')
        write_new(output / 'online.json', encode(manifest))
    except BaseException:
        shutil.rmtree(output)
        raise
    return manifest


def docker_reader():
    require(not os.environ.get('DOCKER_HOST') and not os.environ.get('DOCKER_CONTEXT'), 'LOCAL_DOCKER_ENDPOINT_REQUIRED')
    env = {key: value for key, value in os.environ.items() if key not in
           ('DOCKER_HOST', 'DOCKER_CONTEXT', 'DOCKER_TLS_VERIFY', 'DOCKER_CERT_PATH')}
    def command(args):
        result = subprocess.run(args, capture_output=True, timeout=30, env=env)
        require(result.returncode == 0 and len(result.stdout) <= 1000000, 'DOCKER_READ_FAILED')
        return result.stdout
    endpoint = json_bytes(command(['docker', 'context', 'inspect', '--format', '{{json .Endpoints.docker}}']))['Host']
    uri = urlsplit(endpoint)
    require(uri.scheme == 'unix' and not uri.netloc and uri.path.startswith('/') and not uri.query and not uri.fragment,
            'LOCAL_DOCKER_ENDPOINT_REQUIRED')
    return lambda args: command(['docker', '--host', endpoint, *args])


def verify_local(args):
    directory = Path(args.bundle)
    raw = read(directory / 'online.json')
    require(digest(raw) == sha(args.expected_manifest_sha256), 'MANIFEST_CHANGED')
    manifest = json_bytes(raw)
    require(manifest['profile'] == PROFILE and manifest['status'] == 'PREPARED_NOT_PUBLISHED'
            and manifest['registryPublished'] is False and manifest['releaseEligible'] is False
            and manifest['stack'] == STACK and manifest['environment'] == ENVIRONMENT
            and manifest['host'] == HOST and str(directory) == manifest['targetDirectory'], 'PREVIEW_MANIFEST_INVALID')
    require(set(p.name for p in directory.iterdir()) == {'online.json', *manifest['files']}, 'EXACT_BUNDLE_REQUIRED')
    for name, expected in manifest['files'].items():
        require(name in ('stack.online.json', 'entrypoint-online.sh', 'db-init.sh', 'online.py', 'api.env', 'api-image.tar'),
                'BUNDLE_PATH_INVALID')
        require(file_digest(directory / name) == (expected['sha256'], expected['bytes']), 'BUNDLE_FILE_CHANGED')
    utc_expiry(manifest['expiresAt'])
    run = docker_reader()
    identity = run(['info', '--format', '{{.Name}}|{{.Swarm.LocalNodeState}}|{{.Swarm.ControlAvailable}}']).decode().strip()
    require(identity == 'srv1397065|active|true', 'TARGET_MANAGER_IDENTITY_INVALID')
    nodes = run(['node', 'ls', '--format', '{{.ID}}']).decode().splitlines()
    require(len(nodes) == 1, 'SINGLE_NODE_DISTRIBUTION_ONLY')
    template = '{"id":{{json .Id}},"digests":{{json .RepoDigests}},"os":{{json .Os}},"arch":{{json .Architecture}}}'
    api = json_bytes(run(['image', 'inspect', '--format', template, manifest['apiImage']['reference']]))
    require(api['id'] == manifest['apiImage']['imageId'] and api['os'] == 'linux' and api['arch'] == 'amd64',
            'LOADED_API_REFERENCE_DOES_NOT_MATCH_APPROVED_IMAGE_ID')
    if '@sha256:' in manifest['apiImage']['reference']:
        require(manifest['apiImage']['reference'] in (api['digests'] or []), 'LOADED_NOMINAL_DIGEST_NOT_PRESERVED')
    postgres = json_bytes(run(['image', 'inspect', '--format', template, manifest['postgresImage']]))
    require(manifest['postgresImage'] in (postgres['digests'] or []) and postgres['os'] == 'linux'
            and postgres['arch'] == 'amd64', 'LOCAL_POSTGRES_DIGEST_UNAVAILABLE')
    if args.running:
        for name, expected_id in (('api', api['id']), ('db', postgres['id'])):
            service = STACK + '_' + name
            containers = run(['ps', '--no-trunc', '--filter', 'label=com.docker.swarm.service.name=' + service,
                              '--format', '{{.ID}}']).decode().splitlines()
            require(len(containers) == 1 and re.fullmatch('[a-f0-9]{64}', containers[0]), 'EXACTLY_ONE_RUNNING_TASK_REQUIRED')
            observed = json_bytes(run(['inspect', '--format',
                '{"imageId":{{json .Image}},"running":{{json .State.Running}},"labels":{{json .Config.Labels}}}', containers[0]]))
            require(observed['imageId'] == expected_id and observed['running'] is True
                    and observed['labels'].get('com.docker.stack.namespace') == STACK
                    and observed['labels'].get('com.docker.swarm.service.name') == service, 'RUNNING_TASK_IMAGE_ID_CHANGED')
    return {'profile': PROFILE, 'status': 'RUNNING_IMAGE_IDS_MATCH' if args.running else 'LOCAL_IMAGE_REFERENCES_MATCH',
            'apiImageId': api['id'], 'postgresImageId': postgres['id'], 'publishedOrAccepted': False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    subs = parser.add_subparsers(dest='action', required=True)
    p = subs.add_parser('render')
    for name in ('api-image-ref', 'api-image-id', 'api-archive', 'api-archive-sha256', 'jar', 'jar-sha256',
                 'source-sha', 'source-tree', 'postgres-image', 'expires-at', 'target-directory', 'output'):
        p.add_argument('--' + name, required=True)
    p = subs.add_parser('verify-local')
    p.add_argument('--bundle', required=True); p.add_argument('--expected-manifest-sha256', required=True)
    p.add_argument('--running', action='store_true')
    args = parser.parse_args()
    try:
        value = render(args) if args.action == 'render' else verify_local(args)
        print(json.dumps({'profile': PROFILE, 'status': value['status'], 'publishedOrAccepted': False}, separators=(',', ':')))
        return 0
    except (OSError, ValueError, KeyError, TypeError, tarfile.TarError, subprocess.SubprocessError):
        print('ONLINE_PREVIEW_PREPARATION_BLOCKED', file=sys.stderr)
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
