"""Disposable local gateway smoke, invoked via container stdin; safe output only.

No remote targets, redirects, credentials in arguments/env, or request logging.
The private bundle is provisioned inside a dedicated Docker volume by the CLI.
"""
import http.client
import json
import re
import time

from receiver.cli import SafeParser, read_private

HOST = "gps-spike.vanrides.matheusduarte.dev.br"
STATUS = "/driver-app/v1/status"


class Smoke:
    phase = "INITIALIZE"

    def __init__(self, target, port, bundle):
        self.target, self.port = target, port
        self.token = bundle["token"]
        self.bundle = bundle

    def response(self, response, expected):
        raw = response.read(32769)
        assert response.status == expected, "HTTP_STATUS_EXPECTED_%d_GOT_%d" % (expected, response.status)
        assert len(raw) <= 32768 and self.token.encode() not in raw, "RESPONSE_BOUND"
        assert response.getheader("Content-Type", "").split(";", 1)[0] == "application/json", "CONTENT_TYPE"
        assert "no-store" in response.getheader("Cache-Control", ""), "CACHE_CONTROL"
        assert response.getheader("X-Content-Type-Options") == "nosniff", "NOSNIFF"
        assert response.getheader("Location") is None, "NO_REDIRECT"
        result = json.loads(raw)
        if expected != 200:
            assert set(result) == {"code"}, "ERROR_SHAPE"
        return result

    def request(self, method, path, body=None, expected=200, authenticated=True, host=HOST):
        # Remain below the configured shared proxy-hop request limit.
        time.sleep(0.25)
        connection = http.client.HTTPConnection(self.target, self.port, timeout=12)
        headers = {"Host": host, "Content-Type": "application/json"}
        if authenticated:
            headers["Authorization"] = "Bearer " + self.token
        raw = body if isinstance(body, bytes) else None if body is None else json.dumps(body, separators=(",", ":")).encode()
        try:
            connection.request(method, path, raw, headers)
            return self.response(connection.getresponse(), expected)
        finally:
            connection.close()

    def ready(self):
        deadline = time.monotonic() + 20
        while True:
            try:
                self.request("GET", STATUS, expected=401, authenticated=False)
                return
            except (OSError, http.client.HTTPException, AssertionError):
                if time.monotonic() >= deadline:
                    raise RuntimeError("GATEWAY_NOT_READY") from None
                time.sleep(0.25)

    def lifecycle(self):
        Smoke.phase = "READY"
        self.ready()
        Smoke.phase = "LIFECYCLE"
        assert self.request("GET", STATUS)["session"] is None, "STATUS_MUST_NOT_START"
        assert self.request("GET", STATUS, expected=400, host="unexpected.invalid")["code"] == "INVALID_REQUEST", "HOST_GUARD"
        self.request("DELETE", STATUS, expected=400)
        self.request("GET", "/", expected=404, authenticated=False)
        self.request("GET", "/driver-app/v1", expected=404, authenticated=False)
        self.request("POST", "/driver-app/v1/unrecognised", b"x" * 32769, expected=413)
        activate = "/driver-app/v1/start-intents/%s/activate" % self.bundle["startIntentId"]
        control = self.request("POST", activate, {"schemaVersion": 1})
        assert control["action"] == "CONTINUE", "ACTIVATE"
        session = "/driver-app/v1/tracking-sessions/%s" % control["sessionId"]
        now = time.time_ns() // 1000000
        body = {"schemaVersion": 1, "sessionId": control["sessionId"], "generation": control["generation"],
                "batchId": "synthetic_gateway_smoke", "deviceTime": now,
                "samples": [{"sequence": 1, "capturedAt": now, "latitude": 0.0, "longitude": 0.0, "accuracyM": 5}]}
        ack = self.request("POST", session + "/samples", body)
        assert ack["accepted"] == [1] and ack["gpsState"] == "FRESH", "DURABLE_ACK"
        ack = self.request("POST", session + "/samples", body)
        assert ack["duplicate"] == [1] and ack["accepted"] == [], "REPLAY"
        stopped = self.request("POST", session + "/state", {"schemaVersion": 1,
            "generation": control["generation"], "state": "STOPPED", "reason": "USER_STOP",
            "stopRequestedAt": now, "stoppedAt": now})
        assert stopped["action"] == "STOP", "STOP"
        assert self.request("POST", activate, {"schemaVersion": 1})["action"] == "STOP", "CURRENT_CONTROL"
        assert self.request("POST", session + "/samples", body)["action"] == "STOP", "CURRENT_REPLAY"
        assert self.request("GET", STATUS)["session"]["state"] == "STOPPED", "STOP_ACK_STATE"
        idle_timeout = self.slow_body()
        self.rate_limit()
        print(json.dumps({"smoke": "PASS", "transport": "HTTP_NGINX_GUNICORN", "accepted": 1,
            "duplicate": 1, "state": "STOPPED", "hostGuard": True, "bodyLimit": True,
            "idleTimeout": idle_timeout, "rateLimit": True}))

    def slow_body(self):
        Smoke.phase = "BODY_IDLE_TIMEOUT"
        # Body buffering must keep an incomplete body away from the receiver.
        connection = http.client.HTTPConnection(self.target, self.port, timeout=12)
        try:
            connection.putrequest("POST", "/driver-app/v1/unrecognised", skip_host=True)
            connection.putheader("Host", HOST)
            connection.putheader("Authorization", "Bearer " + self.token)
            connection.putheader("Content-Type", "application/json")
            connection.putheader("Content-Length", "16")
            connection.endheaders()
            connection.send(b"{")
            start = time.monotonic()
            try:
                result = self.response(connection.getresponse(), 503)
                assert result["code"] == "REQUEST_TIMEOUT", "BODY_IDLE_TIMEOUT"
                outcome = "HTTP_503"
            except http.client.RemoteDisconnected:
                # nginx may terminate an incomplete request without sending an
                # HTTP response when its connection timedout flag is set.
                outcome = "CONNECTION_CLOSED"
            assert 6 <= time.monotonic() - start <= 12, "BODY_TIMEOUT_BOUND"
            return outcome
        finally:
            connection.close()

    def rate_limit(self):
        Smoke.phase = "RATE_LIMIT"
        # Unauthenticated fixed route: avoid consuming the identity's own rate.
        limited = False
        for unused in range(40):
            connection = http.client.HTTPConnection(self.target, self.port, timeout=3)
            try:
                connection.request("GET", STATUS, headers={"Host": HOST})
                response = connection.getresponse()
                code = response.status
                assert code in (401, 429), "RATE_STATUS"
                result = self.response(response, code)
                if code == 429:
                    assert result["code"] == "RATE_LIMITED", "RATE_ERROR"
                    limited = True
                    break
            finally:
                connection.close()
        assert limited, "GATEWAY_RATE_LIMIT"

    def restored(self):
        Smoke.phase = "RESTORE"
        self.ready()
        result = self.request("GET", STATUS, expected=403)
        assert result["code"] == "CREDENTIAL_REVOKED", "RESTORED_CREDENTIAL_REVOKED"
        print('{"smoke":"PASS","transport":"HTTP_NGINX_GUNICORN","restore":"CREDENTIALS_REVOKED"}')


def main():
    if not __debug__:
        raise ValueError("OPTIMIZED_TESTS_FORBIDDEN")
    parser = SafeParser(add_help=False)
    parser.add_argument("--connect", choices=("gateway", "127.0.0.1"), default="gateway")
    parser.add_argument("--port", type=int, choices=(8080,), default=8080)
    parser.add_argument("--credential-file", required=True)
    parser.add_argument("--mode", choices=("lifecycle", "restored"), default="lifecycle")
    args = parser.parse_args()
    smoke = Smoke(args.connect, args.port, read_private(args.credential_file))
    getattr(smoke, args.mode)()


if __name__ == "__main__":
    try:
        main()
    except Exception as error:
        # Emit only test-owned constant codes, never network/header/file errors.
        code = str(error) if isinstance(error, (AssertionError, RuntimeError)) else "UNAVAILABLE"
        if not re.fullmatch(r"[A-Z0-9_]{1,80}", code):
            code = "UNAVAILABLE"
        print(json.dumps({"smoke": "FAIL", "transport": "HTTP_NGINX_GUNICORN",
                          "code": code, "phase": Smoke.phase, "errorType": type(error).__name__}))
        raise SystemExit(1)
