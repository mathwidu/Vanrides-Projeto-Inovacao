#!/usr/bin/env python3
"""Kernel-backed keeper for the shared Maven evidence lock.

The shell wrapper owns the lifecycle. This process owns only the advisory
flock and exits after an authenticated RELEASE command or control-channel EOF.
"""

from __future__ import annotations

import argparse
import errno
import fcntl
import hashlib
import os
import re
import stat
import subprocess
import sys
import time
from pathlib import Path


LOCK_NAME = "maven-evidence.lock"
OWNER_NAME = "owner"
SUITE_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
RUN_ID_PATTERN = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$")
SHA_PATTERN = re.compile(r"^(?:[0-9a-f]{40}|[0-9a-f]{64})$")
TOKEN_PATTERN = re.compile(r"^[0-9a-f]{32}$")
OWNER_KEYS = (
    "schema_version",
    "pid",
    "process_start_id",
    "owner_token",
    "suite",
    "run_id",
    "started_epoch",
    "git_sha",
)


class KeeperError(Exception):
    def __init__(self, code: str) -> None:
        super().__init__(code)
        self.code = code


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(add_help=False)
    parser.add_argument("--root", required=True)
    parser.add_argument("--control", required=True)
    parser.add_argument("--status", required=True)
    parser.add_argument("--owner-pid", required=True, type=int)
    parser.add_argument("--owner-token", required=True)
    parser.add_argument("--suite", required=True)
    parser.add_argument("--run-id", required=True)
    parser.add_argument("--git-sha", required=True)
    return parser.parse_args()


def validate_value(value: str, pattern: re.Pattern[str], code: str) -> None:
    if not pattern.fullmatch(value):
        raise KeeperError(code)


def validate_private_root(raw_root: str) -> Path:
    if not raw_root or any(ord(character) < 32 for character in raw_root):
        raise KeeperError("unsafe-root")
    root = Path(raw_root)
    if not root.is_absolute() or root == Path("/") or ".." in root.parts:
        raise KeeperError("unsafe-root")
    try:
        root_stat = root.lstat()
    except OSError as error:
        raise KeeperError("unsafe-root") from error
    if (
        stat.S_ISLNK(root_stat.st_mode)
        or not stat.S_ISDIR(root_stat.st_mode)
        or root_stat.st_uid != os.getuid()
        or stat.S_IMODE(root_stat.st_mode) != 0o700
    ):
        raise KeeperError("unsafe-root")
    return root


def validate_fifo(path: Path) -> None:
    try:
        fifo_stat = path.lstat()
    except OSError as error:
        raise KeeperError("unsafe-control") from error
    if (
        stat.S_ISLNK(fifo_stat.st_mode)
        or not stat.S_ISFIFO(fifo_stat.st_mode)
        or fifo_stat.st_uid != os.getuid()
        or stat.S_IMODE(fifo_stat.st_mode) != 0o600
    ):
        raise KeeperError("unsafe-control")


def open_persistent_lock(path: Path) -> int:
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    flags = os.O_RDWR | no_follow
    created = False
    try:
        descriptor = os.open(path, flags | os.O_CREAT | os.O_EXCL, 0o600)
        created = True
    except FileExistsError:
        try:
            descriptor = os.open(path, flags)
        except OSError as error:
            raise KeeperError("unsafe-lock-file") from error
    except OSError as error:
        raise KeeperError("unsafe-lock-file") from error

    try:
        if created:
            os.fchmod(descriptor, 0o600)
        lock_stat = os.fstat(descriptor)
        if (
            not stat.S_ISREG(lock_stat.st_mode)
            or lock_stat.st_uid != os.getuid()
            or stat.S_IMODE(lock_stat.st_mode) != 0o600
        ):
            raise KeeperError("unsafe-lock-file")
        current = path.lstat()
        if (
            stat.S_ISLNK(current.st_mode)
            or not stat.S_ISREG(current.st_mode)
            or (current.st_dev, current.st_ino)
            != (lock_stat.st_dev, lock_stat.st_ino)
        ):
            raise KeeperError("unsafe-lock-file")
        return descriptor
    except BaseException:
        os.close(descriptor)
        raise


