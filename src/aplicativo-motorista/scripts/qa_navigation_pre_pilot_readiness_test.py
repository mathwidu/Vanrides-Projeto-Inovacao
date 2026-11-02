#!/usr/bin/env python3
from __future__ import annotations

import json
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path
from unittest import mock

import qa_navigation_pre_pilot_readiness as readiness
import qa_navigation_tts_evidence as tts_evidence


ROOT = Path(__file__).resolve().parents[1]
SCRIPT = ROOT / "scripts/qa_navigation_pre_pilot_readiness.py"
QA_GATE = ROOT / "scripts/qa_gate_card.sh"


class NavigationPrePilotReadinessContractTest(unittest.TestCase):
    def test_script_exists_and_is_executable(self) -> None:
        self.assertTrue(SCRIPT.exists(), "pre-pilot readiness script is missing")
        mode = SCRIPT.stat().st_mode
        self.assertTrue(mode & stat.S_IXUSR, "pre-pilot readiness script is not executable")

    def test_check_only_reports_hold_until_human_audio_is_confirmed(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("NAVIGATION_PRE_PILOT_READINESS", result.stdout)
        self.assertIn("status=hold", result.stdout)
        self.assertIn("contract_status=pass", result.stdout)
        self.assertIn("gate_execution_status=not_run", result.stdout)
        self.assertIn("contract_checks=pass", result.stdout)
        self.assertIn("device_evidence_status=not_provided", result.stdout)
        self.assertIn("route_plan_evidence_status=coordinator_required", result.stdout)
        self.assertIn("human_audio_status=waiting_human", result.stdout)
        self.assertIn("audio_human_confirmed=false", result.stdout)
        self.assertIn("external_pilot_ready=false", result.stdout)
        self.assertIn("field_ready=false", result.stdout)

    def test_report_writes_markdown_and_json_summary(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            evidence_dir = Path(temp) / "pre-pilot-readiness"

            result = subprocess.run(
                [str(SCRIPT), "--report", "--output-dir", str(evidence_dir)],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stderr + result.stdout)
            self.assertIn("NAVIGATION_PRE_PILOT_READINESS_REPORT_OK", result.stdout)
            report = evidence_dir / "navigation-pre-pilot-readiness.md"
            data_file = evidence_dir / "navigation-pre-pilot-readiness.json"
            self.assertTrue(report.exists(), result.stderr + result.stdout)
            self.assertTrue(data_file.exists(), result.stderr + result.stdout)

            data = json.loads(data_file.read_text(encoding="utf-8"))
            self.assertEqual("APP-NAV-PRE-PILOT-READINESS-001", data["card"])
            self.assertEqual("hold", data["status"])
            self.assertEqual("pass", data["contractStatus"])
            self.assertTrue(data["contractChecksPassed"])
            self.assertEqual("not_run", data["gateExecutionStatus"])
            self.assertEqual("hold", data["readinessStatus"])
            self.assertEqual("not_provided", data["deviceEvidenceStatus"])
            self.assertEqual("coordinator_required", data["routePlanEvidenceStatus"])
            self.assertEqual("waiting_human", data["humanAudioStatus"])
            self.assertFalse(data["audioHumanConfirmed"])
            self.assertFalse(data["externalPilotReady"])
            self.assertFalse(data["fieldReady"])
            self.assertFalse(data["usesDevice"])
            self.assertFalse(data["usesBackend"])
            self.assertFalse(data["usesVps"])
            self.assertTrue(data["checks"]["navigationDriveSimulation"]["passed"])
            self.assertTrue(data["checks"]["navigationActionAutoSync"]["passed"])
            self.assertTrue(data["checks"]["navigationTtsDeviceContract"]["passed"])
            self.assertTrue(data["checks"]["navigationAudioOperatorHandoff"]["passed"])
            self.assertNotIn("routePlanPayloadContract", data["checks"])
            self.assertNotIn("routePlanPayloadE2e", data["checks"])
            self.assertEqual("not_run", data["checks"]["gateExecution"]["status"])
            self.assertFalse(data["checks"]["humanAudioConfirmation"]["passed"])

            text = report.read_text(encoding="utf-8")
            self.assertIn("APP-NAV-AUDIO-CONFIRM-001", text)
            self.assertIn("HOLD", text)
            self.assertIn("operatorHeardPtBr=false", text)
            self.assertIn("nao libera parceiro", text)

    def test_check_only_has_no_workspace_or_ignored_output_dependency(self) -> None:
        script = SCRIPT.read_text(encoding="utf-8")

        self.assertNotIn("WORKSPACE_ROOT", script)
        self.assertNotIn("20260706-tts-sim-iphone16e", script)
        self.assertNotIn('ROOT / "output/navigation-tts-device-smoke', script)
        self.assertNotIn("qa_route_plan_payload_contract.py", script)

    def test_forged_sibling_route_script_is_never_a_local_gate_input(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            workspace = Path(temp)
            app_root = workspace / "app"
            scripts = app_root / "scripts"
            backlog = app_root / "backlog"
            scripts.mkdir(parents=True)
            backlog.mkdir()

            required_files = {
                scripts / "qa_navigation_drive_sim_smoke.sh": (
                    "#!/usr/bin/env bash\nexit 0\n"
                ),
                scripts / "qa_navigation_action_auto_sync_smoke.py": (
                    "#!/usr/bin/env python3\nraise SystemExit(0)\n"
                ),
                scripts / "qa_navigation_tts_device_smoke.sh": (
                    "#!/usr/bin/env bash\nexit 0\n"
                ),
                scripts / "qa_navigation_audio_operator_handoff_test.py": (
                    "#!/usr/bin/env python3\nraise SystemExit(0)\n"
                ),
            }
            for path, content in required_files.items():
                path.write_text(content, encoding="utf-8")
                path.chmod(0o755)

            forged_sibling = workspace / "scripts/qa_route_plan_payload_contract.py"
            forged_sibling.parent.mkdir()
            forged_sibling.write_text(
                "print('ROUTE_PLAN_PAYLOAD_CONTRACT_OK forged')\n",
                encoding="utf-8",
            )

            with mock.patch.object(readiness, "ROOT", app_root):
                data = readiness.build_summary()

            self.assertEqual("pass", data["contractStatus"])
            self.assertEqual("not_run", data["gateExecutionStatus"])
            self.assertEqual(
                "coordinator_required",
                data["routePlanEvidenceStatus"],
            )
            self.assertNotIn("routePlanPayloadContract", data["checks"])
            self.assertNotIn("routePlanPayloadE2e", data["checks"])

    def test_script_tokens_cannot_turn_a_failing_contract_into_pass(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            app_root = Path(temp) / "app"
            scripts = app_root / "scripts"
            scripts.mkdir(parents=True)
            fixtures = {
                "qa_navigation_drive_sim_smoke.sh": (
                    "#!/usr/bin/env bash\n"
                    "# --check-only output/navigation-drive-sim\n"
                    "exit 99\n"
                ),
                "qa_navigation_action_auto_sync_smoke.py": (
                    "#!/usr/bin/env python3\n"
                    "# APP-NAV-ACTION-AUTO-SYNC-QA-001 foreground_only\n"
                    "raise SystemExit(0)\n"
                ),
                "qa_navigation_tts_device_smoke.sh": (
                    "#!/usr/bin/env bash\n"
                    "# --check-only --run-device NAVIGATION_TTS_DEVICE_SMOKE_OK\n"
                    "exit 0\n"
                ),
                "qa_navigation_audio_operator_handoff_test.py": (
                    "#!/usr/bin/env python3\nraise SystemExit(0)\n"
                ),
            }
            for name, content in fixtures.items():
                path = scripts / name
                path.write_text(content, encoding="utf-8")
                path.chmod(0o755)

            with mock.patch.object(readiness, "ROOT", app_root):
                data = readiness.build_summary()

            self.assertEqual("fail", data["contractStatus"])
            self.assertFalse(data["contractChecksPassed"])
            self.assertEqual(
                99,
                data["checks"]["navigationDriveSimulation"]["exitCode"],
            )

    def test_check_only_rejects_external_evidence_input(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only", "--tts-evidence-dir", "/tmp/legacy"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(2, result.returncode, result.stderr + result.stdout)
        self.assertIn("somente com --report", result.stderr + result.stdout)

    def test_check_only_rejects_output_dir_instead_of_ignoring_it(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--check-only", "--output-dir", "/tmp/ignored"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertEqual(2, result.returncode, result.stderr + result.stdout)
        self.assertIn("somente com --report", result.stderr + result.stdout)

    def test_legacy_or_forged_audio_package_never_confirms_human_readiness(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            evidence_dir = root / "legacy-evidence"
            report_dir = root / "report"
            evidence_dir.mkdir(parents=True)
            (evidence_dir / "audio-review-package.json").write_text(
                json.dumps(
                    {
                        "card": "APP-NAV-AUDIO-CONFIRM-001",
                        "operatorHeardPtBr": True,
                        "status": "CONFIRMED",
                    }
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    str(SCRIPT),
                    "--report",
                    "--output-dir",
                    str(report_dir),
                    "--tts-evidence-dir",
                    str(evidence_dir),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(85, result.returncode, result.stderr + result.stdout)
            self.assertIn(
                "NAVIGATION_PRE_PILOT_READINESS_EVIDENCE_INVALID",
                result.stderr,
            )
            data = json.loads(
                (report_dir / "navigation-pre-pilot-readiness.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual("invalid", data["deviceEvidenceStatus"])
            self.assertEqual("waiting_human", data["humanAudioStatus"])
            self.assertFalse(data["audioHumanConfirmed"])
            self.assertFalse(data["fieldReady"])
            self.assertFalse(data["externalPilotReady"])

    def test_verified_device_evidence_still_cannot_self_confirm_human_audio(self) -> None:
        verified_manifest = {
            "runId": "run-technical-001",
            "sourceGitSha": "a" * 40,
            "sourceGitTree": "b" * 40,
            "logSha256": "c" * 64,
            "deviceTargetPlatform": "ios",
        }
        with mock.patch.object(
            readiness.tts_evidence,
            "verify_manifest",
            return_value=verified_manifest,
        ) as verify_manifest:
            data = readiness.build_summary(Path("/tmp/technical-evidence"))

        self.assertEqual("integrity_verified", data["deviceEvidenceStatus"], data)
        self.assertEqual("waiting_human", data["humanAudioStatus"])
        self.assertFalse(data["audioHumanConfirmed"])
        self.assertFalse(data["fieldReady"])
        self.assertFalse(data["externalPilotReady"])
        self.assertEqual("hold", data["readinessStatus"])
        self.assertNotIn("operatorConfirmationReported", data)
        verify_manifest.assert_called_once_with(
            Path("/tmp/technical-evidence").resolve(),
            require_current_source=True,
        )

    def test_malformed_explicit_evidence_is_reported_as_invalid_not_gate_failure(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            root = Path(temp)
            evidence_dir = root / "malformed"
            report_dir = root / "report"
            evidence_dir.mkdir()
            (evidence_dir / tts_evidence.MANIFEST_FILE_NAME).write_text(
                "{not-json",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    str(SCRIPT),
                    "--report",
                    "--output-dir",
                    str(report_dir),
                    "--tts-evidence-dir",
                    str(evidence_dir),
                ],
                cwd=ROOT,
                text=True,
                capture_output=True,
                check=False,
            )

            self.assertEqual(85, result.returncode, result.stderr + result.stdout)
            self.assertIn(
                "NAVIGATION_PRE_PILOT_READINESS_EVIDENCE_INVALID",
                result.stderr,
            )
            data = json.loads(
                (report_dir / "navigation-pre-pilot-readiness.json").read_text(
                    encoding="utf-8"
                )
            )
            self.assertEqual("invalid", data["deviceEvidenceStatus"])
            self.assertEqual("pass", data["contractStatus"])
            self.assertEqual("not_run", data["gateExecutionStatus"])
            self.assertEqual("hold", data["readinessStatus"])

    def test_output_dir_requires_path_for_report(self) -> None:
        result = subprocess.run(
            [str(SCRIPT), "--report", "--output-dir"],
            cwd=ROOT,
            text=True,
            capture_output=True,
            check=False,
        )

        self.assertNotEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("--output-dir", result.stderr + result.stdout)

    def test_gate_runs_check_only_contract(self) -> None:
        gate = QA_GATE.read_text(encoding="utf-8")

        self.assertIn("qa_navigation_pre_pilot_readiness_test.py", gate)
        self.assertIn("qa_navigation_pre_pilot_readiness.py", gate)
        self.assertIn("--check-only", gate)
        self.assertNotIn("qa_navigation_pre_pilot_readiness.py\" --report", gate)
        self.assertNotIn("--tts-evidence-dir", gate)


if __name__ == "__main__":
    unittest.main()
