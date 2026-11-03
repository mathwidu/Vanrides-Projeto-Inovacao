"""Private SQLite metadata/receipts. This schema never contains raw GPS."""
import hashlib
import os
from pathlib import Path
import secrets
import sqlite3
from contextlib import closing, contextmanager

from .validation import opaque

MAX_CREDENTIAL_MS = 4 * 60 * 60 * 1000
INTENT_MS = 300000

SCHEMA = """
CREATE TABLE IF NOT EXISTS credentials (
 id TEXT PRIMARY KEY, run_id TEXT NOT NULL, alias TEXT NOT NULL,
 token_hash TEXT NOT NULL UNIQUE, created_at INTEGER NOT NULL,
 expires_at INTEGER NOT NULL, window_ends_at INTEGER NOT NULL,
 revoked_at INTEGER, finished_at INTEGER
);
CREATE TABLE IF NOT EXISTS intents (
 id TEXT PRIMARY KEY, credential_id TEXT NOT NULL REFERENCES credentials(id),
 expires_at INTEGER NOT NULL, session_id TEXT
);
CREATE TABLE IF NOT EXISTS sessions (
 id TEXT PRIMARY KEY, credential_id TEXT NOT NULL REFERENCES credentials(id),
 generation INTEGER NOT NULL, state TEXT NOT NULL,
 lease_expires_at INTEGER NOT NULL, created_at INTEGER NOT NULL,
 last_captured_at INTEGER NOT NULL DEFAULT -1,
 stop_requested_at INTEGER, stopped_at INTEGER, stop_ack_at INTEGER,
 UNIQUE(credential_id,generation)
);
CREATE TABLE IF NOT EXISTS batches (
 session_id TEXT NOT NULL REFERENCES sessions(id), generation INTEGER NOT NULL,
 batch_id TEXT NOT NULL, digest TEXT NOT NULL, received_at INTEGER NOT NULL,
 PRIMARY KEY(session_id,generation,batch_id)
);
CREATE TABLE IF NOT EXISTS receipts (
 session_id TEXT NOT NULL REFERENCES sessions(id), generation INTEGER NOT NULL,
 sequence INTEGER NOT NULL, digest TEXT NOT NULL, received_at INTEGER NOT NULL,
 PRIMARY KEY(session_id,generation,sequence)
);
CREATE TABLE IF NOT EXISTS sink (
 id TEXT PRIMARY KEY, occurred_at INTEGER NOT NULL, type TEXT NOT NULL
);
"""


def token_hash(token):
    return hashlib.sha256(token.encode("ascii")).hexdigest()


def new_id(prefix):
    return prefix + "_" + secrets.token_urlsafe(18)


