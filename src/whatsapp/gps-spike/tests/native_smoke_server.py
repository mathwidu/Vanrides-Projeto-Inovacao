"""Loopback-only native integration harness, excluded from the runtime image.

Inject one deterministic STOP fault; never expose an administrative HTTP route.
Only the mode and aggregate counters are printed, never request data.
"""
import argparse
import io
import json
from pathlib import Path
import sys
from wsgiref.simple_server import make_server

sys.path.insert(0, str(Path(__file__).resolve().parents[1]))
from receiver.cli import QuietHandler, private_path
from receiver.service import Receiver
from receiver.store import Store
from receiver.web import Application


class StopFault:
    def __init__(self, app, mode):
        self.app = app
        self.mode = mode
        self.stop_requests = 0
        self.injected = 0

    def __call__(self, environ, start_response):
        stop = False
        if (environ.get("REQUEST_METHOD") == "POST"
                and environ.get("PATH_INFO", "").endswith("/state")):
            try:
                length = int(environ.get("CONTENT_LENGTH", "0"))
                if 0 < length <= 32768:
                    body = environ["wsgi.input"].read(length)
                    environ["wsgi.input"] = io.BytesIO(body)
                    stop = json.loads(body).get("state") == "STOPPED"
            except (ValueError, AttributeError):
                pass
        if stop:
            self.stop_requests += 1
        if not stop or self.mode == "none" or self.injected:
            return self.app(environ, start_response)
        self.injected += 1
        if self.mode == "stop-ack-loss":
            # Commit the real stop but replace its ACK, reproducing ambiguous delivery.
            list(self.app(environ, lambda *args: None))
        if self.mode == "stop-invalid-response":
            status, content_type, payload = "200 OK", "text/html", b"invalid test response"
        else:
            status, content_type, payload = "503 Service Unavailable", "application/json", b'{"code":"TEST_TRANSIENT"}'
        start_response(status, [("Content-Type", content_type), ("Content-Length", str(len(payload)))])
        print(json.dumps({"fault": self.mode, "injected": self.injected}), flush=True)
        return [payload]


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--db", required=True)
    parser.add_argument("--mode", choices=("none", "stop-request-loss", "stop-ack-loss", "stop-invalid-response"), default="none")
    args = parser.parse_args()
    receiver = Receiver(Store(private_path(args.db)))
    app = StopFault(Application(receiver), args.mode)
    try:
        with make_server("127.0.0.1", 18765, app, handler_class=QuietHandler) as server:
            print(json.dumps({"ready": True, "mode": args.mode}), flush=True)
            server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        receiver.close()
        print(json.dumps({"mode": args.mode, "stopRequests": app.stop_requests, "injected": app.injected}), flush=True)


if __name__ == "__main__":
    main()
