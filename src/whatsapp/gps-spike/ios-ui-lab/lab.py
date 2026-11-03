#!/usr/bin/env python3
"""Prepare private one-shot UI runs and audit sentinel artifacts; never run devices."""
from __future__ import annotations

import argparse
import datetime as dt
import gzip
import hashlib
import json
import os
from pathlib import Path
import plistlib
import re
import stat
import subprocess
import sys
import uuid
from urllib.parse import urlsplit

HERE = Path(__file__).resolve().parent
DRIVER_ID = "dev.matheusduarte.vanrides.driver"
TARGET = "DriverGPSProofUITests"
SENTINEL = b"VRUILAB_SENTINEL_030_NON_SECRET_7F8C"
COMMANDS = ("inspect", "sentinel-pass", "sentinel-fail-intent", "sentinel-fail-credential",
            "configure", "start", "stop", "background")
CLEANUP_FLAGS = ("autWasRunning", "runnerForegroundConfirmed", "runnerUIApplicationActive",
                 "readabilityConfirmed", "probeChangeCountAdvanced", "clearChangeCountAdvanced",
                 "emptyAfterClear", "runnerActiveDuringClear", "cleared", "autForegroundRestored")


class LabError(Exception):
    """Only constant error codes may cross the CLI boundary."""


def source_digest() -> str:
    return hashlib.sha256((HERE / "DriverGPSProofUITests.swift").read_bytes()).hexdigest()


def outside_git(path: Path) -> Path:
    resolved = path.expanduser().resolve()
    if any((ancestor / ".git").exists() for ancestor in (resolved, *resolved.parents)):
        raise LabError("PRIVATE_PATH_MUST_BE_OUTSIDE_GIT")
    return resolved


def private_json(path: Path) -> dict:
    path = outside_git(path)
    if not path.is_file() or stat.S_IMODE(path.stat().st_mode) & 0o077:
        raise LabError("PRIVATE_FILE_REQUIRED_MODE_0600")
    try:
        value = json.loads(path.read_text())
    except (ValueError, UnicodeError):
        raise LabError("INVALID_PRIVATE_JSON") from None
    if not isinstance(value, dict):
        raise LabError("INVALID_PRIVATE_JSON")
    return value


def write_private(path: Path, data: bytes) -> None:
    path = outside_git(path)
    # Never overwrite a secret, a previous run, or a symlink.
    fd = os.open(path, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600)
    with os.fdopen(fd, "wb") as handle:
        handle.write(data)


def json_bytes(value: dict) -> bytes:
    return (json.dumps(value, indent=2, sort_keys=True) + "\n").encode()


def xcode_version() -> str:
    result = subprocess.run(["xcodebuild", "-version"], capture_output=True, check=False)
    if result.returncode:
        raise LabError("XCODE_VERSION_UNAVAILABLE")
    return result.stdout.decode().strip()


def runner_info(app: Path) -> tuple[str, str, str]:
    app = outside_git(app)
    if not app.is_dir() or app.suffix != ".app":
        raise LabError("RUNNER_APP_REQUIRED")
    try:
        info = plistlib.loads((app / "Info.plist").read_bytes())
        bundle_id = info["CFBundleIdentifier"]
    except (OSError, ValueError, KeyError):
        raise LabError("INVALID_RUNNER_INFO") from None
    if not isinstance(bundle_id, str) or not re.fullmatch(r"[A-Za-z0-9.-]+\.xctrunner", bundle_id):
        raise LabError("UITEST_RUNNER_BUNDLE_REQUIRED")
    tests = sorted(app.glob("PlugIns/*.xctest"))
    if len(tests) != 1 or tests[0].stem != TARGET:
        raise LabError("SINGLE_LAB_TEST_BUNDLE_REQUIRED")
    digest = hashlib.sha256()
    for item in sorted(app.rglob("*")):
        if item.is_symlink():
            digest.update(str(item.relative_to(app)).encode())
            digest.update(os.readlink(item).encode())
        elif item.is_file():
            digest.update(str(item.relative_to(app)).encode())
            digest.update(b"\0")
            with item.open("rb") as handle:
                for chunk in iter(lambda: handle.read(1024 * 1024), b""):
                    digest.update(chunk)
    relative_test = "__TESTHOST__/" + tests[0].relative_to(app).as_posix()
    return bundle_id, relative_test, digest.hexdigest()


