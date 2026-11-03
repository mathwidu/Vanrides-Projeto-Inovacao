#!/usr/bin/env python3
"""Copy reviewed sources into a NEW private runtime; never runs SSH, QA or a device."""
import argparse
import hashlib
import json
import os
from pathlib import Path
import stat
import sys

SOURCE = Path(__file__).resolve().parent
FILES = ('protocol.py','transport.py','phone_guard_core.py','launch_vps110_mac.py','test_mac_prep.py')


def need(value, code):
    if not value: raise ValueError(code)


def sha(raw): return hashlib.sha256(raw).hexdigest()


def private_directory(path):
    need(not path.is_symlink(),'PRIVATE_DIRECTORY_SYMLINK')
    path.mkdir(mode=0o700,parents=True,exist_ok=True)
    info=path.stat()
    need(info.st_uid==os.getuid() and stat.S_IMODE(info.st_mode)==0o700,'PRIVATE_DIRECTORY_REQUIRED')


def install(destination=None):
    pins=json.loads((SOURCE/'SOURCE_PINS.json').read_bytes())
    need(set(pins['versionedFiles'])==set(FILES),'SOURCE_WHITELIST_CHANGED')
    payload={}
    for name in FILES:
        path=SOURCE/name
        need(path.is_file() and not path.is_symlink(),'SOURCE_TYPE_CHANGED')
        payload[name]=path.read_bytes()
        need(sha(payload[name])==pins['versionedFiles'][name],'SOURCE_HASH_CHANGED')
    bundle=sha(json.dumps(pins['versionedFiles'],sort_keys=True,separators=(',',':')).encode())
    base=Path.home()/'.local/share/vanrides/wa110/mac-runtimes'
    destination=Path(destination).expanduser().absolute() if destination else base/('source-'+bundle[:16])
    need(destination.parent==base and destination.name not in ('.','..'),'DESTINATION_MUST_BE_DIRECT_CHILD_OF_PRIVATE_BASE')
    need(not destination.exists() and not destination.is_symlink(),'NEW_DESTINATION_REQUIRED_NO_OVERWRITE')
    need(not any(parent.is_symlink() for parent in (base,*base.parents)),'PRIVATE_PARENT_SYMLINK')
    private_directory(base)
    destination.mkdir(mode=0o700)
    for name,raw in payload.items():
        fd=os.open(destination/name,os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
        with os.fdopen(fd,'wb') as target: target.write(raw)
    receipt={'schemaVersion':1,'sourceBundleSha256':bundle,'files':pins['versionedFiles'],
             'runtimeStarted':False,'specsSecretsOrReceiptsImported':False}
    fd=os.open(destination/'INSTALLED_SOURCES.json',os.O_WRONLY|os.O_CREAT|os.O_EXCL,0o600)
    with os.fdopen(fd,'w') as target: json.dump(receipt,target,indent=2,sort_keys=True);target.write('\n')
    return destination,receipt


def main():
    os.umask(0o077)
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--destination',type=Path)
    args=parser.parse_args()
    try:
        destination,receipt=install(args.destination)
        print(json.dumps({'status':'INSTALLED_SOURCES_ONLY','directory':str(destination),
                          'sourceBundleSha256':receipt['sourceBundleSha256'],'runtimeStarted':False}))
        return 0
    except (OSError,ValueError,KeyError):
        print(json.dumps({'status':'INSTALL_BLOCKED','runtimeStarted':False}))
        return 1


if __name__=='__main__': raise SystemExit(main())
