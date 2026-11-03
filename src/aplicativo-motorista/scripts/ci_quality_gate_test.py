#!/usr/bin/env python3
from __future__ import annotations

import os
import shutil
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
WORKFLOW = ROOT.parents[1] / ".github/workflows/aplicativo.yml"
CI_SCRIPT = ROOT / "scripts/ci_quality_gate.sh"
CODEX_CLOUD_DOC = ROOT / "docs/codex-cloud.md"
CODEX_CLOUD_SETUP = ROOT / "scripts/codex_cloud_setup.sh"


class FlutterCiQualityGateWiringTest(unittest.TestCase):
    def _prepare_card_gate_harness(self, root: Path) -> tuple[Path, Path]:
        scripts_dir = root / "scripts"
        fake_bin = root / "fake-bin"
        scripts_dir.mkdir()
        fake_bin.mkdir()

        shutil.copy2(ROOT / "scripts/qa_gate_card.sh", scripts_dir / "qa_gate_card.sh")

        (scripts_dir / "e2e_preflight.sh").write_text(
            """#!/usr/bin/env bash
e2e_resolve_device() { printf '%s\\n' "$1"; }
e2e_preflight_run() { printf '%s|%s|%s\\n' "$1" "$2" "$3" > "$TRACE_FILE"; }
""",
            encoding="utf-8",
        )

        direct_contracts = (
            "qa_e2e_role_matrix_smoke.sh",
            "qa_e2e_real_preflight.sh",
            "qa_visual_matrix_smoke.sh",
            "qa_performance_smoke.sh",
            "qa_navigation_drive_sim_smoke.sh",
            "qa_navigation_action_auto_sync_smoke.py",
            "qa_navigation_tts_device_smoke.sh",
            "qa_navigation_audio_confirmation_review.py",
            "qa_navigation_pre_pilot_readiness.py",
            "qa_maplibre_device_smoke.sh",
            "qa_maplibre_style_tile_smoke.sh",
            "qa_maplibre_tile_fixture_smoke.sh",
            "quality_gate.sh",
        )
        executable_stub = "#!/usr/bin/env bash\nexit 0\n"
        for name in direct_contracts:
            path = scripts_dir / name
            path.write_text(executable_stub, encoding="utf-8")
            path.chmod(0o755)

        for name in ("python3", "flutter"):
            path = fake_bin / name
            path.write_text(executable_stub, encoding="utf-8")
            path.chmod(0o755)

        return scripts_dir, fake_bin

    def test_workflow_calls_dedicated_ci_gate(self) -> None:
        workflow = WORKFLOW.read_text(encoding="utf-8")

        self.assertIn("run: bash scripts/check_app.sh", workflow)
        self.assertNotIn("run: ./scripts/qa_gate_card.sh --no-e2e", workflow)

    def test_ci_gate_delegates_to_card_gate_without_e2e(self) -> None:
        self.assertTrue(CI_SCRIPT.exists(), "scripts/ci_quality_gate.sh is missing")

        script = CI_SCRIPT.read_text(encoding="utf-8")

        self.assertIn("qa_gate_card.sh", script)
        self.assertIn("--no-e2e", script)
        self.assertIn("ci_quality_gate_test.py", script)
        self.assertIn("qa_trip_live_location_real_backend_test.py", script)
        self.assertIn("admin_portal_bootstrap_contract_test.mjs", script)
        self.assertIn("resolve_node_bin", script)
        self.assertIn("XDG_CACHE_HOME", script)
        self.assertIn("codex-primary-runtime/dependencies/node/bin/node", script)
        self.assertIn('"$NODE_BIN" --test', script)
        qa_gate = (ROOT / "scripts/qa_gate_card.sh").read_text(encoding="utf-8")
        self.assertIn("e2e_preflight_test.py", qa_gate)
        self.assertIn("qa_e2e_real_preflight_test.py", qa_gate)
        self.assertIn("qa_e2e_role_matrix_smoke.sh", qa_gate)
        self.assertIn("qa_e2e_real_preflight.sh", qa_gate)
        self.assertIn("qa_visual_matrix_smoke.sh", qa_gate)
        self.assertIn("qa_performance_smoke.sh", qa_gate)
        self.assertIn("--check-only", qa_gate)
        self.assertNotIn("--run-preflight", qa_gate)
        self.assertIn("load_local_e2e_env", qa_gate)
        self.assertNotIn('source "$ENV_FILE"', qa_gate)

    def test_no_e2e_does_not_source_local_e2e_env(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            (scripts_dir / ".dev.env.local").write_text(
                "echo LOCAL_E2E_ENV_CONTAMINATION\nexit 97\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh"), "--no-e2e"],
                cwd=root,
                env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}"},
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(0, result.returncode, output)
            self.assertNotIn("LOCAL_E2E_ENV_CONTAMINATION", output)

    def test_e2e_cli_flags_override_local_env_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            trace_file = root / "preflight.trace"
            (scripts_dir / ".dev.env.local").write_text(
                "\n".join(
                    (
                        "# dotenv publico permitido",
                        'export VANRIDES_API_BASE_URL="https://env.invalid"',
                        "E2E_SUITE='integration_test/from_env.dart'",
                        'VANRIDES_TEST_DEVICE="env-device"',
                        "RUN_E2E=0",
                        "VANRIDES_E2E_PREFLIGHT_ONLY=1 # evita Flutter real",
                    )
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                [
                    "bash",
                    str(scripts_dir / "qa_gate_card.sh"),
                    "--e2e",
                    "--api",
                    "https://cli.example",
                    "--suite",
                    "integration_test/from_cli.dart",
                    "--device",
                    "cli-device",
                ],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "TRACE_FILE": str(trace_file),
                },
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(0, result.returncode, output)
            self.assertEqual(
                "https://cli.example|integration_test/from_cli.dart|cli-device\n",
                trace_file.read_text(encoding="utf-8"),
            )

    def test_e2e_dotenv_accepts_comments_export_and_quoted_values(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            trace_file = root / "preflight.trace"
            (scripts_dir / ".dev.env.local").write_text(
                "\n".join(
                    (
                        "  # comentario com espacos",
                        'export VANRIDES_API_BASE_URL="https://dotenv.example"',
                        "E2E_SUITE='integration_test/from_dotenv.dart'",
                        "VANRIDES_TEST_DEVICE=dotenv-device",
                        "VANRIDES_E2E_PREFLIGHT_ONLY=1 # comentario inline",
                    )
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh"), "--e2e"],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "TRACE_FILE": str(trace_file),
                },
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(0, result.returncode, output)
            self.assertEqual(
                "https://dotenv.example|integration_test/from_dotenv.dart|dotenv-device\n",
                trace_file.read_text(encoding="utf-8"),
            )

    def test_explicit_run_e2e_env_cannot_be_disabled_by_dotenv(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            trace_file = root / "preflight.trace"
            (scripts_dir / ".dev.env.local").write_text(
                "RUN_E2E=0\nVANRIDES_E2E_PREFLIGHT_ONLY=1\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh")],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "RUN_E2E": "1",
                    "TRACE_FILE": str(trace_file),
                },
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(0, result.returncode, output)
            self.assertTrue(trace_file.exists(), output)

    def test_e2e_dotenv_rejects_internal_cli_namespace(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            trace_file = root / "preflight.trace"
            (scripts_dir / ".dev.env.local").write_text(
                "\n".join(
                    (
                        "CLI_API_BASE_URL=https://malicious.invalid",
                        "CLI_E2E_SUITE=integration_test/malicious.dart",
                        "CLI_DEVICE=malicious-device",
                        "CLI_RUN_E2E=0",
                    )
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh"), "--e2e"],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "TRACE_FILE": str(trace_file),
                },
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(2, result.returncode, output)
            self.assertIn("DOTENV_ERROR", result.stderr)
            self.assertIn("CLI_API_BASE_URL", result.stderr)
            self.assertFalse(trace_file.exists())

    def test_e2e_dotenv_rejects_command_substitution_without_executing_it(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)
            trace_file = root / "preflight.trace"
            command_marker = root / "dotenv-command-ran"
            (scripts_dir / ".dev.env.local").write_text(
                "\n".join(
                    (
                        f'VANRIDES_API_BASE_URL="$(touch {command_marker})"',
                        "VANRIDES_E2E_PREFLIGHT_ONLY=1",
                    )
                )
                + "\n",
                encoding="utf-8",
            )

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh"), "--e2e"],
                cwd=root,
                env={
                    **os.environ,
                    "PATH": f"{fake_bin}:{os.environ['PATH']}",
                    "TRACE_FILE": str(trace_file),
                },
                capture_output=True,
                text=True,
                check=False,
            )

            output = result.stdout + result.stderr
            self.assertEqual(2, result.returncode, output)
            self.assertIn("DOTENV_ERROR", result.stderr)
            self.assertIn("sintaxe insegura", result.stderr)
            self.assertFalse(command_marker.exists(), "dotenv executou command substitution")
            self.assertFalse(trace_file.exists())

    def test_options_that_require_values_fail_with_code_2(self) -> None:
        for option in ("--api", "--suite", "--device"):
            with self.subTest(option=option), tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                scripts_dir, fake_bin = self._prepare_card_gate_harness(root)

                result = subprocess.run(
                    ["bash", str(scripts_dir / "qa_gate_card.sh"), option],
                    cwd=root,
                    env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}"},
                    capture_output=True,
                    text=True,
                    check=False,
                )

                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("ARGUMENT_ERROR", result.stderr)

    def test_unknown_option_is_not_treated_as_legacy_device(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)

            result = subprocess.run(
                ["bash", str(scripts_dir / "qa_gate_card.sh"), "--unknown-option"],
                cwd=root,
                env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}"},
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn("ARGUMENT_ERROR", result.stderr)
            self.assertIn("opcao desconhecida", result.stderr)

    def test_conflicting_profiles_fail_closed_in_both_orders(self) -> None:
        for args in (("--e2e", "--no-e2e"), ("--no-e2e", "--e2e")):
            with self.subTest(args=args), tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                scripts_dir, fake_bin = self._prepare_card_gate_harness(root)

                result = subprocess.run(
                    ["bash", str(scripts_dir / "qa_gate_card.sh"), *args],
                    cwd=root,
                    env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}"},
                    capture_output=True,
                    text=True,
                    check=False,
                )

                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertIn("mutuamente exclusivos", result.stderr)

    def test_safe_legacy_positional_device_remains_supported(self) -> None:
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts_dir, fake_bin = self._prepare_card_gate_harness(root)

            result = subprocess.run(
                [
                    "bash",
                    str(scripts_dir / "qa_gate_card.sh"),
                    "--no-e2e",
                    "iPhone 16e",
                ],
                cwd=root,
                env={**os.environ, "PATH": f"{fake_bin}:{os.environ['PATH']}"},
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(0, result.returncode, result.stdout + result.stderr)

    def test_ci_gate_is_executable(self) -> None:
        self.assertTrue(CI_SCRIPT.exists(), "scripts/ci_quality_gate.sh is missing")

        mode = CI_SCRIPT.stat().st_mode

        self.assertTrue(mode & stat.S_IXUSR, "scripts/ci_quality_gate.sh is not executable")

    def test_codex_cloud_docs_recommend_dedicated_ci_gate(self) -> None:
        docs = CODEX_CLOUD_DOC.read_text(encoding="utf-8")

        self.assertIn(
            "Default gate for normal app cards:\n\n```bash\n./scripts/ci_quality_gate.sh\n```",
            docs,
        )
        self.assertIn("The CI entrypoint validates the workflow wiring", docs)
        self.assertIn("delegates to", docs)
        self.assertIn("`qa_gate_card.sh --no-e2e`", docs)

    def test_codex_cloud_setup_recommends_dedicated_ci_gate(self) -> None:
        setup = CODEX_CLOUD_SETUP.read_text(encoding="utf-8")

        self.assertIn("Recommended agent check: ./scripts/ci_quality_gate.sh", setup)
        self.assertNotIn(
            "Recommended agent check: ./scripts/qa_gate_card.sh --no-e2e",
            setup,
        )


if __name__ == "__main__":
    unittest.main()
