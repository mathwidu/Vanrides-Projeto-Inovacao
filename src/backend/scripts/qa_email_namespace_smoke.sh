#!/usr/bin/env bash

set -Eeuo pipefail

umask 077
export LC_ALL=C
export LANG=C
export PYTHONUTF8=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/qa_email_namespace_smoke.sh"
EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"
POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"
EXPECTED_POSTGRES_IMAGE_ID="${VANRIDES_EXPECTED_POSTGRES_IMAGE_ID:-}"
RUN_ID="namespace-$(date -u +%Y%m%dT%H%M%SZ)-$$"
EMAIL_SUFFIX="$(printf '%s' "$RUN_ID" | tr '[:upper:]' '[:lower:]')"
TMP_BASE="${TMPDIR:-/tmp}"
TMP_DIR="$(mktemp -d "$TMP_BASE/vanrides-namespace.XXXXXX")"
SUMMARY_FILE="$TMP_DIR/summary.env"
EVIDENCE_BASE="$ROOT_DIR/output/qa-evidence"
EVIDENCE_DIR="$EVIDENCE_BASE/$RUN_ID"
MANIFEST_PATH="$EVIDENCE_DIR/manifest.env"
MANIFEST_RELATIVE="output/qa-evidence/$RUN_ID/manifest.env"
REQUESTED_CONTAINER_NAME="${VANRIDES_CONTAINER_NAME:-}"
REQUESTED_CONTAINER_RUN_TOKEN="${VANRIDES_CONTAINER_RUN_TOKEN:-}"
CONTAINER_NAME="${REQUESTED_CONTAINER_NAME:-vanrides-$(printf '%s' "$RUN_ID" \
  | tr '[:upper:]' '[:lower:]')}"
CONTAINER_RUN_TOKEN="$REQUESTED_CONTAINER_RUN_TOKEN"
CONTAINER_LABEL_KEY="com.vanrides.qa.email-namespace.run-token"
CONTAINER_ID=""
CONTAINER_ATTEMPTED=0
APP_PID=""
MAIN_RESULT=""
FAILED_CHECK=""

for log_file in build.log application.log backend-gate.log db.stderr docker-run.stderr; do
  : >"$TMP_DIR/$log_file"
done
: >"$SUMMARY_FILE"

record() {
  local key="$1"
  local value="$2"
  [[ "$key" =~ ^[a-z0-9_]+$ ]] || return 1
  [[ "$value" =~ ^[A-Za-z0-9._:/@+-]+$ ]] || return 1
  printf '%s=%s\n' "$key" "$value" >>"$SUMMARY_FILE"
}

sha256_file() {
  shasum -a 256 "$1" | awk '{print $1}'
}

sha256_text() {
  printf '%s' "$1" | shasum -a 256 | awk '{print $1}'
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

assert_no_hidden_index_flags() {
  local repo_root="$1"
  local label="$2"
  local hidden_entries
  hidden_entries="$(git_safe -C "$repo_root" ls-files -v -- \
    | awk 'substr($0, 1, 1) == "S" || substr($0, 1, 1) ~ /[a-z]/ { print }')"
  [[ -z "$hidden_entries" ]] || fail "$label"
}

ensure_evidence_base() {
  local output_dir="$ROOT_DIR/output"
  if [[ -e "$output_dir" ]]; then
    [[ -d "$output_dir" && ! -L "$output_dir" ]] || return 1
  else
    mkdir -- "$output_dir" || return 1
  fi
  if [[ -e "$EVIDENCE_BASE" ]]; then
    [[ -d "$EVIDENCE_BASE" && ! -L "$EVIDENCE_BASE" ]] || return 1
  else
    mkdir -- "$EVIDENCE_BASE" || return 1
  fi
}

persist_manifest_create_only() {
  local body="$1"
  [[ ! -e "$EVIDENCE_DIR" ]] || return 1
  mkdir -- "$EVIDENCE_DIR" || return 1
  (
    set -o noclobber
    umask 077
    printf '%s' "$body" >"$MANIFEST_PATH"
    chmod 600 "$MANIFEST_PATH"
  )
}

append_log_hashes() {
  record build_log_sha256 "$(sha256_file "$TMP_DIR/build.log")"
  record application_log_sha256 "$(sha256_file "$TMP_DIR/application.log")"
  record backend_gate_log_sha256 "$(sha256_file "$TMP_DIR/backend-gate.log")"
  record db_log_sha256 "$(sha256_file "$TMP_DIR/db.stderr")"
  record docker_log_sha256 "$(sha256_file "$TMP_DIR/docker-run.stderr")"
}

cleanup() {
  local original_status=$?
  local cleanup_status=PASS
  local smoke_result=FAIL
  local final_status="$original_status"
  local owned_container_id="$CONTAINER_ID"
  local owner_value=""
  local summary_body=""
  local failure_sha256="$(sha256_text "${FAILED_CHECK:-unexpected_exit}")"
  local manifest_body=""

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
    cleanup_status=FAIL
  fi

  if [[ "$CONTAINER_ATTEMPTED" == "1" ]]; then
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
      cleanup_status=FAIL
    else
      if [[ -z "$owned_container_id" ]] && docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        owned_container_id="$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME" 2>/dev/null)"
      fi
      if [[ -n "$owned_container_id" ]] && docker inspect "$owned_container_id" >/dev/null 2>&1; then
        owner_value="$(docker inspect --format \
          '{{ index .Config.Labels "com.vanrides.qa.email-namespace.run-token" }}' \
          "$owned_container_id" 2>/dev/null)"
        if [[ "$owned_container_id" =~ ^[0-9a-f]{64}$ && \
              "$owner_value" == "$CONTAINER_RUN_TOKEN" ]]; then
          docker rm --force "$owned_container_id" >/dev/null 2>&1 || cleanup_status=FAIL
        else
          cleanup_status=FAIL
        fi
      fi
      if [[ -n "$owned_container_id" ]] && docker inspect "$owned_container_id" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
      # Never remove a replacement that raced to reuse the requested name.
      if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
    fi
  fi

  append_log_hashes || cleanup_status=FAIL
  summary_body="$(<"$SUMMARY_FILE")"
  case "$TMP_DIR" in
    "$TMP_BASE"/vanrides-namespace.*)
      rm -rf -- "$TMP_DIR" || cleanup_status=FAIL
      ;;
    *)
      cleanup_status=FAIL
      ;;
  esac
  [[ ! -e "$TMP_DIR" ]] || cleanup_status=FAIL

  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS && "$cleanup_status" == PASS ]]; then
    smoke_result=PASS
  else
    final_status=1
  fi

  manifest_body="${summary_body}"$'\n'"smoke_result=${smoke_result}"$'\n'\
"failed_check_sha256=${failure_sha256}"$'\n'"cleanup_status=${cleanup_status}"$'\n'
  if ! ensure_evidence_base || ! persist_manifest_create_only "$manifest_body"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
  fi

  printf 'run_id=%s\n' "$RUN_ID"
  printf 'manifest=%s\n' "$MANIFEST_RELATIVE"
  printf 'smoke_result=%s\n' "$smoke_result"
  printf 'failed_check_sha256=%s\n' "$failure_sha256"
  printf 'cleanup=%s\n' "$cleanup_status"
  exit "$final_status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

