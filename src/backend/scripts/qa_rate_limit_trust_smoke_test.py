#!/usr/bin/env python3
from __future__ import annotations

import re
import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "qa_rate_limit_trust_smoke.sh"
LOAD_RUNNER = ROOT / "scripts" / "qa_rate_limit_trust_load.py"
LOAD_TEST = ROOT / "scripts" / "qa_rate_limit_trust_load_test.py"
CI_GATE = ROOT / "scripts" / "ci_quality_gate.sh"


class RateLimitTrustSmokeContractTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls) -> None:
        cls.source = RUNNER.read_text(encoding="utf-8")

    def test_runner_is_executable_bash_with_valid_syntax(self) -> None:
        self.assertTrue(RUNNER.stat().st_mode & stat.S_IXUSR)
        completed = subprocess.run(
            ["bash", "-n", str(RUNNER)],
            cwd=ROOT,
            capture_output=True,
            text=True,
            check=False,
        )
        self.assertEqual(0, completed.returncode, completed.stderr)

    def test_runner_is_repo_relative_and_binds_exact_clean_sha(self) -> None:
        self.assertNotIn("/Users/", self.source)
        self.assertIn(
            'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"',
            self.source,
        )
        self.assertIn('EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"', self.source)
        self.assertIn("expected_backend_sha_required", self.source)
        self.assertIn('assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha', self.source)
        self.assertIn("git_safe status --porcelain --untracked-files=all", self.source)
        self.assertIn('${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_rate_limit_trust_smoke.sh', self.source)
        self.assertIn('git_safe hash-object --no-filters "$SCRIPT_PATH"', self.source)
        self.assertIn("runner_blob", self.source)

    def test_runner_builds_and_copies_one_immutable_jar(self) -> None:
        required = (
            "./mvnw -q clean package -DskipTests",
            "jar_candidate_count",
            'cp "$JAR_PATH" "$TMP_DIR/runtime.jar"',
            'JAR_PATH="$TMP_DIR/runtime.jar"',
            'JAR_SHA256="$(sha256_file "$JAR_PATH")"',
            'assert_equal "$(sha256_file "$JAR_PATH")" "$JAR_SHA256"',
            "jar_changed_during_smoke",
            "backend_sha_changed",
            "backend_tree_changed",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertEqual(2, self.source.count('start_app '))
        self.assertEqual(1, self.source.count('"$JAVA_BIN" -jar "$JAR_PATH"'))

    def test_runner_uses_owned_disposable_postgres_and_fail_closed_cleanup(self) -> None:
        required = (
            "docker run --detach --pull=missing",
            "com.vanrides.qa.rate-limit.run-token",
            'docker rm --force "$owned_id"',
            "CONTAINER_ATTEMPTED=1",
            "trap cleanup EXIT",
            "cleanup_status=FAIL",
            "smoke_result=FAIL",
            'local failed_check="${FAILED_CHECK:-unexpected_exit}"',
            'record failed_check "$failed_check"',
            "failed_check_sha256",
            "chmod 600",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', self.source)

    def test_runner_proves_direct_and_trusted_proxy_semantics_separately(self) -> None:
        self.assertIn('start_app direct ""', self.source)
        self.assertIn('start_app trusted "127.0.0.1/32"', self.source)
        self.assertIn("--rotate-x-forwarded-for", self.source)
        self.assertIn("--expected-limited 6 --expected-non-limited 2", self.source)
        self.assertIn("--expected-limited 0 --expected-non-limited 8", self.source)
        self.assertGreaterEqual(
            self.source.count('--expected-non-limited-status 400'),
            2,
        )
        self.assertIn("trusted_proxy_identity", self.source)
        self.assertIn("proxy_peer_bucket", self.source)
        self.assertIn("INVALID_FORWARDED_ADDRESS", self.source)
        token_family_probe = re.search(
            r'--url-template "[^\n]+/driver-onboarding/invites/token-\{index\}/preview"'
            r'.*?--expected-non-limited-status 404',
            self.source,
            re.DOTALL,
        )
        self.assertIsNotNone(token_family_probe)

    def test_runner_proves_body_cors_request_id_and_metrics_contracts(self) -> None:
        required = (
            "Transfer-Encoding: chunked",
            "PAYLOAD_TOO_LARGE",
            "smoke-body-length",
            "smoke-body-stream",
            "Access-Control-Allow-Origin: https://app\\.example",
            "X-Request-Id",
            "/actuator/metrics/vanrides.rate.limit.requests",
            "metrics_expose_identity",
            "preflight_free",
            "dynamic_token_family",
            "UNSUPPORTED_BODY_MEDIA_TYPE",
            "unread_body_socket_close",
            'capture_request "unread-prime-${index}" /passageiros',
            '"POST /passageiros HTTP/1.1\\r\\n"',
            'response.startswith(b"HTTP/1.1 429")',
            'b"connection: close"',
            'b\'"code":"rate_limited"\'',
            "Connection: keep-alive",
            "client.recv(4096)",
            "vanrides.request_body.unread_connections_closed",
            "unread_body_metric_count",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_manifest_contains_only_sanitized_result_markers(self) -> None:
        recorded = set(re.findall(r"^record ([a-z0-9_]+) ", self.source, re.MULTILINE))
        required = {
            "backend_sha",
            "backend_tree",
            "jar_sha256",
            "direct_rotating_xff",
            "dynamic_token_family",
            "body_content_length",
            "body_unknown_length",
            "unsupported_body_media_type",
            "unread_body_connection_close",
            "unread_body_close_metric",
            "preflight_free",
            "metrics_no_identity",
            "trusted_proxy_identity",
            "invalid_forwarded_fail_closed",
            "proxy_peer_bucket",
            "same_jar_runtime",
            "remote_environment_mutation",
        }
        self.assertTrue(required.issubset(recorded), required - recorded)
        self.assertNotIn("record db_password", self.source.lower())
        self.assertNotIn("record jwt_secret", self.source.lower())

    def test_static_contract_and_load_tests_are_wired_once_into_ci(self) -> None:
        gate = CI_GATE.read_text(encoding="utf-8")
        for script in (LOAD_TEST.name, Path(__file__).name):
            line = f'python3 "$ROOT_DIR/scripts/{script}"'
            self.assertEqual(1, gate.splitlines().count(line))
        self.assertTrue(LOAD_RUNNER.stat().st_mode & stat.S_IXUSR)


if __name__ == "__main__":
    unittest.main()
