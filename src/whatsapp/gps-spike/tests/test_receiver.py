import concurrent.futures
import copy
from contextlib import closing, redirect_stderr, redirect_stdout
import io
import json
import os
from pathlib import Path
import subprocess
import sys
import tempfile
import threading
import time
import unittest

from receiver import approach
from receiver.cli import main, read_private, write_private
from receiver.service import Receiver
from receiver.route_config import Route
from receiver.store import MAX_CREDENTIAL_MS, Store
from receiver.validation import ProtocolError
from receiver.web import Application


class FakeClock:
    def __init__(self):
        self.wall = 1793376000000
        self.mono = 0

    def now_ms(self):
        return self.wall

    def monotonic_ms(self):
        return self.mono

    def advance(self, ms):
        self.wall += ms
        self.mono += ms


def encoded(value):
    return json.dumps(value, separators=(",", ":")).encode("utf-8")


TEST_ROUTE = Route("synthetic_unit_route", approach.SYNTHETIC_ROUTE)


class ReceiverTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name) / "receiver.sqlite"
        self.clock = FakeClock()
        self.store = Store(self.path)
        self.bundle = self.store.provision(self.clock.wall, "synthetic_test")
        self.receiver = Receiver(self.store, self.clock, watchdog=False, route=TEST_ROUTE)
        self.auth = "Bearer " + self.bundle["token"]
        self.control = self.request("activate", {"schemaVersion": 1})

    def tearDown(self):
        self.receiver.close()
        self.temp.cleanup()

    def request(self, endpoint, value=None, auth=None, identifier=None):
        if endpoint == "status":
            return self.receiver.handle("GET", "/driver-app/v1/status", auth or self.auth)
        if endpoint == "activate":
            path = "/driver-app/v1/start-intents/%s/activate" % (identifier or self.bundle["startIntentId"])
        else:
            path = "/driver-app/v1/tracking-sessions/%s/%s" % (identifier or self.control["sessionId"], endpoint)
        return self.receiver.handle("POST", path, auth or self.auth, encoded(value))

    def sample(self, sequence=1, **changes):
        value = {"sequence": sequence, "capturedAt": self.clock.wall, "latitude": 35.123456789,
                 "longitude": 137.987654321, "accuracyM": 5.0, "speedMps": 1.0, "headingDeg": 90.0}
        value.update(changes)
        return value

    def batch(self, rows=None, batch_id="batch_1", **changes):
        value = {"schemaVersion": 1, "sessionId": self.control["sessionId"],
                 "generation": self.control["generation"], "batchId": batch_id,
                 "deviceTime": self.clock.wall, "samples": rows or [self.sample()]}
        value.update(changes)
        return value

    def assert_code(self, status, callback, code=None):
        with self.assertRaises(ProtocolError) as raised:
            callback()
        self.assertEqual(status, raised.exception.status)
        if code:
            self.assertEqual(code, raised.exception.code)

    def count(self, table):
        with closing(self.store.connect()) as db:
            return db.execute("SELECT COUNT(*) FROM " + table).fetchone()[0]

    def test_ack_loss_restart_duplicate_durable_without_position(self):
        batch = self.batch()
        ack = self.request("samples", batch)  # Deliberately discard this ACK.
        self.assertEqual([1], ack["accepted"])
        self.assertEqual("FRESH", ack["gpsState"])
        with closing(self.store.connect()) as db:
            self.assertEqual("wal", db.execute("PRAGMA journal_mode").fetchone()[0])
            self.assertEqual(2, db.execute("PRAGMA synchronous").fetchone()[0])
            self.assertEqual(1, db.execute("SELECT COUNT(*) FROM receipts").fetchone()[0])
        self.clock.advance(1)
        self.receiver.close()
        self.receiver = Receiver(Store(self.path), self.clock, watchdog=False, route=TEST_ROUTE)
        self.assertEqual("UNKNOWN", self.request("status")["session"]["gpsState"])
        replay = self.request("samples", batch)
        self.assertEqual([], replay["accepted"])
        self.assertEqual([1], replay["duplicate"])
        self.assertEqual("UNKNOWN", replay["gpsState"])
        new = self.batch([self.sample(2)], "batch_2")
        self.assertEqual("FRESH", self.request("samples", new)["gpsState"])

    def test_process_exit_without_cleanup_preserves_receipt(self):
        credential_file = self.path.parent / "private.json"
        write_private(credential_file, self.bundle)
        script = '''
import json, os, sys
from receiver.service import Receiver
from receiver.route_config import Route
from receiver.approach import SYNTHETIC_ROUTE
from receiver.store import Store
class Clock:
 def now_ms(self): return 1793376000000
 def monotonic_ms(self): return 0
with open(sys.argv[2]) as source: bundle=json.load(source)
receiver=Receiver(Store(sys.argv[1]),Clock(),watchdog=False,lock_instance=False,route=Route("synthetic_child",SYNTHETIC_ROUTE))
auth="Bearer "+bundle["token"]
session=receiver.handle("GET","/driver-app/v1/status",auth)["session"]
batch={"schemaVersion":1,"sessionId":session["sessionId"],"generation":session["generation"],
 "batchId":"hard_exit","deviceTime":1793376000000,"samples":[{"sequence":111,"capturedAt":1793376000000,
 "latitude":0.0,"longitude":0.0,"accuracyM":5.0}]}
receiver.handle("POST","/driver-app/v1/tracking-sessions/"+session["sessionId"]+"/samples",auth,json.dumps(batch).encode())
os._exit(0)
'''
        child = subprocess.run([sys.executable, "-c", script, str(self.path), str(credential_file)],
                               stdout=subprocess.PIPE, stderr=subprocess.PIPE, timeout=8)
        self.assertEqual(0, child.returncode)
        self.assertEqual(b"", child.stdout + child.stderr)
        self.receiver.close()
        self.receiver = Receiver(Store(self.path), self.clock, watchdog=False, route=TEST_ROUTE)
        batch = self.batch([self.sample(111, latitude=0.0, longitude=0.0)], "hard_exit")
        del batch["samples"][0]["speedMps"]
        del batch["samples"][0]["headingDeg"]
        self.assertEqual([111], self.request("samples", batch)["duplicate"])
        self.assertEqual("UNKNOWN", self.request("status")["session"]["gpsState"])

    def test_changed_batch_device_time_conflicts(self):
        batch = self.batch()
        self.request("samples", batch)
        other = copy.deepcopy(batch)
        other["deviceTime"] += 1
        self.assert_code(409, lambda: self.request("samples", other), "BATCH_CONFLICT")
        self.assertEqual(1, self.count("receipts"))

    def test_changed_sample_rolls_back_entire_batch(self):
        self.request("samples", self.batch())
        changed = self.sample(1, latitude=35.222222)
        batch = self.batch([self.sample(2), changed], "batch_other")
        self.assert_code(409, lambda: self.request("samples", batch), "SAMPLE_CONFLICT")
        self.assertEqual(1, self.count("receipts"))
        self.assertEqual(1, self.count("batches"))

    def test_sample_reused_in_new_batch_is_individually_duplicate(self):
        batch = self.batch()
        self.request("samples", batch)
        ack = self.request("samples", self.batch([batch["samples"][0], self.sample(2)], "next"))
        self.assertEqual([2], ack["accepted"])
        self.assertEqual([1], ack["duplicate"])
        self.assertEqual([], ack["rejected"])

    def test_owner_generation_and_exact_session_id(self):
        other = self.store.provision(self.clock.wall, "synthetic_other")
        auth = "Bearer " + other["token"]
        self.assert_code(404, lambda: self.request("activate", {"schemaVersion": 1}, auth=auth))
        self.assert_code(404, lambda: self.request("samples", self.batch(), auth=auth))
        self.assert_code(409, lambda: self.request("samples", self.batch(generation=2)))
        self.assert_code(409, lambda: self.request("samples", self.batch(sessionId="other")))
        self.assertEqual(0, self.count("receipts"))

    def test_old_future_accuracy_and_out_of_order_are_transport_only(self):
        self.clock.advance(30000)
        invalid = [self.sample(1, capturedAt=self.clock.wall - 20001),
                   self.sample(2, capturedAt=self.clock.wall + 10001),
                   self.sample(3, accuracyM=50.01)]
        self.assertEqual("UNKNOWN", self.request("samples", self.batch(invalid))["gpsState"])
        self.assertEqual(3, self.count("receipts"))
        fix_time = self.clock.wall
        self.request("samples", self.batch([self.sample(4)], "fresh"))
        self.clock.advance(1)
        self.request("samples", self.batch([self.sample(5, capturedAt=fix_time)], "same_acquisition"))
        self.assertEqual(4, self.receiver._runtime[self.control["sessionId"]].fix["sequence"])
        self.clock.advance(21000)
        self.receiver.tick()
        self.assertEqual("STALE", self.request("status")["session"]["gpsState"])
        self.assertEqual("STALE", self.request("samples", self.batch([self.sample(5, capturedAt=fix_time)], "old_order"))["gpsState"])
        self.assertEqual("FRESH", self.request("samples", self.batch([self.sample(6)], "new_fix"))["gpsState"])

    def test_heartbeat_and_duplicate_do_not_refresh_gps(self):
        batch = self.batch()
        self.request("samples", batch)
        self.clock.advance(20001)
        state = {"schemaVersion": 1, "generation": 1, "state": "RUNNING"}
        self.assertEqual("STALE", self.request("state", state)["gpsState"])
        self.assertEqual("STALE", self.request("samples", batch)["gpsState"])

    def test_watchdog_degrades_without_heartbeat_or_http_request(self):
        self.receiver.close()
        self.receiver = Receiver(self.store, self.clock, watchdog=True, route=TEST_ROUTE)
        self.request("samples", self.batch())
        self.clock.advance(20001)
        deadline = time.monotonic() + 6.5
        observed = False
        while time.monotonic() < deadline:
            with self.receiver._lock:
                observed = self.receiver._runtime[self.control["sessionId"]].gps_state == "STALE"
            if observed:
                break
            time.sleep(0.02)
        self.assertTrue(observed)

    def test_expiry_never_revives_and_stop_ack_remains_separate(self):
        batch = self.batch()
        self.request("samples", batch)
        self.clock.advance(300000)
        self.receiver.tick()
        self.assertEqual("EXPIRED", self.request("status")["session"]["state"])
        with closing(self.store.connect()) as db:
            self.assertIsNone(db.execute("SELECT stopped_at FROM sessions").fetchone()[0])
            self.assertIsNone(db.execute("SELECT stop_ack_at FROM sessions").fetchone()[0])
        heartbeat = {"schemaVersion": 1, "generation": 1, "state": "RUNNING"}
        self.assertEqual("STOP", self.request("state", heartbeat)["action"])
        self.assertEqual("STOP", self.request("activate", {"schemaVersion": 1})["action"])
        replay = self.request("samples", batch)
        self.assertEqual([1], replay["duplicate"])
        self.assertEqual("STOP", replay["action"])
        self.assert_code(410, lambda: self.request("samples", self.batch([self.sample(2)], "late")))
        stop = {"schemaVersion": 1, "generation": 1, "state": "STOPPED", "reason": "PROCESS_RECREATED",
                "stopRequestedAt": self.clock.wall, "stoppedAt": self.clock.wall}
        self.assertEqual("STOP", self.request("state", stop)["action"])
        self.assertEqual("STOPPED", self.request("status")["session"]["state"])
        self.clock.advance(100)
        self.request("state", dict(stop, stoppedAt=self.clock.wall))
        with closing(self.store.connect()) as db:
            self.assertEqual(stop["stoppedAt"], db.execute("SELECT stopped_at FROM sessions").fetchone()[0])

    def test_lease_renews_only_before_deadline_bounded_by_credential(self):
        self.clock.advance(299999)
        state = {"schemaVersion": 1, "generation": 1, "state": "RUNNING"}
        control = self.request("state", state)
        self.assertEqual(self.clock.wall + 300000, control["leaseExpiresAt"])
        self.assertEqual("CONTINUE", control["action"])
        with self.store.transaction() as db:
            db.execute("UPDATE credentials SET window_ends_at=?", (self.clock.wall + 1234,))
        self.assertEqual(self.clock.wall + 1234, self.request("state", state)["leaseExpiresAt"])
        self.clock.advance(1234)
        self.assertEqual("STOP", self.request("state", state)["action"])

    def test_revoke_before_duplicate_returns_403_never_ack(self):
        batch = self.batch()
        self.request("samples", batch)
        self.store.end(self.clock.wall, self.bundle, revoke=True)
        self.assert_code(403, lambda: self.request("samples", batch), "CREDENTIAL_REVOKED")
        self.assert_code(403, lambda: self.request("state", {"schemaVersion": 1, "generation": 1, "state": "STOPPED"}))
        self.receiver.tick()
        with closing(self.store.connect()) as db:
            row = db.execute("SELECT state,stop_ack_at FROM sessions").fetchone()
            self.assertEqual("REVOKED", row[0])
            self.assertIsNone(row[1])
        self.assertNotIn(self.control["sessionId"], self.receiver._runtime)

    def test_finish_stops_current_duplicate_and_preserves_explicit_stop(self):
        batch = self.batch()
        self.request("samples", batch)
        self.store.end(self.clock.wall, self.bundle)
        self.assertEqual("STOP", self.request("samples", batch)["action"])
        self.assertEqual("EXPIRED", self.request("status")["session"]["state"])
        self.assertEqual("STOP", self.request("state", {"schemaVersion": 1, "generation": 1, "state": "STOPPED"})["action"])
        self.assertEqual("STOPPED", self.request("status")["session"]["state"])

    def test_current_control_after_stop_dedupe_and_no_sensor_restart(self):
        batch = self.batch()
        self.request("samples", batch)
        self.request("state", {"schemaVersion": 1, "generation": 1, "state": "STOPPED"})
        self.assertEqual("STOP", self.request("samples", batch)["action"])
        self.assertEqual("STOP", self.request("activate", {"schemaVersion": 1})["action"])
        intent = self.store.intent(self.clock.wall, self.bundle)
        second = self.request("activate", {"schemaVersion": 1}, identifier=intent["startIntentId"])
        self.assertEqual(2, second["generation"])
        self.assertEqual("STOP", self.request("activate", {"schemaVersion": 1})["action"])
        self.assertEqual(second["sessionId"], self.request("status")["session"]["sessionId"])

    def test_intent_expiry_and_maximum_credential_validity(self):
        other = self.store.provision(self.clock.wall, "synthetic_other")
        self.assertEqual(MAX_CREDENTIAL_MS, other["credentialExpiresAt"] - self.clock.wall)
        self.clock.advance(300000)
        self.assert_code(410, lambda: self.request("activate", {"schemaVersion": 1},
            auth="Bearer " + other["token"], identifier=other["startIntentId"]))
        with self.assertRaises(ValueError):
            self.store.provision(self.clock.wall, "synthetic_bad", MAX_CREDENTIAL_MS + 1)
        self.clock.advance(MAX_CREDENTIAL_MS)
        self.assert_code(401, lambda: self.request("status"))

    def test_strict_types_unknown_keys_nan_and_bounds(self):
        mutations = [{"latitude": float("nan")}, {"latitude": True}, {"latitude": 91},
            {"longitude": -181}, {"accuracyM": -1}, {"accuracyM": 10001},
            {"headingDeg": 360}, {"speedMps": 151}, {"capturedAt": 1.2},
            {"sequence": False}, {"sequence": 0}, {"secret": "unused"}, {"latitude": 10 ** 400}]
        for changes in mutations:
            with self.subTest(changes=tuple(changes)):
                self.assert_code(400, lambda: self.request("samples", self.batch([self.sample(**changes)])))
        for changes in ({"schemaVersion": True}, {"generation": 1.0}, {"deviceTime": True},
                        {"batchId": "bad/id"}, {"unknown": 0}, {"samples": []}):
            self.assert_code(400, lambda: self.request("samples", self.batch(**changes)))
        self.assert_code(400, lambda: self.request("samples", self.batch([self.sample(), self.sample()])))
        self.assert_code(400, lambda: self.request("samples", self.batch([self.sample(i) for i in range(1, 32)])))
        self.assertEqual(0, self.count("receipts"))

    def test_duplicate_json_keys_invalid_utf8_and_exact_byte_bound(self):
        path = "/driver-app/v1/tracking-sessions/%s/samples" % self.control["sessionId"]
        self.assert_code(400, lambda: self.receiver.handle("POST", path, self.auth,
            b'{"schemaVersion":1,"schemaVersion":1}'))
        self.assert_code(400, lambda: self.receiver.handle("POST", path, self.auth, b'\xff'))
        raw = encoded(self.batch())
        padded = raw + b" " * (32768 - len(raw))
        self.assertEqual([1], self.receiver.handle("POST", path, self.auth, padded)["accepted"])
        self.assert_code(413, lambda: self.receiver.handle("POST", path, self.auth, padded + b" "))

    def test_concurrent_requests_have_one_receipt_and_effect(self):
        batch = self.batch()
        barrier = threading.Barrier(8)
        def send(_):
            barrier.wait(timeout=3)
            return self.request("samples", batch)
        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as pool:
            results = list(pool.map(send, range(8)))
        self.assertEqual(1, sum(len(ack["accepted"]) for ack in results))
        self.assertEqual(7, sum(len(ack["duplicate"]) for ack in results))
        self.assertEqual(1, self.count("receipts"))
        self.assertEqual(1, len(self.receiver._runtime[self.control["sessionId"]].progress.window))

    def test_synthetic_fixture_sink_has_only_id_time_type(self):
        fixture_path = Path(__file__).resolve().parent.parent / "fixtures" / "route_v1.json"
        with fixture_path.open() as source:
            fixture = json.load(source)
        initial = self.clock.wall
        last_batch = None
        for sequence, (offset, meters, speed) in enumerate(fixture["trace"], 1):
            self.clock.advance(initial + offset - self.clock.wall)
            sample = self.sample(sequence, latitude=0.0, longitude=meters / fixture["metersPerLongitudeDegree"], speedMps=speed)
            last_batch = self.batch([sample], "fixture_%s" % sequence)
            ack = self.request("samples", last_batch)
            self.assertEqual("FRESH", ack["gpsState"])
        with closing(self.store.connect()) as db:
            events = {row[0]: row[1] for row in db.execute("SELECT type,COUNT(*) FROM sink GROUP BY type")}
            self.assertEqual(fixture["expectedEvents"], events)
            self.assertEqual(["id", "occurred_at", "type"], [row[1] for row in db.execute("PRAGMA table_info(sink)")])
        self.request("samples", last_batch)
        self.assertEqual(4, self.count("sink"))
        self.receiver.close()
        self.receiver = Receiver(Store(self.path), self.clock, watchdog=False, route=TEST_ROUTE)
        self.request("samples", last_batch)
        self.assertEqual(4, self.count("sink"))
        self.assertEqual("UNKNOWN", self.request("status")["session"]["gpsState"])

    def test_database_failure_rolls_back_receipt_and_ram(self):
        original = self.receiver._observe
        def fail_after_observe(*args):
            original(*args)
            raise RuntimeError("SIMULATED_WRITE_FAILURE")
        self.receiver._observe = fail_after_observe
        with self.assertRaises(RuntimeError):
            self.request("samples", self.batch())
        self.assertEqual(0, self.count("receipts"))
        self.assertEqual(0, self.count("batches"))
        self.assertFalse(self.receiver._runtime)

    def test_no_raw_gps_or_token_persisted_or_logged(self):
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            self.request("samples", self.batch())
            self.request("state", {"schemaVersion": 1, "generation": 1, "state": "STOPPED",
                                   "reason": "SOME_ARBITRARY_SECRET"})
        self.assertEqual("", out.getvalue() + err.getvalue())
        for path in self.path.parent.iterdir():
            raw = path.read_bytes()
            for forbidden in (self.bundle["token"], "35.123456789", "137.987654321", "SOME_ARBITRARY_SECRET"):
                self.assertNotIn(forbidden.encode(), raw)
        self.assertFalse(self.receiver._runtime)

    def test_slow_body_cannot_block_watchdog_or_revocation(self):
        entered, release = threading.Event(), threading.Event()
        batch = self.batch()
        def body():
            entered.set()
            if not release.wait(3):
                raise RuntimeError("test_timeout")
            return encoded(batch)
        path = "/driver-app/v1/tracking-sessions/%s/samples" % self.control["sessionId"]
        with concurrent.futures.ThreadPoolExecutor(max_workers=2) as pool:
            pending = pool.submit(self.receiver.handle, "POST", path, self.auth, body)
            self.assertTrue(entered.wait(1))
            self.clock.advance(300001)
            tick = pool.submit(self.receiver.tick)
            tick.result(timeout=1)
            self.assertEqual("EXPIRED", self.request("status")["session"]["state"])
            self.store.end(self.clock.wall, self.bundle, revoke=True)
            release.set()
            self.assert_code(403, lambda: pending.result(timeout=1))
        self.assertEqual(0, self.count("receipts"))

    def test_finish_during_body_revalidation_returns_current_stop(self):
        batch = self.batch()
        self.request("samples", batch)
        def body():
            self.store.end(self.clock.wall, self.bundle)
            return encoded(batch)
        path = "/driver-app/v1/tracking-sessions/%s/samples" % self.control["sessionId"]
        ack = self.receiver.handle("POST", path, self.auth, body)
        self.assertEqual("STOP", ack["action"])
        self.assertEqual([1], ack["duplicate"])

    def test_state_types_times_and_unknown_control_enum(self):
        base = {"schemaVersion": 1, "generation": 1, "state": "STOPPED"}
        for changes in ({"state": "PAUSED"}, {"generation": True}, {"stoppedAt": 1.5},
                        {"stopRequestedAt": 2, "stoppedAt": 1}, {"reason": "X" * 65}):
            self.assert_code(400, lambda: self.request("state", dict(base, **changes)))

    def test_authentication_precedes_route_body_and_owner(self):
        def poison():
            self.fail("Unauthenticated request must not read the body")
        self.assert_code(401, lambda: self.receiver.handle("POST", "/unknown", None, poison))
        self.assert_code(401, lambda: self.receiver.handle("POST", "/unknown", "Bearer bad", b"x" * 40000))
        self.assert_code(404, lambda: self.receiver.handle("GET", "/unknown", self.auth))

    def test_rate_limit_and_unknown_reason_sanitization(self):
        for _ in range(119):  # Activation already consumed one request.
            self.request("status")
        self.assert_code(429, lambda: self.request("status"))
        self.clock.advance(60000)
        self.request("status")
        self.assert_code(400, lambda: self.request("state", {"schemaVersion": 1, "generation": 1,
            "state": "STOPPED", "reason": "secret/unsafe"}))

    def test_second_receiver_cannot_share_ram_state(self):
        with self.assertRaisesRegex(ValueError, "SINGLE_RECEIVER_REQUIRED"):
            Receiver(self.store, self.clock, watchdog=False, route=TEST_ROUTE)

    def test_backward_wall_clock_does_not_extend_lease(self):
        self.clock.advance(100000)
        self.request("status")
        self.clock.wall -= 900000
        self.clock.mono += 200001
        self.receiver.tick()
        self.assertEqual("EXPIRED", self.request("status")["session"]["state"])


class WebAndCliTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.path = Path(self.temp.name)
        self.clock = FakeClock()
        self.store = Store(self.path / "receiver.sqlite")
        self.bundle = self.store.provision(self.clock.wall, "synthetic_web")
        self.receiver = Receiver(self.store, self.clock, watchdog=False, route=TEST_ROUTE)
        self.app = Application(self.receiver)

    def tearDown(self):
        self.receiver.close()
        self.temp.cleanup()

    def call(self, **changes):
        env = {"REQUEST_METHOD": "GET", "PATH_INFO": "/driver-app/v1/status", "QUERY_STRING": "",
               "HTTP_HOST": "127.0.0.1:18765", "HTTP_AUTHORIZATION": "Bearer " + self.bundle["token"],
               "CONTENT_LENGTH": "0", "wsgi.input": io.BytesIO(b"")}
        env.update(changes)
        metadata = []
        result = b"".join(self.app(env, lambda status, headers: metadata.append((status, headers))))
        self.assertEqual("application/json", dict(metadata[0][1])["Content-Type"])
        self.assertNotIn(self.bundle["token"].encode(), result)
        return int(metadata[0][0][:3]), json.loads(result)

    def test_http_status_is_read_only_and_no_public_admin(self):
        status, result = self.call()
        self.assertEqual(200, status)
        self.assertIsNone(result["session"])
        self.assertEqual((404, {"code": "NOT_FOUND"}), self.call(PATH_INFO="/admin/provision"))
        self.assertEqual(401, self.call(HTTP_AUTHORIZATION="Bearer bad", PATH_INFO="/admin")[0])

    def test_http_content_length_host_query_and_type(self):
        self.assertEqual(400, self.call(HTTP_HOST="example.invalid")[0])
        self.assertEqual(400, self.call(QUERY_STRING="token=not_retained")[0])
        path = "/driver-app/v1/start-intents/%s/activate" % self.bundle["startIntentId"]
        self.assertEqual(413, self.call(REQUEST_METHOD="POST", PATH_INFO=path, CONTENT_LENGTH="32769")[0])
        self.assertEqual(400, self.call(REQUEST_METHOD="POST", PATH_INFO=path, CONTENT_LENGTH="-1")[0])
        self.assertEqual(400, self.call(REQUEST_METHOD="POST", PATH_INFO=path, CONTENT_LENGTH="13",
                                      CONTENT_TYPE="text/plain", **{"wsgi.input": io.BytesIO(b'{"schema":1}')})[0])

    def test_framework_exception_never_echoed(self):
        def crash(*args):
            raise ValueError("private_payload_secret")
        self.receiver.handle = crash
        self.assertEqual((503, {"code": "UNAVAILABLE"}), self.call())

    def test_cli_private_bundle_no_token_stdout_or_argument(self):
        out, err = io.StringIO(), io.StringIO()
        args = ["--db", str(self.path / "cli.sqlite"), "provision", "--alias", "synthetic_cli",
                "--out", str(self.path / "private.json")]
        with redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(0, main(args))
        bundle = read_private(self.path / "private.json")
        self.assertEqual(43, len(bundle["token"]))
        self.assertEqual(0o600, (self.path / "private.json").stat().st_mode & 0o777)
        self.assertNotIn(bundle["token"], out.getvalue() + err.getvalue())
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(1, main(args))  # No overwrite.
            self.assertEqual(1, main(["--db", str(self.path / "cli.sqlite"), "--token", bundle["token"]]))
        self.assertNotIn(bundle["token"], out.getvalue() + err.getvalue())

    def test_private_bundle_permissions_symlink_and_git_rejected(self):
        path = self.path / "bundle.json"
        write_private(path, self.bundle)
        path.chmod(0o644)
        with self.assertRaises(ValueError):
            read_private(path)
        link = self.path / "linked.json"
        link.symlink_to(path)
        with self.assertRaises(ValueError):
            read_private(link)
        repo = self.path / "fake-repo"
        repo.mkdir(mode=0o700)
        (repo / ".git").mkdir()
        with self.assertRaises(ValueError):
            write_private(repo / "bundle.json", self.bundle)


