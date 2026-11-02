#!/usr/bin/env python3
"""Inspect actual APK DEX/Mach-O artifacts; never execute an app or print binary strings."""
import argparse
import hashlib
import json
from pathlib import Path
import plistlib
import struct
import zipfile

SPIKE_HOST = "gps-spike.vanrides.matheusduarte.dev.br"
SPIKE_CLASSES = (
    "Lcom/example/vanrides_frontend_dev/driver/DriverSpikeHttpClient;",
    "Lcom/example/vanrides_frontend_dev/driver/DriverSpikeContract;",
)
SPIKE_KEYCHAIN = "dev.matheusduarte.vanrides.driver.gps-spike.v1"


class IsolationFailure(Exception):
    pass


def require(value, code):
    if not value:
        raise IsolationFailure(code)


def digest(path):
    value = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(262144), b""):
            value.update(chunk)
    return value.hexdigest()


def dex_strings(data):
    require(len(data) >= 112 and data[:4] == b"dex\n" and data[7] == 0, "INVALID_DEX_HEADER")
    require(struct.unpack_from("<I", data, 32)[0] == len(data), "INVALID_DEX_LENGTH")
    require(struct.unpack_from("<I", data, 40)[0] == 0x12345678, "UNSUPPORTED_DEX_ENDIAN")
    count, offset = struct.unpack_from("<II", data, 56)
    require(count <= 2_000_000 and offset >= 112 and offset + count * 4 <= len(data), "INVALID_DEX_STRINGS")
    for index in range(count):
        cursor = struct.unpack_from("<I", data, offset + index * 4)[0]
        require(cursor < len(data), "INVALID_DEX_STRING_OFFSET")
        # UTF16 length is ULEB128. Decode only its framing; policy needles are ASCII.
        for _ in range(5):
            require(cursor < len(data), "INVALID_DEX_ULEB")
            byte = data[cursor]
            cursor += 1
            if byte < 128:
                break
        else:
            raise IsolationFailure("INVALID_DEX_ULEB")
        end = data.find(b"\0", cursor, min(len(data), cursor + 1_048_576))
        require(end >= cursor, "INVALID_DEX_STRING")
        yield data[cursor:end]


def inspect_apk(path, expected="mvp"):
    seen = set()
    dex_count = 0
    needles = {value.encode() for value in (SPIKE_HOST, *SPIKE_CLASSES)}
    with zipfile.ZipFile(path) as archive:
        require("AndroidManifest.xml" in archive.namelist(), "APK_MANIFEST_MISSING")
        for entry in archive.infolist():
            name = entry.filename
            if not (name.startswith("classes") and name.endswith(".dex") and "/" not in name):
                continue
            require(0 < entry.file_size <= 128 * 1024 * 1024, "DEX_SIZE_LIMIT")
            dex_count += 1
            for value in dex_strings(archive.read(entry)):
                for needle in needles:
                    if needle in value:
                        seen.add(needle)
    require(dex_count > 0, "APK_DEX_MISSING")
    if expected == "mvp":
        require(not seen, "MVP_CONTAINS_SPIKE030_CODE_OR_ORIGIN")
    else:
        require(seen == needles, "SPIKE030_ARTIFACT_INCOMPLETE")
    return {"status": "PASS", "kind": "apk", "expected": expected, "dexCount": dex_count,
            "artifactSha256": digest(path), "executedApp": False}


MACHO_MAGIC = {b"\xcf\xfa\xed\xfe", b"\xfe\xed\xfa\xcf", b"\xce\xfa\xed\xfe", b"\xfe\xed\xfa\xce",
               b"\xca\xfe\xba\xbe", b"\xbe\xba\xfe\xca", b"\xca\xfe\xba\xbf", b"\xbf\xba\xfe\xca"}


def inspect_ios_app(path, expected="mvp"):
    with (path / "Info.plist").open("rb") as stream:
        info = plistlib.load(stream)
    name = info.get("CFBundleExecutable")
    require(isinstance(name, str) and name and Path(name).name == name, "IOS_EXECUTABLE_INVALID")
    executable = path / name
    candidates = [executable]
    frameworks = path / "Frameworks"
    if frameworks.is_dir():
        candidates += [item for item in frameworks.rglob("*") if item.is_file()]
    found = set()
    count = 0
    needles = {value.encode() for value in (SPIKE_HOST, SPIKE_KEYCHAIN)}
    for item in candidates:
        with item.open("rb") as stream:
            if stream.read(4) not in MACHO_MAGIC:
                continue
            count += 1
            stream.seek(0)
            tail = b""
            for chunk in iter(lambda: stream.read(262144), b""):
                data = tail + chunk
                found.update(needle for needle in needles if needle in data)
                tail = data[-max(map(len, needles)):]
    require(count > 0, "IOS_MACHO_MISSING")
    if expected == "mvp":
        require(not found, "MVP_IOS_CONTAINS_SPIKE030_ORIGIN_OR_STORE")
    else:
        require(found == needles, "SPIKE030_IOS_ARTIFACT_INCOMPLETE")
    return {"status": "PASS", "kind": "ios-app", "expected": expected, "machOCount": count,
            "artifactSha256": digest(executable), "executedApp": False}


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    artifact = parser.add_mutually_exclusive_group(required=True)
    artifact.add_argument("--apk", type=Path)
    artifact.add_argument("--ios-app", type=Path)
    parser.add_argument("--expect", choices=("mvp", "spike"), default="mvp")
    args = parser.parse_args()
    try:
        result = inspect_apk(args.apk, args.expect) if args.apk else inspect_ios_app(args.ios_app, args.expect)
    except IsolationFailure as error:
        print(json.dumps({"status": "FAIL", "reason": str(error), "executedApp": False}))
        return 1
    except (OSError, ValueError, zipfile.BadZipFile, plistlib.InvalidFileException):
        print(json.dumps({"status": "FAIL", "reason": "ARTIFACT_IO_FAILURE", "executedApp": False}))
        return 1
    print(json.dumps(result))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
