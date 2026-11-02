"""Private SQLite backup/restore helper, run via stdin in the receiver image.

The Online Backup API includes committed WAL data. Restore always writes a NEW
database and revokes all imported identities before that database can be served.
No credential JSON is copied. Output contains only counts and schema fingerprint.
"""
from contextlib import closing
import hashlib
import json
import os
import sqlite3
import stat
import time

from receiver.cli import SafeParser, private_path

TABLES = {"credentials", "intents", "sessions", "batches", "receipts", "sink"}


def existing(path):
    path = private_path(path)
    info = path.stat()
    if not stat.S_ISREG(info.st_mode) or stat.S_IMODE(info.st_mode) != 0o600 or info.st_uid != os.getuid():
        raise ValueError("PRIVATE_DATABASE_REQUIRED")
    return path


def connect_readonly(path):
    db = sqlite3.connect(path.as_uri() + "?mode=ro", uri=True, timeout=5)
    db.execute("PRAGMA query_only=ON")
    db.execute("PRAGMA trusted_schema=OFF")
    return db


def summary(db):
    tables = {row[0] for row in db.execute("SELECT name FROM sqlite_master WHERE type='table'")}
    if tables != TABLES or db.execute("PRAGMA quick_check").fetchall() != [("ok",)]:
        raise ValueError("INVALID_DATABASE")
    if db.execute("PRAGMA foreign_key_check").fetchone() is not None:
        raise ValueError("INVALID_FOREIGN_KEYS")
    schema = db.execute("SELECT type,name,tbl_name,sql FROM sqlite_master ORDER BY type,name").fetchall()
    return {"schemaSha256": hashlib.sha256(json.dumps(schema, separators=(",", ":")).encode()).hexdigest(),
            "credentials": db.execute("SELECT COUNT(*) FROM credentials").fetchone()[0],
            "unrevokedCredentials": db.execute("SELECT COUNT(*) FROM credentials WHERE revoked_at IS NULL").fetchone()[0],
            "sessions": db.execute("SELECT COUNT(*) FROM sessions").fetchone()[0],
            "runningSessions": db.execute("SELECT COUNT(*) FROM sessions WHERE state='RUNNING'").fetchone()[0],
            "batches": db.execute("SELECT COUNT(*) FROM batches").fetchone()[0],
            "receipts": db.execute("SELECT COUNT(*) FROM receipts").fetchone()[0],
            "syntheticEvents": db.execute("SELECT COUNT(*) FROM sink").fetchone()[0]}


def durable(path):
    with path.open("rb") as source:
        os.fsync(source.fileno())
    directory = os.open(str(path.parent), os.O_RDONLY)
    try:
        os.fsync(directory)
    finally:
        os.close(directory)


def copy_database(source, output, revoke=False):
    source = existing(source)
    output = private_path(output)
    # Reserve before copying. Never overwrite a database, backup or symlink.
    fd = os.open(str(output), os.O_CREAT | os.O_EXCL | os.O_WRONLY | getattr(os, "O_NOFOLLOW", 0), 0o600)
    os.close(fd)
    deadline = time.monotonic() + 30

    def progress(status, remaining, total):
        if time.monotonic() >= deadline:
            raise TimeoutError("BACKUP_TIMEOUT")

    try:
        with closing(connect_readonly(source)) as original, closing(sqlite3.connect(str(output), timeout=5)) as restored:
            # Validate source before operating on it; do not serialize its rows.
            summary(original)
            original.backup(restored, pages=64, progress=progress, sleep=0.05)
            restored.execute("PRAGMA journal_mode=DELETE")
            restored.execute("PRAGMA synchronous=FULL")
            if revoke:
                now = time.time_ns() // 1000000
                restored.execute("BEGIN IMMEDIATE")
                restored.execute("UPDATE credentials SET revoked_at=COALESCE(revoked_at,?),window_ends_at=MIN(window_ends_at,?)", (now, now))
                # Preserve explicit STOPPED/EXPIRED rows and their stop ACK times.
                restored.execute("UPDATE sessions SET state='REVOKED',lease_expires_at=MIN(lease_expires_at,?) WHERE state='RUNNING'", (now,))
                restored.commit()
            result = summary(restored)
            if revoke and (result["unrevokedCredentials"] or result["runningSessions"]):
                raise ValueError("RESTORE_NOT_REVOKED")
        durable(output)
        return result
    except BaseException:
        # Only remove the newly reserved output; the source is never mutated.
        output.unlink(missing_ok=True)
        raise


def main():
    parser = SafeParser(add_help=False)
    commands = parser.add_subparsers(dest="command", required=True, parser_class=SafeParser)
    for command in ("backup", "restore"):
        sub = commands.add_parser(command)
        sub.add_argument("--source", required=True)
        sub.add_argument("--out", required=True)
    verify = commands.add_parser("verify")
    verify.add_argument("--source", required=True)
    verify.add_argument("--expected-receipts", type=int)
    verify.add_argument("--expected-batches", type=int)
    verify.add_argument("--expect-revoked", action="store_true")
    args = parser.parse_args()
    if args.command == "verify":
        with closing(connect_readonly(existing(args.source))) as db:
            result = summary(db)
        if args.expected_receipts is not None and result["receipts"] != args.expected_receipts:
            raise ValueError("RECEIPT_COUNT")
        if args.expected_batches is not None and result["batches"] != args.expected_batches:
            raise ValueError("BATCH_COUNT")
        if args.expect_revoked and (result["unrevokedCredentials"] or result["runningSessions"]):
            raise ValueError("RESTORE_NOT_REVOKED")
    else:
        result = copy_database(args.source, args.out, revoke=args.command == "restore")
    print(json.dumps({"stateOperation": args.command.upper(), "result": "PASS", **result}, separators=(",", ":")))


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print('{"stateOperation":"FAIL"}')
        raise SystemExit(1)
