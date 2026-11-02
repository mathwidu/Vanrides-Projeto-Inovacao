#!/usr/bin/env python3
"""Single owner supervisor. Secret bootstrap stdout is only for the Mac SSH client."""
import argparse
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import re
import selectors
import shutil
import signal
import socket
import stat
import subprocess
import sys
sys.dont_write_bytecode = True
import time

import infra
from layout import FIXED_FILES
from protocol import Blocked, context, envelope, hash_file, receipt_bytes, require

HERE = Path(__file__).resolve().parent
PROFILE = 'VPS_PHYSICAL_JOURNEY_V1'
CHECKS = {
    'original_worker_conversation_registered_driver_routines_and_two_passengers',
    'mac_observed_pairing_challenge_and_jkt_approved_for_synthetic_driver',
    'same_jkt_redeemed_by_native_pkce_and_reviewed_link_did_not_start_sensor',
    'fresh_native_acquisition_confirmed_once_and_delivered_only_to_loopback_driver',
    'native_stop_acknowledged_in_same_device_bound_session',
}
TABLES = {'identity','identity_alias','profile','driver_authorization','driver_device','pairing_challenge',
          'device_token','driver_bootstrap_invite','driver_invite','driver_passenger_link','pickup_point',
          'routine','routine_member','routine_occurrence','attendance','operation_run','run_stop',
          'start_intent','tracking_session','tracking_batch_receipt','tracking_sample_receipt',
          'conversation_state','message_outbox','message_attempt','command_receipt','support_case',
          'passenger_link_routine','common_pickup_point','passenger_response_route','attendance_question','occurrence_roster_override'}
SESSION_NAMES = {'cohort-scope-private.json', 'conversation_token.secret', 'delivery_token.secret',
                 'control_token.secret', 'evolution_api_key.secret', 'attendance-job.secret'}
SESSION_PATTERN = re.compile(r'\d{3,}-(?:operator-request\.json|operator-result\.json|passenger-step\.json|worker-intent-private\.json|worker-process-private\.json)')


def atomic_json(path, value):
    temporary = path.with_name(path.name + '.pending')
    infra.private_json(temporary, value)
    os.replace(temporary, path)


def validate_result(value):
    keys = {'cleanupConfirmed','cohortReset','issuedCredentials','revokedCredentials','graph','totals','checks','codes',
            'originalNineCheckProfile','hardwareVerified','sensorEvidence','externalBusinessSubmissions','functionalComplete','functionalCode'}
    require(isinstance(value, dict) and set(value) == keys, 'CHILD_RESULT_FIELDS_INVALID')
    require(type(value['cleanupConfirmed']) is bool and type(value['functionalComplete']) is bool
            and value['originalNineCheckProfile'] is False and value['hardwareVerified'] is False
            and value['externalBusinessSubmissions'] == 0
            and value['sensorEvidence'] == 'BACKEND_ACCEPTED_NATIVE_CHANNEL_ONLY_MAC_EVIDENCE_REQUIRED', 'CHILD_RESULT_BOUNDARY_INVALID')
    require(all(type(value[key]) is int and 0 <= value[key] <= limit for key, limit in
                (('cohortReset',1),('issuedCredentials',4),('revokedCredentials',4))), 'CHILD_RESULT_TOTAL_INVALID')
    require(isinstance(value['checks'], list) and len(value['checks']) <= len(CHECKS)
            and all(set(item) == {'check','status'} and item['check'] in CHECKS and item['status'] == 'PASS' for item in value['checks'])
            and len({item['check'] for item in value['checks']}) == len(value['checks']), 'CHILD_CHECK_INVALID')
    require(isinstance(value['codes'], list) and len(value['codes']) <= 30
            and all(isinstance(item, str) and re.fullmatch('[A-Z][A-Z0-9_]{2,119}', item) for item in value['codes']), 'CHILD_CODE_INVALID')
    require(value['functionalCode'] is None or isinstance(value['functionalCode'], str)
            and re.fullmatch('[A-Z][A-Z0-9_]{2,119}', value['functionalCode']), 'CHILD_FUNCTIONAL_CODE_INVALID')
    if value['totals'] is not None:
        totals = {'uniqueEvents','duplicateEvents','commandReceipts','submittedAttempts','providerRequests','crossActorDeliveries','workerStarts'}
        require(set(value['totals']) == totals and all(type(item) is int and 0 <= item <= 1000 for item in value['totals'].values())
                and value['totals']['submittedAttempts'] == value['totals']['providerRequests'], 'CHILD_DELIVERY_TOTAL_INVALID')
    graph = value['graph']
    require(graph is None or isinstance(graph, dict) and set(graph) == TABLES
            and all(re.fullmatch('[a-z_]+', key) and type(item) is int and 0 <= item <= 100000 for key,item in graph.items()), 'CHILD_GRAPH_INVALID')
    if value['cleanupConfirmed']:
        require(not value['codes'] and graph is not None and all(item == 0 for item in graph.values())
                and value['issuedCredentials'] == value['revokedCredentials'], 'CHILD_CLEANUP_NOT_PROVED')
    if value['functionalComplete']:
        require(value['cleanupConfirmed'] and len(value['checks']) == len(CHECKS) and value['cohortReset'] == 1
                and value['issuedCredentials'] == value['revokedCredentials'] == 4, 'CHILD_FUNCTIONAL_NOT_PROVED')
    return value


