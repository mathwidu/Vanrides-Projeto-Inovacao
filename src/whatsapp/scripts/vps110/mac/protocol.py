"""Closed protocol and argv builders for temporary VPS110. No I/O on import."""
from datetime import datetime, timezone
import base64
import hashlib
import json
from pathlib import Path
import re
import shlex
import time

SOURCE = '5ddb7c3db6c38385c018028741470ab6a509604f'
JAR = 'fc5dccdf30e234f6dc9a572ce679aa5cbc126b3e5fc90b4bf9f312270d3063ce'
APK = 'e022b8f2eafe1caa0f3eed13ea7e20d48e27f8a85076383d0175f98bf6b94cce'
CA = '153618de000dc4ad8a23f01771f7557920ea8901ada2a15055cbcbf0b3598781'
READER = '613ae571ac0b8cf78c212344edb0e890eee2203c329f9106c056c44af928b7c5'
PROFILE = 'VPS_PHYSICAL_JOURNEY_V1'
ROUTES = ('/pair', '/intent', '/started', '/stopped')
URI = re.compile(r'vanridesdriver://run/start#v1\.[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}\.(?:0|[1-9][0-9]{0,15})\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]')
RUN_ROUTE = re.compile(r'/driver-app/v1/(start-intents|runs|tracking-sessions)/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}/(activate|resume|samples|state)')


class Rejected(ValueError): pass


def need(value, code):
    if not value: raise Rejected(code)


def token(value):
    if not isinstance(value, str) or not re.fullmatch(r'[A-Za-z0-9_-]{43}', value): return False
    try: return base64.urlsafe_b64encode(base64.urlsafe_b64decode(value + '=')).decode().rstrip('=') == value
    except ValueError: return False


def validate_spec(spec):
    keys = {'schemaVersion', 'attemptId', 'sourceSha', 'backendJarSha256', 'apkSha256', 'publicCaSha256',
            'readerJarSha256', 'qaCommit', 'qaRunnerSha256', 'ssh', 'paths', 'allowPermissions',
            'remoteProfile', 'remoteModuleSha256'}
    need(isinstance(spec, dict) and set(spec) == keys and type(spec['schemaVersion']) is int and spec['schemaVersion'] == 2, 'SPEC_FIELDS_INVALID')
    need(isinstance(spec['attemptId'], str) and re.fullmatch(r'[a-z0-9][a-z0-9-]{2,47}', spec['attemptId']), 'ATTEMPT_INVALID')
    need(spec['remoteProfile'] == PROFILE and isinstance(spec['remoteModuleSha256'], str)
         and re.fullmatch(r'[a-f0-9]{64}', spec['remoteModuleSha256']), 'PHYSICAL_MODULE_PIN_REQUIRED')
    need((spec['sourceSha'], spec['backendJarSha256'], spec['apkSha256'], spec['publicCaSha256'], spec['readerJarSha256'])
         == (SOURCE, JAR, APK, CA, READER), 'ARTIFACT_PINS_INVALID')
    need(re.fullmatch(r'[a-f0-9]{40}', spec['qaCommit']) and re.fullmatch(r'[a-f0-9]{64}', spec['qaRunnerSha256']), 'QA_PIN_REQUIRED')
    need(type(spec['allowPermissions']) is bool, 'PERMISSION_OPT_IN_INVALID')
    ssh = spec['ssh']
    need(isinstance(ssh, dict) and set(ssh) == {'host', 'user', 'port', 'identityFile', 'knownHostsFile'}, 'SSH_FIELDS_INVALID')
    need(isinstance(ssh['host'], str) and re.fullmatch(r'[A-Za-z0-9][A-Za-z0-9.-]{0,252}', ssh['host'])
         and ssh['user'] == 'mathwidu' and ssh['port'] == 22, 'SSH_DESTINATION_INVALID')
    need(isinstance(spec['paths'], dict) and set(spec['paths']) == {'apk', 'qaRoot', 'readerJar', 'tlsDirectory', 'phoneLockDirectory'}, 'PATH_FIELDS_INVALID')
    for value in [*spec['paths'].values(), ssh['identityFile'], ssh['knownHostsFile']]:
        need(isinstance(value, str) and value and '\n' not in value and '\0' not in value, 'LOCAL_PATH_INVALID')
    return spec


