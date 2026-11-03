import base64
import copy
import hashlib
from datetime import datetime, timezone
from email.message import Message
import io
import json
from pathlib import Path
import signal
import socket
import ssl
import tempfile
import threading
import time
from types import SimpleNamespace
import unittest
from unittest import mock

import protocol as p
import transport as t
import phone_guard_core as g
import launch_vps110_mac as launch


NONCE = base64.urlsafe_b64encode(b'N' * 32).decode().rstrip('=')
SECRET = base64.urlsafe_b64encode(b'T' * 32).decode().rstrip('=')
NOW = 1_800_000_000


def spec():
    return {'schemaVersion': 2, 'attemptId': 'redmi-vps110-a1', 'sourceSha': p.SOURCE, 'backendJarSha256': p.JAR,
            'apkSha256': p.APK, 'publicCaSha256': p.CA, 'readerJarSha256': p.READER,
            'remoteProfile': p.PROFILE, 'remoteModuleSha256': 'b' * 64,
            'qaCommit': launch.QA_COMMIT, 'qaRunnerSha256': 'a' * 64, 'allowPermissions': True,
            'ssh': {'host': 'example.invalid', 'user': 'mathwidu', 'port': 22,
                    'identityFile': '/private/test-key-not-read', 'knownHostsFile': '/private/test-hosts-not-read'},
            'paths': {key: '/private/test/' + key for key in ('apk', 'qaRoot', 'readerJar', 'tlsDirectory', 'phoneLockDirectory')}}


def envelope(result):
    return {'attemptId': spec()['attemptId'], 'sourceSha': p.SOURCE, 'nonce': NONCE, 'result': result}


def bootstrap():
    return envelope({'bridgeToken': SECRET, 'expiresAt': datetime.fromtimestamp(NOW + 600, timezone.utc).isoformat().replace('+00:00', 'Z'),
                     'apiPort': 28083, 'sidecarPort': 28444, 'profile': p.PROFILE, 'jarSha256': p.JAR, 'moduleSha256': 'b' * 64})


def observation(pairing_id='00000000-0000-4000-8000-000000000001'):
    # Synthetic public JWK framing; the actual backend validates the key/proof before HTTP200.
    jwk = {'crv': 'P-256', 'kty': 'EC', 'x': NONCE, 'y': SECRET}
    return json.dumps({'publicJwk': jwk, 'platform': 'ANDROID'}).encode(), json.dumps({'pairingId': pairing_id}).encode()


def stopped():
    return envelope({'status': 'STOPPED', 'backendStopped': True, 'databaseStopped': True,
                     'cleanupConfirmed': True, 'listenerClosed': True, 'receiptSha256': 'e' * 64})


def phone_state(**values):
    return {'serialFingerprint': 'd' * 64, 'apkSha256': p.APK, 'servicePresent': False,
            'reverse18443': [], 'qaJars': {}, **values}


def bind_unix_fixture(agent, node):
    previous = Path.cwd()
    try:
        t.os.chdir(node.parent); agent.bind(node.name)
    finally: t.os.chdir(previous)


class Phone:
    def __init__(self, value): self.value = copy.deepcopy(value); self.actions = []
    def snapshot(self): return copy.deepcopy(self.value)
    def force_stop(self): self.actions.append(g.PACKAGE); self.value['servicePresent'] = False
    def remove_reverse(self): self.actions.append('18443'); self.value['reverse18443'] = []