class ShutdownBudget:
    """One monotonic budget; recovery time can only destroy owned infrastructure."""
    def __init__(self, clock=time.monotonic):
        self.clock = clock
        self.deadline = clock() + 280

    def remaining(self, recovery=False):
        return max(0, self.deadline + (15 if recovery else 0) - self.clock())

    def check(self):
        require(self.remaining() > 0, 'SHUTDOWN_BUDGET_EXPIRED')

    def timeout(self, limit, recovery=False):
        available = self.remaining(recovery)
        require(available > 0, 'SHUTDOWN_RECOVERY_BUDGET_EXPIRED' if recovery else 'SHUTDOWN_BUDGET_EXPIRED')
        return min(limit, available)


class Infrastructure(infra.Runtime):
    shutdown_budget = None
    teardown_active = False

    def guard(self, enforce_warning=True):
        if self.shutdown_budget is not None and not self.teardown_active:
            self.shutdown_budget.check()
        return super().guard(enforce_warning)

    def command(self, args, stdin=None, timeout=45, guarded=True):
        if self.shutdown_budget is not None:
            timeout = self.shutdown_budget.timeout(timeout, recovery=self.teardown_active)
        return super().command(args, stdin, timeout, guarded)

    def stop(self):
        self.teardown_active = True
        try:
            self.location(); self.owner(); self.resources()
            grace = 20 if self.shutdown_budget is None else min(20, max(0, int(self.shutdown_budget.remaining(recovery=True) - 2)))
            self.command([*self.docker, 'down', '--timeout', str(grace)], timeout=75, guarded=False)
            rows, networks = self.resources()
            require(not rows and not networks and not infra.open_port('127.0.0.1', 28083), 'OWNED_INFRASTRUCTURE_NOT_STOPPED')
            return {'backendStopped': True, 'databaseStopped': True}
        finally:
            self.teardown_active = False


def runtime(args, fresh=False):
    owner = None if fresh else infra.load(HERE / 'runtime/owner.json')
    owner_nonce = __import__('secrets').token_urlsafe(32) if fresh else owner['nonce']
    value = Infrastructure(argparse.Namespace(attempt=args.attempt, nonce=owner_nonce, hold_seconds=5), HERE)
    require(value.contract['profile'] == PROFILE and re.fullmatch('[a-f0-9]{64}', value.contract['moduleSha256'])
            and hash_file(HERE / 'source-manifest.json') == value.contract['moduleSha256'], 'PHYSICAL_MODULE_NOT_SEALED')
    return value


