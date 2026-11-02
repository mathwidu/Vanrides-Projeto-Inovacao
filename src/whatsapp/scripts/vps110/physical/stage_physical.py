"""Inspect/stage/unstage an approved physical bundle. Import and inspect do no network I/O."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import shlex
import subprocess
import sys
sys.dont_write_bytecode = True
import tarfile

import stage_physical_remote as receiver

MIN_BYTES = 10_000_000
MAX_BYTES = receiver.MAX_BYTES
ALLOW = receiver.ALLOW


def need(ok, code):
    if not ok:
        raise ValueError(code)


def hash_stream(stream, maximum=MAX_BYTES):
    digest = hashlib.sha256(); size = 0
    for chunk in iter(lambda: stream.read(262144), b''):
        size += len(chunk); need(size <= maximum, 'PHYSICAL_BUNDLE_TOO_LARGE'); digest.update(chunk)
    return digest.hexdigest()


def inspect_bundle(stream, expected, module):
    need(re.fullmatch('[a-f0-9]{64}', expected) and re.fullmatch('[a-f0-9]{64}', module), 'INVALID_DIGEST')
    need(hash_stream(stream) == expected, 'PHYSICAL_BUNDLE_DIGEST_CHANGED'); stream.seek(0)
    files = {}; captured = {}; size = count = 0
    with tarfile.open(fileobj=stream, mode='r:*') as archive:
        for item in archive:
            count += 1; need(count <= receiver.MAX_ENTRIES, 'PHYSICAL_ENTRY_LIMIT')
            name = receiver.normalized_member(item)
            if item.isdir() and name in receiver.parents_for(ALLOW) | {''}:
                continue
            need(name in ALLOW and item.isfile() and name not in files and 0 < item.size, 'PHYSICAL_UNEXPECTED_ENTRY')
            limit = 100_000_000 if name == 'inputs/backend.jar' else 1_000_000
            need(item.size <= limit, 'PHYSICAL_ENTRY_TOO_LARGE'); size += item.size
            need(size <= MAX_BYTES, 'PHYSICAL_BUNDLE_TOO_LARGE')
            with archive.extractfile(item) as member:
                if name in ('contract.json', 'source-manifest.json', 'SHA256SUMS.json', 'layout.py'):
                    captured[name] = member.read(limit + 1); need(len(captured[name]) == item.size, 'PHYSICAL_TRUNCATED_ENTRY')
                    files[name] = hashlib.sha256(captured[name]).hexdigest()
                else:
                    files[name] = hash_stream(member, limit)
    need(set(files) == ALLOW, 'PHYSICAL_MISSING_ENTRY')
    receiver.verify_bindings(files, captured, module)
    return files


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('action', choices=('inspect', 'stage', 'unstage-unused'))
    parser.add_argument('archive', type=Path)
    parser.add_argument('--sha256', required=True)
    parser.add_argument('--module-sha256', required=True)
    parser.add_argument('--attempt', required=True)
    args = parser.parse_args()
    need(re.fullmatch('[a-z][a-z0-9-]{2,39}', args.attempt), 'INVALID_ATTEMPT')
    archive = args.archive.absolute()
    need(archive.is_file() and not archive.is_symlink() and MIN_BYTES <= archive.stat().st_size <= MAX_BYTES, 'INVALID_ARCHIVE')
    with archive.open('rb') as stream:
        files = inspect_bundle(stream, args.sha256, args.module_sha256); stream.seek(0)
        if args.action != 'inspect':
            os.umask(0o077)
            receipts = Path.home() / '.local/share/vanrides/wa110/vps-physical-stage-receipts'
            receipts.mkdir(mode=0o700, parents=True, exist_ok=True)
            need(receipts.resolve() == receipts and receipts.stat().st_uid == os.getuid()
                 and receipts.stat().st_mode & 0o777 == 0o700, 'RECEIPT_DIRECTORY_INVALID')
            mark = receipts / (args.attempt + '-' + args.sha256 + '.json')
            remote = Path(__file__).with_name('stage_physical_remote.py').read_text()
            command = ['python3', '-B', '-c', remote, args.attempt, args.sha256, args.module_sha256,
                       str(archive.stat().st_size), json.dumps(files, sort_keys=True)]
            if args.action == 'unstage-unused':
                need(mark.is_file() and not mark.is_symlink() and mark.stat().st_uid == os.getuid()
                     and mark.stat().st_mode & 0o777 == 0o600, 'STAGING_RECEIPT_REQUIRED')
                command.extend(['unstage-unused', mark.read_text()])
            else:
                need(not mark.exists() and not mark.is_symlink(), 'ATTEMPT_ALREADY_STAGED')
            result = subprocess.run(['ssh', '-T', '-o', 'BatchMode=yes', '-o', 'ConnectTimeout=8', '-o', 'ServerAliveInterval=8',
                '-o', 'ServerAliveCountMax=3', '-o', 'StrictHostKeyChecking=yes', '-o', 'ForwardAgent=no',
                '-o', 'ClearAllForwardings=yes', 'vps-pessoal-nova', shlex.join(command)],
                stdin=stream if args.action == 'stage' else subprocess.DEVNULL, stdout=subprocess.PIPE, check=True, timeout=360)
            need(len(result.stdout) <= 65536, 'REMOTE_RESULT_TOO_LARGE'); evidence = json.loads(result.stdout)
            need(evidence['attempt'] == args.attempt and evidence['sha256'] == args.sha256
                 and evidence['moduleSha256'] == args.module_sha256, 'REMOTE_RESULT_MISMATCH')
            if args.action == 'stage':
                need(evidence.get('staged') is True and type(evidence.get('inode')) is int and type(evidence.get('device')) is int
                     and set(evidence.get('fileIdentities', {})) == ALLOW
                     and set(evidence.get('directories', {})) == receiver.parents_for(ALLOW) | {''}, 'REMOTE_RECEIPT_INVALID')
                with mark.open('x') as output:
                    json.dump(evidence, output)
            else:
                need(evidence.get('unusedPackageRemoved') is True, 'UNSTAGE_UNCONFIRMED')
    print(json.dumps({'action': args.action, 'sha256': args.sha256, 'moduleSha256': args.module_sha256,
                      'attempt': args.attempt, 'files': len(files), 'servicesStarted': False}))


if __name__ == '__main__':
    main()
