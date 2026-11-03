#!/usr/bin/env python3
from __future__ import annotations

import json
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_navigation_action_auto_sync_smoke.py"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"


class NavigationActionAutoSyncSmokeContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(SCRIPT.exists(), "action auto-sync smoke script is missing")
        mode = SCRIPT.stat().st_mode
        self.assertTrue(mode & stat.S_IXUSR, "action auto-sync smoke is not executable")

    def test_check_only_runs_foreground_only_proof(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("NAVIGATION_ACTION_AUTO_SYNC_SMOKE_OK", result.stdout)
        self.assertIn("home_load=true", result.stdout)
        self.assertIn("foreground_resume=true", result.stdout)
        self.assertIn("manual_fallback=true", result.stdout)
        self.assertIn("background_service=false", result.stdout)

    def test_run_writes_markdown_and_json_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "action-auto-sync"

            result = subprocess.run(
                [str(SCRIPT), "--run", "--output-dir", str(evidence_dir)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertIn("NAVIGATION_ACTION_AUTO_SYNC_EVIDENCE_OK", result.stdout)
            report = evidence_dir / "navigation-action-auto-sync.md"
            data_file = evidence_dir / "navigation-action-auto-sync.json"
            self.assertTrue(report.exists(), result.stderr + result.stdout)
            self.assertTrue(data_file.exists(), result.stderr + result.stdout)

            data = json.loads(data_file.read_text(encoding="utf-8"))
            self.assertEqual("APP-NAV-ACTION-AUTO-SYNC-QA-001", data["card"])
            self.assertTrue(data["foregroundOnly"])
            self.assertFalse(data["backgroundService"])
            self.assertFalse(data["fieldReady"])
            self.assertTrue(data["scenarios"]["homeLoadAutoSync"])
            self.assertTrue(data["scenarios"]["foregroundResumeAutoSync"])
            self.assertTrue(data["scenarios"]["manualFallbackAfterFailure"])
            self.assertEqual(data["sourceTestFile"], "test/features/driver_trip/home_motorista_active_trip_test.dart")
            self.assertIn("foreground-only", report.read_text(encoding="utf-8"))

    def test_output_dir_requires_path(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--run", "--output-dir"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("--output-dir", result.stderr + result.stdout)

    def test_gate_runs_check_only_contract(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_action_auto_sync_smoke_test.py", gate)
        self.assertIn("qa_navigation_action_auto_sync_smoke.py", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_navigation_action_auto_sync_smoke.py\" --run", gate)


if __name__ == "__main__":
    unittest.main()
