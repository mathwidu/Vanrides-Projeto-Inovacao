#!/usr/bin/env python3
"""Export explicit local CI evidence. No network, secrets, Docker or release approval."""
import argparse
import json
import os
from pathlib import Path
import re
import shutil
import stat
import subprocess
import sys

sys.dont_write_bytecode = True
import monorepo_receipt as M
from plan import Blocked, require, receipt_bytes, hash_file

MANIFEST_FIELDS = ('status', 'suite', 'commandProfile', 'retentionClass', 'eligibleForRelease',
                   'source', 'totals', 'reports', 'allReports', 'environment')
MATRIX_PATH = 'src/backend/scripts/qa_integration_profile.sh'
CANONICAL = ('src/whatsapp/scripts/vps110/server/protocol.py', 'src/whatsapp/scripts/vps110/server/control.py',
             'src/whatsapp/scripts/vps110/physical/stage_physical_remote.py',
             'src/whatsapp/scripts/vps110/physical/contract.json')
ENVIRONMENT_FIELDS = ('scope', 'testcontainersApiVersion', 'dockerMinApiVersion', 'dockerMaxApiVersion',
                      'dockerEndpointTransport', 'mavenRcSkipped', 'userHomeIsolated')


def read_regular(file, maximum=M.MAX_JSON_BYTES):
    file = Path(file)
    require(file.is_absolute() and file.parent.resolve() == file.parent, 'EXPORT_INPUT_PATH_INVALID')
    fd = os.open(file, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        info = os.fstat(fd)
        require(stat.S_ISREG(info.st_mode) and 0 < info.st_size <= maximum, 'EXPORT_INPUT_INVALID')
        with os.fdopen(fd, 'rb', closefd=False) as stream:
            raw = stream.read(maximum + 1)
        after = os.fstat(fd)
        fields = ('st_dev', 'st_ino', 'st_mode', 'st_uid', 'st_size', 'st_mtime_ns', 'st_ctime_ns')
        require(len(raw) == info.st_size and all(getattr(after, key) == getattr(info, key) for key in fields), 'EXPORT_INPUT_CHANGED')
        return raw
    finally:
        os.close(fd)


def output_parent(path, repository=None):
    path = Path(path)
    require(path.is_absolute() and path.parent.resolve() == path.parent
            and path.parent.is_dir() and not path.exists() and not path.is_symlink(), 'EXPORT_OUTPUT_INVALID')
    require(not any((parent / '.git').exists() for parent in (path.parent, *path.parent.parents)), 'EXPORT_OUTPUT_INSIDE_GIT')
    if repository is not None:
        require(not path.is_relative_to(repository), 'EXPORT_OUTPUT_INSIDE_GIT')
    return path


def write_new(path, raw):
    path = output_parent(path)
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
    try:
        os.fchmod(fd, 0o600)
        with os.fdopen(fd, 'wb', closefd=False) as stream:
            stream.write(raw); stream.flush(); os.fsync(fd)
        return os.fstat(fd)
    finally:
        os.close(fd)


def git(root, *args):
    env = {'PATH': os.environ.get('PATH', os.defpath), 'HOME': '/nonexistent', 'LC_ALL': 'C',
           'GIT_CONFIG_GLOBAL': os.devnull, 'GIT_CONFIG_SYSTEM': os.devnull, 'GIT_CONFIG_NOSYSTEM': '1',
           'GIT_NO_REPLACE_OBJECTS': '1', 'GIT_OPTIONAL_LOCKS': '0', 'GIT_TERMINAL_PROMPT': '0'}
    result = subprocess.run(['git', '--no-replace-objects', '-c', 'core.fsmonitor=false',
                             '-c', 'core.untrackedCache=false', '-C', str(root), *args],
                            env=env, capture_output=True, timeout=20)
    require(result.returncode == 0 and len(result.stdout) <= 2000000, 'EXPORT_GIT_FAILED')
    return result.stdout


def selected(name):
    prefixes = ('src/whatsapp/worker/', 'src/whatsapp/agent/', 'src/whatsapp/transport/', 'src/whatsapp/lab/live-ingress/')
    return (name in (*CANONICAL, MATRIX_PATH, 'src/whatsapp/lab/live-runtime/canonical110-pins.json')
            or name.startswith(prefixes) and name.endswith('.mjs') and not name.endswith('.test.mjs')
            or name.startswith('src/whatsapp/lab/live-runtime/') and name.endswith('.py') and not name.endswith('_test.py'))


def source_snapshot(repository):
    root = Path(repository)
    require(root.is_absolute() and root.resolve() == root and root.is_dir(), 'EXPORT_REPOSITORY_INVALID')
    require(git(root, 'rev-parse', '--show-toplevel').decode().strip() == str(root), 'EXPORT_REPOSITORY_INVALID')
    head, tree = (git(root, 'rev-parse', name).decode().strip() for name in ('HEAD', 'HEAD^{tree}'))
    require(git(root, 'status', '--porcelain', '--untracked-files=all') == b'', 'EXPORT_CLEAN_CHECKOUT_REQUIRED')
    files = {}
    for entry in git(root, 'ls-tree', '-rz', '--full-tree', head).split(b'\0'):
        if not entry:
            continue
        metadata, name = entry.decode('utf-8').split('\t', 1)
        if not selected(name):
            continue
        mode, kind, blob = metadata.split()
        require(mode in ('100644', '100755') and kind == 'blob' and M.safe_path(name), 'EXPORT_SOURCE_TYPE_INVALID')
        raw = read_regular(root / name)
        require(git(root, 'show', head + ':' + name) == raw, 'EXPORT_SOURCE_CHANGED')
        files[name] = {'gitBlob': blob, 'sha256': M.digest(raw), 'bytes': len(raw)}
    require(set((*CANONICAL, MATRIX_PATH)) <= set(files)
            and all(any(name.startswith(prefix) for name in files) for prefix in
                    ('src/whatsapp/worker/', 'src/whatsapp/agent/', 'src/whatsapp/transport/', 'src/whatsapp/lab/live-ingress/')),
            'EXPORT_SOURCES_MISSING')
    value = M.source({'repository': M.REPOSITORY, 'sha': head, 'tree': tree, 'files': files})
    require(git(root, 'rev-parse', 'HEAD').decode().strip() == head
            and git(root, 'rev-parse', 'HEAD^{tree}').decode().strip() == tree
            and git(root, 'status', '--porcelain', '--untracked-files=all') == b'', 'EXPORT_SOURCE_CHANGED')
    return value


def ci_context(env):
    try:
        value = {'repository': env['GITHUB_REPOSITORY'], 'sha': env['GITHUB_SHA'],
                 'runId': int(env['GITHUB_RUN_ID']), 'runAttempt': int(env['GITHUB_RUN_ATTEMPT']),
                 'workflowRef': env['GITHUB_WORKFLOW_REF'], 'event': env['GITHUB_EVENT_NAME'], 'job': env['GITHUB_JOB']}
    except (KeyError, ValueError):
        raise Blocked('MONOREPO_CI_INVALID') from None
    return M.ci(value)


def required_classes(root):
    text = read_regular(Path(root) / MATRIX_PATH).decode('utf-8')
    found = re.findall(r'^EXPECTED_TEST_CLASSES=\(\n(.*?)^\)', text, re.M | re.S)
    require(len(found) == 1, 'EXPORT_MATRIX_INVALID')
    result = {}
    for line in found[0].splitlines():
        match = re.fullmatch(r'\s*"([A-Za-z_$][A-Za-z0-9_.$]*)=([1-9][0-9]*)"\s*', line)
        require(match is not None and match[1] not in result, 'EXPORT_MATRIX_INVALID')
        result[match[1]] = int(match[2])
    require(result, 'EXPORT_MATRIX_INVALID')
    return result


def export_gate(evidence_root, repository, output, env=None):
    repository, evidence_root = Path(repository), Path(evidence_root)
    snapshot = source_snapshot(repository); origin = ci_context(os.environ if env is None else env)
    require(origin['sha'] == snapshot['sha'], 'EXPORT_CI_SOURCE_CHANGED')
    require(evidence_root.is_absolute() and evidence_root.resolve() == evidence_root and evidence_root.is_dir(), 'EXPORT_EVIDENCE_ROOT_INVALID')
    children = list(evidence_root.iterdir())
    require(len(children) == 1 and children[0].is_dir() and not children[0].is_symlink()
            and re.fullmatch(r'integration-[0-9]{8}T[0-9]{6}Z-[0-9]+', children[0].name), 'EXPORT_EXACTLY_ONE_RUN_REQUIRED')
    raw = read_regular(children[0] / 'manifest.json'); manifest = M.object_json(raw)
    require(manifest.get('schemaVersion') == 2 and manifest.get('runId') == children[0].name, 'EXPORT_MANIFEST_INVALID')
    require(all(key in manifest for key in MANIFEST_FIELDS), 'EXPORT_MANIFEST_INVALID')
    public = {key: manifest[key] for key in MANIFEST_FIELDS}
    public['environment'] = {key: manifest['environment'].get(key) for key in ENVIRONMENT_FIELDS}
    public['reports'] = [{key: row.get(key) for key in ('className', 'tests', 'sha256')} for row in manifest['reports']]
    all_reports = manifest['allReports']
    public['allReports'] = {'requiredGreen': all_reports.get('requiredGreen'), 'reportCount': all_reports.get('reportCount'),
                            'totals': all_reports.get('totals'), 'reports': [{key: row.get(key) for key in
                            ('className', 'tests', 'sha256', 'skipped', 'failures', 'errors')} for row in all_reports.get('reports', [])]}
    value = M.gate({'schemaVersion': 1, 'profile': 'MONOREPO_CANONICAL_GATE_V1', 'status': 'PASS',
                    'source': snapshot, 'ci': origin, 'manifestSha256': M.digest(raw), 'manifest': public,
                    'requiredClasses': required_classes(repository)})
    require(source_snapshot(repository) == snapshot and read_regular(children[0] / 'manifest.json') == raw, 'EXPORT_SOURCE_CHANGED')
    output_parent(Path(output), repository); write_new(Path(output), receipt_bytes(value))
    return value


def export_bundle(gate_file, smoke_file, jar, repository, output, env=None):
    repository, output, jar = Path(repository), Path(output), Path(jar).absolute()
    snapshot = source_snapshot(repository); origin = ci_context(os.environ if env is None else env)
    gate_raw, smoke_raw = read_regular(Path(gate_file)), read_regular(Path(smoke_file))
    gate_value, smoke_value = M.object_json(gate_raw), M.object_json(smoke_raw)
    require(jar.parent.resolve() == jar.parent and jar.is_file() and not jar.is_symlink(), 'EXPORT_JAR_INVALID')
    binary = M.binary({'sha256': hash_file(jar), 'bytes': jar.stat().st_size})
    value = M.candidate({'schemaVersion': 1, 'profile': M.PROFILE, 'status': 'CANDIDATE_FOR_BOUNDED_LAB',
                         'source': snapshot, 'ci': origin, 'jar': binary, 'gateSha256': M.digest(gate_raw),
                         'smokeSha256': M.digest(smoke_raw), 'releaseEligible': False}, gate_value, smoke_value)
    output_parent(output, repository); output.mkdir(mode=0o700)
    owned = {}
    identity = output.stat()
    try:
        for name, raw in (('gate.json', gate_raw), ('smoke.json', smoke_raw), ('candidate.json', receipt_bytes(value))):
            owned[name] = write_new(output / name, raw)
        target = output / 'backend.jar'
        fd = os.open(target, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600)
        owned['backend.jar'] = os.fstat(fd)
        with os.fdopen(fd, 'wb') as stream, jar.open('rb') as original:
            shutil.copyfileobj(original, stream, 262144); stream.flush(); os.fsync(stream.fileno())
        require(hash_file(target) == hash_file(jar) == binary['sha256'] and target.stat().st_size == binary['bytes']
                and source_snapshot(repository) == snapshot and read_regular(Path(gate_file)) == gate_raw
                and read_regular(Path(smoke_file)) == smoke_raw, 'EXPORT_SOURCE_CHANGED')
        return value
    except BaseException:
        for name, info in owned.items():
            path = output / name
            if path.exists() and not path.is_symlink() and (path.stat().st_dev, path.stat().st_ino) == (info.st_dev, info.st_ino):
                path.unlink()
        if (output.stat().st_dev, output.stat().st_ino) == (identity.st_dev, identity.st_ino):
            try:
                output.rmdir()
            except OSError:
                pass
        raise


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest='action', required=True)
    gate_parser = commands.add_parser('gate'); gate_parser.add_argument('--evidence-root', type=Path, required=True)
    bundle = commands.add_parser('bundle')
    for name in ('gate', 'smoke', 'jar'):
        bundle.add_argument('--' + name, type=Path, required=True)
    for sub in (gate_parser, bundle):
        sub.add_argument('--repository-root', type=Path, required=True); sub.add_argument('--output', type=Path, required=True)
    args = parser.parse_args(); os.umask(0o077)
    try:
        if args.action == 'gate':
            export_gate(args.evidence_root, args.repository_root, args.output)
        else:
            export_bundle(args.gate, args.smoke, args.jar, args.repository_root, args.output)
        print(json.dumps({'status': 'EXPORTED_FOR_BOUNDED_LAB', 'releaseEligible': False}))
        return 0
    except Exception as error:
        print(json.dumps({'status': 'BLOCKED', 'code': str(error) if isinstance(error, Blocked) else 'EXPORT_INPUT_INVALID'}))
        return 2


if __name__ == '__main__':
    sys.exit(main())
