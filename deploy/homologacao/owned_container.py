"""Bounded ownership/cleanup for one temporary container, including lost create responses."""
import json
import re
import signal
import threading
import uuid
from contextlib import contextmanager

from plan import Blocked, require

OWNER = 'dev.vanrides.owner'
OPERATION = 'dev.vanrides.operation'
ENVIRONMENT = 'vanrides-team-hml'


class Terminated(BaseException):
    """A SIGTERM is cancellation, never successful verification."""


@contextmanager
def masked_cleanup():
    # A second cancellation must not interrupt the ownership check/removal.
    previous = signal.pthread_sigmask(signal.SIG_BLOCK, {signal.SIGTERM, signal.SIGINT})
    try:
        yield
    finally:
        signal.pthread_sigmask(signal.SIG_SETMASK, previous)


class OwnedContainer:
    def __init__(self, run, operation, image):
        require(operation in ('image-check', 'restore'), 'HML_OWNER_OPERATION_INVALID')
        self.run = run
        self.operation = operation
        self.image = image
        self.nonce = uuid.uuid4().hex
        self.name = 'vanrides-hml-api-' + operation + '-' + self.nonce
        self.attempted = False
        self.container = None
        self.old_handler = None

    def __enter__(self):
        require(threading.current_thread() is threading.main_thread(), 'HML_SIGNAL_GUARD_REQUIRES_MAIN_THREAD')
        self.old_handler = signal.getsignal(signal.SIGTERM)
        def cancelled(signum, frame):
            raise Terminated()
        signal.signal(signal.SIGTERM, cancelled)
        return self

    def __exit__(self, kind, value, traceback):
        try:
            with masked_cleanup():
                self.cleanup()
        finally:
            signal.signal(signal.SIGTERM, self.old_handler)
        return False

    def ids(self, filters):
        args = ['ps', '-a', '--no-trunc']
        for value in filters:
            args += ['--filter', value]
        output = self.run([*args, '--format', '{{.ID}}'])
        try:
            ids = output.decode('ascii').splitlines()
        except UnicodeError:
            raise Blocked('HML_OWNER_QUERY_INVALID') from None
        require(len(ids) <= 1 and all(re.fullmatch('[a-f0-9]{64}', item) for item in ids), 'HML_OWNER_QUERY_INVALID')
        return ids

    def inspect_owned(self, container):
        template = '{"id":{{json .Id}},"name":{{json .Name}},"labels":{{json .Config.Labels}},"image":{{json .Config.Image}}}'
        try:
            value = json.loads(self.run(['inspect', '--format', template, container]))
            labels = value['labels']
            valid = (value['id'] == container and value['name'] == '/' + self.name and value['image'] == self.image
                     and labels.get(OWNER) == self.nonce and labels.get(OPERATION) == self.operation
                     and labels.get('dev.vanrides.environment') == ENVIRONMENT)
        except (ValueError, KeyError, TypeError, AttributeError):
            raise Blocked('HML_TEMP_CONTAINER_NOT_OWNED') from None
        require(valid, 'HML_TEMP_CONTAINER_NOT_OWNED')

    def create(self, args):
        # Check the random name, then mark attempted BEFORE sending a mutation.
        require(not self.attempted and not self.ids(['name=^/' + self.name + '$']), 'HML_TEMP_NAME_COLLISION')
        self.attempted = True
        raw = self.run(['create', '--name', self.name,
                        '--label', OWNER + '=' + self.nonce,
                        '--label', OPERATION + '=' + self.operation,
                        '--label', 'dev.vanrides.environment=' + ENVIRONMENT, *args, self.image])
        try:
            container = raw.decode('ascii').strip()
        except UnicodeError:
            raise Blocked('HML_CONTAINER_ID_INVALID') from None
        require(re.fullmatch('[a-f0-9]{64}', container), 'HML_CONTAINER_ID_INVALID')
        self.inspect_owned(container)
        self.container = container
        return container

    def cleanup(self):
        if not self.attempted:
            return
        # Do not depend on the create response. The nonce was present on the request.
        filters = ['label=' + OWNER + '=' + self.nonce, 'label=' + OPERATION + '=' + self.operation]
        ids = self.ids(filters)
        # Inspect ALL candidates before any removal. A forged label/name never gets removed.
        for container in ids:
            self.inspect_owned(container)
        for container in ids:
            self.run(['rm', '-f', '-v', container])
        require(not self.ids(filters), 'HML_OWNED_CONTAINER_REMAINS')