def session_cleanup(base):
    folder = base / 'session'
    require(folder.is_dir() and not folder.is_symlink() and folder.stat().st_uid == os.getuid(), 'SESSION_CLEANUP_OWNERSHIP_INVALID')
    for path in folder.iterdir():
        require(path.is_file() and not path.is_symlink() and path.stat().st_uid == os.getuid()
                and stat.S_IMODE(path.stat().st_mode) == 0o600
                and (path.name in SESSION_NAMES or SESSION_PATTERN.fullmatch(path.name)), 'SESSION_CLEANUP_UNKNOWN_ARTIFACT')
    for path in folder.iterdir():
        path.unlink()
    require(not list(folder.iterdir()), 'SESSION_PRIVATE_FILES_REMAIN')


def process_ticks(pid):
    try:
        value = Path('/proc', str(pid), 'stat').read_text()
        return value[value.rfind(')')+2:].split()[19]
    except FileNotFoundError:
        return None


def session_members(pgid):
    """Read kernel identities only; commands, environments and payloads are never collected."""
    rows = []
    for directory in Path('/proc').iterdir():
        if not directory.name.isdigit():
            continue
        try:
            text = (directory / 'stat').read_text()
            fields = text[text.rfind(')')+2:].split()
            if int(fields[2]) == pgid:
                rows.append({'pid':int(directory.name), 'sid':int(fields[3]),
                             'uid':directory.stat().st_uid, 'startTicks':fields[19]})
        except FileNotFoundError:
            continue
    return rows


class OwnedSessionGroup:
    """A live leader or previously witnessed member must anchor the original kernel session.

    A PID/PGID number from disk is never enough to signal a group. Witnesses stay
    only in this supervisor's memory, acquired while its own leader is present.
    """
    def __init__(self, child, start_ticks, *, members=session_members,
                 signal_group=os.killpg, clock=time.monotonic, pause=time.sleep):
        self.child, self.pgid, self.start_ticks = child, child.pid, start_ticks
        self.members, self.signal_group, self.clock, self.pause = members, signal_group, clock, pause
        self.known = {}

    def observe(self):
        rows = self.members(self.pgid)
        require(all(row['sid'] == self.pgid and row['uid'] == os.getuid() for row in rows), 'SESSION_GROUP_SCOPE_CHANGED')
        leader = next((row for row in rows if row['pid'] == self.pgid), None)
        if leader is not None:
            require(self.start_ticks is not None and leader['startTicks'] == self.start_ticks, 'SESSION_LEADER_PID_REUSED')
        else:
            require(not rows or any(self.known.get(row['pid']) == row['startTicks'] for row in rows), 'SESSION_GROUP_IDENTITY_UNCERTAIN')
        self.known.update((row['pid'], row['startTicks']) for row in rows)
        return rows

    def stop(self, *, deadline):
        """Reconcile the owned session even if its leader has already exited. Never trust PID alone."""
        require(type(deadline) in (int, float) and math.isfinite(deadline), 'SESSION_GROUP_DEADLINE_INVALID')
        self.child.poll()  # Reap our leader before observing possible zombies.
        if not self.observe():
            return False
        for number, grace in ((signal.SIGTERM,8),(signal.SIGKILL,5)):
            # A fresh identity proof immediately precedes every mutation/escalation.
            if not self.observe():
                return True
            require(self.clock() < deadline, 'SESSION_GROUP_BUDGET_EXPIRED')
            try:
                self.signal_group(self.pgid, number)
            except ProcessLookupError:
                pass
            phase_deadline = min(self.clock() + grace, deadline)
            while self.clock() < phase_deadline:
                self.child.poll()
                if not self.observe():
                    return True
                self.pause(min(.1, max(0, phase_deadline - self.clock())))
        require(self.clock() < deadline or not self.observe(), 'SESSION_GROUP_BUDGET_EXPIRED')
        require(not self.observe(), 'OWNED_SESSION_GROUP_NOT_CLOSED')
        return True