def ssh_base(spec):
    validate_spec(spec); ssh = spec['ssh']
    options = ('BatchMode=yes', 'StrictHostKeyChecking=yes', 'UpdateHostKeys=no', 'ForwardAgent=no',
        'ForwardX11=no', 'PermitLocalCommand=no', 'RemoteCommand=none', 'ProxyCommand=none', 'ProxyJump=none',
        'ControlMaster=no', 'ControlPath=none', 'ControlPersist=no', 'IdentityAgent=SSH_AUTH_SOCK', 'IdentitiesOnly=yes', 'UseKeychain=yes',
        'PasswordAuthentication=no', 'KbdInteractiveAuthentication=no', 'PreferredAuthentications=publickey',
        'GatewayPorts=no', 'ExitOnForwardFailure=yes', 'ServerAliveInterval=10', 'ServerAliveCountMax=3',
        'ConnectTimeout=10', 'GlobalKnownHostsFile=/dev/null')
    args = ['/usr/bin/ssh', '-F', '/dev/null', '-T', '-n', '-p', '22', '-l', ssh['user'],
            '-i', str(Path(ssh['identityFile']).expanduser())]
    for option in (*options, 'UserKnownHostsFile=' + str(Path(ssh['knownHostsFile']).expanduser())): args += ['-o', option]
    return args


def tunnel_argv(spec):
    return [*ssh_base(spec), '-N', '-L', '127.0.0.1:18083:127.0.0.1:28083',
            '-L', '127.0.0.1:28444:127.0.0.1:28444', spec['ssh']['host']]


def control_argv(spec, verb, nonce):
    need(verb in ('bridge-bootstrap', 'stop', 'status') and token(nonce), 'REMOTE_COMMAND_DENIED')
    remote = '/home/mathwidu/.local/share/vanrides/wa110-vps-lab/' + spec['attemptId'] + '/control.py'
    # SSH runs a remote shell: quote each closed, locally generated argument.
    command = shlex.join(['python3', remote, verb, '--attempt', spec['attemptId'], '--nonce', nonce])
    return [*ssh_base(spec), spec['ssh']['host'], command]


def qa_argv(spec, socket_path, python='python3'):
    validate_spec(spec)
    need(Path(socket_path).is_absolute() and len(str(socket_path).encode()) < 100, 'QA_SOCKET_PATH_INVALID')
    root = Path(spec['paths']['qaRoot']).expanduser()
    args = [python, str(root / 'integration_test/driver_run/android_lab.py'), '--adb',
            str(Path.home() / 'Library/Android/sdk/platform-tools/adb'), '--apk', str(Path(spec['paths']['apk']).expanduser()),
            '--socket', str(socket_path), '--official-reader-jar', str(Path(spec['paths']['readerJar']).expanduser())]
    if spec['allowPermissions']: args += ['--official-reader-permissions']
    return args


def tls_route(method, path):
    pairs = {'/driver-app/v1/pairings/begin': 'POST', '/driver-app/v1/pairings/redeem': 'POST',
             '/driver-app/v1/session/refresh': 'POST', '/driver-app/v1/session/revoke': 'POST',
             '/driver-app/v1/status': 'GET', '/driver-app/v1/tracking-status': 'GET'}
    if pairs.get(path) == method: return True
    if method == 'GET' and re.fullmatch(r'/driver-app/v1/pairings/[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}/status', path): return True
    match = RUN_ROUTE.fullmatch(path) if method == 'POST' else None
    return bool(match and match[2] in {'start-intents': ('activate',), 'runs': ('resume',), 'tracking-sessions': ('samples', 'state')}[match[1]])


