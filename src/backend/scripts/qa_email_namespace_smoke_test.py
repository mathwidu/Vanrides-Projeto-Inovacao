#!/usr/bin/env python3
from __future__ import annotations

import re
import stat
import subprocess
import tempfile
import unittest
from pathlib import Path


ROOT = Path(__file__).resolve().parents[1]
RUNNER = ROOT / "scripts" / "qa_email_namespace_smoke.sh"


class EmailNamespaceSmokeContractTest(unittest.TestCase):
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

    def test_runner_is_repo_relative_and_requires_exact_backend_sha(self) -> None:
        self.assertNotIn("/Users", self.source)
        self.assertIn(
            'ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"',
            self.source,
        )
        self.assertIn(
            'EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"',
            self.source,
        )
        self.assertIn(
            '[[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] || fail expected_backend_sha_required',
            self.source,
        )
        self.assertIn(
            'MODULE_GIT_PREFIX="$(git_safe rev-parse --show-prefix)"',
            self.source,
        )
        self.assertIn(
            'assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha',
            self.source,
        )

    def test_clean_repository_and_runner_provenance_precede_build(self) -> None:
        required = (
            "git_safe status --porcelain --untracked-files=all",
            "git_safe rev-parse 'HEAD^{tree}'",
            '"${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_email_namespace_smoke.sh"',
            'git_safe hash-object --no-filters "$SCRIPT_PATH"',
            "runner_not_versioned",
            "runner_blob_mismatch",
            "GIT_NO_REPLACE_OBJECTS=1",
            "backend_worktree_not_clean",
            "backend_worktree_changed_during_smoke",
            "backend_sha_changed_during_smoke",
            "backend_tree_changed_during_smoke",
            "runner_changed_during_smoke",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        clean = self.source.index("backend_worktree_not_clean")
        provenance = self.source.index("runner_blob_mismatch")
        build = self.source.index('if ! run_clean_build >"$TMP_DIR/build.log"')
        self.assertLess(clean, provenance)
        self.assertLess(provenance, build)

    def test_hidden_index_flags_are_rejected_before_and_after(self) -> None:
        self.assertIn("assert_no_hidden_index_flags()", self.source)
        self.assertIn('git_safe -C "$repo_root" ls-files -v --', self.source)
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

    def test_disposable_postgres_16_has_safe_owner_and_optional_image_binding(self) -> None:
        required = (
            "docker run --detach --pull=missing",
            "--publish '127.0.0.1::5432'",
            '--label-file "$CONTAINER_LABEL_FILE"',
            '--env-file "$POSTGRES_ENV_FILE"',
            'POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"',
            'EXPECTED_POSTGRES_IMAGE_ID="${VANRIDES_EXPECTED_POSTGRES_IMAGE_ID:-}"',
            "invalid_expected_postgres_image_id",
            "POSTGRES_VERSION_NUM",
            "postgres_major_not_16",
            "record postgres_major 16",
            "CONTAINER_ATTEMPTED=1",
            "coordinator_container_ownership_pair_required",
            "invalid_coordinator_container_name",
            "invalid_coordinator_container_run_token",
            "com.vanrides.qa.email-namespace.run-token",
            "postgres_container_ownership",
            "postgres_container_name_collision",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        self.assertIn('docker rm --force "$owned_container_id"', cleanup)
        self.assertNotIn('docker rm --force "$CONTAINER_NAME"', cleanup)
        self.assertIn('owner_value="$(docker inspect --format', cleanup)
        self.assertIn('owner_value" == "$CONTAINER_RUN_TOKEN', cleanup)
        self.assertIn('docker inspect "$CONTAINER_NAME"', cleanup)
        self.assertNotIn('--label "${CONTAINER_LABEL_KEY}=${CONTAINER_RUN_TOKEN}"', self.source)
        self.assertNotIn('--env "POSTGRES_PASSWORD=${DB_PASSWORD}"', self.source)

    def test_cleanup_removes_owned_id_and_never_removes_a_racing_replacement(self) -> None:
        start = self.source.index("cleanup() {")
        end = self.source.index("\n}\n\ntrap cleanup EXIT", start) + 2
        cleanup_function = self.source[start:end]

        for replacement_race in (False, True):
            with self.subTest(replacement_race=replacement_race), tempfile.TemporaryDirectory() as temp_dir:
                root = Path(temp_dir)
                run_dir = root / "vanrides-namespace.fixture"
                run_dir.mkdir()
                summary = run_dir / "summary.env"
                summary.write_text("run_id=fixture\n", encoding="utf-8")
                old_alive = root / "old.alive"
                name_id = root / "name.id"
                removed = root / "removed.log"
                old_id = "a" * 64
                replacement_id = "b" * 64
                old_alive.write_text("1\n", encoding="utf-8")
                name_id.write_text(f"{old_id}\n", encoding="utf-8")
                race_flag = "1" if replacement_race else "0"
                harness = f"""
set -uo pipefail
TMP_BASE={str(root)!r}
TMP_DIR={str(run_dir)!r}
SUMMARY_FILE={str(summary)!r}
EVIDENCE_DIR={str(root / 'evidence')!r}
MANIFEST_PATH={str(root / 'evidence' / 'manifest.env')!r}
RUN_ID=fixture
MANIFEST_RELATIVE=output/qa-evidence/fixture/manifest.env
APP_PID=""
CONTAINER_ATTEMPTED=1
CONTAINER_ID={old_id!r}
CONTAINER_NAME=vanrides-owned-fixture
CONTAINER_RUN_TOKEN=0123456789abcdef0123456789abcdef
MAIN_RESULT=""
FAILED_CHECK=fixture_failure
OLD_ALIVE={str(old_alive)!r}
NAME_ID={str(name_id)!r}
REMOVED={str(removed)!r}
OLD_ID={old_id!r}
REPLACEMENT_ID={replacement_id!r}
RACE={race_flag!r}
append_log_hashes() {{ :; }}
sha256_text() {{ printf '%064d\n' 0; }}
ensure_evidence_base() {{ :; }}
persist_manifest_create_only() {{ :; }}
docker() {{
  local target="${{@: -1}}"
  case "$1" in
    info) return 0 ;;
    inspect)
      if [[ "${{2:-}}" == "--format" ]]; then
        printf '%s\n' "$CONTAINER_RUN_TOKEN"
      elif [[ "$target" == "$OLD_ID" ]]; then
        [[ -f "$OLD_ALIVE" ]]
      elif [[ "$target" == "$CONTAINER_NAME" ]]; then
        [[ -f "$NAME_ID" ]]
      else
        return 1
      fi
      ;;
    rm)
      [[ "${{3:-}}" == "$OLD_ID" ]] || return 1
      printf '%s\n' "${{3:-}}" >>"$REMOVED"
      rm -f "$OLD_ALIVE" "$NAME_ID"
      if [[ "$RACE" == 1 ]]; then
        printf '%s\n' "$REPLACEMENT_ID" >"$NAME_ID"
      fi
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
                self.assertFalse(old_alive.exists())
                expected_cleanup = "FAIL" if replacement_race else "PASS"
                self.assertIn(f"cleanup={expected_cleanup}", completed.stdout)
                if replacement_race:
                    self.assertEqual(f"{replacement_id}\n", name_id.read_text(encoding="utf-8"))
                else:
                    self.assertFalse(name_id.exists())

    def test_private_same_jar_is_built_copied_started_and_held_through_gate(self) -> None:
        required = (
            "run_clean_build()",
            "./mvnw -q clean package -DskipTests",
            "jar_candidate_count",
            'RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"',
            'cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH"',
            "runtime_jar_copy_hash",
            '"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"',
            "assert_runtime_unchanged gate_before",
            "assert_runtime_unchanged gate_after",
            "assert_runtime_unchanged final",
            "jar_changed_during_smoke",
            "record same_jar_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        build = self.source.index('if ! run_clean_build >"$TMP_DIR/build.log"')
        copy = self.source.index('cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH"')
        start = self.source.index('"$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"')
        gate = self.source.index('run_backend_gate >"$TMP_DIR/backend-gate.log"')
        first_http = self.source.index("bootstrap_admin_status")
        self.assertLess(build, copy)
        self.assertLess(copy, start)
        self.assertLess(start, gate)
        self.assertLess(gate, first_http)

    def test_gate_subshell_drops_docker_and_maven_overrides(self) -> None:
        start = self.source.index("run_backend_gate() (")
        end = self.source.index("\n)\n\nfor command", start)
        gate = self.source[start:end]
        self.assertIn("unset DOCKER_HOST DOCKER_CONTEXT", gate)
        self.assertIn("MAVEN_*|MVNW_*) unset", gate)
        self.assertIn(
            'VANRIDES_BACKEND_BASE_URL="$BASE_URL" ./scripts/qa_gate_card.sh',
            gate,
        )
        self.assertNotIn("export DOCKER_HOST", gate)

    def test_http_and_database_credentials_are_not_placed_in_process_arguments(self) -> None:
        http_start = self.source.index("http_request() {")
        http_end = self.source.index("\n}\n\ndb_scalar()", http_start)
        helper = self.source[http_start:http_end]
        self.assertIn('config_file="${output_file}.curl-config"', helper)
        self.assertIn('chmod 600 "$config_file"', helper)
        self.assertIn('user = "%s:%s"', helper)
        self.assertIn('header = "Authorization: Bearer %s"', helper)
        self.assertIn('--data-binary @-', helper)
        self.assertIn('printf \'%s\' "$payload"', helper)
        self.assertNotIn("--user", helper)
        self.assertNotIn('--data "$payload"', helper)
        self.assertNotIn('docker exec --env "PGPASSWORD=', self.source)
        self.assertIn("docker exec --interactive", self.source)
        self.assertIn('--set ON_ERROR_STOP=1 --file -', self.source)
        self.assertNotIn('--command "$sql"', self.source)
        self.assertIn('POSTGRES_PASSWORD=%s', self.source)
        self.assertIn('chmod 600 "$POSTGRES_ENV_FILE" "$CONTAINER_LABEL_FILE"', self.source)
        self.assertIn('chmod 600 "$RELEASED_EMAIL_EXPECTED_FILE"', self.source)
        self.assertIn(
            'assert_changed_body "$TMP_DIR/change.json" "$RELEASED_EMAIL_EXPECTED_FILE"',
            self.source,
        )
        self.assertNotIn(
            'assert_changed_body "$TMP_DIR/change.json" "$RELEASED_EMAIL"',
            self.source,
        )

    def test_runtime_configuration_is_scoped_to_required_lane(self) -> None:
        required = (
            "env -i",
            "SPRING_DATASOURCE_URL=",
            "VANRIDES_EMAIL_PROVIDER=log",
            "VANRIDES_EMAIL_VERIFICATION_RESEND_COOLDOWN_MINUTES=0",
            "VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=qa-admin.invalid",
            "LOGGING_LEVEL_COM_VANRIDES_VANRIDES_SERVICE=WARN",
            "VANRIDES_AUTH_BASIC_ENABLED=true",
            "VANRIDES_RATE_LIMIT_ENABLED=false",
            "VANRIDES_ROUTING_PROVIDER=static",
            "VANRIDES_PUSH_PROVIDER=none",
            "VANRIDES_MAPS_PLATFORM_ENABLED=false",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertIn("@qa-admin.invalid", self.source)
        self.assertIn("@pending-user.invalid", self.source)
        self.assertEqual(3, self.source.count("@qa-admin.invalid"))
        self.assertIn(
            'EMAIL_SUFFIX="$(printf \'%s\' "$RUN_ID" | tr \'[:upper:]\' \'[:lower:]\')"',
            self.source,
        )
        self.assertIn('ADMIN_EMAIL="admin.${EMAIL_SUFFIX}@qa-admin.invalid"', self.source)
        self.assertIn('GUARDIAN_EMAIL="guardian.${EMAIL_SUFFIX}@qa-admin.invalid"', self.source)
        self.assertIn(
            'GUARDIAN_MOVED_EMAIL="guardian-moved.${EMAIL_SUFFIX}@qa-admin.invalid"',
            self.source,
        )
        self.assertIn('PASSENGER_EMAIL="passenger.${EMAIL_SUFFIX}@pending-user.invalid"', self.source)
        self.assertNotIn('ADMIN_EMAIL="admin.${RUN_ID}', self.source)

    def test_flyway_is_exactly_v25_with_all_25_successful_sql_migrations(self) -> None:
        self.assertIn("FLYWAY_LATEST_VERSION", self.source)
        self.assertIn("FLYWAY_SUCCESS_COUNT", self.source)
        self.assertIn(
            'assert_equal "$FLYWAY_LATEST_VERSION" 25 flyway_latest_version',
            self.source,
        )
        self.assertIn(
            'assert_equal "$FLYWAY_SUCCESS_COUNT" 25 flyway_success_count',
            self.source,
        )
        self.assertIn("record flyway_latest_version 25", self.source)
        self.assertIn("record flyway_success_count 25", self.source)

    def test_http_scenarios_are_complete_and_causally_ordered(self) -> None:
        ordered = (
            "bootstrap_admin_status",
            "admin_login_status",
            "basic_protected_status",
            "bearer_protected_status",
            "refresh_rotation_status",
            "rotated_bearer_status",
            "guardian_create_status",
            "guardian_registry_exact",
            "guardian_cross_role_conflict_status",
            "guardian_login_status",
            "device_admin_registration_status",
            "device_race_serialization_status",
            "device_guardian_registration_status",
            "guardian_change_status",
            "device_change_invalidation_status",
            "guardian_stale_bearer_status",
            "guardian_moved_login_status",
            "device_before_delete_registration_status",
            "guardian_delete_status",
            "device_delete_invalidation_status",
            "invalid_address_status",
            "passenger_create_status",
            "passenger_registry_exact",
            "intra_role_conflict_status",
            "resend_status",
            "resend_async_not_observed",
            "unknown_resend_status",
            "malformed_resend_status",
            "opaque_resend_mutation",
            'assert_generic_conflict_body "$TMP_DIR/conflict.json"',
            "record cross_role_conflict_status 409",
            "change_pending_address_status",
            "registry_old_key_not_released",
            "registry_new_key_not_exact",
            "passenger_delete_status",
            "passenger_delete_registry",
            "released_driver_create_status",
            "released_driver_registry_exact",
        )
        positions = [self.source.index(marker) for marker in ordered]
        self.assertEqual(sorted(positions), positions)
        for status in ("201", "200", "202", "409", "204"):
            self.assertIn(f" {status} ", self.source)
        self.assertIn("assert_bootstrap_body", self.source)
        self.assertIn("assert_login_body", self.source)
        self.assertIn("assert_pending_account_body", self.source)
        self.assertIn("assert_generic_conflict_body", self.source)
        self.assertIn("assert_invalid_address_body", self.source)
        self.assertIn("assert_opaque_resend_bodies", self.source)
        self.assertIn("assert_changed_body", self.source)

    def test_guardian_reservation_and_cross_role_collision_are_proved(self) -> None:
        required = (
            "POST /company-admins/responsaveis",
            "assert_guardian_body",
            "guardian_create_status 201",
            "registry.user_role = 'GUARDIAN'",
            "registry.user_id = source.id",
            "registry.normalized_email = source.email",
            "guardian_verified_sql",
            "guardian_cross_role_conflict_status",
            "assert_generic_conflict_body \"$TMP_DIR/guardian-cross-role.json\"",
            "guardian_cross_role_conflict_mutation",
            "DELETE \"/company-admins/responsaveis/${GUARDIAN_ID}\"",
            "guardian_delete_registry",
            "guardian_namespace_release_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        created = self.source.index("guardian_create_status")
        collision = self.source.index("guardian_cross_role_conflict_status")
        deleted = self.source.index("guardian_delete_status")
        self.assertLess(created, collision)
        self.assertLess(collision, deleted)

    def test_device_registration_reassignment_and_invalidation_are_sql_proved(self) -> None:
        required = (
            "assert_device_owner()",
            "POST /devices/fcm-token",
            "device_admin_registration_status 204",
            "DEVICE_ADMIN_RACE_PID",
            "DEVICE_GUARDIAN_RACE_PID",
            'wait "$DEVICE_ADMIN_RACE_PID"',
            'wait "$DEVICE_GUARDIAN_RACE_PID"',
            "device_race_single_row",
            "device_race_serialization_status PASS",
            "device_guardian_registration_status 204",
            'assert_device_owner "$DEVICE_TOKEN_HASH" GUARDIAN',
            "device_change_invalidation_status PASS",
            'SELECT (invalidated_at IS NOT NULL)::text FROM device_token WHERE token_hash =',
            "device_before_delete_registration_status 204",
            'assert_device_owner "$DELETE_DEVICE_TOKEN_HASH" GUARDIAN',
            'for deleted_owner_token_hash in "$DEVICE_TOKEN_HASH" "$DELETE_DEVICE_TOKEN_HASH"',
            "SELECT (user_email IS NULL)::text FROM device_token",
            "device_delete_invalidation_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        owner_helper = self.source[
            self.source.index("assert_device_owner()") : self.source.index(
                "\n}\n\nassert_runtime_unchanged()", self.source.index("assert_device_owner()")
            )
        ]
        self.assertIn("registry.normalized_email = device.user_email", owner_helper)
        self.assertIn("device.active = TRUE", owner_helper)
        self.assertNotIn("DEVICE_TOKEN", owner_helper)

        first_owner = self.source.index("device_admin_owner_status PASS")
        race = self.source.index("device_race_serialization_status PASS")
        guardian_owner = self.source.index("device_guardian_owner_status PASS")
        changed = self.source.index("device_change_invalidation_status PASS")
        delete_owner = self.source.index("device_before_delete_owner_status PASS")
        deleted = self.source.index("device_delete_invalidation_status PASS")
        self.assertEqual(
            sorted((first_owner, race, guardian_owner, changed, delete_owner, deleted)),
            [first_owner, race, guardian_owner, changed, delete_owner, deleted],
        )

    def test_boolean_sql_scalars_use_canonical_text_instead_of_psql_t_f(self) -> None:
        required = (
            "SELECT email_verified::text FROM company_admin",
            "SELECT email_verified::text FROM guardian",
            "SELECT active::text FROM device_token",
            "SELECT (invalidated_at IS NOT NULL)::text FROM device_token",
            "SELECT (device.user_email = source.email)::text FROM device_token",
            "SELECT (user_email IS NULL)::text FROM device_token",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        forbidden = (
            "SELECT email_verified FROM company_admin",
            "SELECT email_verified FROM guardian",
            "SELECT active FROM device_token",
            "SELECT invalidated_at IS NOT NULL FROM device_token",
            "SELECT device.user_email = source.email FROM device_token",
            "SELECT user_email IS NULL FROM device_token",
        )
        for marker in forbidden:
            self.assertNotIn(marker, self.source)

    def test_old_guardian_bearer_is_rejected_after_address_change(self) -> None:
        required = (
            "GUARDIAN_ACCESS_CREDENTIAL",
            "guardian_change_status 200",
            "GET /account/me",
            "guardian_stale_bearer_status 401",
            "GUARDIAN_MOVED_ACCESS_CREDENTIAL",
            "guardian_moved_login_status 200",
        )
        for marker in required:
            self.assertIn(marker, self.source)

        issued = self.source.index('GUARDIAN_ACCESS_CREDENTIAL="$(json_get')
        changed = self.source.index("guardian_change_status 200")
        rejected = self.source.index("guardian_stale_bearer_status 401")
        reissued = self.source.index('GUARDIAN_MOVED_ACCESS_CREDENTIAL="$(json_get')
        self.assertLess(issued, changed)
        self.assertLess(changed, rejected)
        self.assertLess(rejected, reissued)

    def test_conflict_contract_is_exact_and_does_not_echo_context(self) -> None:
        start = self.source.index("assert_generic_conflict_body()")
        end = self.source.index("\n}\n\nassert_changed_body()", start)
        validator = self.source[start:end]
        self.assertIn('payload.get("code") != "EMAIL_ALREADY_IN_USE"', validator)
        self.assertIn('payload.get("message") != "O e-mail informado já está em uso."', validator)
        self.assertIn('payload.get("email") is not None', validator)
        self.assertIn("set(payload).difference(expected_keys)", validator)
        self.assertNotIn("PASSENGER_EMAIL", validator)
        self.assertNotIn("COMPANY_ID", validator)

    def test_async_resend_is_observed_in_sql_with_zero_cooldown(self) -> None:
        required = (
            "INITIAL_VERIFICATION_ID",
            "CURRENT_VERIFICATION_ID",
            "ACTIVE_VERIFICATION_COUNT",
            '"$CURRENT_VERIFICATION_ID" -gt "$INITIAL_VERIFICATION_ID"',
            '"$ACTIVE_VERIFICATION_COUNT" == 1',
            "VANRIDES_EMAIL_VERIFICATION_RESEND_COOLDOWN_MINUTES=0",
            "record resend_async_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_final_sql_invariants_cover_registry_and_active_devices(self) -> None:
        required = (
            "SOURCE_COUNT",
            "IDENTITY_COUNT",
            "MISSING_COUNT",
            "ORPHAN_COUNT",
            "ACTIVE_DEVICE_MISMATCH_COUNT",
            'assert_equal "$SOURCE_COUNT" "$IDENTITY_COUNT"',
            'assert_equal "$MISSING_COUNT" 0',
            'assert_equal "$ORPHAN_COUNT" 0',
            'assert_equal "$ACTIVE_DEVICE_MISMATCH_COUNT" 0',
            "FROM device_token device LEFT JOIN login_identity registry",
            "device.active = TRUE",
            "record source_count",
            "record identity_count",
            "record missing_count 0",
            "record orphan_count 0",
            "record active_device_mismatch_count 0",
        )
        for marker in required:
            self.assertIn(marker, self.source)

    def test_runtime_logs_are_sanitized_before_hashing(self) -> None:
        required = (
            "LOGGING_LEVEL_COM_VANRIDES_VANRIDES_SERVICE=WARN",
            '"$GUARDIAN_EMAIL" "$GUARDIAN_MOVED_EMAIL"',
            '"$GUARDIAN_PASSWORD" "$DEVICE_TOKEN" "$DELETE_DEVICE_TOKEN"',
            '"$GUARDIAN_REFRESH_CREDENTIAL" "$GUARDIAN_MOVED_ACCESS_CREDENTIAL"',
            'SENSITIVE_PATTERNS_FILE="$TMP_DIR/sensitive-patterns.txt"',
            'chmod 600 "$SENSITIVE_PATTERNS_FILE"',
            'printf \'%s\\n\' "$sensitive_value" >>"$SENSITIVE_PATTERNS_FILE"',
            '"$TMP_DIR/backend-gate.log" "$TMP_DIR/db.stderr"',
            'grep -Fq --file="$SENSITIVE_PATTERNS_FILE" "$runtime_log"',
            "runtime_log_sensitive_value",
            "application_log_mail_body_marker",
            "application_log_raw_credential_marker",
            "record application_log_sanitization_status PASS",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        sanitization = self.source.index("record application_log_sanitization_status PASS")
        final_hash = self.source.index('JAR_SHA256_AFTER="$(sha256_file "$RUNTIME_JAR_PATH")"')
        self.assertLess(sanitization, final_hash)

    def test_manifest_is_sanitized_create_only_and_logs_are_hash_only(self) -> None:
        required = (
            'EVIDENCE_BASE="$ROOT_DIR/output/qa-evidence"',
            'MANIFEST_PATH="$EVIDENCE_DIR/manifest.env"',
            "persist_manifest_create_only()",
            '[[ ! -e "$EVIDENCE_DIR" ]] || return 1',
            'mkdir -- "$EVIDENCE_DIR"',
            "set -o noclobber",
            '>"$MANIFEST_PATH"',
            "chmod 600",
            "build_log_sha256",
            "application_log_sha256",
            "backend_gate_log_sha256",
            "db_log_sha256",
            "docker_log_sha256",
            "smoke_result=",
            "failed_check_sha256=",
            "cleanup_status=",
        )
        for marker in required:
            self.assertIn(marker, self.source)
        self.assertNotIn("record failed_check ", self.source)
        self.assertNotIn('printf \'%s\\n\' "$summary_body"', self.source)
        self.assertNotRegex(self.source, r"cp .*\.log .*output/qa-evidence")

        keys = re.findall(r"^\s*record\s+\"?([a-z0-9_]+)\"?\s+", self.source, re.MULTILINE)
        self.assertGreater(len(keys), 30)
        forbidden_key_fragments = (
            "email",
            "password",
            "senha",
            "secret",
            "credential",
            "authorization",
            "access_token",
            "refresh_token",
        )
        for key in keys:
            for fragment in forbidden_key_fragments:
                self.assertNotIn(fragment, key, f"unsafe manifest key: {key}")

        record_lines = [line for line in self.source.splitlines() if line.lstrip().startswith("record ")]
        forbidden_values = (
            "ADMIN_EMAIL",
            "PASSENGER_EMAIL",
            "RELEASED_EMAIL",
            "ADMIN_PASSWORD",
            "PASSENGER_PASSWORD",
            "DRIVER_PASSWORD",
            "GUARDIAN_PASSWORD",
            "DEVICE_TOKEN",
            "DELETE_DEVICE_TOKEN",
            "JWT_SECRET",
            "CONTAINER_RUN_TOKEN",
        )
        for line in record_lines:
            for marker in forbidden_values:
                self.assertNotIn(marker, line, f"unsafe manifest source: {line}")

    def test_manifest_create_only_primitive_cannot_overwrite(self) -> None:
        start = self.source.index("persist_manifest_create_only() {")
        end = self.source.index("\n}\n\nappend_log_hashes()", start) + 2
        persist_function = self.source[start:end]
        with tempfile.TemporaryDirectory() as temp_dir:
            root = Path(temp_dir)
            evidence = root / "run"
            manifest = evidence / "manifest.env"
            harness = f"""
set -euo pipefail
EVIDENCE_DIR={str(evidence)!r}
MANIFEST_PATH={str(manifest)!r}
{persist_function}
persist_manifest_create_only 'smoke_result=FIRST\n'
if persist_manifest_create_only 'smoke_result=SECOND\n'; then
  exit 9
fi
"""
            completed = subprocess.run(
                ["bash", "-c", harness],
                capture_output=True,
                text=True,
                check=False,
            )
            self.assertEqual(0, completed.returncode, completed.stderr)
            self.assertEqual("smoke_result=FIRST\n", manifest.read_text(encoding="utf-8"))

    def test_runner_has_no_unchecked_jq_dependency(self) -> None:
        self.assertNotRegex(self.source, r"(?:^|\s)jq(?:\s|$)")

    def test_stdout_is_a_fixed_sanitized_summary(self) -> None:
        cleanup = self.source[
            self.source.index("cleanup() {") : self.source.index("\n}\n\ntrap cleanup EXIT")
        ]
        stdout_lines = [line.strip() for line in cleanup.splitlines() if line.strip().startswith("printf '")]
        self.assertEqual(
            [
                'printf \'run_id=%s\\n\' "$RUN_ID"',
                'printf \'manifest=%s\\n\' "$MANIFEST_RELATIVE"',
                'printf \'smoke_result=%s\\n\' "$smoke_result"',
                'printf \'failed_check_sha256=%s\\n\' "$failure_sha256"',
                'printf \'cleanup=%s\\n\' "$cleanup_status"',
            ],
            stdout_lines,
        )
        self.assertNotIn('printf \'failed_check=%s', cleanup)
        self.assertNotIn('cat "$TMP_DIR/application.log"', self.source)
        self.assertNotIn('cat "$TMP_DIR/backend-gate.log"', self.source)


if __name__ == "__main__":
    unittest.main()