class Store:
    def __init__(self, path):
        self.path = Path(path).expanduser().absolute()
        self.path.parent.mkdir(mode=0o700, parents=True, exist_ok=True)
        # Reject symlinks/foreign permissive paths instead of silently chmodding
        # another owner's directory. A dedicated private state directory is required.
        if self.path.is_symlink() or self.path.parent.is_symlink():
            raise ValueError("PRIVATE_STATE_REQUIRED")
        if self.path.parent.stat().st_mode & 0o077:
            raise ValueError("PRIVATE_STATE_REQUIRED")
        if self.path.exists() and self.path.stat().st_mode & 0o077:
            raise ValueError("PRIVATE_STATE_REQUIRED")
        fd = os.open(str(self.path), os.O_CREAT | os.O_RDWR, 0o600)
        os.close(fd)
        with closing(self.connect()) as db:
            if db.execute("PRAGMA journal_mode=WAL").fetchone()[0] != "wal":
                raise ValueError("WAL_REQUIRED")
            db.executescript(SCHEMA)

    def connect(self):
        db = sqlite3.connect(str(self.path), timeout=5, isolation_level=None)
        db.row_factory = sqlite3.Row
        db.execute("PRAGMA foreign_keys=ON")
        db.execute("PRAGMA synchronous=FULL")
        db.execute("PRAGMA busy_timeout=5000")
        return db

    @contextmanager
    def transaction(self):
        db = self.connect()
        try:
            db.execute("BEGIN IMMEDIATE")
            yield db
            db.commit()
        except BaseException:
            db.rollback()
            raise
        finally:
            db.close()

    def provision(self, now, alias, duration_ms=MAX_CREDENTIAL_MS):
        opaque(alias)
        if type(duration_ms) is not int or not 1 <= duration_ms <= MAX_CREDENTIAL_MS:
            raise ValueError("INVALID_DURATION")
        token = secrets.token_urlsafe(32)
        credential_id, run_id, intent_id = new_id("device"), new_id("run"), new_id("intent")
        expires = now + duration_ms
        with self.transaction() as db:
            if db.execute("SELECT COUNT(*) FROM credentials WHERE expires_at>? AND revoked_at IS NULL", (now,)).fetchone()[0] >= 100:
                raise ValueError("CREDENTIAL_LIMIT")
            db.execute("INSERT INTO credentials VALUES (?,?,?,?,?,?,?,?,?)",
                (credential_id, run_id, alias, token_hash(token), now, expires, expires, None, None))
            db.execute("INSERT INTO intents VALUES (?,?,?,NULL)", (intent_id, credential_id, min(expires, now + INTENT_MS)))
        return {"schemaVersion": 1, "environment": "gps-spike", "kind": "TEST_BEARER",
                "baseUrl": "https://gps-spike.vanrides.matheusduarte.dev.br",
                "testRunId": run_id, "alias": alias, "token": token,
                "credentialExpiresAt": expires, "startIntentId": intent_id,
                "intentExpiresAt": min(expires, now + INTENT_MS)}

    def credential_for_file(self, db, bundle):
        token = bundle.get("token")
        if not isinstance(token, str) or len(token) != 43 or not token.isascii():
            raise ValueError("INVALID_PRIVATE_FILE")
        row = db.execute("SELECT * FROM credentials WHERE token_hash=?", (token_hash(token),)).fetchone()
        if row is None:
            raise ValueError("UNKNOWN_CREDENTIAL")
        return row

    def intent(self, now, bundle):
        with self.transaction() as db:
            row = self.credential_for_file(db, bundle)
            if row["revoked_at"] is not None or row["finished_at"] is not None or now >= row["expires_at"]:
                raise ValueError("CREDENTIAL_UNAVAILABLE")
            # Expire before checking: a silent client is not a STOPPED sensor.
            db.execute("UPDATE sessions SET state='EXPIRED' WHERE credential_id=? AND state='RUNNING' AND lease_expires_at<=?", (row["id"], now))
            if db.execute("SELECT 1 FROM sessions WHERE credential_id=? AND state='RUNNING'", (row["id"],)).fetchone():
                raise ValueError("SESSION_ACTIVE")
            intent_id, expiry = new_id("intent"), min(row["expires_at"], now + INTENT_MS)
            db.execute("INSERT INTO intents VALUES (?,?,?,NULL)", (intent_id, row["id"], expiry))
            return {"schemaVersion": 1, "startIntentId": intent_id, "intentExpiresAt": expiry}

    def end(self, now, bundle, revoke=False):
        with self.transaction() as db:
            row = self.credential_for_file(db, bundle)
            column, state = ("revoked_at", "REVOKED") if revoke else ("finished_at", "EXPIRED")
            db.execute("UPDATE credentials SET " + column + "=COALESCE(" + column + ",?) WHERE id=?", (now, row["id"]))
            db.execute("UPDATE sessions SET state=?,lease_expires_at=MIN(lease_expires_at,?) WHERE credential_id=? AND state='RUNNING'", (state, now, row["id"]))

    def summary(self):
        with closing(self.connect()) as db:
            return {"credentials": db.execute("SELECT COUNT(*) FROM credentials").fetchone()[0],
                    "sessions": {row[0]: row[1] for row in db.execute("SELECT state,COUNT(*) FROM sessions GROUP BY state")},
                    "receipts": db.execute("SELECT COUNT(*) FROM receipts").fetchone()[0],
                    "syntheticEvents": db.execute("SELECT COUNT(*) FROM sink").fetchone()[0]}
