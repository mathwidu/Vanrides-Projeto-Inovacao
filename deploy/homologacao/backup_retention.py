#!/usr/bin/env python3
"""Explicit, private backup catalogue and reviewed deletion plans. No scheduler or Docker."""
import argparse
from contextlib import contextmanager
from datetime import datetime, timedelta, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import stat
import sys
import uuid

CATALOG = '.backup-retention.json'
MAX_DUMP = 64 * 1024 * 1024
ACTION = 'DELETE_EXACT_EXPIRED_BACKUPS'


def require(condition, code):
    if not condition:
        raise ValueError(code)


def utc(value=None):
    return (value or datetime.now(timezone.utc)).astimezone(timezone.utc).isoformat(timespec='microseconds')


def date(value):
    parsed = datetime.fromisoformat(value)
    require(parsed.utcoffset() == timedelta(0), 'UTC_DATE_REQUIRED')
    return parsed


def encode(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n').encode()


def sha(raw):
    return hashlib.sha256(raw).hexdigest()


def digest(value):
    return isinstance(value, str) and re.fullmatch('[a-f0-9]{64}', value)


def name(value, suffix):
    require(isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9_.-]*' + re.escape(suffix), value),
            'DIRECT_CHILD_NAME_REQUIRED')
    return value


def private(info, kind):
    mode = 0o700 if kind is stat.S_ISDIR else 0o600
    require(kind(info.st_mode) and info.st_uid == os.getuid() and info.st_mode & 0o777 == mode,
            'PRIVATE_OWNED_PATH_REQUIRED')


def read(fd, filename, maximum, dump=False):
    handle = os.open(filename, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd)
    try:
        before = os.fstat(handle)
        private(before, stat.S_ISREG)
        require(before.st_nlink == 1 and 0 < before.st_size <= maximum, 'FILE_SIZE_OR_LINK_INVALID')
        chunks, size = [], 0
        while True:
            chunk = os.read(handle, min(65536, maximum + 1 - size))
            if not chunk:
                break
            chunks.append(chunk); size += len(chunk)
            require(size <= maximum, 'FILE_TOO_LARGE')
        after = os.fstat(handle)
        require((before.st_dev, before.st_ino, before.st_size, before.st_mtime_ns, before.st_ctime_ns)
                == (after.st_dev, after.st_ino, after.st_size, after.st_mtime_ns, after.st_ctime_ns), 'FILE_CHANGED_DURING_READ')
        raw = b''.join(chunks)
        require(not dump or raw.startswith(b'PGDMP'), 'POSTGRES_CUSTOM_DUMP_REQUIRED')
        identity = {'device': after.st_dev, 'inode': after.st_ino, 'mtimeNs': after.st_mtime_ns,
                    'ctimeNs': after.st_ctime_ns, 'bytes': len(raw), 'sha256': sha(raw)}
        return raw, identity
    finally:
        os.close(handle)


def write(fd, filename, value, replace=False):
    raw, temporary = encode(value), '.retention-' + uuid.uuid4().hex + '.tmp'
    handle = os.open(temporary, os.O_WRONLY | os.O_CREAT | os.O_EXCL | os.O_NOFOLLOW, 0o600, dir_fd=fd)
    try:
        with os.fdopen(handle, 'wb') as output:
            output.write(raw); output.flush(); os.fsync(output.fileno())
        if replace:
            os.replace(temporary, filename, src_dir_fd=fd, dst_dir_fd=fd)
        else:
            os.link(temporary, filename, src_dir_fd=fd, dst_dir_fd=fd, follow_symlinks=False)
        os.fsync(fd)
    finally:
        try:
            os.unlink(temporary, dir_fd=fd)
        except FileNotFoundError:
            pass
    return sha(raw)


