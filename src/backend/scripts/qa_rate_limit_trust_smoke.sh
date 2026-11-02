#!/usr/bin/env bash
set -Eeuo pipefail
umask 077
export LC_ALL=C
export LANG=C
export PYTHONUTF8=1
ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/qa_rate_limit_trust_smoke.sh"
LOAD_RUNNER="$ROOT_DIR/scripts/qa_rate_limit_trust_load.py"
EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"
POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"
RUN_ID="rate-limit-$(date -u +%Y%m%dT%H%M%SZ)-$$"
TMP_BASE="${TMPDIR:-/tmp}"
TMP_DIR="$(mktemp -d "$TMP_BASE/vanrides-rate-limit.XXXXXX")"
SUMMARY_FILE="$TMP_DIR/summary.env"
EVIDENCE_DIR="$ROOT_DIR/output/qa-evidence/$RUN_ID"
MANIFEST_PATH="$EVIDENCE_DIR/manifest.env"
CONTAINER_NAME="vanrides-$(printf '%s' "$RUN_ID" | tr '[:upper:]' '[:lower:]')"
CONTAINER_TOKEN=""
CONTAINER_ID=""
CONTAINER_ATTEMPTED=0
CONTAINER_LABEL="com.vanrides.qa.rate-limit.run-token"
APP_PID=""
APP_PHASE=""
APP_PORT=""
MANAGEMENT_PORT=""
MAIN_RESULT=""
FAILED_CHECK=""
for log_file in build.log docker.stderr app-direct.log app-trusted.log; do
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
  [[ "$1" == "$2" ]] || fail "$3"
}
git_safe() {
  command git --no-replace-objects -c core.fsmonitor=false "$@"
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
cleanup() {
  local original_status=$?
  local cleanup_status=PASS
  local smoke_result=FAIL
  local final_status="$original_status"
  local owned_id="$CONTAINER_ID"
  local owner=""
  local failed_check="${FAILED_CHECK:-unexpected_exit}"
  local manifest_body=""
  trap - EXIT INT TERM
  set +e
  stop_app
  if [[ "$CONTAINER_ATTEMPTED" == "1" ]] && docker info >/dev/null 2>&1; then
    if [[ -z "$owned_id" ]] && docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
      owned_id="$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME" 2>/dev/null)"
    fi
    if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
      owner="$(docker inspect --format \
        '{{ index .Config.Labels "com.vanrides.qa.rate-limit.run-token" }}' \
        "$owned_id" 2>/dev/null)"
      if [[ "$owned_id" =~ ^[0-9a-f]{64}$ && "$owner" == "$CONTAINER_TOKEN" ]]; then
        docker rm --force "$owned_id" >/dev/null 2>&1 || cleanup_status=FAIL
      else
        cleanup_status=FAIL
      fi
    fi
    if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
      cleanup_status=FAIL
    fi
    if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
      cleanup_status=FAIL
    fi
  elif [[ "$CONTAINER_ATTEMPTED" == "1" ]]; then
    cleanup_status=FAIL
  fi
  record build_log_sha256 "$(sha256_file "$TMP_DIR/build.log")" || cleanup_status=FAIL
  record direct_log_sha256 "$(sha256_file "$TMP_DIR/app-direct.log")" || cleanup_status=FAIL
  record trusted_log_sha256 "$(sha256_file "$TMP_DIR/app-trusted.log")" || cleanup_status=FAIL
  record docker_log_sha256 "$(sha256_file "$TMP_DIR/docker.stderr")" || cleanup_status=FAIL
  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS && "$cleanup_status" == PASS ]]; then
    failed_check=none
  fi
  record failed_check "$failed_check" || cleanup_status=FAIL
  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS && "$cleanup_status" == PASS ]]; then
    smoke_result=PASS
  else
    final_status=1
  fi
  manifest_body="$(<"$SUMMARY_FILE")"$'\n'"smoke_result=$smoke_result"$'\n'\
