from contextlib import closing, redirect_stderr, redirect_stdout
from dataclasses import FrozenInstanceError
import io
import json
import os
from pathlib import Path
import tempfile
import time
import unittest
from unittest.mock import patch

from receiver.approach import Stop
from receiver.cli import main
from receiver.route_config import MAX_ROUTE_BYTES, ROUTE_FILE, Route, RouteConfigError, decode_route, load_route
from receiver.service import Receiver
from receiver.store import Store
from receiver import web


def configuration():
    # Deliberately synthetic private configuration, away from the builtin route.
    return {"schemaVersion": 1, "routeId": "private_fixture_A", "stops": [
        {"id": "private_stop_A", "latitude": 12.345678, "longitude": 34.567891, "final": False},
        {"id": "private_final_A", "latitude": 12.345678 + 1000 / 111195, "longitude": 34.567891, "final": True}]}


def encoded(value):
    return json.dumps(value, separators=(",", ":")).encode()


class Clock:
    def __init__(self):
        self.wall, self.mono = 1793376000000, 0

    def now_ms(self):
        return self.wall

    def monotonic_ms(self):
        return self.mono

    def advance(self, ms):
        self.wall += ms
        self.mono += ms


class RouteConfigTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.path = self.directory / "route-v1.json"

    def tearDown(self):
        self.temp.cleanup()

    def write(self, value=None, raw=None):
        self.path.write_bytes(encoded(configuration() if value is None else value) if raw is None else raw)
        self.path.chmod(0o600)

    def invalid(self, callback):
        with self.assertRaises(RouteConfigError) as raised:
            callback()
        self.assertEqual("INVALID_PRIVATE_ROUTE", str(raised.exception))

    def test_strict_schema_limits_and_unique_last_final(self):
        cases = []
        for field, value in (("schemaVersion", True), ("schemaVersion", 1.0), ("routeId", "x" * 81), ("routeId", "não_ASCII"), ("stops", []), ("unknown", "PRIVATE_CONTENT")):
            candidate = configuration()
            candidate[field] = value
            cases.append(candidate)
        for field, value in (("latitude", True), ("latitude", float('nan')), ("latitude", float('inf')), ("latitude", 90.01), ("longitude", -180.01), ("longitude", "34.567891"), ("final", 0), ("final", True), ("extra", "PRIVATE_CONTENT")):
            candidate = configuration()
            candidate["stops"][0][field] = value
            cases.append(candidate)
        for mutation in ("duplicate_id", "duplicate_point", "missing_final", "no_final"):
            candidate = configuration()
            if mutation == "duplicate_id":
                candidate["stops"][1]["id"] = candidate["stops"][0]["id"]
            elif mutation == "duplicate_point":
                candidate["stops"][1].update(latitude=candidate["stops"][0]["latitude"], longitude=candidate["stops"][0]["longitude"])
            elif mutation == "missing_final":
                del candidate["stops"][1]["final"]
            else:
                candidate["stops"][1]["final"] = False
            cases.append(candidate)
        candidate = configuration()
        candidate["stops"] = [{"id": "stop_%s" % index, "latitude": index, "longitude": 0, "final": index == 30} for index in range(31)]
        cases.append(candidate)
        for index, candidate in enumerate(cases):
            with self.subTest(case=index):
                self.invalid(lambda: decode_route(encoded(candidate)))
        self.invalid(lambda: decode_route(b'{"schemaVersion":1,"routeId":"a","routeId":"b","stops":[]}'))
        self.invalid(lambda: decode_route(b'\xffPRIVATE_CONTENT'))
        self.invalid(lambda: decode_route(encoded(configuration()) + b' ' * MAX_ROUTE_BYTES))
        boundary = {"schemaVersion": 1, "routeId": "boundary", "stops": [
            {"id": "only", "latitude": -90, "longitude": 180, "final": True}]}
        self.assertEqual(1, len(decode_route(encoded(boundary)).stops))

    def test_private_file_and_missing_are_distinct(self):
        self.assertIsNone(load_route(self.path, missing_ok=True))
        self.invalid(lambda: load_route(self.path))
        self.write()
        route = load_route(self.path)
        self.assertEqual({"routeId": "private_fixture_A", "stops": 2}, route.summary())
        self.path.chmod(0o644)
        self.invalid(lambda: load_route(self.path, missing_ok=True))
        self.path.chmod(0o600)
        with patch("receiver.route_config.os.getuid", return_value=os.getuid() + 1):
            self.invalid(lambda: load_route(self.path))
        link = self.directory / "linked.json"
        link.symlink_to(self.path)
        self.invalid(lambda: load_route(link, missing_ok=True))
        self.path.unlink()
        self.invalid(lambda: load_route(link, missing_ok=True))
        self.invalid(lambda: load_route(self.directory))
        self.write(raw=b" " * (MAX_ROUTE_BYTES + 1))
        self.invalid(lambda: load_route(self.path))
        self.write(raw=b"PRIVATE_CONTENT_WITH_COORDINATES")
        self.invalid(lambda: load_route(self.path, missing_ok=True))

    def test_special_file_does_not_block_or_get_read(self):
        if not hasattr(os, "mkfifo"):
            self.skipTest("FIFO unsupported")
        os.mkfifo(self.path, 0o600)
        started = time.monotonic()
        self.invalid(lambda: load_route(self.path))
        self.assertLess(time.monotonic() - started, 1)

    def test_injected_route_freezes_original_collection_and_stops(self):
        original = [Stop("a", 1, 2), Stop("b", 2, 2, True)]
        route = Route("injected", original)
        original.clear()
        self.assertEqual(2, len(route.stops))
        with self.assertRaises(FrozenInstanceError):
            route.stops = ()
        with self.assertRaises(FrozenInstanceError):
            route.stops[0].latitude = 90
        self.invalid(lambda: Route("invalid", [Stop("missing_final", 0, 0)]))

    def test_cli_validates_only_selected_file_and_outputs_no_coordinates(self):
        self.write()
        out, err = io.StringIO(), io.StringIO()
        with patch("receiver.cli.Store") as store, redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(0, main(["route-check", "--file", str(self.path)]))
            store.assert_not_called()
        self.assertEqual({"result": "ROUTE_VALID", "routeId": "private_fixture_A", "stops": 2}, json.loads(out.getvalue()))
        self.assertEqual("", err.getvalue())
        self.write(raw=b"PRIVATE_CONTENT_WITH_COORDINATES")
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err):
            self.assertEqual(1, main(["route-check", "--file", str(self.path)]))
        self.assertEqual("", out.getvalue())
        self.assertEqual({"code": "INVALID_PRIVATE_ROUTE"}, json.loads(err.getvalue()))
        self.assertFalse((self.directory / "receiver.sqlite").exists())

    def test_wsgi_missing_disables_and_invalid_fails_before_database(self):
        self.assertEqual(Path("/data/route-v1.json"), ROUTE_FILE)
        store = Store(self.directory / "receiver.sqlite")
        with patch("receiver.web.load_route", side_effect=lambda **kw: load_route(self.path, **kw)) as loader, patch("receiver.web.Store", return_value=store):
            app = web.create_app()
        try:
            self.assertIsNone(app.receiver._configured_route)
            loader.assert_called_once_with(missing_ok=True)
        finally:
            app.receiver.close()
        self.write(raw=b"PRIVATE_CONTENT_WITH_COORDINATES")
        with patch("receiver.web.load_route", side_effect=lambda **kw: load_route(self.path, **kw)), patch("receiver.web.Store") as store_factory:
            self.invalid(web.create_app)
            store_factory.assert_not_called()


