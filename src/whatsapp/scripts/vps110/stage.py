"""Stage an approved, hash-pinned test bundle in a new private directory; no Docker or service start."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
import tarfile

MIN_BYTES = 10_000_000
MAX_BYTES = 120_000_000
ALLOW = {'control.py', 'protocol.py', 'compose.yaml', 'contract.json', 'SHA256SUMS.json',
         'inputs/backend.jar', 'inputs/verified-ci-receipt.json', 'inputs/public-ca.der'}
PIN_JAR = 'fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce'
PIN_CA = '153618de000dc4ad8a23f01771f7557920ea8901ada2a15055cbcbf0b3598781'


def need(ok, code):
    if not ok:
        raise ValueError(code)


def hash_stream(stream):
    h = hashlib.sha256()
    for block in iter(lambda: stream.read(262144), b''):
        h.update(block)
    return h.hexdigest()


def inspect_bundle(stream, expected):
    need(re.fullmatch('[0-9a-f]{64}', expected), 'INVALID_DIGEST')
    need(hash_stream(stream) == expected, 'BUNDLE_DIGEST_CHANGED')
    stream.seek(0)
    seen = {}; size = 0
    with tarfile.open(fileobj=stream, mode='r:*') as archive:
        for item in archive:
            raw = item.name.removeprefix('./')
            if raw == 'package' and item.isdir():
                continue
            name = raw.removeprefix('package/')
            if name == 'inputs' and item.isdir():
                continue
            need(name in ALLOW and item.isfile() and name not in seen and item.size > 0, 'UNEXPECTED_ENTRY')
            need(item.size <= (100_000_000 if name == 'inputs/backend.jar' else 1_000_000), 'ENTRY_TOO_LARGE')
            size += item.size; need(size <= MAX_BYTES, 'BUNDLE_TOO_LARGE')
            seen[name] = hash_stream(archive.extractfile(item))
    need(set(seen) == ALLOW, 'MISSING_ENTRY')
    need(seen['inputs/backend.jar'] == PIN_JAR and seen['inputs/public-ca.der'] == PIN_CA, 'PIN_CHANGED')
    return seen


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=['inspect', 'stage', 'unstage-unused'])
    parser.add_argument('archive', type=Path)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--attempt', required=True)
    args = parser.parse_args()
    need(re.fullmatch('[a-z][a-z0-9-]{2,39}', args.attempt), 'INVALID_ATTEMPT')
    p = args.archive.absolute()
    need(p.is_file() and not p.is_symlink() and MIN_BYTES <= p.stat().st_size <= MAX_BYTES, 'INVALID_ARCHIVE')
    with p.open('rb') as stream:
        files = inspect_bundle(stream,args.sha256); stream.seek(0)
        if args.action != 'inspect':
            os.umask(0o077)
            receipts=Path.home()/'.local/share/vanrides/wa110/vps-stage-receipts'
            receipts.mkdir(mode=0o700,parents=True,exist_ok=True)
            need(receipts.resolve()==receipts and receipts.stat().st_uid==os.getuid() and receipts.stat().st_mode & 0o777==0o700,'RECEIPT_DIRECTORY_INVALID')
            mark=receipts/(args.attempt+'-'+args.sha256+'.json')
            remote = Path(__file__).with_name('stage_remote.py').read_text()
            cmd = ['python3','-B','-c',remote,args.attempt,args.sha256,str(p.stat().st_size),json.dumps(files,sort_keys=True)]
            if args.action=='unstage-unused':
                need(mark.is_file() and not mark.is_symlink() and mark.stat().st_uid==os.getuid()
                     and mark.stat().st_mode & 0o777==0o600, 'STAGING_RECEIPT_REQUIRED')
                cmd.extend(['unstage-unused',mark.read_text()])
            else:
                need(not mark.exists() and not mark.is_symlink(),'ATTEMPT_ALREADY_STAGED')
            result=subprocess.run(['ssh','-T','-o','BatchMode=yes','-o','ConnectTimeout=8','-o','ServerAliveInterval=8','-o','ServerAliveCountMax=3','-o','StrictHostKeyChecking=yes','-o','ForwardAgent=no','-o','ClearAllForwardings=yes','vps-pessoal-nova',shlex.join(cmd)],stdin=stream if args.action=='stage' else subprocess.DEVNULL,stdout=subprocess.PIPE,check=True,timeout=360)
            evidence=json.loads(result.stdout)
            need(evidence['attempt']==args.attempt and evidence['sha256']==args.sha256, 'REMOTE_RESULT_MISMATCH')
            if args.action=='stage':
                need(evidence.get('staged') is True and type(evidence.get('inode')) is int and type(evidence.get('device')) is int,'REMOTE_RECEIPT_INVALID')
                with mark.open('x') as output:json.dump(evidence,output)
            else:
                need(evidence.get('unusedPackageRemoved') is True,'UNSTAGE_UNCONFIRMED')
    print(json.dumps(dict(action=args.action,sha256=args.sha256,attempt=args.attempt,files=len(files),servicesStarted=False)))


if __name__ == '__main__':
    main()