class ProtocolTest(unittest.TestCase):
    def test_only_two_loopback_forwards_no_session_agent_or_remote_control(self):
        argv = p.tunnel_argv(spec())
        self.assertIn('-N', argv); self.assertNotIn('-R', argv); self.assertNotIn('-D', argv)
        self.assertEqual(2, argv.count('-L'))
        for option in ('ForwardAgent=no', 'ControlPath=none', 'IdentityAgent=SSH_AUTH_SOCK', 'StrictHostKeyChecking=yes',
                       'PermitLocalCommand=no', 'ProxyCommand=none', 'UpdateHostKeys=no'):
            self.assertIn(option, argv)
        self.assertNotIn('ClearAllForwardings=yes', argv)  # This would also erase our command-line -L entries.
        self.assertEqual(['/usr/bin/ssh', '-F', '/dev/null'], argv[:3])

    def test_mac_keychain_and_local_agent_are_explicit_without_forwarding_or_prompt(self):
        for argv in (p.tunnel_argv(spec()),p.control_argv(spec(),'bridge-bootstrap',NONCE),
                     p.control_argv(spec(),'stop',NONCE),p.control_argv(spec(),'status',NONCE)):
            for option in ('UseKeychain=yes','IdentitiesOnly=yes','IdentityAgent=SSH_AUTH_SOCK','ForwardAgent=no',
                           'StrictHostKeyChecking=yes','BatchMode=yes','PasswordAuthentication=no',
                           'KbdInteractiveAuthentication=no'):
                self.assertIn(option,argv)
            self.assertEqual(1,argv.count('UseKeychain=yes'))

    def test_remote_command_is_allowlisted_and_path_never_comes_from_peer(self):
        for verb in ('bridge-bootstrap', 'stop', 'status'):
            command = p.control_argv(spec(), verb, NONCE)[-1]
            self.assertIn('/home/mathwidu/.local/share/vanrides/wa110-vps-lab/redmi-vps110-a1/control.py', command)
            self.assertNotIn('adb', command)
        for verb in ('shell', 'exec', 'java', 'adb', 'rm -rf /'):
            with self.assertRaises(p.Rejected): p.control_argv(spec(), verb, NONCE)
        bad = spec(); bad['attemptId'] = 'x;touch /tmp/unwanted'
        with self.assertRaises(p.Rejected): p.tunnel_argv(bad)

    def test_broker_only_four_ordered_bounded_messages_and_exact_uri(self):
        bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW); bridge.bootstrap(bootstrap())
        bridge.observe_pairing_begin(*observation())
        with self.assertRaises(p.Rejected): bridge.exchange('/intent', {}, lambda *x: None)
        def send(route, body, headers):
            self.assertEqual(SECRET, headers['X-WA110-Bridge-Token'])
            if route == '/pair':
                self.assertEqual({'code', 'pairingId', 'jkt'}, set(body))
                self.assertEqual('00000000-0000-4000-8000-000000000001', body['pairingId'])
            return envelope({'uri': 'vanridesdriver://run/start#v1.00000000-0000-4000-8000-000000000001.0.' + SECRET} if route == '/intent' else {})
        for route in p.ROUTES: bridge.exchange(route, {'code': 'BCDFGHJK'} if route == '/pair' else {}, send)
        self.assertEqual(4, bridge.step)
        with self.assertRaises(p.Rejected): bridge.exchange('/stopped', {}, send)

    def test_wrong_context_or_arbitrary_command_result_fails_without_retry(self):
        for result in (envelope({'uri': 'https://untrusted.invalid'}), envelope({'command': ['adb', 'shell']})):
            bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW); bridge.bootstrap(bootstrap()); bridge.step = 1
            with self.assertRaises(p.Rejected): bridge.exchange('/intent', {}, lambda *x: result)
            self.assertTrue(bridge.failed)
        bad = envelope({}); bad['nonce'] = SECRET
        bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW); bridge.bootstrap(bootstrap())
        bridge.observe_pairing_begin(*observation())
        with self.assertRaises(p.Rejected): bridge.exchange('/pair', {'code': 'BCDFGHJK'}, lambda *x: bad)
        self.assertEqual(0, bridge.step)

    def test_expiry_and_shutdown_receipt_cannot_assert_native_sensor(self):
        bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW); bridge.bootstrap(bootstrap())
        result = {'status': 'SHUTDOWN_ACK', 'backendStopped': True, 'databaseStopped': True,
                  'cleanupConfirmed': True, 'receiptSha256': 'e' * 64}
        bridge.shutdown(lambda *x: envelope(result)); self.assertIsNone(bridge.secret)
        bridge.stopped(stopped())
        bad = stopped(); bad['result']['receiptSha256'] = 'f' * 64
        with self.assertRaises(p.Rejected): bridge.stopped(bad)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', p.final_status(True, True, False, True, True))
        self.assertEqual('FAIL_FUNCTIONAL_CLEANUP_VERIFIED', p.final_status(False, True, True, True, True))

    def test_tls_does_not_expose_other_api_or_shell_paths(self):
        for method, path in (('GET', '/actuator/env'), ('POST', '/operator'), ('POST', '/shell'),
                             ('POST', '/driver-app/v1/pairings/begin?redirect=x'), ('GET', 'https://remote.invalid')):
            self.assertFalse(p.tls_route(method, path))
        self.assertTrue(p.tls_route('POST', '/driver-app/v1/pairings/begin'))

    def test_lsof_pid_and_addresses_are_required_before_control_token(self):
        t.assert_owned_listeners(123, 'p123\nn127.0.0.1:18083\nn127.0.0.1:28444\n')
        for value in ('p999\nn127.0.0.1:18083\nn127.0.0.1:28444', 'p123\nn*:18083\nn127.0.0.1:28444'):
            with self.assertRaises(p.Rejected): t.assert_owned_listeners(123, value)

    def test_cleanup_deadlines_allow_official_reset_without_retrying(self):
        result = SimpleNamespace(returncode=0, stdout=json.dumps(stopped()).encode())
        with mock.patch.object(t,'ssh_env',return_value={'PATH':'/usr/bin:/bin','SSH_AUTH_SOCK':'/private/synthetic/socket'}), \
             mock.patch.object(t.subprocess, 'run', return_value=result) as call:
            t.control(spec(), 'stop', NONCE)
            self.assertEqual(330, call.call_args.kwargs['timeout']); self.assertEqual(1, call.call_count)
        client = mock.Mock(); client.getresponse.return_value.status = 200
        client.getresponse.return_value.read.return_value = json.dumps(envelope({})).encode()
        client.getresponse.return_value.getheader.return_value = None
        with mock.patch.object(t.http.client, 'HTTPConnection', return_value=client) as connect:
            t.send_control('/shutdown', {}, {})
            connect.assert_called_once_with('127.0.0.1', 28444, timeout=300)
            self.assertEqual(1, client.request.call_count)


