"""Private scoped phone guard core; no launcher, JVM, SSH or import-time runtime actions.
Adapted from reviewed guard SHA256 8f76a540f6138fe16aa390b53406c52f2ab53c805f16600a526337fcf6b696f8.
"""
from __future__ import annotations
import argparse
from datetime import datetime, timezone
import fcntl
import hashlib
import json
import os
from pathlib import Path
import re
import selectors
import signal
import stat
import subprocess
import sys
import time
import uuid
PACKAGE = 'dev.matheusduarte.vanrides.driver.lab'
SERVICE = '.driver.DriverTrackingService'
APK_SHA = 'e022b8f2eafe1caa0f3eed13ea7e20d48e27f8a85076383d0175f98bf6b94cce'
READER_SHA = '613ae571ac0b8cf78c212344edb0e890eee2203c329f9106c056c44af928b7c5'
JAR_NAME = re.compile('qa-wa110-ui-[a-f0-9]{32}\\.(?:jar|dex|odex|vdex|art)')
HASH = re.compile('[a-f0-9]{64}')

class GuardFailure(ValueError):
    pass

class GuardInterrupted(BaseException):
    pass

def require(value, code):
    if not value:
        raise GuardFailure(code)

def now():
    return datetime.now(timezone.utc).isoformat()