def targets(document: dict) -> list[dict]:
    if "TestConfigurations" in document:
        configs = document["TestConfigurations"]
        if not isinstance(configs, list) or len(configs) != 1:
            raise LabError("SINGLE_TEST_CONFIGURATION_REQUIRED")
        found = configs[0].get("TestTargets", [])
    else:
        found = [value for key, value in document.items()
                 if not key.startswith("__") and isinstance(value, dict)]
    if not isinstance(found, list) or len(found) != 1 or not isinstance(found[0], dict):
        raise LabError("SINGLE_TEST_TARGET_REQUIRED")
    identity = {found[0].get(key) for key in ("BlueprintName", "ProductModuleName", "TargetName")}
    if TARGET not in identity:
        raise LabError("LAB_TEST_TARGET_REQUIRED")
    return found


def patch_run(document: dict, runner_id: str, test_path: str) -> dict:
    target = targets(document)[0]
    for key in ("TestBundlePath", "TestHostPath", "UITargetAppPath"):
        target.pop(key, None)
    target.update({
        "UseDestinationArtifacts": True,
        "TestHostBundleIdentifier": runner_id,
        "TestBundleDestinationRelativePath": test_path,
        "UITargetAppBundleIdentifier": DRIVER_ID,
        "DependentProductPaths": [],
        "SystemAttachmentLifetime": "keepNever",
        "UserAttachmentLifetime": "keepNever",
        "ParallelizationEnabled": False,
        "CodeCoverageEnabled": False,
        "GatherLocalizableStringsData": False,
        "EnvironmentVariables": {}, "CommandLineArguments": [],
        "UITargetAppEnvironmentVariables": {}, "UITargetAppCommandLineArguments": [],
    })
    if "CodeCoverageBuildableInfos" in document:
        document["CodeCoverageBuildableInfos"] = []
    return document


def verify_receipt(receipt: dict, runner_digest: str, version: str) -> None:
    try:
        approved = dt.datetime.fromisoformat(receipt["approvedAt"])
        age = dt.datetime.now(dt.timezone.utc) - approved
    except (KeyError, ValueError, TypeError):
        raise LabError("INVALID_PRIVACY_RECEIPT") from None
    if (receipt.get("schemaVersion") != 1 or receipt.get("approved") is not True
            or receipt.get("sourceDigest") != source_digest()
            or receipt.get("runnerDigest") != runner_digest
            or receipt.get("xcodeVersion") != version
            or not dt.timedelta(0) <= age <= dt.timedelta(hours=24)):
        raise LabError("PRIVACY_RECEIPT_EXPIRED_OR_MISMATCHED")


