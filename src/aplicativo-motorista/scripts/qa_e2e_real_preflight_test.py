#!/usr/bin/env python3
from __future__ import annotations

import os
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_e2e_real_preflight.sh"
GATE = ROOT / "scripts/qa_gate_card.sh"


def run_script(*args: str) -> subprocess.CompletedProcess[str]:
    if not SCRIPT.exists():
        return subprocess.CompletedProcess(
            ["bash", str(SCRIPT), *args],
            127,
            "",
            f"{SCRIPT} missing",
        )

    env = os.environ.copy()
    env.pop("RUN_E2E", None)
    env.pop("VANRIDES_E2E_PREFLIGHT_ONLY", None)
    return subprocess.run(
        ["bash", str(SCRIPT), *args],
        cwd=ROOT,
        env=env,
        text=True,
        capture_output=True,
    )


class E2eRealPreflightTest(unittest.TestCase):
    def test_check_only_validates_contract_without_creating_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_root = Path(temp) / "evidence"

            result = run_script("--check-only", "--evidence-root", str(evidence_root))

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertIn("E2E_REAL_PREFLIGHT_CONTRACT_OK roles=4 mobile=3 web=1", result.stdout)
            self.assertFalse(evidence_root.exists(), "check-only must not create evidence")

    def test_dry_run_driver_writes_role_summary_without_running_flutter(self) -> None:
        timestamp = "20260706T120000Z"
        with tempfile.TemporaryDirectory() as temp:
            evidence_root = Path(temp) / "e2e-role-smoke"

            result = run_script(
                "--dry-run",
                "--role",
                "driver-mobile",
                "--device",
                "macos",
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
            self.assertIn("API: https://api-dev.example", contents)
            self.assertIn("Device: macos", contents)
            self.assertIn("VANRIDES_E2E_PREFLIGHT_ONLY=1", contents)
            self.assertIn("scripts/run_dev_e2e.sh integration_test/motorista/all.dart macos", contents)

    def test_invalid_role_reports_valid_roles(self) -> None:
        result = run_script("--dry-run", "--role", "unknown-role")

        self.assertEqual(2, result.returncode, result.stderr + result.stdout)
        self.assertIn("Papel invalido: unknown-role", result.stderr)
        self.assertIn("driver-mobile", result.stderr)
        self.assertIn("guardian-mobile", result.stderr)

    def test_card_gate_runs_contract_check_without_real_e2e(self) -> None:
        gate = GATE.read_text(encoding="utf-8")

        self.assertIn("qa_e2e_real_preflight_test.py", gate)
        self.assertIn("qa_e2e_real_preflight.sh", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("--run-preflight", gate)


if __name__ == "__main__":
    unittest.main()
