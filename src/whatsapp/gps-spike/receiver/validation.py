"""Strict wire validation. Errors contain stable codes only."""
import hashlib
import json
import math
import re

MAX_BODY = 32768
MAX_INT = 2 ** 63 - 1
ID = re.compile(r"[A-Za-z0-9_-]{1,80}\Z", re.ASCII)


class ProtocolError(Exception):
    def __init__(self, status, code):
        self.status, self.code = status, code
        super().__init__(code)


def fail(code="INVALID_CONTRACT", status=400):
    raise ProtocolError(status, code)


def integer(value, minimum=0):
    if type(value) is not int or not minimum <= value <= MAX_INT:
        fail()
    return value


def opaque(value):
    if not isinstance(value, str) or not ID.fullmatch(value):
        fail()
    return value


def number(value, minimum, maximum, exclusive=False):
    if type(value) not in (int, float):
        fail()
    try:
        finite = math.isfinite(value)
    except OverflowError:
        finite = False
    if not finite:
        fail()
    if value < minimum or (value >= maximum if exclusive else value > maximum):
        fail()


def fields(value, required, optional=()):
    if type(value) is not dict or not set(required) <= value.keys():
        fail()
    if value.keys() - set(required) - set(optional):
        fail("UNKNOWN_FIELD")


def _unique(pairs):
    value = {}
    for key, item in pairs:
        if key in value:
            fail("DUPLICATE_FIELD")
        value[key] = item
    return value


def decode(body):
    if len(body) > MAX_BODY:
        fail("BODY_TOO_LARGE", 413)
    try:
        value = json.loads(body.decode("utf-8"), object_pairs_hook=_unique,
                           parse_constant=lambda _: fail())
    except (UnicodeError, ValueError, RecursionError):
        fail("INVALID_JSON")
    if type(value) is not dict:
        fail()
    if type(value.get("schemaVersion")) is not int or value["schemaVersion"] != 1:
        fail("UNSUPPORTED_SCHEMA")
    return value


def digest(value):
    canonical = json.dumps(value, sort_keys=True, separators=(",", ":"),
                           ensure_ascii=True, allow_nan=False).encode("ascii")
    return hashlib.sha256(canonical).hexdigest()


def samples(value):
    fields(value, ("schemaVersion", "sessionId", "generation", "batchId", "deviceTime", "samples"))
    opaque(value["sessionId"])
    opaque(value["batchId"])
    integer(value["generation"], 1)
    integer(value["deviceTime"])
    rows = value["samples"]
    if type(rows) is not list or not 1 <= len(rows) <= 30:
        fail("SAMPLE_LIMIT")
    sequences = set()
    for row in rows:
        fields(row, ("sequence", "capturedAt", "latitude", "longitude", "accuracyM"),
               ("speedMps", "headingDeg"))
        sequence = integer(row["sequence"], 1)
        if sequence in sequences:
            fail("DUPLICATE_SEQUENCE")
        sequences.add(sequence)
        integer(row["capturedAt"])
        number(row["latitude"], -90, 90)
        number(row["longitude"], -180, 180)
        number(row["accuracyM"], 0, 10000)
        if "speedMps" in row:
            number(row["speedMps"], 0, 150)
        if "headingDeg" in row:
            number(row["headingDeg"], 0, 360, True)


def state(value):
    fields(value, ("schemaVersion", "generation", "state"),
           ("reason", "stopRequestedAt", "stoppedAt"))
    integer(value["generation"], 1)
    if value["state"] not in ("RUNNING", "STOPPED"):
        fail()
    if "reason" in value:
        if not isinstance(value["reason"], str) or not re.fullmatch(r"[A-Z0-9_]{1,64}", value["reason"], re.ASCII):
            fail()
    for key in ("stopRequestedAt", "stoppedAt"):
        if key in value:
            integer(value[key])
    if value["state"] == "RUNNING" and set(value) & {"stopRequestedAt", "stoppedAt"}:
        fail()
    if ("stoppedAt" in value and "stopRequestedAt" in value
            and value["stopRequestedAt"] > value["stoppedAt"]):
        fail()
