#!/usr/bin/env python3
"""Owned, temporary infrastructure only. Never emits a physical-test PASS."""
import argparse
import datetime as dt
import hashlib
import json
import math
import os
from pathlib import Path
import platform
import pwd
import re
import secrets
import shutil
import socket
import stat
import subprocess
import sys
sys.dont_write_bytecode = True
import signal
import time
import urllib.request

from protocol import Blocked, context, envelope, hash_file, receipt_bytes, require

HERE = Path(__file__).resolve().parent
FIXED_FILES = ('control.py', 'protocol.py', 'compose.yaml', 'contract.json',
               'inputs/backend.jar', 'inputs/verified-ci-receipt.json', 'inputs/public-ca.der')
PRIVATE_NAMES = ('db-password', 'pgpass', 'application.properties', 'operator-private.json')


def load(path):
    return json.loads(path.read_bytes())


def private_json(path, value):
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, 'O_NOFOLLOW', 0)
    fd = os.open(path, flags, 0o600)
    with os.fdopen(fd, 'wb') as target:
        target.write(receipt_bytes(value))


def verify_package(base):
    seal = load(base / 'SHA256SUMS.json')
    require(set(seal) == set(FIXED_FILES), 'PACKAGE_WHITELIST_MISMATCH')
    for name, expected in seal.items():
        path = base / name
        require(not path.is_symlink() and path.is_file() and path.resolve().is_relative_to(base.resolve()), 'PACKAGE_PATH_INVALID')
        require(re.fullmatch('[0-9a-f]{64}', expected) and hash_file(path) == expected, 'PACKAGE_DIGEST_MISMATCH')
    contract = load(base / 'contract.json')
    require((base / 'inputs/backend.jar').stat().st_size == contract['backend']['jarBytes']
            and seal['inputs/backend.jar'] == contract['backend']['jarSha256']
            and seal['inputs/verified-ci-receipt.json'] == contract['backend']['verifiedReceiptSha256']
            and seal['inputs/public-ca.der'] == contract['apk']['publicCaSha256'], 'PINNED_INPUT_MISMATCH')
    return contract, hashlib.sha256(receipt_bytes(seal)).hexdigest()


def memory_values():
    text = Path('/proc/meminfo').read_text()
    available = int(re.search(r'^MemAvailable:\s+(\d+)', text, re.M)[1]) // 1024
    pressure = float(re.search(r'^full avg10=([\d.]+)', Path('/proc/pressure/memory').read_text(), re.M)[1])
    return available, pressure


class PressureGuard:
    """Bounded lab guard: headroom and severe stalls fail immediately; warnings need continuity."""
    def __init__(self, clock=time.monotonic, snapshot=memory_values):
        self.clock, self.snapshot = clock, snapshot
        self.warning_since = None
        self.last_at = None
        self.contiguous = False
        self.samples, self.minimum_mib, self.peak_full = 0, None, 0.0
        self.current_mib = None

    def sample(self, enforce_warning=True):
        now = self.clock()
        available, full = self.snapshot()
        require(type(available) is int and available >= 0 and isinstance(full, (int, float))
                and math.isfinite(full) and 0 <= full <= 100, 'VPS_MEMORY_MEASUREMENT_INVALID')
        self.samples += 1
        self.current_mib = available
        self.minimum_mib = available if self.minimum_mib is None else min(self.minimum_mib, available)
        self.peak_full = max(self.peak_full, full)
        require(available >= 2048, 'VPS_HEADROOM_LOW')
        require(not enforce_warning or full < 5, 'VPS_CRITICAL_MEMORY_PRESSURE')
        self.contiguous = self.last_at is not None and 0 <= now-self.last_at <= 2
        require(not enforce_warning or self.warning_since is None or self.contiguous, 'VPS_PRESSURE_OBSERVATION_GAP')
        if full < 1:
            self.warning_since = None
        elif self.warning_since is not None and not self.contiguous:
            require(not enforce_warning, 'VPS_PRESSURE_OBSERVATION_GAP')
            self.warning_since = now
        elif self.warning_since is None:
            self.warning_since = now
        self.last_at = now
        require(not enforce_warning or self.warning_since is None or now-self.warning_since < 10, 'VPS_SUSTAINED_MEMORY_PRESSURE')
        return full < 1

    def summary(self):
        return {'samples': self.samples, 'minimumAvailableMiB': self.minimum_mib,
                'peakFullAvg10': self.peak_full, 'minimumRequiredMiB': 2048,
                'criticalFullAvg10': 5, 'warningFullAvg10': 1, 'warningSeconds': 10}