def make_fixture(command: str, supplied: dict, approved: bool, run_id: str) -> dict:
    fixture = {"schemaVersion": 1, "command": command, "runID": run_id,
               "sourceDigest": source_digest(), "privacyApproved": approved}
    nonempty = {key for key, value in supplied.items() if value is not None and key != "schemaVersion"}
    if command == "configure":
        # Do not accidentally carry the previous run's start code with a new token.
        if nonempty - {"baseURL", "credential", "backgroundSeconds"}:
            raise LabError("CONFIGURE_FIXTURE_HAS_UNRELATED_VALUES")
        base, credential = supplied.get("baseURL"), supplied.get("credential")
        if not isinstance(base, str) or not isinstance(credential, str):
            raise LabError("CONFIGURE_FIXTURE_REQUIRED")
        try:
            url = urlsplit(base)
            endpoint_ok = (url.scheme == "https" and url.hostname == "gps-spike.vanrides.matheusduarte.dev.br"
                           and url.port in (None, 443) and not url.username and not url.password
                           and url.path in ("", "/") and not url.query and not url.fragment)
        except ValueError:
            endpoint_ok = False
        if not endpoint_ok or not re.fullmatch(r"[\x21-\x7e]{32,512}", credential):
            raise LabError("INVALID_CONFIGURE_FIXTURE")
        fixture.update(baseURL=base, credential=credential)
    elif command == "start":
        if nonempty - {"intent", "backgroundSeconds"}:
            raise LabError("START_FIXTURE_HAS_UNRELATED_VALUES")
        intent = supplied.get("intent")
        if not isinstance(intent, str) or not re.fullmatch(r"[A-Za-z0-9_-]{1,80}", intent):
            raise LabError("START_FIXTURE_REQUIRED")
        fixture["intent"] = intent
    elif command == "background":
        if nonempty - {"backgroundSeconds"}:
            raise LabError("BACKGROUND_FIXTURE_HAS_UNRELATED_VALUES")
        seconds = supplied.get("backgroundSeconds", 30)
        if type(seconds) is not int or not 1 <= seconds <= 180:
            raise LabError("INVALID_BACKGROUND_DURATION")
        fixture["backgroundSeconds"] = seconds
    elif nonempty - {"backgroundSeconds"}:
        raise LabError("NON_SECRET_COMMAND_REFUSES_CREDENTIALS")
    if command in ("configure", "start") and not approved:
        raise LabError("PRIVACY_AUDIT_REQUIRED")
    return fixture


def prepare(args: argparse.Namespace) -> None:
    output = outside_git(args.output_dir)
    if output.exists():
        raise LabError("OUTPUT_DIRECTORY_MUST_BE_NEW")
    source = outside_git(args.source_xctestrun)
    runner_id, test_path, digest = runner_info(args.runner_app)
    try:
        document = plistlib.loads(source.read_bytes())
    except (OSError, ValueError):
        raise LabError("INVALID_SOURCE_XCTESTRUN") from None
    patched = patch_run(document, runner_id, test_path)
    version = xcode_version()
    approved = False
    if args.command in ("configure", "start"):
        if not args.privacy_receipt:
            raise LabError("PRIVACY_AUDIT_REQUIRED")
        verify_receipt(private_json(args.privacy_receipt), digest, version)
        approved = True
    supplied = private_json(args.fixture_input) if args.fixture_input else {}
    run_id = str(uuid.uuid4())
    fixture = make_fixture(args.command, supplied, approved, run_id)
    run_bytes = plistlib.dumps(patched)
    for secret_key in ("credential", "intent"):
        value = fixture.get(secret_key)
        if value and value.encode() in run_bytes:
            raise LabError("SECRET_FOUND_IN_XCTESTRUN")
    output.mkdir(parents=True, mode=0o700)
    output.chmod(0o700)
    write_private(output / "run.xctestrun", run_bytes)
    write_private(output / "command.json", json_bytes(fixture))
    write_private(output / "run-meta.json", json_bytes({
        "schemaVersion": 1, "runID": run_id, "command": args.command,
        "runnerBundleIdentifier": runner_id, "runnerDigest": digest,
        "sourceDigest": source_digest(), "xcodeVersion": version,
        "xctestrunDigest": hashlib.sha256(run_bytes).hexdigest(),
        "createdAt": dt.datetime.now(dt.timezone.utc).isoformat(),
    }))


def walk(value):
    yield value
    if isinstance(value, dict):
        for child in value.values():
            yield from walk(child)
    elif isinstance(value, list):
        for child in value:
            yield from walk(child)


def tool_json(arguments: list[str], destination: Path) -> dict:
    result = subprocess.run(["xcrun", "xcresulttool", *arguments], capture_output=True, check=False)
    write_private(destination, result.stdout + (b"\n" + result.stderr if result.stderr else b""))
    if result.returncode:
        raise LabError("XCRESULT_EXPORT_FAILED")
    try:
        return json.loads(result.stdout)
    except ValueError:
        raise LabError("XCRESULT_EXPORT_NOT_JSON") from None