class ApproachTest(unittest.TestCase):
    def fix(self, at, meters, **values):
        # Synthetic equatorial eastbound line. 1 degree ~111195m.
        fix = {"sequence": at + 1, "capturedAt": at * 1000, "latitude": 0,
               "longitude": meters / 111195, "accuracyM": 5, "speedMps": 8, "headingDeg": 90}
        fix.update(values)
        return fix

    def test_three_coherent_fixes_approach_then_pass_then_final_dwell(self):
        progress = approach.Progress()
        events = []
        route = (approach.Stop("stop", 0, 0), approach.Stop("final", 0, 1000 / 111195, True))
        for at, m in ((0, -650), (5, -500), (10, -300), (15, -80), (20, 120),
                      (25, 340), (30, 500), (35, 700), (40, 905)):
            events += approach.advance(progress, self.fix(at, m, speedMps=1 if at == 40 else 8), route)
        self.assertIn(("stop", "APPROACH_V1"), events)
        self.assertIn(("stop", "PASS_V1"), events)
        self.assertIn(("final", "APPROACH_V1"), events)
        self.assertFalse(progress.finished)
        for at in range(50, 131, 10):
            events += approach.advance(progress, self.fix(at, 905, speedMps=0.3), route)
        self.assertTrue(progress.finished)
        self.assertEqual(1, events.count(("final", "FINAL_DWELL_V1")))
        self.assertEqual([], approach.advance(progress, self.fix(140, 905, speedMps=0.3), route))

    def test_stationary_wrong_heading_and_teleport_do_not_approach(self):
        for positions, heading in (((-600, -600, -600), 90), ((-650, -500, -300), 270), ((-650, -500, 5000), 90)):
            progress = approach.Progress()
            events = []
            for at, meters in zip((0, 5, 10), positions):
                events += approach.advance(progress, self.fix(at, meters, headingDeg=heading), approach.SYNTHETIC_ROUTE)
            self.assertFalse(events)

    def test_no_final_from_silence_or_single_late_fix(self):
        progress = approach.Progress()
        route = (approach.Stop("final", 0, 0, True),)
        for at, m in ((0, -650), (5, -500), (10, -300), (15, -80)):
            approach.advance(progress, self.fix(at, m, speedMps=1), route)
        self.assertIsNotNone(progress.dwell_since)
        self.assertFalse(progress.finished)
        events = approach.advance(progress, self.fix(120, -80, speedMps=0), route)
        self.assertFalse(progress.finished)
        self.assertNotIn(("final", "FINAL_DWELL_V1"), events)


if __name__ == "__main__":
    unittest.main()
