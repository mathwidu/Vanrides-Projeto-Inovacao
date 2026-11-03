#!/usr/bin/env python3
"""Prepare or run only the temporary VPS110 Mac/Redmi endpoint. Never starts a JVM.

prepare/plan are offline: no SSH connection, phone or listener. run/recover are
reserved for the principal after explicit authorization of the exact spec hash.
The spec hash is an input-integrity check, not evidence of user authorization.
"""
import argparse
import hashlib
import json
import os
from pathlib import Path
import re
import secrets
import selectors
import signal
import socket
import stat
import subprocess
import sys
import tempfile
import time

import phone_guard_core as g
import protocol as p
import transport as t

ROOT = Path(__file__).resolve().parent
QA_COMMIT = 'fa1da0af3d66013f04742060952b3728c76217bb'
RUNTIME_FILES = ('protocol.py', 'transport.py', 'phone_guard_core.py', 'launch_vps110_mac.py')


def resolved(value): return Path(value).expanduser().resolve()


def require_private_runtime():
    """Versioned sources cannot create specs, nonces or journals in a checkout."""
    info = ROOT.lstat()
    p.need(not ROOT.is_symlink() and stat.S_ISDIR(info.st_mode) and info.st_uid == os.getuid()
           and stat.S_IMODE(info.st_mode) == 0o700
           and not any((parent / '.git').exists() for parent in (ROOT,*ROOT.parents)),
           'INSTALL_IN_PRIVATE_DIRECTORY_OUTSIDE_GIT_REQUIRED')


def local_command(args):
    result = subprocess.run(list(map(str, args)), capture_output=True, timeout=15)
    p.need(result.returncode == 0, 'LOCAL_PREFLIGHT_FAILED')
    return result.stdout


def validate_files(spec):
    p.validate_spec(spec); paths = {key: resolved(value) for key, value in spec['paths'].items()}
    p.need(paths['qaRoot'] == Path.home() / '.local/share/vanrides/qa-worktrees/wa110-automacao-redmi', 'QA_ROOT_NOT_ALLOWED')
    p.need(g.sha(paths['apk']) == p.APK and g.sha(paths['readerJar']) == p.READER, 'LOCAL_APK_OR_READER_CHANGED')
    qa = paths['qaRoot'] / 'integration_test/driver_run/android_lab.py'
    p.need(g.sha(qa) == spec['qaRunnerSha256'], 'QA_RUNNER_CHANGED')
    p.need(local_command(['git', '-C', paths['qaRoot'], 'rev-parse', 'HEAD']).decode().strip() == spec['qaCommit'] == QA_COMMIT
           and local_command(['git', '-C', paths['qaRoot'], 'status', '--porcelain']) == b'', 'QA_WORKTREE_NOT_PINNED_CLEAN')
    p.need(g.sha(paths['tlsDirectory'] / 'ca.der') == p.CA, 'PUBLIC_CA_CHANGED')
    for file in (resolved(spec['ssh']['identityFile']), paths['tlsDirectory'] / 'leaf-key.pem'):
        info = file.stat()
        p.need(file.is_file() and info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) & 0o077 == 0, 'PRIVATE_KEY_FILE_PERMISSIONS_INVALID')
    p.need(resolved(spec['ssh']['knownHostsFile']).is_file(), 'VERIFIED_KNOWN_HOSTS_REQUIRED')
    tls = paths['tlsDirectory']
    p.need(hashlib.sha256(local_command(['/usr/bin/openssl', 'x509', '-in', tls / 'ca.pem', '-outform', 'DER'])).hexdigest()
           == p.CA, 'PUBLIC_CA_PEM_DER_MISMATCH')
    local_command(['/usr/bin/openssl', 'verify', '-CAfile', tls / 'ca.pem', tls / 'leaf.pem'])
    certificate = local_command(['/usr/bin/openssl', 'x509', '-in', tls / 'leaf.pem', '-noout', '-text']).decode()
    p.need(re.search(r'X509v3 Subject Alternative Name:\s*\n\s*DNS:localhost\s*\n', certificate), 'LOCALHOST_CERTIFICATE_NAME_INVALID')
    for name in ('ca.pem', 'leaf.pem'):
        local_command(['/usr/bin/openssl', 'x509', '-in', tls / name, '-noout', '-checkend', '1200'])
    return paths


