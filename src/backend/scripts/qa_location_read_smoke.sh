#!/usr/bin/env bash

set -Eeuo pipefail

umask 077
export LC_ALL=C
export LANG=C
export PYTHONUTF8=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/qa_location_read_smoke.sh"
EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"
EXPECTED_APP_SHA="${VANRIDES_EXPECTED_APP_SHA:-}"
APP_CONSUMER_PROBE="${VANRIDES_APP_CONSUMER_PROBE:-}"
POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"
EXPECTED_POSTGRES_IMAGE_ID="${VANRIDES_EXPECTED_POSTGRES_IMAGE_ID:-}"
RUN_ID="location-read-$(date -u +%Y%m%dT%H%M%SZ)-$$"
TMP_BASE="${TMPDIR:-/tmp}"
TMP_DIR="$(mktemp -d "$TMP_BASE/vanrides-location-read.XXXXXX")"
SUMMARY_FILE="$TMP_DIR/summary.env"
REQUESTED_CONTAINER_NAME="${VANRIDES_CONTAINER_NAME:-}"
REQUESTED_CONTAINER_RUN_TOKEN="${VANRIDES_CONTAINER_RUN_TOKEN:-}"
CONTAINER_NAME="${REQUESTED_CONTAINER_NAME:-vanrides-$(printf '%s' "$RUN_ID" \
  | tr '[:upper:]' '[:lower:]')}"
APP_PID=""
CONTAINER_STARTED=0
CONTAINER_ATTEMPTED=0
CONTAINER_RUN_TOKEN="$REQUESTED_CONTAINER_RUN_TOKEN"
CONTAINER_LABEL_KEY="com.vanrides.qa.location-read.run-token"
MAIN_RESULT=""
FAILED_CHECK=""

for log_file in \
  build.log application.log backend-gate.log fixture.stdout fixture.stderr \
  app-positive.stdout app-positive.stderr app-negative.stdout app-negative.stderr; do
  : >"$TMP_DIR/$log_file"
done

record() {
  local key="$1"
  local value="$2"
  printf '%s=%s\n' "$key" "$value" >>"$SUMMARY_FILE"
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

fail() {
  if [[ -z "$FAILED_CHECK" ]]; then
    FAILED_CHECK="$1"
  fi
  exit 1
}

assert_equal() {
  local actual="$1"
  local expected="$2"
  local label="$3"
  [[ "$actual" == "$expected" ]] || fail "$label"
}

git_safe() {
  command git --no-replace-objects -c core.fsmonitor=false "$@"
}

canonical_path() {
  python3 - "$1" <<'PY'
from pathlib import Path
import sys

print(Path(sys.argv[1]).resolve(strict=True))
PY
}

assert_no_hidden_index_flags() {
  local repo_root="$1"
  local label="$2"
  local hidden_entries
  hidden_entries="$(git_safe -C "$repo_root" ls-files -v -- \
    | awk 'substr($0, 1, 1) == "S" || substr($0, 1, 1) ~ /[a-z]/ { print }')"
  [[ -z "$hidden_entries" ]] || fail "$label"
}

assert_app_runtime_unchanged() {
  local phase="$1"
  local boundary="$2"
  kill -0 "$APP_PID" >/dev/null 2>&1 \
    || fail "app_process_not_alive_${phase}_${boundary}"
  assert_equal "$(sha256_file "$JAR_PATH")" "$JAR_SHA256" \
    "jar_changed_${phase}_${boundary}"
}

append_log_hashes() {
  record build_log_sha256 "$(sha256_file "$TMP_DIR/build.log")"
  record application_log_sha256 "$(sha256_file "$TMP_DIR/application.log")"
  record backend_gate_log_sha256 "$(sha256_file "$TMP_DIR/backend-gate.log")"
  record fixture_stdout_sha256 "$(sha256_file "$TMP_DIR/fixture.stdout")"
  record fixture_stderr_sha256 "$(sha256_file "$TMP_DIR/fixture.stderr")"
  record app_consumer_positive_stdout_sha256 "$(sha256_file "$TMP_DIR/app-positive.stdout")"
  record app_consumer_positive_stderr_sha256 "$(sha256_file "$TMP_DIR/app-positive.stderr")"
  record app_consumer_negative_stdout_sha256 "$(sha256_file "$TMP_DIR/app-negative.stdout")"
  record app_consumer_negative_stderr_sha256 "$(sha256_file "$TMP_DIR/app-negative.stderr")"
}

cleanup() {
  local original_status=$?
  local cleanup_status="PASS"
  local final_status="$original_status"
  local summary_output=""

  trap - EXIT INT TERM
  set +e

  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    kill "$APP_PID" >/dev/null 2>&1
    for _ in $(seq 1 20); do
      kill -0 "$APP_PID" >/dev/null 2>&1 || break
      sleep 0.25
    done
    if kill -0 "$APP_PID" >/dev/null 2>&1; then
      kill -KILL "$APP_PID" >/dev/null 2>&1
    fi
    wait "$APP_PID" >/dev/null 2>&1
  fi
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    cleanup_status="FAIL"
  fi

  if [[ "$CONTAINER_ATTEMPTED" == "1" ]]; then
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
      cleanup_status="FAIL"
    else
      if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        local container_id container_run_token
        container_id="$(docker inspect --format '{{.Id}}' \
          "$CONTAINER_NAME" 2>/dev/null)"
        container_run_token="$(docker inspect --format \
          '{{ index .Config.Labels "com.vanrides.qa.location-read.run-token" }}' \
          "$container_id" 2>/dev/null)"
        if [[ "$container_id" =~ ^[0-9a-f]{64}$ && -n "$CONTAINER_RUN_TOKEN" && \
          "$container_run_token" == "$CONTAINER_RUN_TOKEN" ]]; then
          docker rm --force "$container_id" >/dev/null 2>&1 \
            || cleanup_status="FAIL"
        else
          cleanup_status="FAIL"
        fi
        if [[ -n "$container_id" ]] && \
          docker inspect "$container_id" >/dev/null 2>&1; then
          cleanup_status="FAIL"
        fi
      fi
      # A third party may race to reuse the name. Never remove that replacement;
      # surface the conflict and let the coordinator fail closed.
      if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        cleanup_status="FAIL"
      fi
    fi
  fi

  append_log_hashes
  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == "PASS" && "$cleanup_status" == "PASS" ]]; then
    record smoke_result PASS
  else
    record smoke_result FAIL
    record failed_check "${FAILED_CHECK:-unexpected_exit}"
    final_status="${original_status:-1}"
    [[ "$final_status" -ne 0 ]] || final_status=1
  fi

  summary_output="$(cat "$SUMMARY_FILE")"
  case "$TMP_DIR" in
    "$TMP_BASE"/vanrides-location-read.*)
      rm -rf "$TMP_DIR" || cleanup_status="FAIL"
      ;;
    *)
      cleanup_status="FAIL"
      ;;
  esac
  [[ ! -e "$TMP_DIR" ]] || cleanup_status="FAIL"

  if [[ "$cleanup_status" != "PASS" ]]; then
    final_status=1
  fi

  printf '%s\n' "$summary_output"
  printf 'cleanup=%s\n' "$cleanup_status"
  exit "$final_status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