class Bridge:
    def __init__(self, spec, nonce, clock=time.time):
        validate_spec(spec); need(token(nonce), 'NONCE_REQUIRED')
        self.spec, self.nonce, self.clock = spec, nonce, clock
        self.secret = None; self.deadline = 0; self.step = 0; self.failed = False; self.receipt = None
        self.pairing = None; self.pairing_observations = 0
    def unwrap(self, envelope):
        need(isinstance(envelope, dict) and set(envelope) == {'attemptId', 'sourceSha', 'nonce', 'result'}
             and envelope['attemptId'] == self.spec['attemptId'] and envelope['sourceSha'] == SOURCE
             and envelope['nonce'] == self.nonce and isinstance(envelope['result'], dict), 'BRIDGE_CONTEXT_MISMATCH')
        return envelope['result']
    def bootstrap(self, envelope):
        result = self.unwrap(envelope)
        need(not (result.get('status') == 'BLOCKED'), 'REMOTE_BRIDGE_BLOCKED_BEFORE_PHONE')
        need(set(result) == {'bridgeToken', 'expiresAt', 'apiPort', 'sidecarPort', 'profile', 'jarSha256', 'moduleSha256'}
             and token(result['bridgeToken']) and type(result['apiPort']) is int and type(result['sidecarPort']) is int
             and result['apiPort'] == 28083 and result['sidecarPort'] == 28444
             and result['profile'] == self.spec['remoteProfile'] == PROFILE
             and result['jarSha256'] == self.spec['backendJarSha256'] == JAR
             and result['moduleSha256'] == self.spec['remoteModuleSha256'], 'PHYSICAL_BOOTSTRAP_PINS_INVALID')
        need(isinstance(result['expiresAt'], str) and result['expiresAt'].endswith('Z'), 'BRIDGE_EXPIRY_INVALID')
        expiry = datetime.fromisoformat(result['expiresAt'].replace('Z', '+00:00')).timestamp()
        need(self.clock() + 30 < expiry <= self.clock() + 1800, 'BRIDGE_LIFETIME_INVALID')
        self.secret, self.deadline = result['bridgeToken'], expiry
    def observe_pairing_begin(self, request_bytes, response_bytes):
        """Only the local TLS relay may call this after upstream HTTP200. No private value is recorded."""
        try:
            self.headers()
            body, response = json.loads(request_bytes), json.loads(response_bytes)
            need(isinstance(body, dict) and body.get('platform') == 'ANDROID'
                 and isinstance(body.get('publicJwk'), dict), 'PAIRING_OBSERVATION_INVALID')
            jwk = body['publicJwk']
            need(set(jwk) == {'crv', 'kty', 'x', 'y'} and jwk['crv'] == 'P-256' and jwk['kty'] == 'EC'
                 and token(jwk['x']) and token(jwk['y']), 'PUBLIC_PAIRING_JWK_INVALID')
            need(isinstance(response, dict) and isinstance(response.get('pairingId'), str)
                 and re.fullmatch(r'[a-f0-9]{8}(?:-[a-f0-9]{4}){3}-[a-f0-9]{12}', response['pairingId']), 'PAIRING_RESPONSE_INVALID')
            canonical = json.dumps({key: jwk[key] for key in ('crv', 'kty', 'x', 'y')}, separators=(',', ':')).encode()
            jkt = base64.urlsafe_b64encode(hashlib.sha256(canonical).digest()).decode().rstrip('=')
            observed = {'pairingId': response['pairingId'], 'jkt': jkt, 'nonce': self.nonce}
            need(self.pairing is None or self.pairing == observed, 'MULTIPLE_PHYSICAL_PAIRINGS')
            self.pairing = observed; self.pairing_observations += 1
        except Exception:
            self.failed = True
            raise Rejected('PAIRING_OBSERVATION_FAILED') from None
    def audit(self):
        # A hash binds the public challenge/key/nonce without retaining those identifiers in receipts.
        binding = {'attemptId': self.spec['attemptId'], 'sourceSha': SOURCE, **(self.pairing or {})}
        return {'completedSteps': self.step, 'failed': self.failed,
                'successfulBeginResponses': self.pairing_observations,
                'pairingBindingSha256': hashlib.sha256(json.dumps(binding, sort_keys=True, separators=(',', ':')).encode()).hexdigest() if self.pairing else None}
    def headers(self):
        need(self.secret is not None and self.clock() < self.deadline, 'BRIDGE_EXPIRED_OR_CLOSED')
        return {'Content-Type': 'application/json', 'X-WA110-Attempt': self.spec['attemptId'],
                'X-WA110-Source-Sha': SOURCE, 'X-WA110-Nonce': self.nonce, 'X-WA110-Bridge-Token': self.secret}
    def exchange(self, route, body, send):
        need(not self.failed and self.step < len(ROUTES) and route == ROUTES[self.step], 'CONTROL_ORDER_DENIED')
        need(isinstance(body, dict) and (set(body) == {'code'} and isinstance(body['code'], str)
             and re.fullmatch(r'[BCDFGHJKMNPQRSTVWXYZ]{8}', body['code']) if route == '/pair' else body == {}), 'CONTROL_BODY_DENIED')
        try:
            outgoing = body
            if route == '/pair':
                need(self.pairing is not None and self.pairing['nonce'] == self.nonce, 'PAIRING_NOT_OBSERVED_ON_MAC_TLS')
                outgoing = {'code': body['code'], 'pairingId': self.pairing['pairingId'], 'jkt': self.pairing['jkt']}
            result = self.unwrap(send(route, outgoing, self.headers()))
            need(set(result) == ({'uri'} if route == '/intent' else set()), 'CONTROL_RESULT_DENIED')
            if route == '/intent': need(isinstance(result['uri'], str) and URI.fullmatch(result['uri']), 'START_URI_DENIED')
            self.step += 1
            return result
        except Exception:
            self.failed = True
            raise Rejected('CONTROL_FAILED_NO_AUTOMATIC_RETRY') from None
    def shutdown(self, send):
        result = self.unwrap(send('/shutdown', {}, self.headers()))
        need(set(result) == {'status', 'backendStopped', 'databaseStopped', 'cleanupConfirmed', 'receiptSha256'}
             and result['status'] == 'SHUTDOWN_ACK' and all(result[key] is True for key in ('backendStopped', 'databaseStopped', 'cleanupConfirmed'))
             and re.fullmatch(r'[a-f0-9]{64}', result['receiptSha256']), 'REMOTE_SHUTDOWN_NOT_PROVED')
        self.receipt = result['receiptSha256']; self.secret = None
        return self.receipt
    def stopped(self, envelope):
        result = self.unwrap(envelope)
        need(set(result) == {'status', 'backendStopped', 'databaseStopped', 'cleanupConfirmed', 'listenerClosed', 'receiptSha256'}
             and result['status'] == 'STOPPED'
             and all(result[key] is True for key in ('backendStopped', 'databaseStopped', 'cleanupConfirmed', 'listenerClosed'))
             and re.fullmatch(r'[a-f0-9]{64}', result['receiptSha256'])
             and (self.receipt is None or result['receiptSha256'] == self.receipt), 'REMOTE_STOP_STATUS_NOT_PROVED')
        self.receipt = result['receiptSha256']; self.secret = None
        return True


def qa_pass(report):
    required = {'actual_apk_dex_excludes_spike030_origin_and_transport', 'verified_apk_and_explicit_launcher',
                'synthetic_pairing_confirmed_on_same_device', 'explicit_start_confirmed_only_after_backend_valid_gps',
                'native_sensor_stopped_while_ack_offline', 'durable_stop_ack_after_process_reopening_without_new_collection'}
    return (isinstance(report, dict) and report.get('status') == 'PASS' and report.get('apkSha256') == APK
            and report.get('physicalAndroid') is True and report.get('physicalIos') is False
            and report.get('channel') == 'synthetic' and required <= set(report.get('checks', [])))


def final_status(qa_ok, protocol_complete, phone_verified, remote_stopped, local_closed, interrupted=False):
    if not all((phone_verified, remote_stopped, local_closed)): return 'BLOCKED_RECOVERY_REQUIRED'
    return 'PASS_TEMPORARY_VPS110_PHYSICAL' if qa_ok and protocol_complete and not interrupted else 'FAIL_FUNCTIONAL_CLEANUP_VERIFIED'