class OwnedMemoryGuard:
    """A validated container's pinned cgroup v2; samples never call Docker."""
    FILES = ('memory.max', 'memory.current', 'memory.peak', 'memory.events', 'memory.pressure')
    EVENTS = {'low', 'high', 'max', 'oom', 'oom_kill', 'oom_group_kill'}
    FATAL_EVENTS = {'max', 'oom', 'oom_kill', 'oom_group_kill'}

    def __init__(self, row, limit, available, proc_root=Path('/proc'), cgroup_root=Path('/sys/fs/cgroup'), clock=time.monotonic):
        self.container_id, self.pid = row['Id'], row['State']['Pid']
        require(re.fullmatch('[a-f0-9]{64}', self.container_id) and type(self.pid) is int and self.pid > 0
                and row['State']['Running'] is True and row['State'].get('OOMKilled') is False
                and row.get('RestartCount') == 0 and row['HostConfig']['Memory'] == limit, 'OWNED_CONTAINER_RUNTIME_INVALID')
        self.proc = Path(proc_root) / str(self.pid)
        self.root, self.limit = Path(cgroup_root), limit
        require(self.root.resolve() == self.root and (self.root / 'cgroup.controllers').is_file(), 'CGROUP_V2_UNAVAILABLE')
        self.proc_cgroup = self._proc_read('cgroup')
        lines = self.proc_cgroup.strip().splitlines()
        require(len(lines) == 1 and lines[0].startswith('0::/'), 'CGROUP_V2_PATH_INVALID')
        relative = lines[0][3:]
        parts = relative.split('/')[1:]
        require(parts and all(part and part not in ('.', '..') for part in parts)
                and parts[-1] in (self.container_id, 'docker-' + self.container_id + '.scope'), 'OWNED_CGROUP_ID_MISMATCH')
        self.path = self.root.joinpath(*parts)
        require(self.path.resolve() == self.path and self.path.is_dir() and not self.path.is_symlink(), 'OWNED_CGROUP_PATH_INVALID')
        self.directory = self._fingerprint(self.path)
        self.start_ticks = self._ticks()
        self.file_ids = {}
        self.available, self.full = available, 0.0
        self.pressure = PressureGuard(clock=clock, snapshot=lambda: (self.available, self.full))
        self.events_baseline = self.events_last = None
        self.psi_baseline = self.psi_last = None
        self.current = self.peak = self.some_peak = 0
        self.samples = 0
        self.sample(available)

    @staticmethod
    def _fingerprint(path):
        value = path.lstat()
        require(not stat.S_ISLNK(value.st_mode), 'OWNED_CGROUP_REPLACED')
        return value.st_dev, value.st_ino

    def _proc_read(self, name):
        try:
            return (self.proc / name).read_text()
        except (OSError, UnicodeError):
            raise Blocked('OWNED_CGROUP_PROCESS_UNAVAILABLE') from None

    def _ticks(self):
        value = self._proc_read('stat')
        try:
            return int(value[value.rfind(')') + 2:].split()[19])
        except (IndexError, ValueError):
            raise Blocked('OWNED_CGROUP_PROCESS_INVALID') from None

    def _read(self, name):
        fd = None
        try:
            require(self._fingerprint(self.path) == self.directory and self.path.resolve() == self.path,
                    'OWNED_CGROUP_REPLACED')
            fd = os.open(self.path / name, os.O_RDONLY | os.O_NOFOLLOW)
            info = os.fstat(fd)
            require(stat.S_ISREG(info.st_mode), 'OWNED_CGROUP_METRIC_INVALID')
            identity = (info.st_dev, info.st_ino)
            require(name not in self.file_ids or self.file_ids[name] == identity, 'OWNED_CGROUP_METRIC_REPLACED')
            self.file_ids[name] = identity
            raw = os.read(fd, 16385)
            require(0 < len(raw) <= 16384, 'OWNED_CGROUP_METRIC_INVALID')
            return raw.decode('ascii').strip()
        except (OSError, UnicodeError):
            raise Blocked('OWNED_CGROUP_MEASUREMENT_UNAVAILABLE') from None
        finally:
            if fd is not None:
                os.close(fd)

    @staticmethod
    def _integer(value):
        require(re.fullmatch('[0-9]+', value) is not None, 'OWNED_CGROUP_METRIC_INVALID')
        return int(value)

    def sample(self, available):
        require(self._proc_read('cgroup') == self.proc_cgroup and self._ticks() == self.start_ticks,
                'OWNED_CGROUP_PROCESS_CHANGED')
        require(self._integer(self._read('memory.max')) == self.limit, 'OWNED_CGROUP_LIMIT_CHANGED')
        self.current = self._integer(self._read('memory.current'))
        self.peak = max(self.peak, self._integer(self._read('memory.peak')))
        event_lines = self._read('memory.events').splitlines()
        events = {}
        for line in event_lines:
            fields = line.split()
            require(len(fields) == 2 and fields[0] not in events, 'OWNED_CGROUP_EVENTS_INVALID')
            events[fields[0]] = self._integer(fields[1])
        require(self.EVENTS <= set(events), 'OWNED_CGROUP_EVENTS_MISSING')
        if self.events_baseline is None:
            require(all(events[key] == 0 for key in self.FATAL_EVENTS), 'OWNED_CGROUP_EVENTS_NOT_FRESH')
            self.events_baseline = events.copy()
        require(set(events) == set(self.events_last or events)
                and all(events[key] >= (self.events_last or events)[key] for key in events), 'OWNED_CGROUP_EVENTS_RESET')
        self.events_last = events
        require(all(events[key] == self.events_baseline[key] for key in self.FATAL_EVENTS), 'OWNED_CGROUP_MEMORY_EVENT')
        psi = {}
        for line in self._read('memory.pressure').splitlines():
            fields = line.split()
            require(len(fields) == 5 and fields[0] in ('some', 'full') and fields[0] not in psi, 'OWNED_CGROUP_PSI_INVALID')
            values = dict(field.split('=', 1) for field in fields[1:] if '=' in field)
            require(set(values) == {'avg10', 'avg60', 'avg300', 'total'}, 'OWNED_CGROUP_PSI_INVALID')
            try:
                averages = [float(values[key]) for key in ('avg10', 'avg60', 'avg300')]
            except ValueError:
                raise Blocked('OWNED_CGROUP_PSI_INVALID') from None
            require(all(math.isfinite(value) and 0 <= value <= 100 for value in averages), 'OWNED_CGROUP_PSI_INVALID')
            psi[fields[0]] = {'avg10': averages[0], 'total': self._integer(values['total'])}
        require(set(psi) == {'some', 'full'}, 'OWNED_CGROUP_PSI_MISSING')
        if self.psi_baseline is None:
            self.psi_baseline = {key: value['total'] for key, value in psi.items()}
        require(all(psi[key]['total'] >= (self.psi_last or psi)[key]['total'] for key in psi), 'OWNED_CGROUP_PSI_RESET')
        self.psi_last = psi
        self.available, self.full = available, psi['full']['avg10']
        self.some_peak = max(self.some_peak, psi['some']['avg10'])
        self.samples += 1
        return self.pressure.sample()

    def summary(self):
        return {'samples': self.samples, 'limitBytes': self.limit, 'currentBytes': self.current, 'peakBytes': self.peak,
                'peakFullAvg10': self.pressure.peak_full, 'peakSomeAvg10': self.some_peak,
                'fullTotalDeltaUs': self.psi_last['full']['total'] - self.psi_baseline['full'],
                'someTotalDeltaUs': self.psi_last['some']['total'] - self.psi_baseline['some'],
                'eventDeltas': {key: value - self.events_baseline[key] for key, value in self.events_last.items()}}


