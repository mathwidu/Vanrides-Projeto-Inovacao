#!/usr/bin/env python3
"""Prepare private MVP XCUITest commands or open a private link on an authorized iPhone.

Preparation never builds, installs, signs or operates a device. Only the explicit
open-link subcommand operates a device. No token, code, device ID or URL is printed.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import stat
import subprocess
import sys

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'integration_test/driver_mvp_ios/DriverMVPUITests.swift'
TARGET = 'DriverMVPUITests'
DRIVER = 'dev.matheusduarte.vanrides.driver'
COMMANDS = ('inspect', 'pair', 'copy-code', 'open-whatsapp', 'refresh', 'confirm-start', 'stop')


class PreparationError(Exception):
    pass


def private_path(value):
    path = Path(value).expanduser()
    if path.is_symlink():
        raise PreparationError('SYMLINK_REFUSED')
    path = path.resolve()
    if any((parent / '.git').exists() for parent in (path, *path.parents)):
        raise PreparationError('PRIVATE_PATH_OUTSIDE_GIT_REQUIRED')
    return path


def read_private(value):
    path = private_path(value)
    details = path.stat()
    if not stat.S_ISREG(details.st_mode) or details.st_uid != os.getuid() or stat.S_IMODE(details.st_mode) & 0o077:
        raise PreparationError('PRIVATE_FILE_MODE_0600_REQUIRED')
    if details.st_size > 4096:
        raise PreparationError('PRIVATE_FILE_TOO_LARGE')
    data = json.loads(path.read_bytes())
    if not isinstance(data, dict):
        raise PreparationError('INVALID_PRIVATE_INPUT')
    return data


def write_private(path, data):
    with os.fdopen(os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), 'wb') as output:
        output.write(data)


def patch_run(document, runner_id):
    if not isinstance(runner_id, str) or not re.fullmatch(r'[A-Za-z0-9.-]+\.xctrunner', runner_id):
        raise PreparationError('UITEST_RUNNER_REQUIRED')
    if 'TestConfigurations' in document:
        configurations = document['TestConfigurations']
        if not isinstance(configurations, list) or len(configurations) != 1:
            raise PreparationError('SINGLE_TEST_CONFIGURATION_REQUIRED')
        targets = configurations[0].get('TestTargets', [])
    else:
        targets = [value for key, value in document.items() if not key.startswith('__') and isinstance(value, dict)]
    if len(targets) != 1 or not isinstance(targets[0], dict):
        raise PreparationError('SINGLE_MVP_TARGET_REQUIRED')
    target = targets[0]
    if TARGET not in {target.get(key) for key in ('BlueprintName', 'ProductModuleName', 'TargetName')}:
        raise PreparationError('MVP_TARGET_REQUIRED')
    for key in ('TestBundlePath', 'TestHostPath', 'UITargetAppPath'):
        target.pop(key, None)
    target.update({
        'UseDestinationArtifacts': True,
        'TestHostBundleIdentifier': runner_id,
        'TestBundleDestinationRelativePath': '__TESTHOST__/PlugIns/' + TARGET + '.xctest',
        'UITargetAppBundleIdentifier': DRIVER,
        'DependentProductPaths': [],
        'SystemAttachmentLifetime': 'keepNever', 'UserAttachmentLifetime': 'keepNever',
        'ParallelizationEnabled': False, 'CodeCoverageEnabled': False,
        'GatherLocalizableStringsData': False,
        'EnvironmentVariables': {}, 'CommandLineArguments': [],
        'UITargetAppEnvironmentVariables': {}, 'UITargetAppCommandLineArguments': [],
    })
    document.pop('CodeCoverageBuildableInfos', None)
    return document


def prepare(args):
    output = private_path(args.output_dir)
    if output.exists():
        raise PreparationError('NEW_OUTPUT_DIRECTORY_REQUIRED')
    runner = private_path(args.runner_app)
    if not runner.is_dir() or not (runner / ('PlugIns/' + TARGET + '.xctest')).is_dir():
        raise PreparationError('MVP_RUNNER_APP_REQUIRED')
    info = plistlib.loads((runner / 'Info.plist').read_bytes())
    source = private_path(args.source_xctestrun)
    document = patch_run(plistlib.loads(source.read_bytes()), info.get('CFBundleIdentifier'))
    output.mkdir(mode=0o700, parents=True)
    output.chmod(0o700)
    write_private(output / 'run.xctestrun', plistlib.dumps(document))
    fixture = {'schemaVersion': 1, 'command': args.command, 'sourceDigest': hashlib.sha256(SOURCE.read_bytes()).hexdigest()}
    write_private(output / 'command.json', json.dumps(fixture, sort_keys=True).encode())


def validate_link(value, host):
    if not isinstance(value, str) or '%' in value or not isinstance(host, str):
        raise PreparationError('INVALID_PRIVATE_LINK')
    if host and not re.fullmatch(r'[a-z0-9-]+(?:\.[a-z0-9-]+)+', host):
        raise PreparationError('REVIEWED_LINK_HOST_REQUIRED')
    if value == 'vanridesdriver://pairing/return' or host and value == 'https://' + host + '/motorista/vinculo/retorno':
        return value
    prefix = r'(?:vanridesdriver://run/start' + (r'|https://' + re.escape(host) + '/motorista/saida' if host else '') + ')'
    if not re.fullmatch(prefix + r'#v1\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(?:0|[1-9][0-9]{0,15})\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]', value):
        raise PreparationError('INVALID_PRIVATE_LINK')
    version = int(value.split('#v1.', 1)[1].split('.')[1])
    if version > 9_007_199_254_740_991:
        raise PreparationError('INVALID_PRIVATE_LINK')
    return value


def open_link(args):
    data = read_private(args.input)
    if set(data) != {'device', 'url'} or not isinstance(data['device'], str) or not re.fullmatch(r'[A-Za-z0-9-]{8,80}', data['device']):
        raise PreparationError('INVALID_PRIVATE_INPUT')
    url = validate_link(data['url'], args.host)
    result = subprocess.run(['xcrun', 'devicectl', 'device', 'process', 'launch', '--quiet',
                             '--device', data['device'], '--payload-url', url, DRIVER],
                            capture_output=True, timeout=25, check=False)
    if result.returncode:
        raise PreparationError('DEVICE_LINK_OPEN_FAILED')


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    sub = parser.add_subparsers(dest='action', required=True)
    prep = sub.add_parser('prepare', help='Prepare files only; no device action')
    for name in ('source-xctestrun', 'runner-app', 'output-dir'):
        prep.add_argument('--' + name, required=True)
    prep.add_argument('--command', choices=COMMANDS, default='inspect')
    link = sub.add_parser('open-link', help='Explicit device action using a private 0600 JSON input')
    link.add_argument('--input', required=True)
    link.add_argument('--host', default='', help='Reviewed HTTPS host; empty accepts the custom scheme only')
    args = parser.parse_args()
    try:
        (prepare if args.action == 'prepare' else open_link)(args)
    except PreparationError as error:
        print('BLOCKED: ' + str(error), file=sys.stderr)
        return 2
    except Exception:
        print('BLOCKED: LOCAL_OPERATION_FAILED', file=sys.stderr)
        return 2
    print('Private command prepared; no device action executed.' if args.action == 'prepare' else 'Private link delivered; start still requires explicit confirmation.')
    return 0


if __name__ == '__main__':
    sys.exit(main())