def prepare(attempt, output, module_sha256):
    require_private_runtime()
    home = Path.home(); base = home / '.local/share/vanrides'
    qa = base / 'qa-worktrees/wa110-automacao-redmi'
    spec = {'schemaVersion': 2, 'attemptId': attempt, 'sourceSha': p.SOURCE, 'backendJarSha256': p.JAR,
            'apkSha256': p.APK, 'publicCaSha256': p.CA, 'readerJarSha256': p.READER,
            'remoteProfile': p.PROFILE, 'remoteModuleSha256': module_sha256,
            'qaCommit': QA_COMMIT, 'qaRunnerSha256': g.sha(qa / 'integration_test/driver_run/android_lab.py'),
            'allowPermissions': True,
            'ssh': {'host': '187.77.47.96', 'user': 'mathwidu', 'port': 22,
                    'identityFile': '~/.ssh/id_ed25519_vps_personal_m5', 'knownHostsFile': '~/.ssh/known_hosts'},
            'paths': {'apk': str(base / 'wa110/apk/driver-lab-e022b8f2eafe.apk'), 'qaRoot': str(qa),
                      'readerJar': str(base / 'wa110/ui-readonly-probe/wa110-readonly-ui-613ae571ac0b.jar'),
                      'tlsDirectory': str(base / 'wa060/tls-v2'), 'phoneLockDirectory': str(base / 'wa110/phone-guard')}}
    validate_files(spec)
    p.need(output.parent.resolve() == ROOT and not output.exists(), 'NEW_SPEC_IN_ASSIGNED_FOLDER_REQUIRED')
    g.write_journal(output, spec)
    return spec


def plan(spec, spec_hash):
    validate_files(spec)
    return {'status': 'OFFLINE_PREPARED_NOT_AUTHORIZED_NOT_EXECUTED', 'specSha256': spec_hash,
            'attemptId': spec['attemptId'], 'sourceSha': p.SOURCE, 'backendJarSha256Expected': p.JAR,
            'origin': 'https://localhost:18443', 'publicCaSha256': p.CA,
            'forwards': ['127.0.0.1:18083 -> VPS127.0.0.1:28083', '127.0.0.1:28444 -> VPS127.0.0.1:28444'],
            'sshArgv': p.tunnel_argv(spec), 'controlActions': ['bridge-bootstrap', 'stop', 'status'],
            'qaCommandTemplate': p.qa_argv(spec, '/private/tmp/v110-EXAMPLE/qa.sock', sys.executable),
            'privateKeyExported': False, 'phoneAccessed': False, 'jvmStarted': False,
            'remoteProfile': spec['remoteProfile'], 'remoteModuleSha256': spec['remoteModuleSha256'],
            'remoteFunctionalReadiness': 'NOT_VERIFIED_PINNED_PHYSICAL_BOOTSTRAP_REQUIRED_BEFORE_PHONE'}


def collect_qa_output(child, timeout=400, clock=time.monotonic):
    """Bounded bytes and bounded wait even if a dead executor's descendant keeps its pipe open."""
    raw = bytearray(); end = clock() + timeout; exited_at = None
    selector = selectors.DefaultSelector(); selector.register(child.stdout, selectors.EVENT_READ)
    try:
        while clock() < end:
            for key, _ in selector.select(.2):
                chunk = os.read(key.fileobj.fileno(), 4096)
                if not chunk:
                    child.wait(timeout=5)
                    return bytes(raw)
                p.need(len(raw) + len(chunk) <= 16384, 'QA_OUTPUT_LIMIT')
                raw.extend(chunk)
            if child.poll() is not None:
                if exited_at is None: exited_at = clock()
                if clock() - exited_at >= 1: return bytes(raw)
        raise p.Rejected('QA_EXECUTOR_TIMEOUT')
    finally: selector.close()


