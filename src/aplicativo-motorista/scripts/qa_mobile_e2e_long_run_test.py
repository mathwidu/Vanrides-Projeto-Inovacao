#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_mobile_e2e_long_run.py"


def run_script(*args: str) -> subprocess.CompletedProcess[str]:
    if not SCRIPT.exists():
        return subprocess.CompletedProcess(
            ["python3", str(SCRIPT), *args],
            127,
            "",
            f"{SCRIPT} missing",
        )

    env = os.environ.copy()
    env.pop("VANRIDES_E2E_PREFLIGHT_ONLY", None)
    return subprocess.run(
        ["python3", str(SCRIPT), *args],
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=True,
    )


class MobileE2eLongRunTest(unittest.TestCase):
    def test_check_only_validates_mobile_roles_without_creating_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_root = Path(temp) / "mobile-e2e-long"

            result = run_script("--check-only", "--evidence-root", str(evidence_root))

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertIn("MOBILE_E2E_LONG_CONTRACT_OK roles=4", result.stdout)
            self.assertFalse(evidence_root.exists(), "check-only must not create evidence")

    def test_dry_run_writes_summary_without_running_flutter(self) -> None:
        timestamp = "20260707T000000Z-dry"
        with tempfile.TemporaryDirectory() as temp:
            evidence_root = Path(temp) / "mobile-e2e-long"

            result = run_script(
                "--dry-run",
                "--role",
                "driver-mobile",
                "--device",
                "ios-device",
                "--api",
                "https://api-dev.example",
                "--evidence-root",
                str(evidence_root),
                "--timestamp",
                timestamp,
            )

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            summary = evidence_root / "driver-mobile" / timestamp / "SUMMARY.md"
            self.assertTrue(summary.exists(), result.stderr + result.stdout)
            contents = summary.read_text(encoding="utf-8")
            self.assertIn("Status: DRY_RUN", contents)
            self.assertIn("Papel: driver-mobile", contents)
            self.assertIn("Suite: integration_test/motorista/all.dart", contents)
            self.assertIn("Device: ios-device", contents)
            self.assertIn("API: https://api-dev.example", contents)

    def test_list_includes_operational_fixture_mobile_suite(self) -> None:
        result = run_script("--list")

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn(
            "operational-fixture-mobile|operacional|integration_test/operational_fixture/all.dart",
            result.stdout,
        )

    def test_run_role_classifies_wireless_iphone_flutter_test_blocker(self) -> None:
        timestamp = "20260707T000000Z"
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            evidence_root = temp_dir / "mobile-e2e-long"
            runner = temp_dir / "fake_run_dev_e2e.sh"
            runner.write_text(
                "#!/usr/bin/env bash\n"
                "echo 'Cannot start app on wirelessly tethered iOS device. Try running again with the --publish-port flag' >&2\n"
                "exit 1\n",
                encoding="utf-8",
            )
            runner.chmod(0o755)

            result = run_script(
                "--run-role",
                "--role",
                "driver-mobile",
                "--device",
                "ios-device",
                "--api",
                "https://api-dev.example",
                "--runner",
                str(runner),
                "--evidence-root",
                str(evidence_root),
                "--timestamp",
                timestamp,
            )

            self.assertEqual(84, result.returncode, result.stderr + result.stdout)
            self.assertIn("MOBILE_E2E_LONG_BLOCKED", result.stderr)
            self.assertIn("BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT", result.stderr)
            summary = evidence_root / "driver-mobile" / timestamp / "SUMMARY.md"
            log_file = evidence_root / "driver-mobile" / timestamp / "e2e.log"
            self.assertTrue(summary.exists(), result.stderr + result.stdout)
            self.assertTrue(log_file.exists(), result.stderr + result.stdout)
            self.assertIn("Classificacao: BLOCKED_VALIDATION_IOS_WIRELESS_PUBLISH_PORT", summary.read_text(encoding="utf-8"))

    def test_invalid_role_lists_valid_mobile_roles(self) -> None:
        result = run_script("--dry-run", "--role", "admin-web")

        self.assertEqual(2, result.returncode, result.stderr + result.stdout)
        self.assertIn("Papel invalido: admin-web", result.stderr)
        self.assertIn("driver-mobile", result.stderr)
        self.assertIn("guardian-mobile", result.stderr)
        self.assertIn("operational-fixture-mobile", result.stderr)


if __name__ == "__main__":
    unittest.main()
