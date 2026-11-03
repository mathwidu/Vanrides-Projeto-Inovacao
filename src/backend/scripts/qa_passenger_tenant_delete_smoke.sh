#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
export LC_ALL=C
export LANG=C
export PYTHONUTF8=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/qa_passenger_tenant_delete_smoke.sh"
EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"
REQUIRE_EXPECTED_BACKEND_SHA="${VANRIDES_REQUIRE_EXPECTED_BACKEND_SHA:-false}"
POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"
RUN_ID="passenger-delete-$(date -u +%Y%m%dT%H%M%SZ)-$$"
EMAIL_SUFFIX="$(printf '%s' "$RUN_ID" | tr '[:upper:]' '[:lower:]')"
TMP_BASE="${TMPDIR:-/tmp}"
TMP_DIR="$(mktemp -d "$TMP_BASE/vanrides-passenger-delete.XXXXXX")"
SUMMARY_FILE="$TMP_DIR/summary.env"
EVIDENCE_BASE="$ROOT_DIR/output/qa-evidence"
EVIDENCE_DIR="$EVIDENCE_BASE/$RUN_ID"
MANIFEST_PATH="$EVIDENCE_DIR/manifest.env"
MANIFEST_RELATIVE="output/qa-evidence/$RUN_ID/manifest.env"
CONTAINER_NAME="vanrides-$(printf '%s' "$RUN_ID" | tr '[:upper:]' '[:lower:]')"
CONTAINER_LABEL_KEY="com.vanrides.qa.passenger-delete.run-token"
CONTAINER_RUN_TOKEN=""
CONTAINER_ID=""
CONTAINER_ATTEMPTED=0
APP_PID=""
MAIN_RESULT=""
FAILED_CHECK=""

for log_file in build.log application.log db.stderr docker.stderr; do
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
  [[ -n "$FAILED_CHECK" ]] || FAILED_CHECK="$1"
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
  local hidden_entries
  hidden_entries="$(git_safe ls-files -v -- \
    | awk 'substr($0, 1, 1) == "S" || substr($0, 1, 1) ~ /[a-z]/ { print }')"
  [[ -z "$hidden_entries" ]] || fail "$1"
}

free_port() {
  python3 - <<'PY'
import socket

with socket.socket() as sock:
    sock.bind(("127.0.0.1", 0))
    print(sock.getsockname()[1])
PY
}

stop_app() {
  if [[ -n "$APP_PID" ]] && kill -0 "$APP_PID" >/dev/null 2>&1; then
    kill "$APP_PID" >/dev/null 2>&1 || true
    for _ in $(seq 1 20); do
      kill -0 "$APP_PID" >/dev/null 2>&1 || break
      sleep 0.25
    done
    if kill -0 "$APP_PID" >/dev/null 2>&1; then
      kill -KILL "$APP_PID" >/dev/null 2>&1 || true
    fi
    wait "$APP_PID" >/dev/null 2>&1 || true
  fi
  APP_PID=""
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

cleanup() {
  local original_status=$?
  local cleanup_status=PASS
  local smoke_result=FAIL
  local final_status="$original_status"
  local owned_id="$CONTAINER_ID"
  local owner_value=""
  local failed_check="${FAILED_CHECK:-unexpected_exit}"
  local summary_body=""
  local manifest_body=""

  trap - EXIT INT TERM
  set +e

  stop_app
  if [[ "$CONTAINER_ATTEMPTED" == 1 ]]; then
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
      cleanup_status=FAIL
    else
      if [[ -z "$owned_id" ]] && docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        owned_id="$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME" 2>/dev/null)"
      fi
      if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
        owner_value="$(docker inspect --format \
          '{{ index .Config.Labels "com.vanrides.qa.passenger-delete.run-token" }}' \
          "$owned_id" 2>/dev/null)"
        if [[ "$owned_id" =~ ^[0-9a-f]{64}$ && "$owner_value" == "$CONTAINER_RUN_TOKEN" ]]; then
          docker rm --force "$owned_id" >/dev/null 2>&1 || cleanup_status=FAIL
        else
          cleanup_status=FAIL
        fi
      fi
      if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
      # A racing replacement is never removed by name.
      if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
    fi
  fi

  record build_log_sha256 "$(sha256_file "$TMP_DIR/build.log")" || cleanup_status=FAIL
  record application_log_sha256 "$(sha256_file "$TMP_DIR/application.log")" || cleanup_status=FAIL
  record db_log_sha256 "$(sha256_file "$TMP_DIR/db.stderr")" || cleanup_status=FAIL
  record docker_log_sha256 "$(sha256_file "$TMP_DIR/docker.stderr")" || cleanup_status=FAIL

  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS && "$cleanup_status" == PASS ]]; then
    failed_check=none
    smoke_result=PASS
  else
    final_status=1
  fi
  record failed_check "$failed_check" || cleanup_status=FAIL
  summary_body="$(<"$SUMMARY_FILE")"
  manifest_body="${summary_body}"$'\n'"smoke_result=${smoke_result}"$'\n'\