def read_qa(child, timeout=400):
    raw = collect_qa_output(child, timeout)
    try: value = json.loads(raw)
    except ValueError: raise p.Rejected('QA_RESULT_MISSING') from None
    allowed_checks = {'actual_apk_dex_excludes_spike030_origin_and_transport', 'verified_apk_and_explicit_launcher',
        'synthetic_pairing_confirmed_on_same_device', 'app_location_permission_accepted_by_own_system_dialog',
        'explicit_start_confirmed_only_after_backend_valid_gps', 'native_sensor_stopped_while_ack_offline',
        'durable_stop_ack_after_process_reopening_without_new_collection', 'cleanup_confirmed_native_stop'}
    safe = {'status': value.get('status') if value.get('status') in ('PASS', 'FAIL', 'ACTION_REQUIRED') else 'FAIL',
            'reason': (value['reason'] if isinstance(value['reason'],str) and re.fullmatch(r'[A-Z][A-Z0-9_]{2,119}',value['reason'])
                       else 'QA_DIAGNOSTIC_REDACTED') if 'reason' in value else None,
            'checks': [item for item in value.get('checks', []) if item in allowed_checks],
            'apkSha256': p.APK if value.get('apkSha256') == p.APK else None,
            'physicalAndroid': value.get('physicalAndroid') is True, 'physicalIos': value.get('physicalIos') is True,
            'channel': 'synthetic' if value.get('channel') == 'synthetic' else 'UNKNOWN'}
    return child.returncode == 0 and p.qa_pass(value), safe


def capture_local_inputs(paths):
    hashes = {'apk': g.sha(paths['apk']), 'readerJar': g.sha(paths['readerJar']),
              'publicCa': g.sha(paths['tlsDirectory'] / 'ca.der'), 'leafCertificate': g.sha(paths['tlsDirectory'] / 'leaf.pem')}
    for directory in ('driver_run', 'driver_pairing'):
        base = paths['qaRoot'] / 'integration_test' / directory
        for file in sorted(base.rglob('*')):
            if file.suffix not in ('.py', '.swift'): continue
            p.need(file.is_file() and not file.is_symlink(), 'QA_INPUT_SYMLINK_OR_MISSING')
            hashes[str(file.relative_to(paths['qaRoot']))] = g.sha(file)
    return hashlib.sha256(json.dumps(hashes, sort_keys=True, separators=(',', ':')).encode()).hexdigest()