class QaDiagnosticTest(unittest.TestCase):
    def read(self, reason=..., status='FAIL'):
        report={'status':status,'apkSha256':p.APK,'physicalAndroid':True,'physicalIos':False,'channel':'synthetic',
                'checks':['actual_apk_dex_excludes_spike030_origin_and_transport','verified_apk_and_explicit_launcher',
                    'synthetic_pairing_confirmed_on_same_device','explicit_start_confirmed_only_after_backend_valid_gps',
                    'native_sensor_stopped_while_ack_offline','durable_stop_ack_after_process_reopening_without_new_collection']}
        if reason is not ...: report['reason']=reason
        with mock.patch.object(launch,'collect_qa_output',return_value=json.dumps(report).encode()):
            return launch.read_qa(SimpleNamespace(returncode=0))

    def test_known_constant_is_kept_without_changing_pass_or_fail(self):
        for status in ('PASS','FAIL','ACTION_REQUIRED'):
            passed,safe=self.read('ACTION_REQUIRED_UNLOCK_REDMI',status)
            self.assertEqual(status=='PASS',passed);self.assertEqual(status,safe['status'])
            self.assertEqual('ACTION_REQUIRED_UNLOCK_REDMI',safe['reason'])
        passed,safe=self.read('PASS','FAIL')
        self.assertFalse(passed);self.assertEqual('FAIL',safe['status'])

    def test_secret_payload_or_wrong_type_is_redacted_without_changing_verdict(self):
        for reason in ('token=synthetic-secret',{'payload':'synthetic'},['PRIVATE'],None,1,'bad code','ABC\nPAYLOAD','AB','A'*121):
            for status in ('PASS','FAIL'):
                passed,safe=self.read(reason,status)
                self.assertEqual(status=='PASS',passed);self.assertEqual(status,safe['status'])
                self.assertEqual('QA_DIAGNOSTIC_REDACTED',safe['reason'])
                self.assertNotIn('synthetic-secret',json.dumps(safe))

    def test_missing_reason_is_none(self):
        passed,safe=self.read(status='FAIL')
        self.assertFalse(passed);self.assertIsNone(safe['reason'])


class SshEnvironmentTest(unittest.TestCase):
    def test_owned_0666_socket_in_owned_0700_parent_is_accepted_but_not_exported_to_qa(self):
        with tempfile.TemporaryDirectory() as folder:
            parent=Path(folder);parent.chmod(0o700);node=parent/'socket'
            with socket.socket(socket.AF_UNIX) as agent:
                bind_unix_fixture(agent,node);node.chmod(0o666)
                with mock.patch.dict(t.os.environ,{'SSH_AUTH_SOCK':str(node)}):
                    self.assertEqual(str(node),t.ssh_env()['SSH_AUTH_SOCK'])
                    self.assertNotIn('SSH_AUTH_SOCK',t.minimal_env())

    def test_missing_regular_or_symlink_socket_is_denied(self):
        with mock.patch.dict(t.os.environ,{},clear=True),self.assertRaises(p.Rejected):t.ssh_env()
        with tempfile.TemporaryDirectory() as folder:
            parent=Path(folder);parent.chmod(0o700);regular=parent/'regular';regular.write_text('synthetic')
            link=parent/'link';link.symlink_to(regular)
            for node in (parent/'missing',regular,link):
                with mock.patch.dict(t.os.environ,{'SSH_AUTH_SOCK':str(node)}),self.assertRaises(p.Rejected):t.ssh_env()

    def test_foreign_socket_or_parent_and_unprotected_parent_are_denied(self):
        with tempfile.TemporaryDirectory() as folder:
            parent=Path(folder);parent.chmod(0o700);node=parent/'socket'
            with socket.socket(socket.AF_UNIX) as agent:
                bind_unix_fixture(agent,node);actual=Path.lstat
                for target,change in ((node,{'st_uid':t.os.getuid()+1}),(parent,{'st_uid':t.os.getuid()+1}),
                                      (parent,{'st_mode':0o40755}),(parent,{'st_mode':0o40777})):
                    def lstat(path,*args,**kwargs):
                        info=actual(path,*args,**kwargs)
                        return SimpleNamespace(st_uid=change.get('st_uid',info.st_uid),st_mode=change.get('st_mode',info.st_mode)) if path==target else info
                    with mock.patch.dict(t.os.environ,{'SSH_AUTH_SOCK':str(node)}),mock.patch.object(Path,'lstat',lstat),self.assertRaises(p.Rejected):t.ssh_env()

    def test_symlink_parent_is_denied(self):
        with tempfile.TemporaryDirectory() as folder:
            base=Path(folder);parent=base/'private';parent.mkdir(mode=0o700);alias=base/'alias';alias.symlink_to(parent)
            with socket.socket(socket.AF_UNIX) as agent:
                bind_unix_fixture(agent,parent/'socket')
                with mock.patch.dict(t.os.environ,{'SSH_AUTH_SOCK':str(alias/'socket')}),self.assertRaises(p.Rejected):t.ssh_env()

    def test_only_tunnel_and_control_receive_the_validated_agent_environment(self):
        environment={'PATH':'/usr/bin:/bin','SSH_AUTH_SOCK':'/private/synthetic/socket'}
        child=mock.Mock(pid=123);child.poll.return_value=None
        result=SimpleNamespace(returncode=0,stdout=b'p123\nn127.0.0.1:18083\nn127.0.0.1:28444\n')
        with mock.patch.object(t,'ssh_env',return_value=environment),mock.patch.object(t,'port_closed',return_value=True), \
             mock.patch.object(t.subprocess,'Popen',return_value=child) as spawn,mock.patch.object(t.subprocess,'run',return_value=result):
            t.Tunnel(spec()).start();self.assertEqual(environment,spawn.call_args.kwargs['env'])
        result=SimpleNamespace(returncode=0,stdout=json.dumps(stopped()).encode())
        with mock.patch.object(t,'ssh_env',return_value=environment),mock.patch.object(t.subprocess,'run',return_value=result) as run:
            t.control(spec(),'status',NONCE);self.assertEqual(environment,run.call_args.kwargs['env'])


