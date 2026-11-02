"""Run through stdin inside the prepared local container; emits no secrets/GPS.

Requires /data/smoke-credential.json created by the private CLI, never in image.
"""
import http.client
import json
import time


def main():
    with open("/data/smoke-credential.json", encoding="utf-8") as source:
        credential = json.load(source)
    token = credential["token"]
    # Wait only for local worker startup; this is not a retry of a GPS request.
    deadline = time.monotonic() + 8
    while True:
        probe = http.client.HTTPConnection("127.0.0.1", 18765, timeout=1)
        try:
            probe.request("GET", "/driver-app/v1/status", headers={"Host": "127.0.0.1:18765"})
            assert probe.getresponse().status == 401, "AUTH_GUARD"
            break
        except OSError:
            if time.monotonic() >= deadline:
                raise
            time.sleep(0.1)
        finally:
            probe.close()
    def request(method, path, body=None):
        connection = http.client.HTTPConnection("127.0.0.1", 18765, timeout=8)
        headers = {"Host": "gps-spike.vanrides.matheusduarte.dev.br",
                   "Authorization": "Bearer " + token, "Content-Type": "application/json"}
        raw = None if body is None else json.dumps(body, separators=(",", ":")).encode()
        try:
            connection.request(method, path, raw, headers)
            response = connection.getresponse()
            data = response.read(32769)
            assert response.status == 200, "HTTP_STATUS"
            assert len(data) <= 32768 and token.encode() not in data, "RESPONSE_BOUND"
            assert response.getheader("Content-Type") == "application/json", "CONTENT_TYPE"
            return json.loads(data)
        finally:
            connection.close()
    status = request("GET", "/driver-app/v1/status")
    assert status["session"] is None, "STATUS_MUST_NOT_START"
    activate = "/driver-app/v1/start-intents/%s/activate" % credential["startIntentId"]
    control = request("POST", activate, {"schemaVersion": 1})
    assert control["action"] == "CONTINUE", "ACTIVATE"
    session = "/driver-app/v1/tracking-sessions/%s" % control["sessionId"]
    now = time.time_ns() // 1000000
    body = {"schemaVersion": 1, "sessionId": control["sessionId"], "generation": control["generation"],
            "batchId": "synthetic_http_smoke", "deviceTime": now,
            "samples": [{"sequence": 1, "capturedAt": now, "latitude": 0.0, "longitude": 0.0, "accuracyM": 5}]}
    ack = request("POST", session + "/samples", body)
    assert ack["accepted"] == [1] and ack["gpsState"] == "FRESH", "DURABLE_ACK"
    ack = request("POST", session + "/samples", body)
    assert ack["duplicate"] == [1] and ack["accepted"] == [], "REPLAY"
    stopped = request("POST", session + "/state", {"schemaVersion": 1, "generation": control["generation"],
        "state": "STOPPED", "reason": "USER_STOP", "stopRequestedAt": now, "stoppedAt": now})
    assert stopped["action"] == "STOP", "STOP"
    assert request("POST", activate, {"schemaVersion": 1})["action"] == "STOP", "CURRENT_CONTROL"
    assert request("POST", session + "/samples", body)["action"] == "STOP", "CURRENT_REPLAY"
    assert request("GET", "/driver-app/v1/status")["session"]["state"] == "STOPPED", "STOP_ACK_STATE"
    print('{"smoke":"PASS","transport":"HTTP_GUNICORN","accepted":1,"duplicate":1,"state":"STOPPED"}')


if __name__ == "__main__":
    try:
        main()
    except Exception:
        print('{"smoke":"FAIL"}')
        raise SystemExit(1)