def extract_node(value):
    tools = HERE / 'tools'
    require(not tools.exists(), 'NODE_TOOL_DIRECTORY_ALREADY_PRESENT')
    tools.mkdir(mode=0o700)
    name = value.project + '-node-extract'
    require(not value.command(['docker','ps','-aq','--filter','name=^/' + name + '$']), 'NODE_EXTRACT_CONTAINER_ALREADY_PRESENT')
    attempted = False
    try:
        value.command(['docker','pull','--platform','linux/amd64',value.contract['images']['node']], timeout=240)
        attempted = True
        value.command(['docker','create','--name',name,'--network','none','--label','wa110.owner=' + value.args.nonce,
                       '--label','wa110.project=' + value.project,'--label','wa110.backend=' + value.binding['sourceSha'],
                       value.contract['images']['node']], timeout=45)
        row = json.loads(value.command(['docker','inspect',name]))[0]
        require(row['Config']['Image'] == value.contract['images']['node'] and row['Config']['Labels'].get('wa110.owner') == value.args.nonce
                and row['Config']['Labels'].get('wa110.project') == value.project and not row['State']['Running']
                and row['HostConfig']['NetworkMode'] == 'none', 'NODE_EXTRACT_OWNERSHIP_MISMATCH')
        value.command(['docker','cp',name + ':/usr/local/bin/node',str(tools / 'node')], timeout=60)
        require((tools / 'node').is_file() and not (tools / 'node').is_symlink()
                and (tools / 'node').stat().st_uid == os.getuid(), 'NODE_BINARY_OWNERSHIP_INVALID')
        (tools / 'node').chmod(0o700)
    finally:
        if attempted:
            found = value.command(['docker','ps','-aq','--filter','name=^/' + name + '$'], guarded=False)
            if found:
                row = json.loads(value.command(['docker','inspect',name], guarded=False))[0]
                require(row['Config']['Image'] == value.contract['images']['node']
                        and row['Config']['Labels'].get('wa110.owner') == value.args.nonce
                        and row['Config']['Labels'].get('wa110.project') == value.project and not row['State']['Running'], 'NODE_EXTRACT_CLEANUP_OWNERSHIP_MISMATCH')
                value.command(['docker','rm',name], guarded=False)
    require(value.command([tools / 'node','--version']) == 'v22.23.2', 'NODE_VERSION_MISMATCH')
    return tools / 'node'


def bound_scope(value, nonce):
    requested = context(value.args.attempt, nonce, value.contract)
    binding_path = HERE / 'runtime/bridge-binding.json'
    require(binding_path.exists() and infra.load(binding_path) == requested, 'BRIDGE_CONTEXT_MISMATCH')
    return requested


def status(value, nonce):
    value.location(); value.owner()
    scope = bound_scope(value, nonce)
    record = infra.load(HERE / 'runtime/final-state.json')
    require(record['binding'] == scope, 'FINAL_SCOPE_MISMATCH')
    rows, networks = value.resources()
    require(not rows and not networks and not infra.open_port('127.0.0.1', 28083)
            and not infra.open_port('127.0.0.1', 28444) and not (HERE / 'control.sock').exists(), 'OWNED_LISTENER_NOT_CLOSED')
    child_path = HERE / 'runtime/session-child-private.json'
    if child_path.exists():
        child = infra.load(child_path)
        require(process_ticks(child['pid']) != child['startTicks'], 'SESSION_PROCESS_STILL_PRESENT')
        require(not session_members(child['pid']), 'SESSION_GROUP_STILL_PRESENT')
    value.assert_no_private_files()
    require(not list((HERE / 'session').iterdir()), 'SESSION_PRIVATE_FILES_REMAIN')
    require(hash_file(HERE / 'receipt.json') == record['receiptSha256'], 'RECEIPT_DIGEST_MISMATCH')
    if not record['cleanupConfirmed']:
        return envelope(scope, {'status':'BLOCKED','code':'CLEANUP_NOT_CONFIRMED','receiptSha256':record['receiptSha256']})
    return envelope(scope, {'status':'STOPPED','backendStopped':True,'databaseStopped':True,'cleanupConfirmed':True,
                            'listenerClosed':True,'receiptSha256':record['receiptSha256']})


