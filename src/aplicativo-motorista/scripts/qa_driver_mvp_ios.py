#!/usr/bin/env python3
"""Prepare private MVP XCUITest commands or open a private link on an authorized iPhone.

Preparation never builds, installs, signs or operates a device. Only the explicit
open-link subcommand operates a device. No token, code, device ID or URL is printed.
sanitize-export removes only the validated local export's raw XCTest artifacts.
"""
import argparse
import hashlib
import json
import math
import os
from pathlib import Path
import plistlib
import re
import shutil
import stat
import subprocess
import sys
import tempfile

ROOT = Path(__file__).resolve().parents[1]
SOURCE = ROOT / 'integration_test/driver_mvp_ios/DriverMVPUITests.swift'
TARGET = 'DriverMVPUITests'
DRIVER = 'dev.matheusduarte.vanrides.driver'
COMMANDS = ('inspect', 'pair', 'copy-code', 'export-pairing-code', 'open-whatsapp', 'refresh', 'confirm-start', 'stop')
EXPORT_LOG_REDACTED = b'Private XCTest export log removed; retain only the sanitized receipt.\n'
EXPORT_RECEIPT = 'export-sanitization.private.json'


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
        output.flush()
        os.fsync(output.fileno())


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
    if args.command not in COMMANDS:
        raise PreparationError('MVP_COMMAND_REQUIRED')
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


def export_action_path(value):
    path = Path(value).expanduser().absolute()
    if '..' in path.parts:
        raise PreparationError('EXPORT_PATH_TRAVERSAL_REFUSED')
    for parent in (path, *path.parents):
        if parent.is_symlink():
            raise PreparationError('EXPORT_SYMLINK_REFUSED')
        if (parent / '.git').exists() or (parent / '.git').is_symlink():
            raise PreparationError('PRIVATE_PATH_OUTSIDE_GIT_REQUIRED')
    details = path.stat()
    if not stat.S_ISDIR(details.st_mode) or details.st_uid != os.getuid() or stat.S_IMODE(details.st_mode) != 0o700:
        raise PreparationError('PRIVATE_ACTION_DIRECTORY_0700_REQUIRED')
    return path


def validate_raw_bundle(bundle):
    def checked(path):
        details = path.lstat()
        if stat.S_ISLNK(details.st_mode):
            raise PreparationError('EXPORT_SYMLINK_REFUSED')
        if details.st_uid != os.getuid() or not (stat.S_ISREG(details.st_mode) or stat.S_ISDIR(details.st_mode)):
            raise PreparationError('EXPORT_RAW_PATH_REFUSED')
    if not bundle.exists() and not bundle.is_symlink():
        return
    checked(bundle)
    if not bundle.is_dir():
        raise PreparationError('EXPORT_RAW_BUNDLE_DIRECTORY_REQUIRED')
    def failed(_):
        raise PreparationError('EXPORT_RAW_PATH_REFUSED')
    for directory, children, files in os.walk(bundle, followlinks=False, onerror=failed):
        for name in (*children, *files):
            checked(Path(directory) / name)


def safe_test_summary(document):
    names = ('totalTestCount', 'passedTests', 'failedTests', 'skippedTests', 'expectedFailures')
    statuses = {'Passed', 'Failed', 'Skipped', 'Expected Failure', 'unknown'}
    if not isinstance(document, dict) or document.get('result') not in statuses:
        raise PreparationError('EXPORT_SUMMARY_INVALID')
    counts = {name: document.get(name) for name in names}
    if any(type(value) is not int or value < 0 for value in counts.values()):
        raise PreparationError('EXPORT_SUMMARY_INVALID')
    result = {'counts': counts, 'status': document['result']}
    if 'startTime' in document or 'finishTime' in document:
        times = [document.get(name) for name in ('startTime', 'finishTime')]
        if any(type(value) not in (int, float) or not math.isfinite(value) for value in times):
            raise PreparationError('EXPORT_SUMMARY_INVALID')
        duration = times[1] - times[0]
        if not math.isfinite(duration) or duration < 0:
            raise PreparationError('EXPORT_SUMMARY_INVALID')
        result['durationSeconds'] = duration
    return result


def replace_private(path, data):
    descriptor, temporary = tempfile.mkstemp(prefix='.export-sanitizing-', dir=path.parent)
    try:
        with os.fdopen(descriptor, 'wb') as output:
            output.write(data)
            output.flush()
            os.fsync(output.fileno())
        os.replace(temporary, path)
    finally:
        if os.path.exists(temporary):
            os.unlink(temporary)


