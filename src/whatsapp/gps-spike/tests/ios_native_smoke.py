"""Simulator-only XCTest runner: already-built app, private fixtures, loopback faults."""
import argparse
import json
import os
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
    sim = ["xcrun", "simctl"]
    bundle_id = "dev.matheusduarte.vanrides.driver"
    subprocess.run(sim + ["install", args.simulator_id, args.app], check=True, capture_output=True)
    subprocess.run(sim + ["privacy", args.simulator_id, "grant", "location-always", bundle_id], check=True, capture_output=True)
    private = private_path(Path(args.private_dir) / "native-smoke.sqlite").parent
    stamp = str(time.time_ns())
    for mode in ("none", "stop-request-loss", "stop-ack-loss"):
        prefix = private / ("ios-" + mode + "-" + stamp)
        db, credential = Path(str(prefix) + ".sqlite"), Path(str(prefix) + ".json")
        subprocess.run([sys.executable, "-m", "receiver", "--db", str(db), "provision",
                        "--alias", "synthetic_iphone", "--out", str(credential)],
                       cwd=ROOT, check=True, stdout=subprocess.DEVNULL)
        bundle = read_private(credential)
        fixture = {"baseUrl": "http://127.0.0.1:18765", "token": bundle["token"], "startIntentId": bundle["startIntentId"]}
        # XCTest may replace the app's data container during installation.
        container = Path(subprocess.check_output(sim + ["get_app_container", args.simulator_id, bundle_id, "data"], text=True).strip())
        path = container / "tmp/GPS_SPIKE_TEST.json"
        path.parent.mkdir(exist_ok=True)
        with os.fdopen(os.open(str(path), os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o600), "w") as output:
            json.dump(fixture, output)
        server_log, native_log = Path(str(prefix) + "-server.log"), Path(str(prefix) + "-native.log")
        result_bundle = Path(str(prefix) + ".xcresult")
        with server_log.open("w") as output:
            server = subprocess.Popen([sys.executable, str(ROOT / "tests/native_smoke_server.py"),
                                       "--db", str(db), "--mode", mode], stdout=output, stderr=output)
            test = None
            try:
                deadline = time.monotonic() + 10
                while time.monotonic() < deadline:
                    if server.poll() is not None:
                        raise RuntimeError("SERVER_START_FAILED")
                    try:
                        urllib.request.urlopen("http://127.0.0.1:18765/driver-app/v1/status", timeout=1)
                    except urllib.error.HTTPError as error:
                        if error.code == 401 and '"ready": true' in server_log.read_text(): break
                    except OSError:
                        pass
                    time.sleep(0.2)
                else: raise RuntimeError("SERVER_READY_TIMEOUT")
                command = ["xcodebuild", "test-without-building", "-xctestrun", args.xctestrun,
                    "-destination", "id=" + args.simulator_id, "-parallel-testing-enabled", "NO",
                    "-collect-test-diagnostics", "never",
                    "-only-testing:RunnerTests/DriverTrackingIntegrationTests", "-resultBundlePath", str(result_bundle)]
                with native_log.open("w") as test_output:
                    test = subprocess.Popen(command, stdout=test_output, stderr=test_output, start_new_session=True)
                    deadline = time.monotonic() + 180
                    while test.poll() is None and time.monotonic() < deadline:
                        subprocess.run(sim + ["location", args.simulator_id, "set", "0.002,0.002"], capture_output=True, timeout=5)
                        time.sleep(2)
                    if test.poll() is None:
                        raise RuntimeError("NATIVE_TEST_TIMEOUT")
                if test.returncode:
                    raise RuntimeError("NATIVE_TEST_FAILED_" + mode)
                summary = json.loads(subprocess.check_output(["xcrun", "xcresulttool", "get", "test-results", "summary", "--path", str(result_bundle)], text=True))
                if summary.get("passedTests") != 1 or summary.get("failedTests") != 0 or summary.get("skippedTests") != 0:
                    raise RuntimeError("INTEGRATION_NOT_EXECUTED_" + mode)
            finally:
                if test is not None and test.poll() is None:
                    os.killpg(test.pid, signal.SIGTERM)
                    try: test.wait(timeout=10)
                    except subprocess.TimeoutExpired:
                        os.killpg(test.pid, signal.SIGKILL)
                        test.wait(timeout=5)
                server.send_signal(signal.SIGINT)
                server.wait(timeout=10)
                path.unlink(missing_ok=True)
        summary = json.loads(server_log.read_text().strip().splitlines()[-1])
        expected = 1 if mode == "none" else 2
        if summary["stopRequests"] != expected or summary["injected"] != (0 if mode == "none" else 1):
            raise RuntimeError("UNEXPECTED_STOP_RETRY_" + mode)
        print(json.dumps({"platform": "ios-simulator", "mode": mode, "result": "PASS",
                          "stopRequests": summary["stopRequests"]}), flush=True)


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    for name in ("app", "xctestrun", "simulator-id", "private-dir"):
        parser.add_argument("--" + name, required=True)
    try:
        run(parser.parse_args())
    except (RuntimeError, subprocess.SubprocessError, OSError) as error:
        print(str(error) if isinstance(error, RuntimeError) else "NATIVE_HARNESS_FAILED", file=sys.stderr)
        sys.exit(1)
