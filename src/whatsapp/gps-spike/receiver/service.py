"""Serialized, durable protocol with a RAM-only operational GPS window."""
from dataclasses import dataclass, field
import copy
import fcntl
import hashlib
import os
import re
import threading
import time

from . import approach, validation as v
from .route_config import Route, RouteConfigError
from .store import Store, new_id, token_hash

LEASE_MS = 300000
FRESH_MS = 20000


class Clock:
    def now_ms(self):
        return time.time_ns() // 1000000

    def monotonic_ms(self):
        # CLOCK_BOOTTIME on Linux includes suspend. The receiver is not a mobile
        # process; wall-time checks below also ensure suspend/forward jumps expire.
        clock_id = getattr(time, "CLOCK_BOOTTIME", None)
        return int((time.clock_gettime(clock_id) if clock_id is not None else time.monotonic()) * 1000)


@dataclass
class Runtime:
    fix: object = None
    gps_state: str = "UNKNOWN"
    progress: approach.Progress = field(default_factory=approach.Progress)


class Receiver:
    def __init__(self, store, clock=None, watchdog=True, lock_instance=True, route=None):
        if route is not None and type(route) is not Route:
            raise RouteConfigError()
        # No fallback: absent configuration disables only the approach sink.
        # A frozen snapshot is installed before accepting any session/request.
        self._configured_route = route
        self.store = store
        self.clock = clock or Clock()
        self.started_at = self.clock.now_ms()
        self._wall_at_start = self.started_at
        self._mono_at_start = self.clock.monotonic_ms()
        self._lock = threading.RLock()
        self._runtime = {}
        self._rates = {}
        self._closed = threading.Event()
        self._healthy = True
        self._instance_lock = None
        if lock_instance:
            path = str(store.path) + ".receiver.lock"
            self._instance_lock = os.open(path, os.O_CREAT | os.O_RDWR, 0o600)
            try:
                fcntl.flock(self._instance_lock, fcntl.LOCK_EX | fcntl.LOCK_NB)
            except OSError:
                os.close(self._instance_lock)
                self._instance_lock = None
                raise ValueError("SINGLE_RECEIVER_REQUIRED")
        self.tick()
        self._watchdog = None
        if watchdog:
            self._watchdog = threading.Thread(target=self._watch, name="gps-watchdog", daemon=True)
            self._watchdog.start()

    def now(self):
        # A backward wall-clock jump cannot lengthen lease/freshness in-process.
        mono = self.clock.monotonic_ms()
        now = max(self.clock.now_ms(), self._wall_at_start + mono - self._mono_at_start)
        self._wall_at_start, self._mono_at_start = now, mono
        return now

    def close(self):
        self._closed.set()
        if self._watchdog:
            self._watchdog.join(timeout=6)
        with self._lock:
            self._runtime.clear()
            if self._instance_lock is not None:
                fcntl.flock(self._instance_lock, fcntl.LOCK_UN)
                os.close(self._instance_lock)
                self._instance_lock = None

    def _watch(self):
        while not self._closed.wait(5):
            try:
                self.tick()
            except Exception:
                # No exception/URI/body is logged. Fail closed until restart.
                self._healthy = False

    def _expire(self, db, now):
        db.execute("UPDATE sessions SET state='REVOKED',lease_expires_at=MIN(lease_expires_at,?) WHERE state='RUNNING' AND credential_id IN (SELECT id FROM credentials WHERE revoked_at IS NOT NULL)", (now,))
        db.execute("UPDATE sessions SET state='EXPIRED' WHERE state='RUNNING' AND (lease_expires_at<=? OR credential_id IN (SELECT id FROM credentials WHERE expires_at<=? OR window_ends_at<=? OR finished_at IS NOT NULL))", (now, now, now))
        active = {row[0] for row in db.execute("SELECT id FROM sessions WHERE state='RUNNING'")}
        for session_id in list(self._runtime):
            runtime = self._runtime[session_id]
            if session_id not in active:
                del self._runtime[session_id]
            elif runtime.fix and now - runtime.fix["capturedAt"] > FRESH_MS:
                runtime.gps_state = "STALE"
                runtime.fix = None
                runtime.progress.window.clear()
                runtime.progress.approached = False
                runtime.progress.nearest = float("inf")
                runtime.progress.dwell_since = None
                runtime.progress.entered_pass = False
                runtime.progress.last = None

    def tick(self):
        with self._lock, self.store.transaction() as db:
            self._expire(db, self.now())

    def _authenticate(self, db, authorization, now):
        if not isinstance(authorization, str) or not re.fullmatch(r"Bearer [A-Za-z0-9_-]{43}", authorization, re.ASCII):
            v.fail("UNAUTHORIZED", 401)
        row = db.execute("SELECT * FROM credentials WHERE token_hash=?", (token_hash(authorization[7:]),)).fetchone()
        if row is None:
            v.fail("UNAUTHORIZED", 401)
        if row["revoked_at"] is not None:
            v.fail("CREDENTIAL_REVOKED", 403)
        if now >= row["expires_at"]:
            v.fail("CREDENTIAL_EXPIRED", 401)
        return row

    def _rate(self, credential_id, now):
        minute = now // 60000
        # Only authenticated IDs enter this bounded map; private CLI allows <=100
        # live credentials. Expired buckets are deleted, no IPs/headers retained.
        self._rates = {key: val for key, val in self._rates.items() if val[0] == minute}
        bucket, count = self._rates.get(credential_id, (minute, 0))
        if count >= 120:
            v.fail("RATE_LIMITED", 429)
        self._rates[credential_id] = (bucket, count + 1)

    def _session(self, db, credential, session_id, generation):
        session = db.execute("SELECT * FROM sessions WHERE id=? AND credential_id=?", (session_id, credential["id"])).fetchone()
        if session is None:
            v.fail("NOT_FOUND", 404)
        if generation != session["generation"]:
            v.fail("GENERATION_CONFLICT", 409)
        return session

    def _renew(self, db, credential, session, now):
        if (session["state"] == "RUNNING" and now < session["lease_expires_at"]
                and credential["finished_at"] is None and now < credential["window_ends_at"]):
            expiry = min(now + LEASE_MS, credential["expires_at"], credential["window_ends_at"])
            db.execute("UPDATE sessions SET lease_expires_at=? WHERE id=?", (expiry, session["id"]))
            return db.execute("SELECT * FROM sessions WHERE id=?", (session["id"],)).fetchone()
        return session

    def _gps_state(self, session):
        runtime = self._runtime.get(session["id"])
        return runtime.gps_state if runtime else "UNKNOWN"

    def _control(self, session, now):
        return {"schemaVersion": 1, "environment": "gps-spike", "sessionId": session["id"],
                "generation": session["generation"], "serverTime": now,
                "leaseExpiresAt": session["lease_expires_at"],
                "action": "CONTINUE" if session["state"] == "RUNNING" and now < session["lease_expires_at"] else "STOP",
                "gpsState": self._gps_state(session)}

    def handle(self, method, path, authorization, body=b"", content_type="application/json"):
        """All authentication precedes route/body/owner information. No logging.

        BEGIN IMMEDIATE also serializes private CLI revoke/finish with requests.
        The commit completes before this method returns an ACK to WSGI.
        """
        streamed = callable(body)
        if streamed:
            # A slow client may occupy an HTTP thread, never the watchdog/DB
            # transaction. Revalidate below after reading to close revoke/expiry
            # TOCTOU. The edge must additionally bound body-read time.
            with self._lock, self.store.transaction() as db:
                if self._closed.is_set() or not self._healthy:
                    v.fail("UNAVAILABLE", 503)
                credential = self._authenticate(db, authorization, self.now())
                self._rate(credential["id"], self.now())
            body = body()
        with self._lock:
            if self._closed.is_set() or not self._healthy:
                v.fail("UNAVAILABLE", 503)
            previous_runtime = copy.deepcopy(self._runtime)
            try:
                with self.store.transaction() as db:
                    now = self.now()
                    credential = self._authenticate(db, authorization, now)
                    if not streamed:
                        self._rate(credential["id"], now)
                    self._expire(db, now)
                    result = self._route(db, credential, method, path, body, content_type, now)
                return result
            except BaseException:
                # Neither partial receipt nor RAM-only decisions escape rollback.
                self._runtime = previous_runtime
                raise

    def _route(self, db, credential, method, path, body, content_type, now):
        if len(body) > v.MAX_BODY:
            v.fail("BODY_TOO_LARGE", 413)
        if method == "GET" and path == "/driver-app/v1/status":
            if body:
                v.fail()
            session = db.execute("SELECT * FROM sessions WHERE credential_id=? ORDER BY generation DESC LIMIT 1", (credential["id"],)).fetchone()
            snapshot = None if session is None else {
                "sessionId": session["id"], "generation": session["generation"], "state": session["state"],
                "leaseExpiresAt": session["lease_expires_at"], "gpsState": self._gps_state(session)}
            return {"schemaVersion": 1, "environment": "gps-spike", "serverTime": now,
                    "credentialExpiresAt": credential["expires_at"], "session": snapshot}
        if method != "POST":
            v.fail("NOT_FOUND", 404)
        match = re.fullmatch(r"/driver-app/v1/(start-intents|tracking-sessions)/([A-Za-z0-9_-]{1,80})/(activate|samples|state)", path, re.ASCII)
        if not match or (match[1] == "start-intents") != (match[3] == "activate"):
            v.fail("NOT_FOUND", 404)
        if content_type.split(";", 1)[0].strip().lower() != "application/json":
            v.fail("INVALID_CONTENT_TYPE")
        value = v.decode(body)
        if match[3] == "activate":
            v.fields(value, ("schemaVersion",))
            return self._activate(db, credential, match[2], now)
        if match[3] == "samples":
            v.samples(value)
            if value["sessionId"] != match[2]:
                v.fail("SESSION_CONFLICT", 409)
            session = self._session(db, credential, match[2], value["generation"])
            return self._samples(db, credential, session, value, now)
        v.state(value)
        session = self._session(db, credential, match[2], value["generation"])
        return self._state(db, credential, session, value, now)

    def _activate(self, db, credential, intent_id, now):
        intent = db.execute("SELECT * FROM intents WHERE id=? AND credential_id=?", (intent_id, credential["id"])).fetchone()
        if intent is None:
            v.fail("NOT_FOUND", 404)
        if intent["session_id"]:
            session = db.execute("SELECT * FROM sessions WHERE id=?", (intent["session_id"],)).fetchone()
            return self._control(self._renew(db, credential, session, now), now)
        if now >= intent["expires_at"] or credential["finished_at"] is not None:
            v.fail("INTENT_EXPIRED", 410)
        if db.execute("SELECT 1 FROM sessions WHERE credential_id=? AND state='RUNNING'", (credential["id"],)).fetchone():
            v.fail("SESSION_ACTIVE", 409)
        generation = db.execute("SELECT COALESCE(MAX(generation),0)+1 FROM sessions WHERE credential_id=?", (credential["id"],)).fetchone()[0]
        session_id = new_id("session")
        expiry = min(now + LEASE_MS, credential["expires_at"], credential["window_ends_at"])
        db.execute("INSERT INTO sessions (id,credential_id,generation,state,lease_expires_at,created_at) VALUES (?,?,?,'RUNNING',?,?)", (session_id, credential["id"], generation, expiry, now))
        db.execute("UPDATE intents SET session_id=? WHERE id=?", (session_id, intent_id))
        session = db.execute("SELECT * FROM sessions WHERE id=?", (session_id,)).fetchone()
        return self._control(session, now)

    def _samples(self, db, credential, session, value, now):
        key = (session["id"], session["generation"])
        batch_hash = v.digest(value)
        existing = db.execute("SELECT digest FROM batches WHERE session_id=? AND generation=? AND batch_id=?", key + (value["batchId"],)).fetchone()
        if existing and existing[0] != batch_hash:
            v.fail("BATCH_CONFLICT", 409)
        accepted, duplicate, new_rows = [], [], []
        # Validate every digest before writing or operating on any sample.
        for sample in value["samples"]:
            sequence, sample_hash = sample["sequence"], v.digest(sample)
            receipt = db.execute("SELECT digest FROM receipts WHERE session_id=? AND generation=? AND sequence=?", key + (sequence,)).fetchone()
            if receipt:
                if receipt[0] != sample_hash:
                    v.fail("SAMPLE_CONFLICT", 409)
                duplicate.append(sequence)
            else:
                accepted.append(sequence)
                new_rows.append((sample, sample_hash))
        if new_rows and session["state"] != "RUNNING":
            v.fail("SESSION_ENDED", 410)
        if existing is None:
            db.execute("INSERT INTO batches VALUES (?,?,?,?,?)", key + (value["batchId"], batch_hash, now))
        for sample, sample_hash in new_rows:
            db.execute("INSERT INTO receipts VALUES (?,?,?,?,?)", key + (sample["sequence"], sample_hash, now))
            self._observe(db, session, sample, now)
        session = db.execute("SELECT * FROM sessions WHERE id=?", (session["id"],)).fetchone()
        session = self._renew(db, credential, session, now)
        return dict(self._control(session, now), accepted=accepted, duplicate=duplicate, rejected=[])

    def _observe(self, db, session, sample, now):
        last = db.execute("SELECT last_captured_at FROM sessions WHERE id=?", (session["id"],)).fetchone()[0]
        captured = sample["capturedAt"]
        # Future/old/bad fixes are transport receipts only. Never advance the
        # acquisition watermark with an invalid fix (future poison prevention).
        if (captured <= last or captured < self.started_at or now - captured > FRESH_MS
                or captured - now > 10000 or sample["accuracyM"] > 50):
            return
        db.execute("UPDATE sessions SET last_captured_at=? WHERE id=?", (captured, session["id"]))
        runtime = self._runtime.setdefault(session["id"], Runtime())
        runtime.fix = dict(sample)
        runtime.gps_state = "FRESH"
        stops = self._configured_route.stops if self._configured_route is not None else ()
        for stop_key, event_type in approach.advance(runtime.progress, sample, stops):
            # Sink stores only a deterministic opaque ID, server time and type.
            raw_key = "%s:%s:%s:%s" % (session["id"], session["generation"], stop_key, event_type)
            event_id = hashlib.sha256(raw_key.encode("ascii")).hexdigest()
            db.execute("INSERT OR IGNORE INTO sink VALUES (?,?,?)", (event_id, now, event_type))

    def _state(self, db, credential, session, value, now):
        if value["state"] == "STOPPED":
            # Only explicit client ACK sets STOPPED/stopped_at, even after lease.
            if session["stop_ack_at"] is None:
                db.execute("UPDATE sessions SET state='STOPPED',lease_expires_at=MIN(lease_expires_at,?),stop_requested_at=?,stopped_at=?,stop_ack_at=? WHERE id=?",
                    (now, value.get("stopRequestedAt"), value.get("stoppedAt"), now, session["id"]))
            self._runtime.pop(session["id"], None)
            session = db.execute("SELECT * FROM sessions WHERE id=?", (session["id"],)).fetchone()
        else:
            session = self._renew(db, credential, session, now)
        return self._control(session, now)