def run(spec, spec_hash):
    require_private_runtime()
    paths = validate_files(spec)
    nonce = secrets.token_urlsafe(32); bridge = p.Bridge(spec, nonce)
    record_file = ROOT / ('session-' + spec['attemptId'] + '-' + nonce[:8] + '.json')
    record = {'schemaVersion': 1, 'profile': 'TEMPORARY_VPS110_MAC', 'attemptId': spec['attemptId'],
              'nonce': nonce, 'sourceSha': p.SOURCE, 'backendJarSha256Expected': p.JAR, 'apkSha256': p.APK,
              'specSha256': spec_hash, 'startedAtUtc': g.now(), 'status': 'STARTING', 'phoneUsed': False,
              'phoneInspected': False, 'phonePrestateAccepted': False,
              'runtimeSourceSha256': {name: g.sha(ROOT / name) for name in RUNTIME_FILES},
              'localInputsSha256': capture_local_inputs(paths), 'remoteModuleSha256': spec['remoteModuleSha256'],
              'physicalAndroidProof': False, 'physicalIos': False, 'pilotApproved': False, 'productionApproved': False}
    journal_verified = True; codes = []
    def persist():
        nonlocal journal_verified
        try: g.write_journal(record_file, record)
        except Exception:
            journal_verified = False
            codes.append('SESSION_JOURNAL_WRITE_FAILED')
            raise
    def cleanup_persist():
        # Cleanup must remain independent from the filesystem/journal health.
        try: persist()
        except Exception: pass
    persist(); tunnel = t.Tunnel(spec); servers = child = phone = phone_lock = None
    socket_dir = None; remote_touched = False; qa_ok = False; quiet = True; interrupted = False; critical = False
    previous_signals = {}
    def interrupt(_signum, _frame):
        nonlocal interrupted
        interrupted = True
        if not critical: raise g.GuardInterrupted()
    try:
        for sig in (signal.SIGINT, signal.SIGTERM): previous_signals[sig] = signal.signal(sig, interrupt)
        tunnel.start(); remote_touched = True
        record['sshPid'] = tunnel.child.pid; persist()
        bridge.bootstrap(t.control(spec, 'bridge-bootstrap', nonce))
        # Remote BLOCKED bootstrap never reaches phone discovery, QA or ADB.
        socket_dir = Path(tempfile.mkdtemp(prefix='v110-', dir='/private/tmp')); socket_dir.chmod(0o700)
        record['socketDirectory'] = str(socket_dir); persist()
        servers = t.LocalServers(bridge, socket_dir / 'qa.sock', paths['tlsDirectory']); servers.start()
        record['socketInode'] = list(servers.inode); persist()
        phone = g.AdbPhone(Path.home() / 'Library/Android/sdk/platform-tools/adb')
        record['phoneInspected'] = True; persist()
        fingerprint = phone.bind()
        phone_lock = g.DeviceLock(g.private_dir(paths['phoneLockDirectory']), fingerprint); phone_lock.__enter__()
        native = {'before': phone.snapshot(), 'childSpawned': False, 'phoneActions': []}; record['nativeGuard'] = native
        p.need(native['before']['servicePresent'] is False and native['before']['reverse18443'] == [], 'PHONE_NOT_IDLE')
        record['phonePrestateAccepted'] = True; record['phoneUsed'] = True; persist()
        critical = True
        child = subprocess.Popen(p.qa_argv(spec, servers.socket_path, sys.executable), cwd=paths['qaRoot'],
                                 stdin=subprocess.DEVNULL, stdout=subprocess.PIPE, stderr=subprocess.DEVNULL,
                                 env=t.minimal_env(), start_new_session=True)
        native['childSpawned'] = True; record['qaPid'] = child.pid; persist(); critical = False
        if interrupted: raise g.GuardInterrupted()
        qa_ok, record['qa'] = read_qa(child)
        p.need(qa_ok and bridge.step == 4 and not bridge.failed, 'PHYSICAL_PROTOCOL_NOT_COMPLETE')
    except BaseException as error:
        codes.append(str(error) if isinstance(error, (p.Rejected, g.GuardFailure)) else 'INTERRUPTED' if isinstance(error, g.GuardInterrupted) else 'MAC_SESSION_FAILED')
        qa_ok = False
    finally:
        critical = True; record['status'] = 'CLOSING'; cleanup_persist()
        if child:
            try: g.stop_owned_child(child)
            except BaseException: quiet = False; codes.append('QA_STOP_UNCONFIRMED')
        phone_verified = not record['phoneInspected']
        if record['phoneUsed']:
            try:
                native = record['nativeGuard']
                phone_verified = (g.reconcile_after_executor(phone, native, quiet, cleanup_persist) if native['childSpawned']
                                  else g.verify_unchanged_without_spawn(phone, native))
                if not phone_verified: codes.extend(native.get('phoneCleanupCodes', []))
            except BaseException: phone_verified = False; codes.append('NATIVE_PHONE_RECONCILIATION_REQUIRED')
        elif record['phoneInspected']:
            codes.append('NATIVE_PHONE_PRESTATE_NOT_ACCEPTED_NO_MUTATION')
        remote_stopped = False
        if remote_touched:
            if qa_ok and bridge.step == 4:
                try: bridge.shutdown(t.send_control)
                except BaseException: codes.append('HTTP_SHUTDOWN_NOT_ATTESTED')
            try:
                bridge.stopped(t.control(spec, 'stop', nonce))
                bridge.stopped(t.control(spec, 'status', nonce)); remote_stopped = True
            except BaseException: codes.append('REMOTE_SCOPED_STOP_STATUS_REQUIRED')
        else: remote_stopped = True
        try: ssh_closed = tunnel.close()
        except BaseException: ssh_closed = False; codes.append('OWNED_SSH_STOP_REQUIRED')
        try: local_closed = servers.close() if servers else True
        except BaseException: local_closed = False; codes.append('LOCAL_SERVERS_CLOSE_REQUIRED')
        if socket_dir:
            try: socket_dir.rmdir()
            except OSError: local_closed = False; codes.append('PRIVATE_SOCKET_DIRECTORY_PRESERVED')
        try: source_frozen = all(g.sha(ROOT / name) == digest for name, digest in record['runtimeSourceSha256'].items())
        except OSError: source_frozen = False
        if not source_frozen: qa_ok = False; codes.append('MAC_RUNTIME_SOURCE_CHANGED')
        try: inputs_frozen = capture_local_inputs(paths) == record['localInputsSha256']
        except (OSError, p.Rejected): inputs_frozen = False
        if not inputs_frozen: qa_ok = False; codes.append('LOCAL_APK_QA_TLS_INPUT_CHANGED')
        # Keep the device lock through all shutdowns. A remote statement never
        # replaces a fresh native observation after its listeners are closed.
        if record['phoneUsed']:
            try:
                native = record['nativeGuard']
                phone_verified = (g.reconcile_after_executor(phone, native, quiet, cleanup_persist) if native['childSpawned']
                                  else g.verify_unchanged_without_spawn(phone, native))
                if not phone_verified: codes.extend(native.get('phoneCleanupCodes', []))
                if 'force_stop_only_driver_lab' in native.get('phoneActions', []):
                    qa_ok = False; codes.append('NATIVE_SENSOR_REQUIRED_EMERGENCY_STOP')
            except BaseException: phone_verified = False; codes.append('FINAL_NATIVE_PHONE_RECONCILIATION_REQUIRED')
        if phone_lock:
            try: phone_lock.__exit__(None, None, None)
            except BaseException: phone_verified = False; codes.append('PHONE_LOCK_CLOSE_FAILED')
        record['bridgeAudit'] = bridge.audit()
        record.update(status=p.final_status(qa_ok, bridge.step == 4 and not bridge.failed, phone_verified,
                      remote_stopped, local_closed and ssh_closed, interrupted) if journal_verified else 'BLOCKED_RECOVERY_REQUIRED', finishedAtUtc=g.now(),
                      phoneResourcesVerified=phone_verified, remoteStopped=remote_stopped, localClosed=local_closed and ssh_closed,
                      physicalAndroidObserved=bool(qa_ok),
                      physicalAndroidProof=bool(qa_ok and phone_verified and remote_stopped and local_closed and ssh_closed
                                                and bridge.step == 4 and not bridge.failed and not interrupted and journal_verified), remoteReceiptSha256=bridge.receipt,
                      localInputsUnchanged=inputs_frozen, codes=sorted(set(codes)), bridgeTokenPersisted=False, interrupted=interrupted,
                      sessionJournalVerified=journal_verified)
        bridge.secret = None; bridge.pairing = None; cleanup_persist()
        if not journal_verified:
            # A failure of the very last write must also invalidate the returned verdict.
            record.update(status='BLOCKED_RECOVERY_REQUIRED',physicalAndroidProof=False,
                          sessionJournalVerified=False,codes=sorted(set(codes + ['SESSION_JOURNAL_WRITE_FAILED'])))
        for sig, handler in previous_signals.items(): signal.signal(sig, handler)
        if child and child.stdout: child.stdout.close()
    return record_file, record


