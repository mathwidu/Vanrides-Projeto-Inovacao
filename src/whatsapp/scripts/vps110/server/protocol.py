"""Shared private bridge contract. No network/process work on import."""
import base64
import datetime as dt
import hashlib
import hmac
import json
import re
from pathlib import Path


class Blocked(Exception):
    pass


def require(value, code):
    if not value:
        raise Blocked(code)


def hash_file(path):
    value = hashlib.sha256()
    with Path(path).open('rb') as source:
        for block in iter(lambda: source.read(262144), b''):
            value.update(block)
    return value.hexdigest()


def token(value):
    return isinstance(value, str) and re.fullmatch(r'[A-Za-z0-9_-]{43}', value) and base64.urlsafe_b64encode(base64.urlsafe_b64decode(value + '=')).decode().rstrip('=') == value


def context(attempt, nonce, contract):
    require(isinstance(attempt, str) and re.fullmatch(r'[a-z0-9][a-z0-9-]{0,39}', attempt), 'ATTEMPT_INVALID')
    require(token(nonce), 'NONCE_INVALID')
    return {'attemptId': attempt, 'sourceSha': contract['backend']['commit'], 'nonce': nonce}


def envelope(binding, result):
    return {**binding, 'result': result}


def check_headers(headers, binding, auth, now=None):
    expected = {'x-wa110-attempt': binding['attemptId'], 'x-wa110-source-sha': binding['sourceSha'], 'x-wa110-nonce': binding['nonce']}
    require(all(headers.get(key) == value for key, value in expected.items()), 'BRIDGE_CONTEXT_MISMATCH')
    require(hmac.compare_digest(headers.get('x-wa110-bridge-token', ''), auth['bridgeToken']), 'BRIDGE_AUTH_FAILED')
    expiry = dt.datetime.fromisoformat(auth['expiresAt'].replace('Z', '+00:00'))
    require(expiry > (now or dt.datetime.now(dt.timezone.utc)), 'BRIDGE_EXPIRED')


def phase_body(route, value, phase):
    routes = ['/pair', '/intent', '/started', '/stopped']
    require(phase < 4 and route == routes[phase], 'BRIDGE_PHASE_ORDER')
    require(isinstance(value, dict), 'BRIDGE_BODY_INVALID')
    if route == '/pair':
        require(set(value) == {'code'} and isinstance(value['code'], str) and re.fullmatch(r'[BCDFGHJKMNPQRSTVWXYZ]{8}', value['code']), 'BRIDGE_PAIR_BODY_INVALID')
    else:
        require(value == {}, 'BRIDGE_EMPTY_BODY_REQUIRED')


def receipt_bytes(value):
    return (json.dumps(value, sort_keys=True, separators=(',', ':')) + '\n').encode()
