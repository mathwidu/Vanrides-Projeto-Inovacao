#!/usr/bin/env python3
from __future__ import annotations

import importlib.util
import json
import stat
import sys
import threading
import unittest
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts" / "qa_rate_limit_trust_load.py"
SPEC = importlib.util.spec_from_file_location("qa_rate_limit_trust_load", SCRIPT)
assert SPEC is not None and SPEC.loader is not None
MODULE = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = MODULE
SPEC.loader.exec_module(MODULE)


class _RateLimitHandler(BaseHTTPRequestHandler):
    lock = threading.Lock()
    requests = 0
    limit = 2
    forwarded_values: set[str] = set()
    include_retry_after = True
    non_limited_status = 400

    def do_POST(self) -> None:  # noqa: N802
        self._respond()

    def do_GET(self) -> None:  # noqa: N802
        self._respond()

    def _respond(self) -> None:
        with self.lock:
            type(self).requests += 1
            request_number = type(self).requests
            forwarded = self.headers.get("X-Forwarded-For")
            if forwarded:
                type(self).forwarded_values.add(forwarded)
        if request_number <= self.limit:
            status = self.non_limited_status
            body = {"code": "INVALID_TEST_INPUT" if status == 400 else "TEST_SERVER_ERROR"}
        else:
            status = 429
            body = {"code": "RATE_LIMITED"}
        encoded = json.dumps(body).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json")
        self.send_header("Content-Length", str(len(encoded)))
        self.send_header("X-Request-Id", self.headers.get("X-Request-Id", ""))
        if self.headers.get("Origin"):
            self.send_header("Access-Control-Allow-Origin", self.headers["Origin"])
        if status == 429 and self.include_retry_after:
            self.send_header("Retry-After", "60")
        self.end_headers()
        self.wfile.write(encoded)

    def log_message(self, _format: str, *_args: object) -> None:
        return


class RateLimitTrustLoadTest(unittest.TestCase):
    def setUp(self) -> None:
        _RateLimitHandler.requests = 0
        _RateLimitHandler.limit = 2
        _RateLimitHandler.forwarded_values = set()
        _RateLimitHandler.include_retry_after = True
        _RateLimitHandler.non_limited_status = 400
        self.server = ThreadingHTTPServer(("127.0.0.1", 0), _RateLimitHandler)
        self.thread = threading.Thread(target=self.server.serve_forever, daemon=True)
        self.thread.start()

    def tearDown(self) -> None:
        self.server.shutdown()
        self.server.server_close()
        self.thread.join(timeout=2)

    def args(self, *extra: str):
        port = self.server.server_address[1]
        return MODULE.parse_args([
            "--url-template", f"http://127.0.0.1:{port}/login",
            "--method", "POST",
            "--requests", "6",
            "--workers", "4",
            "--expected-limited", "4",
            "--expected-non-limited", "2",
            "--expected-non-limited-status", "400",
            "--expected-non-limited-code", "INVALID_TEST_INPUT",
            "--body", "{}",
            *extra,
        ])

    def test_runner_is_executable_and_asserts_exact_counts(self) -> None:
        self.assertTrue(SCRIPT.stat().st_mode & stat.S_IXUSR)

        limited, non_limited = MODULE.run(self.args("--rotate-x-forwarded-for"))

        self.assertEqual((4, 2), (limited, non_limited))
        self.assertEqual(6, len(_RateLimitHandler.forwarded_values))

    def test_runner_rejects_non_loopback_target_before_network(self) -> None:
        args = MODULE.parse_args([
            "--url-template", "https://api.example/login",
            "--method", "POST",
            "--requests", "1",
            "--expected-limited", "0",
            "--expected-non-limited", "1",
            "--expected-non-limited-status", "400",
        ])

        with self.assertRaisesRegex(ValueError, "HTTP loopback"):
            MODULE.validate_args(args)

    def test_runner_bounds_local_concurrency(self) -> None:
        args = self.args()
        args.workers = 65

        with self.assertRaisesRegex(ValueError, "between 1 and 64"):
            MODULE.validate_args(args)

    def test_runner_fails_when_retry_after_is_missing(self) -> None:
        _RateLimitHandler.include_retry_after = False

        with self.assertRaisesRegex(AssertionError, "Retry-After"):
            MODULE.run(self.args())

    def test_runner_rejects_server_errors_in_admitted_responses(self) -> None:
        _RateLimitHandler.non_limited_status = 500

        with self.assertRaisesRegex(AssertionError, "unexpected non-limited status"):
            MODULE.run(self.args())

    def test_url_template_can_vary_synthetic_token_without_changing_contract(self) -> None:
        port = self.server.server_address[1]
        args = MODULE.parse_args([
            "--url-template",
            f"http://127.0.0.1:{port}/driver-onboarding/invites/token-{{index}}/preview",
            "--method", "GET",
            "--requests", "3",
            "--workers", "1",
            "--expected-limited", "1",
            "--expected-non-limited", "2",
            "--expected-non-limited-status", "400",
            "--expected-non-limited-code", "INVALID_TEST_INPUT",
        ])

        self.assertEqual((1, 2), MODULE.run(args))


if __name__ == "__main__":
    unittest.main()
