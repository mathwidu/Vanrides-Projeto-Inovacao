#!/usr/bin/env python3
from __future__ import annotations

import http.server
import os
import re
import stat
import subprocess
import tempfile
import threading
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "qa_location_read_smoke.sh"
CI_GATE = ROOT / "scripts" / "ci_quality_gate.sh"


class LocationReadSmokeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = RUNNER.read_text(encoding="utf-8")

    def test_runner_is_executable_bash_with_valid_syntax(self) -> None:
        self.assertTrue(RUNNER.stat().st_mode & stat.S_IXUSR)
        result = subprocess.run(
            ["bash", "-n", str(RUNNER)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, result.returncode, result.stderr)

    def test_runner_is_repo_relative_and_requires_exact_backend_sha(self) -> None:
        self.assertNotIn("/Users/", self.source)
        self.assertNotIn("38b5489fb20af694b2b2d7f0eabab6e506b90ea1", self.source)
        self.assertNotIn("BACKEND_DIR=", self.source)
        self.assertIn('ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"', self.source)
        self.assertIn('EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"', self.source)
        self.assertIn("expected_backend_sha_required", self.source)
        self.assertIn('assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha', self.source)

    def test_runner_binds_worktree_runner_and_jar_provenance(self) -> None:
        required = (
            "git_safe status --porcelain --untracked-files=all",
            "git_safe rev-parse 'HEAD^{tree}'",
            'git_safe rev-parse "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_location_read_smoke.sh"',
            'git_safe hash-object --no-filters "$SCRIPT_PATH"',
            "runner_git_blob",
            "runner_sha256",
            "BUILT_JAR_PATH",
            'RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"',
            'cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH"',
            "runtime_jar_copy_hash",
            'JAR_PATH="$RUNTIME_JAR_PATH"',
            "jar_sha256_after",
            "jar_changed_during_smoke",
            "backend_sha_changed_during_smoke",
            "backend_tree_changed_during_smoke",
            "GIT_NO_REPLACE_OBJECTS=1",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertLess(
            self.source.index("backend_worktree_not_clean"),
            self.source.index("./mvnw -q clean package -DskipTests"),
        )
        runtime_jar_ready = self.source.index('JAR_PATH="$RUNTIME_JAR_PATH"')
        runtime_started = self.source.index('"$JAVA_BIN" -jar "$JAR_PATH"')
        clean_gate = self.source.index(
            'if ! run_backend_gate >"$TMP_DIR/backend-gate.log"'
        )
        self.assertLess(runtime_jar_ready, runtime_started)
        self.assertLess(runtime_started, clean_gate)

    def test_runner_rejects_assume_unchanged_and_skip_worktree_before_and_after(self) -> None:
        self.assertIn("assert_no_hidden_index_flags()", self.source)
        self.assertIn("git_safe -C \"$repo_root\" ls-files -v --", self.source)
        self.assertIn('substr($0, 1, 1) == "S"', self.source)
        self.assertIn('substr($0, 1, 1) ~ /[a-z]/', self.source)
        self.assertEqual(
            1,
            self.source.splitlines().count(
                'assert_no_hidden_index_flags "$ROOT_DIR" backend_index_flags_hidden_before'
            ),
        )
        self.assertEqual(
            1,
            self.source.splitlines().count(
                'assert_no_hidden_index_flags "$ROOT_DIR" backend_index_flags_hidden_after'
            ),
        )

        with tempfile.TemporaryDirectory() as temp_dir:
            repo = Path(temp_dir)
            subprocess.run(["git", "init", "-q"], cwd=repo, check=True)
            for name in ("normal.txt", "assume.txt", "skip.txt"):
                (repo / name).write_text(name, encoding="utf-8")
            subprocess.run(["git", "add", "."], cwd=repo, check=True)
            subprocess.run(
                [
                    "git", "-c", "user.name=VanRides QA", "-c",
                    "user.email=qa@vanrides.dev", "commit", "-qm", "fixture",
                ],
                cwd=repo,
                check=True,
            )
            subprocess.run(
                ["git", "update-index", "--assume-unchanged", "assume.txt"],
                cwd=repo,
                check=True,
            )
            subprocess.run(
                ["git", "update-index", "--skip-worktree", "skip.txt"],
                cwd=repo,
                check=True,
            )
            listing = subprocess.run(
                ["git", "ls-files", "-v", "--"],
                cwd=repo,
                check=True,
                capture_output=True,
                text=True,
            ).stdout.splitlines()

        hidden = {
            line[2:]
            for line in listing
            if line and (line[0] == "S" or line[0].islower())
        }
        self.assertEqual({"assume.txt", "skip.txt"}, hidden)

    def test_runner_uses_disposable_postgres_and_distinguishes_image_identifiers(self) -> None:
        self.assertIn("docker run --detach --pull=missing", self.source)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', self.source)
        self.assertIn("CONTAINER_ATTEMPTED=0", self.source)
        self.assertIn("CONTAINER_ATTEMPTED=1", self.source)
        self.assertIn('REQUESTED_CONTAINER_NAME="${VANRIDES_CONTAINER_NAME:-}"', self.source)
        self.assertIn(
            'REQUESTED_CONTAINER_RUN_TOKEN="${VANRIDES_CONTAINER_RUN_TOKEN:-}"',
            self.source,
        )
        self.assertIn("coordinator_container_ownership_pair_required", self.source)
        self.assertIn("invalid_coordinator_container_name", self.source)
        self.assertIn("invalid_coordinator_container_run_token", self.source)
        self.assertIn('CONTAINER_RUN_TOKEN="$REQUESTED_CONTAINER_RUN_TOKEN"', self.source)
        self.assertIn("com.vanrides.qa.location-read.run-token", self.source)
        self.assertIn('postgres_container_name_collision', self.source)
        self.assertIn("postgres_image_ref", self.source)
        self.assertIn("postgres_image_id", self.source)
        self.assertIn("postgres_image_repo_digest", self.source)
        self.assertIn("not_available", self.source)
        self.assertIn("VANRIDES_EXPECTED_POSTGRES_IMAGE_ID", self.source)
        self.assertIn('CONTAINER_STARTED=1', self.source)
        self.assertIn('docker info >/dev/null 2>&1', self.source)
        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        self.assertIn("container_id=", cleanup)
        self.assertIn('docker rm --force "$container_id"', cleanup)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', cleanup)

    def test_cleanup_removes_owned_container_even_when_docker_run_returned_nonzero(self) -> None:
        start = self.source.index("cleanup() {")
        end = self.source.index("\n}\n\ntrap cleanup EXIT", start) + 2
        cleanup_function = self.source[start:end]

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            fake_state = root / "container.state"
            fake_state.write_text("owned-run-token\n", encoding="utf-8")
            run_dir = root / "vanrides-location-read.fake"
            run_dir.mkdir()
            summary = run_dir / "summary.env"
            summary.write_text("", encoding="utf-8")
            harness = f"""
set -uo pipefail
TMP_BASE={str(root)!r}
TMP_DIR={str(run_dir)!r}
SUMMARY_FILE={str(summary)!r}
FAKE_STATE={str(fake_state)!r}
FAKE_CONTAINER_ID={('a' * 64)!r}
APP_PID=""
CONTAINER_ATTEMPTED=1
CONTAINER_STARTED=0
CONTAINER_NAME=vanrides-failed-create
CONTAINER_RUN_TOKEN=owned-run-token
MAIN_RESULT=""
FAILED_CHECK=postgres_container_start
append_log_hashes() {{ :; }}
record() {{ printf '%s=%s\\n' "$1" "$2" >>"$SUMMARY_FILE"; }}
docker() {{
  case "$1" in
    info) return 0 ;;
    inspect)
      if [[ "${{2:-}}" == "--format" ]]; then
        if [[ "${{3:-}}" == "{{{{.Id}}}}" ]]; then
          printf '%s\\n' "$FAKE_CONTAINER_ID"
        else
          cat "$FAKE_STATE"
        fi
      else
        [[ -f "$FAKE_STATE" ]]
      fi
      ;;
    rm)
      [[ "${{3:-}}" == "$FAKE_CONTAINER_ID" ]] || return 1
      rm -f "$FAKE_STATE"
      ;;
    *) return 1 ;;
  esac
}}
{cleanup_function}
false
cleanup
"""
            completed = subprocess.run(
                ["bash", "-c", harness],
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(1, completed.returncode, completed.stderr)
            self.assertFalse(
                fake_state.exists(),
                "owned residual container was not removed\n"
                f"stdout:\n{completed.stdout}\nstderr:\n{completed.stderr}",
            )
            self.assertIn("smoke_result=FAIL", completed.stdout)
            self.assertIn("cleanup=PASS", completed.stdout)

    def test_cleanup_never_removes_a_racing_name_replacement(self) -> None:
        start = self.source.index("cleanup() {")
        end = self.source.index("\n}\n\ntrap cleanup EXIT", start) + 2
        cleanup_function = self.source[start:end]

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            run_dir = root / "vanrides-location-read.race"
            run_dir.mkdir()
            summary = run_dir / "summary.env"
            summary.write_text("", encoding="utf-8")
            name_id = root / "name.id"
            name_token = root / "name.token"
            old_alive = root / "old.alive"
            removed = root / "removed.log"
            old_id = "a" * 64
            replacement_id = "b" * 64
            name_id.write_text(f"{old_id}\n", encoding="utf-8")
            name_token.write_text("owned-run-token\n", encoding="utf-8")
            old_alive.write_text("1\n", encoding="utf-8")
            harness = f"""
set -uo pipefail
TMP_BASE={str(root)!r}
TMP_DIR={str(run_dir)!r}
SUMMARY_FILE={str(summary)!r}
FAKE_NAME_ID={str(name_id)!r}
FAKE_NAME_TOKEN={str(name_token)!r}
FAKE_OLD_ALIVE={str(old_alive)!r}
FAKE_REMOVED={str(removed)!r}
OLD_ID={old_id!r}
REPLACEMENT_ID={replacement_id!r}
APP_PID=""
CONTAINER_ATTEMPTED=1
CONTAINER_STARTED=1
CONTAINER_NAME=vanrides-racing-name
CONTAINER_RUN_TOKEN=owned-run-token
MAIN_RESULT=PASS
FAILED_CHECK=""
append_log_hashes() {{ :; }}
record() {{ printf '%s=%s\\n' "$1" "$2" >>"$SUMMARY_FILE"; }}
docker() {{
  local target="${{@: -1}}"
  case "$1" in
    info) return 0 ;;
    inspect)
      if [[ "${{2:-}}" == "--format" ]]; then
        if [[ "${{3:-}}" == "{{{{.Id}}}}" ]]; then
          cat "$FAKE_NAME_ID"
        else
          cat "$FAKE_NAME_TOKEN"
        fi
      elif [[ "$target" == "$CONTAINER_NAME" ]]; then
        [[ -f "$FAKE_NAME_ID" ]]
      elif [[ "$target" == "$OLD_ID" ]]; then
        [[ -f "$FAKE_OLD_ALIVE" ]]
      else
        return 1
      fi
      ;;
    rm)
      printf '%s\\n' "${{3:-}}" >>"$FAKE_REMOVED"
      [[ "${{3:-}}" == "$OLD_ID" ]] || return 1
      rm -f "$FAKE_OLD_ALIVE"
      printf '%s\\n' "$REPLACEMENT_ID" >"$FAKE_NAME_ID"
      printf '%s\\n' replacement-token >"$FAKE_NAME_TOKEN"
      ;;
    *) return 1 ;;
  esac
}}
{cleanup_function}
false
cleanup
"""
            completed = subprocess.run(
                ["bash", "-c", harness],
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(1, completed.returncode, completed.stderr)
            self.assertEqual(f"{old_id}\n", removed.read_text(encoding="utf-8"))
            self.assertEqual(
                f"{replacement_id}\n", name_id.read_text(encoding="utf-8")
            )
            self.assertIn("cleanup=FAIL", completed.stdout)

    def test_cleanup_binds_the_ownership_label_to_the_captured_id(self) -> None:
        start = self.source.index("cleanup() {")
        end = self.source.index("\n}\n\ntrap cleanup EXIT", start) + 2
        cleanup_function = self.source[start:end]

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            run_dir = root / "vanrides-location-read.id-label-race"
            run_dir.mkdir()
            summary = run_dir / "summary.env"
            summary.write_text("", encoding="utf-8")
            removed = root / "removed.log"
            replacement_id = "b" * 64
            harness = f"""
set -uo pipefail
TMP_BASE={str(root)!r}
TMP_DIR={str(run_dir)!r}
SUMMARY_FILE={str(summary)!r}
FAKE_REMOVED={str(removed)!r}
REPLACEMENT_ID={replacement_id!r}
APP_PID=""
CONTAINER_ATTEMPTED=1
CONTAINER_STARTED=1
CONTAINER_NAME=vanrides-changing-name
CONTAINER_RUN_TOKEN=owned-run-token
MAIN_RESULT=PASS
FAILED_CHECK=""
append_log_hashes() {{ :; }}
record() {{ printf '%s=%s\\n' "$1" "$2" >>"$SUMMARY_FILE"; }}
docker() {{
  local target="${{@: -1}}"
  case "$1" in
    info) return 0 ;;
    inspect)
      if [[ "${{2:-}}" == "--format" && "${{3:-}}" == "{{{{.Id}}}}" ]]; then
        printf '%s\\n' "$REPLACEMENT_ID"
      elif [[ "${{2:-}}" == "--format" && "$target" == "$REPLACEMENT_ID" ]]; then
        printf '%s\\n' replacement-token
      elif [[ "${{2:-}}" == "--format" ]]; then
        printf '%s\\n' owned-run-token
      else
        return 0
      fi
      ;;
    rm) printf '%s\\n' "${{3:-}}" >>"$FAKE_REMOVED" ;;
    *) return 1 ;;
  esac
}}
{cleanup_function}
false
cleanup
"""
            completed = subprocess.run(
                ["bash", "-c", harness],
                capture_output=True,
                text=True,
                check=False,
            )

            self.assertEqual(1, completed.returncode, completed.stderr)
            self.assertFalse(removed.exists(), "replacement container was removed")
            self.assertIn("cleanup=FAIL", completed.stdout)
            cleanup = self.source[
                self.source.index("cleanup() {") : self.source.index(
                    "\n}\n\ntrap cleanup EXIT"
                )
            ]
            self.assertIn('"$container_id" 2>/dev/null)', cleanup)

    def test_docker_endpoint_is_pinned_to_a_local_transport_before_info_or_run(self) -> None:
        required = (
            "docker_host_override_not_allowed",
            "docker_context_override_not_allowed",
            "docker context show",
            "docker context inspect",
            "unix:///*",
            "npipe:////./pipe/*",
            "docker_endpoint_not_local",
            'export DOCKER_HOST="$DOCKER_ENDPOINT"',
            "docker_endpoint_transport",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        endpoint_check = self.source.index('case "$DOCKER_ENDPOINT" in')
        docker_info = self.source.index("docker info >/dev/null 2>&1", endpoint_check)
        docker_run = self.source.index("docker run --detach --pull=missing", docker_info)
        self.assertLess(endpoint_check, docker_info)
        self.assertLess(docker_info, docker_run)

    def test_loopback_http_isolated_from_proxy_and_curl_config(self) -> None:
        for marker in (
            "unset HTTP_PROXY HTTPS_PROXY ALL_PROXY FTP_PROXY",
            "http_proxy https_proxy all_proxy ftp_proxy",
            "export NO_PROXY='*'",
            "export no_proxy='*'",
            'export CURL_HOME="$TMP_DIR/curl-home"',
            'export XDG_CONFIG_HOME="$TMP_DIR/xdg-config"',
            "http_transport_scope loopback_no_proxy",
            "--disable --noproxy '*'",
        ):
            self.assertIn(marker, self.source)

        origin_requests: list[str] = []
        proxy_requests: list[str] = []

        class OriginHandler(http.server.BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802 - stdlib callback name
                origin_requests.append(self.headers.get("Authorization", ""))
                self.send_response(204)
                self.end_headers()

            def log_message(self, _format: str, *args: object) -> None:
                del args

        class ProxyHandler(http.server.BaseHTTPRequestHandler):
            def do_GET(self) -> None:  # noqa: N802 - stdlib callback name
                proxy_requests.append(self.headers.get("Authorization", ""))
                self.send_response(418)
                self.end_headers()

            def log_message(self, _format: str, *args: object) -> None:
                del args

        origin = http.server.ThreadingHTTPServer(("127.0.0.1", 0), OriginHandler)
        proxy = http.server.ThreadingHTTPServer(("127.0.0.1", 0), ProxyHandler)
        threads = [
            threading.Thread(target=server.serve_forever, daemon=True)
            for server in (origin, proxy)
        ]
        for thread in threads:
            thread.start()
        try:
            with tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                curl_home = root / "curl-home"
                xdg_home = root / "xdg-config"
                hostile_home = root / "hostile-home"
                curl_home.mkdir()
                xdg_home.mkdir()
                hostile_home.mkdir()
                proxy_url = f"http://127.0.0.1:{proxy.server_port}"
                (hostile_home / ".curlrc").write_text(
                    f"proxy = {proxy_url}\n", encoding="utf-8"
                )
                (curl_home / ".curlrc").write_text("", encoding="utf-8")
                (xdg_home / "curlrc").write_text("", encoding="utf-8")
                environment = os.environ.copy()
                for key in (
                    "HTTP_PROXY", "HTTPS_PROXY", "ALL_PROXY", "FTP_PROXY",
                    "http_proxy", "https_proxy", "all_proxy", "ftp_proxy",
                ):
                    environment.pop(key, None)
                environment.update(
                    {
                        "HOME": str(hostile_home),
                        "NO_PROXY": "*",
                        "no_proxy": "*",
                        "CURL_HOME": str(curl_home),
                        "XDG_CONFIG_HOME": str(xdg_home),
                    }
                )
                url = f"http://127.0.0.1:{origin.server_port}/location"
                direct = subprocess.run(
                    [
                        "curl", "--disable", "--noproxy", "*", "--silent",
                        "--output", "/dev/null", "--write-out", "%{http_code}",
                        "--user", "qa:ephemeral", url,
                    ],
                    env=environment,
                    capture_output=True,
                    text=True,
                    check=False,
                )
                child = subprocess.run(
                    ["curl", "--silent", "--output", "/dev/null", "--write-out", "%{http_code}", url],
                    env=environment,
                    capture_output=True,
                    text=True,
                    check=False,
                )
        finally:
            origin.shutdown()
            proxy.shutdown()
            origin.server_close()
            proxy.server_close()

        self.assertEqual((0, "204"), (direct.returncode, direct.stdout), direct.stderr)
        self.assertEqual((0, "204"), (child.returncode, child.stdout), child.stderr)
        self.assertEqual([], proxy_requests)
        self.assertEqual(2, len(origin_requests))

    def test_app_probe_is_mandatory_direct_and_has_two_strict_phases(self) -> None:
        self.assertIn('APP_CONSUMER_PROBE="${VANRIDES_APP_CONSUMER_PROBE:-}"', self.source)
        self.assertIn('EXPECTED_APP_SHA="${VANRIDES_EXPECTED_APP_SHA:-}"', self.source)
        self.assertIn("app_consumer_probe_required", self.source)
        self.assertIn("expected_app_sha_required", self.source)
        self.assertNotIn(" SKIP", self.source)
        self.assertNotIn("not_run", self.source)
        self.assertEqual(1, self.source.splitlines().count("run_app_probe positive"))
        self.assertEqual(1, self.source.splitlines().count("run_app_probe negative"))
        self.assertEqual(1, self.source.count("VANRIDES_CONSUMER_EMPTY_DRIVER_ID="))
        self.assertGreaterEqual(self.source.count('"$APP_CONSUMER_PROBE" >"$stdout_file"'), 2)
        self.assertNotIn("eval ", self.source)
        self.assertNotIn("bash -c", self.source)
        for name in (
            "VANRIDES_API_BASE_URL",
            "VANRIDES_EXPECTED_APP_SHA",
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
        ):
            self.assertIn(name, self.source)
        self.assertIn("app_consumer_probe_sha256", self.source)
        self.assertIn("assert_app_runtime_unchanged()", self.source)
        self.assertIn('assert_app_runtime_unchanged "$phase" before', self.source)
        self.assertIn('assert_app_runtime_unchanged "$phase" after', self.source)
        self.assertIn("kill -0 \"$APP_PID\"", self.source)
        self.assertIn('sha256_file "$JAR_PATH"', self.source)

    def test_app_probe_is_bound_to_exact_clean_git_source_before_build(self) -> None:
        required = (
            "APP_CONSUMER_PROBE_CANONICAL",
            "app_consumer_probe_not_canonical",
            '"$APP_ROOT/scripts/qa_trip_live_location_real_backend.sh"',
            "APP_GIT_ROOT_CANONICAL",
            "app_git_root_mismatch",
            'APP_SHA="$(git_safe -C "$APP_ROOT" rev-parse HEAD)"',
            'APP_TREE="$(git_safe -C "$APP_ROOT" rev-parse \'HEAD^{tree}\')"',
            'assert_equal "$APP_SHA" "$EXPECTED_APP_SHA" app_sha',
            'assert_no_hidden_index_flags "$APP_ROOT" app_index_flags_hidden_before',
            'git_safe -C "$APP_ROOT" status --porcelain --untracked-files=all',
            "app_consumer_probe_git_blob",
            "app_consumer_probe_raw_sha256",
            "--no-replace-objects",
            "core.fsmonitor=false",
            "--no-filters",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertLess(
            self.source.index("app_consumer_probe_raw_sha256"),
            self.source.index("./mvnw -q clean package -DskipTests"),
        )
        self.assertIn(
            'assert_no_hidden_index_flags "$APP_ROOT" app_index_flags_hidden_after',
            self.source,
        )
        self.assertIn("app_consumer_probe_changed_during_smoke", self.source)

    def test_app_markers_are_exact_and_attestation_limits_are_explicit(self) -> None:
        self.assertEqual(2, self.source.count("grep -Fxq"))
        self.assertEqual(2, self.source.count("attestation_scope=consumer_only backend_binding=coordinator_required"))
        self.assertEqual(
            2,
            self.source.splitlines().count(
                '    printf \'%s\\n\' "$expected_marker" >>"$SUMMARY_FILE"'
            ),
        )
        self.assertNotIn('cat "$stdout_file"', self.source)
        for marker in (
            "app_consumer_positive_http_200",
            "app_consumer_positive_http_204",
            "app_consumer_negative_http_404",
            "app_consumer_negative_generic_exception",
            "app_attestation_scope",
            "app_backend_binding",
        ):
            self.assertIn(marker, self.source)

    def test_locale_is_deterministic_without_forcing_ascii_on_java_or_flutter(self) -> None:
        self.assertIn("export LC_ALL=C", self.source)
        self.assertIn("export LANG=C", self.source)
        self.assertIn("export PYTHONUTF8=1", self.source)
        self.assertGreaterEqual(
            self.source.count("LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8"),
            3,
        )

    def test_probe_and_sensitive_fixture_output_are_never_replayed(self) -> None:
        self.assertNotRegex(self.source, r"cat \"\$TMP_DIR/(?:fixture|app-(?:positive|negative))")
        for marker in (
            "fixture_stdout_sha256",
            "fixture_stderr_sha256",
            "app_consumer_positive_stdout_sha256",
            "app_consumer_positive_stderr_sha256",
            "app_consumer_negative_stdout_sha256",
            "app_consumer_negative_stderr_sha256",
        ):
            self.assertIn(marker, self.source)
        self.assertIn("umask 077", self.source)
        self.assertIn("openssl rand -hex", self.source)

    def test_runner_sanitizes_runner_owned_environment_before_gate(self) -> None:
        start = self.source.index("run_backend_gate() (")
        end = self.source.index("\n)\n", start) + 2
        function = self.source[start:end]

        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            scripts = root / "scripts"
            scripts.mkdir()
            gate = scripts / "qa_gate_card.sh"
            gate.write_text(
                "#!/usr/bin/env bash\n"
                "set -euo pipefail\n"
                "while IFS= read -r variable; do\n"
                "  case \"$variable\" in\n"
                "    DOCKER_HOST|MAVEN_*|MVNW_*) "
                "echo \"leaked:$variable\" >&2; exit 91 ;;\n"
                "  esac\n"
                "done < <(builtin compgen -e)\n"
                "[[ \"${KEEP_ME:-}\" == present ]] || exit 92\n"
                "[[ \"${VANRIDES_BACKEND_BASE_URL:-}\" == "
                "http://127.0.0.1:18080 ]] || exit 93\n"
                "printf 'GATE_ENV_PASS\\n'\n",
                encoding="utf-8",
            )
            gate.chmod(0o755)
            environment = os.environ.copy()
            environment.update(
                {
                    "KEEP_ME": "present",
                    "DOCKER_HOST": "unix:///runner-owned/docker.sock",
                    "MAVEN_OPTS": "-javaagent:/tmp/untrusted.jar",
                    "MAVEN_USER_HOME": "/tmp/untrusted-m2",
                    "MAVEN_SKIP_RC": "0",
                    "MVNW_REPOURL": "https://untrusted.invalid",
                }
            )
            completed = subprocess.run(
                [
                    "bash",
                    "-c",
                    "set -euo pipefail\n"
                    "compgen() { :; }\n"
                    "export -f compgen\n"
                    "BASE_URL=http://127.0.0.1:18080\n"
                    f"{function}\n"
                    "run_backend_gate\n",
                ],
                cwd=root,
                env=environment,
                capture_output=True,
                text=True,
                check=False,
            )

        self.assertEqual(0, completed.returncode, completed.stderr)
        self.assertEqual("GATE_ENV_PASS\n", completed.stdout)
        self.assertIn('if ! run_backend_gate >"$TMP_DIR/backend-gate.log"', self.source)

    def test_cleanup_is_fail_closed_and_is_the_last_result_marker(self) -> None:
        self.assertIn("trap cleanup EXIT", self.source)
        self.assertIn("trap 'exit 130' INT", self.source)
        self.assertIn("trap 'exit 143' TERM", self.source)
        self.assertIn('cleanup_status="FAIL"', self.source)
        self.assertIn('final_status=1', self.source)
        self.assertIn("printf 'cleanup=%s\\n'", self.source)
        cleanup_print = self.source.index("printf 'cleanup=%s\\n'")
        final_exit = self.source.index('exit "$final_status"', cleanup_print)
        self.assertLess(cleanup_print, final_exit)

    def test_success_contract_contains_required_sanitized_markers(self) -> None:
        required = {
            "backend_qa_gate_card",
            "fixture_contract",
            "authorized_passenger_location",
            "authorized_guardian_location",
            "authorized_without_location",
            "bootstrap_empty",
            "bootstrap_slug_only",
            "bootstrap_duplicate_company",
            "legacy_link_persisted",
            "canceled_trip_location",
            "legacy_link_only_location",
            "guardian_legacy_link_only_location",
            "cross_tenant_location",
            "nonexistent_target_location",
            "not_found_fingerprint_sha256",
            "same_jar_runtime",
            "remote_environment_mutation",
        }
        recorded = set(re.findall(r"^record ([a-z0-9_]+) ", self.source, flags=re.M))
        self.assertTrue(required.issubset(recorded), required - recorded)

    def test_non_docker_contract_test_is_wired_once_into_ci_gate(self) -> None:
        gate = CI_GATE.read_text(encoding="utf-8")
        line = 'python3 "$ROOT_DIR/scripts/qa_location_read_smoke_test.py"'
        self.assertEqual(1, gate.splitlines().count(line))


if __name__ == "__main__":
    unittest.main()