@contextmanager
def scope(directory):
    root = Path(directory)
    require(root.is_absolute() and root.resolve(strict=True) == root, 'CANONICAL_ABSOLUTE_SCOPE_REQUIRED')
    fd = os.open(root, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    lock = None
    try:
        info = os.fstat(fd); private(info, stat.S_ISDIR)
        lock = os.open('.backup-retention.lock', os.O_RDWR | os.O_CREAT | os.O_NOFOLLOW | os.O_NONBLOCK, 0o600, dir_fd=fd)
        private(os.fstat(lock), stat.S_ISREG)
        require(os.fstat(lock).st_nlink == 1, 'LOCK_LINK_REJECTED')
        fcntl.flock(lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
        yield fd, {'path': str(root), 'device': info.st_dev, 'inode': info.st_ino}
    finally:
        if lock is not None:
            os.close(lock)
        os.close(fd)


def catalogue(fd, target, absent=False):
    try:
        raw, _ = read(fd, CATALOG, 512000)
        value = json.loads(raw)
    except FileNotFoundError:
        require(absent, 'CATALOG_REQUIRED')
        return {'schemaVersion': 1, 'scope': target, 'backups': []}, None
    require(set(value) == {'schemaVersion', 'scope', 'backups'} and value['schemaVersion'] == 1
            and value['scope'] == target and isinstance(value['backups'], list)
            and len(value['backups']) <= 500, 'CATALOG_SCOPE_OR_SCHEMA_INVALID')
    require(len({item['name'] for item in value['backups']}) == len(value['backups']), 'DUPLICATE_BACKUP')
    return value, sha(raw)


def register(fd, target, filename, dataset, created_at=None, restored_sha=None, evidence_sha=None, now=None):
    value, _ = catalogue(fd, target, absent=True)
    require(len(value['backups']) < 500, 'CATALOG_LIMIT')
    filename = name(filename, '.dump')
    require(isinstance(dataset, str) and re.fullmatch('[a-z][a-z0-9_-]{0,63}', dataset), 'LOGICAL_DATASET_REQUIRED')
    require(all(item['name'] != filename for item in value['backups']), 'ALREADY_REGISTERED')
    _, identity = read(fd, filename, MAX_DUMP, dump=True)
    recorded = utc(now)
    require(created_at is None or date(created_at) <= date(recorded), 'CREATION_DATE_IN_FUTURE')
    require((restored_sha is None and evidence_sha is None)
            or (restored_sha == identity['sha256'] and digest(evidence_sha)), 'EXACT_RESTORE_ATTESTATION_REQUIRED')
    value['backups'].append({'name': filename, 'dataset': dataset, 'createdAt': created_at, 'recordedAt': recorded,
                             'restoredSha256': restored_sha, 'restoreEvidenceSha256': evidence_sha, 'file': identity})
    value['backups'].sort(key=lambda item: item['name'])
    write(fd, CATALOG, value, replace=True)
    return {'status': 'REGISTERED', 'createdAtKnown': created_at is not None, 'restoreAttested': restored_sha is not None}


def plan(fd, target, now=None):
    now = date(utc(now))
    value, catalog_hash = catalogue(fd, target)
    delete, keep = [], []
    for item in value['backups']:
        require(set(item) == {'name', 'dataset', 'createdAt', 'recordedAt', 'restoredSha256', 'restoreEvidenceSha256', 'file'}
                and isinstance(item['dataset'], str) and re.fullmatch('[a-z][a-z0-9_-]{0,63}', item['dataset']),
                'BACKUP_RECORD_INVALID')
        _, actual = read(fd, name(item['name'], '.dump'), MAX_DUMP, dump=True)
        require(actual == item['file'], 'REGISTERED_BACKUP_CHANGED')
        recorded = date(item['recordedAt'])
        created = date(item['createdAt']) if item['createdAt'] is not None else recorded
        require(created <= recorded <= now, 'BACKUP_DATE_INVALID')
        require((item['restoredSha256'] is None and item['restoreEvidenceSha256'] is None)
                or (item['restoredSha256'] == actual['sha256'] and digest(item['restoreEvidenceSha256'])),
                'RESTORE_ATTESTATION_INVALID')
        entry = {**item, 'retainUntil': utc(created + timedelta(days=7)),
                 'ageBasis': 'DECLARED_CREATION_UTC' if item['createdAt'] else 'UNKNOWN_CREATION_REGISTRATION_FLOOR'}
        (delete if date(entry['retainUntil']) <= now else keep).append(entry)
    protected = {item['dataset'] for item in keep if item['restoredSha256']}
    require(all(item['dataset'] in protected for item in delete), 'LAST_RESTORABLE_COPY_GENERATE_AND_RESTORE_NEW_BACKUP')
    return {'schemaVersion': 1, 'operation': ACTION, 'scope': target, 'catalogSha256': catalog_hash,
            'createdAt': utc(now), 'validUntil': utc(now + timedelta(hours=24)), 'delete': delete, 'keep': keep}


def reviewed(fd, target, filename, expected, now=None):
    raw, _ = read(fd, name(filename, '.json'), 512000)
    require(digest(expected) and sha(raw) == expected, 'PLAN_HASH_MISMATCH')
    value = json.loads(raw); current = date(utc(now))
    require(value.get('scope') == target and date(value['createdAt']) <= current <= date(value['validUntil']), 'PLAN_EXPIRED_OR_SCOPE_CHANGED')
    require(plan(fd, target, date(value['createdAt'])) == value, 'PLAN_OR_BACKUPS_CHANGED')
    require(all(date(item['retainUntil']) <= current for item in value['delete']), 'BACKUP_NOT_EXPIRED')
    return value


def approve(fd, target, filename, expected, output, now=None):
    reviewed(fd, target, filename, expected, now)
    approval = {'schemaVersion': 1, 'operation': ACTION, 'scope': target, 'planSha256': expected, 'approvedAt': utc(now)}
    write(fd, name(output, '.json'), approval)
    return {'status': 'EXACT_PLAN_APPROVED', 'planSha256': expected}


def apply(fd, target, filename, expected, approval_file, execute=False, now=None):
    value = reviewed(fd, target, filename, expected, now)
    raw, _ = read(fd, name(approval_file, '.json'), 64000)
    approval = json.loads(raw)
    require(approval == {'schemaVersion': 1, 'operation': ACTION, 'scope': target, 'planSha256': expected,
                         'approvedAt': approval.get('approvedAt')}
            and date(value['createdAt']) <= date(approval['approvedAt']) <= date(utc(now)), 'EXACT_PLAN_APPROVAL_REQUIRED')
    if not execute or not value['delete']:
        return {'status': 'SIMULATION_ONLY', 'deleteCount': len(value['delete']), 'planSha256': expected}
    # Stage into a fresh private directory before any unlink. A late replacement is retained, never erased.
    staging = '.retention-purge-' + uuid.uuid4().hex
    os.mkdir(staging, mode=0o700, dir_fd=fd)
    staged = os.open(staging, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW, dir_fd=fd)
    try:
        for item in value['delete']:
            # Pin the old inode so a replacement cannot recycle it during the rename.
            with os.fdopen(os.open(item['name'], os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK, dir_fd=fd), 'rb') as pinned:
                info = os.fstat(pinned.fileno())
                require((info.st_dev, info.st_ino) == (item['file']['device'], item['file']['inode']), 'BACKUP_REPLACED')
                os.rename(item['name'], item['name'], src_dir_fd=fd, dst_dir_fd=staged)
                _, actual = read(staged, item['name'], MAX_DUMP, dump=True)
                # Our rename changes ctime; all other identity/content fields must still match.
                require(actual == {**item['file'], 'ctimeNs': actual['ctimeNs']}, 'LATE_CHANGE_RETAINED_IN_PURGE_DIRECTORY')
        for item in value['keep']:
            require(read(fd, item['name'], MAX_DUMP, dump=True)[1] == item['file'], 'RETAINED_BACKUP_CHANGED')
        for item in value['delete']:
            os.unlink(item['name'], dir_fd=staged)
        os.fsync(staged)
        catalog, _ = catalogue(fd, target)
        deleted = {item['name'] for item in value['delete']}
        catalog['backups'] = [item for item in catalog['backups'] if item['name'] not in deleted]
        write(fd, CATALOG, catalog, replace=True)
        result = {'status': 'DELETED_EXACT_PLAN', 'deletedCount': len(deleted), 'planSha256': expected, 'at': utc(now)}
        write(fd, 'retention-receipt-' + uuid.uuid4().hex + '.json', result)
        os.rmdir(staging, dir_fd=fd)
        return result
    finally:
        os.close(staged)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True)
    commands = parser.add_subparsers(dest='command', required=True)
    register_args = commands.add_parser('register')
    register_args.add_argument('--dump', required=True)
    register_args.add_argument('--dataset', required=True)
    register_args.add_argument('--created-at')
    register_args.add_argument('--restored-sha256')
    register_args.add_argument('--restore-evidence-sha256')
    plan_args = commands.add_parser('plan'); plan_args.add_argument('--output', required=True)
    for command in ('approve', 'apply'):
        child = commands.add_parser(command)
        child.add_argument('--plan', required=True); child.add_argument('--plan-sha256', required=True)
        if command == 'approve':
            child.add_argument('--output', required=True)
        else:
            child.add_argument('--approval', required=True); child.add_argument('--execute', action='store_true')
    args = parser.parse_args()
    try:
        with scope(args.directory) as (fd, target):
            if args.command == 'register':
                result = register(fd, target, args.dump, args.dataset, args.created_at, args.restored_sha256, args.restore_evidence_sha256)
            elif args.command == 'plan':
                result = {'status': 'PLANNED_ONLY', 'planSha256': write(fd, name(args.output, '.json'), plan(fd, target))}
            elif args.command == 'approve':
                result = approve(fd, target, args.plan, args.plan_sha256, args.output)
            else:
                result = apply(fd, target, args.plan, args.plan_sha256, args.approval, args.execute)
        print(json.dumps(result, sort_keys=True))
    except Exception as error:
        code = str(error) if type(error) is ValueError and re.fullmatch('[A-Z_]{1,100}', str(error)) else 'PRIVATE_STATE_OR_IO_INVALID'
        print(json.dumps({'status': 'BLOCKED', 'code': code}))
        return 2
    return 0


if __name__ == '__main__':
    sys.exit(main())