def sha(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as stream:
        for chunk in iter(lambda : stream.read(262144), b''):
            value.update(chunk)
    return value.hexdigest()

def private_dir(path):
    path = Path(path).expanduser().absolute()
    require(not path.is_symlink(), 'PRIVATE_DIRECTORY_SYMLINK')
    path.mkdir(mode=448, parents=True, exist_ok=True)
    info = path.stat()
    require(info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 448, 'DIRECTORY_NOT_PRIVATE')
    return path.resolve()

def write_journal(path, value):
    path = Path(path)
    require(not path.is_symlink(), 'JOURNAL_SYMLINK')
    temporary = path.with_name('.' + path.name + '.' + uuid.uuid4().hex + '.pending')
    try:
        descriptor = os.open(temporary, os.O_CREAT | os.O_EXCL | os.O_WRONLY, 384)
        with os.fdopen(descriptor, 'w') as stream:
            json.dump(value, stream, indent=2, sort_keys=True)
            stream.write('\n')
            stream.flush()
            os.fsync(stream.fileno())
        os.replace(temporary, path)
        directory = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    finally:
        temporary.unlink(missing_ok=True)

class DeviceLock:

    def __init__(self, directory, fingerprint):
        require(HASH.fullmatch(fingerprint), 'SERIAL_FINGERPRINT_INVALID')
        self.path = directory / ('device-' + fingerprint + '.lock')
        self.fd = None

    def __enter__(self):
        self.fd = os.open(self.path, os.O_CREAT | os.O_RDWR | getattr(os, 'O_NOFOLLOW', 0), 384)
        info = os.fstat(self.fd)
        require(stat.S_ISREG(info.st_mode) and info.st_uid == os.getuid() and (stat.S_IMODE(info.st_mode) == 384), 'DEVICE_LOCK_NOT_PRIVATE')
        try:
            fcntl.flock(self.fd, fcntl.LOCK_EX | fcntl.LOCK_NB)
        except BlockingIOError:
            os.close(self.fd)
            self.fd = None
            raise GuardFailure('ANOTHER_PHONE_GUARD_OWNS_DEVICE') from None
        return self

    def __exit__(self, *unused):
        if self.fd is not None:
            os.close(self.fd)
            self.fd = None

class AdbPhone:

    def __init__(self, adb, fingerprint=None):
        self.adb = str(adb)
        self.expected_fingerprint = fingerprint
        self.serial = None

    def command(self, *args, timeout=15):
        result = subprocess.run([self.adb, *map(str, args)], capture_output=True, timeout=timeout)
        require(result.returncode == 0, 'PHONE_READ_OR_SCOPED_COMMAND_FAILED')
        return result.stdout.decode(errors='replace').strip()

    def bind(self):
        rows = [line.split() for line in self.command('devices').splitlines()[1:] if line.strip()]
        require(len(rows) == 1 and len(rows[0]) == 2 and (rows[0][1] == 'device'), 'ONE_AUTHORIZED_DEVICE_REQUIRED')
        serial = rows[0][0]
        fingerprint = hashlib.sha256(serial.encode()).hexdigest()
        if self.expected_fingerprint:
            require(fingerprint == self.expected_fingerprint, 'PHONE_SERIAL_CHANGED')
        (self.serial, self.expected_fingerprint) = (serial, fingerprint)
        require(self.call('shell', 'getprop', 'ro.product.model') == 'Redmi Note 8', 'PINNED_REDMI_REQUIRED')
        return fingerprint

    def call(self, *args, **kwargs):
        require(self.serial is not None, 'PHONE_NOT_BOUND')
        return self.command('-s', self.serial, *args, **kwargs)

    def remote_hash(self, file):
        output = self.call('shell', 'sha256sum', file)
        require(re.fullmatch('[a-f0-9]{64}\\s+\\S+', output), 'REMOTE_HASH_UNAVAILABLE')
        return output.split()[0]

    def reverse(self):
        result = []
        for line in self.call('reverse', '--list').splitlines():
            fields = line.split()
            require(len(fields) in (2, 3), 'REVERSE_LIST_AMBIGUOUS')
            if fields[-2] == 'tcp:18443':
                result.append(fields[-2:])
        return result

    def service(self):
        text = self.call('shell', 'dumpsys', 'activity', 'services', PACKAGE)
        return any(('ServiceRecord{' in line and SERVICE in line for line in text.splitlines()))

    def jars(self):
        result = {}
        for name in self.call('shell', 'ls', '-1', '/data/local/tmp').splitlines():
            if not JAR_NAME.fullmatch(name):
                continue
            file = '/data/local/tmp/' + name
            kind = self.call('shell', 'stat', '-c', '%F', file)
            require(kind == 'regular file', 'QA_JAR_IDENTITY_UNCERTAIN')
            result[name] = self.remote_hash(file)
        return result

    def snapshot(self):
        fingerprint = self.bind()
        location = self.call('shell', 'pm', 'path', PACKAGE)
        require(re.fullmatch('package:/data/app/[A-Za-z0-9_./+=~-]+/base\\.apk', location), 'LAB_INSTALLED_APK_PATH_INVALID')
        apk = self.remote_hash(location.removeprefix('package:'))
        require(apk == APK_SHA, 'PHONE_APK_CHANGED')
        return {'serialFingerprint': fingerprint, 'apkSha256': apk, 'servicePresent': self.service(), 'reverse18443': self.reverse(), 'qaJars': self.jars()}

    def force_stop(self):
        self.call('shell', 'am', 'force-stop', PACKAGE)

    def remove_reverse(self):
        require(self.reverse() == [['tcp:18443', 'tcp:18443']], 'REVERSE_CHANGED_BEFORE_REMOVAL')
        self.call('reverse', '--remove', 'tcp:18443')

def same_phone(before, after):
    require(after['serialFingerprint'] == before['serialFingerprint'] and after['apkSha256'] == before['apkSha256'] == APK_SHA, 'PHONE_IDENTITY_CHANGED_NO_MUTATION')

def clean_phone(phone, journal, persist=lambda : None):
    """Only a launched attempt owns newly active service and its formerly vacant exact reverse."""
    before = journal['before']
    observed = phone.snapshot()
    same_phone(before, observed)
    require(before['servicePresent'] is False and before['reverse18443'] == [], 'PREEXISTING_RESOURCE_NOT_OWNED')
    require(journal.get('childSpawned') is True, 'EXECUTOR_NOT_LAUNCHED_NO_CLEANUP_AUTHORITY')
    (codes, actions) = ([], journal.setdefault('phoneActions', []))
    if observed['servicePresent']:
        journal['pendingPhoneAction'] = 'FORCE_STOP_ONLY_DRIVER_LAB'
        persist()
        same_phone(before, phone.snapshot())
        phone.force_stop()
        actions.append('force_stop_only_driver_lab')
        observed = phone.snapshot()
        same_phone(before, observed)
        if observed['servicePresent']:
            codes.append('LAB_SENSOR_STOP_UNCONFIRMED')
    if observed['reverse18443']:
        if observed['reverse18443'] == [['tcp:18443', 'tcp:18443']]:
            journal['pendingPhoneAction'] = 'REMOVE_ONLY_ATTEMPT_REVERSE_18443'
            persist()
            fresh = phone.snapshot()
            same_phone(before, fresh)
            require(fresh['reverse18443'] == [['tcp:18443', 'tcp:18443']], 'REVERSE_CHANGED_NO_REMOVAL')
            phone.remove_reverse()
            actions.append('remove_only_reverse_18443')
        else:
            codes.append('UNEXPECTED_REVERSE_18443_PRESERVED')
    final = phone.snapshot()
    same_phone(before, final)
    if final['servicePresent']:
        codes.append('LAB_SENSOR_STILL_ACTIVE')
    if final['reverse18443']:
        codes.append('REVERSE_18443_STILL_PRESENT')
    for (name, digest) in before['qaJars'].items():
        if final['qaJars'].get(name) != digest:
            codes.append('PREEXISTING_QA_JAR_CHANGED')
    new = {name: digest for (name, digest) in final['qaJars'].items() if name not in before['qaJars']}
    if new:
        journal['preservedNewQaJars'] = new
        codes.append('QA_JAR_WITHOUT_ATTEMPT_MARKER_PRESERVED')
        if any((value != READER_SHA for value in new.values())):
            codes.append('UNEXPECTED_QA_JAR_HASH_PRESERVED')
    journal.pop('pendingPhoneAction', None)
    journal['after'] = final
    journal['phoneCleanupCodes'] = sorted(set(codes))
    journal['phoneResourcesVerified'] = not codes
    persist()
    return not codes

def verify_unchanged_without_spawn(phone, journal):
    observed = phone.snapshot()
    same_phone(journal['before'], observed)
    require(observed == journal['before'], 'UNRECORDED_LAUNCH_RESOURCE_STATE_CHANGED_NO_MUTATION')
    journal.update(after=observed, phoneCleanupCodes=[], phoneResourcesVerified=True)
    return True

def reconcile_after_executor(phone, journal, quiet, persist=lambda : None):
    clean = clean_phone(phone, journal, persist)
    if not quiet:
        journal['phoneResourcesVerified'] = False
        journal['phoneCleanupCodes'] = sorted(set(journal['phoneCleanupCodes'] + ['OWNED_EXECUTOR_STILL_UNCONFIRMED']))
        persist()
    return clean and quiet

def group_exists(pgid):
    try:
        os.killpg(pgid, 0)
        return True
    except ProcessLookupError:
        return False
    except PermissionError:
        return True

def stop_owned_child(child):
    """Only the live Popen child's newly created session/group; never used by recover."""
    if child.poll() is None:
        try:
            os.killpg(child.pid, signal.SIGINT)
        except ProcessLookupError:
            pass
        try:
            child.wait(timeout=180)
        except subprocess.TimeoutExpired:
            try:
                os.killpg(child.pid, signal.SIGTERM)
            except ProcessLookupError:
                pass
            try:
                child.wait(timeout=10)
            except subprocess.TimeoutExpired:
                try:
                    os.killpg(child.pid, signal.SIGKILL)
                except ProcessLookupError:
                    pass
                child.wait(timeout=5)
    require(not group_exists(child.pid), 'OWNED_EXECUTOR_GROUP_NOT_CLOSED')
