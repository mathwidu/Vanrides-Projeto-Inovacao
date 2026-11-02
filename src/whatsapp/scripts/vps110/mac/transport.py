"""Mac-only transports. No connection/listener/process is started by import."""
import http.client
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import json
import os
from pathlib import Path
import re
import socket
import socketserver
import ssl
import stat
import subprocess
import threading
import time

import protocol as p


def minimal_env():
    return {key: value for key, value in os.environ.items() if key in ('HOME', 'USER', 'LOGNAME', 'LANG', 'LC_ALL', 'TMPDIR')} | {'PATH': '/usr/bin:/bin'}


def ssh_env():
    value = os.environ.get('SSH_AUTH_SOCK')
    p.need(isinstance(value,str) and value and '\0' not in value and '\n' not in value
           and Path(value).is_absolute(), 'SSH_AUTH_SOCKET_REQUIRED')
    node = Path(value)
    try:
        info, parent = node.lstat(), node.parent.lstat()
    except OSError:
        raise p.Rejected('SSH_AUTH_SOCKET_UNAVAILABLE') from None
    p.need(stat.S_ISSOCK(info.st_mode) and info.st_uid == os.getuid(), 'SSH_AUTH_SOCKET_NOT_OWNED')
    p.need(stat.S_ISDIR(parent.st_mode) and parent.st_uid == os.getuid()
           and stat.S_IMODE(parent.st_mode) == 0o700, 'SSH_AUTH_SOCKET_PARENT_NOT_PRIVATE')
    # A 0666 socket is valid inside its own 0700 directory. No socket is opened here.
    return minimal_env() | {'SSH_AUTH_SOCK':value}


def control(spec, verb, nonce):
    result = subprocess.run(p.control_argv(spec, verb, nonce), capture_output=True,
                            env=ssh_env(), timeout=330 if verb == 'stop' else 30)
    p.need(result.returncode == 0 and len(result.stdout) <= 8192, 'SCOPED_SSH_CONTROL_FAILED')
    try: return json.loads(result.stdout)
    except ValueError: raise p.Rejected('SCOPED_SSH_CONTROL_JSON_INVALID') from None


def assert_owned_listeners(pid, text):
    owners, addresses = set(), set()
    for line in text.splitlines():
        if line.startswith('p'): owners.add(line[1:])
        if line.startswith('n'): addresses.add(line[1:])
    p.need(owners == {str(pid)} and addresses == {'127.0.0.1:18083', '127.0.0.1:28444'}, 'SSH_LISTENER_OWNERSHIP_NOT_PROVED')


def port_closed(port):
    with socket.socket() as client:
        client.settimeout(.3)
        return client.connect_ex(('127.0.0.1', port)) != 0


class Tunnel:
    def __init__(self, spec): self.spec, self.child = spec, None
    def start(self):
        p.need(all(port_closed(port) for port in (18083, 28444, 18443)), 'LOCAL_LAB_PORT_ALREADY_USED')
        self.child = subprocess.Popen(p.tunnel_argv(self.spec), stdin=subprocess.DEVNULL,
                                     stdout=subprocess.DEVNULL, stderr=subprocess.DEVNULL,
                                     env=ssh_env(), start_new_session=True)
        deadline = time.monotonic() + 15
        while time.monotonic() < deadline:
            p.need(self.child.poll() is None, 'SSH_TUNNEL_NOT_RUNNING')
            found = subprocess.run(['/usr/sbin/lsof', '-nP', '-a', '-p', str(self.child.pid),
                                    '-iTCP', '-sTCP:LISTEN', '-Fpn'], capture_output=True, timeout=3)
            try:
                assert_owned_listeners(self.child.pid, found.stdout.decode())
                return
            except p.Rejected: time.sleep(.2)
        raise p.Rejected('SSH_FORWARD_OWNERSHIP_TIMEOUT')
    def close(self):
        if self.child is not None and self.child.poll() is None:
            self.child.terminate()
            try: self.child.wait(timeout=10)
            except subprocess.TimeoutExpired: self.child.kill(); self.child.wait(timeout=5)
        return (self.child is None or self.child.poll() is not None) and all(port_closed(port) for port in (18083, 28444))


def send_control(route, body, headers):
    p.need(route in (*p.ROUTES, '/shutdown'), 'CONTROL_ROUTE_DENIED')
    client = http.client.HTTPConnection('127.0.0.1', 28444, timeout=300 if route == '/shutdown' else 120)
    try:
        client.request('POST', route, json.dumps(body).encode(), headers)
        response = client.getresponse(); raw = response.read(8193)
        p.need(response.status == 200 and len(raw) <= 8192 and response.getheader('Set-Cookie') is None,
               'CONTROL_HTTP_REJECTED')
        return json.loads(raw)
    finally: client.close()


class UnixServer(socketserver.ThreadingMixIn, socketserver.UnixStreamServer):
    daemon_threads = True
    def handle_error(self, request, client_address): pass


class TlsServer(ThreadingHTTPServer):
    handshake_timeout = 6
    def handle_error(self, request, client_address): pass
    def get_request(self):
        # Accept raw TCP first so a peer that never sends ClientHello cannot
        # block serve_forever/shutdown indefinitely inside SSLSocket.accept().
        connection, address = super().get_request()
        connection.settimeout(self.handshake_timeout)
        try:
            secured = self.ssl_context.wrap_socket(connection, server_side=True)
            secured.settimeout(15)
            return secured, address
        except BaseException:
            connection.close()
            raise