json_get() {
  local file="$1"
  local path="$2"
  python3 - "$file" "$path" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    value = json.load(handle)
for part in sys.argv[2].split("."):
    value = value[int(part)] if part.isdigit() else value[part]
if isinstance(value, bool):
    print("true" if value else "false")
elif value is None:
    print("")
else:
    print(value)
PY
}

assert_location_body() {
  local file="$1"
  python3 - "$file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
required = {"latitude", "longitude", "updatedAt", "freshness"}
missing = sorted(required.difference(payload))
if missing:
    raise SystemExit("missing location fields")
if not isinstance(payload["latitude"], (int, float)) or not isinstance(payload["longitude"], (int, float)):
    raise SystemExit("location coordinates are not numeric")
PY
}

run_backend_gate() (
  local variable
  # The smoke resolves and exports its validated local Docker endpoint for the
  # disposable PostgreSQL lifecycle. The canonical gate must resolve Docker
  # independently and reject caller overrides, so do not leak that runner-owned
  # value across this seam.
  unset DOCKER_HOST

  # The coordinator uses Maven variables only to isolate the preliminary JAR
  # build. The canonical gate must own its Maven runtime and reject/define its
  # own inputs, so do not leak build-only overrides across this seam.
  while IFS= read -r variable; do
    case "$variable" in
      MAVEN_*|MVNW_*) unset "$variable" ;;
    esac
  done < <(builtin compgen -e)

  VANRIDES_BACKEND_BASE_URL="$BASE_URL" ./scripts/qa_gate_card.sh
)

not_found_fingerprint() {
  local file="$1"
  python3 - "$file" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
expected = {
    "code": "NOT_FOUND",
    "message": "Motorista não encontrado na empresa atual.",
    "email": None,
}
if payload != expected:
    raise SystemExit("unexpected not-found body contract")
print(json.dumps(payload, ensure_ascii=False, sort_keys=True, separators=(",", ":")))
PY
}

content_type() {
  local headers_file="$1"
  grep -i '^content-type:' "$headers_file" | head -n 1 | cut -d: -f2- \
    | sed 's/^[[:space:]]*//; s/[[:space:]\r]*$//'
}

free_port() {
  python3 <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

http_request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local payload="${4:-}"
  local email="${5:-}"
  local password="${6:-}"
  local -a args=(
    --disable --noproxy '*' --silent --show-error --connect-timeout 5 --max-time 30
    --request "$method" --output "$output_file"
    --dump-header "${output_file}.headers" --write-out '%{http_code}'
    --header 'Content-Type: application/json'
  )

  if [[ -n "$email" ]]; then
    args+=(--user "${email}:${password}")
  fi
  if [[ -n "$payload" ]]; then
    args+=(--data "$payload")
  fi

  curl "${args[@]}" "${BASE_URL}${path}"
}

db_scalar() {
  local sql="$1"
  docker exec --env "PGPASSWORD=${DB_PASSWORD}" "$CONTAINER_NAME" \
    psql --username vanrides --dbname vanrides --tuples-only --no-align --command "$sql"
}

