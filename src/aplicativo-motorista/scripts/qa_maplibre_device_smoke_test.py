#!/usr/bin/env python3
from __future__ import annotations

import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_maplibre_device_smoke.sh"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"
DOC = ROOT / "backlog/QA-MapLibre-Device-Smoke.md"
DRIVER = ROOT / "test_driver/maplibre_device_smoke_driver.dart"
INTEGRATION_TEST = ROOT / "integration_test/maplibre_device_smoke_test.dart"


class MapLibreDeviceSmokeContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(SCRIPT.exists(), "scripts/qa_maplibre_device_smoke.sh is missing")

        mode = SCRIPT.stat().st_mode

        self.assertTrue(
            mode & stat.S_IXUSR,
            "scripts/qa_maplibre_device_smoke.sh is not executable",
        )

    def test_publish_port_driver_file_exists(self) -> None:
        self.assertTrue(
            DRIVER.exists(),
            "test_driver/maplibre_device_smoke_driver.dart is missing",
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
        self.assertIn("MAPLIBRE_DEVICE_SMOKE_OK", result.stdout)

    def test_card_gate_runs_contract_only(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_maplibre_device_smoke.sh", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_maplibre_device_smoke.sh\" --run-device", gate)

    def test_real_mode_uses_maplibre_flags_and_blocks_missing_device(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("--dart-define=VANRIDES_MAPLIBRE_PROOF=true", script)
        self.assertIn("--dart-define=VANRIDES_MAPLIBRE_DEVICE_SMOKE=true", script)
        self.assertIn("BLOCKED_VALIDATION_DEVICE", script)
        self.assertIn("output/maplibre-device-smoke", script)

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

    def test_run_device_publish_port_blocks_unknown_device_portably(self) -> None:
        result = subprocess.run(
            [
                str(SCRIPT),
                "--run-device",
                "--publish-port",
                "--device",
                "vanrides-missing-device",
            ],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
            timeout=30,
        )

        self.assertEqual(result.returncode, 84, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_DEVICE", result.stderr + result.stdout)
        self.assertNotIn("bad substitution", result.stderr + result.stdout)

    def test_real_mode_classifies_wireless_ios_start_failure(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("wirelessly tethered iOS device", script)
        self.assertIn("No supported devices found", script)
        self.assertIn("No devices are connected", script)
        self.assertIn("developer disk image could not be mounted", script)
        self.assertIn("Timed out waiting for all destinations", script)

    def test_help_documents_publish_port_opt_in(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--help"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(result.returncode, 0, result.stderr + result.stdout)
        self.assertIn("--publish-port", result.stdout)
        self.assertIn("VANRIDES_MAPLIBRE_PUBLISH_PORT", result.stdout)

    def test_real_mode_supports_publish_port_opt_in(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertIn("VANRIDES_MAPLIBRE_PUBLISH_PORT", script)
        self.assertIn("--publish-port)", script)
        self.assertIn("flutter drive", script)
        self.assertIn("--driver=test_driver/maplibre_device_smoke_driver.dart", script)
        self.assertIn("--target=integration_test/maplibre_device_smoke_test.dart", script)
        self.assertIn('FLUTTER_DRIVE_ARGS+=("--publish-port")', script)
        self.assertIn('"${FLUTTER_DRIVE_ARGS[@]}"', script)

    def test_device_screenshot_waits_for_visual_ready_marker(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")
        integration_test = INTEGRATION_TEST.read_text(encoding="utf-8")

        self.assertIn("VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY", script)
        self.assertIn("VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY", integration_test)
        self.assertLess(
            integration_test.index("await tester.pump(const Duration(seconds: 2));"),
            integration_test.index("VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY"),
            "visual-ready marker must be emitted after an explicit visual settling pump",
        )
        self.assertLess(
            integration_test.index("VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY"),
            integration_test.index("await tester.pump(const Duration(seconds: 3));"),
            "app must stay open after visual-ready so simctl can capture the app, not SpringBoard",
        )

    def test_publish_port_bool_parsing_is_macos_bash_compatible(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertNotIn("${PUBLISH_PORT,,}", script)

    def test_qa_doc_records_promotion_limits(self) -> None:
        doc = DOC.read_text(encoding="utf-8")

        self.assertIn("MapLibre nao e default", doc)
        self.assertIn("BLOCKED_VALIDATION_DEVICE", doc)
        self.assertIn("output/maplibre-device-smoke/", doc)
        self.assertIn("style sintetico", doc)
        self.assertIn("tile service", doc)
        self.assertIn("--publish-port", doc)
        self.assertIn("VANRIDES_MAPLIBRE_PUBLISH_PORT", doc)


if __name__ == "__main__":
    unittest.main()
