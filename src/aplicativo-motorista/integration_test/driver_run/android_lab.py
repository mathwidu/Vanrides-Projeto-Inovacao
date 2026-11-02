#!/usr/bin/env python3
"""Physical110 lab: explicit pairing/start, native stop, durable ACK after reopening.

Provision a driver.lab APK/TLS proxy first. Unlock the Redmi and grant precise
location plus notifications through the app's own permission action. The runner
may accept the explicit while-in-use/notification system dialogs for this app. It
never unlocks the screen, grants permissions through adb, installs APKs, or logs
codes, URIs, identities or GPS. All backend data belongs to the private sidecar.
"""
import argparse
import http.client
import importlib.util
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import time
import xml.etree.ElementTree as ET

_pairing_path = Path(__file__).resolve().parents[1] / "driver_pairing/android_lab.py"
_spec = importlib.util.spec_from_file_location("driver_pairing_lab", _pairing_path)
pairing = importlib.util.module_from_spec(_spec)
_spec.loader.exec_module(pairing)
_isolation_spec = importlib.util.spec_from_file_location("driver_run_isolation", Path(__file__).with_name("artifact_isolation.py"))
isolation = importlib.util.module_from_spec(_isolation_spec)
_isolation_spec.loader.exec_module(isolation)


def control(path, route, payload, timeout=120):
    details = path.lstat()
    pairing.require(stat.S_ISSOCK(details.st_mode) and details.st_uid == os.getuid()
                    and stat.S_IMODE(details.st_mode) == 0o600, "SOCKET_NOT_PRIVATE")
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as transport:
        transport.settimeout(timeout)
        transport.connect(str(path))
        client = http.client.HTTPConnection("localhost", timeout=timeout)
        client.sock = transport
        try:
            client.request("POST", route, json.dumps(payload).encode(), {"Content-Type": "application/json"})
            response = client.getresponse()
            pairing.require(200 <= response.status < 300, "SIDECAR_" + route[1:].upper() + "_REJECTED")
            data = response.read(8193)
            pairing.require(len(data) <= 8192, "SIDECAR_RESPONSE_TOO_LARGE")
            return json.loads(data) if data else {}
        except (socket.timeout, TimeoutError):
            raise pairing.LabFailure("SIDECAR_" + route[1:].upper() + "_TIMEOUT") from None
        finally:
            client.close()


def unlocked(device):
    policy = device.adb("shell", "dumpsys", "window", "policy", timeout=8)
    pairing.require(not re.search(r"(?:showing|mShowingLockscreen|mKeyguardShowing|isStatusBarKeyguard)=true", policy),
                    "ACTION_REQUIRED_UNLOCK_REDMI")
    pairing.require(bool(re.search(r"(?:showing|mShowingLockscreen|mKeyguardShowing|isStatusBarKeyguard)=false", policy)),
                    "ACTION_REQUIRED_VERIFY_UNLOCKED_REDMI")


def labels(device):
    return {label for values, _ in device.nodes() for label in values}


def scroll_to(device, text, timeout=25):
    deadline = time.monotonic() + timeout
    while time.monotonic() < deadline:
        unlocked(device)
        if text in labels(device):
            return
        device.adb("shell", "input", "swipe", "500", "1500", "500", "500", "400")
        time.sleep(.3)
    raise pairing.LabFailure("EXPECTED_RUN_CONTROL_MISSING")


def service_present(device):
    services = device.adb("shell", "dumpsys", "activity", "services", pairing.PACKAGE, timeout=8)
    return any("ServiceRecord{" in row and ".driver.DriverTrackingService" in row
               for row in services.splitlines())


def service_absent(device):
    pairing.require(not service_present(device), "NATIVE_LOCATION_SERVICE_STILL_PRESENT")