"failed_check_sha256=$(sha256_text "$failed_check")"$'\n'\
"cleanup_status=${cleanup_status}"$'\n'

  if ! ensure_evidence_base || ! persist_manifest_create_only "$manifest_body"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
  fi

  case "$TMP_DIR" in
    "$TMP_BASE"/vanrides-passenger-delete.*) rm -rf -- "$TMP_DIR" || final_status=1 ;;
    *) final_status=1 ;;
  esac

  printf 'run_id=%s\n' "$RUN_ID"
  printf 'manifest=%s\n' "$MANIFEST_RELATIVE"
  printf 'smoke_result=%s\n' "$smoke_result"
  printf 'cleanup=%s\n' "$cleanup_status"
  exit "$final_status"
}

trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM

json_get() {
  python3 - "$1" "$2" <<'PY'
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

assert_passenger_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload.get("id"), int):
    raise SystemExit("passenger identifier missing")
if payload.get("emailVerificado") is not False:
    raise SystemExit("fixture passenger must have pending email")
if "senha" in payload:
    raise SystemExit("passenger response exposes password material")
PY
}

http_request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local payload="${4:-}"
  local login_name="${5:-}"
  local login_password="${6:-}"
  local config_file="${output_file}.curl-config"
  local response_status=""
  local curl_status=0
  local -a args=(
    --noproxy '*' --silent --show-error --http1.1
    --connect-timeout 5 --max-time 30 --request "$method"
    --dump-header "${output_file}.headers"
    --output "$output_file" --write-out '%{http_code}'
    --header 'Content-Type: application/json'
  )

  : >"$config_file"
  chmod 600 "$config_file"
  if [[ -n "$login_name" ]]; then
    [[ "$login_name" =~ ^[a-z0-9@._+-]+$ ]] || return 1
    [[ "$login_password" =~ ^[0-9a-f]{32}$ ]] || return 1
    printf 'user = "%s:%s"\n' "$login_name" "$login_password" >"$config_file"
  fi

  set +e
  if [[ -n "$payload" ]]; then
    response_status="$(printf '%s' "$payload" \
      | curl --disable --config "$config_file" "${args[@]}" \
          --data-binary @- "${BASE_URL}${path}")"
    curl_status=$?
  else
    response_status="$(curl --disable --config "$config_file" "${args[@]}" \
      "${BASE_URL}${path}")"
    curl_status=$?
  fi
  set -e
  rm -f -- "$config_file"
  [[ "$curl_status" -eq 0 ]] || return "$curl_status"
  printf '%s' "$response_status"
}

header_value() {
  local file="$1"
  local target="$2"
  awk -F: -v target="$target" '
    tolower($1) == target {
      sub(/^[^:]*:[[:space:]]*/, "")
      sub(/\r$/, "")
      print
      exit
    }
  ' "$file"
}

db_scalar() {
  local sql="$1"
  printf '%s\n' "$sql" | docker exec --interactive "$CONTAINER_ID" \
    psql --username vanrides --dbname vanrides --tuples-only --no-align \
      --set ON_ERROR_STOP=1 --file - 2>>"$TMP_DIR/db.stderr"
}

db_exec() {
  local sql="$1"
  printf '%s\n' "$sql" | docker exec --interactive "$CONTAINER_ID" \
    psql --username vanrides --dbname vanrides --quiet \
      --set ON_ERROR_STOP=1 --file - >>"$TMP_DIR/db.stdout" \
      2>>"$TMP_DIR/db.stderr"
}