def sanitize_export(args):
    action = export_action_path(args.action_dir)
    command_path = action / 'command.json'
    command = read_private(command_path)
    if stat.S_IMODE(command_path.stat().st_mode) != 0o600 or set(command) != {'schemaVersion', 'command', 'sourceDigest'} or \
       type(command['schemaVersion']) is not int or command['schemaVersion'] != 1 or \
       command['command'] != 'export-pairing-code' or not isinstance(command['sourceDigest'], str) or \
       not re.fullmatch(r'[0-9a-fA-F]{64}', command['sourceDigest']):
        raise PreparationError('EXPORT_ACTION_REQUIRED')
    bundle = action / 'Result.xcresult'
    log = action / 'test.private.log'
    receipt_path = action / EXPORT_RECEIPT
    validate_raw_bundle(bundle)
    if log.is_symlink() or receipt_path.exists() or receipt_path.is_symlink():
        raise PreparationError('NEW_EXPORT_RECEIPT_AND_REGULAR_LOG_REQUIRED')
    if log.exists() and (not log.is_file() or log.stat().st_uid != os.getuid()):
        raise PreparationError('EXPORT_LOG_REFUSED')
    receipt = {'schemaVersion': 1,
               'provenance': {'command': 'export-pairing-code', 'sourceDigest': command['sourceDigest'].lower(),
                              'summarySchemaVersion': '0.5.0'},
               'summaryExtraction': 'failed', 'rawBundleRemoved': False, 'testLogReplaced': False,
               'cleanupStatus': 'pending'}
    try:
        extracted = subprocess.run(['xcrun', 'xcresulttool', 'get', 'test-results', 'summary',
                                    '--schema-version', '0.5.0', '--path', str(bundle), '--compact'],
                                   capture_output=True, timeout=30, check=False)
        if extracted.returncode:
            raise PreparationError('EXPORT_SUMMARY_EXTRACTION_FAILED')
        document = json.loads(extracted.stdout, parse_constant=lambda _: None)
        receipt['summary'] = safe_test_summary(document)
        receipt['summaryExtraction'] = 'passed'
    except Exception:
        receipt['summaryFailureCode'] = 'EXPORT_SUMMARY_EXTRACTION_FAILED'
    # Persist only typed data before deleting anything. Never preserve tool text.
    write_private(receipt_path, json.dumps(receipt, sort_keys=True, allow_nan=False).encode())
    try:
        validate_raw_bundle(bundle)
        if bundle.exists():
            shutil.rmtree(bundle)
        receipt['rawBundleRemoved'] = not bundle.exists() and not bundle.is_symlink()
    except Exception:
        receipt['rawBundleRemoved'] = False
    try:
        replace_private(log, EXPORT_LOG_REDACTED)
        receipt['testLogReplaced'] = log.read_bytes() == EXPORT_LOG_REDACTED and stat.S_IMODE(log.stat().st_mode) == 0o600
    except Exception:
        receipt['testLogReplaced'] = False
    cleaned = receipt['rawBundleRemoved'] and receipt['testLogReplaced']
    receipt['cleanupStatus'] = 'passed' if cleaned else 'failed'
    replace_private(receipt_path, json.dumps(receipt, sort_keys=True, allow_nan=False).encode())
    if not cleaned:
        raise PreparationError('EXPORT_CLEANUP_FAILED')
    if receipt['summaryExtraction'] != 'passed':
        raise PreparationError('EXPORT_SUMMARY_EXTRACTION_FAILED')


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
    sanitizer = sub.add_parser('sanitize-export', help='Retain a typed receipt and remove raw export XCTest artifacts')
    sanitizer.add_argument('--action-dir', required=True)
    args = parser.parse_args()
    try:
        {'prepare': prepare, 'open-link': open_link, 'sanitize-export': sanitize_export}[args.action](args)
    except PreparationError as error:
        print('BLOCKED: ' + str(error), file=sys.stderr)
        return 2
    except Exception:
        print('BLOCKED: LOCAL_OPERATION_FAILED', file=sys.stderr)
        return 2
    messages = {'prepare': 'Private command prepared; no device action executed.',
                'open-link': 'Private link delivered; start still requires explicit confirmation.',
                'sanitize-export': 'Private export artifacts sanitized; retain only the typed receipt.'}
    print(messages[args.action])
    return 0


if __name__ == '__main__':
    sys.exit(main())