def validate_existing_owner_path(path: Path) -> None:
    try:
        owner_stat = path.lstat()
    except FileNotFoundError:
        return
    except OSError as error:
        raise KeeperError("unsafe-owner-file") from error
    if (
        stat.S_ISLNK(owner_stat.st_mode)
        or not stat.S_ISREG(owner_stat.st_mode)
        or owner_stat.st_uid != os.getuid()
        or stat.S_IMODE(owner_stat.st_mode) != 0o600
    ):
        raise KeeperError("unsafe-owner-file")


def process_start_identity(pid: int) -> str:
    proc_stat = Path(f"/proc/{pid}/stat")
    try:
        raw_stat = proc_stat.read_text(encoding="ascii")
    except OSError:
        raw_stat = ""
    if raw_stat:
        closing_parenthesis = raw_stat.rfind(")")
        fields_after_command = raw_stat[closing_parenthesis + 2 :].split()
        if closing_parenthesis > 0 and len(fields_after_command) > 19:
            identity = f"linux-proc-startticks:{fields_after_command[19]}"
            return hashlib.sha256(identity.encode("ascii")).hexdigest()

    try:
        completed = subprocess.run(
            ["ps", "-o", "lstart=", "-p", str(pid)],
            check=False,
            capture_output=True,
            text=True,
            timeout=2,
            env={"PATH": "/usr/bin:/bin", "LC_ALL": "C"},
        )
    except (OSError, subprocess.SubprocessError) as error:
        raise KeeperError("owner-process-unverifiable") from error
    normalized = " ".join(completed.stdout.split())
    if completed.returncode != 0 or not normalized:
        raise KeeperError("owner-process-unverifiable")
    identity = f"ps-lstart:{normalized}"
    return hashlib.sha256(identity.encode("ascii")).hexdigest()


def publish_owner(path: Path, values: dict[str, str]) -> None:
    validate_existing_owner_path(path)
    temporary = path.parent / f".owner.{os.getpid()}.{os.urandom(8).hex()}.tmp"
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    descriptor = -1
    try:
        descriptor = os.open(
            temporary,
            os.O_WRONLY | os.O_CREAT | os.O_EXCL | no_follow,
            0o600,
        )
        os.fchmod(descriptor, 0o600)
        payload = "".join(f"{key}={values[key]}\n" for key in OWNER_KEYS).encode(
            "ascii"
        )
        with os.fdopen(descriptor, "wb", closefd=True) as owner_stream:
            descriptor = -1
            owner_stream.write(payload)
            owner_stream.flush()
            os.fsync(owner_stream.fileno())
        os.replace(temporary, path)
        directory_descriptor = os.open(path.parent, os.O_RDONLY)
        try:
            os.fsync(directory_descriptor)
        finally:
            os.close(directory_descriptor)
    except BaseException:
        if descriptor >= 0:
            os.close(descriptor)
        try:
            temporary.unlink()
        except FileNotFoundError:
            pass
        raise


def read_owner_diagnostic(path: Path) -> str:
    try:
        owner_stat = path.lstat()
        if (
            stat.S_ISLNK(owner_stat.st_mode)
            or not stat.S_ISREG(owner_stat.st_mode)
            or owner_stat.st_uid != os.getuid()
            or stat.S_IMODE(owner_stat.st_mode) != 0o600
            or owner_stat.st_size > 1024
        ):
            return "owner=unknown"
        payload = path.read_text(encoding="ascii")
    except (OSError, UnicodeError):
        return "owner=unknown"
    if any(ord(character) < 32 and character != "\n" for character in payload):
        return "owner=unknown"
    lines = payload.splitlines()
    if len(lines) != len(OWNER_KEYS):
        return "owner=unknown"
    values: dict[str, str] = {}
    for expected_key, line in zip(OWNER_KEYS, lines):
        key, separator, value = line.partition("=")
        if not separator or key != expected_key or key in values:
            return "owner=unknown"
        values[key] = value
    validators = (
        values["schema_version"] == "1",
        re.fullmatch(r"[1-9][0-9]*", values["pid"]) is not None,
        re.fullmatch(r"[0-9a-f]{64}", values["process_start_id"]) is not None,
        TOKEN_PATTERN.fullmatch(values["owner_token"]) is not None,
        SUITE_PATTERN.fullmatch(values["suite"]) is not None,
        RUN_ID_PATTERN.fullmatch(values["run_id"]) is not None,
        re.fullmatch(r"[1-9][0-9]*", values["started_epoch"]) is not None,
        SHA_PATTERN.fullmatch(values["git_sha"]) is not None,
    )
    if not all(validators):
        return "owner=unknown"
    return (
        f"suite={values['suite']} run_id={values['run_id']} pid={values['pid']}"
    )


