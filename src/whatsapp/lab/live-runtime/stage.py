#!/usr/bin/env python3
"""Offline inspection and sealed receiver generation. SSH belongs to principal."""
import argparse
import hashlib
import json
from pathlib import Path
import re
import sys
import tarfile

sys.dont_write_bytecode = True
import canonical110
import plan
import receipt

PROFILE = 'WA120_LIVE_DELIVERY_ONLY_V1'


def inspect_archive(path, expected):
    plan.require(path.is_file() and not path.is_symlink() and 0 < path.stat().st_size <= 120000000
                 and re.fullmatch('[a-f0-9]{64}', expected) and plan.hash_file(path) == expected, 'ARCHIVE_DIGEST_CHANGED')
    files, captured = {}, {}
    with tarfile.open(path, 'r:*') as archive:
        for item in archive:
            name = item.name
            plan.require(len(files) < 64 and item.isfile() and name not in files
                         and Path(name).as_posix() == name and not Path(name).is_absolute()
                         and '..' not in Path(name).parts and 0 < item.size <= (100000000 if name == 'inputs/backend.jar' else 1000000), 'ARCHIVE_ENTRY_INVALID')
            with archive.extractfile(item) as source:
                digest, size = hashlib.sha256(), 0
                chunks = []
                for chunk in iter(lambda: source.read(262144), b''):
                    digest.update(chunk); size += len(chunk)
                    if name in ('contract.json', 'source-manifest.json', 'SHA256SUMS.json'):
                        chunks.append(chunk)
                plan.require(size == item.size, 'ARCHIVE_TRUNCATED')
                files[name] = digest.hexdigest()
                if chunks:
                    captured[name] = b''.join(chunks)
    verify_bindings(files, captured, files.get('source-manifest.json'))
    return files, captured


def verify_bindings(files, captured, module):
    plan.require({'contract.json', 'source-manifest.json', 'SHA256SUMS.json', 'inputs/backend.jar',
                  'inputs/verified-ci-receipt.json', 'control.py', 'plan.py', 'canonical110.py',
                  'canonical110-pins.json', 'private_files.py', 'receipt.py', 'stage.py'} <= set(files), 'PACKAGE_FILES_MISSING')
    contract = receipt.object_json(captured['contract.json'])
    backend, _ = receipt.ready_pins(contract)
    manifest = receipt.object_json(captured['source-manifest.json'])
    seal = receipt.object_json(captured['SHA256SUMS.json'])
    plan.require(manifest.get('schemaVersion') == 1 and manifest.get('profile') == PROFILE
                 and manifest.get('backendSha') == backend['sourceSha']
                 and isinstance(manifest.get('files'), dict)
                 and set(manifest['files']) == set(files) - {'source-manifest.json', 'SHA256SUMS.json'}
                 and all(files[name] == digest for name, digest in manifest['files'].items())
                 and set(seal) == set(files) - {'SHA256SUMS.json'}
                 and all(files[name] == digest for name, digest in seal.items())
                 and files['source-manifest.json'] == module
                 and files['inputs/backend.jar'] == backend['jarSha256']
                 and files['inputs/verified-ci-receipt.json'] == backend['verifiedCiReceiptSha256'], 'PACKAGE_BINDING_CHANGED')
    # The frozen package matrix is the authority; receiver generation embeds it
    # verbatim. A future transfer cannot expand the whitelist from its own tar.
    return contract


def configured_receiver(files, contract_raw, manifest_raw):
    receiver = canonical110.stage_receiver()
    receiver.ALLOW = set(files)
    receiver.MAX_ENTRIES = 64
    receiver.ROOT_PARENTS = ('.local', '.local/share', '.local/share/vanrides', '.local/share/vanrides/wa120-live')
    def bindings(actual, captured, module):
        plan.require(actual == files and captured['contract.json'] == contract_raw
                     and captured['source-manifest.json'] == manifest_raw, 'APPROVED_PACKAGE_CHANGED')
        verify_bindings(actual, captured, module)
    receiver.verify_bindings = bindings
    return receiver


def receiver_program(files, captured):
    """Single self-contained public program, pinned before principal transports it."""
    verify_bindings(files, captured, files.get('source-manifest.json'))
    relative = 'src/whatsapp/scripts/vps110/physical/stage_physical_remote.py'
    source = plan.resolve_source(relative).read_bytes()
    canonical110.stage_receiver()  # Enforce the canonical source digest first.
    # Reuse original stage/unstage, inode, limits and rollback functions unchanged.
    # The generated bootstrap imports no workspace code and executes no archive.
    prelude = "import json, hashlib, os, pwd, socket, sys\nfrom pathlib import Path\n"
    prelude += 'ns={"__name__":"wa120_sealed_stage110"}\nexec(compile(' + repr(source.decode()) + ',"sealed-stage110","exec"),ns)\n'
    prelude += 'FILES=' + repr(files) + '\nCONTRACT=' + repr(captured['contract.json']) + '\nMANIFEST=' + repr(captured['source-manifest.json']) + '\n'
    prelude += """
def fixed_bindings(files, captured, module):
    ns['require'](files == FILES and captured['contract.json'] == CONTRACT and captured['source-manifest.json'] == MANIFEST)
    seal = json.loads(captured['SHA256SUMS.json'])
    ns['require'](set(seal) == set(FILES)-{'SHA256SUMS.json'} and all(seal[key] == FILES[key] for key in seal))
    ns['require'](module == FILES['source-manifest.json'])
ns['ALLOW'] = set(FILES)
ns['MAX_ENTRIES'] = 64
ns['ROOT_PARENTS'] = ('.local','.local/share','.local/share/vanrides','.local/share/vanrides/wa120-live')
ns['verify_bindings'] = fixed_bindings
os.umask(0o077)
ns['require'](pwd.getpwuid(os.getuid()).pw_name == 'mathwidu' and socket.gethostname() == 'srv1397065' and Path.home() == Path('/home/mathwidu'))
ns['require'](len(sys.argv) in (5,6))
action,attempt,archive_sha,size = sys.argv[1:5]
ns['require'](action in ('stage','unstage-unused'))
root,parents = ns['private_root'](Path.home())
try:
    if action == 'stage':
        ns['require'](len(sys.argv)==5)
        result=ns['stage'](root,attempt,archive_sha,FILES['source-manifest.json'],int(size),FILES,sys.stdin.buffer)
        result['rootParentsCreated']=parents
    else:
        ns['require'](len(sys.argv)==6)
        result=ns['unstage_unused'](root,attempt,archive_sha,FILES['source-manifest.json'],FILES,json.loads(sys.argv[5]))
except BaseException:
    ns['remove_created_parents'](Path.home(),parents)
    raise
print(json.dumps(result,sort_keys=True))
"""
    return prelude


if __name__ == '__main__':
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('inspect', 'emit-receiver'))
    parser.add_argument('archive', type=Path)
    parser.add_argument('--sha256', required=True)
    args = parser.parse_args()
    try:
        files, captured = inspect_archive(args.archive, args.sha256)
        if args.action == 'emit-receiver':
            print(receiver_program(files, captured), end='')
        else:
            print(json.dumps({'status': 'INSPECTED_NOT_STARTED', 'files': len(files), 'archiveSha256': args.sha256,
                              'sourceManifestSha256': files['source-manifest.json']}))
    except Exception as error:
        print(json.dumps({'status': 'PREPARADO_NAO_EXECUTAVEL', 'code': str(error) if isinstance(error, plan.Blocked) else 'STAGE_INPUT_INVALID'}))
        sys.exit(2)