class ReceiverRouteTest(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory()
        self.directory = Path(self.temp.name)
        self.clock = Clock()
        self.store = Store(self.directory / "receiver.sqlite")
        self.bundle = self.store.provision(self.clock.wall, "synthetic_custom_route")
        self.auth = "Bearer " + self.bundle["token"]
        self.receiver = None

    def tearDown(self):
        if self.receiver:
            self.receiver.close()
        self.temp.cleanup()

    def start(self, route=None):
        self.receiver = Receiver(self.store, self.clock, watchdog=False, route=route)
        self.control = self.receiver.handle("POST", "/driver-app/v1/start-intents/" + self.bundle["startIntentId"] + "/activate", self.auth, b'{"schemaVersion":1}')

    def sample(self, sequence, latitude, longitude, speed=8):
        sample = {"sequence": sequence, "capturedAt": self.clock.wall, "latitude": latitude, "longitude": longitude,
                  "accuracyM": 5, "speedMps": speed, "headingDeg": 0}
        body = {"schemaVersion": 1, "sessionId": self.control["sessionId"], "generation": self.control["generation"],
                "batchId": "route_batch_%s" % sequence, "deviceTime": self.clock.wall, "samples": [sample]}
        return self.receiver.handle("POST", "/driver-app/v1/tracking-sessions/" + self.control["sessionId"] + "/samples", self.auth, encoded(body))

    def test_custom_file_drives_approach_pass_and_final_without_reload_or_persistence(self):
        path = self.directory / "route-v1.json"
        path.write_bytes(encoded(configuration()))
        path.chmod(0o600)
        self.start(load_route(path))
        initial = self.clock.wall
        trace = [(0, -650), (5, -500), (10, -300), (15, -80), (20, 120), (25, 340), (30, 500), (35, 700), (40, 905)]
        trace += [(second, 905) for second in range(50, 131, 10)]
        for sequence, (second, meters) in enumerate(trace, 1):
            self.clock.advance(initial + second * 1000 - self.clock.wall)
            ack = self.sample(sequence, 12.345678 + meters / 111195, 34.567891, speed=0.3 if second >= 40 else 8)
            self.assertEqual([sequence], ack["accepted"])
            self.assertEqual("FRESH", ack["gpsState"])
            if sequence == 1:
                # Editing an on-disk route during RUNNING cannot replace snapshot.
                path.write_bytes(b"INVALID_REPLACEMENT_MUST_WAIT_FOR_RESTART")
        with closing(self.store.connect()) as db:
            events = dict(db.execute("SELECT type,COUNT(*) FROM sink GROUP BY type"))
            self.assertEqual({"APPROACH_V1": 2, "PASS_V1": 1, "FINAL_DWELL_V1": 1}, events)
            dump = "\n".join(db.iterdump())
        for marker in ("12.345678", "34.567891", "private_fixture_A", "private_stop_A", "latitude", "longitude"):
            self.assertNotIn(marker, dump)
        out, err = io.StringIO(), io.StringIO()
        with redirect_stdout(out), redirect_stderr(err), patch("receiver.cli.load_route") as loader:
            self.assertEqual(0, main(["--db", str(self.store.path), "status"]))
            loader.assert_not_called()
        self.assertEqual(4, json.loads(out.getvalue())["syntheticEvents"])
        self.assertEqual("", err.getvalue())
        self.receiver.close()
        self.receiver = None
        with patch("receiver.web.load_route", side_effect=lambda **kw: load_route(path, **kw)):
            with self.assertRaisesRegex(RouteConfigError, "^INVALID_PRIVATE_ROUTE$"):
                web.create_app()

    def test_default_has_no_synthetic_fallback_and_preserves_transport_lease_stop(self):
        self.start()
        for sequence, meters in enumerate((-650, -500, -300), 1):
            if sequence > 1:
                self.clock.advance(5000)
            ack = self.sample(sequence, meters / 111195, 0)
            self.assertEqual("FRESH", ack["gpsState"])
            self.assertEqual("CONTINUE", ack["action"])
        with closing(self.store.connect()) as db:
            self.assertEqual(0, db.execute("SELECT COUNT(*) FROM sink").fetchone()[0])
            self.assertEqual(3, db.execute("SELECT COUNT(*) FROM receipts").fetchone()[0])
        self.clock.advance(300001)
        self.receiver.tick()
        status = self.receiver.handle("GET", "/driver-app/v1/status", self.auth)
        self.assertEqual("EXPIRED", status["session"]["state"])
        stop = {"schemaVersion": 1, "generation": self.control["generation"], "state": "STOPPED"}
        result = self.receiver.handle("POST", "/driver-app/v1/tracking-sessions/" + self.control["sessionId"] + "/state", self.auth, encoded(stop))
        self.assertEqual("STOP", result["action"])
        self.assertEqual("STOPPED", self.receiver.handle("GET", "/driver-app/v1/status", self.auth)["session"]["state"])


if __name__ == "__main__":
    unittest.main()
