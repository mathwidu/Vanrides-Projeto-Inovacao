#!/usr/bin/env python3
from __future__ import annotations

import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_navigation_tts_device_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
DOC = ROOT / "backlog/QA-Navigation-TTS-Device-Smoke.md"
DRIVER = ROOT / "test_driver/navigation_tts_device_smoke_driver.dart"
INTEGRATION_TEST = ROOT / "integration_test/navigation_tts_device_smoke_test.dart"


class NavigationTtsDeviceSmokeContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(
            SCRIPT.exists(),
            "scripts/qa_navigation_tts_device_smoke.sh is missing",
        )

        mode = SCRIPT.stat().st_mode

        self.assertTrue(
            mode & stat.S_IXUSR,
            "scripts/qa_navigation_tts_device_smoke.sh is not executable",
        )

    def test_driver_and_integration_test_exist(self) -> None:
        self.assertTrue(
            DRIVER.exists(),
            "test_driver/navigation_tts_device_smoke_driver.dart is missing",
        )
        self.assertTrue(
            INTEGRATION_TEST.exists(),
            "integration_test/navigation_tts_device_smoke_test.dart is missing",
        )

    def test_check_only_passes_without_device(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("NAVIGATION_TTS_DEVICE_SMOKE_OK", result.stdout)
        self.assertIn("evidence_root=output/navigation-tts-device-smoke", result.stdout)

    def test_card_gate_runs_contract_only(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_tts_device_smoke_test.py", gate)
        self.assertIn("qa_navigation_tts_device_smoke.sh", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_navigation_tts_device_smoke.sh\" --run-device", gate)

    def test_run_device_blocks_unknown_flutter_device(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--run-device", "--device", "vanrides-missing-device"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
            timeout=30,
        )

        self.assertEqual(result.returncode, 84, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_DEVICE", result.stderr + result.stdout)

    def test_real_mode_uses_tts_flags_and_records_audible_marker(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")
        integration_test = INTEGRATION_TEST.read_text(encoding="utf-8")

        self.assertIn("--dart-define=VANRIDES_NAV_TTS_DEVICE_SMOKE=true", script)
        self.assertIn("flutter drive", script)
        self.assertIn("--driver=test_driver/navigation_tts_device_smoke_driver.dart", script)
        self.assertIn("--target=integration_test/navigation_tts_device_smoke_test.dart", script)
        self.assertIn("NAVIGATION_TTS_DEVICE_SMOKE_AUDIO_READY", integration_test)
        self.assertIn("VANRIDES_NAV_TTS_SOURCE_SHA", script)
        self.assertIn("VANRIDES_NAV_TTS_SOURCE_TREE", script)
        self.assertIn("VANRIDES_NAV_TTS_RUN_ID", script)
        self.assertIn("source_sha=$_sourceGitSha", integration_test)
        self.assertIn("source_tree=$_sourceGitTree", integration_test)
        self.assertIn("run_id=$_runId", integration_test)
        self.assertIn("DriverHomeNavigationPanel", integration_test)
        self.assertIn("DriverNavigationPanel.repeatInstructionKey", integration_test)

    def test_rerun_rejects_existing_output_before_touching_evidence(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            output_dir = Path(temp) / "existing-run"
            output_dir.mkdir()
            evidence = {
                "navigation-tts-device-smoke.log": b"original log\n",
                "audio-confirmation.md": b"original checklist\n",
                "navigation-tts-device-smoke.provenance.json": b'{"original":true}\n',
            }
            for name, content in evidence.items():
                (output_dir / name).write_bytes(content)

            result = subprocess.run(
                [
                    str(SCRIPT),
                    "--run-device",
                    "--output-dir",
                    str(output_dir),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
                timeout=30,
            )

            self.assertEqual(result.returncode, 87, result.stderr + result.stdout)
            self.assertIn(
                "NAVIGATION_TTS_OUTPUT_DIR_ALREADY_EXISTS",
                result.stderr + result.stdout,
            )
            for name, content in evidence.items():
                self.assertEqual(content, (output_dir / name).read_bytes())

    def test_options_that_require_values_fail_with_code_2(self) -> None:
        for option in ("--device", "--output-dir"):
            with self.subTest(option=option):
                result = subprocess.run(
                    [str(SCRIPT), option],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=False,
                )

                self.assertEqual(2, result.returncode, result.stderr + result.stdout)
                self.assertIn("ARGUMENT_ERROR", result.stderr)

    def test_conflicting_modes_fail_closed_in_both_orders(self) -> None:
        for args in (
            ("--run-device", "--check-only"),
            ("--check-only", "--run-device"),
            ("--list", "--check-only"),
        ):
            with self.subTest(args=args):
                result = subprocess.run(
                    [str(SCRIPT), *args],
                    cwd=ROOT,
                    text=True,
                    capture_output=True,
                    check=False,
                )

                self.assertEqual(2, result.returncode, result.stderr + result.stdout)
                self.assertIn("mutuamente exclusivos", result.stderr)

    def test_help_documents_device_and_output_options(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--help"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("--run-device", result.stdout)
        self.assertIn("--device", result.stdout)
        self.assertIn("--output-dir", result.stdout)
        self.assertIn("VANRIDES_NAV_TTS_DEVICE_SMOKE_DIR", result.stdout)
        self.assertIn("VANRIDES_NAV_TTS_RUN_ID", result.stdout)

    def test_doc_records_limits_and_evidence_policy(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("BLOCKED_VALIDATION_DEVICE", doc)
        self.assertIn("output/navigation-tts-device-smoke/", doc)
        self.assertIn("audivel", doc)
        self.assertIn("nao promove campo real", doc)
        self.assertIn("VANRIDES_NAV_TTS_DEVICE_SMOKE=true", doc)
        self.assertIn("source_sha", doc)
        self.assertIn("source_tree", doc)
        self.assertIn("run_id", doc)
        self.assertIn("nao e sobrescrito", doc)


if __name__ == "__main__":
    unittest.main()
