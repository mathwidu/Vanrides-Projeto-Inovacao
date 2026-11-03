#!/usr/bin/env python3
"""Drive the isolated Android lab APK through its real UI against the 060 Unix socket."""
import argparse
import hashlib
import http.client
import json
import os
from pathlib import Path
import re
import socket
import stat
import subprocess
import time
import xml.etree.ElementTree as ET

PACKAGE = "dev.matheusduarte.vanrides.driver.lab"
ACTIVITY = PACKAGE + "/com.example.vanrides_frontend_dev.driver.DriverActivity"
CONSONANTS = re.compile(r"[BCDFGHJKMNPQRSTVWXYZ]{8}")
BOUNDS = re.compile(r"\[(\d+),(\d+)\]\[(\d+),(\d+)\]")
PORT = "tcp:18443"


class LabFailure(Exception):
    """Only constant diagnostics may be reported to the caller."""


def require(value, reason):
    if not value:
        raise LabFailure(reason)


def stream_hash(stream):
    digest = hashlib.sha256()
    for chunk in iter(lambda: stream.read(262144), b""):
        digest.update(chunk)
    return digest.hexdigest()


def file_hash(path):
    with path.open("rb") as stream:
        return stream_hash(stream)


def unix_post(path, route, payload):
    details = path.lstat()
    require(stat.S_ISSOCK(details.st_mode) and details.st_uid == os.getuid()
            and stat.S_IMODE(details.st_mode) == 0o600, "SOCKET_NOT_PRIVATE")
    with socket.socket(socket.AF_UNIX, socket.SOCK_STREAM) as transport:
        transport.settimeout(90)
        transport.connect(str(path))
        client = http.client.HTTPConnection("localhost")
        client.sock = transport
        try:
            client.request("POST", route, json.dumps(payload).encode(), {"Content-Type": "application/json"})
            response = client.getresponse()
            require(200 <= response.status < 300, "LAB_CONTROL_REJECTED")
            response.read(2048)
        finally:
            client.close()


def target(adb):
    result = subprocess.run([adb, "devices"], capture_output=True, timeout=6, check=True)
    devices = [line.split() for line in result.stdout.decode().splitlines()[1:] if line.strip()]
    require(len(devices) == 1 and len(devices[0]) == 2 and devices[0][1] == "device", "ONE_AUTHORIZED_REDMI_REQUIRED")
    return Device(adb, devices[0][0])