passenger_artifact_snapshot() {
  local passenger_id="$1"
  db_scalar "SELECT concat_ws(':',
    (SELECT md5(coalesce(string_agg(source::text, '|' ORDER BY source.id), ''))
       FROM passageiro source WHERE source.id = ${passenger_id}),
    (SELECT md5(coalesce(string_agg(source::text, '|' ORDER BY source.normalized_email), ''))
       FROM login_identity source
      WHERE source.user_role = 'PASSENGER' AND source.user_id = ${passenger_id}),
    (SELECT md5(coalesce(string_agg(source::text, '|' ORDER BY source.id), ''))
       FROM refresh_token source
      WHERE source.user_role = 'PASSENGER' AND source.user_id = ${passenger_id}),
    (SELECT md5(coalesce(string_agg(source::text, '|' ORDER BY source.id), ''))
       FROM email_verification_token source
      WHERE source.user_role = 'PASSENGER' AND source.user_id = ${passenger_id}),
    (SELECT md5(coalesce(string_agg(source::text, '|' ORDER BY source.id), ''))
       FROM device_token source
      WHERE source.user_role = 'PASSENGER' AND source.user_id = ${passenger_id})
  );"
}

run_clean_build() (
  local variable
  while IFS= read -r variable; do
    case "$variable" in
      MAVEN_*|MVNW_*) unset "$variable" ;;
    esac
  done < <(builtin compgen -e)
  export MAVEN_SKIP_RC=1
  export MAVEN_USER_HOME="$HOME/.m2"
  ./mvnw -q \
    -Duser.home="$MAVEN_ISOLATED_HOME" \
    -Dmaven.repo.local="$MAVEN_REPOSITORY" \
    -DskipTests \
    clean package
)

for command in awk basename chmod cmp cp curl date docker env find git java kill \
  mkdir mktemp openssl python3 rm seq shasum sleep tr; do
  command -v "$command" >/dev/null 2>&1 || fail "missing_command_${command}"
done

unset HTTP_PROXY HTTPS_PROXY ALL_PROXY FTP_PROXY \
  http_proxy https_proxy all_proxy ftp_proxy
export NO_PROXY='*'
export no_proxy='*'
export CURL_HOME="$TMP_DIR/curl-home"
export XDG_CONFIG_HOME="$TMP_DIR/xdg-config"
mkdir -p "$CURL_HOME" "$XDG_CONFIG_HOME" "$TMP_DIR/home"
: >"$CURL_HOME/.curlrc"
: >"$XDG_CONFIG_HOME/curlrc"
: >"$TMP_DIR/db.stdout"

for variable in JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS; do
  [[ -z "${!variable:-}" ]] || fail "${variable}_override_not_allowed"
