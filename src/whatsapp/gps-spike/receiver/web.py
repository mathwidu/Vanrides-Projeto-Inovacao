"""Small WSGI adapter: bounded authenticated reads and sanitized JSON errors."""
import json
from http import HTTPStatus

from .service import Receiver
from .route_config import load_route
from .store import Store
from .validation import MAX_BODY, ProtocolError, fail

ALLOWED_HOSTS = {"gps-spike.vanrides.matheusduarte.dev.br",
                 "gps-spike.vanrides.matheusduarte.dev.br:443",
                 "127.0.0.1:18765", "10.0.2.2:18765"}


class Application:
    def __init__(self, receiver):
        self.receiver = receiver

    def __call__(self, environ, start_response):
        def read_body():
            # This closure is invoked after authentication, outside the protocol
            # lock/transaction; auth is revalidated after the bounded read.
            if environ.get("HTTP_HOST", "") not in ALLOWED_HOSTS:
                fail("INVALID_HOST")
            if environ.get("QUERY_STRING") or environ.get("HTTP_CONTENT_ENCODING"):
                fail("INVALID_REQUEST")
            raw_length = environ.get("CONTENT_LENGTH", "")
            if raw_length == "" and environ.get("REQUEST_METHOD") == "GET":
                length = 0
            elif not raw_length.isascii() or not raw_length.isdigit() or len(raw_length) > 10:
                fail("INVALID_CONTENT_LENGTH")
            else:
                length = int(raw_length)
            if length > MAX_BODY:
                fail("BODY_TOO_LARGE", 413)
            if environ.get("HTTP_TRANSFER_ENCODING"):
                fail("INVALID_REQUEST")
            raw = environ["wsgi.input"].read(length)
            if len(raw) != length:
                fail("INVALID_CONTENT_LENGTH")
            return raw

        try:
            result = self.receiver.handle(environ.get("REQUEST_METHOD", ""),
                environ.get("PATH_INFO", ""), environ.get("HTTP_AUTHORIZATION"),
                read_body, environ.get("CONTENT_TYPE", ""))
            status = 200
        except ProtocolError as error:
            status, result = error.status, {"code": error.code}
        except Exception:
            status, result = 503, {"code": "UNAVAILABLE"}
        payload = json.dumps(result, separators=(",", ":"), allow_nan=False).encode("utf-8")
        start_response("%s %s" % (status, HTTPStatus(status).phrase), [
            ("Content-Type", "application/json"), ("Content-Length", str(len(payload))),
            ("Cache-Control", "no-store"), ("X-Content-Type-Options", "nosniff")])
        return [payload]


def create_app():
    # No environment variable or image layer ever carries a credential.
    # Load once before constructing/starting the receiver; invalid is fail-closed.
    route = load_route(missing_ok=True)
    return Application(Receiver(Store("/data/receiver.sqlite"), route=route))