class Device:
    def __init__(self, adb, serial):
        self.command = [adb, "-s", serial]

    def adb(self, *args, timeout=12):
        result = subprocess.run([*self.command, *args], capture_output=True, timeout=timeout)
        require(result.returncode == 0, "ADB_COMMAND_FAILED")
        return result.stdout.decode(errors="replace").strip()

    def verify_apk(self, apk):
        require(self.adb("shell","getprop","ro.product.model") == "Redmi Note 8", "UNEXPECTED_DEVICE_MODEL")
        path = self.adb("shell","pm","path",PACKAGE)
        require(re.fullmatch(r"package:/data/app/[A-Za-z0-9_./+=~-]+/base\.apk",path), "LAB_BASE_APK_REQUIRED")
        remote = path.removeprefix("package:")
        local_digest = file_hash(apk)
        # Stream through the pipe. The installed APK never occupies a second full buffer.
        with subprocess.Popen([*self.command,"exec-out","cat",remote],stdout=subprocess.PIPE,stderr=subprocess.DEVNULL) as process:
            digest = stream_hash(process.stdout)
            require(process.wait(timeout=10)==0 and digest==local_digest,"INSTALLED_APK_MISMATCH")
        return local_digest

    def opened(self):
        text = self.adb("shell","dumpsys","activity","activities",timeout=8)
        return any(PACKAGE + "/" in line or PACKAGE + "/." in line for line in text.splitlines()
                   if "mResumedActivity" in line or "topResumedActivity" in line)

    def start(self, *intent):
        # Resolve the actual filter, then use the same explicit component as the launcher on MIUI.
        resolved = self.adb("shell", "cmd", "package", "resolve-activity", "--brief", *intent, "-p", PACKAGE)
        require(resolved.splitlines()[-1:] == [ACTIVITY], "LAB_INTENT_NOT_RESOLVED")
        result = self.adb("shell", "am", "start", "-W", *intent, "-n", ACTIVITY, timeout=30)
        require("Status: ok" in result.splitlines(), "LAB_ACTIVITY_START_FAILED")

    def launch(self):
        self.start("-a", "android.intent.action.MAIN", "-c", "android.intent.category.LAUNCHER")

    def return_to_pairing(self):
        self.start("-a", "android.intent.action.VIEW", "-c", "android.intent.category.BROWSABLE",
                   "-d", "vanridesdriver://pairing/return")

    def nodes(self):
        if not self.opened():
            return []
        # A terminal receives the UI hierarchy directly, without a screenshot or an XML file on the handset.
        data = self.adb("shell","-tt","uiautomator","dump","/dev/tty",timeout=15)
        start = data.find("<hierarchy")
        end = data.rfind("</hierarchy>")
        if start < 0 or end < start:
            return []
        root = ET.fromstring(data[start:end+len("</hierarchy>")])
        result = []
        for node in root.iter("node"):
            if node.get("package") != PACKAGE:
                continue
            labels = (node.get("text","") + "\n" + node.get("content-desc","")).splitlines()
            result.append(([label.strip() for label in labels if label.strip()],node.get("bounds","")))
        return result

    def find(self, predicate, timeout=40):
        deadline = time.monotonic()+timeout
        while time.monotonic()<deadline:
            for labels,bounds in self.nodes():
                for label in labels:
                    if predicate(label):
                        return label,bounds
            time.sleep(.45)
        raise LabFailure("EXPECTED_LAB_CONTROL_MISSING")

    def wait_text(self, label, timeout=40):
        self.find(lambda actual:actual==label,timeout)

    def tap(self, label):
        _,bounds=self.find(lambda actual:actual==label)
        match=BOUNDS.fullmatch(bounds)
        require(match, "LAB_CONTROL_BOUNDS_INVALID")
        x,y,right,bottom=map(int,match.groups())
        require(right>x and bottom>y,"LAB_CONTROL_OFFSCREEN")
        self.adb("shell","input","tap",str((x+right)//2),str((y+bottom)//2))


def exercise(args, checks):
    device=target(str(args.adb))
    digest=device.verify_apk(args.apk)
    if args.return_links:
        # Only a completed synthetic run may be exercised without the server fixture.
        device.launch()
        device.wait_text("Aparelho desvinculado")
        device.adb("shell", "am", "force-stop", PACKAGE)
        device.return_to_pairing()
        device.wait_text("Feche o VanRides e abra novamente pelo ícone para criar um vínculo.")
        require(not any("Vincular este celular" in labels for labels, _ in device.nodes()),
                "RETURN_OFFERED_PAIRING")
        checks.append("cold_return_cannot_open_a_new_pairing")
        previous = device.adb("shell", "pidof", PACKAGE)
        device.adb("shell", "input", "keyevent", "KEYCODE_HOME")
        device.launch()
        device.wait_text("Vincular este celular")
        device.wait_text("Aparelho desvinculado")
        require(previous and device.adb("shell", "pidof", PACKAGE) == previous, "PROCESS_CHANGED")
        checks.append("warm_icon_restored_button_without_pairing")
        return digest
    entries=[row.split()[-2:] for row in device.adb("reverse","--list").splitlines() if row.strip()]
    require(all(row[0]!=PORT or row[1]==PORT for row in entries),"LAB_PORT_ALREADY_ASSIGNED")
    owned=[PORT,PORT] not in entries
    require(not args.offline_revoke or owned, "PRIVATE_REVERSE_REQUIRED_FOR_FAULT")
    if owned:
        device.adb("reverse",PORT,PORT)
    try:
        device.launch()
        checks.append("lab_launcher_resolved_and_opened")
        device.tap("Vincular este celular")
        device.tap("Mostrar código")
        code,_=device.find(lambda label:bool(CONSONANTS.fullmatch(label)))
        unix_post(args.socket,"/confirm",{"userCode":code})
        del code
        checks.append("explicit_code_approved_via_synthetic_inbox")
        device.return_to_pairing()
        device.wait_text("Aparelho vinculado")
        checks.append("paired_after_return")
        device.adb("shell","am","force-stop",PACKAGE)
        device.launch()
        device.wait_text("Aparelho vinculado")
        checks.append("reopened_with_persisted_key_and_session")
        if args.refresh:
            # Access expires after five minutes. Reopening invokes the normal refresh path.
            print("Aguardando a expiração do acesso de teste no laboratório.",flush=True)
            time.sleep(310)
            device.adb("shell", "am", "force-stop", PACKAGE)
            device.launch()
            # The app itself must recover the dropped, committed refresh response.
            device.wait_text("Aparelho vinculado", timeout=45)
            checks.append("ready_after_expiry_and_lost_response_without_manual_retry")
        if args.offline_revoke:
            # Drop only this lab's route. Restart discards pooled TLS connections.
            device.adb("reverse", "--remove", PORT)
            device.adb("shell", "am", "force-stop", PACKAGE)
            device.launch()
        device.tap("Desvincular este celular")
        device.tap("Desvincular")
        if args.offline_revoke:
            device.wait_text("Desvinculação em andamento")
            checks.append("offline_revoke_shows_pending_without_ack")
            device.adb("reverse", PORT, PORT)
            try:
                device.wait_text("Aparelho desvinculado", timeout=8)
                checks.append("revoke_ack_visible_after_reconnecting")
            except LabFailure as error:
                if str(error) != "EXPECTED_LAB_CONTROL_MISSING":
                    raise
                # Foreground reconciliation remains safe even if the ACK arrives now.
                device.adb("shell", "input", "keyevent", "KEYCODE_HOME")
                device.launch()
                device.wait_text("Aparelho desvinculado")
                checks.append("revoke_ack_visible_on_return_to_foreground")
        device.wait_text("Aparelho desvinculado")
        unix_post(args.socket,"/complete",{})
        checks.append("server_acknowledged_revoke")
        return digest
    finally:
        if owned and [PORT, PORT] in [line.split()[-2:] for line in device.adb("reverse", "--list").splitlines()]:
            device.adb("reverse","--remove",PORT)


def main():
    parser=argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--adb",type=Path,required=True)
    mode = parser.add_mutually_exclusive_group(required=True)
    mode.add_argument("--socket", type=Path)
    mode.add_argument("--return-links", action="store_true", help="verify cold and warm returns after revocation")
    parser.add_argument("--apk",type=Path,required=True)
    parser.add_argument("--refresh",action="store_true",help="wait for the actual five-minute access TTL")
    parser.add_argument("--offline-revoke", action="store_true", help="interrupt only the lab route during revocation")
    args=parser.parse_args()
    if args.return_links and (args.refresh or args.offline_revoke):
        parser.error("--refresh and --offline-revoke require --socket")
    checks=[]
    try:
        digest=exercise(args,checks)
    except LabFailure as exc:
        print(json.dumps({"status":"FAIL","reason":str(exc),"checks":checks}))
        return 1
    except (OSError,subprocess.SubprocessError,ET.ParseError,http.client.HTTPException):
        print(json.dumps({"status":"FAIL","reason":"LAB_IO_FAILURE","checks":checks}))
        return 1
    print(json.dumps({"status":"PASS","checks":checks,"apkSha256":digest,
                      "scenario":"return_links" if args.return_links else "pairing",
                      "physicalAndroid":True,"channel":"offline" if args.return_links else "synthetic",
                      "physicalIos":False,"refreshWait":args.refresh}))
    return 0


if __name__=="__main__":
    raise SystemExit(main())
