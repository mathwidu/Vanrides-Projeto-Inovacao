"""Pure, conservative v1 hypothesis with an explicitly supplied route.

Three approaching fixes, heading when supplied, bounded gaps and plausible
movement are required. Events describe this hypothesis, never an ETA promise.
"""
from dataclasses import dataclass, field
import math


def distance(a, b):
    lat1, lat2 = math.radians(a[0]), math.radians(b[0])
    dlat, dlon = lat2 - lat1, math.radians(b[1] - a[1])
    h = math.sin(dlat / 2) ** 2 + math.cos(lat1) * math.cos(lat2) * math.sin(dlon / 2) ** 2
    return 6371000 * 2 * math.asin(min(1, math.sqrt(h)))


def bearing(a, b):
    p1, p2 = math.radians(a[0]), math.radians(b[0])
    dl = math.radians(b[1] - a[1])
    return math.degrees(math.atan2(math.sin(dl) * math.cos(p2),
        math.cos(p1) * math.sin(p2) - math.sin(p1) * math.cos(p2) * math.cos(dl))) % 360


@dataclass(frozen=True)
class Stop:
    # Field coordinates are supplied privately; only synthetic ones live in Git.
    key: str
    latitude: float
    longitude: float
    final: bool = False


@dataclass
class Progress:
    index: int = 0
    window: list = field(default_factory=list)
    approached: bool = False
    nearest: float = float("inf")
    entered_pass: bool = False
    dwell_since: object = None
    last: object = None
    finished: bool = False


SYNTHETIC_ROUTE = (Stop("synthetic_stop", 0.0, 0.0),
                   Stop("synthetic_final", 0.0, 0.02, True))


def advance(progress, fix, stops):
    """Mutates RAM-only progress; return unique event types for the current stop.

Caller guarantees freshness/quality and strictly increasing capturedAt. No
wall-clock tick or heartbeat can create a PASS/FINAL event.
"""
    if progress.finished or not stops:
        return []
    stop = stops[progress.index]
    point = (fix["latitude"], fix["longitude"])
    target = (stop.latitude, stop.longitude)
    d = distance(point, target)
    last = progress.last
    if last:
        elapsed = (fix["capturedAt"] - last["capturedAt"]) / 1000
        movement = distance(point, (last["latitude"], last["longitude"]))
        # A gap or teleport breaks the chain instead of manufacturing progress.
        if elapsed <= 0 or elapsed > 20 or movement / elapsed > 45:
            progress.window.clear()
            progress.approached = False
            progress.dwell_since = None
            progress.entered_pass = False
            progress.nearest = float("inf")
    progress.last = dict(fix)
    heading_ok = "headingDeg" not in fix or abs((fix["headingDeg"] - bearing(point, target) + 180) % 360 - 180) <= 60
    progress.window.append((fix["capturedAt"], d, heading_ok))
    progress.window[:] = progress.window[-3:]
    events = []
    w = progress.window
    if (not progress.approached and len(w) == 3 and all(row[1] <= 800 and row[2] for row in w)
            and w[0][1] - w[-1][1] >= 15 and w[0][1] >= w[1][1] >= w[2][1]):
        progress.approached = True
        events.append((stop.key, "APPROACH_V1"))
    if not progress.approached:
        return events
    progress.nearest = min(progress.nearest, d)
    if d <= 80:
        progress.entered_pass = True
    if stop.final:
        # Missing speed is insufficient to infer stopped. Gaps reset dwell.
        slow = fix.get("speedMps", 151) <= 1.5
        if d <= 100 and slow:
            if progress.dwell_since is None:
                progress.dwell_since = fix["capturedAt"]
            if fix["capturedAt"] - progress.dwell_since >= 90000:
                progress.finished = True
                events.append((stop.key, "FINAL_DWELL_V1"))
        else:
            progress.dwell_since = None
    elif progress.entered_pass and d > 100 and d - progress.nearest >= 50:
        events.append((stop.key, "PASS_V1"))
        progress.index += 1
        progress.window.clear()
        progress.approached = False
        progress.nearest = float("inf")
        progress.entered_pass = False
        progress.dwell_since = None
        progress.last = None
    return events
