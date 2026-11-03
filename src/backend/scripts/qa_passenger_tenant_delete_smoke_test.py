#!/usr/bin/env python3
from __future__ import annotations

import re
import stat
import subprocess
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "qa_passenger_tenant_delete_smoke.sh"


class PassengerTenantDeleteSmokeContractTest(unittest.TestCase):
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

    def test_runner_is_repo_relative_and_binds_committed_provenance(self) -> None:
        self.assertNotIn("/Users/", self.source)
        required = (
            'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"',
            'BACKEND_SHA="$(git_safe rev-parse HEAD)"',
            'BACKEND_TREE="$(git_safe rev-parse \'HEAD^{tree}\')"',
            '"${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_passenger_tenant_delete_smoke.sh"',
            'git_safe hash-object --no-filters "$SCRIPT_PATH"',
            "runner_not_versioned",
            "runner_blob_mismatch",
            "backend_worktree_not_clean",
            "backend_sha_changed_during_smoke",
            "backend_tree_changed_during_smoke",
            "runner_changed_during_smoke",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_expected_sha_is_optional_but_enforced_for_closure(self) -> None:
        required = (
            'EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"',
            'REQUIRE_EXPECTED_BACKEND_SHA="${VANRIDES_REQUIRE_EXPECTED_BACKEND_SHA:-false}"',
            "expected_backend_sha_required_for_closure",
            "invalid_expected_backend_sha",
            'assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha',
            'record expected_backend_sha "${EXPECTED_BACKEND_SHA:-not_configured}"',
            'record expected_sha_required "$REQUIRE_EXPECTED_BACKEND_SHA"',
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_runner_builds_and_runs_one_immutable_private_jar(self) -> None:
        required = (
            "run_clean_build()",
            'for variable in JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS; do',
            'fail "${variable}_override_not_allowed"',
            'MAVEN_ISOLATED_HOME="$TMP_DIR/maven-user-home"',
            'MAVEN_REPOSITORY="$HOME/.m2/repository"',
            'export MAVEN_USER_HOME="$HOME/.m2"',
            '-Duser.home="$MAVEN_ISOLATED_HOME"',
            '-Dmaven.repo.local="$MAVEN_REPOSITORY"',
            "-DskipTests",
            "clean package",
            "jar_candidate_count",
            'RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"',
            'cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH"',
            '"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"',
            "runtime_jar_copy_hash",
            "jar_changed_during_smoke",
            "record same_jar_runtime PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertEqual(1, self.source.count('"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"'))

    def test_postgres_is_local_disposable_owned_and_cleanup_fails_closed(self) -> None:
        required = (
            "docker_endpoint_not_local",
            "docker_host_override_not_allowed",
            "docker_context_override_not_allowed",
            "docker run --detach --pull=missing",
            "--publish '127.0.0.1::5432'",
            '--label-file "$CONTAINER_LABEL_FILE"',
            '--env-file "$POSTGRES_ENV_FILE"',
            "com.vanrides.qa.passenger-delete.run-token",
            "postgres_container_ownership",
            "invalid_postgres_runtime_image_id",
            "postgres_major_not_16",
            "CONTAINER_ATTEMPTED=1",
            "trap cleanup EXIT",
            "cleanup_status=FAIL",
            "smoke_result=FAIL",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        self.assertIn('docker rm --force "$owned_id"', cleanup)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', cleanup)

    def test_two_real_tenants_prove_opaque_not_found_without_mutation(self) -> None:
        ordered = (
            "admin_a_bootstrap_status",
            "admin_b_bootstrap_status",
            "tenant_fixture_not_distinct",
            "passenger_create_status",
            "INITIAL_SNAPSHOT=",
            "cross_tenant_delete_status",
            "cross_tenant_no_mutation",
            "missing_delete_status",
            "missing_no_mutation",
            "not_found_response_body_distinguishable",
            "authorized_delete_status",
        )
        positions = []
        for marker in ordered:
            self.assertIn(marker, self.source)
            positions.append(self.source.index(marker))
        self.assertEqual(sorted(positions), positions)
        self.assertIn(
            'cmp -s "$TMP_DIR/cross-tenant.json" "$TMP_DIR/missing.json"',
            self.source,
        )
        self.assertIn("not_found_content_type_distinguishable", self.source)
        self.assertIn("not_found_content_length_distinguishable", self.source)
        self.assertEqual(5, self.source.count("string_agg(source::text"))
        self.assertEqual(2, len(re.findall(r'assert_equal "\$STATUS" 404', self.source)))

    def test_success_removes_account_and_invalidates_all_three_artifacts(self) -> None:
        required = (
            "INSERT INTO refresh_token",
            "INSERT INTO device_token",
            "verification_fixture_active",
            'assert_equal "$STATUS" 204 authorized_delete_status',
            "passenger_not_removed",
            "login_identity_not_removed",
            "refresh_artifact_not_revoked",
            "verification_artifact_not_consumed",
            "device_artifact_not_invalidated",
            "record refresh_token_revoked PASS",
            "record email_verification_consumed PASS",
            "record device_token_invalidated PASS",
            "record artifact_transaction_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertIn("active = FALSE AND invalidated_at IS NOT NULL AND user_email IS NULL", self.source)

    def test_fixture_sql_and_http_credentials_do_not_leak_in_arguments(self) -> None:
        required = (
            "docker exec --interactive",
            "--set ON_ERROR_STOP=1 --file -",
            'config_file="${output_file}.curl-config"',
            'chmod 600 "$config_file"',
            'user = "%s:%s"',
            "--data-binary @-",
            '--env-file "$POSTGRES_ENV_FILE"',
        )
        for marker in required:
            self.assertIn(marker, self.source)
        http_helper = self.source[
            self.source.index("http_request() {") : self.source.index("\n}\n\ndb_scalar()")
        ]
        self.assertNotIn("--user", http_helper)
        self.assertNotIn('--env "POSTGRES_PASSWORD=', self.source)
        self.assertNotIn('docker exec --env "PGPASSWORD=', self.source)

    def test_manifest_is_sanitized_complete_and_create_only(self) -> None:
        recorded = set(re.findall(r"^record ([a-z0-9_]+) ", self.source, re.MULTILINE))
        required = {
            "backend_sha",
            "backend_tree",
            "runner_git_blob",
            "jar_sha256",
            "postgres_image_ref",
            "postgres_image_id",
            "postgres_major",
            "cross_tenant_not_found",
            "missing_not_found",
            "not_found_indistinguishable",
            "not_found_no_mutation",
            "authorized_delete_http",
            "passenger_removed",
            "login_identity_removed",
            "refresh_token_revoked",
            "email_verification_consumed",
            "device_token_invalidated",
            "artifact_transaction_status",
            "foreign_key_fixture",
            "same_jar_runtime",
            "remote_environment_mutation",
            "inherited_java_options",
            "build_user_home_isolated",
            "build_maven_repository_explicit",
        }
        self.assertTrue(required.issubset(recorded), required - recorded)
        for forbidden in (
            "record db_password",
            "record admin_a_password",
            "record admin_b_password",
            "record passenger_password",
            "record jwt_secret",
            "record refresh_token_hash",
            "record device_token_value",
            "record passenger_email",
        ):
            self.assertNotIn(forbidden, self.source.lower())
        self.assertIn("set -o noclobber", self.source)
        self.assertIn("failed_check_sha256", self.source)
        self.assertIn("cleanup_status=", self.source)
        self.assertIn("chmod 600 \"$MANIFEST_PATH\"", self.source)

    def test_focused_smoke_uses_no_fk_blocker_fixture_or_remote_target(self) -> None:
        self.assertIn("record foreign_key_fixture NOT_USED", self.source)
        self.assertNotIn("INSERT INTO presenca", self.source)
        self.assertNotIn("https://api", self.source)
        self.assertIn("record remote_environment_mutation NONE", self.source)


if __name__ == "__main__":
    unittest.main()