def request_control(value, operation, nonce):
    value.location(); value.owner()
    scope = context(value.args.attempt, nonce, value.contract)
    path = HERE / 'control.sock'
    info = path.lstat()
    require(stat.S_ISSOCK(info.st_mode) and stat.S_IMODE(info.st_mode) == 0o600 and info.st_uid == os.getuid(), 'CONTROL_SOCKET_OWNERSHIP_INVALID')
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as client:
        client.settimeout(55 if operation == 'stop' else 15); client.connect(str(path))
        require(path.lstat().st_ino == info.st_ino, 'CONTROL_SOCKET_REPLACED')
        client.sendall(receipt_bytes({'operation':operation,**scope}))
        data = b''
        while not data.endswith(b'\n'):
            chunk = client.recv(1024)
            require(chunk and len(data) + len(chunk) <= 4096, 'CONTROL_SOCKET_RESPONSE_INVALID')
            data += chunk
    result = json.loads(data)
    require(set(result) == {'attemptId','sourceSha','nonce','result'} and all(result[key] == item for key,item in scope.items()), 'CONTROL_SOCKET_SCOPE_MISMATCH')
    return result


def stop(value, nonce):
    if (HERE / 'runtime/final-state.json').exists():
        return status(value, nonce)
    response = request_control(value, 'stop', nonce)
    require(response['result'] == {'status':'STOP_REQUESTED'}, 'REMOTE_STOP_NOT_ACCEPTED')
    deadline = time.monotonic() + 300
    while not (HERE / 'runtime/final-state.json').exists():
        require(time.monotonic() < deadline, 'SUPERVISOR_STOP_TIMEOUT')
        time.sleep(.3)
    return status(value, nonce)