class GuardTest(unittest.TestCase):
    def test_wrong_apk_or_serial_never_force_stops(self):
        for change in ({'apkSha256': 'f' * 64}, {'serialFingerprint': 'f' * 64}):
            phone = Phone(phone_state(servicePresent=True, **change)); value = {'before': phone_state(), 'childSpawned': True}
            with self.assertRaises(g.GuardFailure): g.clean_phone(phone, value)
            self.assertEqual([], phone.actions)

    def test_owned_sensor_and_reverse_recovered_without_turning_failure_into_pass(self):
        phone = Phone(phone_state(servicePresent=True, reverse18443=[['tcp:18443', 'tcp:18443']]))
        value = {'before': phone_state(), 'childSpawned': True}
        self.assertTrue(g.clean_phone(phone, value)); self.assertEqual([g.PACKAGE, '18443'], phone.actions)
        self.assertEqual('FAIL_FUNCTIONAL_CLEANUP_VERIFIED', p.final_status(False, False, True, True, True))

    def test_unowned_jar_is_preserved_even_when_bytes_match(self):
        phone = Phone(phone_state(qaJars={'qa-wa110-ui-' + 'c' * 32 + '.jar': p.READER}))
        value = {'before': phone_state(), 'childSpawned': True}
        self.assertFalse(g.clean_phone(phone, value)); self.assertEqual([], phone.actions)
        self.assertIn('QA_JAR_WITHOUT_ATTEMPT_MARKER_PRESERVED', value['phoneCleanupCodes'])

    def test_bridge_blocked_never_accesses_phone_or_starts_qa(self):
        class Tunnel:
            child = SimpleNamespace(pid=12345)
            def __init__(self, unused): pass
            def start(self): pass
            def close(self): return True
        def control(unused, verb, nonce):
            self.assertEqual(NONCE, nonce)
            return envelope({'status': 'BLOCKED', 'code': 'PHYSICAL_JOURNEY_MODULE_NOT_IMPLEMENTED'}) if verb == 'bridge-bootstrap' else stopped()
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary)
            for name in launch.RUNTIME_FILES: (root / name).write_text('synthetic frozen source')
            with mock.patch.object(launch, 'ROOT', root), mock.patch.object(launch, 'require_private_runtime'), \
                 mock.patch.object(launch, 'validate_files', return_value={}), \
                 mock.patch.object(launch, 'capture_local_inputs', return_value='f' * 64), \
                 mock.patch.object(launch.secrets, 'token_urlsafe', return_value=NONCE), \
                 mock.patch.object(t, 'Tunnel', Tunnel), mock.patch.object(t, 'control', side_effect=control), \
                 mock.patch.object(g, 'AdbPhone') as phone, mock.patch.object(launch.subprocess, 'Popen') as spawn:
                file, result = launch.run(spec(), 'a' * 64)
            phone.assert_not_called(); spawn.assert_not_called()
            self.assertFalse(result['phoneUsed']); self.assertFalse(result['physicalAndroidProof'])
            self.assertIn('REMOTE_BRIDGE_BLOCKED_BEFORE_PHONE', result['codes'])
            self.assertNotIn(SECRET, file.read_text())

    def test_new_launcher_has_no_local_jvm_or_original_launcher_route(self):
        for path in (Path(launch.__file__), Path(g.__file__)):
            source = path.read_text()
            for forbidden in ('launch_redmi_phased.py', 'run_native_phased.py', 'java -jar', 'mvn '):
                self.assertNotIn(forbidden, source)