done
[[ -n "${HOME:-}" && "$HOME" == /* && -d "$HOME" ]] \
  || fail invalid_build_home
MAVEN_ISOLATED_HOME="$TMP_DIR/maven-user-home"
MAVEN_REPOSITORY="$HOME/.m2/repository"
mkdir -p "$MAVEN_ISOLATED_HOME"
[[ -d "$MAVEN_REPOSITORY" && ! -L "$MAVEN_REPOSITORY" ]] \
  || fail invalid_maven_repository

[[ -z "${DOCKER_HOST:-}" ]] || fail docker_host_override_not_allowed
[[ -z "${DOCKER_CONTEXT:-}" ]] || fail docker_context_override_not_allowed
DOCKER_CONTEXT_NAME="$(docker context show 2>/dev/null)" || fail docker_context_unavailable
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

case "$REQUIRE_EXPECTED_BACKEND_SHA" in
  true|false) ;;
  *) fail invalid_expected_backend_sha_requirement ;;
esac
if [[ "$REQUIRE_EXPECTED_BACKEND_SHA" == true && -z "$EXPECTED_BACKEND_SHA" ]]; then
  fail expected_backend_sha_required_for_closure
fi
if [[ -n "$EXPECTED_BACKEND_SHA" ]]; then
  [[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] || fail invalid_expected_backend_sha
fi
[[ "$POSTGRES_IMAGE" =~ ^[A-Za-z0-9._/@:+-]+$ ]] || fail invalid_postgres_image
[[ ! -e "$EVIDENCE_DIR" ]] || fail evidence_run_id_collision

cd "$ROOT_DIR"
REPOSITORY_DIR="$(cd "$(git_safe rev-parse --show-toplevel)" && pwd -P)"
MODULE_DIR="$(cd "$ROOT_DIR" && pwd -P)"
[[ "$MODULE_DIR" == "$REPOSITORY_DIR" || "$MODULE_DIR" == "$REPOSITORY_DIR/src/backend" ]] \
  || fail backend_repo_root
MODULE_GIT_PREFIX="$(git_safe rev-parse --show-prefix)"
BACKEND_SHA="$(git_safe rev-parse HEAD)"
BACKEND_TREE="$(git_safe rev-parse 'HEAD^{tree}')"
if [[ -n "$EXPECTED_BACKEND_SHA" ]]; then
  assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha
fi
assert_no_hidden_index_flags backend_index_flags_hidden_before
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_not_clean
RUNNER_GIT_BLOB="$(git_safe rev-parse \
  "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_passenger_tenant_delete_smoke.sh")" \
  || fail runner_not_versioned
RUNNER_WORKTREE_BLOB="$(git_safe hash-object --no-filters "$SCRIPT_PATH")"
assert_equal "$RUNNER_WORKTREE_BLOB" "$RUNNER_GIT_BLOB" runner_blob_mismatch

record run_id "$RUN_ID"
record expected_backend_sha "${EXPECTED_BACKEND_SHA:-not_configured}"
record expected_sha_required "$REQUIRE_EXPECTED_BACKEND_SHA"
record backend_sha "$BACKEND_SHA"
record backend_tree "$BACKEND_TREE"
record runner_git_blob "$RUNNER_GIT_BLOB"
record runner_sha256 "$(sha256_file "$SCRIPT_PATH")"
record docker_endpoint_transport "$DOCKER_ENDPOINT_TRANSPORT"
record inherited_java_options NONE
record build_user_home_isolated PASS
record build_maven_repository_explicit PASS

if ! run_clean_build >"$TMP_DIR/build.log" 2>&1; then
  fail backend_clean_package
fi
BUILT_JAR_PATH=""
JAR_COUNT=0
while IFS= read -r candidate; do
  [[ "$candidate" == *.jar.original ]] && continue
  BUILT_JAR_PATH="$candidate"
  JAR_COUNT=$((JAR_COUNT + 1))
done < <(find "$ROOT_DIR/target" -maxdepth 1 -type f -name '*.jar' -print)
assert_equal "$JAR_COUNT" 1 jar_candidate_count
[[ -n "$BUILT_JAR_PATH" && -f "$BUILT_JAR_PATH" ]] || fail jar_not_found
RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"
cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH" || fail runtime_jar_copy
JAR_SHA256="$(sha256_file "$RUNTIME_JAR_PATH")"
assert_equal "$JAR_SHA256" "$(sha256_file "$BUILT_JAR_PATH")" runtime_jar_copy_hash
record jar_sha256 "$JAR_SHA256"

DB_PASSWORD="$(openssl rand -hex 24)"
ADMIN_A_PASSWORD="$(openssl rand -hex 16)"
ADMIN_B_PASSWORD="$(openssl rand -hex 16)"
PASSENGER_PASSWORD="$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 48)"
REFRESH_TOKEN_HASH="$(openssl rand -hex 32)"
DEVICE_TOKEN_VALUE="$(openssl rand -hex 32)"
DEVICE_TOKEN_HASH="$(sha256_text "$DEVICE_TOKEN_VALUE")"
APP_PORT="$(free_port)"

docker inspect "$CONTAINER_NAME" >/dev/null 2>&1 \
  && fail postgres_container_name_collision
CONTAINER_RUN_TOKEN="$(openssl rand -hex 16)"
POSTGRES_ENV_FILE="$TMP_DIR/postgres.env"
CONTAINER_LABEL_FILE="$TMP_DIR/container.labels"
printf 'POSTGRES_DB=vanrides\nPOSTGRES_USER=vanrides\nPOSTGRES_PASSWORD=%s\n' \
  "$DB_PASSWORD" >"$POSTGRES_ENV_FILE"
printf '%s=%s\n' "$CONTAINER_LABEL_KEY" "$CONTAINER_RUN_TOKEN" \
  >"$CONTAINER_LABEL_FILE"
chmod 600 "$POSTGRES_ENV_FILE" "$CONTAINER_LABEL_FILE"
CONTAINER_ATTEMPTED=1
docker run --detach --pull=missing \
  --name "$CONTAINER_NAME" --label-file "$CONTAINER_LABEL_FILE" \
  --env-file "$POSTGRES_ENV_FILE" --publish '127.0.0.1::5432' \
  "$POSTGRES_IMAGE" >"$TMP_DIR/container.id" 2>"$TMP_DIR/docker.stderr" \
  || fail postgres_container_start
CONTAINER_ID="$(<"$TMP_DIR/container.id")"
[[ "$CONTAINER_ID" =~ ^[0-9a-f]{64}$ ]] || fail invalid_postgres_container_id
assert_equal "$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME")" \
  "$CONTAINER_ID" postgres_container_identity
assert_equal "$(docker inspect --format \
  '{{ index .Config.Labels "com.vanrides.qa.passenger-delete.run-token" }}' \
  "$CONTAINER_ID")" "$CONTAINER_RUN_TOKEN" postgres_container_ownership
POSTGRES_IMAGE_ID="$(docker inspect --format '{{.Image}}' "$CONTAINER_ID")"
[[ "$POSTGRES_IMAGE_ID" =~ ^sha256:[0-9a-f]{64}$ ]] \
  || fail invalid_postgres_runtime_image_id
record postgres_image_ref "$POSTGRES_IMAGE"
record postgres_image_id "$POSTGRES_IMAGE_ID"
DB_PORT="$(docker port "$CONTAINER_ID" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
[[ "$DB_PORT" =~ ^[0-9]+$ ]] || fail postgres_port_not_bound
for _ in $(seq 1 60); do
  docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
    >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
  >/dev/null 2>&1 || fail postgres_not_ready
POSTGRES_VERSION_NUM="$(db_scalar 'SHOW server_version_num;')"
[[ "$POSTGRES_VERSION_NUM" =~ ^16[0-9]{4}$ ]] || fail postgres_major_not_16
record postgres_major 16

BASE_URL="http://127.0.0.1:${APP_PORT}"
JAVA_BIN="$(command -v java)"
env -i \
  HOME="$TMP_DIR/home" TMPDIR="$TMP_DIR" LC_ALL=C LANG=C \
  SPRING_PROFILES_ACTIVE=local \
  SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/vanrides" \
  SPRING_DATASOURCE_USERNAME=vanrides \
  SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
  SPRING_JPA_SHOW_SQL=false SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
  SERVER_PORT="$APP_PORT" \
  VANRIDES_EMAIL_PROVIDER=log \
  VANRIDES_EMAIL_VERIFICATION_RESEND_COOLDOWN_MINUTES=0 \
  VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL="${BASE_URL}/auth/email-verifications/confirm" \
  VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=qa-admin.invalid \
  VANRIDES_AUTH_BASIC_ENABLED=true VANRIDES_RATE_LIMIT_ENABLED=false \
  VANRIDES_ROUTING_PROVIDER=static VANRIDES_PUSH_PROVIDER=none \
  VANRIDES_MAPS_PLATFORM_ENABLED=false VANRIDES_JWT_SECRET="$JWT_SECRET" \
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
assert_equal "$FLYWAY_LATEST_VERSION" 25 flyway_latest_version
record flyway_latest_version "$FLYWAY_LATEST_VERSION"

ADMIN_A_EMAIL="admin-a.${EMAIL_SUFFIX}@qa-admin.invalid"
ADMIN_B_EMAIL="admin-b.${EMAIL_SUFFIX}@qa-admin.invalid"
PASSENGER_EMAIL="passenger.${EMAIL_SUFFIX}@pending-user.invalid"
ADMIN_A_PAYLOAD="{\"companyName\":\"Delete Tenant A ${RUN_ID}\",\"nome\":\"Delete Admin A\",\"email\":\"${ADMIN_A_EMAIL}\",\"telefone\":\"51990000001\",\"senha\":\"${ADMIN_A_PASSWORD}\"}"
ADMIN_B_PAYLOAD="{\"companyName\":\"Delete Tenant B ${RUN_ID}\",\"nome\":\"Delete Admin B\",\"email\":\"${ADMIN_B_EMAIL}\",\"telefone\":\"51990000002\",\"senha\":\"${ADMIN_B_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/admin-a.json" "$ADMIN_A_PAYLOAD")"
assert_equal "$STATUS" 201 admin_a_bootstrap_status
assert_bootstrap_body "$TMP_DIR/admin-a.json" || fail admin_a_bootstrap_body
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/admin-b.json" "$ADMIN_B_PAYLOAD")"
assert_equal "$STATUS" 201 admin_b_bootstrap_status
assert_bootstrap_body "$TMP_DIR/admin-b.json" || fail admin_b_bootstrap_body
COMPANY_A_ID="$(json_get "$TMP_DIR/admin-a.json" companyId)"
COMPANY_B_ID="$(json_get "$TMP_DIR/admin-b.json" companyId)"
[[ "$COMPANY_A_ID" =~ ^[1-9][0-9]*$ ]] || fail company_a_id_invalid
[[ "$COMPANY_B_ID" =~ ^[1-9][0-9]*$ ]] || fail company_b_id_invalid
[[ "$COMPANY_A_ID" != "$COMPANY_B_ID" ]] || fail tenant_fixture_not_distinct
record two_tenant_fixture PASS

PASSENGER_PAYLOAD="{\"nome\":\"Delete Passenger\",\"email\":\"${PASSENGER_EMAIL}\",\"telefone\":\"51990000003\",\"endereco\":\"Rua Delete\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/passenger.json" \
  "$PASSENGER_PAYLOAD" "$ADMIN_A_EMAIL" "$ADMIN_A_PASSWORD")"
assert_equal "$STATUS" 201 passenger_create_status
assert_passenger_body "$TMP_DIR/passenger.json" || fail passenger_create_body
PASSENGER_ID="$(json_get "$TMP_DIR/passenger.json" id)"
[[ "$PASSENGER_ID" =~ ^[1-9][0-9]*$ ]] || fail passenger_id_invalid
assert_equal "$(db_scalar "SELECT company_id FROM passageiro WHERE id = ${PASSENGER_ID};")" \
  "$COMPANY_A_ID" passenger_tenant_fixture
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND consumed_at IS NULL;")" \
  1 verification_fixture_active

db_exec "INSERT INTO refresh_token (
  token_hash, user_id, user_role, user_email, company_id, expires_at,
  revoked, created_at, updated_at
) VALUES (
  '${REFRESH_TOKEN_HASH}', ${PASSENGER_ID}, 'PASSENGER', '${PASSENGER_EMAIL}',
  ${COMPANY_A_ID}, CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE,
  CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);" || fail refresh_fixture_insert
db_exec "INSERT INTO device_token (
  token_hash, token_value, platform, user_id, user_role, user_email, company_id,
  active, last_registered_at, invalidated_at, created_at, updated_at
) VALUES (
  '${DEVICE_TOKEN_HASH}', '${DEVICE_TOKEN_VALUE}', 'ANDROID', ${PASSENGER_ID},
  'PASSENGER', '${PASSENGER_EMAIL}', ${COMPANY_A_ID}, TRUE,
  CURRENT_TIMESTAMP, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
);" || fail device_fixture_insert

assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND normalized_email = '${PASSENGER_EMAIL}';")" \
  1 identity_fixture_exact
assert_equal "$(db_scalar "SELECT count(*) FROM refresh_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND revoked = FALSE;")" \
  1 refresh_fixture_active
assert_equal "$(db_scalar "SELECT count(*) FROM device_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND active = TRUE AND invalidated_at IS NULL;")" \
  1 device_fixture_active
INITIAL_SNAPSHOT="$(passenger_artifact_snapshot "$PASSENGER_ID")"
MISSING_PASSENGER_ID=$((PASSENGER_ID + 1000000))

STATUS="$(http_request DELETE "/passageiros/${PASSENGER_ID}" \
  "$TMP_DIR/cross-tenant.json" "" "$ADMIN_B_EMAIL" "$ADMIN_B_PASSWORD")"
assert_equal "$STATUS" 404 cross_tenant_delete_status
assert_equal "$(passenger_artifact_snapshot "$PASSENGER_ID")" \
  "$INITIAL_SNAPSHOT" cross_tenant_no_mutation

STATUS="$(http_request DELETE "/passageiros/${MISSING_PASSENGER_ID}" \
  "$TMP_DIR/missing.json" "" "$ADMIN_B_EMAIL" "$ADMIN_B_PASSWORD")"
assert_equal "$STATUS" 404 missing_delete_status
assert_equal "$(passenger_artifact_snapshot "$PASSENGER_ID")" \
  "$INITIAL_SNAPSHOT" missing_no_mutation
cmp -s "$TMP_DIR/cross-tenant.json" "$TMP_DIR/missing.json" \
  || fail not_found_response_body_distinguishable
NOT_FOUND_BODY_SHA256="$(sha256_file "$TMP_DIR/cross-tenant.json")"
assert_equal "$NOT_FOUND_BODY_SHA256" "$(sha256_file "$TMP_DIR/missing.json")" \
  not_found_response_hash_distinguishable
assert_equal "$(header_value "$TMP_DIR/cross-tenant.json.headers" content-type)" \
  "$(header_value "$TMP_DIR/missing.json.headers" content-type)" \
  not_found_content_type_distinguishable
assert_equal "$(header_value "$TMP_DIR/cross-tenant.json.headers" content-length)" \
  "$(header_value "$TMP_DIR/missing.json.headers" content-length)" \
  not_found_content_length_distinguishable
record cross_tenant_not_found PASS
record missing_not_found PASS
record not_found_indistinguishable PASS
record not_found_no_mutation PASS
record not_found_body_sha256 "$NOT_FOUND_BODY_SHA256"

STATUS="$(http_request DELETE "/passageiros/${PASSENGER_ID}" \
  "$TMP_DIR/authorized.json" "" "$ADMIN_A_EMAIL" "$ADMIN_A_PASSWORD")"
assert_equal "$STATUS" 204 authorized_delete_status
[[ ! -s "$TMP_DIR/authorized.json" ]] || fail authorized_delete_body
assert_equal "$(db_scalar "SELECT count(*) FROM passageiro WHERE id = ${PASSENGER_ID};")" \
  0 passenger_not_removed
assert_equal "$(db_scalar "SELECT count(*) FROM login_identity WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  0 login_identity_not_removed
assert_equal "$(db_scalar "SELECT count(*) FROM refresh_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  1 refresh_artifact_missing
assert_equal "$(db_scalar "SELECT count(*) FROM refresh_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND revoked = TRUE;")" \
  1 refresh_artifact_not_revoked
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  1 verification_artifact_missing
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND consumed_at IS NOT NULL;")" \
  1 verification_artifact_not_consumed
assert_equal "$(db_scalar "SELECT count(*) FROM device_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID};")" \
  1 device_artifact_missing
assert_equal "$(db_scalar "SELECT count(*) FROM device_token WHERE user_role = 'PASSENGER' AND user_id = ${PASSENGER_ID} AND active = FALSE AND invalidated_at IS NOT NULL AND user_email IS NULL;")" \
  1 device_artifact_not_invalidated
record authorized_delete_http PASS
record passenger_removed PASS
record login_identity_removed PASS
record refresh_token_revoked PASS
record email_verification_consumed PASS
record device_token_invalidated PASS
record artifact_transaction_status PASS
# This focused HTTP smoke creates no FK blocker fixture; rollback-by-FK is
# covered by the mandatory PostgreSQL integration lane for this card.
record foreign_key_fixture NOT_USED

kill -0 "$APP_PID" >/dev/null 2>&1 || fail application_not_alive_final
assert_equal "$(sha256_file "$RUNTIME_JAR_PATH")" "$JAR_SHA256" jar_changed_during_smoke
assert_equal "$(git_safe rev-parse HEAD)" "$BACKEND_SHA" backend_sha_changed_during_smoke
assert_equal "$(git_safe rev-parse 'HEAD^{tree}')" "$BACKEND_TREE" backend_tree_changed_during_smoke
assert_equal "$(git_safe hash-object --no-filters "$SCRIPT_PATH")" \
  "$RUNNER_WORKTREE_BLOB" runner_changed_during_smoke
assert_no_hidden_index_flags backend_index_flags_hidden_after
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_changed_during_smoke
record same_jar_runtime PASS
record remote_environment_mutation NONE
MAIN_RESULT=PASS