def export_available_logs(bundle: Path, exports: Path) -> tuple[dict, dict]:
    availability = tool_json(["get", "content-availability", "--path", str(bundle)],
                             exports / "content-availability.json")
    if (not isinstance(availability, dict)
            or any(type(availability.get(key)) is not bool
                   for key in ("hasCoverage", "hasDiagnostics", "hasTestResults"))
            or availability["hasTestResults"] is not True):
        raise LabError("INVALID_CONTENT_AVAILABILITY")
    kinds = availability.get("logs")
    if (not isinstance(kinds, list) or any(not isinstance(kind, str) for kind in kinds)
            or len(kinds) != len(set(kinds)) or set(kinds) - {"action", "build", "console"}
            or "action" not in kinds):
        raise LabError("INVALID_LOG_AVAILABILITY")
    status = {kind: {"status": "EXPORTED"} for kind in kinds}
    for kind in kinds:
        tool_json(["get", "log", "--path", str(bundle), "--type", kind], exports / (kind + ".json"))
    if "console" not in kinds:
        # An absent console is accepted only with BOTH native availability
        # metadata and the exact CLI absence response. Every other error blocks.
        result = subprocess.run(["xcrun", "xcresulttool", "get", "log", "--path", str(bundle),
                                 "--type", "console"], capture_output=True, check=False)
        write_private(exports / "console-absence.log", result.stdout + result.stderr)
        confirmed = (result.returncode == 1 and result.stdout == b""
                     and result.stderr.strip() == b"Error: No console log available")
        status["console"] = {"status": "ABSENT_CONFIRMED" if confirmed else "ERROR",
                             "advertised": False, "exitCode": result.returncode,
                             "stdoutEmpty": result.stdout == b"", "exactAbsenceResponse": confirmed,
                             "evidenceFile": "console-absence.log"}
        write_private(exports / "log-availability.json", json_bytes(status))
        if not confirmed:
            raise LabError("CONSOLE_ABSENCE_NOT_CONFIRMED")
    else:
        write_private(exports / "log-availability.json", json_bytes(status))
    return availability, status


def export_diagnostics(bundle: Path, exports: Path, advertised: bool) -> dict:
    status = {"advertised": advertised, "status": "NOT_ADVERTISED", "fileCount": 0}
    if advertised:
        output = exports / "diagnostics"
        result = subprocess.run(["xcrun", "xcresulttool", "export", "diagnostics", "--path", str(bundle),
                                 "--output-path", str(output)], capture_output=True, check=False)
        write_private(exports / "diagnostics-export.log", result.stdout + result.stderr)
        if result.returncode:
            raise LabError("DIAGNOSTICS_EXPORT_FAILED")
        files = [path for path in output.rglob("*") if path.is_file()] if output.is_dir() else []
        if not files:
            raise LabError("ADVERTISED_DIAGNOSTICS_UNAVAILABLE")
        if any(path.suffix.lower() in {".zip", ".tar", ".tgz", ".xz", ".bz2", ".zst"} for path in files):
            raise LabError("UNSUPPORTED_COMPRESSED_DIAGNOSTIC")
        status.update(status="EXPORTED", fileCount=len(files))
    write_private(exports / "diagnostics-availability.json", json_bytes(status))
    return status


def contains_marker(path: Path) -> bool:
    def scan(handle) -> bool:
        tail = b""
        for chunk in iter(lambda: handle.read(64 * 1024), b""):
            if SENTINEL in tail + chunk:
                return True
            tail = chunk[-len(SENTINEL):]
        return False

    with path.open("rb") as handle:
        compressed = handle.read(2) == b"\x1f\x8b"
        handle.seek(0)
        if scan(handle):
            return True
    if compressed:
        try:
            with gzip.open(path, "rb") as handle:
                return scan(handle)
        except (OSError, EOFError):
            raise LabError("UNREADABLE_COMPRESSED_LOG") from None
    return False


