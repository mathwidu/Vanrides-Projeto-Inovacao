#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/qa_maven_evidence_lock.sh"
RBAC_TESTS="RbacWebMvcTest,PresencaRbacWebMvcTest,TenantIsolationWebMvcTest,StompTenantChannelInterceptorTest,SecurityFilterWebMvcTest,LocalizacaoMotoristaControllerWebMvcTest,PassengerLocationReadPolicyTest,OperationalDateProviderTest,GuardianAccessServiceTest,PassageiroServiceTest,PassageiroServiceCredentialPolicyTest,PassageiroServiceIdentityLifecycleTest,PassageiroControllerWebMvcTest,MotoristaServiceTest,MotoristaPublicRegistrationServiceTest,MotoristaControllerWebMvcTest,CompanyServiceTest,CompanyAdminServiceTest,CompanyAdminIdentityMaintenanceServiceTest,CompanyAdminControllerWebMvcTest,ViagemOperationalDateWebMvcTest"
EXPECTED_TEST_CLASSES=(
  "com.vanrides.vanrides.controller.RbacWebMvcTest=10"
  "com.vanrides.vanrides.controller.PresencaRbacWebMvcTest=21"
  "com.vanrides.vanrides.controller.TenantIsolationWebMvcTest=8"
  "com.vanrides.vanrides.config.StompTenantChannelInterceptorTest=9"
  "com.vanrides.vanrides.controller.SecurityFilterWebMvcTest=9"
  "com.vanrides.vanrides.controller.LocalizacaoMotoristaControllerWebMvcTest=17"
  "com.vanrides.vanrides.service.PassengerLocationReadPolicyTest=3"
  "com.vanrides.vanrides.service.OperationalDateProviderTest=4"
  "com.vanrides.vanrides.service.GuardianAccessServiceTest=4"
  "com.vanrides.vanrides.service.PassageiroServiceTest=6"
  "com.vanrides.vanrides.service.PassageiroServiceCredentialPolicyTest=9"
  "com.vanrides.vanrides.service.PassageiroServiceIdentityLifecycleTest=6"
  "com.vanrides.vanrides.controller.PassageiroControllerWebMvcTest=8"
  "com.vanrides.vanrides.service.MotoristaServiceTest=10"
  "com.vanrides.vanrides.service.MotoristaPublicRegistrationServiceTest=4"
  "com.vanrides.vanrides.controller.MotoristaControllerWebMvcTest=5"
  "com.vanrides.vanrides.service.CompanyServiceTest=4"
  "com.vanrides.vanrides.service.CompanyAdminServiceTest=9"
  "com.vanrides.vanrides.service.CompanyAdminIdentityMaintenanceServiceTest=2"
  "com.vanrides.vanrides.controller.CompanyAdminControllerWebMvcTest=6"
  "com.vanrides.vanrides.controller.ViagemOperationalDateWebMvcTest=2"
)
EVIDENCE_ROOT="${VANRIDES_QA_EVIDENCE_ROOT:-$ROOT_DIR/output/qa-evidence}"

fail() {
  echo "FAIL: $*" >&2
  exit 1
}

reject_git_environment_overrides() {
  local variable

  while IFS= read -r variable; do
    case "$variable" in
      GIT_PAGER)
        ;;
      GIT_*)
        [[ -z "${!variable:-}" ]] \
          || fail "$variable override nao permitido no gate canonico."
        ;;
    esac
  done < <(builtin compgen -e)
}

canonical_git() {
  env -i \
    HOME=/nonexistent \
    XDG_CONFIG_HOME=/nonexistent \
    PATH="$PATH" \
    LC_ALL=C \
    GIT_CONFIG_GLOBAL=/dev/null \
    GIT_CONFIG_SYSTEM=/dev/null \
    GIT_CONFIG_NOSYSTEM=1 \
    GIT_NO_REPLACE_OBJECTS=1 \
    git -c core.fsmonitor=false -c core.untrackedCache=false \
      -C "$ROOT_DIR" "$@"
}

cd "$ROOT_DIR"

RETENTION_CLASS="${VANRIDES_QA_RETENTION_CLASS:-local}"
REQUIRE_CLEAN="${VANRIDES_QA_REQUIRE_CLEAN:-0}"
case "$RETENTION_CLASS" in
  local|ci|release)
    ;;
  *)
    fail "VANRIDES_QA_RETENTION_CLASS deve ser local, ci ou release."
    ;;