free_port() {
  python3 <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

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

assert_bootstrap_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload.get("id"), int) or not isinstance(payload.get("companyId"), int):
    raise SystemExit("bootstrap identifiers missing")
if payload.get("emailVerificado") is not True:
    raise SystemExit("bootstrap admin is not verified")
PY
}

assert_login_body() {
  assert_role_login_body "$1" Admin
}

assert_role_login_body() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if payload.get("tipoUsuario") != sys.argv[2]:
    raise SystemExit("unexpected login role")
if payload.get("tokenType") != "Bearer" or not payload.get("accessToken") or not payload.get("refreshToken"):
    raise SystemExit("login tokens missing")
PY
}

assert_guardian_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload.get("id"), int):
    raise SystemExit("guardian identifier missing")
if payload.get("acessoConfigurado") is not True:
    raise SystemExit("guardian access is not configured")
if payload.get("emailVerificado") is not True:
    raise SystemExit("guardian is not verified")
if payload.get("passageiros") != []:
    raise SystemExit("new guardian has unexpected passenger links")
if "senha" in payload:
    raise SystemExit("guardian response exposes password material")
PY
}

assert_refresh_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if payload.get("tokenType") != "Bearer":
    raise SystemExit("unexpected refresh token type")
if not payload.get("accessToken") or not payload.get("refreshToken"):
    raise SystemExit("rotated credentials missing")
PY
}

assert_pending_account_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload.get("id"), int):
    raise SystemExit("account identifier missing")
if payload.get("emailVerificado") is not False:
    raise SystemExit("account must remain pending")
PY
}

assert_generic_conflict_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
expected_keys = {"code", "message", "email"}
if set(payload).difference(expected_keys):
    raise SystemExit("conflict body exposes unexpected context")
if payload.get("code") != "EMAIL_ALREADY_IN_USE":
    raise SystemExit("conflict code mismatch")
if payload.get("message") != "O e-mail informado já está em uso.":
    raise SystemExit("conflict message mismatch")
if payload.get("email") is not None:
    raise SystemExit("conflict body is not generic")
PY
}

assert_invalid_address_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
expected_keys = {"code", "message", "email"}
if set(payload).difference(expected_keys):
    raise SystemExit("invalid-address body exposes unexpected context")
if payload.get("code") != "INVALID_EMAIL":
    raise SystemExit("invalid-address code mismatch")
if payload.get("message") != "Informe um e-mail válido.":
    raise SystemExit("invalid-address message mismatch")
if payload.get("email") is not None:
    raise SystemExit("invalid-address body echoes context")
PY
}

assert_opaque_resend_bodies() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

payloads = []
for file_name in sys.argv[1:]:
    with open(file_name, encoding="utf-8") as handle:
        payloads.append(json.load(handle))
expected_keys = {"message", "email"}
expected_message = "Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada."
for payload in payloads:
    if set(payload) != expected_keys:
        raise SystemExit("unexpected resend contract")
    if not isinstance(payload.get("email"), str):
        raise SystemExit("resend request echo missing")
    if payload.get("message") != expected_message:
        raise SystemExit("resend message is not generic")
if payloads[0]["message"] != payloads[1]["message"]:
    raise SystemExit("resend existence oracle detected")
PY
}

assert_changed_body() {
  python3 - "$1" "$2" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
with open(sys.argv[2], encoding="utf-8") as handle:
    expected_email = handle.read()
if payload.get("email") != expected_email or not isinstance(payload.get("message"), str):
    raise SystemExit("pending address change body mismatch")
PY
}

http_request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local payload="${4:-}"
  local login_name="${5:-}"
  local login_password="${6:-}"
  local bearer_credential="${7:-}"
  local config_file="${output_file}.curl-config"
  local curl_status=0
  local response_status=""
  local -a args=(
    --noproxy '*' --silent --show-error --connect-timeout 5 --max-time 30
    --request "$method" --output "$output_file" --write-out '%{http_code}'
    --header 'Content-Type: application/json'
  )

  : >"$config_file"
  chmod 600 "$config_file"
  if [[ -n "$login_name" ]]; then
    [[ -z "$bearer_credential" ]] || return 1
    [[ "$login_name" =~ ^[a-z0-9@._+-]+$ ]] || return 1
    [[ "$login_password" =~ ^[0-9a-f]{32}$ ]] || return 1
    printf 'user = "%s:%s"\n' "$login_name" "$login_password" >>"$config_file"
  elif [[ -n "$bearer_credential" ]]; then
    [[ "$bearer_credential" =~ ^[A-Za-z0-9._-]+$ ]] || return 1
    printf 'header = "Authorization: Bearer %s"\n' \
      "$bearer_credential" >>"$config_file"
  fi

  set +e
  if [[ -n "$payload" ]]; then
    response_status="$(printf '%s' "$payload" \
      | curl --disable --config "$config_file" "${args[@]}" --data-binary @- "${BASE_URL}${path}")"
    curl_status=$?
  else
    response_status="$(curl --disable --config "$config_file" "${args[@]}" "${BASE_URL}${path}")"
    curl_status=$?
  fi
  set -e
  rm -f -- "$config_file"
  [[ "$curl_status" -eq 0 ]] || return "$curl_status"
  printf '%s' "$response_status"
}

db_scalar() {
  local sql="$1"
  printf '%s\n' "$sql" | docker exec --interactive "$CONTAINER_ID" \
    psql --username vanrides --dbname vanrides --tuples-only --no-align \
      --set ON_ERROR_STOP=1 --file - 2>>"$TMP_DIR/db.stderr"
}

assert_device_owner() {
  local token_hash="$1"
  local expected_role="$2"
  local expected_user_id="$3"
  local expected_company_id="$4"
  local label="$5"

  [[ "$token_hash" =~ ^[0-9a-f]{64}$ ]] || fail "${label}_token_hash"
  [[ "$expected_role" =~ ^[A-Z_]+$ ]] || fail "${label}_role"
  [[ "$expected_user_id" =~ ^[1-9][0-9]*$ ]] || fail "${label}_user_id"
  [[ "$expected_company_id" =~ ^[1-9][0-9]*$ ]] || fail "${label}_company_id"

  assert_equal "$(db_scalar "SELECT count(*) FROM device_token WHERE token_hash = '${token_hash}';")" \
    1 "${label}_cardinality"
  assert_equal "$(db_scalar "SELECT user_role FROM device_token WHERE token_hash = '${token_hash}';")" \
    "$expected_role" "${label}_role_exact"
  assert_equal "$(db_scalar "SELECT user_id FROM device_token WHERE token_hash = '${token_hash}';")" \
    "$expected_user_id" "${label}_user_exact"
  assert_equal "$(db_scalar "SELECT company_id FROM device_token WHERE token_hash = '${token_hash}';")" \
    "$expected_company_id" "${label}_company_exact"
  assert_equal "$(db_scalar "SELECT active::text FROM device_token WHERE token_hash = '${token_hash}';")" \
    true "${label}_active"
  assert_equal "$(db_scalar "SELECT count(*) FROM device_token device JOIN login_identity registry ON registry.user_role = device.user_role AND registry.user_id = device.user_id AND registry.normalized_email = device.user_email WHERE device.token_hash = '${token_hash}' AND device.active = TRUE;")" \
    1 "${label}_registry_owner_exact"
}

