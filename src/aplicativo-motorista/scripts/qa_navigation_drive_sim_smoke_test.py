#!/usr/bin/env python3
from __future__ import annotations

import json
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_navigation_drive_sim_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
TOOL = ROOT / "tool/navigation_drive_sim_report.dart"


class NavigationDriveSimSmokeContractTest(unittest.TestCase):
    def test_script_and_tool_exist(self) -> None:
        self.assertTrue(SCRIPT.exists(), "qa_navigation_drive_sim_smoke.sh is missing")
        self.assertTrue(TOOL.exists(), "navigation_drive_sim_report.dart is missing")
        mode = SCRIPT.stat().st_mode
        self.assertTrue(mode & stat.S_IXUSR, "smoke script is not executable")

    def test_check_only_passes_without_device_backend_or_audio(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("NAVIGATION_DRIVE_SIM_SMOKE_OK", result.stdout)
        self.assertIn("steps=6", result.stdout)
        self.assertIn("evidence_root=output/navigation-drive-sim", result.stdout)

    def test_run_writes_markdown_and_json_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "drive-sim"

            result = subprocess.run(
                [str(SCRIPT), "--run", "--output-dir", str(evidence_dir)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertIn("NAVIGATION_DRIVE_SIM_EVIDENCE_OK", result.stdout)
            report = evidence_dir / "navigation-drive-sim.md"
            data_file = evidence_dir / "navigation-drive-sim.json"
            self.assertTrue(report.exists(), result.stderr + result.stdout)
            self.assertTrue(data_file.exists(), result.stderr + result.stdout)
            self.assertIn("APP-NAV-SIM-DRIVE-QA-001", report.read_text(encoding="utf-8"))
            data = json.loads(data_file.read_text(encoding="utf-8"))
            self.assertEqual("APP-NAV-SIM-DRIVE-QA-001", data["card"])
            self.assertFalse(data["fieldReady"])
            self.assertEqual(6, len(data["steps"]))

    def test_output_dir_requires_path(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--run", "--output-dir"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(2, result.returncode, result.stderr + result.stdout)
        self.assertIn("--output-dir requer um caminho", result.stderr)

    def test_gate_runs_check_only_contract(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_drive_sim_smoke_test.py", gate)
        self.assertIn("qa_navigation_drive_sim_smoke.sh", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_navigation_drive_sim_smoke.sh\" --run", gate)


if __name__ == "__main__":
    unittest.main()
