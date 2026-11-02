#!/usr/bin/env python3
"""Local, exact-input packaging. No network, Docker, Java or device calls."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import shutil
import sys
sys.dont_write_bytecode = True
import tarfile

from control import FIXED_FILES, verify_package
from protocol import hash_file, receipt_bytes, require

HERE = Path(__file__).resolve().parent
BASE = Path.home() / '.local/share/vanrides/wa110'


def prepare(destination):
    require(not destination.exists() and destination.parent.resolve() == (BASE / 'vps-110-prep').resolve(), 'PACKAGE_DESTINATION_MUST_BE_NEW_LOCAL_CHILD')
    sources = {name: HERE / name for name in FIXED_FILES if not name.startswith('inputs/')}
    sources.update({'inputs/backend.jar': BASE / 'tested-linux-jar/fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce.jar',
                    'inputs/verified-ci-receipt.json': BASE / 'tested-linux-jar/41267558403-verified.json',
                    'inputs/public-ca.der': BASE.parent / 'wa060/tls-v2/ca.der'})
    contract = json.loads((HERE / 'contract.json').read_bytes())
    require(hash_file(sources['inputs/backend.jar']) == contract['backend']['jarSha256']
            and sources['inputs/backend.jar'].stat().st_size == contract['backend']['jarBytes'], 'JAR_NOT_EXACT')
    require(hash_file(sources['inputs/verified-ci-receipt.json']) == contract['backend']['verifiedReceiptSha256'], 'CI_RECEIPT_NOT_EXACT')
    require(hash_file(sources['inputs/public-ca.der']) == contract['apk']['publicCaSha256'], 'PUBLIC_CA_NOT_EXACT')
    destination.mkdir(mode=0o700)
    (destination / 'inputs').mkdir(mode=0o700)
    for name, source in sources.items():
        require(source.is_file() and not source.is_symlink(), 'PACKAGE_SOURCE_INVALID')
        target = destination / name
        shutil.copyfile(source, target)
        target.chmod(0o700 if name == 'control.py' else 0o600)
    seal = {name: hash_file(destination / name) for name in FIXED_FILES}
    (destination / 'SHA256SUMS.json').write_bytes(receipt_bytes(seal))
    (destination / 'SHA256SUMS.json').chmod(0o600)
    _, digest = verify_package(destination)
    archive = destination.with_suffix('.tar')
    require(not archive.exists(), 'ARCHIVE_ALREADY_PRESENT')
    with tarfile.open(archive, 'w', format=tarfile.USTAR_FORMAT) as target:
        for name in sorted((*FIXED_FILES, 'SHA256SUMS.json')):
            source = destination / name
            info = tarfile.TarInfo(name)
            info.size, info.mode, info.mtime, info.uid, info.gid = source.stat().st_size, (0o700 if name == 'control.py' else 0o600), 0, 0, 0
            info.uname = info.gname = ''
            with source.open('rb') as contents:
                target.addfile(info, contents)
    archive.chmod(0o600)
    return {'status': 'PACKAGED_OFFLINE_INFRASTRUCTURE_ONLY', 'files': len(seal), 'packageSha256': digest,
            'archiveSha256': hash_file(archive), 'archiveBytes': archive.stat().st_size,
            'physicalJourney': 'BLOCKED_MODULE_NOT_IMPLEMENTED'}


if __name__ == '__main__':
    parser = argparse.ArgumentParser()
    parser.add_argument('--name', default='package')
    args = parser.parse_args()
    require(args.name.isidentifier(), 'LOCAL_PACKAGE_NAME_INVALID')
    output_root = BASE / 'vps-110-prep'
    output_root.mkdir(mode=0o700, parents=True, exist_ok=True)
    require(output_root.resolve() == output_root and output_root.stat().st_uid == os.getuid()
            and output_root.stat().st_mode & 0o777 == 0o700, 'OUTPUT_ROOT_NOT_PRIVATE')
    print(json.dumps(prepare(output_root / args.name), separators=(',', ':')))