def audit_one(directory: Path, expected: str, digest: str, version: str, *, exports_dir: Path | None = None) -> dict:
    directory = outside_git(directory)
    meta = private_json(directory / "run-meta.json")
    result = private_json(directory / "result.json")
    if (meta.get("command") != expected or result.get("command") != expected
            or result.get("runID") != meta.get("runID")
            or meta.get("sourceDigest") != source_digest()
            or result.get("sourceDigest") != source_digest()
            or meta.get("runnerDigest") != digest or meta.get("xcodeVersion") != version
            or result.get("success") is not (expected == "sentinel-pass")
            or result.get("intentSentinelEntered") is not True
            or result.get("teardownCompleted") is not True
            or result.get("fixtureRemoved") is not True
            or result.get("pasteboardEmptyAtTeardown") is not True
            or result.get("intentionalFailure") is not (expected != "sentinel-pass")):
        raise LabError("SENTINEL_RUN_INCOMPLETE_OR_MISMATCHED")
    cleanup = result.get("teardownPasteboardCleanup")
    if not isinstance(cleanup, dict) or any(cleanup.get(key) is not True for key in CLEANUP_FLAGS):
        raise LabError("FOREGROUND_TEARDOWN_CLEANUP_UNVERIFIED")
    events = result.get("pasteEvents")
    if not isinstance(events, list) or not events:
        raise LabError("FOREGROUND_PASTE_CLEANUP_UNVERIFIED")
    for event in events:
        cleanup = event.get("cleanup") if isinstance(event, dict) else None
        if not isinstance(cleanup, dict) or any(cleanup.get(key) is not True for key in CLEANUP_FLAGS):
            raise LabError("FOREGROUND_PASTE_CLEANUP_UNVERIFIED")
    if expected != "sentinel-fail-intent" and (result.get("credentialSentinelEntered") is not True
                                               or result.get("credentialFieldProtected") is not True):
        raise LabError("CREDENTIAL_SENTINEL_NOT_REACHED")
    if expected == "sentinel-pass" and result.get("intentReplacementVerified") is not True:
        raise LabError("NONEMPTY_REPLACEMENT_NOT_VERIFIED")
    failure_code = {"sentinel-fail-intent": "INTENTIONAL_INTENT_FAILURE",
                    "sentinel-fail-credential": "INTENTIONAL_CREDENTIAL_FAILURE"}.get(expected)
    if failure_code and result.get("code") != failure_code:
        raise LabError("EXPECTED_FATAL_ABORT_NOT_CONFIRMED")
    run_bytes = (directory / "run.xctestrun").read_bytes()
    if hashlib.sha256(run_bytes).hexdigest() != meta.get("xctestrunDigest"):
        raise LabError("XCTESTRUN_CHANGED_AFTER_PREPARATION")
    target = targets(plistlib.loads(run_bytes))[0]
    if (target.get("SystemAttachmentLifetime") != "keepNever"
            or target.get("UserAttachmentLifetime") != "keepNever"
            or target.get("UseDestinationArtifacts") is not True):
        raise LabError("PRIVACY_OPTIONS_MISSING")
    log = directory / "xcodebuild.log"
    if not log.is_file():
        raise LabError("TERMINAL_LOG_REQUIRED")
    bundle = directory / "result.xcresult"
    if not bundle.is_dir():
        raise LabError("XCRESULT_REQUIRED")
    exports = outside_git(exports_dir) if exports_dir else directory / "audit-exports"
    if exports.exists() and not exports_dir:
        exports = directory / ("audit-exports-" + uuid.uuid4().hex)
    if exports.exists():
        raise LabError("AUDIT_EXPORT_DIRECTORY_ALREADY_EXISTS")
    exports.mkdir(parents=True, mode=0o700)
    tests = tool_json(["get", "test-results", "tests", "--path", str(bundle)], exports / "tests.json")
    cases = [node for node in walk(tests) if isinstance(node, dict) and node.get("nodeType") == "Test Case"]
    if len(cases) != 1 or cases[0].get("result") != ("Passed" if expected == "sentinel-pass" else "Failed"):
        raise LabError("EXPECTED_SENTINEL_TEST_RESULT_MISSING")
    test_id = cases[0].get("nodeIdentifierURL") or cases[0].get("nodeIdentifier")
    if not isinstance(test_id, str):
        raise LabError("TEST_IDENTIFIER_UNAVAILABLE")
    activities = tool_json(["get", "test-results", "activities", "--path", str(bundle), "--test-id", test_id],
                           exports / "activities.json")
    availability, log_status = export_available_logs(bundle, exports)
    activity_bytes = json.dumps(activities).encode()
    identity = ("UI_LAB_RUN_" + meta["runID"] + "_SOURCE_" + meta["sourceDigest"]
                + "_COMMAND_" + expected).encode()
    teardown = identity.replace(b"UI_LAB_RUN_", b"UI_LAB_TEARDOWN_", 1)
    if (identity not in activity_bytes or identity not in (exports / "action.json").read_bytes()
            or identity not in log.read_bytes() or teardown not in activity_bytes
            or teardown not in (exports / "action.json").read_bytes() or teardown not in log.read_bytes()):
        raise LabError("XCRESULT_OR_TERMINAL_RUN_IDENTITY_MISMATCH")
    if log_status["console"]["status"] == "EXPORTED":
        console = (exports / "console.json").read_bytes()
        if identity not in console or teardown not in console:
            raise LabError("CONSOLE_RUN_IDENTITY_MISMATCH")
    required_markers = [b"UI_LAB_INTENT_SENTINEL_INSERTED"]
    if expected != "sentinel-fail-intent":
        required_markers.append(b"UI_LAB_CREDENTIAL_SENTINEL_INSERTED")
    if expected == "sentinel-pass":
        required_markers.append(b"UI_LAB_NONEMPTY_REPLACEMENT_VERIFIED")
    for marker in required_markers:
        if marker not in activity_bytes:
            raise LabError("SENTINEL_ACTIVITY_NOT_REACHED")
    if failure_code:
        details = json.dumps(tests).encode() + activity_bytes
        if ("UI_LAB_" + failure_code).encode() not in details:
            raise LabError("DELIBERATE_FAILURE_NOT_CONFIRMED")
    violations = []
    if any(isinstance(node, dict) and node.get("attachments") for node in walk(activities)):
        violations.append("RETAINED_ACTIVITY_ATTACHMENTS")
    attachments = exports / "attachments"
    exported = subprocess.run(["xcrun", "xcresulttool", "export", "attachments", "--path", str(bundle),
                               "--output-path", str(attachments)], capture_output=True, check=False)
    write_private(exports / "attachment-export.log", exported.stdout + exported.stderr)
    if exported.returncode:
        raise LabError("ATTACHMENT_EXPORT_FAILED")
    if not attachments.is_dir():
        raise LabError("ATTACHMENT_EXPORT_UNAVAILABLE")
    if any(path.is_file() and path.name != "manifest.json" for path in attachments.rglob("*")):
        violations.append("RETAINED_ATTACHMENTS")
    diagnostics = export_diagnostics(bundle, exports, availability["hasDiagnostics"])
    # The marker is public. Even its presence in a failure hierarchy blocks secrets.
    seen = set()
    exposures = []
    for group, root in (("run-artifacts", directory), ("audit-exports", exports)):
        for path in root.rglob("*"):
            if path.is_symlink():
                raise LabError("SYMLINK_ARTIFACT_NOT_ALLOWED")
            if not path.is_file() or path in seen:
                continue
            seen.add(path)
            if contains_marker(path):
                exposures.append({"group": group, "relativePath": path.relative_to(root).as_posix()})
    write_private(exports / "sentinel-scan.json", json_bytes({
        "status": "BLOCKED" if exposures else "PASSED", "exposures": exposures,
    }))
    if exposures:
        violations.append("SENTINEL_EXPOSED_IN_ARTIFACTS")
    write_private(exports / "audit-status.json", json_bytes({
        "status": "BLOCKED" if violations else "PASSED", "violations": violations,
        "logs": log_status, "diagnostics": diagnostics,
    }))
    if violations:
        raise LabError(violations[0])
    return {"command": expected, "runID": meta["runID"], "logs": log_status,
            "diagnostics": diagnostics}