def serve(value):
    child = None; child_result = None; bridge_binding = None; receipt_sha = None
    session_group = None; group_closed = True
    interrupted = None; buffer = b''; abort_deadline = None; sent_abort = None; ready = False
    shutdown_budget = None; cleanup_started = False
    def group_deadline():
        nonlocal shutdown_budget
        if shutdown_budget is None:
            shutdown_budget = ShutdownBudget(); value.shutdown_budget = shutdown_budget
        return shutdown_budget.deadline + 15
    selector = selectors.DefaultSelector()
    def signal_abort(_signum, _frame):
        nonlocal interrupted
        interrupted = 'SUPERVISOR_SIGNAL_INTERRUPTED'
    for number in (signal.SIGHUP,signal.SIGTERM,signal.SIGINT): signal.signal(number,signal_abort)
    def tell(message):
        require(child is not None and child.poll() is None, 'SESSION_CHILD_NOT_RUNNING')
        child.stdin.write(receipt_bytes(message)); child.stdin.flush()
    try:
        value.start()
        value.assert_live_health()
        executable = extract_node(value)
        value.settle_before_start()
        value.assert_live_health()
        child = subprocess.Popen([str(executable),'--max-old-space-size=192',str(HERE / 'physical_session.mjs'),str(HERE),value.args.attempt],
                                 cwd=HERE,env=value.env,stdin=subprocess.PIPE,stdout=subprocess.PIPE,stderr=subprocess.DEVNULL,start_new_session=True)
        group_closed = False
        child_record = {'pid':child.pid,'startTicks':process_ticks(child.pid),'executable':str(executable),
                        'main':str(HERE / 'physical_session.mjs'),'attemptId':value.args.attempt,'ownerNonce':value.args.nonce}
        session_group = OwnedSessionGroup(child, child_record['startTicks'])
        session_group.observe()
        infra.private_json(HERE / 'runtime/session-child-private.json',child_record)
        selector.register(child.stdout,selectors.EVENT_READ)
        while child.poll() is None:
            session_group.observe()
            if child_result is None:
                try: value.guard()
                except Blocked as error: interrupted = str(error)
            if interrupted and sent_abort != interrupted:
                if shutdown_budget is None:
                    shutdown_budget = ShutdownBudget(); value.shutdown_budget = shutdown_budget
                if abort_deadline is None: abort_deadline = shutdown_budget.deadline
                tell({'operation':'ABORT','code':interrupted})
                sent_abort = interrupted
            require(abort_deadline is None or time.monotonic() < abort_deadline, 'SESSION_ABORT_TIMEOUT')
            for key,_mask in selector.select(.4):
                chunk = os.read(key.fileobj.fileno(),8192)
                if not chunk: continue
                buffer += chunk
                require(len(buffer) <= 32768, 'SESSION_IPC_TOO_LARGE')
                while b'\n' in buffer:
                    line,buffer = buffer.split(b'\n',1)
                    require(len(line) <= 16384, 'SESSION_IPC_TOO_LARGE')
                    event = json.loads(line)
                    require(isinstance(event,dict) and event.get('event') in ('READY','BOUND','CLEANUP_BEGIN','CLEANUP_READY','CLOSED','FATAL'), 'SESSION_IPC_EVENT_INVALID')
                    if event['event'] == 'READY':
                        require(not ready and event == {'event':'READY','profile':PROFILE,'jarSha256':value.contract['backend']['jarSha256'],
                                                        'moduleSha256':value.contract['moduleSha256']}, 'SESSION_READY_PIN_MISMATCH')
                        ready = True
                        infra.private_json(HERE / 'runtime/physical-ready.json',{'profile':PROFILE,'moduleSha256':value.contract['moduleSha256'],'jarSha256':value.contract['backend']['jarSha256']})
                        print(json.dumps({'status':'READY_FOR_MAC_BOOTSTRAP','attemptId':value.args.attempt,'profile':PROFILE,
                                          'moduleSha256':value.contract['moduleSha256']},separators=(',',':')),flush=True)
                    elif event['event'] == 'BOUND':
                        require(set(event) == {'event','binding'}, 'SESSION_BINDING_FIELDS_INVALID')
                        expected = context(value.args.attempt,event['binding'].get('nonce'),value.contract)
                        require(event['binding'] == expected and (bridge_binding is None or bridge_binding == expected)
                                and (not cleanup_started or bridge_binding == expected), 'SESSION_BINDING_CHANGED')
                        bridge_binding = expected
                        atomic_json(HERE / 'runtime/bridge-binding.json',expected)
                    elif event['event'] == 'CLEANUP_BEGIN':
                        require(not cleanup_started and set(event) == {'event','binding','budgetSeconds'}
                                and type(event['budgetSeconds']) is int and event['budgetSeconds'] == 280
                                and event['binding'] == bridge_binding, 'SESSION_SHUTDOWN_BINDING_INVALID')
                        cleanup_started = True
                        if shutdown_budget is None:
                            shutdown_budget = ShutdownBudget(); value.shutdown_budget = shutdown_budget
                    elif event['event'] == 'CLEANUP_READY':
                        require(cleanup_started and shutdown_budget is not None and child_result is None
                                and set(event) == {'event','result'}, 'SESSION_CLEANUP_STATE_INVALID')
                        shutdown_budget.check()
                        child_result = validate_result(event['result'])
                        if child_result['cleanupConfirmed']:
                            value.settle_before_start()
                            value.assert_live_health()
                        value.stop()
                        session_cleanup(HERE); value.clean_private_files()
                        shutdown_budget.check()
                        report = {'schemaVersion':1,'profile':PROFILE,'attemptId':value.args.attempt,
                                  'sourceSha':value.binding['sourceSha'],'jarSha256':value.contract['backend']['jarSha256'],
                                  'apkSha256':value.contract['apk']['sha256'],'publicCaSha256':value.contract['apk']['publicCaSha256'],
                                  'moduleSha256':value.contract['moduleSha256'],'packageSha256':value.package_sha,
                                  'nodeExecutableSha256':hash_file(executable),'backendStopped':True,'databaseStopped':True,
                                  'temporaryDatabaseDestroyed':True,'memory':value.memory_summary(),
                                  'shutdownBudgetSeconds':280,'teardownRecoverySeconds':15,**child_result}
                        infra.private_json(HERE / 'receipt.json',report); receipt_sha = hash_file(HERE / 'receipt.json')
                        tell({'operation':'INFRA_STOPPED','receiptSha256':receipt_sha,'cleanupConfirmed':child_result['cleanupConfirmed']})
                    elif event['event'] == 'FATAL':
                        raise Blocked('SESSION_CHILD_FATAL')
        require(child_result is not None and receipt_sha is not None
                and (child.returncode == 0 or child_result['cleanupConfirmed'] is False), 'SESSION_CHILD_NOT_CONFIRMED')
        forced = session_group.stop(deadline=group_deadline()); group_closed = True
        require(not forced, 'SESSION_GROUP_REQUIRED_FORCED_RECOVERY')
        shutdown_budget.check()
        require(not infra.open_port('127.0.0.1',28444) and not (HERE / 'control.sock').exists(), 'SIDECAR_NOT_CLOSED')
        # A never-bootstrapped interrupted session has no Mac scope to acknowledge.
        if bridge_binding is not None:
            infra.private_json(HERE / 'runtime/final-state.json',{'binding':bridge_binding,'cleanupConfirmed':child_result['cleanupConfirmed'],'receiptSha256':receipt_sha})
        return {'status':'PHYSICAL_BACKEND_PHASES_AND_CLEANUP_VERIFIED' if child_result['functionalComplete'] else 'FUNCTIONAL_INCOMPLETE_CLEANUP_RECORDED',
                'hardwareVerified':False,'receiptSha256':receipt_sha,'cleanupConfirmed':child_result['cleanupConfirmed']}
    finally:
        selector.close()
        try:
            try:
                if child is not None and not group_closed:
                    require(session_group is not None, 'SESSION_GROUP_WITNESS_UNAVAILABLE')
                    session_group.stop(deadline=group_deadline()); group_closed = True
            finally:
                if child is not None:
                    child.stdin.close(); child.stdout.close()
        finally:
            if value.owner_path.exists():
                # An uncertain process identity must not skip destruction of our owned DB/API.
                value.stop()
                if child_result is None and group_closed:
                    # Preserve private witnesses when process ownership remains uncertain.
                    session_cleanup(HERE); value.clean_private_files()


