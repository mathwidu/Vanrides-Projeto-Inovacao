#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
source "$ROOT_DIR/scripts/qa_maven_evidence_lock.sh"
EXPECTED_TEST_CLASSES=()
TEST_GROUP=""
EVIDENCE_ROOT="${VANRIDES_QA_EVIDENCE_ROOT:-$ROOT_DIR/output/qa-evidence}"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

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

validate_api_version() {
  local label="$1"
  local value="$2"

  if [[ ! "$value" =~ ^(0|[1-9][0-9]{0,2})\.(0|[1-9][0-9]{0,2})$ ]]; then
    fail "$label invalida: '$value'. Use major.minor canonico, sem zeros a esquerda (ex.: 1.44)."
  fi
}

api_version_is_less() {
  local left="$1"
  local right="$2"
  local left_major="${left%%.*}"
  local left_minor="${left#*.}"
  local right_major="${right%%.*}"
  local right_minor="${right#*.}"

  left_major=$((10#$left_major))
  left_minor=$((10#$left_minor))
  right_major=$((10#$right_major))
  right_minor=$((10#$right_minor))

  if (( left_major < right_major \
        || (left_major == right_major && left_minor < right_minor) )); then
    return 0
  fi
  return 1
}

PROFILE_SCOPE="focused"
case "${1:-}" in
  ""|--all-tests)
    (( $# <= 1 )) || fail "Use --all-tests no maximo uma vez; nenhum outro argumento e aceito."
    [[ "${1:-}" != --all-tests ]] || PROFILE_SCOPE="all-tests"
    ;;
  --group)
    (( $# == 2 )) || fail "Uso: $0 --group pg-1..pg-8|unit"
    case "$2" in pg-[1-8]|unit) TEST_GROUP="$2" ;; *) fail "Grupo desconhecido: $2" ;; esac
    ;;
  *) fail "Argumento desconhecido: $1. Uso: $0 [--all-tests | --group pg-1..pg-8|unit]" ;;
esac
selection="${TEST_GROUP:-focused}"
[[ "$selection" != unit ]] || selection="focused"
expectation_lines="$(python3 "$ROOT_DIR/scripts/qa_test_groups.py" expectations --group "$selection")" \
  || fail "Manifesto da matriz PostgreSQL invalido."
while IFS= read -r expectation; do
  [[ -z "$expectation" ]] || EXPECTED_TEST_CLASSES+=("$expectation")
done <<< "$expectation_lines"

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

RUN_ID="integration-$(date -u '+%Y%m%dT%H%M%SZ')-$$"
RUN_STARTED_EPOCH="$(date '+%s')"
RUN_GIT_SHA="$(canonical_git rev-parse HEAD)"
RUN_GIT_TREE="$(canonical_git rev-parse 'HEAD^{tree}')"

MAVEN_ISOLATED_HOME=""
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
    --suite integration
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
    printf 'WARN: nao foi possivel arquivar o diagnostico de falha da suite integration.\n' >&2
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
    if (( release_status != 0 )); then
      if (( original_status == 0 )); then
        original_status=1
      fi
    else
      LOCK_ACQUIRED=0
    fi
  fi
  if [[ -n "$MAVEN_ISOLATED_HOME" ]]; then
    if (( LOCK_ACQUIRED == 0 )); then
      rm -rf -- "$MAVEN_ISOLATED_HOME" 2>/dev/null || true
    else
      printf 'WARN: user.home isolado preservado porque o lock Maven nao confirmou release.\n' >&2
    fi
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

trap cleanup_runtime EXIT
trap 'forward_signal_to_maven TERM 130' INT
trap 'forward_signal_to_maven TERM 143' TERM

log "Docker preflight for Testcontainers"
command -v docker >/dev/null 2>&1 || fail "Docker CLI nao encontrado. Instale/inicie Docker Desktop para rodar este perfil."
for variable in \
  DOCKER_HOST \
  DOCKER_CONTEXT \
  DOCKER_API_VERSION \
  DOCKER_TLS_VERIFY \
  DOCKER_CERT_PATH \
  JAVA_TOOL_OPTIONS \
  _JAVA_OPTIONS \
  JDK_JAVA_OPTIONS; do
  [[ -z "${!variable:-}" ]] \
    || fail "$variable override nao permitido no gate canonico; remova-o e rode novamente."
done
while IFS= read -r variable; do
  case "$variable" in
    TESTCONTAINERS_API_VERSION)
      ;;
    TESTCONTAINERS_*)
      [[ -z "${!variable:-}" ]] \
        || fail "$variable override nao permitido no gate canonico; apenas TESTCONTAINERS_API_VERSION e aceito."
      ;;
    MAVEN_*|MVNW_*)
      [[ -z "${!variable:-}" ]] \
        || fail "$variable override nao permitido no gate canonico; o runtime Maven e definido pelo perfil."
      ;;
  esac
done < <(builtin compgen -e)
export MAVEN_SKIP_RC=1
# Keep the compiler's initial heap small while PostgreSQL and Surefire share the host.
export MAVEN_OPTS='-Xms24m -XX:+UseSerialGC -XX:ActiveProcessorCount=2'
[[ -n "${HOME:-}" && "$HOME" == /* && -d "$HOME" ]] \
  || fail "HOME deve apontar para um diretorio absoluto existente no gate canonico."
DOCKER_CONTEXT_NAME="$(docker context show 2>/dev/null)" \
  || fail "Nao foi possivel identificar o contexto Docker corrente."
[[ -n "$DOCKER_CONTEXT_NAME" ]] \
  || fail "Contexto Docker corrente vazio."
DOCKER_ENDPOINT="$(docker context inspect "$DOCKER_CONTEXT_NAME" \
  --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" \
  || fail "Nao foi possivel inspecionar o endpoint do contexto Docker corrente."
case "$DOCKER_ENDPOINT" in
  unix:///*)
    DOCKER_ENDPOINT_TRANSPORT="unix"
    ;;
  npipe:////./pipe/*)
    DOCKER_ENDPOINT_TRANSPORT="npipe"
    ;;
  *)
    fail "Docker endpoint remoto nao permitido. Use contexto unix:// ou npipe:// local."
    ;;
esac
export DOCKER_HOST="$DOCKER_ENDPOINT"
unset DOCKER_CONTEXT DOCKER_TLS_VERIFY DOCKER_CERT_PATH
docker info >/dev/null 2>&1 || fail "Docker nao esta disponivel no endpoint local inspecionado. Inicie Docker Desktop e rode novamente."

DOCKER_MIN_API_VERSION="$(docker version --format '{{.Server.MinAPIVersion}}' 2>/dev/null)" \
  || fail "Nao foi possivel obter Docker Server.MinAPIVersion. Verifique a compatibilidade do CLI/daemon."
[[ -n "$DOCKER_MIN_API_VERSION" ]] \
  || fail "Nao foi possivel obter Docker Server.MinAPIVersion. Verifique a compatibilidade do CLI/daemon."
DOCKER_MAX_API_VERSION="$(docker version --format '{{.Server.APIVersion}}' 2>/dev/null)" \
  || fail "Nao foi possivel obter Docker Server.APIVersion. Verifique a compatibilidade do CLI/daemon."
[[ -n "$DOCKER_MAX_API_VERSION" ]] \
  || fail "Nao foi possivel obter Docker Server.APIVersion. Verifique a compatibilidade do CLI/daemon."

validate_api_version "Docker Server.MinAPIVersion" "$DOCKER_MIN_API_VERSION"
validate_api_version "Docker Server.APIVersion" "$DOCKER_MAX_API_VERSION"
if api_version_is_less "$DOCKER_MAX_API_VERSION" "$DOCKER_MIN_API_VERSION"; then
  fail "Faixa Docker invalida: minimo $DOCKER_MIN_API_VERSION maior que maximo $DOCKER_MAX_API_VERSION."
fi

TESTCONTAINERS_API_VERSION="${TESTCONTAINERS_API_VERSION:-$DOCKER_MIN_API_VERSION}"
validate_api_version "TESTCONTAINERS_API_VERSION" "$TESTCONTAINERS_API_VERSION"
if api_version_is_less "$TESTCONTAINERS_API_VERSION" "$DOCKER_MIN_API_VERSION" \
    || api_version_is_less "$DOCKER_MAX_API_VERSION" "$TESTCONTAINERS_API_VERSION"; then
  fail "TESTCONTAINERS_API_VERSION=$TESTCONTAINERS_API_VERSION fora da faixa Docker [$DOCKER_MIN_API_VERSION, $DOCKER_MAX_API_VERSION]."
fi

# Bash starts asynchronous commands with SIGINT ignored when job control is off.
# Terminate the Maven child with SIGTERM, but preserve the conventional 130
# exit code that identifies the caller's SIGINT.
if [[ "$REQUIRE_CLEAN" == "1" ]] \
    && [[ -n "$(canonical_git status --porcelain --untracked-files=all)" ]]; then
  fail "Clean source required before starting integration evidence run."
fi
qa_maven_evidence_lock_acquire integration "$RUN_ID" "$RUN_GIT_SHA" || exit $?
LOCK_ACQUIRED=1

MAVEN_ISOLATED_HOME="$(mktemp -d "${TMPDIR:-/tmp}/vanrides-maven-home.XXXXXX")" \
  || fail "Nao foi possivel criar o user.home isolado do Maven/Testcontainers."
MAVEN_REPOSITORY="$HOME/.m2/repository"
export MAVEN_USER_HOME="$HOME/.m2"

log "Removing previous reports for the Docker-required test matrix"
INTEGRATION_TESTS=""
for expectation in "${EXPECTED_TEST_CLASSES[@]}"; do
  class_name="${expectation%=*}"
  simple_class_name="${class_name##*.}"
  if [[ -n "$INTEGRATION_TESTS" ]]; then
    INTEGRATION_TESTS+=","
  fi
  INTEGRATION_TESTS+="$class_name"
  rm -f \
    "target/surefire-reports/TEST-${class_name}.xml" \
    "target/surefire-reports/${class_name}.txt"
done

log "Docker OK; running $PROFILE_SCOPE integration profile with Docker API $TESTCONTAINERS_API_VERSION (server range $DOCKER_MIN_API_VERSION..$DOCKER_MAX_API_VERSION)"
FAILURE_STAGE="maven"
maven_args=(
  -ntp
  -Dapi.version="$TESTCONTAINERS_API_VERSION"
  -Duser.home="$MAVEN_ISOLATED_HOME"
  -Dmaven.repo.local="$MAVEN_REPOSITORY"
  -Dvanrides.qa.run-id="$RUN_ID"
  -Dvanrides.qa.git-sha="$RUN_GIT_SHA"
)
if [[ "$TEST_GROUP" == unit ]]; then
  python3 scripts/qa_test_groups.py excludes > "$MAVEN_ISOLATED_HOME/postgres-excludes"
  maven_args+=(-Dsurefire.excludesFile="$MAVEN_ISOLATED_HOME/postgres-excludes")
elif [[ "$PROFILE_SCOPE" == "focused" ]]; then
  maven_args+=(-Dtest="$INTEGRATION_TESTS")
fi
maven_args+=(
  -DfailIfNoTests=true
  clean
  test
)

run_maven_and_wait ./mvnw -B "${maven_args[@]}"

log "Checking integration surefire reports"
FAILURE_STAGE="validation"
if [[ -n "$TEST_GROUP" ]]; then
  expectation_lines="$(python3 scripts/qa_test_groups.py expectations --group "$TEST_GROUP" \
    --reports-dir target/surefire-reports)" || fail "Inventario do grupo incompleto ou inesperado."
  EXPECTED_TEST_CLASSES=()
  while IFS= read -r expectation; do
    [[ -z "$expectation" ]] || EXPECTED_TEST_CLASSES+=("$expectation")
  done <<< "$expectation_lines"
fi
common_evidence_args=(
  --suite integration
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
  --metadata "scope=$PROFILE_SCOPE"
  --metadata "testcontainersApiVersion=$TESTCONTAINERS_API_VERSION"
  --metadata "dockerMinApiVersion=$DOCKER_MIN_API_VERSION"
  --metadata "dockerMaxApiVersion=$DOCKER_MAX_API_VERSION"
  --metadata "dockerEndpointTransport=$DOCKER_ENDPOINT_TRANSPORT"
  --metadata "mavenHomeIsolated=true"
  --metadata "mavenRcSkipped=true"
  --expected-user-home "$MAVEN_ISOLATED_HOME"
)
for expectation in "${EXPECTED_TEST_CLASSES[@]}"; do
  prepare_evidence_args+=(--expected "$expectation")
done
if [[ "$REQUIRE_CLEAN" == "1" ]]; then
  common_evidence_args+=(--require-clean)
fi
if [[ "$PROFILE_SCOPE" == "all-tests" ]]; then
  prepare_evidence_args+=(--require-all-reports-green)
fi

if ! python3 scripts/qa_report_evidence.py \
  "${common_evidence_args[@]}" \
  "${prepare_evidence_args[@]}"; then
  fail "Validacao da evidencia falhou para scope=$PROFILE_SCOPE, Docker API=$TESTCONTAINERS_API_VERSION e endpoint=$DOCKER_ENDPOINT_TRANSPORT. Consulte os Surefire reports acima."
fi

log "Releasing the Maven evidence lock before publishing PASS"
if qa_maven_evidence_lock_release; then
  LOCK_ACQUIRED=0
else
  fail "Nao foi possivel liberar o lock Maven; a evidencia preparada sera convertida em diagnostico de falha."
fi

if ! python3 scripts/qa_report_evidence.py \
  "${common_evidence_args[@]}" \
  --phase finalize; then
  fail "Finalizacao pos-lock da evidencia falhou; nenhum PASS elegivel foi publicado."
fi
EVIDENCE_ARCHIVED=1
if [[ -n "${VANRIDES_QA_GROUP_RECEIPT:-}" ]]; then
  [[ -n "$TEST_GROUP" ]] || fail "Recibo de grupo exige --group."
  python3 scripts/qa_test_groups.py receipt --group "$TEST_GROUP" \
    --evidence "$EVIDENCE_ROOT/$RUN_ID/manifest.json" --output "$VANRIDES_QA_GROUP_RECEIPT"
fi

log "Backend integration profile finalizado com sucesso"