"failed_check_sha256=$(sha256_text "$failed_check")"$'\n'\
"cleanup_status=$cleanup_status"$'\n'
  if [[ -e "$EVIDENCE_DIR" ]] || ! mkdir -p "$EVIDENCE_DIR"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
  else
    (set -o noclobber; printf '%s' "$manifest_body" >"$MANIFEST_PATH") \
      || { cleanup_status=FAIL; smoke_result=FAIL; final_status=1; }
    chmod 600 "$MANIFEST_PATH" >/dev/null 2>&1 || final_status=1
  fi
  case "$TMP_DIR" in
    "$TMP_BASE"/vanrides-rate-limit.*) rm -rf -- "$TMP_DIR" || final_status=1 ;;
    *) final_status=1 ;;
  esac
  printf 'run_id=%s\n' "$RUN_ID"
  printf 'manifest=output/qa-evidence/%s/manifest.env\n' "$RUN_ID"
  printf 'smoke_result=%s\n' "$smoke_result"
  printf 'cleanup=%s\n' "$cleanup_status"
  exit "$final_status"
}
trap cleanup EXIT
trap 'exit 130' INT
trap 'exit 143' TERM
json_code() {
  python3 - "$1" <<'PY'
import json
import sys
with open(sys.argv[1], encoding="utf-8") as handle:
    print(json.load(handle).get("code", ""))
PY
}
capture_request() {
  local name="$1"
  local path="$2"
  shift 2
  curl --disable --noproxy '*' --silent --show-error --http1.1 \
    --dump-header "$TMP_DIR/$name.headers" \
    --output "$TMP_DIR/$name.body" \
    --write-out '%{http_code}' \
    --max-time 10 "$@" "http://127.0.0.1:${APP_PORT}${path}"
}
assert_early_error_contract() {
  local name="$1"
  local expected_status="$2"
  local expected_code="$3"
  local expected_request_id="$4"
  local actual_status="$5"
  assert_equal "$actual_status" "$expected_status" "${name}_status"
  assert_equal "$(json_code "$TMP_DIR/$name.body")" "$expected_code" "${name}_code"
  grep -Eiq '^Access-Control-Allow-Origin: https://app\.example\r?$' \
    "$TMP_DIR/$name.headers" || fail "${name}_cors"
  grep -Eiq "^X-Request-Id: ${expected_request_id}\\r?$" \
    "$TMP_DIR/$name.headers" || fail "${name}_request_id"
}
start_app() {
  local phase="$1"
  local trusted_cidrs="$2"
  APP_PHASE="$phase"
  APP_PORT="$(free_port)"
  MANAGEMENT_PORT="$(free_port)"
  local base_url="http://127.0.0.1:${APP_PORT}"
  local log_file="$TMP_DIR/app-${phase}.log"
  env -i \
    PATH="$PATH" HOME="$TMP_DIR/home" TMPDIR="$TMP_DIR" LC_ALL=C LANG=C \
    SPRING_PROFILES_ACTIVE=local \
    SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/vanrides" \
    SPRING_DATASOURCE_USERNAME=vanrides \
    SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD" \
    SPRING_JPA_SHOW_SQL=false SPRING_JPA_HIBERNATE_DDL_AUTO=validate \
    SERVER_PORT="$APP_PORT" VANRIDES_HTTP_MAX_THREADS=200 \
    MANAGEMENT_SERVER_PORT="$MANAGEMENT_PORT" \
    MANAGEMENT_SERVER_ADDRESS=127.0.0.1 \
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,info,metrics \
    VANRIDES_CORS_ALLOWED_ORIGIN_PATTERNS=https://app.example \
    VANRIDES_CORS_ALLOWED_METHODS=GET,POST,OPTIONS \
    VANRIDES_CORS_ALLOWED_HEADERS='*' \
    VANRIDES_AUTH_BASIC_ENABLED=true VANRIDES_JWT_SECRET="$JWT_SECRET" \
    VANRIDES_EMAIL_PROVIDER=log VANRIDES_ROUTING_PROVIDER=static \
    VANRIDES_PUSH_PROVIDER=none VANRIDES_MAPS_PLATFORM_ENABLED=false \
    VANRIDES_RATE_LIMIT_ENABLED=true VANRIDES_RATE_LIMIT_SENSITIVE_LIMIT=2 \
    VANRIDES_RATE_LIMIT_WINDOW_SECONDS=60 \
    VANRIDES_RATE_LIMIT_MAX_ENTRIES_PER_ROUTE=32 \
    VANRIDES_RATE_LIMIT_TRUSTED_PROXY_CIDRS="$trusted_cidrs" \
    VANRIDES_RATE_LIMIT_FORWARDED_MAX_BYTES=1024 \
    VANRIDES_RATE_LIMIT_FORWARDED_MAX_HOPS=10 \
    VANRIDES_REQUEST_BODY_MAX_BYTES=64 \
    VANRIDES_REQUEST_BODY_MAX_CONCURRENT_READS=32 \
    VANRIDES_REQUEST_BODY_RESERVED_REQUEST_THREADS=16 \
    "$JAVA_BIN" -jar "$JAR_PATH" >"$log_file" 2>&1 &
  APP_PID=$!
  local ready=000
  for _ in $(seq 1 120); do
    kill -0 "$APP_PID" >/dev/null 2>&1 || fail "${phase}_application_exited"
    ready="$(curl --disable --noproxy '*' --silent --output /dev/null \
      --write-out '%{http_code}' --max-time 2 "$base_url/motoristas" || true)"
    [[ "$ready" == 200 ]] && break
    sleep 1
  done
  assert_equal "$ready" 200 "${phase}_application_not_ready"
  grep -q 'Started VanridesApplication' "$log_file" || fail "${phase}_started_marker"
}
for command in awk curl date docker find git grep java mkdir mktemp openssl \
  python3 sed seq shasum tr; do
  command -v "$command" >/dev/null 2>&1 || fail "missing_command_${command}"