assert_runtime_unchanged() {
  local phase="$1"
  kill -0 "$APP_PID" >/dev/null 2>&1 || fail "application_not_alive_${phase}"
  assert_equal "$(sha256_file "$RUNTIME_JAR_PATH")" "$JAR_SHA256" \
    "runtime_jar_changed_${phase}"
}

run_clean_build() (
  local variable
  while IFS= read -r variable; do
    case "$variable" in
      MAVEN_*|MVNW_*) unset "$variable" ;;
    esac
  done < <(builtin compgen -e)
  export MAVEN_SKIP_RC=1
  ./mvnw -q clean package -DskipTests
)

run_backend_gate() (
  local variable
  unset DOCKER_HOST DOCKER_CONTEXT
  while IFS= read -r variable; do
    case "$variable" in
      MAVEN_*|MVNW_*) unset "$variable" ;;
    esac
  done < <(builtin compgen -e)
  VANRIDES_BACKEND_BASE_URL="$BASE_URL" ./scripts/qa_gate_card.sh
)

for command in awk basename chmod cp curl date docker env find git grep java kill mkdir \
  mktemp openssl python3 rm seq shasum sleep tr; do
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

[[ -z "${DOCKER_HOST:-}" ]] || fail docker_host_override_not_allowed
[[ -z "${DOCKER_CONTEXT:-}" ]] || fail docker_context_override_not_allowed
DOCKER_CONTEXT_NAME="$(docker context show 2>/dev/null)" || fail docker_context_unavailable
[[ -n "$DOCKER_CONTEXT_NAME" ]] || fail docker_context_empty
DOCKER_ENDPOINT="$(docker context inspect "$DOCKER_CONTEXT_NAME" \
  --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" || fail docker_endpoint_unavailable
case "$DOCKER_ENDPOINT" in
  unix:///*) DOCKER_ENDPOINT_TRANSPORT=unix ;;
  npipe:////./pipe/*) DOCKER_ENDPOINT_TRANSPORT=npipe ;;
  *) fail docker_endpoint_not_local ;;
esac
export DOCKER_HOST="$DOCKER_ENDPOINT"
unset DOCKER_CONTEXT
docker info >/dev/null 2>&1 || fail docker_daemon_unavailable

while IFS='=' read -r variable _; do
  case "$variable" in
    GIT_*) unset "$variable" ;;
  esac
done < <(env)
export GIT_NO_REPLACE_OBJECTS=1