def close_stale_unix(record):
    value = record.get('socketDirectory')
    if not value: return True
    directory = Path(value)
    p.need(directory.parent == Path('/private/tmp') and re.fullmatch(r'v110-[A-Za-z0-9_-]{6,32}', directory.name)
           and not directory.is_symlink(), 'RECOVERY_SOCKET_SCOPE_INVALID')
    if not directory.exists(): return True
    info = directory.stat()
    p.need(info.st_uid == os.getuid() and stat.S_IMODE(info.st_mode) == 0o700, 'RECOVERY_SOCKET_DIRECTORY_UNOWNED')
    node = directory / 'qa.sock'
    if node.exists():
        info = node.lstat()
        p.need(stat.S_ISSOCK(info.st_mode) and info.st_uid == os.getuid()
               and [info.st_dev, info.st_ino] == record.get('socketInode'), 'RECOVERY_SOCKET_IDENTITY_UNCERTAIN')
        with socket.socket(socket.AF_UNIX) as client:
            client.settimeout(.3)
            p.need(client.connect_ex(str(node)) != 0, 'ORPHAN_UNIX_LISTENER_PRESERVED')
        node.unlink()
    directory.rmdir()
    return True


def recover(spec, spec_hash, journal, confirm_qa_stopped=False):
    require_private_runtime()
    p.need(journal.parent.resolve() == ROOT.resolve(), 'RECOVERY_JOURNAL_MUST_STAY_IN_PRIVATE_RUNTIME')
    info = journal.lstat()
    p.need(stat.S_ISREG(info.st_mode) and not journal.is_symlink() and info.st_uid == os.getuid()
           and stat.S_IMODE(info.st_mode) == 0o600 and info.st_size < 1_000_000, 'PRIVATE_SESSION_RECORD_REQUIRED')
    record = json.loads(journal.read_bytes())
    p.need(record['specSha256'] == spec_hash and record['attemptId'] == spec['attemptId']
           and record['sourceSha'] == p.SOURCE and p.token(record['nonce']), 'RECOVERY_SCOPE_CHANGED')
    qa_pid = record.get('qaPid')
    p.need(not qa_pid or not g.group_exists(qa_pid) or confirm_qa_stopped, 'OWNED_QA_MAY_STILL_BE_RUNNING')
    bridge = p.Bridge(spec, record['nonce']); remote_stopped = False
    if record.get('remoteReceiptSha256'):
        p.need(isinstance(record['remoteReceiptSha256'], str)
               and re.fullmatch(r'[a-f0-9]{64}', record['remoteReceiptSha256']), 'RECOVERY_RECEIPT_INVALID')
        bridge.receipt = record['remoteReceiptSha256']
    phone_verified = not record.get('phoneInspected', record.get('phoneUsed', False)); codes = []
    phone_lock = None; phone_lock_acquired = False
    if record.get('phoneUsed'):
        try:
            native = record['nativeGuard']; fingerprint = native['before']['serialFingerprint']
            phone_lock = g.DeviceLock(g.private_dir(resolved(spec['paths']['phoneLockDirectory'])), fingerprint)
            phone_lock.__enter__(); phone_lock_acquired = True
            phone = g.AdbPhone(Path.home() / 'Library/Android/sdk/platform-tools/adb', fingerprint)
            phone_verified = g.clean_phone(phone, native) if native['childSpawned'] else g.verify_unchanged_without_spawn(phone, native)
        except Exception: phone_verified = False; codes.append('NATIVE_PHONE_RECONCILIATION_REQUIRED')
    elif not phone_verified: codes.append('NATIVE_PHONE_PRESTATE_NOT_ACCEPTED_NO_MUTATION')
    try:
        bridge.stopped(t.control(spec, 'stop', record['nonce'])); bridge.stopped(t.control(spec, 'status', record['nonce']))
        remote_stopped = True
    except Exception: codes.append('REMOTE_SCOPED_STOP_STATUS_REQUIRED')
    # Never kill a PID taken from a journal. Operator must reconcile an orphan SSH process independently.
    local_closed = all(t.port_closed(port) for port in (18083, 28444, 18443))
    try: local_closed = close_stale_unix(record) and local_closed
    except Exception: local_closed = False; codes.append('PRIVATE_UNIX_RESOURCE_PRESERVED_UNTIL_OWNERSHIP_VERIFIED')
    if not local_closed: codes.append('ORPHAN_LOCAL_LISTENER_PRESERVED_VERIFY_OWNERSHIP')
    if record.get('phoneUsed') and phone_lock_acquired:
        try:
            phone_verified = g.clean_phone(phone, native) if native['childSpawned'] else g.verify_unchanged_without_spawn(phone, native)
        except Exception: phone_verified = False; codes.append('FINAL_NATIVE_PHONE_RECONCILIATION_REQUIRED')
    if phone_lock:
        try: phone_lock.__exit__(None, None, None)
        except Exception: phone_verified = False; codes.append('PHONE_LOCK_CLOSE_FAILED')
    record['recovery'] = {'phoneResourcesVerified': phone_verified, 'remoteStopped': remote_stopped,
                          'localClosed': local_closed, 'codes': codes, 'functionalPassGranted': False,
                          'remoteReceiptSha256': bridge.receipt, 'finishedAtUtc': g.now()}
    g.write_journal(journal, record)
    return journal, {'status': 'RECOVERY_ONLY_VERIFIED' if phone_verified and remote_stopped and local_closed else 'BLOCKED_RECOVERY_REQUIRED',
                     'attemptId': spec['attemptId'], 'physicalAndroidProof': False, 'codes': codes}