class PhysicalBindingTest(unittest.TestCase):
    def bridge(self):
        bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW); bridge.bootstrap(bootstrap()); return bridge

    def test_physical_profile_jar_and_source_manifest_are_required_before_token(self):
        for change in ({'profile': 'VPS_INFRASTRUCTURE_SMOKE_ONLY_V1'}, {'jarSha256': 'a' * 64}, {'moduleSha256': 'a' * 64}):
            packet = bootstrap(); packet['result'].update(change)
            bridge = p.Bridge(spec(), NONCE, clock=lambda: NOW)
            with self.assertRaises(p.Rejected): bridge.bootstrap(packet)
            self.assertIsNone(bridge.secret)

    def test_pairing_requires_mac_tls_observation_and_qa_cannot_supply_key(self):
        bridge = self.bridge(); send = mock.Mock()
        with self.assertRaises(p.Rejected): bridge.exchange('/pair', {'code': 'BCDFGHJK'}, send)
        send.assert_not_called()
        bridge = self.bridge(); bridge.observe_pairing_begin(*observation())
        with self.assertRaises(p.Rejected): bridge.exchange('/pair', {'code': 'BCDFGHJK', 'jkt': SECRET}, send)
        send.assert_not_called()

    def test_jkt_is_canonical_rfc7638_and_duplicate_same_begin_is_idempotent(self):
        bridge = self.bridge(); request, response = observation()
        bridge.observe_pairing_begin(request, response); bridge.observe_pairing_begin(request, response)
        jwk = json.loads(request)['publicJwk']; canonical = json.dumps(jwk, sort_keys=True, separators=(',', ':')).encode()
        expected = base64.urlsafe_b64encode(hashlib.sha256(canonical).digest()).decode().rstrip('=')
        self.assertEqual(expected, bridge.pairing['jkt']); self.assertEqual(NONCE, bridge.pairing['nonce'])
        audit = bridge.audit()
        self.assertNotIn(expected, json.dumps(audit)); self.assertNotIn('00000000-0000-4000-8000-000000000001', json.dumps(audit))
        self.assertEqual(2, audit['successfulBeginResponses'])

    def test_different_or_private_key_observation_fails_closed(self):
        bridge = self.bridge(); bridge.observe_pairing_begin(*observation())
        with self.assertRaises(p.Rejected): bridge.observe_pairing_begin(*observation('00000000-0000-4000-8000-000000000002'))
        self.assertTrue(bridge.failed)
        bridge = self.bridge(); raw, response = observation(); request = json.loads(raw); request['publicJwk']['d'] = NONCE
        with self.assertRaises(p.Rejected): bridge.observe_pairing_begin(json.dumps(request).encode(), response)
        self.assertTrue(bridge.failed)

    def test_qa_output_limit_is_enforced_without_waiting_for_process_end(self):
        selector = mock.Mock(); file = mock.Mock(); file.fileno.return_value = 42
        selector.select.return_value = [(SimpleNamespace(fileobj=file), None)]
        child = mock.Mock(); child.poll.return_value = None
        with mock.patch.object(launch.selectors, 'DefaultSelector', return_value=selector), \
             mock.patch.object(launch.os, 'read', return_value=b'x' * 4096):
            with self.assertRaisesRegex(p.Rejected, 'QA_OUTPUT_LIMIT'): launch.collect_qa_output(child, clock=lambda: 0)
        selector.close.assert_called_once()

    def test_tls_handler_binds_successful_begin_and_forwards_only_scoped_headers(self):
        bridge = self.bridge(); request, response = observation()
        owner = t.LocalServers(bridge, '/private/test-qa.sock', '/private/test-tls')
        handler = object.__new__(owner.handler('tls'))
        handler.command = 'POST'; handler.path = '/driver-app/v1/pairings/begin'
        handler.headers = Message()
        for name, value in {'Host': 'localhost:18443', 'Content-Length': str(len(request)),
                            'Content-Type': 'application/json', 'DPoP': 'synthetic-proof',
                            'Cookie': 'must-not-cross', 'X-Forwarded-Host': 'must-not-cross'}.items():
            handler.headers[name] = value
        handler.rfile = io.BytesIO(request); handler.connection = mock.Mock(); handler.reply = mock.Mock()
        upstream = mock.Mock(); upstream.getresponse.return_value = mock.Mock(status=200)
        upstream.getresponse.return_value.read.return_value = response
        upstream.getresponse.return_value.getheader.return_value = None
        with mock.patch.object(t.http.client, 'HTTPConnection', return_value=upstream): handler.dispatch()
        self.assertIsNotNone(bridge.pairing)
        self.assertEqual({'Content-Type': 'application/json', 'DPoP': 'synthetic-proof'}, upstream.request.call_args.kwargs['headers'])
        handler.reply.assert_called_once_with(200, response, None)
        self.assertEqual(0, owner.active)

    def test_tls_wrong_origin_cannot_reach_upstream_or_bind_pairing(self):
        bridge = self.bridge(); owner = t.LocalServers(bridge, '/private/test-qa.sock', '/private/test-tls')
        handler = object.__new__(owner.handler('tls')); handler.command = 'POST'
        handler.path = '/driver-app/v1/pairings/begin'; handler.headers = Message(); handler.headers['Host'] = 'untrusted.invalid'
        handler.reply = mock.Mock()
        with mock.patch.object(t.http.client, 'HTTPConnection') as upstream: handler.dispatch()
        upstream.assert_not_called(); self.assertIsNone(bridge.pairing)
        handler.reply.assert_called_once_with(400, b'{"code":"SCOPED_REQUEST_REJECTED"}')