def remove_private(value, nonce):
    result = status(value, nonce)
    require(result['result']['status'] == 'STOPPED', 'REMOVE_PRIVATE_CLEANUP_NOT_CONFIRMED')
    allowed = {*FIXED_FILES,'SHA256SUMS.json','receipt.json','tools/node','runtime/owner.json','runtime/started.json',
               'runtime/session-child-private.json','runtime/physical-ready.json','runtime/bridge-binding.json','runtime/final-state.json'}
    directories = {'inputs','session','tools','runtime','runtime/docker-config'}
    directories.update(str(Path(name).parent) for name in FIXED_FILES)
    for name in list(directories):
        directories.update(str(parent) for parent in Path(name).parents if str(parent) != '.')
    for path in HERE.rglob('*'):
        require(not path.is_symlink() and path.stat().st_uid == os.getuid(), 'REMOVE_PRIVATE_OWNERSHIP_INVALID')
        relative = str(path.relative_to(HERE))
        require((path.is_file() and relative in allowed) or (path.is_dir() and relative in directories), 'REMOVE_PRIVATE_UNKNOWN_ARTIFACT')
    shutil.rmtree(HERE)
    return {'status':'PRIVATE_DIRECTORY_REMOVED'}


def failure_diagnostic(error, value):
    """Code locations only: never exception messages, locals, argv or payloads."""
    kinds = {'Blocked', 'OSError', 'ValueError', 'KeyError', 'TypeError', 'JSONDecodeError',
             'UnicodeDecodeError', 'FileNotFoundError', 'PermissionError', 'BrokenPipeError',
             'ConnectionResetError', 'TimeoutExpired', 'CalledProcessError', 'SubprocessError'}
    stages = {'PULL_POSTGRES', 'PULL_API', 'CHECK_POSTGRES', 'CHECK_API', 'HEALTH_HOLD',
              'GLOBAL_CONCLUSION', 'START_POSTGRES', 'START_API', 'PREFLIGHT', 'CONFIGURE', 'NOT_STARTED'}
    stage = getattr(value, 'startup_stage', None)
    result = {'stage': stage if isinstance(stage, str) and stage in stages else 'UNAVAILABLE',
              'exceptions': []}
    seen = set()
    while error is not None and id(error) not in seen and len(result['exceptions']) < 2:
        seen.add(id(error)); frames = []; trace = error.__traceback__
        while trace is not None:
            code = trace.tb_frame.f_code; file = Path(code.co_filename)
            if (file.parent == HERE and file.name in ('control.py', 'infra.py', 'protocol.py')
                    and re.fullmatch('[A-Za-z_][A-Za-z0-9_]{0,79}', code.co_name)
                    and 1 <= trace.tb_lineno <= 10000):
                frames.append({'file': file.name, 'function': code.co_name, 'line': trace.tb_lineno})
            trace = trace.tb_next
        kind = type(error).__name__
        result['exceptions'].append({'kind': kind if kind in kinds else 'OTHER_CAUGHT_ERROR',
                                     'frames': frames[-4:]})
        error = error.__cause__ if error.__cause__ is not None else error.__context__
    return result


