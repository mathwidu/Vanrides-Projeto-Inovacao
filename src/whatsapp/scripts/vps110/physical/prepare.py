#!/usr/bin/env python3
"""Recover the exact v6 harness from Git and explicit external inputs, entirely offline."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import subprocess
import sys
sys.dont_write_bytecode = True
import tarfile
import tempfile

from layout import FIXED_FILES
from protocol import hash_file, receipt_bytes, require

HERE = Path(__file__).resolve().parent
SOURCE_COMMIT = '870b1234a13158e11707e57fd160338b3c59bde8'
INFRA_EQUIVALENT_COMMIT = '1ea0f1790f94586509ffb2af8fa991e08c5c97bb'
INFRA_DIRECTORY = 'src/whatsapp/scripts/vps110/server/'
PIN_MODULE = '77f4cfcdc8be3be39d88cd1e328053fb4eefaaa715da6dc8e88a331e41967d07'
PIN_PACKAGE = '57ed553c994de6a23c6401141dbc40bff5771e318b32d8783bff59987c42c7ed'
PIN_TAR = '854127a87e91b2dc6a8cddbd42bdf8ea803f9b2d771b069f06dac20837dbc89d'
PIN_MATRIX = 'd9d15dea3476b01f9c62ff45f1298918219a164a61983dd2f8a1742ad7a30228'
INPUT_PINS = {'inputs/backend.jar': 'fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce',
              'inputs/verified-ci-receipt.json': '8c856988e236956e156a7c8011b1810378263fd3eb6b50c4753c12275efbc035',
              'inputs/public-ca.der': '153618de000dc4ad8a23f01771f7557920ea8901ada2a15055cbcbf0b3598781'}
INFRA_PINS = {'control.py': '7d42a86410b99163f1d409c65bfff1c3de18918825075b4b86c88b36a5739013',
              'compose.yaml': '2f1a41ce06883e89c136a16f9c3776a88aaf9aeaf2d9b20887472b7f561d48ab'}
RUNTIME = 'integrations/whatsapp/scripts/070-worker-runtime.mjs'
PATCHES = [("fs.openSync(tools.next('worker.stdout.log'), 'wx', 0o600)", "fs.openSync('/dev/null', 'w')"),
           ("fs.openSync(tools.next('worker.stderr.log'), 'wx', 0o600)", "fs.openSync('/dev/null', 'w')"),
           ("spawn(process.execPath, [main, '--wa070-smoke-owner=' + owner]", "spawn(process.execPath, ['--max-old-space-size=128', main, '--wa070-smoke-owner=' + owner]")]


def default_repository():
    if os.environ.get('WA110_SOURCE_REPOSITORY'):
        return Path(os.environ['WA110_SOURCE_REPOSITORY']).resolve()
    result = subprocess.run(['git', '-C', str(HERE), 'rev-parse', '--show-toplevel'],
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, check=True)
    return Path(result.stdout.decode().strip()).resolve()


def git_bytes(repository, revision, name):
    result = subprocess.run(['git', '-C', str(repository), 'show', revision + ':' + name],
                            env={**os.environ, 'GIT_NO_REPLACE_OBJECTS': '1'},
                            stdout=subprocess.PIPE, stderr=subprocess.DEVNULL)
    require(result.returncode == 0, 'PINNED_GIT_SOURCE_UNAVAILABLE_FETCH_FULL_HISTORY')
    return result.stdout


def canonical_infrastructure(repository):
    values = {}
    for name, expected in INFRA_PINS.items():
        path = repository / INFRA_DIRECTORY / name
        raw = path.read_bytes() if path.is_file() and not path.is_symlink() else b''
        if hashlib.sha256(raw).hexdigest() != expected:
            raw = git_bytes(repository, INFRA_EQUIVALENT_COMMIT, INFRA_DIRECTORY + name)
        require(hashlib.sha256(raw).hexdigest() == expected, 'INFRASTRUCTURE_ORIGIN_CHANGED')
        values[name] = raw
    return values['control.py'], values['compose.yaml']


def verify_infrastructure(repository):
    raw, compose = canonical_infrastructure(repository)
    expected = raw.decode().replace("FIXED_FILES = ('control.py', 'protocol.py', 'compose.yaml', 'contract.json',\n               'inputs/backend.jar', 'inputs/verified-ci-receipt.json', 'inputs/public-ca.der')", 'from layout import FIXED_FILES')
    expected = expected.replace("'/run/wa110/application.properties': ('runtime/application.properties', False)}",
                                "'/run/wa110/application.properties': ('runtime/application.properties', False),\n                                '/run/wa110/session': ('session', True)}")
    require((HERE / 'infra.py').read_bytes() == expected.encode(), 'INFRASTRUCTURE_UNREVIEWED_ADAPTATION')
    expected = compose.decode().replace('      - ./runtime/application.properties:/run/wa110/application.properties:ro\n',
                                        '      - ./runtime/application.properties:/run/wa110/application.properties:ro\n      - ./session:/run/wa110/session\n')
    require((HERE / 'compose.yaml').read_bytes() == expected.encode(), 'COMPOSE_UNREVIEWED_ADAPTATION')


def source_bytes(repository):
    canonical = json.loads((HERE / 'canonical-source-manifest.json').read_bytes())
    module = json.loads((HERE / 'source-manifest.json').read_bytes())
    require(hash_file(HERE / 'source-manifest.json') == PIN_MODULE, 'MODULE_PIN_CHANGED')
    require(canonical['fileMatrixSha256'] == PIN_MATRIX and hashlib.sha256(json.dumps(canonical['files'], sort_keys=True, separators=(',', ':')).encode()).hexdigest() == PIN_MATRIX,
            'CANONICAL_MATRIX_CHANGED')
    require({'source/' + name for name in canonical['files']} == {name for name in FIXED_FILES if name.startswith('source/')}, 'CANONICAL_SELECTION_CHANGED')
    output = {}
    for name, row in canonical['files'].items():
        raw = git_bytes(repository, SOURCE_COMMIT, name)
        require(hashlib.sha256(raw).hexdigest() == row['sha256'] and hashlib.sha1(b'blob ' + str(len(raw)).encode() + b'\0' + raw).hexdigest() == row['gitBlob'], 'CANONICAL_SOURCE_CHANGED')
        if name == RUNTIME:
            text = raw.decode()
            for before, after in PATCHES:
                require(text.count(before) == 1, 'PLATFORM_PATCH_ANCHOR_CHANGED'); text = text.replace(before, after)
            raw = text.encode()
        require(hashlib.sha256(raw).hexdigest() == module['files']['source/' + name], 'CANONICAL_ADAPTATION_CHANGED')
        output['source/' + name] = raw
    return output


def materialize_sources(destination, repository):
    for name, raw in source_bytes(repository).items():
        target = destination / name
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        with target.open('xb') as output:
            output.write(raw)
        target.chmod(0o600)


def require_output_outside_git(path):
    canonical = path.expanduser().resolve()
    require(all(not os.path.lexists(parent / '.git') for parent in (canonical, *canonical.parents)),
            'OUTPUT_MUST_BE_OUTSIDE_ANY_GIT_CHECKOUT')


def package(repository, destination, jar, receipt, public_ca):
    repository, destination = repository.resolve(), destination.expanduser().resolve()
    require(not destination.is_relative_to(repository), 'OUTPUT_MUST_BE_OUTSIDE_REPOSITORY')
    archive = destination.with_suffix('.tar')
    require_output_outside_git(destination)
    require_output_outside_git(archive)
    require(not destination.exists() and not archive.exists(), 'PACKAGE_DESTINATION_ALREADY_EXISTS')
    inputs = {'inputs/backend.jar': jar, 'inputs/verified-ci-receipt.json': receipt, 'inputs/public-ca.der': public_ca}
    for name, source in inputs.items():
        require(source.is_file() and not source.is_symlink() and hash_file(source) == INPUT_PINS[name], 'PINNED_INPUT_CHANGED')
    require(jar.stat().st_size == 58304246, 'JAR_SIZE_CHANGED')
    verify_infrastructure(repository)
    business = source_bytes(repository)
    module = json.loads((HERE / 'source-manifest.json').read_bytes())
    for name, expected in module['files'].items():
        if not name.startswith('source/'):
            require(hash_file(HERE / name) == expected, 'FROZEN_RUNTIME_CHANGED')
    destination.mkdir(mode=0o700)
    for name in FIXED_FILES:
        target = destination / name
        target.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        if name in inputs:
            shutil.copyfile(inputs[name], target)
        else:
            with target.open('xb') as output:
                output.write(business[name] if name in business else (HERE / name).read_bytes())
        target.chmod(0o700 if name == 'control.py' else 0o600)
    seal = {name: hash_file(destination / name) for name in FIXED_FILES}
    plan = hashlib.sha256(receipt_bytes(seal)).hexdigest()
    require(plan == PIN_PACKAGE, 'FROZEN_V6_PACKAGE_CHANGED')
    (destination / 'SHA256SUMS.json').write_bytes(receipt_bytes(seal)); (destination / 'SHA256SUMS.json').chmod(0o600)
    with tarfile.open(archive, 'w', format=tarfile.USTAR_FORMAT) as target:
        for name in sorted((*FIXED_FILES, 'SHA256SUMS.json')):
            source = destination / name; info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime, info.uid, info.gid = source.stat().st_size, (0o700 if name == 'control.py' else 0o600), 0, 0, 0
            info.uname = info.gname = ''
            with source.open('rb') as stream:
                target.addfile(info, stream)
    archive.chmod(0o600)
    require(hash_file(archive) == PIN_TAR, 'FROZEN_V6_TAR_CHANGED')
    return {'status': 'PREPARED_OFFLINE_EXACT_V6', 'moduleSha256': PIN_MODULE, 'packageSha256': plan,
            'archiveSha256': PIN_TAR, 'archiveBytes': archive.stat().st_size, 'archiveFiles': 50, 'hardwareVerified': False}


def test_offline(repository):
    environment = {key: value for key, value in os.environ.items() if key in ('PATH', 'HOME', 'LANG', 'LC_ALL')}
    environment.update(PYTHONDONTWRITEBYTECODE='1', WA110_SOURCE_REPOSITORY=str(repository))
    subprocess.run([sys.executable, '-B', '-m', 'unittest', 'discover', '-s', str(HERE), '-p', '*_test.py'], env=environment, check=True)
    with tempfile.TemporaryDirectory(prefix='wa110-git-sources-') as temporary:
        target = Path(temporary).resolve(); materialize_sources(target, repository)
        for name in ('bridge.mjs', 'tools.mjs', 'physical_session.mjs', 'bridge.test.mjs', 'tools.test.mjs'):
            (target / name).write_bytes((HERE / name).read_bytes())
        subprocess.run(['node', '--test', str(target / 'bridge.test.mjs'), str(target / 'tools.test.mjs')], env=environment, check=True)


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('package', 'test'))
    parser.add_argument('--repository', type=Path)
    parser.add_argument('--output', type=Path)
    parser.add_argument('--jar', type=Path)
    parser.add_argument('--receipt', type=Path)
    parser.add_argument('--public-ca', type=Path)
    args = parser.parse_args(); repository = args.repository.resolve() if args.repository else default_repository()
    if args.action == 'test':
        test_offline(repository)
    else:
        require(all(value is not None for value in (args.output, args.jar, args.receipt, args.public_ca)), 'EXPLICIT_EXTERNAL_INPUT_PATHS_REQUIRED')
        print(json.dumps(package(repository, args.output, args.jar, args.receipt, args.public_ca), separators=(',', ':')))