def write_status(path: Path, message: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9 =._:-]+", message):
        message = "ERROR unsafe-status"
    no_follow = getattr(os, "O_NOFOLLOW", 0)
    descriptor = os.open(
        path,
        os.O_WRONLY | os.O_CREAT | os.O_EXCL | no_follow,
        0o600,
    )
    os.fchmod(descriptor, 0o600)
    with os.fdopen(descriptor, "w", encoding="ascii", closefd=True) as stream:
        stream.write(message + "\n")
        stream.flush()
        os.fsync(stream.fileno())


def run_keeper(arguments: argparse.Namespace) -> int:
    validate_value(arguments.suite, SUITE_PATTERN, "invalid-suite")
    validate_value(arguments.run_id, RUN_ID_PATTERN, "invalid-run-id")
    validate_value(arguments.git_sha, SHA_PATTERN, "invalid-git-sha")
    validate_value(arguments.owner_token, TOKEN_PATTERN, "invalid-owner-token")
    if arguments.owner_pid <= 0:
        raise KeeperError("invalid-owner-pid")

    root = validate_private_root(arguments.root)
    control = Path(arguments.control)
    status_path = Path(arguments.status)
    if control.parent != root or status_path.parent != root:
        raise KeeperError("unsafe-channel-path")
    validate_fifo(control)

    with control.open("r", encoding="ascii", errors="strict") as control_stream:
        lock_descriptor = open_persistent_lock(root / LOCK_NAME)
        try:
            try:
                fcntl.flock(lock_descriptor, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError as error:
                if error.errno not in (errno.EACCES, errno.EAGAIN):
                    raise KeeperError("lock-operation-failed") from error
                diagnostic = read_owner_diagnostic(root / OWNER_NAME)
                write_status(status_path, f"BLOCKED {diagnostic}")
                return 1

            process_start_id = process_start_identity(arguments.owner_pid)
            publish_owner(
                root / OWNER_NAME,
                {
                    "schema_version": "1",
                    "pid": str(arguments.owner_pid),
                    "process_start_id": process_start_id,
                    "owner_token": arguments.owner_token,
                    "suite": arguments.suite,
                    "run_id": arguments.run_id,
                    "started_epoch": str(int(time.time())),
                    "git_sha": arguments.git_sha,
                },
            )
            write_status(status_path, f"ACQUIRED {arguments.owner_token}")
            expected_release = f"RELEASE {arguments.owner_token}"
            for line in control_stream:
                if line.rstrip("\n") == expected_release:
                    # The authenticated request authorizes a normal release,
                    # but the advisory lock remains held until every inherited
                    # writer closes the channel. This prevents a wrapper that
                    # exits before a Maven descendant from unlocking early.
                    continue
            return 0
        finally:
            os.close(lock_descriptor)


def main() -> int:
    status_path: Path | None = None
    try:
        arguments = parse_args()
        status_path = Path(arguments.status)
        return run_keeper(arguments)
    except KeeperError as error:
        if status_path is not None:
            try:
                write_status(status_path, f"ERROR {error.code}")
            except OSError:
                pass
        return 2
    except (OSError, UnicodeError, ValueError):
        if status_path is not None:
            try:
                write_status(status_path, "ERROR keeper-failure")
            except OSError:
                pass
        return 2


if __name__ == "__main__":
    sys.exit(main())
