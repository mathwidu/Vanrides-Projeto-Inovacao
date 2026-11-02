"""Private local administration, deliberately absent from HTTP routes."""
import argparse
import json
import os
from pathlib import Path
import stat
import sys
from wsgiref.simple_server import WSGIRequestHandler, make_server

from .service import Clock, Receiver
from .route_config import RouteConfigError, load_route
from .store import MAX_CREDENTIAL_MS, Store
from .validation import ProtocolError
from .web import Application


class SafeParser(argparse.ArgumentParser):
    def error(self, message):
        # argparse's normal error can echo an accidental secret in argv.
        raise ValueError("INVALID_ARGUMENTS")


def private_path(path):
    path = Path(path).expanduser().absolute()
    if path.is_symlink():
        raise ValueError("PRIVATE_PATH_REQUIRED")
    # Canonicalize OS aliases such as macOS /var -> /private/var before checking
    # Git ancestry; the output leaf itself may never be a symlink.
    path = path.resolve()
    # Secrets must live outside any Git checkout.
    for ancestor in (path,) + tuple(path.parents):
        if ancestor.is_symlink() or (ancestor / ".git").exists():
            raise ValueError("PRIVATE_PATH_REQUIRED")
    if not path.parent.exists():
        path.parent.mkdir(mode=0o700, parents=True)
    if path.parent.stat().st_mode & 0o077:
        raise ValueError("PRIVATE_PATH_REQUIRED")
    return path


def read_private(path):
    path = private_path(path)
    fd = os.open(str(path), os.O_RDONLY | getattr(os, "O_NOFOLLOW", 0))
    with os.fdopen(fd, "rb") as source:
        info = os.fstat(source.fileno())
        if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.getuid():
            raise ValueError("PRIVATE_FILE_REQUIRED")
        raw = source.read(4097)
    if len(raw) > 4096:
        raise ValueError("INVALID_PRIVATE_FILE")
    value = json.loads(raw.decode("utf-8"))
    if not isinstance(value, dict):
        raise ValueError("INVALID_PRIVATE_FILE")
    return value


def write_private(path, value):
    path = private_path(path)
    flags = os.O_WRONLY | os.O_CREAT | os.O_EXCL | getattr(os, "O_NOFOLLOW", 0)
    fd = os.open(str(path), flags, 0o600)
    try:
        with os.fdopen(fd, "w", encoding="utf-8") as target:
            json.dump(value, target, separators=(",", ":"), allow_nan=False)
            target.write("\n")
            target.flush()
            os.fsync(target.fileno())
        directory = os.open(str(path.parent), os.O_RDONLY)
        try:
            os.fsync(directory)
        finally:
            os.close(directory)
    except BaseException:
        path.unlink(missing_ok=True)
        raise


class QuietHandler(WSGIRequestHandler):
    def setup(self):
        super().setup()
        self.connection.settimeout(8)

    def log_message(self, format, *args):
        pass

    def log_error(self, format, *args):
        pass


def main(argv=None):
    parser = SafeParser(description="GPS synthetic lab; credentials only in private files.")
    parser.add_argument("--db", help="SQLite path in a private directory outside Git; required except route-check")
    commands = parser.add_subparsers(dest="command", required=True, parser_class=SafeParser)
    route_check = commands.add_parser("route-check", help="Validate only an explicit private route file")
    route_check.add_argument("--file", required=True)
    provision = commands.add_parser("provision")
    provision.add_argument("--alias", required=True, help="Synthetic ASCII alias only")
    provision.add_argument("--hours", type=int, choices=range(1, 5), default=4)
    provision.add_argument("--out", required=True, help="New private 0600 credential file")
    intent = commands.add_parser("intent")
    intent.add_argument("--credential-file", required=True)
    intent.add_argument("--out", required=True, help="New private intent file")
    for command in ("revoke", "finish"):
        sub = commands.add_parser(command)
        sub.add_argument("--credential-file", required=True)
    commands.add_parser("status")
    commands.add_parser("serve-dev", help="Loopback-only stdlib development server")
    try:
        args = parser.parse_args(argv)
        if args.command == "route-check":
            if args.db is not None:
                raise ValueError("INVALID_ARGUMENTS")
            route = load_route(args.file)
            print(json.dumps(dict(result="ROUTE_VALID", **route.summary()), separators=(",", ":")))
            return 0
        if args.db is None:
            raise ValueError("DATABASE_REQUIRED")
        store = Store(private_path(args.db))
        now = Clock().now_ms()
        if args.command == "provision":
            # Reserve output path before creating a credential. Never overwrite a
            # recoverable credential file. Failure revokes the unusable credential.
            output = private_path(args.out)
            if output.exists():
                raise ValueError("OUTPUT_EXISTS")
            bundle = store.provision(now, args.alias, args.hours * 3600000)
            try:
                write_private(output, bundle)
            except BaseException:
                store.end(now, bundle, revoke=True)
                raise
            result = {"result": "PROVISIONED"}
        elif args.command == "intent":
            bundle = read_private(args.credential_file)
            output = private_path(args.out)
            if output.exists():
                raise ValueError("OUTPUT_EXISTS")
            write_private(output, store.intent(now, bundle))
            result = {"result": "INTENT_CREATED"}
        elif args.command in ("revoke", "finish"):
            store.end(now, read_private(args.credential_file), revoke=args.command == "revoke")
            result = {"result": "REVOKED" if args.command == "revoke" else "FINISHED"}
        elif args.command == "status":
            # Status is aggregate and never serializes rows, aliases, IDs or token.
            result = store.summary()
        else:
            receiver = Receiver(store, route=load_route(store.path.parent / "route-v1.json", missing_ok=True))
            try:
                with make_server("127.0.0.1", 18765, Application(receiver), handler_class=QuietHandler) as httpd:
                    httpd.serve_forever()
            except KeyboardInterrupt:
                pass
            finally:
                receiver.close()
            return 0
        print(json.dumps(result, separators=(",", ":")))
        return 0
    except RouteConfigError:
        print('{"code":"INVALID_PRIVATE_ROUTE"}', file=sys.stderr)
        return 1
    except (ValueError, OSError, ProtocolError):
        # Neither filesystem exception nor user input is echoed.
        print('{"code":"PRIVATE_OPERATION_FAILED"}', file=sys.stderr)
        return 1
    except Exception:
        print('{"code":"UNAVAILABLE"}', file=sys.stderr)
        return 1
