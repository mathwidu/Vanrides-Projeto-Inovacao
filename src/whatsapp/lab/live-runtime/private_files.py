"""Small private-file writer using the original 110 inode/dirfd checks."""
import json
import os
from pathlib import Path
import re
import stat

import canonical110
from plan import require, receipt_bytes

R = canonical110.stage_receiver()


def directory(path):
    path = Path(path)
    require(path.is_absolute() and path.resolve() == path and not path.is_symlink(), 'PRIVATE_DIRECTORY_INVALID')
    return R.identity(path, '', True)


def create_directory(parent, name):
    require(re.fullmatch('[A-Za-z0-9_.-]+', name) and name not in ('.', '..'), 'PRIVATE_NAME_INVALID')
    before = directory(parent)
    fd = os.open(parent, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        require(R.identity_from_stat(os.fstat(fd), '', True) == before, 'PRIVATE_PARENT_CHANGED')
        os.mkdir(name, 0o700, dir_fd=fd)
        return directory(Path(parent) / name)
    finally:
        os.close(fd)


def write_new(base, name, raw):
    require(re.fullmatch('[A-Za-z0-9_.-]+', name) and name not in ('.', '..') and isinstance(raw, bytes), 'PRIVATE_NAME_INVALID')
    directories = {'': directory(base)}
    target, identity = R.create_private_file(Path(base), name, directories)
    with target:
        target.write(raw)
        target.flush()
        os.fsync(target.fileno())
    require(R.matches(Path(base) / name, identity, False), 'PRIVATE_FILE_CHANGED')
    return identity


def read_owned(base, name, expected=None, maximum=262144):
    directory(base)
    path = Path(base) / name
    require(re.fullmatch('[A-Za-z0-9_.-]+', name) and name not in ('.', '..'), 'PRIVATE_NAME_INVALID')
    identity = R.identity(path, name, False)
    require(expected is None or identity == expected, 'PRIVATE_FILE_CHANGED')
    fd = os.open(path, os.O_RDONLY | os.O_NOFOLLOW | os.O_NONBLOCK)
    try:
        require(R.identity_from_stat(os.fstat(fd), name, False) == identity, 'PRIVATE_FILE_CHANGED')
        raw = os.read(fd, maximum + 1)
        require(0 < len(raw) <= maximum and R.matches(path, identity, False), 'PRIVATE_FILE_CHANGED')
        return raw, identity
    finally:
        os.close(fd)


def remove_owned(base, identity):
    before = directory(base)
    path = Path(base) / identity['path']
    require(path.parent == Path(base) and R.matches(path, identity, False), 'PRIVATE_FILE_CHANGED')
    fd = os.open(base, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        require(R.identity_from_stat(os.fstat(fd), '', True) == before
                and R.identity_from_stat(os.stat(path.name, dir_fd=fd, follow_symlinks=False), path.name, False) == identity,
                'PRIVATE_FILE_CHANGED')
        os.unlink(path.name, dir_fd=fd)
    finally:
        os.close(fd)


def replace_owned(base, identity, raw):
    """Atomic replacement only of a previously owned file; never follows a link."""
    before = directory(base)
    name = identity['path']
    require(R.matches(Path(base) / name, identity, False), 'PRIVATE_FILE_CHANGED')
    temporary = '.' + name + '-replacement'
    temp_identity = write_new(base, temporary, raw)
    fd = os.open(base, os.O_RDONLY | os.O_DIRECTORY | os.O_NOFOLLOW)
    try:
        require(R.identity_from_stat(os.fstat(fd), '', True) == before
                and R.identity_from_stat(os.stat(name, dir_fd=fd, follow_symlinks=False), name, False) == identity
                and R.matches(Path(base) / temporary, temp_identity, False), 'PRIVATE_FILE_CHANGED')
        os.replace(temporary, name, src_dir_fd=fd, dst_dir_fd=fd)
        os.fsync(fd)
    finally:
        os.close(fd)
    return R.identity(Path(base) / name, name, False)