[[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] || fail expected_backend_sha_required
[[ "$POSTGRES_IMAGE" =~ ^[A-Za-z0-9._/@:+-]+$ ]] || fail invalid_postgres_image_ref
if [[ -n "$EXPECTED_POSTGRES_IMAGE_ID" ]]; then
  [[ "$EXPECTED_POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] \
    || fail invalid_expected_postgres_image_id
fi
if [[ -n "$REQUESTED_CONTAINER_NAME" || -n "$REQUESTED_CONTAINER_RUN_TOKEN" ]]; then
  [[ -n "$REQUESTED_CONTAINER_NAME" && -n "$REQUESTED_CONTAINER_RUN_TOKEN" ]] \
    || fail coordinator_container_ownership_pair_required
  [[ "$REQUESTED_CONTAINER_NAME" =~ ^vanrides-[a-z0-9][a-z0-9-]{7,62}$ ]] \
    || fail invalid_coordinator_container_name
  [[ "$REQUESTED_CONTAINER_RUN_TOKEN" =~ ^[0-9a-f]{32}$ ]] \
    || fail invalid_coordinator_container_run_token
fi
[[ ! -e "$EVIDENCE_DIR" ]] || fail evidence_run_id_collision

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
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_not_clean
RUNNER_GIT_BLOB="$(git_safe rev-parse \
  "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_email_namespace_smoke.sh")" || fail runner_not_versioned
RUNNER_WORKTREE_BLOB="$(git_safe hash-object --no-filters "$SCRIPT_PATH")"
assert_equal "$RUNNER_WORKTREE_BLOB" "$RUNNER_GIT_BLOB" runner_blob_mismatch

record run_id "$RUN_ID"
record expected_backend_sha "$EXPECTED_BACKEND_SHA"
record backend_sha "$BACKEND_SHA"
record backend_tree "$BACKEND_TREE"
record runner_git_blob "$RUNNER_GIT_BLOB"
record runner_sha256 "$(sha256_file "$SCRIPT_PATH")"
record docker_endpoint_transport "$DOCKER_ENDPOINT_TRANSPORT"

if ! run_clean_build >"$TMP_DIR/build.log" 2>&1; then
  fail backend_clean_package
fi

BUILT_JAR_PATH=""
JAR_COUNT=0
while IFS= read -r candidate; do
  case "$candidate" in
    *.jar.original) continue ;;
  esac
  BUILT_JAR_PATH="$candidate"
  JAR_COUNT=$((JAR_COUNT + 1))
done < <(find "$ROOT_DIR/target" -maxdepth 1 -type f -name '*.jar' -print)
assert_equal "$JAR_COUNT" 1 jar_candidate_count
[[ -n "$BUILT_JAR_PATH" && -f "$BUILT_JAR_PATH" ]] || fail jar_not_found
BUILT_JAR_SHA256="$(sha256_file "$BUILT_JAR_PATH")"
RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"
cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH" || fail runtime_jar_copy
assert_equal "$(sha256_file "$RUNTIME_JAR_PATH")" "$BUILT_JAR_SHA256" \
  runtime_jar_copy_hash
JAR_SHA256="$BUILT_JAR_SHA256"
record jar_file "$(basename "$BUILT_JAR_PATH")"
record jar_sha256 "$JAR_SHA256"

DB_PASSWORD="$(openssl rand -hex 24)"
ADMIN_PASSWORD="$(openssl rand -hex 16)"
PASSENGER_PASSWORD="$(openssl rand -hex 16)"
DRIVER_PASSWORD="$(openssl rand -hex 16)"
GUARDIAN_PASSWORD="$(openssl rand -hex 16)"
DEVICE_TOKEN="$(openssl rand -hex 32)"
DELETE_DEVICE_TOKEN="$(openssl rand -hex 32)"
DEVICE_TOKEN_HASH="$(sha256_text "$DEVICE_TOKEN")"
DELETE_DEVICE_TOKEN_HASH="$(sha256_text "$DELETE_DEVICE_TOKEN")"
JWT_SECRET="$(openssl rand -hex 48)"
APP_PORT="$(free_port)"

if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
  fail postgres_container_name_collision
fi
if [[ -z "$CONTAINER_RUN_TOKEN" ]]; then
  CONTAINER_RUN_TOKEN="$(openssl rand -hex 16)"
fi
POSTGRES_ENV_FILE="$TMP_DIR/postgres.env"
CONTAINER_LABEL_FILE="$TMP_DIR/container.labels"
printf 'POSTGRES_DB=vanrides\nPOSTGRES_USER=vanrides\nPOSTGRES_PASSWORD=%s\n' \
  "$DB_PASSWORD" >"$POSTGRES_ENV_FILE"
printf '%s=%s\n' "$CONTAINER_LABEL_KEY" "$CONTAINER_RUN_TOKEN" \
  >"$CONTAINER_LABEL_FILE"
chmod 600 "$POSTGRES_ENV_FILE" "$CONTAINER_LABEL_FILE"
CONTAINER_ATTEMPTED=1
if ! docker run --detach --pull=missing \
  --name "$CONTAINER_NAME" \
  --label-file "$CONTAINER_LABEL_FILE" \
  --env-file "$POSTGRES_ENV_FILE" \
  --publish '127.0.0.1::5432' \
  "$POSTGRES_IMAGE" >"$TMP_DIR/container.id" 2>"$TMP_DIR/docker-run.stderr"; then
  fail postgres_container_start
fi
CONTAINER_ID="$(<"$TMP_DIR/container.id")"
[[ "$CONTAINER_ID" =~ ^[0-9a-f]{64}$ ]] || fail invalid_postgres_container_id
assert_equal "$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME")" "$CONTAINER_ID" \
  postgres_container_identity
assert_equal "$(docker inspect --format \
  '{{ index .Config.Labels "com.vanrides.qa.email-namespace.run-token" }}' \
  "$CONTAINER_ID")" "$CONTAINER_RUN_TOKEN" postgres_container_ownership

POSTGRES_IMAGE_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER_ID")"
[[ "$POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] || fail invalid_postgres_runtime_image_id
if [[ -n "$EXPECTED_POSTGRES_IMAGE_ID" ]]; then
  assert_equal "$POSTGRES_IMAGE_ID" "$EXPECTED_POSTGRES_IMAGE_ID" postgres_image_id
fi
POSTGRES_IMAGE_REPO_DIGEST="$(docker image inspect "$POSTGRES_IMAGE_ID" \
  --format '{{range .RepoDigests}}{{println .}}{{end}}' 2>/dev/null | awk 'NR == 1 {print}')"
POSTGRES_IMAGE_REPO_DIGEST="${POSTGRES_IMAGE_REPO_DIGEST:-not_available}"
record postgres_image_ref "$POSTGRES_IMAGE"
record postgres_image_id "$POSTGRES_IMAGE_ID"
record postgres_image_repo_digest "$POSTGRES_IMAGE_REPO_DIGEST"
record postgres_container_id "$CONTAINER_ID"

DB_PORT="$(docker port "$CONTAINER_ID" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] || fail postgres_port_not_bound
for _ in $(seq 1 60); do
  if docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
      >/dev/null 2>&1; then
    break
  fi
  sleep 1
done
docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
  >/dev/null 2>&1 || fail postgres_not_ready
POSTGRES_VERSION_NUM="$(db_scalar 'SHOW server_version_num;')"
[[ "$POSTGRES_VERSION_NUM" =~ ^16[0-9]{4}$ ]] || fail postgres_major_not_16
record postgres_major 16

BASE_URL="http://127.0.0.1:${APP_PORT}"
JAVA_BIN="$(command -v java)"
mkdir -p "$TMP_DIR/home"
env -i \
  HOME="$TMP_DIR/home" TMPDIR="$TMP_DIR" LC_ALL=C LANG=C \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/vanrides" \
  SPRING_DATASOURCE_USERNAME=vanrides \
  SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  SPRING_PROFILES_ACTIVE=local \
  SPRING_JPA_SHOW_SQL=false \
  SERVER_PORT="$APP_PORT" \
  VANRIDES_EMAIL_PROVIDER=log \
  VANRIDES_EMAIL_VERIFICATION_RESEND_COOLDOWN_MINUTES=0 \
  VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL="${BASE_URL}/auth/email-verifications/confirm" \
  VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=qa-admin.invalid \
  LOGGING_LEVEL_COM_VANRIDES_VANRIDES_SERVICE=WARN \
  VANRIDES_AUTH_BASIC_ENABLED=true \
  VANRIDES_RATE_LIMIT_ENABLED=false \
  VANRIDES_ROUTING_PROVIDER=static \
  VANRIDES_PUSH_PROVIDER=none \
  VANRIDES_MAPS_PLATFORM_ENABLED=false \
  VANRIDES_JWT_SECRET="$JWT_SECRET" \
  VANRIDES_OPERATIONS_TIME_ZONE=America/Sao_Paulo \
  "$JAVA_BIN" -jar "$RUNTIME_JAR_PATH" >"$TMP_DIR/application.log" 2>&1 &
APP_PID=$!

READY_CODE=000
for _ in $(seq 1 120); do
  kill -0 "$APP_PID" >/dev/null 2>&1 || fail application_exited_before_readiness
  READY_CODE="$(curl --disable --noproxy '*' --silent --output /dev/null \
    --write-out '%{http_code}' --max-time 2 "${BASE_URL}/motoristas" || true)"
  [[ "$READY_CODE" == 200 ]] && break
  sleep 1
done
assert_equal "$READY_CODE" 200 application_not_ready

FLYWAY_LATEST_VERSION="$(db_scalar \
  'SELECT version FROM flyway_schema_history WHERE success = true ORDER BY installed_rank DESC LIMIT 1;')"
FLYWAY_SUCCESS_COUNT="$(db_scalar \
  'SELECT count(*) FROM flyway_schema_history WHERE success = true AND type = '\''SQL'\'';')"
assert_equal "$FLYWAY_LATEST_VERSION" 25 flyway_latest_version
assert_equal "$FLYWAY_SUCCESS_COUNT" 25 flyway_success_count
record flyway_latest_version 25
record flyway_success_count 25

assert_runtime_unchanged gate_before
if ! run_backend_gate >"$TMP_DIR/backend-gate.log" 2>&1; then
  fail backend_qa_gate_card
fi
assert_runtime_unchanged gate_after
record backend_gate_status PASS

ADMIN_EMAIL="admin.${EMAIL_SUFFIX}@qa-admin.invalid"
PASSENGER_EMAIL="passenger.${EMAIL_SUFFIX}@pending-user.invalid"
RELEASED_EMAIL="released.${EMAIL_SUFFIX}@pending-user.invalid"
ADMIN_PAYLOAD="{\"companyName\":\"Namespace ${RUN_ID}\",\"nome\":\"Namespace Admin\",\"email\":\"${ADMIN_EMAIL}\",\"telefone\":\"51990000001\",\"senha\":\"${ADMIN_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/bootstrap.json" "$ADMIN_PAYLOAD")"
assert_equal "$STATUS" 201 bootstrap_admin_status
assert_bootstrap_body "$TMP_DIR/bootstrap.json" || fail bootstrap_admin_body
ADMIN_ID="$(json_get "$TMP_DIR/bootstrap.json" id)"
COMPANY_ID="$(json_get "$TMP_DIR/bootstrap.json" companyId)"
assert_equal "$(db_scalar "SELECT email_verified::text FROM company_admin WHERE id = ${ADMIN_ID};")" \
  true bootstrap_admin_verified_sql
record bootstrap_admin_status 201
record bootstrap_admin_verified_status PASS

LOGIN_PAYLOAD="{\"email\":\"${ADMIN_EMAIL}\",\"senha\":\"${ADMIN_PASSWORD}\"}"
STATUS="$(http_request POST /login "$TMP_DIR/login.json" "$LOGIN_PAYLOAD")"
assert_equal "$STATUS" 200 admin_login_status
assert_login_body "$TMP_DIR/login.json" || fail admin_login_body
record admin_login_status 200

ACCESS_CREDENTIAL="$(json_get "$TMP_DIR/login.json" accessToken)"
REFRESH_CREDENTIAL="$(json_get "$TMP_DIR/login.json" refreshToken)"
[[ -n "$ACCESS_CREDENTIAL" && -n "$REFRESH_CREDENTIAL" ]] || fail login_credentials_missing
STATUS="$(http_request GET /company-admins/passageiros "$TMP_DIR/basic-protected.json" "" \
  "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 200 basic_protected_status
STATUS="$(http_request GET /company-admins/passageiros "$TMP_DIR/bearer-protected.json" \
  "" "" "" "$ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 200 bearer_protected_status
REFRESH_PAYLOAD="{\"refreshToken\":\"${REFRESH_CREDENTIAL}\"}"
STATUS="$(http_request POST /auth/refresh "$TMP_DIR/refresh.json" "$REFRESH_PAYLOAD")"
assert_equal "$STATUS" 200 refresh_rotation_status
assert_refresh_body "$TMP_DIR/refresh.json" || fail refresh_rotation_body
ROTATED_ACCESS_CREDENTIAL="$(json_get "$TMP_DIR/refresh.json" accessToken)"
STATUS="$(http_request GET /company-admins/passageiros "$TMP_DIR/rotated-bearer.json" \
  "" "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 200 rotated_bearer_status
record basic_protected_status 200
record bearer_protected_status 200
record refresh_rotation_status 200
record rotated_bearer_status 200

GUARDIAN_EMAIL="guardian.${EMAIL_SUFFIX}@qa-admin.invalid"
GUARDIAN_MOVED_EMAIL="guardian-moved.${EMAIL_SUFFIX}@qa-admin.invalid"
GUARDIAN_PAYLOAD="{\"nome\":\"Namespace Guardian\",\"email\":\"${GUARDIAN_EMAIL}\",\"telefone\":\"51990000006\",\"dataNascimento\":\"1980-01-01\",\"senha\":\"${GUARDIAN_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/responsaveis "$TMP_DIR/guardian.json" \
  "$GUARDIAN_PAYLOAD" "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 201 guardian_create_status
assert_guardian_body "$TMP_DIR/guardian.json" || fail guardian_create_body
GUARDIAN_ID="$(json_get "$TMP_DIR/guardian.json" id)"
assert_equal "$(db_scalar "SELECT count(*) FROM guardian source JOIN login_identity registry ON registry.user_role = 'GUARDIAN' AND registry.user_id = source.id AND registry.normalized_email = source.email WHERE source.id = ${GUARDIAN_ID};")" \
  1 guardian_registry_exact
assert_equal "$(db_scalar "SELECT email_verified::text FROM guardian WHERE id = ${GUARDIAN_ID};")" \
  true guardian_verified_sql
record guardian_create_status 201
record guardian_registry_status PASS
record guardian_verified_status PASS

PASSENGER_COUNT_BEFORE_GUARDIAN_CONFLICT="$(db_scalar 'SELECT count(*) FROM passageiro;')"
GUARDIAN_COLLISION_PAYLOAD="{\"nome\":\"Namespace Guardian Collision\",\"email\":\"${GUARDIAN_EMAIL}\",\"telefone\":\"51990000007\",\"endereco\":\"Rua Namespace\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
STATUS="$(http_request POST /company-admins/passageiros \
  "$TMP_DIR/guardian-cross-role.json" "$GUARDIAN_COLLISION_PAYLOAD" \
  "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 409 guardian_cross_role_conflict_status
assert_generic_conflict_body "$TMP_DIR/guardian-cross-role.json" \
  || fail guardian_cross_role_conflict_body
assert_equal "$(db_scalar 'SELECT count(*) FROM passageiro;')" \
  "$PASSENGER_COUNT_BEFORE_GUARDIAN_CONFLICT" guardian_cross_role_conflict_mutation
record guardian_cross_role_conflict_status 409
record guardian_cross_role_contract_status PASS

GUARDIAN_LOGIN_PAYLOAD="{\"email\":\"${GUARDIAN_EMAIL}\",\"senha\":\"${GUARDIAN_PASSWORD}\"}"
STATUS="$(http_request POST /login "$TMP_DIR/guardian-login.json" \
  "$GUARDIAN_LOGIN_PAYLOAD")"
assert_equal "$STATUS" 200 guardian_login_status
assert_role_login_body "$TMP_DIR/guardian-login.json" Responsavel \
  || fail guardian_login_body
GUARDIAN_ACCESS_CREDENTIAL="$(json_get "$TMP_DIR/guardian-login.json" accessToken)"
GUARDIAN_REFRESH_CREDENTIAL="$(json_get "$TMP_DIR/guardian-login.json" refreshToken)"
[[ -n "$GUARDIAN_ACCESS_CREDENTIAL" && -n "$GUARDIAN_REFRESH_CREDENTIAL" ]] \
  || fail guardian_login_credentials_missing
record guardian_login_status 200

DEVICE_PAYLOAD="{\"token\":\"${DEVICE_TOKEN}\",\"platform\":\"ios\"}"
STATUS="$(http_request POST /devices/fcm-token "$TMP_DIR/device-admin.json" \
  "$DEVICE_PAYLOAD" "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 204 device_admin_registration_status
[[ ! -s "$TMP_DIR/device-admin.json" ]] || fail device_admin_registration_body
assert_device_owner "$DEVICE_TOKEN_HASH" COMPANY_ADMIN "$ADMIN_ID" "$COMPANY_ID" \
  device_admin_owner
record device_admin_registration_status 204
record device_admin_owner_status PASS

# Competing owners exercise the unique-token lane. Both requests must complete
# through the row-lock path; the explicit guardian registration below then
# establishes the deterministic owner used by the change/delete assertions.
(
  race_status="$(http_request POST /devices/fcm-token \
    "$TMP_DIR/device-race-admin.json" "$DEVICE_PAYLOAD" \
    "" "" "$ROTATED_ACCESS_CREDENTIAL")"
  printf '%s' "$race_status" >"$TMP_DIR/device-race-admin.status"
) &
DEVICE_ADMIN_RACE_PID=$!
(
  race_status="$(http_request POST /devices/fcm-token \
    "$TMP_DIR/device-race-guardian.json" "$DEVICE_PAYLOAD" \
    "" "" "$GUARDIAN_ACCESS_CREDENTIAL")"
  printf '%s' "$race_status" >"$TMP_DIR/device-race-guardian.status"
) &
DEVICE_GUARDIAN_RACE_PID=$!
DEVICE_ADMIN_RACE_RESULT=0
DEVICE_GUARDIAN_RACE_RESULT=0
wait "$DEVICE_ADMIN_RACE_PID" || DEVICE_ADMIN_RACE_RESULT=$?
wait "$DEVICE_GUARDIAN_RACE_PID" || DEVICE_GUARDIAN_RACE_RESULT=$?
assert_equal "$DEVICE_ADMIN_RACE_RESULT" 0 device_race_admin_request
assert_equal "$DEVICE_GUARDIAN_RACE_RESULT" 0 device_race_guardian_request
assert_equal "$(<"$TMP_DIR/device-race-admin.status")" 204 \
  device_race_admin_status
assert_equal "$(<"$TMP_DIR/device-race-guardian.status")" 204 \
  device_race_guardian_status
[[ ! -s "$TMP_DIR/device-race-admin.json" ]] || fail device_race_admin_body
[[ ! -s "$TMP_DIR/device-race-guardian.json" ]] || fail device_race_guardian_body
assert_equal "$(db_scalar "SELECT count(*) FROM device_token WHERE token_hash = '${DEVICE_TOKEN_HASH}';")" \
  1 device_race_single_row
record device_race_admin_status 204
record device_race_guardian_status 204
record device_race_serialization_status PASS

STATUS="$(http_request POST /devices/fcm-token "$TMP_DIR/device-guardian.json" \
  "$DEVICE_PAYLOAD" "" "" "$GUARDIAN_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 204 device_guardian_registration_status
[[ ! -s "$TMP_DIR/device-guardian.json" ]] || fail device_guardian_registration_body
assert_device_owner "$DEVICE_TOKEN_HASH" GUARDIAN "$GUARDIAN_ID" "$COMPANY_ID" \
  device_guardian_owner
record device_guardian_registration_status 204
record device_guardian_owner_status PASS

GUARDIAN_UPDATE_PAYLOAD="{\"nome\":\"Namespace Guardian\",\"email\":\"${GUARDIAN_MOVED_EMAIL}\",\"telefone\":\"51990000006\",\"dataNascimento\":\"1980-01-01\"}"
STATUS="$(http_request PUT "/company-admins/responsaveis/${GUARDIAN_ID}" \
  "$TMP_DIR/guardian-update.json" "$GUARDIAN_UPDATE_PAYLOAD" \
  "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 200 guardian_change_status
assert_guardian_body "$TMP_DIR/guardian-update.json" || fail guardian_change_body
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE normalized_email = '${GUARDIAN_EMAIL}';")" \
  0 guardian_old_registry_key_not_released
assert_equal "$(db_scalar "SELECT count(*) FROM guardian source JOIN login_identity registry ON registry.user_role = 'GUARDIAN' AND registry.user_id = source.id AND registry.normalized_email = source.email WHERE source.id = ${GUARDIAN_ID};")" \
  1 guardian_moved_registry_exact
assert_equal "$(db_scalar "SELECT active::text FROM device_token WHERE token_hash = '${DEVICE_TOKEN_HASH}';")" \
  false device_not_invalidated_after_guardian_change
assert_equal "$(db_scalar "SELECT (invalidated_at IS NOT NULL)::text FROM device_token WHERE token_hash = '${DEVICE_TOKEN_HASH}';")" \
  true device_invalidation_time_missing_after_guardian_change
assert_equal "$(db_scalar "SELECT (device.user_email = source.email)::text FROM device_token device JOIN guardian source ON source.id = device.user_id WHERE device.token_hash = '${DEVICE_TOKEN_HASH}' AND device.user_role = 'GUARDIAN';")" \
  true device_owner_snapshot_not_moved
record guardian_change_status 200
record guardian_registry_move_status PASS
record device_change_invalidation_status PASS

STATUS="$(http_request GET /account/me "$TMP_DIR/guardian-stale-bearer.json" \
  "" "" "" "$GUARDIAN_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 401 guardian_stale_bearer_status
record guardian_stale_bearer_status 401

GUARDIAN_MOVED_LOGIN_PAYLOAD="{\"email\":\"${GUARDIAN_MOVED_EMAIL}\",\"senha\":\"${GUARDIAN_PASSWORD}\"}"
STATUS="$(http_request POST /login "$TMP_DIR/guardian-moved-login.json" \
  "$GUARDIAN_MOVED_LOGIN_PAYLOAD")"
assert_equal "$STATUS" 200 guardian_moved_login_status
assert_role_login_body "$TMP_DIR/guardian-moved-login.json" Responsavel \
  || fail guardian_moved_login_body
GUARDIAN_MOVED_ACCESS_CREDENTIAL="$(json_get \
  "$TMP_DIR/guardian-moved-login.json" accessToken)"
GUARDIAN_MOVED_REFRESH_CREDENTIAL="$(json_get \
  "$TMP_DIR/guardian-moved-login.json" refreshToken)"
[[ -n "$GUARDIAN_MOVED_ACCESS_CREDENTIAL" && \
    -n "$GUARDIAN_MOVED_REFRESH_CREDENTIAL" ]] \
  || fail guardian_moved_login_credentials_missing
record guardian_moved_login_status 200

DELETE_DEVICE_PAYLOAD="{\"token\":\"${DELETE_DEVICE_TOKEN}\",\"platform\":\"android\"}"
STATUS="$(http_request POST /devices/fcm-token \
  "$TMP_DIR/device-before-delete.json" "$DELETE_DEVICE_PAYLOAD" \
  "" "" "$GUARDIAN_MOVED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 204 device_before_delete_registration_status
[[ ! -s "$TMP_DIR/device-before-delete.json" ]] \
  || fail device_before_delete_registration_body
assert_device_owner "$DELETE_DEVICE_TOKEN_HASH" GUARDIAN "$GUARDIAN_ID" \
  "$COMPANY_ID" device_before_delete_owner
record device_before_delete_registration_status 204
record device_before_delete_owner_status PASS

STATUS="$(http_request DELETE "/company-admins/responsaveis/${GUARDIAN_ID}" \
  "$TMP_DIR/guardian-delete.json" "" "" "" "$ROTATED_ACCESS_CREDENTIAL")"
assert_equal "$STATUS" 204 guardian_delete_status
[[ ! -s "$TMP_DIR/guardian-delete.json" ]] || fail guardian_delete_body
assert_equal "$(db_scalar "SELECT count(*) FROM guardian WHERE id = ${GUARDIAN_ID};")" \
  0 guardian_delete_source
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE user_role = 'GUARDIAN' AND user_id = ${GUARDIAN_ID};")" \
  0 guardian_delete_registry
for deleted_owner_token_hash in "$DEVICE_TOKEN_HASH" "$DELETE_DEVICE_TOKEN_HASH"; do
  assert_equal "$(db_scalar "SELECT active::text FROM device_token WHERE token_hash = '${deleted_owner_token_hash}';")" \
    false device_not_invalidated_after_guardian_delete
  assert_equal "$(db_scalar "SELECT (invalidated_at IS NOT NULL)::text FROM device_token WHERE token_hash = '${deleted_owner_token_hash}';")" \
    true device_invalidation_time_missing_after_guardian_delete
  assert_equal "$(db_scalar "SELECT (user_email IS NULL)::text FROM device_token WHERE token_hash = '${deleted_owner_token_hash}';")" \
    true device_owner_snapshot_not_cleared_after_guardian_delete
done
record guardian_delete_status 204
record guardian_namespace_release_status PASS
record device_delete_invalidation_status PASS

DRIVER_COUNT_BEFORE_INVALID="$(db_scalar 'SELECT count(*) FROM motorista;')"
INVALID_DRIVER_PAYLOAD="{\"nome\":\"Namespace Invalid\",\"email\":\"invalid-address\",\"telefone\":\"51990000000\",\"senha\":\"${DRIVER_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/motoristas "$TMP_DIR/invalid.json" \
  "$INVALID_DRIVER_PAYLOAD" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 400 invalid_address_status
assert_invalid_address_body "$TMP_DIR/invalid.json" || fail invalid_address_body
assert_equal "$(db_scalar 'SELECT count(*) FROM motorista;')" \
  "$DRIVER_COUNT_BEFORE_INVALID" invalid_address_mutation
record invalid_address_status 400
record invalid_address_contract_status PASS

PASSENGER_PAYLOAD="{\"nome\":\"Namespace Passenger\",\"email\":\"${PASSENGER_EMAIL}\",\"telefone\":\"51990000002\",\"endereco\":\"Rua Namespace\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/passenger.json" \
  "$PASSENGER_PAYLOAD" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 201 passenger_create_status
assert_pending_account_body "$TMP_DIR/passenger.json" || fail passenger_pending_body
PASSENGER_ID="$(json_get "$TMP_DIR/passenger.json" id)"
assert_equal "$(db_scalar "SELECT company_id FROM passageiro WHERE id = ${PASSENGER_ID};")" \
  "$COMPANY_ID" passenger_tenant_scope
assert_equal "$(db_scalar "SELECT normalized_email FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  "$PASSENGER_EMAIL" passenger_registry_exact
record passenger_create_status 201
record passenger_pending_status PASS
record passenger_tenant_scope_status PASS
record registry_passenger_status PASS

PASSENGER_COUNT_BEFORE_DUPLICATE="$(db_scalar 'SELECT count(*) FROM passageiro;')"
INTRA_ROLE_PAYLOAD="{\"nome\":\"Namespace Duplicate\",\"email\":\"${PASSENGER_EMAIL}\",\"telefone\":\"51990000005\",\"endereco\":\"Rua Namespace\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/intra-role.json" \
  "$INTRA_ROLE_PAYLOAD" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 409 intra_role_conflict_status
assert_generic_conflict_body "$TMP_DIR/intra-role.json" || fail intra_role_conflict_body
assert_equal "$(db_scalar 'SELECT count(*) FROM passageiro;')" \
  "$PASSENGER_COUNT_BEFORE_DUPLICATE" intra_role_conflict_mutation
record intra_role_conflict_status 409
record intra_role_conflict_contract_status PASS

INITIAL_VERIFICATION_ID="$(db_scalar "SELECT coalesce(max(id), 0) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")"
[[ "$INITIAL_VERIFICATION_ID" =~ ^[1-9][0-9]*$ ]] || fail initial_verification_missing
RESEND_PAYLOAD="{\"email\":\"${PASSENGER_EMAIL}\"}"
STATUS="$(http_request POST /auth/email-verifications/resend "$TMP_DIR/resend.json" "$RESEND_PAYLOAD")"
assert_equal "$STATUS" 202 resend_status
ASYNC_OBSERVED=0
for _ in $(seq 1 80); do
  CURRENT_VERIFICATION_ID="$(db_scalar "SELECT coalesce(max(id), 0) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")"
  ACTIVE_VERIFICATION_COUNT="$(db_scalar "SELECT count(*) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND consumed_at IS NULL;")"
  if [[ "$CURRENT_VERIFICATION_ID" -gt "$INITIAL_VERIFICATION_ID" && \
        "$ACTIVE_VERIFICATION_COUNT" == 1 ]]; then
    ASYNC_OBSERVED=1
    break
  fi
  sleep 0.25
done
assert_equal "$ASYNC_OBSERVED" 1 resend_async_not_observed
record resend_status 202
record resend_async_status PASS

VERIFICATION_COUNT_BEFORE_OPAQUE="$(db_scalar 'SELECT count(*) FROM email_verification_token;')"
UNKNOWN_RESEND_PAYLOAD="{\"email\":\"unknown.${EMAIL_SUFFIX}@pending-user.invalid\"}"
MALFORMED_RESEND_PAYLOAD='{"email":"malformed-address"}'
STATUS="$(http_request POST /auth/email-verifications/resend \
  "$TMP_DIR/resend-unknown.json" "$UNKNOWN_RESEND_PAYLOAD")"
assert_equal "$STATUS" 202 unknown_resend_status
STATUS="$(http_request POST /auth/email-verifications/resend \
  "$TMP_DIR/resend-malformed.json" "$MALFORMED_RESEND_PAYLOAD")"
assert_equal "$STATUS" 202 malformed_resend_status
assert_opaque_resend_bodies "$TMP_DIR/resend-unknown.json" \
  "$TMP_DIR/resend-malformed.json" || fail opaque_resend_contract
sleep 1
assert_equal "$(db_scalar 'SELECT count(*) FROM email_verification_token;')" \
  "$VERIFICATION_COUNT_BEFORE_OPAQUE" opaque_resend_mutation
record unknown_resend_status 202
record malformed_resend_status 202
record opaque_resend_contract_status PASS
record opaque_resend_no_mutation_status PASS

DRIVER_COUNT_BEFORE_CONFLICT="$(db_scalar 'SELECT count(*) FROM motorista;')"
CONFLICT_PAYLOAD="{\"nome\":\"Namespace Conflict\",\"email\":\"${PASSENGER_EMAIL}\",\"telefone\":\"51990000003\",\"senha\":\"${DRIVER_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/motoristas "$TMP_DIR/conflict.json" \
  "$CONFLICT_PAYLOAD" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 409 cross_role_conflict_status
assert_generic_conflict_body "$TMP_DIR/conflict.json" || fail cross_role_conflict_body
assert_equal "$(db_scalar 'SELECT count(*) FROM motorista;')" \
  "$DRIVER_COUNT_BEFORE_CONFLICT" cross_role_conflict_mutation
record cross_role_conflict_status 409
record cross_role_conflict_contract_status PASS

CHANGE_PAYLOAD="{\"email\":\"${PASSENGER_EMAIL}\",\"senha\":\"${PASSENGER_PASSWORD}\",\"newEmail\":\"${RELEASED_EMAIL}\"}"
STATUS="$(http_request POST /auth/email-verifications/change-email "$TMP_DIR/change.json" "$CHANGE_PAYLOAD")"
assert_equal "$STATUS" 200 change_pending_address_status
RELEASED_EMAIL_EXPECTED_FILE="$TMP_DIR/released-email.expected"
printf '%s' "$RELEASED_EMAIL" >"$RELEASED_EMAIL_EXPECTED_FILE"
chmod 600 "$RELEASED_EMAIL_EXPECTED_FILE"
assert_changed_body "$TMP_DIR/change.json" "$RELEASED_EMAIL_EXPECTED_FILE" \
  || fail change_pending_address_body
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE normalized_email = '${PASSENGER_EMAIL}';")" \
  0 registry_old_key_not_released
assert_equal "$(db_scalar "SELECT normalized_email FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  "$RELEASED_EMAIL" registry_new_key_not_exact
assert_equal "$(db_scalar "SELECT email FROM passageiro WHERE id = ${PASSENGER_ID};")" \
  "$RELEASED_EMAIL" passenger_source_not_moved
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  1 registry_owner_cardinality_after_move
record change_pending_address_status 200
record registry_move_status PASS

STATUS="$(http_request DELETE "/passageiros/${PASSENGER_ID}" "$TMP_DIR/delete.json" "" \
  "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 204 passenger_delete_status
[[ ! -s "$TMP_DIR/delete.json" ]] || fail passenger_delete_body
assert_equal "$(db_scalar "SELECT count(*) FROM passageiro WHERE id = ${PASSENGER_ID};")" \
  0 passenger_delete_source
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  0 passenger_delete_registry
record passenger_delete_status 204
record namespace_release_status PASS

RELEASED_DRIVER_PAYLOAD="{\"nome\":\"Namespace Released Driver\",\"email\":\"${RELEASED_EMAIL}\",\"telefone\":\"51990000004\",\"senha\":\"${DRIVER_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/motoristas "$TMP_DIR/driver.json" \
  "$RELEASED_DRIVER_PAYLOAD" "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 201 released_driver_create_status
assert_pending_account_body "$TMP_DIR/driver.json" || fail released_driver_pending_body
DRIVER_ID="$(json_get "$TMP_DIR/driver.json" id)"
assert_equal "$(db_scalar "SELECT normalized_email FROM login_identity WHERE user_role = 'DRIVER' AND user_id = ${DRIVER_ID};")" \
  "$RELEASED_EMAIL" released_driver_registry_exact
record released_driver_create_status 201
record registry_driver_status PASS

SOURCE_COUNT="$(db_scalar "WITH accounts(user_role, user_id, address) AS (SELECT 'COMPANY_ADMIN', id, email FROM company_admin UNION ALL SELECT 'PASSENGER', id, email FROM passageiro UNION ALL SELECT 'GUARDIAN', id, email FROM guardian UNION ALL SELECT 'DRIVER', id, email FROM motorista) SELECT count(*) FROM accounts;")"
IDENTITY_COUNT="$(db_scalar 'SELECT count(*) FROM login_identity;')"
MISSING_COUNT="$(db_scalar "WITH accounts(user_role, user_id, address) AS (SELECT 'COMPANY_ADMIN', id, email FROM company_admin UNION ALL SELECT 'PASSENGER', id, email FROM passageiro UNION ALL SELECT 'GUARDIAN', id, email FROM guardian UNION ALL SELECT 'DRIVER', id, email FROM motorista) SELECT count(*) FROM accounts source LEFT JOIN login_identity registry ON registry.normalized_email = source.address AND registry.user_role = source.user_role AND registry.user_id = source.user_id WHERE registry.normalized_email IS NULL;")"
ORPHAN_COUNT="$(db_scalar "WITH accounts(user_role, user_id, address) AS (SELECT 'COMPANY_ADMIN', id, email FROM company_admin UNION ALL SELECT 'PASSENGER', id, email FROM passageiro UNION ALL SELECT 'GUARDIAN', id, email FROM guardian UNION ALL SELECT 'DRIVER', id, email FROM motorista) SELECT count(*) FROM login_identity registry LEFT JOIN accounts source ON source.address = registry.normalized_email AND source.user_role = registry.user_role AND source.user_id = registry.user_id WHERE source.user_id IS NULL;")"
ACTIVE_DEVICE_MISMATCH_COUNT="$(db_scalar "SELECT count(*) FROM device_token device LEFT JOIN login_identity registry ON registry.user_role = device.user_role AND registry.user_id = device.user_id AND registry.normalized_email = device.user_email WHERE device.active = TRUE AND registry.normalized_email IS NULL;")"
assert_equal "$SOURCE_COUNT" "$IDENTITY_COUNT" source_identity_count_mismatch
assert_equal "$MISSING_COUNT" 0 missing_identity_count
assert_equal "$ORPHAN_COUNT" 0 orphan_identity_count
assert_equal "$ACTIVE_DEVICE_MISMATCH_COUNT" 0 active_device_owner_mismatch_count
record source_count "$SOURCE_COUNT"
record identity_count "$IDENTITY_COUNT"
record missing_count 0
record orphan_count 0
record active_device_mismatch_count 0

SENSITIVE_PATTERNS_FILE="$TMP_DIR/sensitive-patterns.txt"
: >"$SENSITIVE_PATTERNS_FILE"
chmod 600 "$SENSITIVE_PATTERNS_FILE"
for sensitive_value in \
  "$ADMIN_EMAIL" "$PASSENGER_EMAIL" "$RELEASED_EMAIL" \
  "$GUARDIAN_EMAIL" "$GUARDIAN_MOVED_EMAIL" \
  "$DB_PASSWORD" "$ADMIN_PASSWORD" "$PASSENGER_PASSWORD" "$DRIVER_PASSWORD" \
  "$GUARDIAN_PASSWORD" "$DEVICE_TOKEN" "$DELETE_DEVICE_TOKEN" \
  "$DEVICE_TOKEN_HASH" "$DELETE_DEVICE_TOKEN_HASH" \
  "$JWT_SECRET" "$ACCESS_CREDENTIAL" "$REFRESH_CREDENTIAL" \
  "$ROTATED_ACCESS_CREDENTIAL" "$GUARDIAN_ACCESS_CREDENTIAL" \
  "$GUARDIAN_REFRESH_CREDENTIAL" "$GUARDIAN_MOVED_ACCESS_CREDENTIAL" \
  "$GUARDIAN_MOVED_REFRESH_CREDENTIAL"; do
  printf '%s\n' "$sensitive_value" >>"$SENSITIVE_PATTERNS_FILE"
done
for runtime_log in \
  "$TMP_DIR/build.log" "$TMP_DIR/application.log" \
  "$TMP_DIR/backend-gate.log" "$TMP_DIR/db.stderr" \
  "$TMP_DIR/docker-run.stderr"; do
  if grep -Fq --file="$SENSITIVE_PATTERNS_FILE" "$runtime_log"; then
    fail runtime_log_sensitive_value
  fi
done
if grep -Fq '[EMAIL][DEV]' "$TMP_DIR/application.log"; then
  fail application_log_mail_body_marker
fi
if grep -Eq 'token=[A-Za-z0-9._~-]{16,}' "$TMP_DIR/application.log"; then
  fail application_log_raw_credential_marker
fi
record application_log_sanitization_status PASS

assert_runtime_unchanged final
JAR_SHA256_AFTER="$(sha256_file "$RUNTIME_JAR_PATH")"
assert_equal "$JAR_SHA256_AFTER" "$JAR_SHA256" jar_changed_during_smoke
assert_equal "$(git_safe rev-parse HEAD)" "$BACKEND_SHA" backend_sha_changed_during_smoke
assert_equal "$(git_safe rev-parse 'HEAD^{tree}')" "$BACKEND_TREE" \
  backend_tree_changed_during_smoke
assert_no_hidden_index_flags "$ROOT_DIR" backend_index_flags_hidden_after
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_changed_during_smoke
assert_equal "$(git_safe hash-object --no-filters "$SCRIPT_PATH")" "$RUNNER_GIT_BLOB" \
  runner_changed_during_smoke
record jar_sha256_after "$JAR_SHA256_AFTER"
record final_backend_sha "$BACKEND_SHA"
record final_backend_tree "$BACKEND_TREE"
record same_jar_status PASS
record repository_invariant_status PASS
MAIN_RESULT=PASS