class LifecycleTest(unittest.TestCase):
    def simulate(self, *, functional_failure=False, stop_failure=False, preexisting_sensor=False,
                 sensor_after_qa=False, late_sensor=False, journal_failure=None):
        events = []
        phone = Phone(phone_state(servicePresent=preexisting_sensor))
        phone.bind = lambda: events.append('phone-bind') or 'd' * 64
        original_snapshot = phone.snapshot
        phone.snapshot = lambda: events.append('phone-snapshot') or original_snapshot()
        bridge_holder = {}
        class Tunnel:
            child = SimpleNamespace(pid=12345)
            def __init__(self, unused): pass
            def start(self): events.append('ssh-start')
            def close(self): events.append('ssh-close'); return True
        class Servers:
            inode = (1, 2)
            def __init__(self, bridge, socket, tls): self.socket_path = Path('/private/synthetic-qa.sock'); bridge_holder['value'] = bridge
            def start(self): events.append('servers-start')
            def close(self):
                events.append('servers-close')
                if late_sensor: phone.value['servicePresent'] = True
                return True
        class Lock:
            def __init__(self, *args): pass
            def __enter__(self): events.append('lock-acquire'); return self
            def __exit__(self, *args): events.append('lock-release')
        def control(unused, verb, nonce):
            events.append(verb)
            if verb == 'bridge-bootstrap':
                value = bootstrap(); value['result']['expiresAt'] = datetime.fromtimestamp(time.time() + 600, timezone.utc).isoformat().replace('+00:00', 'Z')
                return value
            if stop_failure: return envelope({'status': 'BLOCKED', 'code': 'SCOPED_CLEANUP_UNCONFIRMED'})
            return stopped()
        def send(route, body, headers):
            events.append(route)
            if route == '/shutdown':
                return envelope({'status': 'SHUTDOWN_ACK', 'backendStopped': True, 'databaseStopped': True,
                                 'cleanupConfirmed': True, 'receiptSha256': 'e' * 64})
            return envelope({'uri': 'vanridesdriver://run/start#v1.00000000-0000-4000-8000-000000000001.0.' + SECRET} if route == '/intent' else {})
        child = mock.Mock(pid=23456)
        original_write = g.write_journal
        def write(path, value):
            if (journal_failure == 'ssh-record' and value.get('status') == 'STARTING' and 'sshPid' in value
                or journal_failure == 'closing' and value.get('status') == 'CLOSING'
                or journal_failure == 'final' and 'finishedAtUtc' in value):
                events.append('journal-failure')
                raise OSError('synthetic private filesystem failure; never emitted')
            return original_write(path, value)
        def read(*unused):
            events.append('qa-read'); bridge = bridge_holder['value']; bridge.observe_pairing_begin(*observation())
            for route in p.ROUTES:
                if functional_failure and route == '/started':
                    phone.value['servicePresent'] = True; phone.value['reverse18443'] = [['tcp:18443', 'tcp:18443']]
                    raise p.Rejected('SYNTHETIC_ACK_LOST')
                bridge.exchange(route, {'code': 'BCDFGHJK'} if route == '/pair' else {}, send)
            if sensor_after_qa: phone.value['servicePresent'] = True
            return True, {'status': 'PASS', 'checks': []}
        with tempfile.TemporaryDirectory() as temporary:
            root = Path(temporary); socket = root / 'v110-test123'; socket.mkdir(mode=0o700)
            for name in launch.RUNTIME_FILES: (root / name).write_text('synthetic frozen source')
            with mock.patch.object(launch, 'ROOT', root), mock.patch.object(launch, 'require_private_runtime'), \
                 mock.patch.object(launch, 'validate_files', return_value={'qaRoot': root, 'tlsDirectory': root, 'phoneLockDirectory': root}), \
                 mock.patch.object(launch, 'capture_local_inputs', return_value='f' * 64), \
                 mock.patch.object(launch.secrets, 'token_urlsafe', return_value=NONCE), \
                 mock.patch.object(launch.tempfile, 'mkdtemp', return_value=str(socket)), \
                 mock.patch.object(t, 'Tunnel', Tunnel), mock.patch.object(t, 'LocalServers', Servers), \
                 mock.patch.object(t, 'control', side_effect=control), mock.patch.object(t, 'send_control', side_effect=send), \
                 mock.patch.object(g, 'AdbPhone', return_value=phone), mock.patch.object(g, 'DeviceLock', Lock), \
                 mock.patch.object(launch.subprocess, 'Popen', return_value=child) as spawn, \
                 mock.patch.object(launch, 'read_qa', side_effect=read), \
                 mock.patch.object(g, 'write_journal', side_effect=write), \
                 mock.patch.object(g, 'stop_owned_child', side_effect=lambda x: events.append('qa-stop')):
                file, value = launch.run(spec(), 'a' * 64)
            written = file.read_text()
            self.assertNotIn(SECRET, written); self.assertNotIn('BCDFGHJK', written)
            return value, events, phone.actions, spawn.call_count

    def test_full_mocked_success_requires_native_remote_and_local_closure(self):
        value, events, actions, spawned = self.simulate()
        self.assertEqual('PASS_TEMPORARY_VPS110_PHYSICAL', value['status'])
        self.assertTrue(value['physicalAndroidProof']); self.assertEqual(1, spawned)
        self.assertGreater(events.index('lock-release'), events.index('servers-close'))
        self.assertGreater(events.index('lock-release'), events.index('status'))
        self.assertGreater(max(i for i, event in enumerate(events) if event == 'phone-snapshot'), events.index('servers-close'))

    def test_ack_loss_stops_only_owned_native_resources_and_remains_functional_fail(self):
        value, events, actions, spawned = self.simulate(functional_failure=True)
        self.assertEqual('FAIL_FUNCTIONAL_CLEANUP_VERIFIED', value['status'])
        self.assertFalse(value['physicalAndroidProof']); self.assertEqual([g.PACKAGE, '18443'], actions)
        self.assertNotIn('/shutdown', events); self.assertIn('stop', events)

    def test_remote_cleanup_failure_cannot_promote_a_successful_qa_report(self):
        value, events, actions, spawned = self.simulate(stop_failure=True)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', value['status'])
        self.assertTrue(value['physicalAndroidObserved']); self.assertFalse(value['physicalAndroidProof'])

    def test_existing_sensor_blocks_qa_and_is_never_force_stopped(self):
        value, events, actions, spawned = self.simulate(preexisting_sensor=True)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', value['status'])
        self.assertEqual(0, spawned); self.assertEqual([], actions); self.assertFalse(value['phonePrestateAccepted'])

    def test_successful_qa_cannot_hide_emergency_sensor_stop_including_after_closure(self):
        for values in ({'sensor_after_qa': True}, {'late_sensor': True}):
            with self.subTest(values=values):
                value, events, actions, spawned = self.simulate(**values)
                self.assertEqual('FAIL_FUNCTIONAL_CLEANUP_VERIFIED', value['status'])
                self.assertFalse(value['physicalAndroidProof']); self.assertEqual([g.PACKAGE], actions)
                self.assertIn('NATIVE_SENSOR_REQUIRED_EMERGENCY_STOP', value['codes'])

    def test_journal_failure_during_cleanup_does_not_skip_native_or_remote_stop(self):
        value, events, actions, spawned = self.simulate(functional_failure=True,journal_failure='closing')
        self.assertEqual([g.PACKAGE,'18443'],actions)
        for event in ('qa-stop','stop','status','ssh-close','servers-close','lock-release'):
            self.assertIn(event,events)
        self.assertGreater(events.index('stop'),events.index('journal-failure'))
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED',value['status'])
        self.assertFalse(value['physicalAndroidProof']); self.assertFalse(value['sessionJournalVerified'])
        self.assertIn('SESSION_JOURNAL_WRITE_FAILED',value['codes'])

    def test_journal_failure_immediately_after_tunnel_still_requests_scoped_remote_stop(self):
        value, events, actions, spawned = self.simulate(journal_failure='ssh-record')
        self.assertEqual(0,spawned); self.assertEqual([],actions)
        self.assertNotIn('bridge-bootstrap',events); self.assertNotIn('phone-bind',events)
        for event in ('stop','status','ssh-close'): self.assertIn(event,events)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED',value['status']); self.assertFalse(value['physicalAndroidProof'])
        self.assertIn('SESSION_JOURNAL_WRITE_FAILED',value['codes'])

    def test_even_the_final_journal_write_failure_invalidates_an_otherwise_successful_verdict(self):
        value, events, actions, spawned = self.simulate(journal_failure='final')
        self.assertTrue(value['phoneResourcesVerified']); self.assertTrue(value['remoteStopped']); self.assertTrue(value['localClosed'])
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED',value['status'])
        self.assertFalse(value['physicalAndroidProof']); self.assertFalse(value['sessionJournalVerified'])
        self.assertIn('SESSION_JOURNAL_WRITE_FAILED',value['codes'])