def main():
    parser=argparse.ArgumentParser()
    parser.add_argument('command',choices=('dry-run','preflight','serve','bridge-bootstrap','stop','status','remove-private'))
    parser.add_argument('--attempt',required=True);parser.add_argument('--nonce')
    args=parser.parse_args(); value=None
    try:
        fresh=args.command in ('dry-run','preflight','serve');value=runtime(args,fresh)
        if args.command=='dry-run':
            result={'status':'DRY_RUN_ONLY','profile':PROFILE,'packageSha256':value.package_sha,'moduleSha256':value.contract['moduleSha256'],
                    'remoteDirectory':str(Path(value.contract['destination']['rootPrefix'])/args.attempt),'hardwareVerified':False}
        elif args.command=='preflight':value.preflight(fresh=True);result={'status':'PREFLIGHT_ONLY','profile':PROFILE}
        elif args.command=='serve':result=serve(value)
        elif args.command=='bridge-bootstrap':result=request_control(value,'bridge-bootstrap',args.nonce)
        elif args.command=='stop':result=stop(value,args.nonce)
        elif args.command=='status':result=status(value,args.nonce)
        else:result=remove_private(value,args.nonce)
        print(json.dumps(result,separators=(',',':')));return 0
    except (Blocked,OSError,ValueError,KeyError,TypeError,subprocess.SubprocessError):
        error=sys.exc_info()[1];code=str(error) if isinstance(error,Blocked) else 'LOCAL_VALIDATION_ERROR'
        result={'status':'BLOCKED','code':code,'diagnostic':failure_diagnostic(error,value)}
        if value is not None and args.nonce:
            try:result=envelope(context(args.attempt,args.nonce,value.contract),result)
            except Blocked:pass
        print(json.dumps(result,separators=(',',':')));return 2


if __name__=='__main__':raise SystemExit(main())
