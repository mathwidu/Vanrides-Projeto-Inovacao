#!/usr/bin/env python3
from __future__ import annotations

import re
import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts/qa_email_confirmation_intent_smoke.sh"
RENDERER = ROOT / (
    "src/main/java/com/vanrides/vanrides/service/"
    "EmailVerificationLandingPageRenderer.java"
)


class EmailConfirmationIntentSmokeContractTest(unittest.TestCase):
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

    def test_runner_binds_clean_expected_sha_tree_and_own_blob(self) -> None:
        required = (
            'EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"',
            'EXPECTED_BACKEND_TREE="${VANRIDES_EXPECTED_BACKEND_TREE:-}"',
            "expected_backend_sha_required",
            "expected_backend_tree_required",
            'BACKEND_SHA="$(git_safe rev-parse HEAD)"',
            'BACKEND_TREE="$(git_safe rev-parse \'HEAD^{tree}\')"',
            '"${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_email_confirmation_intent_smoke.sh"',
            'git_safe hash-object --no-filters "$SCRIPT_PATH"',
            "runner_not_versioned",
            "runner_blob_mismatch",
            "backend_worktree_not_clean",
            'BUILD_SNAPSHOT_DIR="$TMP_DIR/backend-snapshot"',
            'git_safe archive --format=tar "$BUILD_TREEISH"',
            'tar -xf - -C "$BUILD_SNAPSHOT_DIR"',
            "record build_source_origin GIT_ARCHIVE",
            'record build_source_sha "$EXPECTED_BACKEND_SHA"',
            'record build_source_tree "$EXPECTED_BACKEND_TREE"',
            "backend_sha_changed_during_smoke",
            "backend_tree_changed_during_smoke",
            "runner_changed_during_smoke",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_one_immutable_private_jar_is_built_and_used_once(self) -> None:
        required = (
            "run_clean_build()",
            'cd "$BUILD_SNAPSHOT_DIR"',
            "clean package",
            "jar_candidate_count",
            'find "$BUILD_SNAPSHOT_DIR/target"',
            'RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"',
            'chmod 500 "$RUNTIME_JAR_PATH"',
            "runtime_jar_copy_hash",
            '"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"',
            "jar_changed_during_smoke",
            "record immutable_private_jar PASS",
            "record same_jar_runtime PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertNotIn('find "$ROOT_DIR/target"', self.source)
        self.assertEqual(1, self.source.count('"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"'))

    def test_opt_in_browser_handoff_preserves_the_exact_private_jar_create_only(self) -> None:
        required = (
            'PRESERVE_RUNTIME_JAR="${VANRIDES_PRESERVE_RUNTIME_JAR:-false}"',
            'PRESERVED_JAR_PATH="$EVIDENCE_DIR/runtime.jar"',
            '[[ ! -e "$EVIDENCE_DIR" ]]',
            '[[ ! -e "$PRESERVED_JAR_PATH" ]]',
            "os.O_CREAT | os.O_EXCL",
            'python3 - "$runtime_jar_source" "$PRESERVED_JAR_PATH"',
            'chmod 400 "$PRESERVED_JAR_PATH"',
            '"$(sha256_file "$runtime_jar_source")"',
            'record browser_runtime_jar "$PRESERVED_JAR_RELATIVE"',
            "prepare_evidence_create_only",
            "persist_manifest_create_only",
            "set -o noclobber",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_postgres_is_owned_loopback_disposable_and_cleanup_fails_closed(self) -> None:
        required = (
            "docker_host_override_not_allowed",
            "docker_context_override_not_allowed",
            "docker_endpoint_not_local",
            "docker run --detach --pull=missing",
            "--publish '127.0.0.1::5432'",
            '--label-file "$CONTAINER_LABEL_FILE"',
            '--env-file "$POSTGRES_ENV_FILE"',
            "com.vanrides.qa.email-confirmation-intent.run-token",
            "postgres_container_ownership",
            "postgres_major_not_16",
            "CONTAINER_ATTEMPTED=1",
            "trap cleanup EXIT",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        self.assertIn('docker rm --force "$owned_id"', cleanup)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', cleanup)
        self.assertIn("cleanup_status=FAIL", cleanup)
        self.assertIn("smoke_result=FAIL", cleanup)
        self.assertIn("temporary_directory_cleanup", cleanup)
        self.assertIn("tmp_removed=FAIL", cleanup)
        self.assertLess(
            cleanup.index('rm -rf -- "$TMP_DIR"'),
            cleanup.index('persist_manifest_create_only "$manifest_body"'),
        )

    def test_all_passive_methods_are_db_non_mutating(self) -> None:
        ordered = (
            "LANDING_FINGERPRINT=",
            "landing_clean_no_mutation",
            "landing_legacy_no_mutation",
            "landing_prefetch_no_mutation",
            "landing_head_no_mutation",
            "landing_options_no_mutation",
        )
        positions = []
        for marker in ordered:
            self.assertIn(marker, self.source)
            positions.append(self.source.index(marker))
        self.assertEqual(sorted(positions), positions)
        self.assertIn('http_request GET', self.source)
        self.assertIn('http_request HEAD', self.source)
        self.assertIn('http_request OPTIONS', self.source)
        self.assertIn('"" "" prefetch', self.source)
        self.assertIn('"" "" preflight', self.source)
        self.assertEqual(
            3,
            self.source.count("?token=${SUCCESS_TOKEN}"),
            "legacy GET, prefetch GET and HEAD must challenge a persisted valid token",
        )
        self.assertIn("valid_legacy_token_not_consumed", self.source)
        self.assertIn("record valid_legacy_token_preserved PASS", self.source)

    def test_state_fingerprints_cover_complete_rows_and_only_expected_changes(self) -> None:
        required = (
            "state_fingerprint()",
            "SELECT (${account_projection})::text",
            "jsonb_agg(to_jsonb(identity_row)",
            "jsonb_agg(to_jsonb(refresh_row)",
            "jsonb_agg(${token_projection}",
            "ignore_token_consumption",
            "ignore_confirmation_transition",
            "expired_changed_non_consumption_state",
            "explicit_confirmation_changed_forbidden_state",
            "concurrent_confirmation_changed_forbidden_state",
            "replay_changed_committed_state",
            "concurrent_replay_changed_committed_state",
            "global_state_fingerprint",
            "record full_state_fingerprints PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_clean_legacy_prefetch_and_head_share_exact_page_contract(self) -> None:
        required = (
            'cmp -s "$TMP_DIR/landing-clean.html" "$TMP_DIR/landing-legacy.html"',
            'cmp -s "$TMP_DIR/landing-clean.html" "$TMP_DIR/landing-prefetch.html"',
            "write_landing_header_contract",
            'cmp -s "$TMP_DIR/landing-clean.contract"',
            "assert_landing_headers",
            "legacy_token_reflected",
            "landing_head_body",
            "raw_head_request()",
            'socket.create_connection(("127.0.0.1", int(port_value))',
            'raw_response.partition(b"\\r\\n\\r\\n")',
            "assert_landing_inline_policy",
            "inline CSP hash contract mismatch",
            "camera=(), microphone=(), geolocation=(), payment=()",
            "landing_byte_equality",
            "landing_header_equality",
            "record landing_inline_csp_exact PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_only_exact_explicit_post_success_counts_as_confirmation(self) -> None:
        required = (
            'assert_equal "$STATUS" 200 explicit_confirmation_status',
            "explicit_confirmation_not_committed",
            "explicit_confirmation_timestamp_not_committed",
            "explicit_confirmation_token_not_consumed",
            'assert_equal "$STATUS" 400 replay_status',
            "replay_changed_committed_state",
            'assert_equal "$STATUS" 400 expired_status',
            "expired_verified_account",
            "expired_token_not_terminal",
            "expired_invalid_body_distinguishable",
            '"$CONCURRENT_STATUS_A" == 200 && "$CONCURRENT_STATUS_B" == 400',
            '"$CONCURRENT_STATUS_A" == 400 && "$CONCURRENT_STATUS_B" == 200',
            "concurrent_lock_holder_not_ready",
            "concurrent_requests_not_observed_waiting",
            "pg_terminate_backend",
            "concurrent_outcomes_not_single_winner",
            "concurrent_account_timestamp_not_verified",
            "concurrent_invalid_body_distinguishable",
            "concurrent_replay_body_distinguishable",
            "record concurrent_single_winner PASS",
            "record concurrent_waiters_observed PASS",
            "Token de verificação inválido, expirado ou já utilizado.",
            "record generic_invalid_byte_contract PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertNotIn("--location", self.source)
        self.assertNotIn('[[ "$STATUS" == 2', self.source)

    def test_renderer_static_contract_fails_closed_on_redirect_or_unexpected_2xx(self) -> None:
        renderer = RENDERER.read_text(encoding="utf-8")
        required = (
            "const controller = new AbortController();",
            'redirect: "error"',
            "signal: controller.signal",
            'responseType.split(";", 1)[0].trim().toLowerCase()',
            'response.status === 200 && responseMediaType === "application/json"',
            "const confirmed = response.status === 200",
            'payload.message === "E-mail verificado com sucesso."',
        )
        for marker in required:
            self.assertIn(marker, renderer)
        self.assertNotIn("response.ok", renderer)
        self.assertNotIn(".startsWith(", renderer)
        self.assertNotIn("application/jsonp", renderer)
        self.assertNotIn("application/json-evil", renderer)

    def test_get_and_head_are_quota_free_while_third_post_is_429(self) -> None:
        required = (
            "VANRIDES_RATE_LIMIT_SENSITIVE_LIMIT=2",
            "for attempt in 1 2 3; do",
            '"$TMP_DIR/rate-get-${attempt}.html"',
            '"$TMP_DIR/rate-head-${attempt}.body"',
            '"$TMP_DIR/rate-options.body" "" 198.51.100.40',
            "rate_options_status",
            "rate_options_cors_origin",
            "rate_options_cors_method",
            "rate_options_database_mutation",
            "for attempt in 1 2; do",
            'assert_equal "$STATUS" 400 "rate_post_${attempt}_allowed"',
            'assert_equal "$STATUS" 429 rate_post_blocked_status',
            "rate_post_retry_after",
            "rate_post_cors_origin",
            "rate_post_request_id",
            "rate_post_content_type",
            "rate_post_blocked_token_reflected",
            "rate_lane_database_mutation",
            "record get_head_quota_free PASS",
            "record options_quota_free PASS",
            "record post_rate_limit_429 PASS",
            "record rate_limit_cors_contract PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_sensitive_values_use_private_files_or_stdin_not_process_arguments(self) -> None:
        required = (
            'SENSITIVE_PATTERNS_FILE="$TMP_DIR/sensitive-patterns.txt"',
            'TOKEN_PATTERNS_FILE="$TMP_DIR/token-patterns.txt"',
            "register_token_material",
            "files_exclude_patterns",
            "assert_response_excludes_token_material",
            "run_private_app()",
            'export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"',
            'export VANRIDES_JWT_SECRET="$JWT_SECRET"',
            'exec "$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"',
            '--env-file "$POSTGRES_ENV_FILE"',
            'user = "%s:%s"',
            "--data-binary @-",
            "docker exec --interactive",
            "--set ON_ERROR_STOP=1 --file -",
            '--data-binary "@$CONCURRENT_REQUEST_FILE"',
            "record sensitive_material_in_argv NONE",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        http_helper = self.source[
            self.source.index("http_request() {") : self.source.index("\n}\n\nheader_value()")
        ]
        self.assertNotIn("--user", http_helper)
        self.assertNotIn('docker exec --env "PGPASSWORD=', self.source)
        self.assertNotIn('--env "POSTGRES_PASSWORD=', self.source)
        self.assertNotIn("env -i", self.source)

    def test_async_children_are_owned_immediately_and_cleaned_before_runtime(self) -> None:
        required = (
            'ASYNC_PIDS="$LOCK_HOLDER_OS_PID"',
            'ASYNC_PIDS="$LOCK_HOLDER_OS_PID $CONCURRENT_PID_A"',
            'ASYNC_PIDS="$LOCK_HOLDER_OS_PID $CONCURRENT_PID_A $CONCURRENT_PID_B"',
            "stop_async_clients()",
            'kill -KILL "$pid"',
            'wait "$pid"',
        )
        for marker in required:
            self.assertIn(marker, self.source)
        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        self.assertLess(cleanup.index("stop_async_clients"), cleanup.index("stop_app"))

    def test_manifest_is_sanitized_complete_and_records_no_token_identity(self) -> None:
        recorded = set(
            re.findall(r"^\s*record ([a-z0-9_]+) ", self.source, re.MULTILINE)
        )
        required = {
            "backend_sha",
            "backend_tree",
            "runner_git_blob",
            "build_source_origin",
            "build_source_sha",
            "build_source_tree",
            "jar_sha256",
            "postgres_image_ref",
            "postgres_image_id",
            "landing_get_non_mutating",
            "legacy_query_non_mutating",
            "prefetch_non_mutating",
            "head_non_mutating",
            "options_non_mutating",
            "landing_byte_equality",
            "landing_header_equality",
            "landing_inline_csp_exact",
            "valid_legacy_token_preserved",
            "explicit_post_confirmation",
            "replay_generic_invalid",
            "expired_token_terminal",
            "concurrent_single_winner",
            "concurrent_waiters_observed",
            "session_artifacts_unchanged",
            "get_head_quota_free",
            "options_quota_free",
            "post_rate_limit_429",
            "rate_limit_cors_contract",
            "generic_invalid_byte_contract",
            "full_state_fingerprints",
            "same_jar_runtime",
            "sensitive_log_scan",
            "db_stdout_sha256",
            "token_material_in_evidence",
            "remote_environment_mutation",
        }
        self.assertTrue(required.issubset(recorded), required - recorded)
        for forbidden in (
            "record admin_email",
            "record success_email",
            "record raw_token",
            "record token_hash",
            "record jwt_secret",
            "record db_password",
        ):
            self.assertNotIn(forbidden, self.source.lower())
        self.assertIn("manifest_sanitization=PASS", self.source)
        self.assertIn('chmod 600 "$MANIFEST_PATH"', self.source)

    def test_runner_targets_only_loopback_runtime_and_records_no_remote_mutation(self) -> None:
        self.assertIn('BASE_URL="http://127.0.0.1:${APP_PORT}"', self.source)
        self.assertIn("jdbc:postgresql://127.0.0.1", self.source)
        self.assertNotIn("https://api", self.source)
        self.assertIn("record remote_environment_mutation NONE", self.source)


if __name__ == "__main__":
    unittest.main()