run_app_probe() {
  local phase="$1"
  local stdout_file="$TMP_DIR/app-${phase}.stdout"
  local stderr_file="$TMP_DIR/app-${phase}.stderr"
  local expected_marker=""

  assert_app_runtime_unchanged "$phase" before
  local probe_status=0
  if [[ "$phase" == "positive" ]]; then
    set +e
    env -i \
      PATH="$PATH" HOME="${HOME:-$TMP_DIR/home}" TMPDIR="$TMP_BASE" \
      LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8 \
      VANRIDES_API_BASE_URL="$BASE_URL" \
      VANRIDES_EXPECTED_APP_SHA="$EXPECTED_APP_SHA" \
      VANRIDES_CONSUMER_PHASE=positive \
      VANRIDES_CONSUMER_OK_EMAIL="$passenger_main_email" \
      VANRIDES_CONSUMER_OK_PASSWORD="$passenger_password" \
      VANRIDES_CONSUMER_OK_DRIVER_ID="$driver_1_id" \
      VANRIDES_CONSUMER_EMPTY_EMAIL="$PASSENGER3_EMAIL" \
      VANRIDES_CONSUMER_EMPTY_PASSWORD="$passenger_password" \
      VANRIDES_CONSUMER_EMPTY_DRIVER_ID="$DRIVER3_ID" \
      "$APP_CONSUMER_PROBE" >"$stdout_file" 2>"$stderr_file"
    probe_status=$?
    set -e
    assert_app_runtime_unchanged "$phase" after
    [[ "$probe_status" -eq 0 ]] || fail app_consumer_positive
    expected_marker="TRIP_LIVE_LOCATION_REAL_BACKEND_PASS phase=positive http_200=PASS http_204=PASS app_sha=${EXPECTED_APP_SHA} attestation_scope=consumer_only backend_binding=coordinator_required"
    grep -Fxq "$expected_marker" "$stdout_file" || fail app_consumer_positive_marker
    printf '%s\n' "$expected_marker" >>"$SUMMARY_FILE"
    record app_consumer_positive_http_200 PASS
    record app_consumer_positive_http_204 PASS
  else
    set +e
    env -i \
      PATH="$PATH" HOME="${HOME:-$TMP_DIR/home}" TMPDIR="$TMP_BASE" \
      LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8 \
      VANRIDES_API_BASE_URL="$BASE_URL" \
      VANRIDES_EXPECTED_APP_SHA="$EXPECTED_APP_SHA" \
      VANRIDES_CONSUMER_PHASE=negative \
      VANRIDES_CONSUMER_DENIED_EMAIL="$LEGACY_PASSENGER_EMAIL" \
      VANRIDES_CONSUMER_DENIED_PASSWORD="$passenger_password" \
      VANRIDES_CONSUMER_DENIED_DRIVER_ID="$driver_1_id" \
      "$APP_CONSUMER_PROBE" >"$stdout_file" 2>"$stderr_file"
    probe_status=$?
    set -e
    assert_app_runtime_unchanged "$phase" after
    [[ "$probe_status" -eq 0 ]] || fail app_consumer_negative
    expected_marker="TRIP_LIVE_LOCATION_REAL_BACKEND_PASS phase=negative http_404=PASS generic_exception=PASS app_sha=${EXPECTED_APP_SHA} attestation_scope=consumer_only backend_binding=coordinator_required"
    grep -Fxq "$expected_marker" "$stdout_file" || fail app_consumer_negative_marker
    printf '%s\n' "$expected_marker" >>"$SUMMARY_FILE"
    record app_consumer_negative_http_404 PASS
    record app_consumer_negative_generic_exception PASS
  fi

  record "app_consumer_${phase}" PASS
}

for command in awk basename cmp cp curl date docker find git grep head java mkdir mktemp openssl \
  python3 sed seq shasum tr; do
  command -v "$command" >/dev/null 2>&1 || fail "missing_command_${command}"
done

unset HTTP_PROXY HTTPS_PROXY ALL_PROXY FTP_PROXY \
  http_proxy https_proxy all_proxy ftp_proxy
export NO_PROXY='*'
export no_proxy='*'
export CURL_HOME="$TMP_DIR/curl-home"
export XDG_CONFIG_HOME="$TMP_DIR/xdg-config"
mkdir -p "$CURL_HOME" "$XDG_CONFIG_HOME"
: >"$CURL_HOME/.curlrc"
: >"$XDG_CONFIG_HOME/curlrc"
record http_transport_scope loopback_no_proxy

[[ -z "${DOCKER_HOST:-}" ]] || fail docker_host_override_not_allowed
[[ -z "${DOCKER_CONTEXT:-}" ]] || fail docker_context_override_not_allowed
DOCKER_CONTEXT_NAME="$(docker context show 2>/dev/null)" \
  || fail docker_context_unavailable
[[ -n "$DOCKER_CONTEXT_NAME" ]] || fail docker_context_empty
DOCKER_ENDPOINT="$(docker context inspect "$DOCKER_CONTEXT_NAME" \
  --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" \
  || fail docker_endpoint_unavailable
case "$DOCKER_ENDPOINT" in
  unix:///*)
    DOCKER_ENDPOINT_TRANSPORT=unix
    ;;
  npipe:////./pipe/*)
    DOCKER_ENDPOINT_TRANSPORT=npipe
    ;;
  *)
    fail docker_endpoint_not_local
    ;;
esac
export DOCKER_HOST="$DOCKER_ENDPOINT"
unset DOCKER_CONTEXT
docker info >/dev/null 2>&1 || fail docker_daemon_unavailable
record docker_endpoint_transport "$DOCKER_ENDPOINT_TRANSPORT"

while IFS='=' read -r variable _; do
  case "$variable" in
    GIT_*) unset "$variable" ;;
  esac
done < <(env)
export GIT_NO_REPLACE_OBJECTS=1