esac
case "$REQUIRE_CLEAN" in
  0|1)
    ;;
  *)
    fail "VANRIDES_QA_REQUIRE_CLEAN deve ser 0 ou 1."
    ;;
esac
if [[ -z "$EVIDENCE_ROOT" || "$EVIDENCE_ROOT" != /* \
    || "$EVIDENCE_ROOT" == "/" || "$EVIDENCE_ROOT" == *$'\n'* \
    || "$EVIDENCE_ROOT" == *$'\r'* || "$EVIDENCE_ROOT" == *$'\t'* ]]; then
  fail "VANRIDES_QA_EVIDENCE_ROOT deve ser um caminho absoluto seguro."
fi

reject_git_environment_overrides
RUN_GIT_TOPLEVEL="$(canonical_git rev-parse --show-toplevel)"
REPOSITORY_DIR="$(cd "$RUN_GIT_TOPLEVEL" && pwd -P)"
MODULE_DIR="$(cd "$ROOT_DIR" && pwd -P)"
# Standalone fixtures remain supported; the academic checkout has one fixed module path.
[[ "$MODULE_DIR" == "$REPOSITORY_DIR" || "$MODULE_DIR" == "$REPOSITORY_DIR/src/backend" ]] \
  || fail "Git toplevel nao corresponde a raiz do repositorio e ao modulo backend esperado."

RUN_ID="rbac-$(date -u '+%Y%m%dT%H%M%SZ')-$$"
RUN_STARTED_EPOCH="$(date '+%s')"
RUN_GIT_SHA="$(canonical_git rev-parse HEAD)"
RUN_GIT_TREE="$(canonical_git rev-parse 'HEAD^{tree}')"

MAVEN_PID=""
MAVEN_STARTING=0
SIGNAL_EXIT_CODE=0
SIGNAL_NAME=""
LOCK_ACQUIRED=0
EVIDENCE_ARCHIVED=0
FAILURE_STAGE="preflight"

record_failure_diagnostic() {
  local failure_status="$1"
  local failure_args=(
    --suite rbac
    --run-id "$RUN_ID"
    --started-epoch "$RUN_STARTED_EPOCH"
    --evidence-dir "$EVIDENCE_ROOT/$RUN_ID"
    --repo-root "$REPOSITORY_DIR"
    --expected-git-sha "$RUN_GIT_SHA"
    --expected-git-tree "$RUN_GIT_TREE"
    --retention-class "$RETENTION_CLASS"
    --failure-stage "$FAILURE_STAGE"
    --exit-code "$failure_status"
  )

  if [[ "$REQUIRE_CLEAN" == "1" ]]; then
    failure_args+=(--require-clean)
  fi

  if ! python3 "$ROOT_DIR/scripts/qa_report_evidence.py" \
      "${failure_args[@]}"; then
    printf 'WARN: nao foi possivel arquivar o diagnostico de falha da suite RBAC.\n' >&2
  fi
}

evidence_has_terminal_manifest() {
  local manifest_path="$EVIDENCE_ROOT/$RUN_ID/manifest.json"

  [[ -f "$manifest_path" && ! -L "$manifest_path" ]] || return 1
  python3 -c \
    'import json,sys; status=json.load(open(sys.argv[1], "rb")).get("status"); raise SystemExit(0 if status in {"passed", "failed"} else 1)' \
    "$manifest_path" >/dev/null 2>&1
}

cleanup_runtime() {
  local original_status=$?
  local release_status=0

  trap - EXIT
  set +e
  if (( original_status != 0 && EVIDENCE_ARCHIVED == 0 )) \
      && ! evidence_has_terminal_manifest; then
    record_failure_diagnostic "$original_status"
  fi
  if (( LOCK_ACQUIRED == 1 )); then
    qa_maven_evidence_lock_release
    release_status=$?
    if (( release_status != 0 && original_status == 0 )); then
      original_status=1
    fi
    LOCK_ACQUIRED=0
  fi
  exit "$original_status"
}

forward_signal_to_maven() {
  local signal_name="$1"
  local exit_code="$2"

  FAILURE_STAGE="signal"
  if (( SIGNAL_EXIT_CODE == 0 )); then
    SIGNAL_EXIT_CODE="$exit_code"
    SIGNAL_NAME="$signal_name"
  fi
  if [[ -n "$MAVEN_PID" ]] && kill -0 "$MAVEN_PID" 2>/dev/null; then
    kill -s "$signal_name" "$MAVEN_PID" 2>/dev/null || true
  elif (( MAVEN_STARTING == 0 )); then
    exit "$SIGNAL_EXIT_CODE"
  fi
}

run_maven_and_wait() {
  local maven_status=0

  MAVEN_STARTING=1
  "$@" &
  MAVEN_PID=$!
  MAVEN_STARTING=0
  if [[ -n "$SIGNAL_NAME" ]] && kill -0 "$MAVEN_PID" 2>/dev/null; then
    kill -s "$SIGNAL_NAME" "$MAVEN_PID" 2>/dev/null || true
  fi

  set +e
  wait "$MAVEN_PID"
  maven_status=$?
  while kill -0 "$MAVEN_PID" 2>/dev/null; do
    wait "$MAVEN_PID"
    maven_status=$?
  done
  set -e
  MAVEN_PID=""

  if (( SIGNAL_EXIT_CODE != 0 )); then
    exit "$SIGNAL_EXIT_CODE"
  fi
  return "$maven_status"
}
# Bash starts asynchronous commands with SIGINT ignored when job control is off.
# Terminate the Maven child with SIGTERM, but preserve the conventional 130
# exit code that identifies the caller's SIGINT.
trap cleanup_runtime EXIT
trap 'forward_signal_to_maven TERM 130' INT
trap 'forward_signal_to_maven TERM 143' TERM

if [[ "$REQUIRE_CLEAN" == "1" ]] \
    && [[ -n "$(canonical_git status --porcelain --untracked-files=all)" ]]; then
  fail "Clean source required before starting RBAC evidence run."
fi
qa_maven_evidence_lock_acquire rbac "$RUN_ID" "$RUN_GIT_SHA" || exit $?
LOCK_ACQUIRED=1

echo "[qa-rbac] Removing previous reports for the exact evidence matrix..."
for expectation in "${EXPECTED_TEST_CLASSES[@]}"; do
  class_name="${expectation%=*}"
  rm -f \
    "target/surefire-reports/TEST-${class_name}.xml" \
    "target/surefire-reports/${class_name}.txt"
done

echo "[qa-rbac] Running RBAC evidence suite..."
FAILURE_STAGE="maven"
run_maven_and_wait ./mvnw -B \
  -Dvanrides.qa.run-id="$RUN_ID" \
  -Dvanrides.qa.git-sha="$RUN_GIT_SHA" \
  -Dtest="$RBAC_TESTS" \
  -DfailIfNoTests=true \
  clean test

FAILURE_STAGE="validation"
common_evidence_args=(
  --suite rbac
  --run-id "$RUN_ID"
  --started-epoch "$RUN_STARTED_EPOCH"
  --evidence-dir "$EVIDENCE_ROOT/$RUN_ID"
  --repo-root "$REPOSITORY_DIR"
  --expected-git-sha "$RUN_GIT_SHA"
  --expected-git-tree "$RUN_GIT_TREE"
  --retention-class "$RETENTION_CLASS"
)
prepare_evidence_args=(
  --phase prepare
  --reports-dir target/surefire-reports
)
for expectation in "${EXPECTED_TEST_CLASSES[@]}"; do
  prepare_evidence_args+=(--expected "$expectation")
done
if [[ "$REQUIRE_CLEAN" == "1" ]]; then
  common_evidence_args+=(--require-clean)
fi

python3 scripts/qa_report_evidence.py \
  "${common_evidence_args[@]}" \
  "${prepare_evidence_args[@]}"

echo "[qa-rbac] Releasing the Maven evidence lock before publishing PASS..."
if qa_maven_evidence_lock_release; then
  LOCK_ACQUIRED=0
else
  fail "Nao foi possivel liberar o lock Maven; a evidencia preparada sera convertida em diagnostico de falha."
fi

python3 scripts/qa_report_evidence.py \
  "${common_evidence_args[@]}" \
  --phase finalize
EVIDENCE_ARCHIVED=1

echo "[qa-rbac] RBAC evidence suite passed."
