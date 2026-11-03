#!/usr/bin/env python3
"""Package only an independently pinned, tested JAR. No Docker, SSH or runtime."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
import tarfile

sys.dont_write_bytecode = True
import canonical110
import evidence
import plan
import private_files as private
import receipt
import stage

RUNTIME_FILES = ('plan.py', 'receipt.py', 'evidence.py', 'monorepo_receipt.py', 'canonical110.py', 'private_files.py', 'control.py',
                 'stage.py', 'canonical110-pins.json')
PROFILE = 'WA120_LIVE_DELIVERY_ONLY_V1'


def package(destination, jar, verified_receipt, contract_file=plan.HERE / 'contract.json'):
    destination, jar, verified_receipt = map(Path, (destination, jar, verified_receipt))
    contract, _ = plan.load_contract(contract_file)
    # No output directory or archive exists before all mandatory evidence passes.
    plan.require(verified_receipt.is_file() and not verified_receipt.is_symlink()
                 and verified_receipt.stat().st_size <= (1000000 if evidence.is_monorepo(contract) else 262144), 'VERIFIED_RECEIPT_FILE_INVALID')
    raw_receipt = verified_receipt.read_bytes()
    binding = evidence.validate_envelope(contract, raw_receipt, jar)
    if evidence.is_monorepo(contract):
        import export as exporter
        plan.require(exporter.source_snapshot(plan.ROOT) == contract['source'], 'MONOREPO_CHECKOUT_SOURCE_CHANGED')
        for name, expected in contract['source']['files'].items():
            path = plan.ROOT / name
            plan.require(path.is_file() and not path.is_symlink() and path.resolve().is_relative_to(plan.ROOT)
                         and plan.hash_file(path) == expected['sha256'] and path.stat().st_size == expected['bytes'],
                         'MONOREPO_CHECKOUT_SOURCE_CHANGED')
    private.directory(destination.parent)
    plan.require(destination.is_absolute() and destination.parent.resolve() == destination.parent
                 and not destination.exists() and not destination.is_symlink(), 'PACKAGE_DESTINATION_INVALID')
    originals = json.loads((plan.HERE / 'canonical110-pins.json').read_bytes())
    for name, digest in originals.items():
        path = plan.resolve_source(name)
        plan.require(path.is_file() and not path.is_symlink() and plan.hash_file(path) == digest, 'CANONICAL110_SOURCE_CHANGED')
    sources = {name: plan.HERE / name for name in RUNTIME_FILES}
    sources.update({'inputs/source/' + name: plan.ROOT / name for name in plan.source_hashes()})
    sources.update({'inputs/source/' + name: plan.resolve_source(name) for name in originals})
    sources.update({'inputs/backend.jar': jar, 'inputs/verified-ci-receipt.json': verified_receipt,
                    'contract.json': Path(contract_file)})
    expected = {name: plan.hash_file(path) for name, path in sources.items()}
    plan.require(len(expected) < 63 and all(path.is_file() and not path.is_symlink() for path in sources.values()), 'PACKAGE_SOURCE_INVALID')
    archive_path = destination.with_suffix('.tar')
    plan.require(not archive_path.exists() and not archive_path.is_symlink(), 'ARCHIVE_ALREADY_PRESENT')
    private.create_directory(destination.parent, destination.name)
    directories = {'': private.directory(destination)}
    files = {}
    archive_identity = None
    try:
        for name, path in sorted(sources.items()):
            parts = Path(name).parts
            for index in range(1, len(parts)):
                relative = '/'.join(parts[:index])
                if relative not in directories:
                    parent = destination.joinpath(*parts[:index-1])
                    private.create_directory(parent, parts[index-1])
                    directories[relative] = private.R.identity(destination / relative, relative, True)
            target, files[name] = private.R.create_private_file(destination, name, directories)
            fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW)
            with os.fdopen(fd, 'rb') as source, target:
                plan.require(os.fstat(source.fileno()).st_nlink == 1, 'PACKAGE_SOURCE_INVALID')
                shutil.copyfileobj(source, target, 262144)
                target.flush(); os.fsync(target.fileno())
            plan.require(plan.hash_file(destination / name) == expected[name] == plan.hash_file(path), 'PACKAGE_SOURCE_CHANGED')
        manifest = {'schemaVersion': 1, 'profile': evidence.package_profile(contract), 'backendSha': binding['sourceSha'], 'files': expected}
        files['source-manifest.json'] = private.write_new(destination, 'source-manifest.json', plan.receipt_bytes(manifest))
        seal = {**expected, 'source-manifest.json': plan.hash_file(destination / 'source-manifest.json')}
        files['SHA256SUMS.json'] = private.write_new(destination, 'SHA256SUMS.json', plan.receipt_bytes(seal))
        if evidence.is_monorepo(contract):
            plan.require(exporter.source_snapshot(plan.ROOT) == contract['source'], 'MONOREPO_CHECKOUT_SOURCE_CHANGED')
        archive_output, archive_identity = private.R.create_private_file(destination.parent, archive_path.name,
            {'': private.directory(destination.parent)})
        with archive_output:
            with tarfile.open(fileobj=archive_output, mode='w', format=tarfile.USTAR_FORMAT) as archive:
                for name in sorted(files):
                    path = destination / name
                    info = tarfile.TarInfo(name)
                    info.size, info.mode, info.mtime, info.uid, info.gid = path.stat().st_size, 0o600, 0, 0, 0
                    info.uname = info.gname = ''
                    with path.open('rb') as source:
                        archive.addfile(info, source)
            archive_output.flush(); os.fsync(archive_output.fileno())
        plan.require(private.R.matches(archive_path, archive_identity, False), 'ARCHIVE_REPLACED')
        stage.inspect_archive(archive_path, plan.hash_file(archive_path))
        return {'status': 'PACKAGED_OFFLINE_NOT_STARTED', **binding, 'archiveSha256': plan.hash_file(archive_path),
                'archiveBytes': archive_path.stat().st_size, 'sourceManifestSha256': seal['source-manifest.json'],
                'files': {**seal, 'SHA256SUMS.json': plan.hash_file(destination / 'SHA256SUMS.json')}}
    except BaseException:
        # The stage110 receiver's inode checks preserve any replaced/foreign files.
        for name, identity in reversed(list(files.items())):
            if private.R.matches(destination / name, identity, False):
                (destination / name).unlink()
        for name, identity in reversed(list(directories.items())):
            try:
                if private.R.matches(destination / name, identity, True):
                    (destination / name).rmdir()
            except OSError:
                pass
        if archive_identity and private.R.matches(archive_path, archive_identity, False):
            private.remove_owned(destination.parent, archive_identity)
        raise


if __name__ == '__main__':
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination', type=Path, required=True)
    parser.add_argument('--jar', type=Path, required=True)
    parser.add_argument('--verified-receipt', type=Path, required=True)
    parser.add_argument('--contract', type=Path, default=plan.HERE / 'contract.json')
    args = parser.parse_args()
    try:
        print(json.dumps(package(args.destination, args.jar, args.verified_receipt, args.contract), sort_keys=True))
    except Exception as error:
        code = str(error) if isinstance(error, plan.Blocked) else 'PACKAGE_INPUT_INVALID'
        print(json.dumps({'status': 'PREPARADO_NAO_EXECUTAVEL', 'code': code}))
        sys.exit(2)
