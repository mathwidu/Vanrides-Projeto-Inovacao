"""Receiver for the bounded stage protocol. No commands, Docker operations or network connections."""
import hashlib
import json
import os
from pathlib import Path, PurePosixPath
import pwd
import re
import socket
import stat
import sys
import tarfile


def require(ok):
    if not ok:
        raise ValueError('STAGE_SCOPE_OR_INTEGRITY_MISMATCH')


def stage(root, attempt, expected, size, files, source):
    require(re.fullmatch(r'[a-z][a-z0-9-]{2,39}',attempt) and re.fullmatch('[a-f0-9]{64}',expected)
            and 0 < size <= 120_000_000 and 1 <= len(files) <= 32)
    for name, digest in files.items():
        path = PurePosixPath(name)
        require(path.as_posix() == name and not path.is_absolute() and '..' not in path.parts and
                re.fullmatch(r'(?:inputs/)?[A-Za-z0-9][A-Za-z0-9_.-]{0,80}',name) and re.fullmatch('[a-f0-9]{64}',digest))
    require(root.is_dir() and root.resolve()==root and root.stat().st_uid==os.getuid() and stat.S_IMODE(root.stat().st_mode)==0o700)
    dest = root / attempt
    dest.mkdir(mode=0o700)
    def identity(path, info):
        return path, info.st_dev, info.st_ino
    def matches(record, expected_type):
        path, dev, ino = record
        try: info = path.lstat()
        except FileNotFoundError: return False
        return expected_type(info.st_mode) and info.st_dev==dev and info.st_ino==ino
    directories = [identity(dest,dest.stat())]; written = []; seen = set(); size_out = 0
    archive_path = dest / '.transfer'
    try:
        with archive_path.open('xb') as tmp:
            written.append(identity(archive_path,os.fstat(tmp.fileno()))); h=hashlib.sha256(); n=0
            while n < size:
                chunk=source.read(min(262144,size-n)); require(chunk)
                tmp.write(chunk); h.update(chunk); n+=len(chunk)
            require(not source.read(1) and h.hexdigest()==expected)
        with tarfile.open(archive_path,mode='r:*') as archive:
            for item in archive:
                rel = item.name.removeprefix('./')
                if item.isdir() and rel in ('package','package/inputs','inputs'):
                    continue
                rel=rel.removeprefix('package/')
                require(rel in files and rel not in seen and item.isfile() and 0<item.size<=100_000_000)
                size_out+=item.size; require(size_out<=120_000_000); seen.add(rel)
                path=dest / rel
                if path.parent != dest and not path.parent.exists():
                    path.parent.mkdir(mode=0o700); directories.append(identity(path.parent,path.parent.stat()))
                require(path.parent.resolve()==path.parent and any(r[0]==path.parent and matches(r,stat.S_ISDIR) for r in directories))
                with path.open('xb') as out:
                    written.append(identity(path,os.fstat(out.fileno()))); h=hashlib.sha256()
                    with archive.extractfile(item) as src:
                        for block in iter(lambda:src.read(262144),b''):
                            out.write(block); h.update(block)
                    require(h.hexdigest()==files[rel])
        require(seen==set(files))
        require(matches(written[0],stat.S_ISREG)); archive_path.unlink()
        return {'staged':True,'attempt':attempt,'sha256':expected,'files':len(seen),'servicesStarted':False,
                'device':dest.stat().st_dev,'inode':dest.stat().st_ino}
    except BaseException:
        for record in reversed(written):
            if matches(record,stat.S_ISREG): record[0].unlink()
        for directory in reversed(directories):
            try:
                if matches(directory,stat.S_ISDIR): directory[0].rmdir()
            except OSError: pass  # preserve any unrecognised contents
        raise


def unstage_unused(root, attempt, expected, files, receipt):
    require(re.fullmatch(r'[a-z][a-z0-9-]{2,39}',attempt) and re.fullmatch('[0-9a-f]{64}',expected))
    require(receipt.get('staged') is True and receipt.get('attempt')==attempt and receipt.get('sha256')==expected)
    d=root / attempt
    st=d.lstat()
    require(d.resolve()==d and stat.S_ISDIR(st.st_mode) and st.st_uid==os.getuid()
            and st.st_dev==receipt['device'] and st.st_ino==receipt['inode'])
    members=list(d.rglob('*')); known_dirs={'inputs'} if any(name.startswith('inputs/') for name in files) else set()
    require({str(p.relative_to(d)) for p in members} == set(files) | known_dirs)
    owned=[]
    for p in members:
        rel=str(p.relative_to(d)); st=p.lstat()
        require(p.resolve()==p and st.st_uid==os.getuid())
        if rel in known_dirs:
            require(stat.S_ISDIR(st.st_mode));continue
        require(stat.S_ISREG(st.st_mode) and st.st_nlink==1)
        h=hashlib.sha256()
        with p.open('rb') as src:
            for chunk in iter(lambda:src.read(262144),b''): h.update(chunk)
        require(h.hexdigest()==files[rel]);owned.append((p,st.st_dev,st.st_ino))
    for p,dev,ino in owned:
        st=p.lstat(); require(st.st_dev==dev and st.st_ino==ino);p.unlink()
    for rel in known_dirs:(d/rel).rmdir()
    d.rmdir()
    return {'unusedPackageRemoved': True, 'attempt': attempt, 'sha256': expected}


if __name__=='__main__':
    os.umask(0o077)
    require(pwd.getpwuid(os.getuid()).pw_name=='mathwidu' and socket.gethostname()=='srv1397065' and Path.home()==Path('/home/mathwidu'))
    root=Path.home()
    for component in ('.local','share','vanrides','wa110-vps-lab'):
        root=root/component
        if not root.exists(): root.mkdir(mode=0o700)
        require(root.is_dir() and not root.is_symlink() and root.resolve()==root and root.stat().st_uid==os.getuid())
    if len(sys.argv)==7 and sys.argv[5]=='unstage-unused':
        result=unstage_unused(root,sys.argv[1],sys.argv[2],json.loads(sys.argv[4]),json.loads(sys.argv[6]))
    else:
        require(len(sys.argv)==5)
        result=stage(root,sys.argv[1],sys.argv[2],int(sys.argv[3]),json.loads(sys.argv[4]),sys.stdin.buffer)
    print(json.dumps(result))
