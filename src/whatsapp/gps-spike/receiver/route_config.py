"""One immutable, private operator route. Never a GPS sample store or API."""
from dataclasses import dataclass
import os
from pathlib import Path
import stat

from .approach import Stop
from . import validation as v

ROUTE_FILE = Path("/data/route-v1.json")
MAX_ROUTE_BYTES = 16384


class RouteConfigError(ValueError):
    def __init__(self):
        # Neither JSON/parser errors nor filesystem paths may escape startup.
        super().__init__("INVALID_PRIVATE_ROUTE")


@dataclass(frozen=True, repr=False)
class Route:
    route_id: str
    stops: tuple

    def __post_init__(self):
        try:
            v.opaque(self.route_id)
            if type(self.stops) not in (tuple, list) or not 1 <= len(self.stops) <= 30:
                raise RouteConfigError()
            keys, points, frozen = set(), set(), []
            for index, stop in enumerate(self.stops):
                if type(stop) is not Stop:
                    raise RouteConfigError()
                v.opaque(stop.key)
                v.number(stop.latitude, -90, 90)
                v.number(stop.longitude, -180, 180)
                if type(stop.final) is not bool or stop.final != (index == len(self.stops) - 1):
                    raise RouteConfigError()
                point = (stop.latitude, stop.longitude)
                if stop.key in keys or point in points:
                    raise RouteConfigError()
                keys.add(stop.key)
                points.add(point)
                # Copy even injected objects/lists. Callers cannot mutate a route
                # by later editing the original collection used in a test.
                frozen.append(Stop(stop.key, stop.latitude, stop.longitude, stop.final))
            object.__setattr__(self, "stops", tuple(frozen))
        except Exception:
            raise RouteConfigError() from None

    def summary(self):
        return {"routeId": self.route_id, "stops": len(self.stops)}


def decode_route(raw):
    try:
        if len(raw) > MAX_ROUTE_BYTES:
            raise RouteConfigError()
        value = v.decode(raw)
        v.fields(value, ("schemaVersion", "routeId", "stops"))
        rows = value["stops"]
        if type(rows) is not list or not 1 <= len(rows) <= 30:
            raise RouteConfigError()
        stops = []
        for row in rows:
            v.fields(row, ("id", "latitude", "longitude", "final"))
            stops.append(Stop(row["id"], row["latitude"], row["longitude"], row["final"]))
        return Route(value["routeId"], stops)
    except Exception:
        raise RouteConfigError() from None


def load_route(path=ROUTE_FILE, missing_ok=False):
    """Open only the explicitly selected file, with bounded nonblocking reads.

    Missing at process startup may disable the hypothesis; invalid/inaccessible
    files must fail startup. No directory traversal, discovery or route reload.
    """
    try:
        if not hasattr(os, "O_NOFOLLOW"):
            raise RouteConfigError()
        try:
            fd = os.open(os.fspath(path), os.O_RDONLY | os.O_NOFOLLOW |
                         getattr(os, "O_NONBLOCK", 0) | getattr(os, "O_CLOEXEC", 0))
        except FileNotFoundError:
            if missing_ok:
                return None
            raise RouteConfigError() from None
        with os.fdopen(fd, "rb") as source:
            before = os.fstat(source.fileno())
            if (not stat.S_ISREG(before.st_mode) or stat.S_IMODE(before.st_mode) != 0o600
                    or before.st_uid != os.getuid() or not 1 <= before.st_size <= MAX_ROUTE_BYTES):
                raise RouteConfigError()
            raw = source.read(MAX_ROUTE_BYTES + 1)
            after = os.fstat(source.fileno())
            if (before.st_size, before.st_mtime_ns, before.st_ctime_ns) != (after.st_size, after.st_mtime_ns, after.st_ctime_ns):
                raise RouteConfigError()
        return decode_route(raw)
    except Exception:
        raise RouteConfigError() from None