def audit(args: argparse.Namespace) -> None:
    receipt = outside_git(args.receipt)
    if receipt.exists():
        raise LabError("RECEIPT_MUST_BE_NEW")
    _, _, digest = runner_info(args.runner_app)
    version = xcode_version()
    run_audits = []
    failures = []
    for directory, command in ((args.pass_dir, "sentinel-pass"),
                               (args.fail_intent_dir, "sentinel-fail-intent"),
                               (args.fail_credential_dir, "sentinel-fail-credential")):
        try:
            run_audits.append(audit_one(directory, command, digest, version))
        except LabError as error:
            failures.append({"command": command, "code": str(error)})
    if failures:
        receipt.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
        write_private(receipt.parent / ("privacy-audit-blocked-" + uuid.uuid4().hex + ".json"), json_bytes({
            "schemaVersion": 1, "approved": False, "status": "BLOCKED", "failures": failures,
            "sourceDigest": source_digest(), "runnerDigest": digest, "xcodeVersion": version,
        }))
        raise LabError(failures[0]["code"])
    receipt.parent.mkdir(parents=True, exist_ok=True, mode=0o700)
    write_private(receipt, json_bytes({
        "schemaVersion": 1, "approved": True, "sourceDigest": source_digest(),
        "runnerDigest": digest, "xcodeVersion": version,
        "approvedAt": dt.datetime.now(dt.timezone.utc).isoformat(),
        "coverage": ["intent-plain", "credential-protected", "success", "fatal-intent-failure",
                     "fatal-credential-failure", "teardown-after-fatal-abort",
                     "activities", "available-xcresult-logs", "console-availability-proven",
                     "advertised-diagnostics", "exported-attachments", "terminal-log",
                     "run-source-command-identity", "foreground-cleanup-readability-restore",
                     "nonempty-field-replacement"],
        "internalOSBuffersVerified": False,
        "runAudits": run_audits,
    }))


