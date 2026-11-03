#!/usr/bin/env python3
"""Create a private, durable test certificate for direct APK installation."""

import argparse
import os
from pathlib import Path
import secrets
import shutil
import subprocess


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--directory', required=True, type=Path)
    parser.add_argument('--keytool', default=shutil.which('keytool'))
    args = parser.parse_args()
    directory = args.directory.expanduser().resolve()
    repository = Path(__file__).resolve().parents[1]
    if directory == repository or repository in directory.parents:
        parser.error('Keep signing material outside the repository.')
    existing_parent = directory.parent
    while not existing_parent.exists():
        existing_parent = existing_parent.parent
    if subprocess.run(
        ['git', '-C', str(existing_parent), 'rev-parse', '--show-toplevel'],
        stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
    ).returncode == 0:
        parser.error('Keep signing material outside every Git checkout.')
    if directory.exists():
        parser.error('Destination already exists; preserve and reuse its certificate.')
    if not args.keytool:
        parser.error('Provide the keytool executable from the configured Java SDK.')

    os.umask(0o077)
    directory.mkdir(mode=0o700, parents=True)
    password = secrets.token_urlsafe(36)
    password_file = directory / '.generation-password'
    password_file.write_text(password, encoding='utf-8')
    keystore = directory / 'driver-test.p12'
    try:
        subprocess.run(
            [
                args.keytool, '-genkeypair', '-noprompt', '-storetype', 'PKCS12',
                '-keystore', str(keystore), '-alias', 'vanrides-driver-test',
                '-keyalg', 'RSA', '-keysize', '3072', '-validity', '10000',
                '-dname', 'CN=VanRides Driver Test,O=VanRides,C=BR',
                '-storepass:file', str(password_file),
                '-keypass:file', str(password_file),
            ],
            check=True,
        )
        properties = directory / 'driver-signing.properties'
        escaped_path = str(keystore).replace('\\', '\\\\')
        properties.write_text(
            f'storeFile={escaped_path}\nstorePassword={password}\n'
            f'keyAlias=vanrides-driver-test\nkeyPassword={password}\n',
            encoding='utf-8',
        )
        keystore.chmod(0o600)
        print(f'Signing file: {properties}')
        print('Preserve this private directory to sign future updates with the same key.')
    finally:
        password_file.unlink(missing_ok=True)


if __name__ == '__main__':
    main()