class TlsShutdownTest(unittest.TestCase):
    def test_stalled_loopback_handshake_has_six_second_bound_and_shutdown_returns(self):
        # An actual loopback-only TCP peer sends no ClientHello. No certificate,
        # private key, external connection, APK or phone is involved.
        server = t.TlsServer(('127.0.0.1',0),t.QuietHandler)
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); accepted = threading.Event()
        class ObservedContext:
            def wrap_socket(self, connection, **kwargs):
                accepted.set(); return context.wrap_socket(connection,**kwargs)
        server.ssl_context = ObservedContext()
        serving = threading.Thread(target=server.serve_forever,kwargs={'poll_interval':.05},daemon=True)
        serving.start(); peer = closing = None
        try:
            self.assertEqual(6,server.handshake_timeout)
            peer = socket.create_connection(server.server_address,timeout=1)
            self.assertTrue(accepted.wait(1),'fixture was not accepted')
            started = time.monotonic()
            closing = threading.Thread(target=server.shutdown,daemon=True); closing.start(); closing.join(7.5)
            self.assertFalse(closing.is_alive(),'stalled handshake prevented server shutdown')
            self.assertLess(time.monotonic()-started,7.5)
        finally:
            if peer: peer.close()
            if closing: closing.join(2)
            if serving.is_alive(): server.shutdown()
            server.server_close(); serving.join(2)
        self.assertFalse(serving.is_alive())


