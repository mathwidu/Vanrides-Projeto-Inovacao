"""Run the driver instrumentation against isolated loopback faults, emulator only."""
import argparse
import json
from pathlib import Path
import signal
import subprocess
import sys
import time
import urllib.error
import urllib.request

ROOT = Path(__file__).resolve().parents[1]
sys.path.insert(0, str(ROOT))
from receiver.cli import private_path, read_private


def run(args):
    adb = [args.adb, "-e"]
    hardware = subprocess.check_output(adb + ["shell", "getprop", "ro.hardware"], text=True).strip()
    if hardware not in ("ranchu", "goldfish"):
        raise RuntimeError("EMULATOR_REQUIRED")
    for apk in (args.app_apk, args.test_apk):
        result = subprocess.run(adb + ["install", "-r", apk], capture_output=True)
        if result.returncode:
            raise RuntimeError("APK_INSTALL_FAILED")
    private = private_path(Path(args.private_dir) / "native-smoke.sqlite").parent
    stamp = str(time.time_ns())
    for mode in ("none", "stop-request-loss", "stop-ack-loss", "stop-invalid-response"):
        prefix = private / ("android-" + mode + "-" + stamp)
        db, credential = Path(str(prefix) + ".sqlite"), Path(str(prefix) + ".json")
        subprocess.run([sys.executable, "-m", "receiver", "--db", str(db), "provision",
                        "--alias", "synthetic_android", "--out", str(credential)],
                       cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        bundle = read_private(credential)
        fixture = {"kind": "TEST_BEARER", "baseUrl": "http://10.0.2.2:18765",
                   "token": bundle["token"], "startIntentId": bundle["startIntentId"]}
        server_log, native_log = Path(str(prefix) + "-server.log"), Path(str(prefix) + "-native.log")
        with server_log.open("w") as output:
            server = subprocess.Popen([sys.executable, str(ROOT / "tests/native_smoke_server.py"),
                                       "--db", str(db), "--mode", mode], stdout=output, stderr=output)
            try:
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    if server.poll() is not None:
                        raise RuntimeError("SERVER_START_FAILED")
                    try:
                        urllib.request.urlopen("http://127.0.0.1:18765/driver-app/v1/status", timeout=1)
                    except urllib.error.HTTPError as error:
                        if error.code == 401 and '"ready": true' in server_log.read_text():
                            break
                    except OSError:
                        pass
                    time.sleep(0.2)
                else:
                    raise RuntimeError("SERVER_READY_TIMEOUT")
                # Payload crosses stdin into app-private storage, never argv/env/stdout.
                subprocess.run(adb + ["shell", "run-as dev.matheusduarte.vanrides.driver sh -c 'cat > cache/GPS_SPIKE_TEST.json'"],
                               input=json.dumps(fixture).encode(), check=True, capture_output=True)
                subprocess.run(adb + ["shell", "run-as", "dev.matheusduarte.vanrides.driver", "chmod", "600", "cache/GPS_SPIKE_TEST.json"],
                               check=True, capture_output=True)
                command = adb + ["shell", "am", "instrument", "-w", "-r", "-e", "class",
                    "com.example.vanrides_frontend_dev.driver.DriverTrackingSmokeTest#authenticatedGpsReceiptAndNativeStop"]
                if mode == "stop-invalid-response":
                    command += ["-e", "stopFailure", "INVALID_CONTRACT"]
                command += ["dev.matheusduarte.vanrides.driver.test/androidx.test.runner.AndroidJUnitRunner"]
                with native_log.open("w") as native_output:
                    test = subprocess.Popen(command, stdout=native_output, stderr=native_output)
                    deadline = time.monotonic() + 120
                    while test.poll() is None and time.monotonic() < deadline:
                        # Synthetic point near the equator. This emulator's GNSS
                        # did not emit a fix for exactly 0,0 in the local trial.
                        subprocess.run(adb + ["emu", "geo", "fix", "0.002", "0.002", "0", "8", "0"], capture_output=True)
                        time.sleep(2)
                    if test.poll() is None:
                        test.terminate(); test.wait(timeout=10)
                        raise RuntimeError("NATIVE_TEST_TIMEOUT")
                result = native_log.read_text()
                if "OK (1 test)" not in result or "FAILURES!!!" in result:
                    raise RuntimeError("NATIVE_TEST_FAILED_" + mode)
            finally:
                server.send_signal(signal.SIGINT)
                server.wait(timeout=10)
        summary = json.loads(server_log.read_text().strip().splitlines()[-1])
        expected = 2 if mode in ("stop-request-loss", "stop-ack-loss") else 1
        if summary["stopRequests"] != expected or summary["injected"] != (0 if mode == "none" else 1):
            raise RuntimeError("UNEXPECTED_STOP_RETRY_" + mode)
        print(json.dumps({"platform": "android-emulator", "mode": mode, "result": "PASS",
                          "stopRequests": summary["stopRequests"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("adb", "app-apk", "test-apk", "private-dir"):
        parser.add_argument("--" + name, required=True)
    try:
        run(parser.parse_args())
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        # Only our constant code is safe; subprocess arguments/private contents are not echoed.
        print(str(error) if isinstance(error, RuntimeError) else "NATIVE_HARNESS_FAILED", file=sys.stderr)
        sys.exit(1)