def main():
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__, formatter_class=argparse.RawDescriptionHelpFormatter)
    parser.add_argument('mode', choices=('prepare', 'plan', 'run', 'recover'))
    parser.add_argument('--attempt', default='redmi-vps110-a1')
    parser.add_argument('--spec', type=Path, default=ROOT / 'spec-physical.json')
    parser.add_argument('--remote-module-sha256', help='Reviewed canonical physical source-manifest.json SHA256; required by prepare.')
    parser.add_argument('--expected-spec-sha256')
    parser.add_argument('--journal', type=Path)
    parser.add_argument('--confirm-qa-stopped', action='store_true')
    args = parser.parse_args()
    try:
        if args.mode in ('prepare','run','recover'): require_private_runtime()
        if args.mode == 'prepare': spec = prepare(args.attempt, args.spec, args.remote_module_sha256)
        else: spec = p.validate_spec(json.loads(args.spec.read_bytes()))
        digest = g.sha(args.spec)
        if args.mode in ('prepare', 'plan'):
            print(json.dumps(plan(spec, digest), indent=2)); return 0
        p.need(args.expected_spec_sha256 == digest, 'EXACT_REVIEWED_SPEC_HASH_REQUIRED')
        if args.mode == 'run': journal, result = run(spec, digest)
        else:
            p.need(args.journal is not None, 'RECOVERY_JOURNAL_REQUIRED')
            journal, result = recover(spec, digest, args.journal, args.confirm_qa_stopped)
        print(json.dumps({key: result.get(key) for key in ('status', 'attemptId', 'physicalAndroidProof', 'phoneResourcesVerified',
                          'remoteStopped', 'localClosed', 'remoteReceiptSha256', 'codes')} | {'journal': str(journal)}))
        return 0 if result['status'] in ('PASS_TEMPORARY_VPS110_PHYSICAL', 'RECOVERY_ONLY_VERIFIED') else 1
    except (p.Rejected, g.GuardFailure, OSError, ValueError, KeyError, subprocess.SubprocessError) as error:
        print(json.dumps({'status': 'BLOCKED_PREPARATION_OR_RECOVERY', 'code': str(error) if isinstance(error, (p.Rejected, g.GuardFailure))
                          else 'LOCAL_INPUT_OR_PREFLIGHT_FAILED', 'physicalAndroidProof': False}))
        return 1


if __name__ == '__main__': raise SystemExit(main())