class QuietHandler(BaseHTTPRequestHandler):
    protocol_version = 'HTTP/1.0'
    def log_message(self, *unused): pass
    def read_body(self, limit):
        p.need(self.headers.get('Transfer-Encoding') is None, 'CHUNKED_REQUEST_DENIED')
        lengths = self.headers.get_all('Content-Length', [])
        p.need(len(lengths) <= 1 and (not lengths or lengths[0].isdigit()), 'BODY_LENGTH_INVALID')
        size = int(lengths[0]) if lengths else 0
        p.need(size <= limit, 'BODY_TOO_LARGE')
        self.connection.settimeout(15)
        body = self.rfile.read(size)
        p.need(len(body) == size, 'BODY_INCOMPLETE')
        return body
    def reply(self, status, body, nonce=None):
        self.send_response(status); self.send_header('Content-Type', 'application/json'); self.send_header('Cache-Control', 'no-store')
        if nonce is not None:
            p.need(re.fullmatch(r'[A-Za-z0-9_-]{1,256}', nonce), 'DPOP_NONCE_INVALID')
            self.send_header('DPoP-Nonce', nonce)
        self.send_header('Content-Length', str(len(body))); self.end_headers(); self.wfile.write(body)


class LocalServers:
    def __init__(self, bridge, socket_path, tls_directory):
        self.bridge, self.socket_path, self.tls_directory = bridge, Path(socket_path), Path(tls_directory)
        self.unix = self.tls = None; self.threads = []; self.serving = []; self.inode = None
        self.active = 0; self.lock = threading.Lock(); self.control_lock = threading.Lock()
    def handler(self, kind):
        owner = self
        class Handler(QuietHandler):
            def do_POST(self): self.dispatch()
            def do_GET(self): self.dispatch()
            def dispatch(self):
                with owner.lock: owner.active += 1
                try:
                    if kind == 'unix':
                        p.need(self.command == 'POST' and self.path in p.ROUTES
                               and self.headers.get('Content-Type') == 'application/json'
                               and self.headers.get('Cookie') is None, 'QA_CONTROL_DENIED')
                        raw = self.read_body(192); body = json.loads(raw)
                        p.need(owner.control_lock.acquire(blocking=False), 'CONTROL_BUSY')
                        try: result = owner.bridge.exchange(self.path, body, send_control)
                        finally: owner.control_lock.release()
                        self.reply(200, json.dumps(result).encode())
                    else:
                        p.need(self.headers.get('Host') == 'localhost:18443' and p.tls_route(self.command, self.path), 'APK_ROUTE_DENIED')
                        body = self.read_body(32768)
                        p.need(self.command != 'GET' or not body, 'GET_BODY_DENIED')
                        headers = {key: self.headers[key] for key in ('Authorization', 'DPoP', 'Idempotency-Key', 'Content-Type') if key in self.headers}
                        client = http.client.HTTPConnection('127.0.0.1', 18083, timeout=12)
                        try:
                            client.request(self.command, self.path, body=body or None, headers=headers)
                            response = client.getresponse(); payload = response.read(32769)
                            p.need(200 <= response.status < 500 and len(payload) <= 32768, 'APK_UPSTREAM_REJECTED')
                            if self.command == 'POST' and self.path == '/driver-app/v1/pairings/begin' and response.status == 200:
                                with owner.control_lock: owner.bridge.observe_pairing_begin(body, payload)
                            self.reply(response.status, payload, response.getheader('DPoP-Nonce'))
                        finally: client.close()
                except Exception:
                    try: self.reply(409 if kind == 'unix' else 400, b'{"code":"SCOPED_REQUEST_REJECTED"}')
                    except (OSError, ValueError): pass
                finally:
                    with owner.lock: owner.active -= 1
        return Handler
    def start(self):
        p.need(len(str(self.socket_path).encode()) < 100 and not self.socket_path.exists(), 'PRIVATE_SOCKET_PATH_INVALID')
        self.unix = UnixServer(str(self.socket_path), self.handler('unix')); os.chmod(self.socket_path, 0o600)
        info = self.socket_path.lstat(); self.inode = (info.st_dev, info.st_ino)
        p.need(stat.S_ISSOCK(info.st_mode) and info.st_uid == os.getuid(), 'PRIVATE_SOCKET_BINDING_INVALID')
        self.tls = TlsServer(('127.0.0.1', 18443), self.handler('tls'))
        context = ssl.SSLContext(ssl.PROTOCOL_TLS_SERVER); context.minimum_version = ssl.TLSVersion.TLSv1_2
        context.load_cert_chain(self.tls_directory / 'leaf.pem', self.tls_directory / 'leaf-key.pem', password=lambda: b'')
        self.tls.ssl_context = context
        for server in (self.unix, self.tls):
            thread = threading.Thread(target=server.serve_forever, daemon=True); thread.start()
            self.threads.append(thread); self.serving.append(server)
    def close(self):
        ok = True
        for server in (self.unix, self.tls):
            if server:
                # shutdown is valid only after serve_forever started.
                if server in self.serving: server.shutdown()
                server.server_close()
        for thread in self.threads: thread.join(timeout=3); ok &= not thread.is_alive()
        deadline = time.monotonic() + 16
        while self.active and time.monotonic() < deadline: time.sleep(.1)
        ok &= self.active == 0
        if self.socket_path.exists():
            info = self.socket_path.lstat()
            if stat.S_ISSOCK(info.st_mode) and (info.st_dev, info.st_ino) == self.inode and info.st_uid == os.getuid(): self.socket_path.unlink()
            else: ok = False
        return ok and port_closed(18443) and not self.socket_path.exists()