def permission_choice(rows):
    text = " ".join(label.casefold() for labels, _ in rows for label in labels)
    pairing.require("vanrides motorista" in text and any(word in text for word in ("localiza", "location", "notifica", "notification")),
                    "ACTION_REQUIRED_UNRECOGNIZED_PERMISSION_DIALOG")
    # Prefer while-in-use; a generic allow is valid only when no unrestricted-location choice exists.
    while_using = {"durante o uso do app", "permitir durante o uso do app", "enquanto o app estiver em uso", "while using the app"}
    for labels, bounds in rows:
        if any(label.casefold() in while_using for label in labels):
            return bounds
    pairing.require(not any(word in text for word in ("o tempo todo", "sempre", "all the time")),
                    "ACTION_REQUIRED_UNRESTRICTED_PERMISSION_NOT_ALLOWED")
    for labels, bounds in rows:
        if any(label.casefold() in {"permitir", "allow"} for label in labels):
            return bounds
    raise pairing.LabFailure("ACTION_REQUIRED_EXPECTED_PERMISSION_BUTTON_MISSING")


def prepare_permissions(device, checks):
    if "Permitir localização no percurso" not in labels(device):
        return
    device.tap("Permitir localização no percurso")
    deadline = time.monotonic() + 25
    while time.monotonic() < deadline:
        unlocked(device)
        data = device.adb("shell", "-tt", "uiautomator", "dump", "/dev/tty", timeout=15)
        start, end = data.find("<hierarchy"), data.rfind("</hierarchy>")
        if start < 0 or end < start:
            time.sleep(.3); continue
        root = ET.fromstring(data[start:end + len("</hierarchy>")])
        system = []
        for node in root.iter("node"):
            if node.get("package") in {"com.android.permissioncontroller", "com.google.android.permissioncontroller", "com.google.android.packageinstaller", "com.android.packageinstaller"}:
                values = (node.get("text", "") + "\n" + node.get("content-desc", "")).splitlines()
                system.append(([value.strip() for value in values if value.strip()], node.get("bounds", "")))
        if system:
            bounds = permission_choice(system)
            match = pairing.BOUNDS.fullmatch(bounds)
            pairing.require(match, "PERMISSION_BUTTON_BOUNDS_INVALID")
            left, top, right, bottom = map(int, match.groups())
            pairing.require(right > left and bottom > top, "PERMISSION_BUTTON_OFFSCREEN")
            device.adb("shell", "input", "tap", str((left + right) // 2), str((top + bottom) // 2))
            time.sleep(.4)
        elif "Permitir localização no percurso" not in labels(device):
            checks.append("app_location_permission_accepted_by_own_system_dialog")
            return
        else:
            time.sleep(.4)
    raise pairing.LabFailure("ACTION_REQUIRED_PRECISE_LOCATION_AND_NOTIFICATIONS_IN_APP")


def exercise(args, checks):
    try:
        isolation.inspect_apk(args.apk, "mvp")
    except isolation.IsolationFailure:
        raise pairing.LabFailure("MVP_APK_ISOLATION_FAILED") from None
    checks.append("actual_apk_dex_excludes_spike030_origin_and_transport")
    device = pairing.target(str(args.adb))
    digest = device.verify_apk(args.apk)
    unlocked(device)
    entries = [row.split()[-2:] for row in device.adb("reverse", "--list").splitlines() if row.strip()]
    # We interrupt only a reverse owned by this invocation, never another lab's tunnel.
    pairing.require(all(row[0] != pairing.PORT for row in entries), "EXCLUSIVE_LAB_REVERSE_REQUIRED")
    device.adb("reverse", pairing.PORT, pairing.PORT)
    may_be_collecting = False
    try:
        device.launch()
        checks.append("verified_apk_and_explicit_launcher")
        if not args.paired:
            device.tap("Vincular este celular")
            device.tap("Mostrar código")
            code, _ = device.find(lambda label: bool(pairing.CONSONANTS.fullmatch(label)))
            control(args.socket, "/pair", {"code": code}, timeout=60)
            del code
            device.return_to_pairing()
        device.wait_text("Aparelho vinculado")
        checks.append("synthetic_pairing_confirmed_on_same_device")
        scroll_to(device, "Compartilhamento de localização")
        prepare_permissions(device, checks)
        response = control(args.socket, "/intent", {}, timeout=30)
        uri = response.get("uri") if isinstance(response, dict) else None
        pairing.require(isinstance(uri, str) and re.fullmatch(
            r"vanridesdriver://run/start#v1\.[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}\.(0|[1-9][0-9]{0,15})\.[A-Za-z0-9_-]{42}[AEIMQUYcgkosw048]", uri),
            "SIDECAR_START_URI_INVALID")
        device.start("-a", "android.intent.action.VIEW", "-c", "android.intent.category.BROWSABLE", "-d", uri)
        del uri, response
        scroll_to(device, "Iniciar localização desta saída")
        may_be_collecting = True
        device.tap("Iniciar localização desta saída")
        control(args.socket, "/started", {}, timeout=90)
        checks.append("explicit_start_confirmed_only_after_backend_valid_gps")
        device.adb("shell", "input", "keyevent", "KEYCODE_HOME")
        device.launch()
        scroll_to(device, "Parar localização")
        # Isolate stop delivery from sensor removal; no handset network setting changes.
        device.adb("reverse", "--remove", pairing.PORT)
        device.tap("Parar localização")
        device.wait_text("Parada solicitada. Aguardando confirmação.", timeout=20)
        service_absent(device)
        may_be_collecting = False
        checks.append("native_sensor_stopped_while_ack_offline")
        # Force-stop only this dedicated lab package after native removal, then replay the private stop journal.
        device.adb("shell", "am", "force-stop", pairing.PACKAGE)
        device.adb("reverse", pairing.PORT, pairing.PORT)
        device.launch()
        control(args.socket, "/stopped", {}, timeout=90)
        scroll_to(device, "Localização interrompida")
        service_absent(device)
        checks.append("durable_stop_ack_after_process_reopening_without_new_collection")
        pairing.require(device.verify_apk(args.apk) == digest, "APK_CHANGED_DURING_RUN")
        return digest
    finally:
        cleanup_failed = False
        if may_be_collecting:
            try:
                if service_present(device):
                    unlocked(device); device.launch(); scroll_to(device, "Parar localização", timeout=10)
                    device.tap("Parar localização")
                    deadline = time.monotonic() + 10
                    while service_present(device) and time.monotonic() < deadline:
                        time.sleep(.3)
                service_absent(device)
                checks.append("cleanup_confirmed_native_stop")
            except (pairing.LabFailure, OSError, subprocess.SubprocessError, ET.ParseError):
                cleanup_failed = True
        entries = [row.split()[-2:] for row in device.adb("reverse", "--list").splitlines() if row.strip()]
        if [pairing.PORT, pairing.PORT] in entries:
            device.adb("reverse", "--remove", pairing.PORT)
        pairing.require(not cleanup_failed, "ACTION_REQUIRED_NATIVE_STOP_UNCONFIRMED")


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb", type=Path, required=True)
    parser.add_argument("--apk", type=Path, required=True)
    parser.add_argument("--socket", type=Path, required=True)
    parser.add_argument("--paired", action="store_true", help="continue only the same sidecar/cohort after its pairing was confirmed")
    args = parser.parse_args()
    checks = []
    try:
        digest = exercise(args, checks)
    except pairing.LabFailure as failure:
        reason = str(failure)
        print(json.dumps({"status": "ACTION_REQUIRED" if reason.startswith("ACTION_REQUIRED_") else "FAIL",
                          "reason": reason, "checks": checks}))
        return 2 if reason.startswith("ACTION_REQUIRED_") else 1
    except (OSError, subprocess.SubprocessError, ET.ParseError, http.client.HTTPException, ValueError):
        print(json.dumps({"status": "FAIL", "reason": "LAB_IO_FAILURE", "checks": checks}))
        return 1
    print(json.dumps({"status": "PASS", "scenario": "run110_offline_stop_reopen", "checks": checks,
                      "apkSha256": digest, "physicalAndroid": True, "physicalIos": False,
                      "channel": "synthetic", "batteryOrMovementProof": False}))
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