done
unset HTTP_PROXY HTTPS_PROXY ALL_PROXY FTP_PROXY http_proxy https_proxy all_proxy ftp_proxy
export NO_PROXY='*'
export no_proxy='*'
export GIT_NO_REPLACE_OBJECTS=1
[[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] || fail expected_backend_sha_required
[[ "$POSTGRES_IMAGE" =~ ^[A-Za-z0-9._/@:+-]+$ ]] || fail invalid_postgres_image
[[ -x "$LOAD_RUNNER" ]] || fail load_runner_missing
cd "$ROOT_DIR"
REPOSITORY_DIR="$(cd "$(git_safe rev-parse --show-toplevel)" && pwd -P)"
MODULE_DIR="$(cd "$ROOT_DIR" && pwd -P)"
[[ "$MODULE_DIR" == "$REPOSITORY_DIR" || "$MODULE_DIR" == "$REPOSITORY_DIR/src/backend" ]] \
  || fail backend_repo_root
MODULE_GIT_PREFIX="$(git_safe rev-parse --show-prefix)"
BACKEND_SHA="$(git_safe rev-parse HEAD)"
BACKEND_TREE="$(git_safe rev-parse 'HEAD^{tree}')"
assert_equal "$BACKEND_SHA" "$EXPECTED_BACKEND_SHA" backend_sha
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_not_clean
RUNNER_BLOB="$(git_safe rev-parse "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_rate_limit_trust_smoke.sh")" \
  || fail runner_not_versioned
assert_equal "$(git_safe hash-object --no-filters "$SCRIPT_PATH")" "$RUNNER_BLOB" runner_blob
record run_id "$RUN_ID"
record backend_sha "$BACKEND_SHA"
record backend_tree "$BACKEND_TREE"
record runner_git_blob "$RUNNER_BLOB"
./mvnw -q clean package -DskipTests >"$TMP_DIR/build.log" 2>&1 || fail backend_build
JAR_PATH=""
JAR_COUNT=0
while IFS= read -r candidate; do
  [[ "$candidate" == *.jar.original ]] && continue
  JAR_PATH="$candidate"
  JAR_COUNT=$((JAR_COUNT + 1))
done < <(find "$ROOT_DIR/target" -maxdepth 1 -type f -name '*.jar' -print)
assert_equal "$JAR_COUNT" 1 jar_candidate_count
[[ -n "$JAR_PATH" && -f "$JAR_PATH" ]] || fail jar_not_found
cp "$JAR_PATH" "$TMP_DIR/runtime.jar" || fail runtime_jar_copy
JAR_PATH="$TMP_DIR/runtime.jar"
JAR_SHA256="$(sha256_file "$JAR_PATH")"
record jar_sha256 "$JAR_SHA256"
docker info >/dev/null 2>&1 || fail docker_unavailable
[[ ! -e "$EVIDENCE_DIR" ]] || fail evidence_collision
docker inspect "$CONTAINER_NAME" >/dev/null 2>&1 && fail container_name_collision
CONTAINER_TOKEN="$(openssl rand -hex 16)"
DB_PASSWORD="$(openssl rand -hex 24)"
JWT_SECRET="$(openssl rand -hex 48)"
CONTAINER_ATTEMPTED=1
docker run --detach --pull=missing \
  --name "$CONTAINER_NAME" --label "${CONTAINER_LABEL}=${CONTAINER_TOKEN}" \
  --env POSTGRES_DB=vanrides --env POSTGRES_USER=vanrides \
  --env "POSTGRES_PASSWORD=${DB_PASSWORD}" --publish '127.0.0.1::5432' \
  "$POSTGRES_IMAGE" >"$TMP_DIR/container.id" 2>"$TMP_DIR/docker.stderr" \
  || fail postgres_container_start
CONTAINER_ID="$(<"$TMP_DIR/container.id")"
[[ "$CONTAINER_ID" =~ ^[0-9a-f]{64}$ ]] || fail invalid_container_id
DB_PORT="$(docker port "$CONTAINER_ID" 5432/tcp | awk -F: 'NR == 1 {print $NF}')"
[[ -n "$DB_PORT" ]] || fail postgres_port_missing
for _ in $(seq 1 60); do
  docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
    >/dev/null 2>&1 && break
  sleep 1
done
docker exec "$CONTAINER_ID" pg_isready --username vanrides --dbname vanrides \
  >/dev/null 2>&1 || fail postgres_not_ready
JAVA_BIN="$(command -v java)"
mkdir -p "$TMP_DIR/home"
printf '%080d' 0 >"$TMP_DIR/oversized.json"
start_app direct ""
for _ in $(seq 1 5); do
  status="$(capture_request preflight /login -X OPTIONS \
    -H 'Origin: https://app.example' -H 'Access-Control-Request-Method: POST')"
  assert_equal "$status" 200 preflight_status
done
status="$(capture_request unsupported-form /driver-onboarding/requests -X POST \
  -H 'Origin: https://app.example' -H 'X-Request-Id: smoke-unsupported-form' \
  -H 'Content-Type: application/x-www-form-urlencoded' --data 'a=b')"
assert_early_error_contract unsupported-form 415 UNSUPPORTED_BODY_MEDIA_TYPE \
  smoke-unsupported-form "$status"
for index in 1 2; do
  status="$(capture_request "unread-prime-${index}" /passageiros -X POST \
    -H 'Origin: https://app.example' \
    -H "X-Request-Id: smoke-unread-prime-${index}" \
    -H 'Content-Type: application/json' --data '{}')"
  assert_equal "$status" 400 "unread_prime_${index}_status"
done
python3 - "$APP_PORT" <<'PY' || fail unread_body_socket_close
import socket
import sys

port = int(sys.argv[1])
cases = (
    ("Content-Length: 5\r\n", b"x"),
    ("Transfer-Encoding: chunked\r\n", b"5\r\nx"),
)
for index, (body_header, incomplete_body) in enumerate(cases):
    with socket.create_connection(("127.0.0.1", port), timeout=2) as client:
        client.settimeout(3)
        request_id = f"smoke-unread-{index}"
        headers = (
            "POST /passageiros HTTP/1.1\r\n"
            "Host: 127.0.0.1\r\n"
            "Origin: https://app.example\r\n"
            f"X-Request-Id: {request_id}\r\n"
            f"{body_header}"
            "Content-Type: application/json\r\n"
            "Connection: keep-alive\r\n"
            "\r\n"
        ).encode("iso-8859-1")
        client.sendall(headers + incomplete_body)
        response = bytearray()
        while True:
            chunk = client.recv(4096)
            if not chunk:
                break
            response.extend(chunk)
        lowered = bytes(response).lower()
        if not response.startswith(b"HTTP/1.1 429"):
            raise SystemExit("unexpected early-response status")
        if b"connection: close" not in lowered:
            raise SystemExit("missing connection close header")
        if b'"code":"rate_limited"' not in lowered:
            raise SystemExit("missing rate-limit response body")
        if b"access-control-allow-origin: https://app.example" not in lowered:
            raise SystemExit("missing cors header")
        if f"x-request-id: {request_id}".encode() not in lowered:
            raise SystemExit("missing request id")
PY
unread_metrics_status="$(curl --disable --noproxy '*' --silent \
  --output "$TMP_DIR/unread-metrics.json" --write-out '%{http_code}' --max-time 5 \
  "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/metrics/vanrides.request_body.unread_connections_closed")"
assert_equal "$unread_metrics_status" 200 unread_body_metric_status
python3 - "$TMP_DIR/unread-metrics.json" <<'PY' || fail unread_body_metric_count
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
count = sum(
    float(item.get("value", 0))
    for item in payload.get("measurements", [])
    if item.get("statistic") == "COUNT"
)
if count < 2:
    raise SystemExit("unread close counter below expected minimum")
PY
python3 "$LOAD_RUNNER" \
  --url-template "http://127.0.0.1:${APP_PORT}/login" --method POST \
  --requests 8 --workers 4 --expected-limited 6 --expected-non-limited 2 \
  --expected-non-limited-status 400 \
  --body '{}' --origin https://app.example --rotate-x-forwarded-for \
  >"$TMP_DIR/direct-load.out" || fail direct_rotating_xff
python3 "$LOAD_RUNNER" \
  --url-template "http://127.0.0.1:${APP_PORT}/driver-onboarding/invites/token-{index}/preview" \
  --method GET --requests 4 --workers 1 --expected-limited 2 \
  --expected-non-limited 2 --expected-non-limited-status 404 \
  >"$TMP_DIR/token-load.out" || fail dynamic_token_family
status="$(capture_request body-length /auth/refresh -X POST \
  -H 'Origin: https://app.example' -H 'X-Request-Id: smoke-body-length' \
  -H 'Content-Type: application/json' --data-binary @"$TMP_DIR/oversized.json")"
assert_early_error_contract body-length 413 PAYLOAD_TOO_LARGE smoke-body-length "$status"
status="$(capture_request body-stream /auth/refresh -X POST \
  -H 'Origin: https://app.example' -H 'X-Request-Id: smoke-body-stream' \
  -H 'Content-Type: application/json' -H 'Transfer-Encoding: chunked' \
  --data-binary @"$TMP_DIR/oversized.json")"
assert_early_error_contract body-stream 413 PAYLOAD_TOO_LARGE smoke-body-stream "$status"
metrics_status="$(curl --disable --noproxy '*' --silent --output "$TMP_DIR/metrics.json" \
  --write-out '%{http_code}' --max-time 5 \
  "http://127.0.0.1:${MANAGEMENT_PORT}/actuator/metrics/vanrides.rate.limit.requests")"
assert_equal "$metrics_status" 200 direct_metrics_status
grep -Eq '198\.51|rate-limit-load' "$TMP_DIR/metrics.json" && fail metrics_expose_identity
record direct_rotating_xff PASS
record dynamic_token_family PASS
record body_content_length PASS
record body_unknown_length PASS
record unsupported_body_media_type PASS
record unread_body_connection_close PASS
record unread_body_close_metric PASS
record preflight_free PASS
record metrics_no_identity PASS
stop_app
start_app trusted "127.0.0.1/32"
python3 "$LOAD_RUNNER" \
  --url-template "http://127.0.0.1:${APP_PORT}/login" --method POST \
  --requests 8 --workers 4 --expected-limited 0 --expected-non-limited 8 \
  --expected-non-limited-status 400 \
  --body '{}' --rotate-x-forwarded-for >"$TMP_DIR/trusted-load.out" \
  || fail trusted_proxy_identity
status="$(capture_request invalid-forwarded /login -X POST \
  -H 'Origin: https://app.example' -H 'X-Request-Id: smoke-invalid-forwarded' \
  -H 'X-Forwarded-For: 198.51.100.1,,127.0.0.1' \
  -H 'Content-Type: application/json' --data '{}')"
assert_early_error_contract invalid-forwarded 400 INVALID_FORWARDED_ADDRESS \
  smoke-invalid-forwarded "$status"
python3 "$LOAD_RUNNER" \
  --url-template "http://127.0.0.1:${APP_PORT}/auth/refresh" --method POST \
  --requests 3 --workers 1 --expected-limited 1 --expected-non-limited 2 \
  --expected-non-limited-status 400 \
  --body '{}' >"$TMP_DIR/proxy-peer-load.out" || fail proxy_peer_bucket
record trusted_proxy_identity PASS
record invalid_forwarded_fail_closed PASS
record proxy_peer_bucket PASS
stop_app
assert_equal "$(sha256_file "$JAR_PATH")" "$JAR_SHA256" jar_changed_during_smoke
assert_equal "$(git_safe rev-parse HEAD)" "$BACKEND_SHA" backend_sha_changed
assert_equal "$(git_safe rev-parse 'HEAD^{tree}')" "$BACKEND_TREE" backend_tree_changed
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_changed
record same_jar_runtime PASS
record remote_environment_mutation NONE
MAIN_RESULT=PASS
