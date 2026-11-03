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
SCRIPT = ROOT / "scripts/qa_trip_live_location_real_backend.sh"
DART_TEST = ROOT / "tool/qa_trip_live_location_real_backend_test.dart"
CI_GATE = ROOT / "scripts/ci_quality_gate.sh"

CONSUMER_ENV_KEYS = {
    "VANRIDES_EXPECTED_APP_SHA",
    "VANRIDES_API_BASE_URL",
    "VANRIDES_CONSUMER_PHASE",
    "VANRIDES_CONSUMER_OK_EMAIL",
    "VANRIDES_CONSUMER_OK_PASSWORD",
    "VANRIDES_CONSUMER_OK_DRIVER_ID",
    "VANRIDES_CONSUMER_EMPTY_EMAIL",
    "VANRIDES_CONSUMER_EMPTY_PASSWORD",
    "VANRIDES_CONSUMER_EMPTY_DRIVER_ID",
    "VANRIDES_CONSUMER_DENIED_EMAIL",
    "VANRIDES_CONSUMER_DENIED_PASSWORD",
    "VANRIDES_CONSUMER_DENIED_DRIVER_ID",
}


class ProbeHarness:
    def __init__(self, module_path: str | None = None) -> None:
        self.temp = tempfile.TemporaryDirectory()
        self.base = Path(self.temp.name)
        self.repo = self.base / "app"
        self.root = self.repo / module_path if module_path else self.repo
        self.fake_bin = self.base / "fake-bin"
        self.trace = self.base / "flutter.args"
        (self.root / "scripts").mkdir(parents=True)
        (self.root / "tool").mkdir(parents=True)
        self.fake_bin.mkdir()

        shutil.copy2(SCRIPT, self.root / "scripts" / SCRIPT.name)
        shutil.copy2(DART_TEST, self.root / "tool" / DART_TEST.name)

        fake_flutter = self.fake_bin / "flutter"
        fake_flutter.write_text(
            "#!/usr/bin/env bash\n"
            "set -euo pipefail\n"
            "printf '%s\\n' \"$@\" > \"$TRACE_FILE\"\n",
            encoding="utf-8",
        )
        fake_flutter.chmod(0o755)

        subprocess.run(["git", "init", "-q", str(self.repo)], check=True)
        self._git("add", "scripts", "tool")
        self._git(
            "-c",
            "user.name=VanRides QA",
            "-c",
            "user.email=qa@vanrides.invalid",
            "commit",
            "-qm",
            "test fixture",
        )
        self.sha = self._git("rev-parse", "HEAD").stdout.strip()

    def close(self) -> None:
        self.temp.cleanup()

    def _git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(
            ["git", *args],
            cwd=self.root,
            check=True,
            capture_output=True,
            text=True,
        )

    def run(self, overrides: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
        env = os.environ.copy()
        for key in CONSUMER_ENV_KEYS:
            env.pop(key, None)
        env.update(
            {
                "PATH": f"{self.fake_bin}:{env['PATH']}",
                "TRACE_FILE": str(self.trace),
                "VANRIDES_EXPECTED_APP_SHA": self.sha,
                "VANRIDES_API_BASE_URL": "http://127.0.0.1:18081",
                "VANRIDES_CONSUMER_PHASE": "positive",
                "VANRIDES_CONSUMER_OK_EMAIL": "ok@vanrides.dev",
                "VANRIDES_CONSUMER_OK_PASSWORD": "positive-secret",
                "VANRIDES_CONSUMER_OK_DRIVER_ID": "11",
                "VANRIDES_CONSUMER_EMPTY_EMAIL": "empty@vanrides.dev",
                "VANRIDES_CONSUMER_EMPTY_PASSWORD": "empty-secret",
                "VANRIDES_CONSUMER_EMPTY_DRIVER_ID": "12",
            }
        )
        if overrides:
            for key, value in overrides.items():
                if value == "__DELETE__":
                    env.pop(key, None)
                else:
                    env[key] = value
        return subprocess.run(
            [str(self.root / "scripts" / SCRIPT.name)],
            cwd=self.root,
            env=env,
            capture_output=True,
            text=True,
            check=False,
        )


class TripLiveLocationRealBackendContractTest(unittest.TestCase):
    def setUp(self) -> None:
        self.harness = ProbeHarness()

    def tearDown(self) -> None:
        self.harness.close()

    def test_accepts_canonical_module_inside_shared_git_root(self) -> None:
        harness = ProbeHarness("src/aplicativo-motorista")
        try:
            result = harness.run()
            self.assertEqual(0, result.returncode, result.stdout + result.stderr)
            self.assertTrue(harness.trace.exists())
            target = harness.root / "tool" / DART_TEST.name
            target.write_text(target.read_text(encoding="utf-8") + "\n", encoding="utf-8")
            harness.trace.unlink()
            result = harness.run()
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn("app_worktree_not_clean", result.stderr)
            self.assertFalse(harness.trace.exists())
        finally:
            harness.close()

    def test_rejects_unexpected_nested_module_before_flutter(self) -> None:
        harness = ProbeHarness("unexpected/module")
        try:
            result = harness.run()
            self.assertEqual(2, result.returncode, result.stdout + result.stderr)
            self.assertIn("unexpected_app_worktree_root", result.stderr)
            self.assertFalse(harness.trace.exists())
        finally:
            harness.close()

    def test_script_exists_is_executable_and_is_wired_without_live_network(self) -> None:
        self.assertTrue(SCRIPT.is_file())
        self.assertTrue(SCRIPT.stat().st_mode & stat.S_IXUSR)
        self.assertTrue(DART_TEST.is_file())

        gate = CI_GATE.read_text(encoding="utf-8")
        self.assertIn("qa_trip_live_location_real_backend_test.py", gate)
        self.assertNotIn('qa_trip_live_location_real_backend.sh"', gate)

    def test_dart_uses_real_production_service_without_mock_client(self) -> None:
        source = DART_TEST.read_text(encoding="utf-8")

        self.assertIn("features/tracking/data/trip_live_location_service.dart", source)
        self.assertIn("TripLiveLocationService(", source)
        self.assertIn("class _StatusRecordingClient extends http.BaseClient", source)
        self.assertIn("lastStatusCode", source)
        self.assertNotIn("package:http/testing.dart", source)
        self.assertNotIn("MockClient", source)
        self.assertNotIn("print(", source)

    def test_positive_phase_passes_only_non_secret_values_as_flutter_arguments(self) -> None:
        result = self.harness.run()

        output = result.stdout + result.stderr
        self.assertEqual(0, result.returncode, output)
        args = self.harness.trace.read_text(encoding="utf-8")
        self.assertIn("tool/qa_trip_live_location_real_backend_test.dart", args)
        self.assertIn("VANRIDES_API_BASE_URL=http://127.0.0.1:18081", args)
        self.assertIn("VANRIDES_CONSUMER_PHASE=positive", args)
        self.assertIn("http_200=PASS http_204=PASS", output)
        self.assertIn("attestation_scope=consumer_only", output)
        self.assertIn("backend_binding=coordinator_required", output)
        for credential in (
            "ok@vanrides.dev",
            "positive-secret",
            "empty@vanrides.dev",
            "empty-secret",
        ):
            self.assertNotIn(credential, args)
            self.assertNotIn(credential, output)

    def test_negative_phase_uses_denied_fixture_without_exposing_credentials(self) -> None:
        result = self.harness.run(
            {
                "VANRIDES_CONSUMER_PHASE": "negative",
                "VANRIDES_CONSUMER_DENIED_EMAIL": "denied@vanrides.dev",
                "VANRIDES_CONSUMER_DENIED_PASSWORD": "denied-secret",
                "VANRIDES_CONSUMER_DENIED_DRIVER_ID": "13",
            }
        )

        output = result.stdout + result.stderr
        self.assertEqual(0, result.returncode, output)
        args = self.harness.trace.read_text(encoding="utf-8")
        self.assertIn("VANRIDES_CONSUMER_PHASE=negative", args)
        self.assertIn("http_404=PASS generic_exception=PASS", output)
        for credential in ("denied@vanrides.dev", "denied-secret"):
            self.assertNotIn(credential, args)
            self.assertNotIn(credential, output)

    def test_rejects_non_full_or_mismatched_sha_before_flutter(self) -> None:
        for invalid_sha in ("abc123", "f" * 40):
            with self.subTest(sha=invalid_sha):
                self.harness.trace.unlink(missing_ok=True)
                result = self.harness.run({"VANRIDES_EXPECTED_APP_SHA": invalid_sha})
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertFalse(self.harness.trace.exists())

    def test_rejects_dirty_worktree_before_flutter(self) -> None:
        target = self.harness.root / "tool" / DART_TEST.name
        target.write_text(target.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        result = self.harness.run()

        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("app_worktree_not_clean", result.stderr)
        self.assertFalse(self.harness.trace.exists())

    def test_rejects_assume_unchanged_before_flutter(self) -> None:
        relative = f"tool/{DART_TEST.name}"
        target = self.harness.root / relative
        self.harness._git("update-index", "--assume-unchanged", relative)
        target.write_text(target.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        result = self.harness.run()

        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("app_index_has_assume_unchanged_or_skip_worktree", result.stderr)
        self.assertFalse(self.harness.trace.exists())

    def test_rejects_skip_worktree_before_flutter(self) -> None:
        relative = f"tool/{DART_TEST.name}"
        target = self.harness.root / relative
        self.harness._git("update-index", "--skip-worktree", relative)
        target.write_text(target.read_text(encoding="utf-8") + "\n", encoding="utf-8")

        result = self.harness.run()

        self.assertEqual(2, result.returncode, result.stdout + result.stderr)
        self.assertIn("app_index_has_assume_unchanged_or_skip_worktree", result.stderr)
        self.assertFalse(self.harness.trace.exists())

    def test_neutralizes_all_inherited_git_environment_before_provenance_checks(self) -> None:
        result = self.harness.run(
            {
                "GIT_DIR": str(self.harness.base / "forged-git-dir"),
                "GIT_WORK_TREE": str(self.harness.base / "forged-worktree"),
                "GIT_NAMESPACE": "forged-namespace",
                "GIT_EXEC_PATH": str(self.harness.base / "forged-exec-path"),
                "GIT_CONFIG_COUNT": "1",
                "GIT_CONFIG_KEY_0": "core.fsmonitor",
                "GIT_CONFIG_VALUE_0": "true",
                "GIT_CONFIG_PARAMETERS": "'core.untrackedCache'='true'",
            }
        )

        self.assertEqual(0, result.returncode, result.stdout + result.stderr)
        self.assertTrue(self.harness.trace.exists())

    def test_rejects_non_loopback_or_ambiguous_base_urls(self) -> None:
        invalid_urls = (
            "https://127.0.0.1:18081",
            "http://localhost:18081",
            "http://192.168.1.10:18081",
            "http://127.0.0.1",
            "http://user:password@127.0.0.1:18081",
            "http://127.0.0.1:18081/api",
            "http://127.0.0.1:18081?target=remote",
        )
        for base_url in invalid_urls:
            with self.subTest(base_url=base_url):
                self.harness.trace.unlink(missing_ok=True)
                result = self.harness.run({"VANRIDES_API_BASE_URL": base_url})
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertFalse(self.harness.trace.exists())

    def test_rejects_invalid_phase_missing_inputs_and_non_positive_ids(self) -> None:
        cases = (
            {"VANRIDES_CONSUMER_PHASE": "other"},
            {"VANRIDES_CONSUMER_EMPTY_PASSWORD": "__DELETE__"},
            {"VANRIDES_CONSUMER_OK_DRIVER_ID": "0"},
            {
                "VANRIDES_CONSUMER_PHASE": "negative",
                "VANRIDES_CONSUMER_DENIED_EMAIL": "denied@vanrides.dev",
                "VANRIDES_CONSUMER_DENIED_PASSWORD": "secret",
                "VANRIDES_CONSUMER_DENIED_DRIVER_ID": "not-an-id",
            },
        )
        for overrides in cases:
            with self.subTest(overrides=overrides):
                self.harness.trace.unlink(missing_ok=True)
                result = self.harness.run(overrides)
                self.assertEqual(2, result.returncode, result.stdout + result.stderr)
                self.assertFalse(self.harness.trace.exists())


if __name__ == "__main__":
    unittest.main()