def open_port(host, port):
    try:
        with socket.create_connection((host, port), timeout=1):
            return True
    except OSError:
        return False


class Runtime:
    def __init__(self, args, base=HERE):
        self.base, self.args = base, args
        self.contract, self.package_sha = verify_package(base)
        self.binding = context(args.attempt, args.nonce, self.contract)
        self.project = self.contract['destination']['projectPrefix'] + args.attempt
        self.runtime = base / 'runtime'
        self.deadline = time.monotonic() + self.contract['destination']['ttlSeconds']
        self.pressure_guard = PressureGuard()
        self.owned_memory = {}
        self.startup_stage = 'NOT_STARTED'
        self.owner_path = self.runtime / 'owner.json'
        self.env = {'PATH': '/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin',
                    'HOME': str(Path.home()), 'LANG': 'C.UTF-8', 'DOCKER_CONFIG': str(self.runtime / 'docker-config'),
                    'WA110_OWNER': args.nonce, 'WA110_JAVA_IMAGE': self.contract['images']['java'],
                    'WA110_POSTGRES_IMAGE': self.contract['images']['postgres'],
                    'WA110_API_UID': str(os.getuid()), 'WA110_API_GID': str(os.getgid())}
        self.docker = ['docker', 'compose', '--project-directory', str(base), '-f', str(base / 'compose.yaml'), '-p', self.project]

    def location(self):
        destination = self.contract['destination']
        require(sys.platform == 'linux' and platform.machine() == 'x86_64', 'LINUX_AMD64_REQUIRED')
        require(pwd.getpwuid(os.getuid()).pw_name == destination['user'] and os.getuid() != 0 and os.getgid() != 0
                and socket.gethostname() == destination['hostname'], 'DESTINATION_IDENTITY_MISMATCH')
        require(self.base == Path(destination['rootPrefix']) / self.args.attempt
                and not self.base.is_symlink(), 'DESTINATION_PATH_MISMATCH')
        require(stat.S_IMODE(self.base.stat().st_mode) == 0o700 and self.base.stat().st_uid == os.getuid(), 'PRIVATE_DIRECTORY_OWNERSHIP_INVALID')

    def guard(self, enforce_warning=True):
        require(time.monotonic() < self.deadline, 'VPS_SUPERVISOR_TTL_EXPIRED')
        normal = self.pressure_guard.sample(enforce_warning=False)
        for owned in self.owned_memory.values():
            owned.sample(self.pressure_guard.current_mib)
        return normal

    def bind_owned_memory(self):
        rows, _ = self.resources()
        seen = set()
        for row in rows:
            service = row['Config']['Labels']['com.docker.compose.service']
            seen.add(service)
            if service in self.owned_memory:
                owned = self.owned_memory[service]
                require(row['Id'] == owned.container_id and row['State']['Pid'] == owned.pid, 'OWNED_CONTAINER_RUNTIME_CHANGED')
            else:
                self.owned_memory[service] = OwnedMemoryGuard(row, (1280 if service == 'api' else 512) * 1024**2,
                                                             self.pressure_guard.current_mib)
        require(set(self.owned_memory) <= seen, 'OWNED_CONTAINER_RUNTIME_MISSING')

    def memory_summary(self):
        return {**self.pressure_guard.summary(), 'hostPressurePolicy': 'ADMISSION_AND_CONCLUSION_ONLY',
                'owned': {service: value.summary() for service, value in self.owned_memory.items()}}

    def settle_before_start(self):
        deadline, normal_since = time.monotonic()+60, None
        while True:
            normal = self.guard(enforce_warning=False)
            now = time.monotonic()
            normal_since = (now if normal_since is None or not self.pressure_guard.contiguous else normal_since) if normal else None
            if normal_since is not None and now-normal_since >= 10:
                return
            require(now < deadline, 'VPS_STARTUP_PRESSURE_NOT_SETTLED')
            time.sleep(.5)

    def command(self, args, stdin=None, timeout=45, guarded=True):
        if guarded:
            self.guard()
        child = subprocess.Popen([str(part) for part in args], cwd=self.base, env=self.env,
                                 stdin=subprocess.PIPE if stdin is not None else subprocess.DEVNULL,
                                 stdout=subprocess.PIPE, stderr=subprocess.DEVNULL, text=True)
        deadline = time.monotonic() + timeout
        try:
            while True:
                try:
                    stdout, _ = child.communicate(stdin, timeout=.5)
                    break
                except subprocess.TimeoutExpired:
                    stdin = None
                    if guarded:
                        self.guard()
                    require(time.monotonic() < deadline, 'OWNED_COMMAND_TIMEOUT')
            require(child.returncode == 0 and len(stdout) < 1048576, 'OWNED_COMMAND_FAILED')
            return stdout.strip()
        finally:
            if child.poll() is None:
                child.terminate()
                try:
                    child.wait(timeout=3)
                except subprocess.TimeoutExpired:
                    child.kill()
                    child.wait()

    def owner(self):
        value = load(self.owner_path)
        require(value == {**self.binding, 'packageSha256': self.package_sha, 'project': self.project}, 'OWNER_BINDING_MISMATCH')
        for path in (self.runtime, self.owner_path):
            require(not path.is_symlink() and path.stat().st_uid == os.getuid()
                    and stat.S_IMODE(path.stat().st_mode) in (0o600, 0o700), 'OWNER_PERMISSIONS_INVALID')

    def resources(self):
        ids = self.command(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + self.project], guarded=False).split()
        require(len(ids) <= 2 and all(re.fullmatch('[0-9a-f]{12,64}', item) for item in ids), 'PROJECT_CONTAINER_SET_INVALID')
        rows = json.loads(self.command(['docker', 'inspect', *ids], guarded=False)) if ids else []
        seen = set()
        for row in rows:
            labels = row['Config']['Labels']
            service = labels.get('com.docker.compose.service')
            require(service in ('api', 'postgres') and service not in seen
                    and labels.get('com.docker.compose.project') == self.project
                    and labels.get('wa110.owner') == self.args.nonce
                    and labels.get('wa110.backend') == self.binding['sourceSha']
                    and labels.get('traefik.enable') == 'false'
                    and row['Config']['Image'] == self.contract['images']['java' if service == 'api' else 'postgres']
                    and row['Config']['User'] == ('0' if service == 'postgres' else str(os.getuid()) + ':' + str(os.getgid()))
                    and row['HostConfig']['LogConfig']['Type'] == 'none'
                    and row['HostConfig']['RestartPolicy']['Name'] == 'no', 'CONTAINER_OWNERSHIP_MISMATCH')
            expected_ports = {'18083/tcp': [{'HostIp': '127.0.0.1', 'HostPort': '28083'}]} if service == 'api' else {}
            require((row['HostConfig'].get('PortBindings') or {}) == expected_ports, 'CONTAINER_EXPOSURE_MISMATCH')
            # Configured publish may be ignored on an internal-only network.
            # A dead container naturally loses runtime bindings and must remain
            # eligible for scoped cleanup; any public/foreign binding still fails.
            published = {port: bindings for port, bindings in (row['NetworkSettings'].get('Ports') or {}).items() if bindings}
            require(published == expected_ports if row['State']['Running'] else published in ({}, expected_ports),
                    'CONTAINER_RUNTIME_EXPOSURE_MISMATCH')
            require(not row['HostConfig']['Privileged'] and not row['HostConfig'].get('NetworkMode') in ('host', 'service'), 'CONTAINER_ISOLATION_MISMATCH')
            require(row['HostConfig']['Memory'] == (1280 if service == 'api' else 512) * 1024**2
                    and row['HostConfig']['NanoCpus'] == (2 if service == 'api' else 1) * 10**9, 'CONTAINER_LIMITS_MISMATCH')
            expected_networks = {self.project + '_private'}
            if service == 'api':
                expected_networks.add(self.project + '_edge')
            require(set(row['NetworkSettings']['Networks']) == expected_networks, 'CONTAINER_NETWORK_SCOPE_MISMATCH')
            expected_mounts = ({'/opt/wa110/app.jar': ('inputs/backend.jar', False),
                                '/run/wa110/application.properties': ('runtime/application.properties', False)} if service == 'api'
                               else {'/run/secrets/db-password': ('runtime/db-password', False),
                                     '/run/secrets/pgpass': ('runtime/pgpass', False)})
            binds = {mount['Destination']: mount for mount in row['Mounts'] if mount['Type'] == 'bind'}
            require(set(binds) == set(expected_mounts), 'CONTAINER_BIND_SET_MISMATCH')
            for destination, (source, read_write) in expected_mounts.items():
                require(binds[destination]['Source'] == str(self.base / source)
                        and binds[destination]['RW'] is read_write, 'CONTAINER_BIND_MISMATCH')
            for mount in row['Mounts']:
                require(mount['Type'] in ('bind', 'tmpfs') and (mount['Type'] != 'bind' or Path(mount['Source']).resolve().is_relative_to(self.base)), 'CONTAINER_MOUNT_SCOPE_MISMATCH')
            seen.add(service)
        networks = self.command(['docker', 'network', 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + self.project], guarded=False).split()
        require(len(networks) <= 2, 'PROJECT_NETWORK_SET_INVALID')
        seen_networks = set()
        for network in networks:
            row = json.loads(self.command(['docker', 'network', 'inspect', network], guarded=False))[0]
            kind = row['Labels'].get('com.docker.compose.network')
            require(kind in ('private', 'edge') and kind not in seen_networks
                    and row['Name'] == self.project + '_' + kind
                    and row['Driver'] == 'bridge' and row['Scope'] == 'local'
                    and row['Internal'] is (kind == 'private')
                    and row['Labels'].get('wa110.owner') == self.args.nonce
                    and row['Labels'].get('traefik.enable') == 'false'
                    and row['Labels'].get('com.docker.compose.project') == self.project, 'NETWORK_OWNERSHIP_MISMATCH')
            allowed = {entry['Id'] for entry in rows if kind == 'private' or entry['Config']['Labels']['com.docker.compose.service'] == 'api'}
            require(set(row.get('Containers', {})) <= allowed, 'NETWORK_FOREIGN_MEMBER')
            seen_networks.add(kind)
        require(all(set(row['NetworkSettings']['Networks']) <= {self.project + '_' + kind for kind in seen_networks}
                    for row in rows), 'CONTAINER_NETWORK_NOT_OWNED')
        return rows, networks

    def preflight(self, fresh):
        self.location()
        self.settle_before_start()
        require(shutil.disk_usage(self.base).free >= 4 * 1024**3, 'DISK_CAPACITY_LOW')
        require(self.command(['docker', 'info', '--format', '{{.OSType}}/{{.Architecture}}']) in ('linux/x86_64', 'linux/amd64'), 'DOCKER_PLATFORM_MISMATCH')
        require(self.command(['docker', 'version', '--format', '{{.Server.Version}}']).startswith('29.'), 'DOCKER_ENGINE_VERSION_MISMATCH')
        require(self.command(['docker', 'compose', 'version', '--short']).lstrip('v').startswith('5.'), 'DOCKER_COMPOSE_VERSION_MISMATCH')
        if fresh:
            require(not self.runtime.exists() and not (self.base / 'session').exists(), 'RUNTIME_ALREADY_PRESENT')
            require(not self.command(['docker', 'ps', '-aq', '--filter', 'label=com.docker.compose.project=' + self.project]), 'PROJECT_ALREADY_PRESENT')
            require(not self.command(['docker', 'network', 'ls', '-q', '--filter', 'label=com.docker.compose.project=' + self.project]), 'NETWORK_ALREADY_PRESENT')
            require(all(not open_port('127.0.0.1', port) for port in (28083, 28444)), 'LOOPBACK_PORT_ALREADY_OPEN')
        else:
            self.owner()

    def configure(self):
        self.runtime.mkdir(mode=0o700)
        (self.runtime / 'docker-config').mkdir(mode=0o700)
        (self.base / 'session').mkdir(mode=0o700)
        private_json(self.owner_path, {**self.binding, 'packageSha256': self.package_sha, 'project': self.project})
        values = {key: secrets.token_urlsafe(32) for key in ('database', 'operator', 'jwt', 'hash', 'response')}
        expires = (dt.datetime.now(dt.timezone.utc) + dt.timedelta(seconds=1800)).isoformat().replace('+00:00', 'Z')
        props = {'server.address': '0.0.0.0', 'server.port': '18083', 'spring.profiles.active': 'local',
                 'spring.datasource.url': 'jdbc:postgresql://postgres:5432/vanrides_wa070',
                 'spring.datasource.username': 'vanrides_wa070', 'spring.datasource.password': values['database'],
                 'spring.jpa.hibernate.ddl-auto': 'validate', 'spring.jpa.show-sql': 'false',
                 'spring.jpa.properties.hibernate.format_sql': 'false', 'spring.flyway.baseline-on-migrate': 'false',
                 'vanrides.auth.jwt-secret': values['jwt'], 'vanrides.routing.provider': 'static',
                 'vanrides.maps-platform.enabled': 'false', 'vanrides.push.provider': 'none', 'vanrides.email.provider': 'log',
                 'vanrides.wa.enabled': 'true', 'vanrides.wa.new-starts-enabled': 'true',
                 'vanrides.wa.test-cohort-only': 'true', 'vanrides.wa.environment': 'wa070-local',
                 'vanrides.wa.public-base-url': 'https://localhost:18443', 'vanrides.wa.start-link-base-url': 'https://vanrides.invalid',
                 'vanrides.wa.hash-key': values['hash'], 'vanrides.wa.response-key': values['response'],
                 'vanrides.wa.operator-credential': values['operator'], 'vanrides.wa.operator-expires-at': expires,
                 'logging.level.root': 'OFF', 'server.tomcat.accesslog.enabled': 'false'}
        files = {'db-password': values['database'] + '\n',
                 'pgpass': '*:*:vanrides_wa070:vanrides_wa070:' + values['database'] + '\n',
                 'application.properties': ''.join(key + '=' + value + '\n' for key, value in props.items())}
        for name, body in files.items():
            fd = os.open(self.runtime / name, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
            with os.fdopen(fd, 'w') as target:
                target.write(body)
        private_json(self.runtime / 'operator-private.json', {'environment': 'wa070-local', 'operatorCredential': values['operator']})

    def sql(self, statement):
        self.owner()
        self.resources()
        return self.command([*self.docker, 'exec', '-T', '--user', '0', 'postgres', 'psql', '-XAtq', '-h', '/var/run/postgresql',
                             '-v', 'ON_ERROR_STOP=1', '-U', 'vanrides_wa070', '-d', 'vanrides_wa070'],
                            "BEGIN READ ONLY; SET LOCAL statement_timeout='5s'; " + statement + '; COMMIT;\n')

    def pull_images(self):
        # Limit only this Compose invocation; never change the shared Docker daemon.
        for service in ('postgres', 'api'):
            self.startup_stage = 'PULL_' + service.upper()
            self.command([*self.docker, '--parallel', '1', 'pull', service], timeout=240)

    def start(self):
        self.startup_stage = 'PREFLIGHT'
        self.preflight(fresh=True)
        self.startup_stage = 'CONFIGURE'
        self.configure()
        try:
            self.pull_images()
            self.startup_stage = 'START_POSTGRES'
            self.command([*self.docker, 'up', '-d', 'postgres'], timeout=60)
            self.bind_owned_memory()
            self.startup_stage = 'CHECK_POSTGRES'
            deadline = time.monotonic() + 100
            while True:
                self.guard()
                require(time.monotonic() < deadline, 'POSTGRES_NOT_READY')
                try:
                    if self.sql('SELECT 1') == '1':
                        break
                except Blocked as error:
                    require(str(error) == 'OWNED_COMMAND_FAILED', str(error))
                time.sleep(1)
            uid_text = self.command([*self.docker, 'exec', '-T', '--user', '0', 'postgres', 'cat', '/proc/1/status'])
            require(int(re.search(r'^Uid:\s+(\d+)', uid_text, re.M)[1]) != 0, 'POSTGRES_SERVER_DID_NOT_DROP_UID')
            require(self.command([*self.docker, 'exec', '-T', '--user', '0', 'postgres', 'id', '-u']) == '0', 'PSQL_CLIENT_UID_NOT_ROOT')
            auth = json.loads(self.sql("SELECT jsonb_build_object('localScram',EXISTS(SELECT 1 FROM pg_hba_file_rules WHERE type='local' AND auth_method='scram-sha-256'),'hostScram',EXISTS(SELECT 1 FROM pg_hba_file_rules WHERE type LIKE 'host%' AND auth_method='scram-sha-256'),'insecureRules',(SELECT count(*) FROM pg_hba_file_rules WHERE auth_method IN ('trust','peer')),'encryption',current_setting('password_encryption'))"))
            require(auth == {'localScram': True, 'hostScram': True, 'insecureRules': 0, 'encryption': 'scram-sha-256'}, 'POSTGRES_SCRAM_NOT_PROVEN')
            self.startup_stage = 'START_API'
            self.command([*self.docker, 'up', '-d', 'api'], timeout=60)
            self.bind_owned_memory()
            self.startup_stage = 'CHECK_API'
            opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
            deadline = time.monotonic() + 140
            while True:
                self.guard()
                rows, _ = self.resources()
                require(all(row['State']['Running'] for row in rows) and time.monotonic() < deadline, 'API_NOT_READY')
                try:
                    with opener.open('http://127.0.0.1:28083/actuator/health', timeout=2) as response:
                        if response.status == 200 and json.load(response).get('status') == 'UP':
                            break
                except (OSError, ValueError):
                    pass
                time.sleep(1)
            require(not open_port(self.contract['destination']['host'], 28083)
                    and not open_port(self.contract['destination']['host'], 28444), 'NON_LOOPBACK_ACCESS_NOT_REJECTED')
            require(not list((self.base / 'session').iterdir()), 'SESSION_UNEXPECTED_FILES')
            require(self.sql('SELECT count(*) FROM wa.service_credential') == '0', 'INFRA_DATABASE_NOT_PRISTINE')
            private_json(self.runtime / 'started.json', {'status': 'INFRA_READY_NO_SENSOR', 'packageSha256': self.package_sha,
                         'jarSha256': self.contract['backend']['jarSha256'], 'sourceSha': self.binding['sourceSha'],
                         'loopbackHealth': True, 'publicAddressRejected': True, 'scramVerified': True,
                         'physicalJourney': 'NOT_IMPLEMENTED_IN_THIS_SLICE', 'credentialsIssued': 0})
            return {'status': 'INFRA_READY_NO_SENSOR', 'packageSha256': self.package_sha, 'physicalJourney': 'BLOCKED'}
        except BaseException:
            self.stop()
            raise

    def clean_private_files(self):
        for name in PRIVATE_NAMES:
            path = self.runtime / name
            require(not path.is_symlink(), 'SECRET_CLEANUP_OWNERSHIP_INVALID')
            if path.exists():
                require(path.is_file() and path.stat().st_uid == os.getuid()
                        and stat.S_IMODE(path.stat().st_mode) == 0o600, 'SECRET_CLEANUP_OWNERSHIP_INVALID')
                path.unlink()
        self.assert_no_private_files()

    def assert_no_private_files(self):
        require(all(not (self.runtime / name).exists() and not (self.runtime / name).is_symlink()
                    for name in PRIVATE_NAMES), 'SECRET_FILES_REMAIN')

    def assert_live_health(self):
        self.guard()
        rows, _ = self.resources()
        require(len(rows) == 2 and all(row['State']['Running'] is True
                and row['State'].get('OOMKilled') is False and row.get('RestartCount') == 0
                for row in rows), 'CONTAINER_UNHEALTHY_DURING_HOLD')
        for row in rows:
            service = row['Config']['Labels']['com.docker.compose.service']
            owned = self.owned_memory.get(service)
            require(owned is not None and row['Id'] == owned.container_id and row['State']['Pid'] == owned.pid,
                    'OWNED_CONTAINER_RUNTIME_CHANGED')
        opener = urllib.request.build_opener(urllib.request.ProxyHandler({}))
        try:
            with opener.open('http://127.0.0.1:28083/actuator/health', timeout=2) as response:
                require(response.status == 200 and json.load(response).get('status') == 'UP',
                        'API_HEALTH_LOST_DURING_HOLD')
        except (OSError, ValueError):
            raise Blocked('API_HEALTH_LOST_DURING_HOLD') from None
        require(self.sql('SELECT 1') == '1', 'POSTGRES_HEALTH_LOST_DURING_HOLD')

    def infra_smoke(self):
        try:
            self.start()
            self.startup_stage = 'HEALTH_HOLD'
            self.settle_before_start()
            self.assert_live_health()
            deadline = time.monotonic() + self.args.hold_seconds
            while time.monotonic() < deadline:
                self.guard()
                time.sleep(.5)
            self.startup_stage = 'GLOBAL_CONCLUSION'
            self.settle_before_start()
            self.assert_live_health()
        finally:
            if self.owner_path.exists():
                self.stop()
        value = self.status()
        return {**value, 'profile': 'VPS_INFRASTRUCTURE_SMOKE_ONLY', 'physicalTest': 'NOT_RUN',
                'smoke': 'INFRASTRUCTURE_READY_AND_ROLLBACK_VERIFIED'}

    def stop(self):
        self.location()
        self.owner()
        stopped = self.runtime / 'stopped.json'
        if stopped.exists():
            rows, networks = self.resources()
            require(not rows and not networks and not open_port('127.0.0.1', 28083) and not open_port('127.0.0.1', 28444), 'STOPPED_STATE_CHANGED')
            self.clean_private_files()
            return self.status()
        self.resources()
        # This infrastructure slice never opens the bridge or issues a cohort/credential.
        require(not (self.runtime / 'bridge-auth-private.json').exists(), 'JOURNEY_CLEANUP_MODULE_REQUIRED')
        require(not list((self.base / 'session').iterdir()), 'SESSION_UNEXPECTED_FILES')
        self.command([*self.docker, 'down', '--timeout', '20'], timeout=70, guarded=False)
        rows, networks = self.resources()
        require(not rows and not networks and not open_port('127.0.0.1', 28083) and not open_port('127.0.0.1', 28444), 'OWNED_RESOURCES_NOT_STOPPED')
        receipt = {'schemaVersion': 1, 'profile': 'VPS_INFRASTRUCTURE_SMOKE_ONLY',
                   'attemptId': self.args.attempt, 'sourceSha': self.binding['sourceSha'], 'jarSha256': self.contract['backend']['jarSha256'],
                   'apkSha256': self.contract['apk']['sha256'], 'publicCaSha256': self.contract['apk']['publicCaSha256'],
                   'packageSha256': self.package_sha, 'status': 'STOPPED_NO_PHYSICAL_TEST',
                   'backendStopped': True, 'databaseStopped': True, 'listenerClosed': True,
                   'credentialsIssued': 0, 'rawSensorReceived': False, 'physicalTest': 'NOT_RUN', 'tmpfsRemoved': True,
                   'memory': self.memory_summary()}
        self.clean_private_files()
        receipt_path = self.base / 'receipt.json'
        if receipt_path.exists():
            require(not receipt_path.is_symlink(), 'RECEIPT_ALREADY_PRESENT_DIFFERENT')
            previous = load(receipt_path)
            # A restarted stop has new monitor counters; retain the original telemetry unchanged.
            require(set(previous) == set(receipt) and isinstance(previous['memory'], dict)
                    and {key: value for key, value in previous.items() if key != 'memory'}
                    == {key: value for key, value in receipt.items() if key != 'memory'}, 'RECEIPT_ALREADY_PRESENT_DIFFERENT')
        else:
            private_json(receipt_path, receipt)
        value = {'status': 'STOPPED', 'backendStopped': True, 'databaseStopped': True, 'cleanupConfirmed': True,
                 'listenerClosed': True, 'receiptSha256': hash_file(self.base / 'receipt.json')}
        private_json(stopped, value)
        return value

    def status(self):
        self.location()
        self.owner()
        self.assert_no_private_files()
        stopped = self.runtime / 'stopped.json'
        require(stopped.exists(), 'NOT_STOPPED')
        rows, networks = self.resources()
        require(not rows and not networks and not open_port('127.0.0.1', 28083) and not open_port('127.0.0.1', 28444), 'STOPPED_STATE_CHANGED')
        value = load(stopped)
        require(value['receiptSha256'] == hash_file(self.base / 'receipt.json'), 'RECEIPT_DIGEST_MISMATCH')
        return value

    def remove_private(self):
        self.status()
        allowed = {*FIXED_FILES, 'SHA256SUMS.json', 'receipt.json', 'runtime/owner.json', 'runtime/started.json', 'runtime/stopped.json'}
        allowed_directories = {'inputs', 'runtime', 'runtime/docker-config', 'session'}
        for path in self.base.rglob('*'):
            require(not path.is_symlink() and path.stat().st_uid == os.getuid(), 'REMOVE_PRIVATE_OWNERSHIP_INVALID')
            if path.is_file():
                require(str(path.relative_to(self.base)) in allowed, 'REMOVE_PRIVATE_UNKNOWN_FILE')
            else:
                require(path.is_dir() and str(path.relative_to(self.base)) in allowed_directories, 'REMOVE_PRIVATE_UNKNOWN_DIRECTORY_OR_TYPE')
        require(not list((self.base / 'session').iterdir()) and not list((self.runtime / 'docker-config').iterdir()), 'REMOVE_PRIVATE_UNKNOWN_STATE')
        shutil.rmtree(self.base)
        return {'status': 'PRIVATE_DIRECTORY_REMOVED', 'packageSha256': self.package_sha}


def main(argv=None):
    parser = argparse.ArgumentParser()
    parser.add_argument('command', choices=('dry-run', 'preflight', 'infra-smoke', 'stop', 'status', 'bridge-bootstrap', 'remove-private'))
    parser.add_argument('--attempt', required=True)
    parser.add_argument('--nonce', required=True)
    parser.add_argument('--hold-seconds', type=int, default=5)
    args = parser.parse_args(argv)
    runtime = None
    try:
        runtime = Runtime(args)
        require(1 <= args.hold_seconds <= 30, 'HOLD_SECONDS_OUT_OF_RANGE')
        def interrupted(_number, _frame):
            raise Blocked('VPS_SUPERVISOR_SIGNAL_INTERRUPTED')
        for number in (signal.SIGTERM, signal.SIGHUP, signal.SIGINT):
            signal.signal(number, interrupted)
        if args.command == 'dry-run':
            result = {'status': 'DRY_RUN_ONLY', 'profile': 'VPS_INFRASTRUCTURE_SMOKE_ONLY', 'packageSha256': runtime.package_sha,
                      'remoteDirectory': str(Path(runtime.contract['destination']['rootPrefix']) / args.attempt),
                      'commands': ['preflight', 'infra-smoke', 'stop', 'status', 'remove-private'], 'physicalJourney': 'BLOCKED_MODULE_NOT_IMPLEMENTED'}
        elif args.command == 'bridge-bootstrap':
            raise Blocked('PHYSICAL_JOURNEY_MODULE_NOT_IMPLEMENTED')
        elif args.command == 'preflight':
            runtime.preflight(fresh=True)
            result = {'status': 'PREFLIGHT_ONLY', 'packageSha256': runtime.package_sha}
        else:
            result = getattr(runtime, args.command.replace('-', '_'))()
        result['memory'] = runtime.memory_summary()
        result['startupStage'] = runtime.startup_stage
        print(json.dumps(envelope(runtime.binding, result), separators=(',', ':')))
        return 0
    except (Blocked, OSError, ValueError, KeyError, TypeError, subprocess.SubprocessError):
        error = sys.exc_info()[1]
        code = str(error) if isinstance(error, Blocked) else 'LOCAL_VALIDATION_ERROR'
        result = {'status': 'BLOCKED', 'code': code}
        if runtime is not None:
            result['memory'] = runtime.memory_summary()
            result['startupStage'] = runtime.startup_stage
        print(json.dumps(envelope(runtime.binding, result) if runtime else {'result': result}, separators=(',', ':')))
        return 2


if __name__ == '__main__':
    raise SystemExit(main())
