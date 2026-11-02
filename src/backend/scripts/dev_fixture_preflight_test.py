#!/usr/bin/env python3
from __future__ import annotations

import os
import stat
import subprocess
import tempfile
import textwrap
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
HELPER = ROOT / "scripts/dev_fixture_preflight.sh"


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


class DevFixturePreflightTest(unittest.TestCase):
    def test_backend_preflight_classifies_motoristas_failure(self) -> None:
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
                "vanrides_fixture_preflight_backend https://api-dev.example igr-lab",
                env={"PATH": f"{temp_dir}:{os.environ['PATH']}"},
            )

        self.assertEqual(82, result.returncode, result.stderr + result.stdout)
        self.assertIn("BLOCKED_VALIDATION_BACKEND_CONNECTIVITY", result.stderr)
        self.assertIn("GET /motoristas", result.stderr)
        self.assertIn("igr-lab", result.stderr)

    def test_backend_preflight_checks_required_public_endpoints(self) -> None:
        with tempfile.TemporaryDirectory() as temp:
            temp_dir = Path(temp)
            calls_file = temp_dir / "calls.txt"
            write_fake_curl(
                temp_dir,
                textwrap.dedent(
                    f"""\
                    #!/usr/bin/env bash
                    output=""
                    method="GET"
                    url=""
                    while [[ $# -gt 0 ]]; do
                      case "$1" in
                        -o) output="$2"; shift 2 ;;
                        -w) shift 2 ;;
                        -X) method="$2"; shift 2 ;;
                        http*) url="$1"; shift ;;
                        *) shift ;;
                      esac
                    done
                    printf '%s %s\\n' "$method" "$url" >> "{calls_file}"
                    if [[ -n "$output" ]]; then
                      printf '{{}}' > "$output"
                    fi
                    case "$method $url" in
                      *"/auth/refresh") printf '400' ;;
                      *) printf '200' ;;
                    esac
                    """
                ),
            )

            result = run_bash(
                "vanrides_fixture_preflight_backend https://api-dev.example igr-lab",
                env={"PATH": f"{temp_dir}:{os.environ['PATH']}"},
            )

            calls = calls_file.read_text(encoding="utf-8")

        self.assertEqual(0, result.returncode, result.stderr + result.stdout)
        self.assertIn("GET https://api-dev.example/motoristas", calls)
        self.assertIn("OPTIONS https://api-dev.example/motoristas", calls)
        self.assertIn("POST https://api-dev.example/auth/refresh", calls)


if __name__ == "__main__":
    unittest.main()