[[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] || fail expected_backend_sha_required
[[ -n "$APP_CONSUMER_PROBE" ]] || fail app_consumer_probe_required
[[ "$EXPECTED_APP_SHA" =~ ^[0-9a-f]{40}$ ]] || fail expected_app_sha_required
[[ "$POSTGRES_IMAGE" =~ ^[A-Za-z0-9._/@:+-]+$ ]] || fail invalid_postgres_image_ref
if [[ -n "$REQUESTED_CONTAINER_NAME" || -n "$REQUESTED_CONTAINER_RUN_TOKEN" ]]; then
  [[ -n "$REQUESTED_CONTAINER_NAME" && -n "$REQUESTED_CONTAINER_RUN_TOKEN" ]] \
    || fail coordinator_container_ownership_pair_required
  [[ "$REQUESTED_CONTAINER_NAME" =~ ^vanrides-[a-z0-9][a-z0-9-]{7,62}$ ]] \
    || fail invalid_coordinator_container_name
  [[ "$REQUESTED_CONTAINER_RUN_TOKEN" =~ ^[0-9a-f]{32}$ ]] \
    || fail invalid_coordinator_container_run_token
fi
if [[ -n "$EXPECTED_POSTGRES_IMAGE_ID" ]]; then
  [[ "$EXPECTED_POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || fail invalid_expected_postgres_image_id
fi
[[ "$APP_CONSUMER_PROBE" == /* && -f "$APP_CONSUMER_PROBE" && -x "$APP_CONSUMER_PROBE" ]] \
  || fail invalid_app_consumer_probe

APP_CONSUMER_PROBE_CANONICAL="$(canonical_path "$APP_CONSUMER_PROBE")" \
  || fail app_consumer_probe_not_resolvable
assert_equal "$APP_CONSUMER_PROBE" "$APP_CONSUMER_PROBE_CANONICAL" \
  app_consumer_probe_not_canonical
APP_ROOT="$(canonical_path "$(dirname "$APP_CONSUMER_PROBE")/..")" \
  || fail app_root_not_resolvable
assert_equal "$APP_CONSUMER_PROBE" \
  "$APP_ROOT/scripts/qa_trip_live_location_real_backend.sh" \
  app_consumer_probe_path
APP_GIT_ROOT="$(git_safe -C "$APP_ROOT" rev-parse --show-toplevel)" \
  || fail app_git_root_missing
APP_GIT_ROOT_CANONICAL="$(canonical_path "$APP_GIT_ROOT")" \
  || fail app_git_root_not_resolvable
[[ "$APP_ROOT" == "$APP_GIT_ROOT_CANONICAL" || "$APP_ROOT" == "$APP_GIT_ROOT_CANONICAL/src/aplicativo-motorista" ]] \
  || fail app_git_root_mismatch
APP_GIT_PREFIX="$(git_safe -C "$APP_ROOT" rev-parse --show-prefix)"
APP_SHA="$(git_safe -C "$APP_ROOT" rev-parse HEAD)"
APP_TREE="$(git_safe -C "$APP_ROOT" rev-parse 'HEAD^{tree}')"
assert_equal "$APP_SHA" "$EXPECTED_APP_SHA" app_sha
assert_no_hidden_index_flags "$APP_ROOT" app_index_flags_hidden_before
[[ -z "$(git_safe -C "$APP_ROOT" status --porcelain --untracked-files=all)" ]] \
  || fail app_worktree_not_clean
APP_CONSUMER_PROBE_GIT_BLOB="$(git_safe -C "$APP_ROOT" rev-parse \
  "${EXPECTED_APP_SHA}:${APP_GIT_PREFIX}scripts/qa_trip_live_location_real_backend.sh")" \
  || fail app_consumer_probe_not_versioned
APP_CONSUMER_PROBE_WORKTREE_BLOB="$(git_safe -C "$APP_ROOT" hash-object \
  --no-filters "$APP_CONSUMER_PROBE")"
assert_equal "$APP_CONSUMER_PROBE_WORKTREE_BLOB" \
  "$APP_CONSUMER_PROBE_GIT_BLOB" app_consumer_probe_blob_mismatch
APP_CONSUMER_PROBE_RAW_SHA256="$(sha256_file "$APP_CONSUMER_PROBE")"

record app_expected_sha "$EXPECTED_APP_SHA"
record app_tree "$APP_TREE"
record app_consumer_probe_git_blob "$APP_CONSUMER_PROBE_GIT_BLOB"
record app_consumer_probe_sha256 "$APP_CONSUMER_PROBE_RAW_SHA256"
record app_consumer_probe_raw_sha256 "$APP_CONSUMER_PROBE_RAW_SHA256"
record app_attestation_scope consumer_only
record app_backend_binding coordinator_required

cd "$ROOT_DIR"
REPOSITORY_DIR="$(cd "$(git_safe rev-parse --show-toplevel)" && pwd -P)"
MODULE_DIR="$(cd "$ROOT_DIR" && pwd -P)"
[[ "$MODULE_DIR" == "$REPOSITORY_DIR" || "$MODULE_DIR" == "$REPOSITORY_DIR/src/backend" ]] \
  || fail backend_repo_root
MODULE_GIT_PREFIX="$(git_safe rev-parse --show-prefix)"
BACKEND_SHA="$(git_safe rev-parse HEAD)"
BACKEND_TREE="$(git_safe rev-parse 'HEAD^{tree}')"
assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha
assert_no_hidden_index_flags "$ROOT_DIR" backend_index_flags_hidden_before
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] || fail backend_worktree_not_clean

RUNNER_GIT_BLOB="$(git_safe rev-parse "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_location_read_smoke.sh")" \
  || fail runner_not_versioned
RUNNER_WORKTREE_BLOB="$(git_safe hash-object --no-filters "$SCRIPT_PATH")"
assert_equal "$RUNNER_WORKTREE_BLOB" "$RUNNER_GIT_BLOB" runner_blob_mismatch

record run_id "$RUN_ID"
record backend_sha "$BACKEND_SHA"
record backend_tree "$BACKEND_TREE"
record runner_git_blob "$RUNNER_GIT_BLOB"
record runner_sha256 "$(sha256_file "$SCRIPT_PATH")"

if ! ./mvnw -q clean package -DskipTests >"$TMP_DIR/build.log" 2>&1; then
  fail backend_build
fi

JAR_PATH=""
JAR_COUNT=0
while IFS= read -r candidate; do
  case "$candidate" in
    *.jar.original) continue ;;
  esac
  JAR_PATH="$candidate"
  JAR_COUNT=$((JAR_COUNT + 1))
done < <(find "$ROOT_DIR/target" -maxdepth 1 -type f -name '*.jar' -print)
assert_equal "$JAR_COUNT" 1 jar_candidate_count
[[ -n "$JAR_PATH" && -f "$JAR_PATH" ]] || fail jar_not_found
BUILT_JAR_PATH="$JAR_PATH"
BUILT_JAR_SHA256="$(sha256_file "$BUILT_JAR_PATH")"
RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"
cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH" || fail runtime_jar_copy
assert_equal "$(sha256_file "$RUNTIME_JAR_PATH")" "$BUILT_JAR_SHA256" \
  runtime_jar_copy_hash
JAR_PATH="$RUNTIME_JAR_PATH"
JAR_SHA256="$BUILT_JAR_SHA256"
record jar_file "$(basename "$BUILT_JAR_PATH")"
record jar_sha256 "$JAR_SHA256"

DB_PASSWORD="$(openssl rand -hex 24)"
ADMIN_PASSWORD="$(openssl rand -hex 16)"
DRIVER_PASSWORD="$(openssl rand -hex 16)"
PASSENGER_PASSWORD="$(openssl rand -hex 16)"
GUARDIAN_PASSWORD="$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 48)"
APP_PORT="$(free_port)"

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  fail postgres_container_name_collision
fi
if [[ -z "$CONTAINER_RUN_TOKEN" ]]; then
  CONTAINER_RUN_TOKEN="$(openssl rand -hex 16)"
fi
CONTAINER_ATTEMPTED=1
if ! docker run --detach --pull=missing \
  --name "$CONTAINER_NAME" \
  --label "${CONTAINER_LABEL_KEY}=${CONTAINER_RUN_TOKEN}" \
  --env POSTGRES_DB=vanrides \
  --env POSTGRES_USER=vanrides \
  --env "POSTGRES_PASSWORD=${DB_PASSWORD}" \
  --publish '127.0.0.1::5432' \
  "$POSTGRES_IMAGE" >"$TMP_DIR/container.id" 2>"$TMP_DIR/docker-run.stderr"; then
  fail postgres_container_start
fi
CONTAINER_STARTED=1

POSTGRES_IMAGE_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER_NAME")"
if [[ -n "$EXPECTED_POSTGRES_IMAGE_ID" ]]; then
  assert_equal "$POSTGRES_IMAGE_ID" "$EXPECTED_POSTGRES_IMAGE_ID" postgres_image_id
fi
POSTGRES_IMAGE_REPO_DIGEST="$(docker image inspect "$POSTGRES_IMAGE_ID" \
  --format '{{range .RepoDigests}}{{println .}}{{end}}' 2>/dev/null | head -n 1)"
POSTGRES_IMAGE_REPO_DIGEST="${POSTGRES_IMAGE_REPO_DIGEST:-not_available}"
record postgres_image_ref "$POSTGRES_IMAGE"
record postgres_image_id "$POSTGRES_IMAGE_ID"
record postgres_image_repo_digest "$POSTGRES_IMAGE_REPO_DIGEST"

DB_PORT="$(docker port "$CONTAINER_NAME" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
[[ -n "$DB_PORT" ]] || fail postgres_port_not_bound
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_NAME" pg_isready --username vanrides --dbname vanrides \
      >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$CONTAINER_NAME" pg_isready --username vanrides --dbname vanrides \
  >/dev/null 2>&1 || fail postgres_not_ready

BASE_URL="http://127.0.0.1:${APP_PORT}"
JAVA_BIN="$(command -v java)"
mkdir -p "$TMP_DIR/home"
env -i \
  HOME="$TMP_DIR/home" TMPDIR="$TMP_DIR" LC_ALL=en_US.UTF-8 LANG=en_US.UTF-8 \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/vanrides" \
  SPRING_DATASOURCE_USERNAME=vanrides \
  SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  SPRING_PROFILES_ACTIVE=local \
  SPRING_JPA_SHOW_SQL=false \
  SERVER_PORT="$APP_PORT" \
  VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev \
  VANRIDES_EMAIL_PROVIDER=log \
  VANRIDES_ROUTING_PROVIDER=static \
  VANRIDES_PUSH_PROVIDER=none \
  VANRIDES_MAPS_PLATFORM_ENABLED=false \
  VANRIDES_AUTH_BASIC_ENABLED=true \
  VANRIDES_JWT_SECRET="$JWT_SECRET" \
  VANRIDES_RATE_LIMIT_ENABLED=false \
  VANRIDES_OPERATIONS_TIME_ZONE=America/Sao_Paulo \
  "$JAVA_BIN" -jar "$JAR_PATH" >"$TMP_DIR/application.log" 2>&1 &
APP_PID=$!

READY_CODE=000
for _ in $(seq 1 120); do
  kill -0 "$APP_PID" >/dev/null 2>&1 || fail application_exited_before_readiness
  READY_CODE="$(curl --disable --noproxy '*' --silent --output /dev/null \
    --write-out '%{http_code}' --max-time 2 \
    "${BASE_URL}/motoristas" || true)"
  [[ "$READY_CODE" == "200" ]] && break
  sleep 1
done
assert_equal "$READY_CODE" 200 application_not_ready
grep -q "Started VanridesApplication" "$TMP_DIR/application.log" \
  || fail spring_started_marker_missing

MIGRATION_VERSION="$(db_scalar \
  'SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;')"
assert_equal "$MIGRATION_VERSION" 24 flyway_latest_version
record flyway_latest_version "$MIGRATION_VERSION"

if ! run_backend_gate >"$TMP_DIR/backend-gate.log" 2>&1; then
  fail backend_qa_gate_card
fi
record backend_qa_gate_card PASS

TODAY="$(TZ=America/Sao_Paulo date +%F)"
if ! BASE_URL="$BASE_URL" TODAY="$TODAY" LAB_PREFIX=vrsec001 \
  ADMIN_PASSWORD="$ADMIN_PASSWORD" DRIVER_PASSWORD="$DRIVER_PASSWORD" \
  PASSENGER_PASSWORD="$PASSENGER_PASSWORD" GUARDIAN_PASSWORD="$GUARDIAN_PASSWORD" \
  ./scripts/seed_operational_service_day_fixture.sh \
  >"$TMP_DIR/fixture.stdout" 2>"$TMP_DIR/fixture.stderr"; then
  fail operational_fixture
fi

awk -F= '/^(base_url|fixture_date|company_id|admin_id|company_name|company_slug|admin_email|admin_password|driver_password|passenger_password|guardian_password|driver_1_id|driver_1_email|passenger_main_id|passenger_main_email|guardian_email|trip_morning_status|trip_morning_snapshot_operacional|passenger_services_count|passenger_day_trip_count|support_day_trip_count|guardian_day_trip_count|driver_1_day_trip_count|driver_2_day_trip_count)=/ {print}' \
  "$TMP_DIR/fixture.stdout" >"$TMP_DIR/fixture.env"
# shellcheck disable=SC1091
source "$TMP_DIR/fixture.env"

assert_equal "$trip_morning_status" IN_PROGRESS fixture_trip_status
assert_equal "$trip_morning_snapshot_operacional" true fixture_trip_snapshot
assert_equal "$passenger_services_count" 3 fixture_passenger_services
assert_equal "$passenger_day_trip_count" 3 fixture_passenger_trips
assert_equal "$support_day_trip_count" 1 fixture_support_trips
assert_equal "$guardian_day_trip_count" 3 fixture_guardian_trips
assert_equal "$driver_1_day_trip_count" 1 fixture_driver_1_trips
assert_equal "$driver_2_day_trip_count" 2 fixture_driver_2_trips
record fixture_contract PASS

STATUS="$(http_request GET "/api/localizacao/${driver_1_id}" \
  "$TMP_DIR/passenger-authorized.json" "" "$passenger_main_email" "$passenger_password")"
assert_equal "$STATUS" 200 passenger_authorized_location_status
assert_location_body "$TMP_DIR/passenger-authorized.json" || fail passenger_authorized_location_body
STATUS="$(http_request GET "/api/localizacao/${driver_1_id}" \
  "$TMP_DIR/guardian-authorized.json" "" "$guardian_email" "$guardian_password")"
assert_equal "$STATUS" 200 guardian_authorized_location_status
assert_location_body "$TMP_DIR/guardian-authorized.json" || fail guardian_authorized_location_body
record authorized_passenger_location 200
record authorized_guardian_location 200

COMPANY_COUNT_BEFORE_NEGATIVE="$(db_scalar 'SELECT COUNT(*) FROM company;')"
ADMIN_COUNT_BEFORE_NEGATIVE="$(db_scalar 'SELECT COUNT(*) FROM company_admin;')"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/bootstrap-empty.json" '{}')"
assert_equal "$STATUS" 400 bootstrap_empty_status
SLUG_ONLY_PAYLOAD="{\"companySlug\":\"legacy-${RUN_ID}\",\"nome\":\"Legacy Admin\",\"email\":\"legacy.${RUN_ID}@vanrides.dev\",\"telefone\":\"51990000000\",\"senha\":\"${ADMIN_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/bootstrap-slug-only.json" "$SLUG_ONLY_PAYLOAD")"
assert_equal "$STATUS" 400 bootstrap_slug_only_status
COMPANY_COUNT_AFTER_INVALID="$(db_scalar 'SELECT COUNT(*) FROM company;')"
ADMIN_COUNT_AFTER_INVALID="$(db_scalar 'SELECT COUNT(*) FROM company_admin;')"
assert_equal "$COMPANY_COUNT_AFTER_INVALID" "$COMPANY_COUNT_BEFORE_NEGATIVE" invalid_bootstrap_company_mutation
assert_equal "$ADMIN_COUNT_AFTER_INVALID" "$ADMIN_COUNT_BEFORE_NEGATIVE" invalid_bootstrap_admin_mutation
DUPLICATE_PAYLOAD="{\"companyName\":\"${company_name}\",\"nome\":\"Duplicate Admin\",\"email\":\"duplicate.${RUN_ID}@vanrides.dev\",\"telefone\":\"51990000001\",\"senha\":\"${ADMIN_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/bootstrap-duplicate.json" "$DUPLICATE_PAYLOAD")"
assert_equal "$STATUS" 409 bootstrap_duplicate_company_status
COMPANY_COUNT_AFTER_DUPLICATE="$(db_scalar 'SELECT COUNT(*) FROM company;')"
ADMIN_COUNT_AFTER_DUPLICATE="$(db_scalar 'SELECT COUNT(*) FROM company_admin;')"
assert_equal "$COMPANY_COUNT_AFTER_DUPLICATE" "$COMPANY_COUNT_BEFORE_NEGATIVE" duplicate_bootstrap_company_mutation
assert_equal "$ADMIN_COUNT_AFTER_DUPLICATE" "$ADMIN_COUNT_BEFORE_NEGATIVE" duplicate_bootstrap_admin_mutation
record bootstrap_empty 400:no-mutation
record bootstrap_slug_only 400:no-mutation
record bootstrap_duplicate_company 409:no-mutation

DRIVER3_EMAIL="driver.no.location.${RUN_ID}@vanrides.dev"
DRIVER3_PAYLOAD="{\"nome\":\"Driver No Location\",\"email\":\"${DRIVER3_EMAIL}\",\"telefone\":\"51990000002\",\"senha\":\"${DRIVER_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/motoristas "$TMP_DIR/driver3.json" "$DRIVER3_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_driver_without_location_status
DRIVER3_ID="$(json_get "$TMP_DIR/driver3.json" id)"
PASSENGER3_EMAIL="passenger.no.location.${RUN_ID}@vanrides.dev"
PASSENGER3_PAYLOAD="{\"nome\":\"Passenger Operational No Location\",\"email\":\"${PASSENGER3_EMAIL}\",\"telefone\":\"51990000003\",\"endereco\":\"Ponto sem localização\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false,\"motoristaId\":${DRIVER3_ID},\"latitude\":-29.57,\"longitude\":-50.79}"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/passenger3.json" "$PASSENGER3_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_operational_passenger_status
PASSENGER3_ID="$(json_get "$TMP_DIR/passenger3.json" id)"
TODAY_DAY="$(python3 - "$TODAY" <<'PY'
from datetime import datetime
import sys
print(datetime.strptime(sys.argv[1], "%Y-%m-%d").strftime("%A").upper())
PY
)"
SERVICE3_PAYLOAD="{\"nome\":\"Service No Location ${RUN_ID}\",\"motoristaPadraoId\":${DRIVER3_ID},\"tipoOperacao\":\"IDA\",\"politicaPresenca\":\"EXIGE_CONFIRMACAO\",\"geracaoAutomaticaAtiva\":true,\"diasOperacao\":[\"${TODAY_DAY}\"],\"horarioPartida\":\"08:00:00\",\"destinoFinalNome\":\"Destino smoke\",\"destinoFinalEndereco\":\"Destino smoke, Igrejinha - RS\",\"destinoFinalLatitude\":-29.571,\"destinoFinalLongitude\":-50.781,\"capacidadePlanejada\":1,\"passageiroIds\":[${PASSENGER3_ID}]}"
STATUS="$(http_request POST /company-admins/servicos-recorrentes "$TMP_DIR/service3.json" "$SERVICE3_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_operational_service_status
SERVICE3_ID="$(json_get "$TMP_DIR/service3.json" id)"
PRESENCE3_PAYLOAD="{\"servicoId\":${SERVICE3_ID},\"passageiroId\":${PASSENGER3_ID},\"dataOperacao\":\"${TODAY}\",\"status\":\"CONFIRMED\"}"
STATUS="$(http_request POST /presencas-servico "$TMP_DIR/presence3.json" "$PRESENCE3_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_operational_presence_status
TRIP3_PAYLOAD="{\"servicoId\":${SERVICE3_ID},\"motoristaId\":${DRIVER3_ID},\"dataOperacao\":\"${TODAY}\"}"
STATUS="$(http_request POST /company-admins/viagens "$TMP_DIR/trip3.json" "$TRIP3_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_operational_trip_status
TRIP3_ID="$(json_get "$TMP_DIR/trip3.json" id)"
STATUS="$(http_request GET "/api/localizacao/${DRIVER3_ID}" "$TMP_DIR/authorized-no-location.json" "" "$PASSENGER3_EMAIL" "$passenger_password")"
assert_equal "$STATUS" 204 authorized_without_location_status
[[ ! -s "$TMP_DIR/authorized-no-location.json" ]] || fail authorized_without_location_body
record authorized_without_location 204
run_app_probe positive

STATUS="$(http_request POST "/company-admins/viagens/${TRIP3_ID}/cancelar" "$TMP_DIR/trip3-canceled.json" "" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 200 cancel_operational_trip_status
assert_equal "$(json_get "$TMP_DIR/trip3-canceled.json" status)" CANCELED canceled_trip_body_status
STATUS="$(http_request GET "/api/localizacao/${DRIVER3_ID}" "$TMP_DIR/canceled-trip-not-found.json" "" "$PASSENGER3_EMAIL" "$passenger_password")"
assert_equal "$STATUS" 404 canceled_trip_location_status

LEGACY_PASSENGER_EMAIL="passenger.legacy.${RUN_ID}@vanrides.dev"
LEGACY_PASSENGER_PAYLOAD="{\"nome\":\"Passenger Legacy Only\",\"email\":\"${LEGACY_PASSENGER_EMAIL}\",\"telefone\":\"51990000004\",\"endereco\":\"Ponto legado\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false,\"motoristaId\":${driver_1_id},\"latitude\":-29.57,\"longitude\":-50.79}"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/legacy-passenger.json" "$LEGACY_PASSENGER_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_legacy_only_passenger_status
LEGACY_PASSENGER_ID="$(json_get "$TMP_DIR/legacy-passenger.json" id)"
assert_equal "$(json_get "$TMP_DIR/legacy-passenger.json" motorista.id)" "$driver_1_id" legacy_passenger_driver_link
record legacy_link_persisted PASS
STATUS="$(http_request GET "/api/localizacao/${driver_1_id}" "$TMP_DIR/legacy-link-not-found.json" "" "$LEGACY_PASSENGER_EMAIL" "$passenger_password")"
assert_equal "$STATUS" 404 legacy_only_location_status

GUARDIAN2_PASSWORD="$(openssl rand -hex 16)"
GUARDIAN2_EMAIL="guardian.legacy.${RUN_ID}@vanrides.dev"
GUARDIAN2_PAYLOAD="{\"nome\":\"Guardian Legacy Only\",\"email\":\"${GUARDIAN2_EMAIL}\",\"telefone\":\"51990000007\",\"senha\":\"${GUARDIAN2_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/responsaveis "$TMP_DIR/guardian2.json" "$GUARDIAN2_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 create_legacy_guardian_status
GUARDIAN2_ID="$(json_get "$TMP_DIR/guardian2.json" id)"
GUARDIAN2_LINK_PAYLOAD="{\"passageiroId\":${LEGACY_PASSENGER_ID}}"
STATUS="$(http_request POST "/company-admins/responsaveis/${GUARDIAN2_ID}/passageiros" "$TMP_DIR/guardian2-link.json" "$GUARDIAN2_LINK_PAYLOAD" "$admin_email" "$admin_password")"
assert_equal "$STATUS" 201 link_legacy_guardian_status
STATUS="$(http_request GET "/api/localizacao/${driver_1_id}" "$TMP_DIR/guardian-legacy-not-found.json" "" "$GUARDIAN2_EMAIL" "$GUARDIAN2_PASSWORD")"
assert_equal "$STATUS" 404 guardian_legacy_only_location_status

ADMIN2_PASSWORD="$(openssl rand -hex 16)"
ADMIN2_EMAIL="admin.cross.${RUN_ID}@vanrides.dev"
COMPANY2_NAME="Cross Tenant ${RUN_ID}"
COMPANY2_PAYLOAD="{\"companyName\":\"${COMPANY2_NAME}\",\"nome\":\"Cross Tenant Admin\",\"email\":\"${ADMIN2_EMAIL}\",\"telefone\":\"51990000005\",\"senha\":\"${ADMIN2_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/company2.json" "$COMPANY2_PAYLOAD")"
assert_equal "$STATUS" 201 second_tenant_bootstrap_status
DRIVER4_EMAIL="driver.cross.${RUN_ID}@vanrides.dev"
DRIVER4_PAYLOAD="{\"nome\":\"Cross Tenant Driver\",\"email\":\"${DRIVER4_EMAIL}\",\"telefone\":\"51990000006\",\"senha\":\"${DRIVER_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/motoristas "$TMP_DIR/driver4.json" "$DRIVER4_PAYLOAD" "$ADMIN2_EMAIL" "$ADMIN2_PASSWORD")"
assert_equal "$STATUS" 201 create_cross_tenant_driver_status
DRIVER4_ID="$(json_get "$TMP_DIR/driver4.json" id)"
STATUS="$(http_request GET "/api/localizacao/${DRIVER4_ID}" "$TMP_DIR/cross-tenant-not-found.json" "" "$passenger_main_email" "$passenger_password")"
assert_equal "$STATUS" 404 cross_tenant_location_status
NONEXISTENT_DRIVER_ID=999999999
STATUS="$(http_request GET "/api/localizacao/${NONEXISTENT_DRIVER_ID}" "$TMP_DIR/nonexistent-not-found.json" "" "$passenger_main_email" "$passenger_password")"
assert_equal "$STATUS" 404 nonexistent_location_status

CANCELED_FINGERPRINT="$(not_found_fingerprint "$TMP_DIR/canceled-trip-not-found.json")"
LEGACY_FINGERPRINT="$(not_found_fingerprint "$TMP_DIR/legacy-link-not-found.json")"
GUARDIAN_LEGACY_FINGERPRINT="$(not_found_fingerprint "$TMP_DIR/guardian-legacy-not-found.json")"
CROSS_FINGERPRINT="$(not_found_fingerprint "$TMP_DIR/cross-tenant-not-found.json")"
NONEXISTENT_FINGERPRINT="$(not_found_fingerprint "$TMP_DIR/nonexistent-not-found.json")"
assert_equal "$LEGACY_FINGERPRINT" "$CANCELED_FINGERPRINT" legacy_not_found_fingerprint
assert_equal "$GUARDIAN_LEGACY_FINGERPRINT" "$CANCELED_FINGERPRINT" guardian_legacy_not_found_fingerprint
assert_equal "$CROSS_FINGERPRINT" "$CANCELED_FINGERPRINT" cross_tenant_not_found_fingerprint
assert_equal "$NONEXISTENT_FINGERPRINT" "$CANCELED_FINGERPRINT" nonexistent_not_found_fingerprint
cmp -s "$TMP_DIR/legacy-link-not-found.json" "$TMP_DIR/canceled-trip-not-found.json" || fail legacy_not_found_body_bytes
cmp -s "$TMP_DIR/guardian-legacy-not-found.json" "$TMP_DIR/canceled-trip-not-found.json" || fail guardian_legacy_not_found_body_bytes
cmp -s "$TMP_DIR/cross-tenant-not-found.json" "$TMP_DIR/canceled-trip-not-found.json" || fail cross_tenant_not_found_body_bytes
cmp -s "$TMP_DIR/nonexistent-not-found.json" "$TMP_DIR/canceled-trip-not-found.json" || fail nonexistent_not_found_body_bytes
NOT_FOUND_CONTENT_TYPE="$(content_type "$TMP_DIR/canceled-trip-not-found.json.headers")"
assert_equal "$(content_type "$TMP_DIR/legacy-link-not-found.json.headers")" "$NOT_FOUND_CONTENT_TYPE" legacy_not_found_content_type
assert_equal "$(content_type "$TMP_DIR/guardian-legacy-not-found.json.headers")" "$NOT_FOUND_CONTENT_TYPE" guardian_legacy_not_found_content_type
assert_equal "$(content_type "$TMP_DIR/cross-tenant-not-found.json.headers")" "$NOT_FOUND_CONTENT_TYPE" cross_tenant_not_found_content_type
assert_equal "$(content_type "$TMP_DIR/nonexistent-not-found.json.headers")" "$NOT_FOUND_CONTENT_TYPE" nonexistent_not_found_content_type
[[ "$NOT_FOUND_CONTENT_TYPE" == application/json* ]] || fail not_found_content_type
record canceled_trip_location 404
record legacy_link_only_location 404
record guardian_legacy_link_only_location 404
record cross_tenant_location 404
record nonexistent_target_location 404
record not_found_content_type application/json
record not_found_fingerprint_sha256 \
  "$(printf '%s' "$CANCELED_FINGERPRINT" | shasum -a 256 | awk '{print $1}')"
run_app_probe negative

COMPANY_COUNT_FINAL="$(db_scalar 'SELECT COUNT(*) FROM company;')"
ADMIN_COUNT_FINAL="$(db_scalar 'SELECT COUNT(*) FROM company_admin;')"
assert_equal "$COMPANY_COUNT_FINAL" "$((COMPANY_COUNT_BEFORE_NEGATIVE + 1))" final_company_count
assert_equal "$ADMIN_COUNT_FINAL" "$((ADMIN_COUNT_BEFORE_NEGATIVE + 1))" final_admin_count
record expected_company_delta 1
record expected_admin_delta 1

JAR_SHA256_AFTER="$(sha256_file "$JAR_PATH")"
record jar_sha256_after "$JAR_SHA256_AFTER"
assert_equal "$JAR_SHA256_AFTER" "$JAR_SHA256" jar_changed_during_smoke
assert_equal "$(git_safe rev-parse HEAD)" "$BACKEND_SHA" backend_sha_changed_during_smoke
assert_equal "$(git_safe rev-parse 'HEAD^{tree}')" "$BACKEND_TREE" backend_tree_changed_during_smoke
assert_no_hidden_index_flags "$ROOT_DIR" backend_index_flags_hidden_after
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_changed_during_smoke
assert_equal "$(git_safe -C "$APP_ROOT" rev-parse HEAD)" "$APP_SHA" \
  app_sha_changed_during_smoke
assert_equal "$(git_safe -C "$APP_ROOT" rev-parse 'HEAD^{tree}')" "$APP_TREE" \
  app_tree_changed_during_smoke
assert_no_hidden_index_flags "$APP_ROOT" app_index_flags_hidden_after
[[ -z "$(git_safe -C "$APP_ROOT" status --porcelain --untracked-files=all)" ]] \
  || fail app_worktree_changed_during_smoke
assert_equal "$(git_safe -C "$APP_ROOT" hash-object --no-filters \
  "$APP_CONSUMER_PROBE")" "$APP_CONSUMER_PROBE_GIT_BLOB" \
  app_consumer_probe_changed_during_smoke
assert_equal "$(sha256_file "$APP_CONSUMER_PROBE")" \
  "$APP_CONSUMER_PROBE_RAW_SHA256" app_consumer_probe_raw_changed_during_smoke
record same_jar_runtime PASS
record remote_environment_mutation none
MAIN_RESULT=PASS
