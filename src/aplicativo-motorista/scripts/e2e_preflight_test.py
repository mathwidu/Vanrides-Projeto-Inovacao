#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "scripts/e2e_preflight.sh"


def run_bash(body: str, env: dict[str, str] | None = None) -> subprocess.CompletedProcess[str]:
    script = f"set -euo pipefail\nsource {HELPER}\n{body}"
    full_env = os.environ.copy()
    if env:
      full_env.update(env)
    return subprocess.run(
        ["bash", "-c", script],
        cwd=ROOT,
        env=full_env,
        text=True,
        capture_output=True,
    )


def write_fake_curl(temp_dir: Path, behavior: str) -> None:
    fake_curl = temp_dir / "curl"
    fake_curl.write_text(behavior, encoding="utf-8")
    fake_curl.chmod(fake_curl.stat().st_mode | stat.S_IXUSR)


class E2ePreflightTest(unittest.TestCase):
    def device_args(self, devices: list[dict], selected: str = "chosen") -> subprocess.CompletedProcess[str]:
        with tempfile.TemporaryDirectory() as temp:
            fake_flutter = Path(temp) / "flutter"
            fake_flutter.write_text(
                '#!/usr/bin/env python3\nimport os, sys\n'
                'assert sys.argv[1:] == ["devices", "--machine"]\n'
                'print(os.environ["TEST_FLUTTER_DEVICES"])\n',
                encoding="utf-8",
            )
            fake_flutter.chmod(0o700)
            return run_bash(
                'e2e_build_device_args "$TEST_SELECTED_DEVICE"; printf "%s\\n" "${E2E_DEVICE_ARGS[@]}"',
                env={
                    "PATH": f"{temp}:{os.environ['PATH']}",
                    "TEST_FLUTTER_DEVICES": json.dumps(devices),
                    "TEST_SELECTED_DEVICE": selected,
                },
            )

    def test_android_integration_selects_legacy_flavor(self) -> None:
        result = self.device_args([
            {"id": "other", "targetPlatform": "ios"},
            {"id": "chosen", "targetPlatform": "android-arm64"},
        ])
        self.assertEqual(0, result.returncode, result.stderr)
        self.assertEqual(["-d", "chosen", "--flavor", "legacy"], result.stdout.splitlines())

    def test_ios_and_macos_keep_default_scheme(self) -> None:
        for platform in ("ios", "darwin"):
            with self.subTest(platform=platform):
                result = self.device_args([{"id": "chosen", "targetPlatform": platform}])
                self.assertEqual(0, result.returncode, result.stderr)
                self.assertEqual(["-d", "chosen"], result.stdout.splitlines())

    def test_unknown_or_unsupported_device_does_not_guess_flavor(self) -> None:
        for devices in ([], [{"id": "other", "targetPlatform": "android-arm64"}],
                        [{"id": "chosen", "targetPlatform": "ios", "isSupported": False}]):
            with self.subTest(devices=devices):
                result = self.device_args(devices)
                self.assertEqual(84, result.returncode, result.stderr)
                self.assertIn("BLOCKED_VALIDATION_MISSING_DEVICE", result.stderr)
                self.assertEqual("", result.stdout)

    def test_guardian_suite_reports_missing_fixture_env(self) -> None:
        result = run_bash("e2e_preflight_fixture_env integration_test/guardian/all.dart")

        self.assertEqual(81, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_MISSING_FIXTURE_ENV", result.stderr)
        self.assertIn("GUARDIAN_E2E_EMAIL", result.stderr)
        self.assertIn("GUARDIAN_E2E_PASSWORD", result.stderr)
        self.assertIn("GUARDIAN_E2E_PASSAGEIRO", result.stderr)

    def test_operational_suite_accepts_seed_lowercase_fixture_env(self) -> None:
        env = {
            "driver_1_email": "m1@vanrides.dev",
            "driver_2_email": "m2@vanrides.dev",
            "driver_password": "Driver@123",
            "driver_1_id": "101",
            "driver_2_id": "102",
            "passenger_main_email": "p1@vanrides.dev",
            "passenger_password": "Passenger@123",
            "passenger_main_name": "P1",
            "passenger_main_id": "201",
            "guardian_email": "r1@vanrides.dev",
            "guardian_password": "Resp@123",
            "guardian_passageiro_name": "P1",
            "guardian_passageiro_id": "201",
        }

        result = run_bash(
            "e2e_preflight_fixture_env integration_test/operational_fixture/all.dart",
            env=env,
        )

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)

    def test_missing_device_has_stable_exit_code(self) -> None:
        result = run_bash("e2e_preflight_device ''")

        self.assertEqual(84, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_MISSING_DEVICE", result.stderr)

    def test_backend_connectivity_failure_is_classified(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            write_fake_curl(
                temp_dir,
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    output=""
                    while [[ $# -gt 0 ]]; do
                      case "$1" in
                        -o) output="$2"; shift 2 ;;
                        -w) shift 2 ;;
                        *) shift ;;
                      esac
                    done
                    if [[ -n "$output" ]]; then
                      printf 'backend down' > "$output"
                    fi
                    printf '503'
                    """
                ),
            )

            result = run_bash(
                "e2e_preflight_backend https://api-dev.example",
                env={"PATH": f"{temp_dir}:{os.environ['PATH']}"},
            )

        self.assertEqual(82, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_BACKEND_CONNECTIVITY", result.stderr)
        self.assertIn("GET /motoristas", result.stderr)

    def test_login_failure_is_classified_as_missing_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            write_fake_curl(
                temp_dir,
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    output=""
                    while [[ $# -gt 0 ]]; do
                      case "$1" in
                        -o) output="$2"; shift 2 ;;
                        -w) shift 2 ;;
                        *) shift ;;
                      esac
                    done
                    if [[ -n "$output" ]]; then
                      printf '{"erro":"unauthorized"}' > "$output"
                    fi
                    printf '401'
                    """
                ),
            )

            result = run_bash(
                "e2e_preflight_login https://api-dev.example qa@vanrides.dev badpass guardian",
                env={"PATH": f"{temp_dir}:{os.environ['PATH']}"},
            )

        self.assertEqual(83, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_MISSING_FIXTURE", result.stderr)
        self.assertIn("guardian", result.stderr)

    def test_guardian_trip_availability_requires_agenda_fixture(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            write_fake_curl(
                temp_dir,
                textwrap.dedent(
                    """\
                    #!/usr/bin/env bash
                    output=""
                    while [[ $# -gt 0 ]]; do
                      case "$1" in
                        -o) output="$2"; shift 2 ;;
                        -w) shift 2 ;;
                        *) shift ;;
                      esac
                    done
                    if [[ -n "$output" ]]; then
                      printf '[{"id":1}]' > "$output"
                    fi
                    printf '200'
                    """
                ),
            )

            env = {
                "PATH": f"{temp_dir}:{os.environ['PATH']}",
                "guardian_email": "r1@vanrides.dev",
                "guardian_password": "Resp@123",
                "guardian_passageiro_name": "P1",
                "guardian_passageiro_id": "201",
            }
            result = run_bash(
                "e2e_preflight_trip_availability https://api-dev.example integration_test/guardian/all.dart",
                env=env,
            )

        self.assertEqual(83, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_MISSING_FIXTURE", result.stderr)
        self.assertIn("guardian_day_trips", result.stderr)

    def test_dart_defines_include_seed_lowercase_fixture_values(self) -> None:
        env = {
            "driver_1_email": "m1@vanrides.dev",
            "driver_2_email": "m2@vanrides.dev",
            "driver_password": "Driver@123",
            "passenger_main_email": "p1@vanrides.dev",
            "passenger_password": "Passenger@123",
            "passenger_main_name": "P1",
            "guardian_email": "r1@vanrides.dev",
            "guardian_password": "Resp@123",
            "guardian_passageiro_name": "P1",
            "fixture_date": "2026-03-21",
        }

        result = run_bash(
            "E2E_DART_DEFINES=(); e2e_build_dart_defines https://api-dev.example; printf '%s\\n' \"${E2E_DART_DEFINES[@]}\"",
            env=env,
        )

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("--dart-define=VANRIDES_API_BASE_URL=https://api-dev.example", result.stdout)
        self.assertIn("--dart-define=VANRIDES_FIXTURE_DRIVER_1_EMAIL=m1@vanrides.dev", result.stdout)
        self.assertIn("--dart-define=GUARDIAN_E2E_PASSAGEIRO=P1", result.stdout)
        self.assertIn("--dart-define=VANRIDES_E2E_OPERATION_DATE=2026-03-21", result.stdout)


if __name__ == "__main__":
    unittest.main()