class RecoveryTest(unittest.TestCase):
    def simulate(self, *, phone_used=False, inspected=True, phone_error=False, previous_receipt=None):
        record = {'specSha256': 'a' * 64, 'attemptId': spec()['attemptId'], 'sourceSha': p.SOURCE,
                  'nonce': NONCE, 'phoneUsed': phone_used, 'phoneInspected': inspected,
                  'nativeGuard': {'before': phone_state(), 'childSpawned': True},
                  'remoteReceiptSha256': previous_receipt}
        phone = Phone(phone_state()); events = []
        if phone_error: phone.snapshot = mock.Mock(side_effect=g.GuardFailure('PHONE_UNAVAILABLE'))
        class Lock:
            def __init__(self, *args): pass
            def __enter__(self): events.append('lock-acquire'); return self
            def __exit__(self, *args): events.append('lock-release')
        with tempfile.TemporaryDirectory() as temporary:
            journal = Path(temporary) / 'session-test.json'; g.write_journal(journal, record)
            with mock.patch.object(launch, 'ROOT', Path(temporary)), mock.patch.object(launch, 'require_private_runtime'), \
                 mock.patch.object(g, 'private_dir', return_value=Path(temporary)), \
                 mock.patch.object(g, 'DeviceLock', Lock), mock.patch.object(g, 'AdbPhone', return_value=phone) as discover, \
                 mock.patch.object(t, 'control', side_effect=lambda *args: events.append(args[1]) or stopped()), \
                 mock.patch.object(t, 'port_closed', return_value=True), \
                 mock.patch.object(launch, 'close_stale_unix', return_value=True):
                _, value = launch.recover(spec(), 'a' * 64, journal)
            return value, events, discover.call_count

    def test_rejected_prestate_is_not_converted_to_verified_native_cleanup(self):
        value, events, discovered = self.simulate()
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', value['status'])
        self.assertEqual(0, discovered); self.assertEqual(['stop', 'status'], events)
        self.assertIn('NATIVE_PHONE_PRESTATE_NOT_ACCEPTED_NO_MUTATION', value['codes'])

    def test_phone_read_failure_does_not_skip_remote_cleanup_and_never_grants_functional_pass(self):
        value, events, discovered = self.simulate(phone_used=True, phone_error=True)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', value['status'])
        self.assertFalse(value['physicalAndroidProof']); self.assertEqual(1, discovered)
        self.assertEqual(['lock-acquire', 'stop', 'status', 'lock-release'], events)

    def test_recovery_rejects_remote_receipt_drift(self):
        value, events, discovered = self.simulate(inspected=False, previous_receipt='f' * 64)
        self.assertEqual('BLOCKED_RECOVERY_REQUIRED', value['status'])
        self.assertIn('REMOTE_SCOPED_STOP_STATUS_REQUIRED', value['codes'])


if __name__ == '__main__': unittest.main()