def main() -> int:
    os.umask(0o077)
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="action", required=True)
    prep = commands.add_parser("prepare", help="Prepare files only; no build or device action")
    prep.add_argument("--source-xctestrun", type=Path, required=True)
    prep.add_argument("--runner-app", type=Path, required=True)
    prep.add_argument("--output-dir", type=Path, required=True)
    prep.add_argument("--command", choices=COMMANDS, default="inspect")
    prep.add_argument("--fixture-input", type=Path)
    prep.add_argument("--privacy-receipt", type=Path)
    scan = commands.add_parser("audit", help="Audit all three sentinel runs and issue a private receipt")
    scan.add_argument("--pass-dir", type=Path, required=True)
    scan.add_argument("--fail-intent-dir", type=Path, required=True)
    scan.add_argument("--fail-credential-dir", type=Path, required=True)
    scan.add_argument("--runner-app", type=Path, required=True)
    scan.add_argument("--receipt", type=Path, required=True)
    args = parser.parse_args()
    try:
        (prepare if args.action == "prepare" else audit)(args)
    except LabError as error:
        print("BLOCKED: " + str(error), file=sys.stderr)
        return 2
    except Exception:
        # A decoder/filesystem/tool exception may contain private contents or paths.
        print("BLOCKED: LOCAL_PREPARATION_ERROR", file=sys.stderr)
        return 2
    print("Prepared private run files; no build/device action executed." if args.action == "prepare"
          else "Sentinel artifact audit passed; private receipt created.")
    return 0


if __name__ == "__main__":
    sys.exit(main())
