#!/usr/bin/env bash
set -Eeuo pipefail

umask 077
export LC_ALL=C
export LANG=C
export PYTHONUTF8=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_PATH="$ROOT_DIR/scripts/qa_email_confirmation_intent_smoke.sh"
EXPECTED_BACKEND_SHA="${VANRIDES_EXPECTED_BACKEND_SHA:-}"
EXPECTED_BACKEND_TREE="${VANRIDES_EXPECTED_BACKEND_TREE:-}"
POSTGRES_IMAGE="${VANRIDES_POSTGRES_IMAGE:-postgres:16-alpine}"
PRESERVE_RUNTIME_JAR="${VANRIDES_PRESERVE_RUNTIME_JAR:-false}"
RUN_ID="email-intent-$(date -u +%Y%m%dT%H%M%SZ)-$$"
RUN_SLUG="$(printf '%s' "$RUN_ID" | tr '[:upper:]' '[:lower:]')"
TMP_BASE="${TMPDIR:-/tmp}"
TMP_DIR="$(mktemp -d "$TMP_BASE/vanrides-email-intent.XXXXXX")"
BUILD_SNAPSHOT_DIR="$TMP_DIR/backend-snapshot"
SUMMARY_FILE="$TMP_DIR/summary.env"
SENSITIVE_PATTERNS_FILE="$TMP_DIR/sensitive-patterns.txt"
TOKEN_PATTERNS_FILE="$TMP_DIR/token-patterns.txt"
EVIDENCE_BASE="$ROOT_DIR/output/qa-evidence"
EVIDENCE_DIR="$EVIDENCE_BASE/$RUN_ID"
MANIFEST_PATH="$EVIDENCE_DIR/manifest.env"
MANIFEST_RELATIVE="output/qa-evidence/$RUN_ID/manifest.env"
PRESERVED_JAR_PATH="$EVIDENCE_DIR/runtime.jar"
PRESERVED_JAR_RELATIVE="output/qa-evidence/$RUN_ID/runtime.jar"
CONTAINER_NAME="vanrides-$RUN_SLUG"
CONTAINER_LABEL_KEY="com.vanrides.qa.email-confirmation-intent.run-token"
CONTAINER_RUN_TOKEN=""
CONTAINER_ID=""
CONTAINER_ATTEMPTED=0
APP_PID=""
ASYNC_PIDS=""
MAIN_RESULT=""
FAILED_CHECK=""

for log_file in build.log application.log db.stderr docker.stderr; do
  : >"$TMP_DIR/$log_file"
done
: >"$TMP_DIR/db.stdout"
: >"$SUMMARY_FILE"
: >"$SENSITIVE_PATTERNS_FILE"
: >"$TOKEN_PATTERNS_FILE"
chmod 600 "$SUMMARY_FILE" "$SENSITIVE_PATTERNS_FILE" "$TOKEN_PATTERNS_FILE"

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

register_sensitive() {
  local value="$1"
  [[ -n "$value" && "$value" != *$'\n'* && "$value" != *$'\r'* ]] \
    || fail invalid_sensitive_registry_value
  printf '%s\n' "$value" >>"$SENSITIVE_PATTERNS_FILE"
}

register_token_material() {
  local raw_token="$1"
  local token_hash="$2"
  register_sensitive "$raw_token"
  register_sensitive "$token_hash"
  printf '%s\n%s\n' "$raw_token" "$token_hash" >>"$TOKEN_PATTERNS_FILE"
}

files_exclude_patterns() {
  local patterns_file="$1"
  shift
  python3 - "$patterns_file" "$@" <<'PY'
import sys
from pathlib import Path

patterns = [
    line.rstrip("\n")
    for line in Path(sys.argv[1]).read_text(encoding="utf-8").splitlines()
    if line.rstrip("\n")
]
for target_name in sys.argv[2:]:
    target = Path(target_name)
    if not target.exists() or not target.is_file():
        continue
    content = target.read_text(encoding="utf-8", errors="replace")
    if any(pattern in content for pattern in patterns):
        raise SystemExit(1)
PY
}

assert_response_excludes_token_material() {
  local response_file="$1"
  local label="$2"
  files_exclude_patterns "$TOKEN_PATTERNS_FILE" \
    "$response_file" "${response_file}.headers" || fail "$label"
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
  local pid="$APP_PID"
  [[ -n "$pid" ]] || return 0
  if kill -0 "$pid" >/dev/null 2>&1; then
    kill "$pid" >/dev/null 2>&1 || true
    for _ in $(seq 1 20); do
      kill -0 "$pid" >/dev/null 2>&1 || break
      sleep 0.25
    done
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill -KILL "$pid" >/dev/null 2>&1 || true
    fi
    wait "$pid" >/dev/null 2>&1 || true
  fi
  APP_PID=""
  ! kill -0 "$pid" >/dev/null 2>&1
}

stop_async_clients() {
  local pid
  local result=0
  for pid in $ASYNC_PIDS; do
    [[ "$pid" =~ ^[1-9][0-9]*$ ]] || {
      result=1
      continue
    }
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill "$pid" >/dev/null 2>&1 || true
      for _ in $(seq 1 20); do
        kill -0 "$pid" >/dev/null 2>&1 || break
        sleep 0.05
      done
    fi
    if kill -0 "$pid" >/dev/null 2>&1; then
      kill -KILL "$pid" >/dev/null 2>&1 || true
    fi
    wait "$pid" >/dev/null 2>&1 || true
    kill -0 "$pid" >/dev/null 2>&1 && result=1
  done
  ASYNC_PIDS=""
  return "$result"
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

prepare_evidence_create_only() {
  local runtime_jar_source="${1:-}"
  [[ ! -e "$EVIDENCE_DIR" ]] || return 1
  mkdir -- "$EVIDENCE_DIR" || return 1
  (
    set -o noclobber
    umask 077
    if [[ -n "$runtime_jar_source" ]]; then
      [[ -f "$runtime_jar_source" && ! -L "$runtime_jar_source" ]] || exit 1
      [[ ! -e "$PRESERVED_JAR_PATH" ]] || exit 1
      python3 - "$runtime_jar_source" "$PRESERVED_JAR_PATH" <<'PY' || exit 1
import os
import shutil
import sys

source, destination = sys.argv[1:]
descriptor = os.open(destination, os.O_WRONLY | os.O_CREAT | os.O_EXCL, 0o400)
try:
    with os.fdopen(descriptor, "wb") as target:
        with open(source, "rb") as origin:
            shutil.copyfileobj(origin, target)
except BaseException:
    try:
        os.unlink(destination)
    finally:
        raise
PY
      chmod 400 "$PRESERVED_JAR_PATH" || exit 1
      [[ "$(sha256_file "$PRESERVED_JAR_PATH")" == \
          "$(sha256_file "$runtime_jar_source")" ]] || exit 1
    fi
  )
}

persist_manifest_create_only() {
  local body="$1"
  [[ -d "$EVIDENCE_DIR" && ! -L "$EVIDENCE_DIR" ]] || return 1
  [[ ! -e "$MANIFEST_PATH" ]] || return 1
  (
    set -o noclobber
    umask 077
    printf '%s' "$body" >"$MANIFEST_PATH" || exit 1
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
  local failed_check="${FAILED_CHECK:-}"
  local summary_body=""
  local manifest_body=""
  local manifest_candidate="$TMP_DIR/manifest-candidate.env"
  local evidence_runtime_jar=""
  local manifest_sanitization=PASS
  local evidence_prepared=PASS
  local tmp_removed=PASS
  local -a sensitive_logs=(
    "$TMP_DIR/build.log"
    "$TMP_DIR/application.log"
    "$TMP_DIR/db.stderr"
    "$TMP_DIR/db.stdout"
    "$TMP_DIR/lock-holder.stdout"
    "$TMP_DIR/docker.stderr"
    "$SUMMARY_FILE"
  )

  trap - EXIT INT TERM
  set +e

  stop_async_clients || cleanup_status=FAIL
  stop_app || cleanup_status=FAIL
  if [[ "$CONTAINER_ATTEMPTED" == 1 ]]; then
    if ! command -v docker >/dev/null 2>&1 || ! docker info >/dev/null 2>&1; then
      cleanup_status=FAIL
    else
      if [[ -z "$owned_id" ]] && docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        owned_id="$(docker inspect --format '{{.Id}}' "$CONTAINER_NAME" 2>/dev/null)"
      fi
      if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
        owner_value="$(docker inspect --format \
          '{{ index .Config.Labels "com.vanrides.qa.email-confirmation-intent.run-token" }}' \
          "$owned_id" 2>/dev/null)"
        if [[ "$owned_id" =~ ^[0-9a-f]{64}$ && \
              "$owner_value" == "$CONTAINER_RUN_TOKEN" ]]; then
          docker rm --force "$owned_id" >/dev/null 2>&1 || cleanup_status=FAIL
        else
          cleanup_status=FAIL
        fi
      fi
      if [[ -n "$owned_id" ]] && docker inspect "$owned_id" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
      # A replacement racing to reuse the name is never removed by name.
      if docker inspect "$CONTAINER_NAME" >/dev/null 2>&1; then
        cleanup_status=FAIL
      fi
    fi
  fi

  while IFS= read -r request_stderr; do
    sensitive_logs+=("$request_stderr")
  done < <(find "$TMP_DIR" -maxdepth 1 -type f -name '*.curl.stderr' -print)

  if files_exclude_patterns "$SENSITIVE_PATTERNS_FILE" "${sensitive_logs[@]}"; then
    record sensitive_log_scan PASS || cleanup_status=FAIL
  else
    cleanup_status=FAIL
    failed_check=sensitive_material_leak
    record sensitive_log_scan FAIL || true
  fi
  record build_log_sha256 "$(sha256_file "$TMP_DIR/build.log")" || cleanup_status=FAIL
  record application_log_sha256 "$(sha256_file "$TMP_DIR/application.log")" \
    || cleanup_status=FAIL
  record db_log_sha256 "$(sha256_file "$TMP_DIR/db.stderr")" || cleanup_status=FAIL
  record db_stdout_sha256 "$(sha256_file "$TMP_DIR/db.stdout")" \
    || cleanup_status=FAIL
  record docker_log_sha256 "$(sha256_file "$TMP_DIR/docker.stderr")" \
    || cleanup_status=FAIL

  if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS && \
        "$cleanup_status" == PASS ]]; then
    smoke_result=PASS
  fi
  if [[ "$PRESERVE_RUNTIME_JAR" == true && "$smoke_result" == PASS ]]; then
    evidence_runtime_jar="$RUNTIME_JAR_PATH"
    record browser_runtime_jar "$PRESERVED_JAR_RELATIVE" || cleanup_status=FAIL
  elif [[ "$PRESERVE_RUNTIME_JAR" == true ]]; then
    record browser_runtime_jar NOT_AVAILABLE || cleanup_status=FAIL
  else
    record browser_runtime_jar NOT_REQUESTED || cleanup_status=FAIL
  fi
  [[ -n "$failed_check" ]] || failed_check=none
  if [[ ! "$failed_check" =~ ^[a-z0-9_]+$ ]]; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
    failed_check=invalid_failed_check
    evidence_runtime_jar=""
  fi
  summary_body="$(<"$SUMMARY_FILE")"
  manifest_body="${summary_body}"$'\n'"failed_check=${failed_check}"$'\n'\
"smoke_result=${smoke_result}"$'\n'"cleanup_status=${cleanup_status}"$'\n'\
"manifest_sanitization=PASS"$'\n'
  printf '%s' "$manifest_body" >"$manifest_candidate"
  if ! files_exclude_patterns "$SENSITIVE_PATTERNS_FILE" "$manifest_candidate"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
    failed_check=manifest_sanitization
    manifest_sanitization=FAIL
    evidence_runtime_jar=""
  fi

  if ! ensure_evidence_base \
      || ! prepare_evidence_create_only "$evidence_runtime_jar"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
    evidence_prepared=FAIL
    [[ "$failed_check" != none ]] || failed_check=evidence_preparation
  fi

  case "$TMP_DIR" in
    "$TMP_BASE"/vanrides-email-intent.*)
      if ! rm -rf -- "$TMP_DIR" || [[ -e "$TMP_DIR" ]]; then
        cleanup_status=FAIL
        smoke_result=FAIL
        final_status=1
        tmp_removed=FAIL
        [[ "$failed_check" != none ]] || failed_check=temporary_directory_cleanup
      fi
      ;;
    *)
      cleanup_status=FAIL
      smoke_result=FAIL
      final_status=1
      tmp_removed=FAIL
      [[ "$failed_check" != none ]] || failed_check=temporary_directory_scope
      ;;
  esac

  if [[ "$cleanup_status" == PASS && "$manifest_sanitization" == PASS && \
        "$evidence_prepared" == PASS && "$tmp_removed" == PASS ]]; then
    if [[ "$original_status" -eq 0 && "$MAIN_RESULT" == PASS ]]; then
      smoke_result=PASS
      failed_check=none
      final_status=0
    else
      smoke_result=FAIL
      final_status=1
      [[ "$failed_check" != none ]] || failed_check=unexpected_exit
    fi
  else
    smoke_result=FAIL
    cleanup_status=FAIL
    final_status=1
    [[ "$failed_check" != none ]] || failed_check=cleanup_failure
    [[ -n "$failed_check" ]] || failed_check=unexpected_exit
  fi
  if [[ "$manifest_sanitization" == PASS ]]; then
    manifest_body="${summary_body}"$'\n'"failed_check=${failed_check}"$'\n'\
"smoke_result=${smoke_result}"$'\n'\
"failed_check_sha256=$(sha256_text "$failed_check")"$'\n'\
"cleanup_status=${cleanup_status}"$'\n'"manifest_sanitization=PASS"$'\n'
  else
    manifest_body="run_id=${RUN_ID}"$'\n'"failed_check=manifest_sanitization"$'\n'\
"smoke_result=FAIL"$'\n'\
"failed_check_sha256=$(sha256_text manifest_sanitization)"$'\n'\
"cleanup_status=FAIL"$'\n'"manifest_sanitization=FAIL"$'\n'
  fi
  if ! persist_manifest_create_only "$manifest_body"; then
    cleanup_status=FAIL
    smoke_result=FAIL
    final_status=1
  fi

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
if payload.get("emailVerificado") is not True or "senha" in payload:
    raise SystemExit("bootstrap response contract mismatch")
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
if payload.get("emailVerificado") is not False or "senha" in payload:
    raise SystemExit("pending passenger response contract mismatch")
PY
}

assert_success_body() {
  python3 - "$1" "$2" <<'PY'
import json
import sys
from pathlib import Path

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
expected_email = Path(sys.argv[2]).read_text(encoding="utf-8")
if set(payload) != {"message", "email"}:
    raise SystemExit("confirmation response keys mismatch")
if payload.get("email") != expected_email:
    raise SystemExit("confirmation response owner mismatch")
if payload.get("message") != "E-mail verificado com sucesso.":
    raise SystemExit("confirmation response message mismatch")
PY
}

assert_invalid_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if set(payload) != {"code", "message", "email"}:
    raise SystemExit("invalid confirmation response keys mismatch")
if payload.get("code") != "INVALID_EMAIL_VERIFICATION_TOKEN":
    raise SystemExit("invalid confirmation code mismatch")
if payload.get("email") is not None:
    raise SystemExit("invalid confirmation exposed owner context")
if payload.get("message") != "Token de verificação inválido, expirado ou já utilizado.":
    raise SystemExit("invalid confirmation message mismatch")
PY
}

assert_rate_limited_body() {
  python3 - "$1" <<'PY'
import json
import sys

with open(sys.argv[1], encoding="utf-8") as handle:
    payload = json.load(handle)
if payload.get("code") != "RATE_LIMITED":
    raise SystemExit("rate limit code mismatch")
if set(payload) != {"code", "message", "email"} or payload.get("email") is not None:
    raise SystemExit("rate limit response keys mismatch")
PY
}

write_curl_config() {
  local method="$1"
  local path="$2"
  local config_file="$3"
  local client_ip="$4"
  local login_name="${5:-}"
  local login_password="${6:-}"
  local request_profile="${7:-standard}"
  local request_id="${8:-}"
  local path_pattern='^/[A-Za-z0-9/?&=._-]+$'

  [[ "$method" =~ ^(GET|POST|OPTIONS)$ ]] || return 1
  [[ "$path" =~ $path_pattern ]] || return 1
  [[ "$client_ip" =~ ^[0-9.]+$ ]] || return 1
  [[ "$request_profile" =~ ^(standard|prefetch|preflight|cors)$ ]] || return 1
  [[ -z "$request_id" || "$request_id" =~ ^[A-Za-z0-9._:-]+$ ]] || return 1
  : >"$config_file"
  chmod 600 "$config_file"
  printf 'url = "%s%s"\n' "$BASE_URL" "$path" >>"$config_file"
  printf 'request = "%s"\n' "$method" >>"$config_file"
  printf 'header = "Content-Type: application/json"\n' >>"$config_file"
  printf 'header = "X-Forwarded-For: %s"\n' "$client_ip" >>"$config_file"
  if [[ -n "$request_id" ]]; then
    printf 'header = "X-Request-Id: %s"\n' "$request_id" >>"$config_file"
  fi
  if [[ -n "$login_name" ]]; then
    [[ "$login_name" =~ ^[a-z0-9@._+-]+$ ]] || return 1
    [[ "$login_password" =~ ^[0-9a-f]{32}$ ]] || return 1
    printf 'user = "%s:%s"\n' "$login_name" "$login_password" \
      >>"$config_file"
  fi
  if [[ "$request_profile" == prefetch ]]; then
    printf 'header = "Purpose: prefetch"\n' >>"$config_file"
    printf 'header = "Sec-Purpose: prefetch;prerender"\n' >>"$config_file"
  elif [[ "$request_profile" == preflight ]]; then
    printf 'header = "Origin: https://app.vanrides.test"\n' >>"$config_file"
    printf 'header = "Access-Control-Request-Method: POST"\n' >>"$config_file"
    printf 'header = "Access-Control-Request-Headers: Content-Type"\n' \
      >>"$config_file"
  elif [[ "$request_profile" == cors ]]; then
    printf 'header = "Origin: https://app.vanrides.test"\n' >>"$config_file"
  fi
}

raw_head_request() {
  local path="$1"
  local output_file="$2"
  local client_ip="$3"
  local request_id="${4:-}"
  local path_file="${output_file}.request-path"
  local path_pattern='^/[A-Za-z0-9/?&=._-]+$'
  [[ "$path" =~ $path_pattern ]] || return 1
  [[ "$client_ip" =~ ^[0-9.]+$ ]] || return 1
  [[ -z "$request_id" || "$request_id" =~ ^[A-Za-z0-9._:-]+$ ]] || return 1
  printf '%s' "$path" >"$path_file"
  chmod 600 "$path_file"
  : >"${output_file}.curl.stderr"
  set +e
  python3 - "$path_file" "$output_file" "${output_file}.headers" \
    "$APP_PORT" "$client_ip" "$request_id" \
    2>"${output_file}.curl.stderr" <<'PY'
import socket
import sys
from pathlib import Path

path_file, body_file, headers_file, port_value, client_ip, request_id = sys.argv[1:]
path = Path(path_file).read_text(encoding="utf-8")
request_headers = [
    f"HEAD {path} HTTP/1.1",
    f"Host: 127.0.0.1:{port_value}",
    "Accept: text/html",
    "Connection: close",
    f"X-Forwarded-For: {client_ip}",
]
if request_id:
    request_headers.append(f"X-Request-Id: {request_id}")
wire_request = ("\r\n".join(request_headers) + "\r\n\r\n").encode("ascii")
with socket.create_connection(("127.0.0.1", int(port_value)), timeout=5) as sock:
    sock.settimeout(30)
    sock.sendall(wire_request)
    chunks = []
    while True:
        chunk = sock.recv(65536)
        if not chunk:
            break
        chunks.append(chunk)
raw_response = b"".join(chunks)
header_blob, separator, body = raw_response.partition(b"\r\n\r\n")
if not separator:
    raise SystemExit("HEAD response has no header boundary")
status_line = header_blob.split(b"\r\n", 1)[0]
parts = status_line.split(b" ", 2)
if len(parts) < 2 or not parts[1].isdigit():
    raise SystemExit("HEAD response status is invalid")
Path(headers_file).write_bytes(header_blob + b"\r\n")
Path(body_file).write_bytes(body)
print(parts[1].decode("ascii"), end="")
PY
  local raw_status=$?
  set -e
  rm -f -- "$path_file"
  return "$raw_status"
}

http_request() {
  local method="$1"
  local path="$2"
  local output_file="$3"
  local payload="${4:-}"
  local client_ip="${5:-198.51.100.1}"
  local login_name="${6:-}"
  local login_password="${7:-}"
  local request_profile="${8:-standard}"
  local request_id="${9:-}"
  local config_file="${output_file}.curl-config"
  local response_status=""
  local curl_status=0
  local -a args=(
    --noproxy '*' --silent --show-error --http1.1
    --connect-timeout 5 --max-time 30
    --dump-header "${output_file}.headers"
    --output "$output_file" --write-out '%{http_code}'
    --stderr "${output_file}.curl.stderr"
  )

  if [[ "$method" == HEAD ]]; then
    [[ -z "$payload" && "$request_profile" == standard ]] || return 1
    raw_head_request "$path" "$output_file" "$client_ip" "$request_id"
    return
  fi
  write_curl_config "$method" "$path" "$config_file" "$client_ip" \
    "$login_name" "$login_password" "$request_profile" "$request_id" || return 1
  set +e
  if [[ -n "$payload" ]]; then
    response_status="$(printf '%s' "$payload" \
      | curl --disable --config "$config_file" "${args[@]}" --data-binary @-)"
    curl_status=$?
  else
    response_status="$(curl --disable --config "$config_file" "${args[@]}")"
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

assert_landing_headers() {
  local headers="$1"
  assert_equal "$(header_value "$headers" content-type)" \
    "text/html;charset=UTF-8" landing_content_type
  assert_equal "$(header_value "$headers" cache-control)" \
    "no-store, max-age=0" landing_cache_control
  assert_equal "$(header_value "$headers" pragma)" no-cache landing_pragma
  assert_equal "$(header_value "$headers" expires)" 0 landing_expires
  assert_equal "$(header_value "$headers" referrer-policy)" no-referrer \
    landing_referrer_policy
  assert_equal "$(header_value "$headers" x-content-type-options)" nosniff \
    landing_nosniff
  assert_equal "$(header_value "$headers" x-frame-options)" DENY \
    landing_frame_options
  assert_equal "$(header_value "$headers" x-robots-tag)" \
    "noindex, nofollow, noarchive, nosnippet" landing_robots
  [[ "$(header_value "$headers" content-security-policy)" == \
      *"default-src 'none'"* ]] || fail landing_csp
  [[ "$(header_value "$headers" permissions-policy)" == \
      *"camera=()"* ]] || fail landing_permissions_policy
}

assert_landing_inline_policy() {
  python3 - "$1" "$2" <<'PY'
import base64
import hashlib
import re
import sys
from pathlib import Path

page = Path(sys.argv[1]).read_text(encoding="utf-8")
headers = Path(sys.argv[2]).read_text(encoding="utf-8", errors="replace")

def single_inline(tag: str) -> str:
    matches = re.findall(fr"<{tag}>(.*?)</{tag}>", page, flags=re.DOTALL)
    if len(matches) != 1:
        raise SystemExit(f"expected exactly one inline {tag}")
    return matches[0]

def header(name: str) -> str:
    prefix = name.lower() + ":"
    for line in headers.splitlines():
        if line.lower().startswith(prefix):
            return line.split(":", 1)[1].strip()
    raise SystemExit(f"missing {name}")

def sha256_base64(value: str) -> str:
    return base64.b64encode(hashlib.sha256(value.encode("utf-8")).digest()).decode()

style = single_inline("style")
script = single_inline("script")
expected_csp = (
    "default-src 'none'; "
    f"style-src 'sha256-{sha256_base64(style)}'; "
    f"script-src 'sha256-{sha256_base64(script)}'; "
    "connect-src 'self'; base-uri 'none'; form-action 'none'; "
    "frame-ancestors 'none'"
)
if header("Content-Security-Policy") != expected_csp:
    raise SystemExit("inline CSP hash contract mismatch")
if header("Permissions-Policy") != (
    "camera=(), microphone=(), geolocation=(), payment=()"
):
    raise SystemExit("permissions policy mismatch")
lower_page = page.lower()
if any(marker in lower_page for marker in (" src=", "<link", "http://", "https://")):
    raise SystemExit("landing contains an external subrequest surface")
PY
}

write_landing_header_contract() {
  local headers="$1"
  local output="$2"
  : >"$output"
  for name in content-type cache-control pragma expires referrer-policy \
    content-security-policy x-content-type-options x-frame-options \
    x-robots-tag permissions-policy; do
    printf '%s=%s\n' "$name" "$(header_value "$headers" "$name")" >>"$output"
  done
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

state_fingerprint() {
  local passenger_id="$1"
  local projection_mode="${2:-full}"
  local account_projection="to_jsonb(account)"
  local token_projection="to_jsonb(token_row)"
  local account_state=""
  local identity_state=""
  local refresh_state=""
  local verification_state=""
  [[ "$passenger_id" =~ ^[1-9][0-9]*$ ]] || return 1
  case "$projection_mode" in
    full) ;;
    ignore_token_consumption)
      token_projection="to_jsonb(token_row) - 'consumed_at'"
      ;;
    ignore_confirmation_transition)
      account_projection="to_jsonb(account) - 'email_verified' - 'email_verified_at'"
      token_projection="to_jsonb(token_row) - 'consumed_at'"
      ;;
    *) return 1 ;;
  esac
  account_state="$(db_scalar "SELECT (${account_projection})::text
    FROM passageiro account WHERE account.id = ${passenger_id};")" || return 1
  identity_state="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(identity_row) ORDER BY identity_row.normalized_email),
    '[]'::jsonb)::text
    FROM (SELECT * FROM login_identity
      WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}) identity_row;")" \
    || return 1
  refresh_state="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(refresh_row) ORDER BY refresh_row.id), '[]'::jsonb)::text
    FROM (SELECT * FROM refresh_token
      WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}) refresh_row;")" \
    || return 1
  verification_state="$(db_scalar "SELECT coalesce(
    jsonb_agg(${token_projection} ORDER BY token_row.id), '[]'::jsonb)::text
    FROM (SELECT * FROM email_verification_token
      WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}) token_row;")" \
    || return 1
  printf '%s:%s:%s:%s' \
    "$(sha256_text "$account_state")" \
    "$(sha256_text "$identity_state")" \
    "$(sha256_text "$refresh_state")" \
    "$(sha256_text "$verification_state")"
}

global_state_fingerprint() {
  local accounts=""
  local identities=""
  local refresh_tokens=""
  local verification_tokens=""
  accounts="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(account_row) ORDER BY account_row.id), '[]'::jsonb)::text
    FROM (SELECT * FROM passageiro) account_row;")" || return 1
  identities="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(identity_row) ORDER BY identity_row.normalized_email),
    '[]'::jsonb)::text FROM (SELECT * FROM login_identity) identity_row;")" \
    || return 1
  refresh_tokens="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(refresh_row) ORDER BY refresh_row.id), '[]'::jsonb)::text
    FROM (SELECT * FROM refresh_token) refresh_row;")" || return 1
  verification_tokens="$(db_scalar "SELECT coalesce(
    jsonb_agg(to_jsonb(token_row) ORDER BY token_row.id), '[]'::jsonb)::text
    FROM (SELECT * FROM email_verification_token) token_row;")" || return 1
  printf '%s:%s:%s:%s' \
    "$(sha256_text "$accounts")" \
    "$(sha256_text "$identities")" \
    "$(sha256_text "$refresh_tokens")" \
    "$(sha256_text "$verification_tokens")"
}

seed_active_refresh_token() {
  local passenger_id="$1"
  local email="$2"
  local company_id="$3"
  local token_hash="$4"
  [[ "$passenger_id" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$company_id" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$email" =~ ^[a-z0-9@._+-]+$ ]] || return 1
  [[ "$token_hash" =~ ^[0-9a-f]{64}$ ]] || return 1
  register_sensitive "$token_hash"
  db_exec "INSERT INTO refresh_token (
    token_hash, user_id, user_role, user_email, company_id, expires_at,
    revoked, created_at, updated_at
  ) VALUES (
    '${token_hash}', ${passenger_id}, 'PASSENGER', '${email}', ${company_id},
    CURRENT_TIMESTAMP + INTERVAL '1 day', FALSE, CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
  );" || return 1
  assert_equal "$(db_scalar "SELECT count(*) FROM refresh_token
    WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}
      AND token_hash = '${token_hash}' AND revoked = FALSE;")" \
    1 seeded_active_refresh_token
}

assert_active_refresh_token() {
  local passenger_id="$1"
  local label="$2"
  assert_equal "$(db_scalar "SELECT count(*) FROM refresh_token
    WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}
      AND revoked = FALSE;")" 1 "$label"
}

seed_confirmation_token() {
  local passenger_id="$1"
  local email="$2"
  local raw_token="$3"
  local expiry="$4"
  local token_hash="$(sha256_text "$raw_token")"
  local expires_sql=""

  [[ "$passenger_id" =~ ^[1-9][0-9]*$ ]] || return 1
  [[ "$email" =~ ^[a-z0-9@._+-]+$ ]] || return 1
  [[ "$token_hash" =~ ^[0-9a-f]{64}$ ]] || return 1
  case "$expiry" in
    future) expires_sql="CURRENT_TIMESTAMP + INTERVAL '30 minutes'" ;;
    expired) expires_sql="CURRENT_TIMESTAMP - INTERVAL '1 minute'" ;;
    *) return 1 ;;
  esac

  register_token_material "$raw_token" "$token_hash"
  db_exec "UPDATE email_verification_token
    SET consumed_at = coalesce(consumed_at, CURRENT_TIMESTAMP)
    WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id};
  UPDATE passageiro
    SET email_verified = FALSE, email_verified_at = NULL
    WHERE id = ${passenger_id};
  INSERT INTO email_verification_token (
    user_role, user_id, email_snapshot, token_hash, expires_at, consumed_at, created_at
  ) VALUES (
    'PASSENGER', ${passenger_id}, '${email}', '${token_hash}',
    ${expires_sql}, NULL, CURRENT_TIMESTAMP
  );" || return 1
  assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token
    WHERE user_role = 'PASSENGER' AND user_id = ${passenger_id}
      AND token_hash = '${token_hash}' AND consumed_at IS NULL;")" \
    1 seeded_confirmation_token
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
  cd "$BUILD_SNAPSHOT_DIR"
  ./mvnw -q \
    -Duser.home="$MAVEN_ISOLATED_HOME" \
    -Dmaven.repo.local="$MAVEN_REPOSITORY" \
    -DskipTests clean package
)

run_private_app() (
  local variable
  while IFS='=' read -r variable _; do
    unset "$variable"
  done < <(env)
  export HOME="$TMP_DIR/home"
  export TMPDIR="$TMP_DIR"
  export LC_ALL=C
  export LANG=C
  export SPRING_PROFILES_ACTIVE=local
  export SPRING_DATASOURCE_URL="jdbc:postgresql://127.0.0.1:${DB_PORT}/vanrides"
  export SPRING_DATASOURCE_USERNAME=vanrides
  export SPRING_DATASOURCE_PASSWORD="$DB_PASSWORD"
  export SPRING_JPA_SHOW_SQL=false
  export SPRING_JPA_HIBERNATE_DDL_AUTO=validate
  export SERVER_PORT="$APP_PORT"
  export VANRIDES_HTTP_MAX_THREADS=200
  export VANRIDES_EMAIL_PROVIDER=log
  export VANRIDES_EMAIL_VERIFICATION_RESEND_COOLDOWN_MINUTES=0
  export VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL="${BASE_URL}/auth/email-verifications/confirm"
  export VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=qa-admin.invalid
  export VANRIDES_CORS_ALLOWED_ORIGIN_PATTERNS=https://app.vanrides.test
  export VANRIDES_CORS_ALLOWED_METHODS=GET,HEAD,POST,OPTIONS
  export VANRIDES_CORS_ALLOWED_HEADERS=Content-Type,Purpose,Sec-Purpose,X-Forwarded-For
  export VANRIDES_AUTH_BASIC_ENABLED=true
  export VANRIDES_RATE_LIMIT_ENABLED=true
  export VANRIDES_RATE_LIMIT_SENSITIVE_LIMIT=2
  export VANRIDES_RATE_LIMIT_WINDOW_SECONDS=60
  export VANRIDES_RATE_LIMIT_MAX_ENTRIES_PER_ROUTE=64
  export VANRIDES_RATE_LIMIT_TRUSTED_PROXY_CIDRS=127.0.0.1/32
  export VANRIDES_ROUTING_PROVIDER=static
  export VANRIDES_PUSH_PROVIDER=none
  export VANRIDES_MAPS_PLATFORM_ENABLED=false
  export VANRIDES_JWT_SECRET="$JWT_SECRET"
  export VANRIDES_OPERATIONS_TIME_ZONE=America/Sao_Paulo
  exec "$JAVA_BIN" -jar "$RUNTIME_JAR_PATH"
)

for command in awk basename chmod cmp cp curl date docker env find git java kill \
  mkdir mktemp openssl python3 rm seq shasum sleep tar tr; do
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

for variable in JAVA_TOOL_OPTIONS _JAVA_OPTIONS JDK_JAVA_OPTIONS; do
  [[ -z "${!variable:-}" ]] || fail "${variable}_override_not_allowed"
done
[[ -n "${HOME:-}" && "$HOME" == /* && -d "$HOME" ]] || fail invalid_build_home
MAVEN_ISOLATED_HOME="$TMP_DIR/maven-user-home"
MAVEN_REPOSITORY="$HOME/.m2/repository"
mkdir -p "$MAVEN_ISOLATED_HOME"
[[ -d "$MAVEN_REPOSITORY" && ! -L "$MAVEN_REPOSITORY" ]] \
  || fail invalid_maven_repository

[[ -z "${DOCKER_HOST:-}" ]] || fail docker_host_override_not_allowed
[[ -z "${DOCKER_CONTEXT:-}" ]] || fail docker_context_override_not_allowed
DOCKER_CONTEXT_NAME="$(docker context show 2>/dev/null)" \
  || fail docker_context_unavailable
DOCKER_ENDPOINT="$(docker context inspect "$DOCKER_CONTEXT_NAME" \
  --format '{{.Endpoints.docker.Host}}' 2>/dev/null)" \
  || fail docker_endpoint_unavailable
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

[[ "$EXPECTED_BACKEND_SHA" =~ ^[0-9a-f]{40}$ ]] \
  || fail expected_backend_sha_required
[[ "$EXPECTED_BACKEND_TREE" =~ ^[0-9a-f]{40}$ ]] \
  || fail expected_backend_tree_required
[[ "$POSTGRES_IMAGE" =~ ^[A-Za-z0-9._/@:+-]+$ ]] \
  || fail invalid_postgres_image
case "$PRESERVE_RUNTIME_JAR" in
  true|false) ;;
  *) fail invalid_preserve_runtime_jar ;;
esac
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
assert_equal "$BACKEND_TREE" "$EXPECTED_BACKEND_TREE" backend_tree
assert_no_hidden_index_flags backend_index_flags_hidden_before
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_not_clean
RUNNER_GIT_BLOB="$(git_safe rev-parse \
  "${BACKEND_SHA}:${MODULE_GIT_PREFIX}scripts/qa_email_confirmation_intent_smoke.sh")" \
  || fail runner_not_versioned
RUNNER_WORKTREE_BLOB="$(git_safe hash-object --no-filters "$SCRIPT_PATH")"
assert_equal "$RUNNER_WORKTREE_BLOB" "$RUNNER_GIT_BLOB" runner_blob_mismatch
mkdir -- "$BUILD_SNAPSHOT_DIR" || fail build_snapshot_directory
BUILD_TREEISH="$EXPECTED_BACKEND_SHA"
if [[ -n "$MODULE_GIT_PREFIX" ]]; then
  BUILD_TREEISH+=":${MODULE_GIT_PREFIX%/}"
fi
if ! git_safe archive --format=tar "$BUILD_TREEISH" \
    | tar -xf - -C "$BUILD_SNAPSHOT_DIR"; then
  fail build_snapshot_extract
fi
[[ -f "$BUILD_SNAPSHOT_DIR/pom.xml" && ! -L "$BUILD_SNAPSHOT_DIR/pom.xml" ]] \
  || fail build_snapshot_pom
[[ -f "$BUILD_SNAPSHOT_DIR/mvnw" && -x "$BUILD_SNAPSHOT_DIR/mvnw" && \
    ! -L "$BUILD_SNAPSHOT_DIR/mvnw" ]] || fail build_snapshot_wrapper

record run_id "$RUN_ID"
record expected_backend_sha "$EXPECTED_BACKEND_SHA"
record expected_backend_tree "$EXPECTED_BACKEND_TREE"
record backend_sha "$BACKEND_SHA"
record backend_tree "$BACKEND_TREE"
record runner_git_blob "$RUNNER_GIT_BLOB"
record runner_sha256 "$(sha256_file "$SCRIPT_PATH")"
record build_source_origin GIT_ARCHIVE
record build_source_sha "$EXPECTED_BACKEND_SHA"
record build_source_tree "$EXPECTED_BACKEND_TREE"
record preserve_runtime_jar_requested "$PRESERVE_RUNTIME_JAR"
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
done < <(find "$BUILD_SNAPSHOT_DIR/target" -maxdepth 1 -type f -name '*.jar' -print)
assert_equal "$JAR_COUNT" 1 jar_candidate_count
[[ -n "$BUILT_JAR_PATH" && -f "$BUILT_JAR_PATH" ]] || fail jar_not_found
RUNTIME_JAR_PATH="$TMP_DIR/$(basename "$BUILT_JAR_PATH")"
cp "$BUILT_JAR_PATH" "$RUNTIME_JAR_PATH" || fail runtime_jar_copy
chmod 500 "$RUNTIME_JAR_PATH" || fail runtime_jar_permissions
JAR_SHA256="$(sha256_file "$RUNTIME_JAR_PATH")"
assert_equal "$JAR_SHA256" "$(sha256_file "$BUILT_JAR_PATH")" \
  runtime_jar_copy_hash
record jar_sha256 "$JAR_SHA256"
record immutable_private_jar PASS

DB_PASSWORD="$(openssl rand -hex 24)"
ADMIN_PASSWORD="$(openssl rand -hex 16)"
PASSENGER_PASSWORD="$(openssl rand -hex 16)"
JWT_SECRET="$(openssl rand -hex 48)"
ADMIN_EMAIL="admin.${RUN_SLUG}@qa-admin.invalid"
SUCCESS_EMAIL="success.${RUN_SLUG}@pending-user.invalid"
EXPIRED_EMAIL="expired.${RUN_SLUG}@pending-user.invalid"
CONCURRENT_EMAIL="concurrent.${RUN_SLUG}@pending-user.invalid"
for sensitive_value in "$DB_PASSWORD" "$ADMIN_PASSWORD" "$PASSENGER_PASSWORD" \
  "$JWT_SECRET" "$ADMIN_EMAIL" "$SUCCESS_EMAIL" "$EXPIRED_EMAIL" \
  "$CONCURRENT_EMAIL"; do
  register_sensitive "$sensitive_value"
done
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
  '{{ index .Config.Labels "com.vanrides.qa.email-confirmation-intent.run-token" }}' \
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
run_private_app >"$TMP_DIR/application.log" 2>&1 &
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
record flyway_latest_version 25

ADMIN_PAYLOAD="{\"companyName\":\"Email Intent ${RUN_ID}\",\"nome\":\"Intent Admin\",\"email\":\"${ADMIN_EMAIL}\",\"telefone\":\"51990000001\",\"senha\":\"${ADMIN_PASSWORD}\"}"
STATUS="$(http_request POST /company-admins/bootstrap "$TMP_DIR/admin.json" \
  "$ADMIN_PAYLOAD" 198.51.100.2)"
assert_equal "$STATUS" 201 bootstrap_status
assert_bootstrap_body "$TMP_DIR/admin.json" || fail bootstrap_body
COMPANY_ID="$(json_get "$TMP_DIR/admin.json" companyId)"
[[ "$COMPANY_ID" =~ ^[1-9][0-9]*$ ]] || fail company_id_invalid

SUCCESS_PAYLOAD="{\"nome\":\"Intent Success\",\"email\":\"${SUCCESS_EMAIL}\",\"telefone\":\"51990000002\",\"endereco\":\"Rua Intent\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
EXPIRED_PAYLOAD="{\"nome\":\"Intent Expired\",\"email\":\"${EXPIRED_EMAIL}\",\"telefone\":\"51990000003\",\"endereco\":\"Rua Intent\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"
CONCURRENT_PAYLOAD="{\"nome\":\"Intent Concurrent\",\"email\":\"${CONCURRENT_EMAIL}\",\"telefone\":\"51990000004\",\"endereco\":\"Rua Intent\",\"bairro\":\"Centro\",\"cidade\":\"Igrejinha\",\"estado\":\"RS\",\"cep\":\"95650-000\",\"senha\":\"${PASSENGER_PASSWORD}\",\"menorDeIdade\":false}"

STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/success-passenger.json" \
  "$SUCCESS_PAYLOAD" 198.51.100.3 "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 201 success_passenger_status
assert_passenger_body "$TMP_DIR/success-passenger.json" || fail success_passenger_body
SUCCESS_ID="$(json_get "$TMP_DIR/success-passenger.json" id)"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/expired-passenger.json" \
  "$EXPIRED_PAYLOAD" 198.51.100.4 "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 201 expired_passenger_status
assert_passenger_body "$TMP_DIR/expired-passenger.json" || fail expired_passenger_body
EXPIRED_ID="$(json_get "$TMP_DIR/expired-passenger.json" id)"
STATUS="$(http_request POST /company-admins/passageiros "$TMP_DIR/concurrent-passenger.json" \
  "$CONCURRENT_PAYLOAD" 198.51.100.5 "$ADMIN_EMAIL" "$ADMIN_PASSWORD")"
assert_equal "$STATUS" 201 concurrent_passenger_status
assert_passenger_body "$TMP_DIR/concurrent-passenger.json" \
  || fail concurrent_passenger_body
CONCURRENT_ID="$(json_get "$TMP_DIR/concurrent-passenger.json" id)"
for passenger_id in "$SUCCESS_ID" "$EXPIRED_ID" "$CONCURRENT_ID"; do
  [[ "$passenger_id" =~ ^[1-9][0-9]*$ ]] || fail passenger_id_invalid
  assert_equal "$(db_scalar "SELECT company_id FROM passageiro WHERE id = ${passenger_id};")" \
    "$COMPANY_ID" passenger_company_fixture
done

SUCCESS_TOKEN="$(openssl rand -hex 32)"
EXPIRED_TOKEN="$(openssl rand -hex 32)"
CONCURRENT_TOKEN="$(openssl rand -hex 32)"
SUCCESS_REFRESH_HASH="$(openssl rand -hex 32)"
EXPIRED_REFRESH_HASH="$(openssl rand -hex 32)"
CONCURRENT_REFRESH_HASH="$(openssl rand -hex 32)"
seed_confirmation_token "$SUCCESS_ID" "$SUCCESS_EMAIL" "$SUCCESS_TOKEN" future \
  || fail success_token_seed
seed_confirmation_token "$EXPIRED_ID" "$EXPIRED_EMAIL" "$EXPIRED_TOKEN" expired \
  || fail expired_token_seed
seed_confirmation_token "$CONCURRENT_ID" "$CONCURRENT_EMAIL" "$CONCURRENT_TOKEN" future \
  || fail concurrent_token_seed
seed_active_refresh_token "$SUCCESS_ID" "$SUCCESS_EMAIL" "$COMPANY_ID" \
  "$SUCCESS_REFRESH_HASH" || fail success_refresh_seed
seed_active_refresh_token "$EXPIRED_ID" "$EXPIRED_EMAIL" "$COMPANY_ID" \
  "$EXPIRED_REFRESH_HASH" || fail expired_refresh_seed
seed_active_refresh_token "$CONCURRENT_ID" "$CONCURRENT_EMAIL" "$COMPANY_ID" \
  "$CONCURRENT_REFRESH_HASH" || fail concurrent_refresh_seed

LANDING_FINGERPRINT="$(state_fingerprint "$SUCCESS_ID")"
STATUS="$(http_request GET /auth/email-verifications/confirm \
  "$TMP_DIR/landing-clean.html" "" 198.51.100.10)"
assert_equal "$STATUS" 200 landing_clean_status
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$LANDING_FINGERPRINT" \
  landing_clean_no_mutation
assert_landing_headers "$TMP_DIR/landing-clean.html.headers"
assert_landing_inline_policy "$TMP_DIR/landing-clean.html" \
  "$TMP_DIR/landing-clean.html.headers" || fail landing_inline_policy
assert_response_excludes_token_material "$TMP_DIR/landing-clean.html" \
  landing_clean_token_reflected

STATUS="$(http_request GET \
  "/auth/email-verifications/confirm?token=${SUCCESS_TOKEN}" \
  "$TMP_DIR/landing-legacy.html" "" 198.51.100.10)"
assert_equal "$STATUS" 200 landing_legacy_status
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$LANDING_FINGERPRINT" \
  landing_legacy_no_mutation
cmp -s "$TMP_DIR/landing-clean.html" "$TMP_DIR/landing-legacy.html" \
  || fail landing_legacy_body_distinguishable
assert_response_excludes_token_material "$TMP_DIR/landing-legacy.html" \
  legacy_token_reflected

STATUS="$(http_request GET \
  "/auth/email-verifications/confirm?token=${SUCCESS_TOKEN}" \
  "$TMP_DIR/landing-prefetch.html" "" 198.51.100.10 "" "" prefetch)"
assert_equal "$STATUS" 200 landing_prefetch_status
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$LANDING_FINGERPRINT" \
  landing_prefetch_no_mutation
cmp -s "$TMP_DIR/landing-clean.html" "$TMP_DIR/landing-prefetch.html" \
  || fail landing_prefetch_body_distinguishable
assert_response_excludes_token_material "$TMP_DIR/landing-prefetch.html" \
  landing_prefetch_token_reflected

STATUS="$(http_request HEAD \
  "/auth/email-verifications/confirm?token=${SUCCESS_TOKEN}" \
  "$TMP_DIR/landing-head.body" "" 198.51.100.10)"
assert_equal "$STATUS" 200 landing_head_status
[[ ! -s "$TMP_DIR/landing-head.body" ]] || fail landing_head_body
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$LANDING_FINGERPRINT" \
  landing_head_no_mutation
assert_landing_headers "$TMP_DIR/landing-head.body.headers"
assert_response_excludes_token_material "$TMP_DIR/landing-head.body" \
  landing_head_token_reflected

STATUS="$(http_request OPTIONS /auth/email-verifications/confirm \
  "$TMP_DIR/landing-options.body" "" 198.51.100.10 "" "" preflight)"
assert_equal "$STATUS" 200 landing_options_status
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$LANDING_FINGERPRINT" \
  landing_options_no_mutation
assert_equal "$(header_value "$TMP_DIR/landing-options.body.headers" \
  access-control-allow-origin)" "https://app.vanrides.test" options_cors_origin
[[ "$(header_value "$TMP_DIR/landing-options.body.headers" \
  access-control-allow-methods)" == *POST* ]] || fail options_cors_method
assert_response_excludes_token_material "$TMP_DIR/landing-options.body" \
  landing_options_token_reflected
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token
  WHERE user_role = 'PASSENGER' AND user_id = ${SUCCESS_ID}
    AND consumed_at IS NULL;")" 1 valid_legacy_token_not_consumed

write_landing_header_contract "$TMP_DIR/landing-clean.html.headers" \
  "$TMP_DIR/landing-clean.contract"
for response_name in landing-legacy.html landing-prefetch.html landing-head.body; do
  write_landing_header_contract "$TMP_DIR/${response_name}.headers" \
    "$TMP_DIR/${response_name}.contract"
  cmp -s "$TMP_DIR/landing-clean.contract" "$TMP_DIR/${response_name}.contract" \
    || fail landing_security_headers_distinguishable
done
record landing_get_non_mutating PASS
record legacy_query_non_mutating PASS
record prefetch_non_mutating PASS
record head_non_mutating PASS
record options_non_mutating PASS
record landing_byte_equality PASS
record landing_header_equality PASS
record landing_inline_csp_exact PASS
record valid_legacy_token_preserved PASS
record automatic_confirmation NONE

printf '%s' "$SUCCESS_EMAIL" >"$TMP_DIR/success-email.expected"
SUCCESS_BEFORE="$(state_fingerprint "$SUCCESS_ID")"
SUCCESS_ALLOWED_BEFORE="$(state_fingerprint \
  "$SUCCESS_ID" ignore_confirmation_transition)"
SUCCESS_REQUEST="{\"token\":\"${SUCCESS_TOKEN}\"}"
STATUS="$(http_request POST /auth/email-verifications/confirm \
  "$TMP_DIR/confirm-success.json" "$SUCCESS_REQUEST" 198.51.100.31)"
assert_equal "$STATUS" 200 explicit_confirmation_status
assert_success_body "$TMP_DIR/confirm-success.json" "$TMP_DIR/success-email.expected" \
  || fail explicit_confirmation_body
assert_response_excludes_token_material "$TMP_DIR/confirm-success.json" \
  success_token_reflected
assert_equal "$(db_scalar "SELECT email_verified::text FROM passageiro WHERE id = ${SUCCESS_ID};")" \
  true explicit_confirmation_not_committed
assert_equal "$(db_scalar "SELECT (email_verified_at IS NOT NULL)::text
  FROM passageiro WHERE id = ${SUCCESS_ID};")" true \
  explicit_confirmation_timestamp_not_committed
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token
  WHERE user_role = 'PASSENGER' AND user_id = ${SUCCESS_ID}
    AND consumed_at IS NULL;")" 0 explicit_confirmation_token_not_consumed
SUCCESS_COMMITTED="$(state_fingerprint "$SUCCESS_ID")"
[[ "$SUCCESS_COMMITTED" != "$SUCCESS_BEFORE" ]] || fail explicit_confirmation_no_change
assert_equal "$(state_fingerprint "$SUCCESS_ID" ignore_confirmation_transition)" \
  "$SUCCESS_ALLOWED_BEFORE" explicit_confirmation_changed_forbidden_state

STATUS="$(http_request POST /auth/email-verifications/confirm \
  "$TMP_DIR/confirm-replay.json" "$SUCCESS_REQUEST" 198.51.100.31)"
assert_equal "$STATUS" 400 replay_status
assert_invalid_body "$TMP_DIR/confirm-replay.json" || fail replay_body
assert_response_excludes_token_material "$TMP_DIR/confirm-replay.json" \
  replay_token_reflected
assert_equal "$(state_fingerprint "$SUCCESS_ID")" "$SUCCESS_COMMITTED" \
  replay_changed_committed_state
assert_active_refresh_token "$SUCCESS_ID" replay_changed_active_session
record explicit_post_confirmation PASS
record replay_generic_invalid PASS

EXPIRED_REQUEST="{\"token\":\"${EXPIRED_TOKEN}\"}"
EXPIRED_BEFORE="$(state_fingerprint "$EXPIRED_ID" ignore_token_consumption)"
STATUS="$(http_request POST /auth/email-verifications/confirm \
  "$TMP_DIR/confirm-expired.json" "$EXPIRED_REQUEST" 198.51.100.32)"
assert_equal "$STATUS" 400 expired_status
assert_invalid_body "$TMP_DIR/confirm-expired.json" || fail expired_body
cmp -s "$TMP_DIR/confirm-replay.json" "$TMP_DIR/confirm-expired.json" \
  || fail expired_invalid_body_distinguishable
assert_response_excludes_token_material "$TMP_DIR/confirm-expired.json" \
  expired_token_reflected
assert_equal "$(db_scalar "SELECT email_verified::text FROM passageiro WHERE id = ${EXPIRED_ID};")" \
  false expired_verified_account
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token
  WHERE user_role = 'PASSENGER' AND user_id = ${EXPIRED_ID}
    AND consumed_at IS NULL;")" 0 expired_token_not_terminal
EXPIRED_AFTER="$(state_fingerprint "$EXPIRED_ID" ignore_token_consumption)"
assert_equal "$EXPIRED_AFTER" "$EXPIRED_BEFORE" \
  expired_changed_non_consumption_state
assert_active_refresh_token "$EXPIRED_ID" expired_changed_active_session
record expired_token_terminal PASS

CONCURRENT_BEFORE="$(state_fingerprint "$CONCURRENT_ID")"
CONCURRENT_ALLOWED_BEFORE="$(state_fingerprint \
  "$CONCURRENT_ID" ignore_confirmation_transition)"
CONCURRENT_REQUEST="{\"token\":\"${CONCURRENT_TOKEN}\"}"
CONCURRENT_REQUEST_FILE="$TMP_DIR/concurrent-request.json"
printf '%s' "$CONCURRENT_REQUEST" >"$CONCURRENT_REQUEST_FILE"
chmod 600 "$CONCURRENT_REQUEST_FILE"
LOCK_HOLDER_APP_NAME="email_intent_lock_${RUN_SLUG}"
[[ "$LOCK_HOLDER_APP_NAME" =~ ^[a-z0-9_-]+$ ]] \
  || fail invalid_lock_holder_name
(
  printf '%s\n' "BEGIN;
    SELECT id FROM passageiro WHERE id = ${CONCURRENT_ID} FOR UPDATE;
    SELECT pg_sleep(60);
    COMMIT;" \
    | docker exec --interactive --env "PGAPPNAME=${LOCK_HOLDER_APP_NAME}" \
        "$CONTAINER_ID" psql --username vanrides --dbname vanrides \
          --tuples-only --no-align --set ON_ERROR_STOP=1 --file -
) >"$TMP_DIR/lock-holder.stdout" 2>>"$TMP_DIR/db.stderr" &
LOCK_HOLDER_OS_PID=$!
ASYNC_PIDS="$LOCK_HOLDER_OS_PID"
LOCK_HOLDER_DB_PID=0
for _ in $(seq 1 100); do
  LOCK_HOLDER_DB_PID="$(db_scalar "SELECT coalesce(max(pid), 0)
    FROM pg_stat_activity
    WHERE application_name = '${LOCK_HOLDER_APP_NAME}'
      AND state = 'active' AND position('pg_sleep' in query) > 0;")"
  [[ "$LOCK_HOLDER_DB_PID" =~ ^[1-9][0-9]*$ ]] && break
  sleep 0.1
done
[[ "$LOCK_HOLDER_DB_PID" =~ ^[1-9][0-9]*$ ]] \
  || fail concurrent_lock_holder_not_ready
write_curl_config POST /auth/email-verifications/confirm \
  "$TMP_DIR/concurrent-a.curl-config" 198.51.100.33
write_curl_config POST /auth/email-verifications/confirm \
  "$TMP_DIR/concurrent-b.curl-config" 198.51.100.33
set +e
curl --disable --config "$TMP_DIR/concurrent-a.curl-config" \
  --noproxy '*' --silent --show-error --http1.1 --connect-timeout 5 --max-time 30 \
  --data-binary "@$CONCURRENT_REQUEST_FILE" \
  --dump-header "$TMP_DIR/concurrent-a.json.headers" \
  --output "$TMP_DIR/concurrent-a.json" --write-out '%{http_code}' \
  --stderr "$TMP_DIR/concurrent-a.curl.stderr" \
  >"$TMP_DIR/concurrent-a.status" &
CONCURRENT_PID_A=$!
ASYNC_PIDS="$LOCK_HOLDER_OS_PID $CONCURRENT_PID_A"
curl --disable --config "$TMP_DIR/concurrent-b.curl-config" \
  --noproxy '*' --silent --show-error --http1.1 --connect-timeout 5 --max-time 30 \
  --data-binary "@$CONCURRENT_REQUEST_FILE" \
  --dump-header "$TMP_DIR/concurrent-b.json.headers" \
  --output "$TMP_DIR/concurrent-b.json" --write-out '%{http_code}' \
  --stderr "$TMP_DIR/concurrent-b.curl.stderr" \
  >"$TMP_DIR/concurrent-b.status" &
CONCURRENT_PID_B=$!
ASYNC_PIDS="$LOCK_HOLDER_OS_PID $CONCURRENT_PID_A $CONCURRENT_PID_B"
set -e
WAITING_CONFIRMATIONS=0
for _ in $(seq 1 100); do
  WAITING_CONFIRMATIONS="$(db_scalar "SELECT count(*)
    FROM pg_stat_activity
    WHERE datname = 'vanrides' AND pid <> ${LOCK_HOLDER_DB_PID}
      AND state = 'active' AND wait_event_type = 'Lock'
      AND position('passageiro' in lower(query)) > 0;")"
  [[ "$WAITING_CONFIRMATIONS" =~ ^[2-9][0-9]*$ ]] && break
  sleep 0.1
done
[[ "$WAITING_CONFIRMATIONS" =~ ^[2-9][0-9]*$ ]] \
  || fail concurrent_requests_not_observed_waiting
assert_equal "$(db_scalar "SELECT CASE
  WHEN pg_terminate_backend(${LOCK_HOLDER_DB_PID}) THEN 'PASS' ELSE 'FAIL' END;")" \
  PASS concurrent_lock_holder_release
set +e
wait "$LOCK_HOLDER_OS_PID" >/dev/null 2>&1
wait "$CONCURRENT_PID_A"
CONCURRENT_RC_A=$?
wait "$CONCURRENT_PID_B"
CONCURRENT_RC_B=$?
set -e
ASYNC_PIDS=""
assert_equal "$CONCURRENT_RC_A" 0 concurrent_a_curl
assert_equal "$CONCURRENT_RC_B" 0 concurrent_b_curl
CONCURRENT_STATUS_A="$(<"$TMP_DIR/concurrent-a.status")"
CONCURRENT_STATUS_B="$(<"$TMP_DIR/concurrent-b.status")"
if [[ "$CONCURRENT_STATUS_A" == 200 && "$CONCURRENT_STATUS_B" == 400 ]]; then
  assert_success_body "$TMP_DIR/concurrent-a.json" \
    <(printf '%s' "$CONCURRENT_EMAIL") || fail concurrent_success_body
  assert_invalid_body "$TMP_DIR/concurrent-b.json" || fail concurrent_invalid_body
  CONCURRENT_INVALID_FILE="$TMP_DIR/concurrent-b.json"
elif [[ "$CONCURRENT_STATUS_A" == 400 && "$CONCURRENT_STATUS_B" == 200 ]]; then
  assert_invalid_body "$TMP_DIR/concurrent-a.json" || fail concurrent_invalid_body
  assert_success_body "$TMP_DIR/concurrent-b.json" \
    <(printf '%s' "$CONCURRENT_EMAIL") || fail concurrent_success_body
  CONCURRENT_INVALID_FILE="$TMP_DIR/concurrent-a.json"
else
  fail concurrent_outcomes_not_single_winner
fi
cmp -s "$TMP_DIR/confirm-replay.json" "$CONCURRENT_INVALID_FILE" \
  || fail concurrent_invalid_body_distinguishable
assert_response_excludes_token_material "$TMP_DIR/concurrent-a.json" \
  concurrent_a_token_reflected
assert_response_excludes_token_material "$TMP_DIR/concurrent-b.json" \
  concurrent_b_token_reflected
assert_equal "$(db_scalar "SELECT email_verified::text FROM passageiro WHERE id = ${CONCURRENT_ID};")" \
  true concurrent_account_not_verified
assert_equal "$(db_scalar "SELECT (email_verified_at IS NOT NULL)::text
  FROM passageiro WHERE id = ${CONCURRENT_ID};")" true \
  concurrent_account_timestamp_not_verified
assert_equal "$(db_scalar "SELECT count(*) FROM email_verification_token
  WHERE user_role = 'PASSENGER' AND user_id = ${CONCURRENT_ID}
    AND consumed_at IS NULL;")" 0 concurrent_token_not_consumed
assert_active_refresh_token "$CONCURRENT_ID" concurrent_changed_active_session
CONCURRENT_COMMITTED="$(state_fingerprint "$CONCURRENT_ID")"
[[ "$CONCURRENT_COMMITTED" != "$CONCURRENT_BEFORE" ]] \
  || fail concurrent_confirmation_no_change
assert_equal "$(state_fingerprint \
  "$CONCURRENT_ID" ignore_confirmation_transition)" \
  "$CONCURRENT_ALLOWED_BEFORE" concurrent_confirmation_changed_forbidden_state
STATUS="$(http_request POST /auth/email-verifications/confirm \
  "$TMP_DIR/concurrent-replay.json" "$CONCURRENT_REQUEST" 198.51.100.34)"
assert_equal "$STATUS" 400 concurrent_replay_status
assert_invalid_body "$TMP_DIR/concurrent-replay.json" || fail concurrent_replay_body
cmp -s "$TMP_DIR/confirm-replay.json" "$TMP_DIR/concurrent-replay.json" \
  || fail concurrent_replay_body_distinguishable
assert_response_excludes_token_material "$TMP_DIR/concurrent-replay.json" \
  concurrent_replay_token_reflected
assert_equal "$(state_fingerprint "$CONCURRENT_ID")" "$CONCURRENT_COMMITTED" \
  concurrent_replay_changed_committed_state
rm -f -- "$CONCURRENT_REQUEST_FILE" "$TMP_DIR/concurrent-a.curl-config" \
  "$TMP_DIR/concurrent-b.curl-config"
record concurrent_single_winner PASS
record concurrent_waiters_observed PASS
record session_artifacts_unchanged PASS

RATE_FINGERPRINT="$(global_state_fingerprint)"
for attempt in 1 2 3; do
  STATUS="$(http_request GET /auth/email-verifications/confirm \
    "$TMP_DIR/rate-get-${attempt}.html" "" 198.51.100.40)"
  assert_equal "$STATUS" 200 "rate_get_${attempt}_status"
  STATUS="$(http_request HEAD /auth/email-verifications/confirm \
    "$TMP_DIR/rate-head-${attempt}.body" "" 198.51.100.40)"
  assert_equal "$STATUS" 200 "rate_head_${attempt}_status"
done
STATUS="$(http_request OPTIONS /auth/email-verifications/confirm \
  "$TMP_DIR/rate-options.body" "" 198.51.100.40 "" "" preflight)"
assert_equal "$STATUS" 200 rate_options_status
assert_equal "$(header_value "$TMP_DIR/rate-options.body.headers" \
  access-control-allow-origin)" "https://app.vanrides.test" rate_options_cors_origin
[[ "$(header_value "$TMP_DIR/rate-options.body.headers" \
  access-control-allow-methods)" == *POST* ]] || fail rate_options_cors_method
assert_response_excludes_token_material "$TMP_DIR/rate-options.body" \
  rate_options_token_reflected
assert_equal "$(global_state_fingerprint)" "$RATE_FINGERPRINT" \
  rate_options_database_mutation
RATE_TOKEN="$(openssl rand -hex 32)"
RATE_TOKEN_HASH="$(sha256_text "$RATE_TOKEN")"
register_token_material "$RATE_TOKEN" "$RATE_TOKEN_HASH"
RATE_REQUEST="{\"token\":\"${RATE_TOKEN}\"}"
for attempt in 1 2 3; do
  assert_response_excludes_token_material "$TMP_DIR/rate-get-${attempt}.html" \
    "rate_get_${attempt}_token_reflected"
  assert_response_excludes_token_material "$TMP_DIR/rate-head-${attempt}.body" \
    "rate_head_${attempt}_token_reflected"
done
for attempt in 1 2; do
  STATUS="$(http_request POST /auth/email-verifications/confirm \
    "$TMP_DIR/rate-post-${attempt}.json" "$RATE_REQUEST" 198.51.100.40 \
    "" "" cors "rate-post-${attempt}")"
  assert_equal "$STATUS" 400 "rate_post_${attempt}_allowed"
  assert_invalid_body "$TMP_DIR/rate-post-${attempt}.json" \
    || fail "rate_post_${attempt}_body"
  cmp -s "$TMP_DIR/confirm-replay.json" "$TMP_DIR/rate-post-${attempt}.json" \
    || fail "rate_post_${attempt}_body_distinguishable"
  assert_response_excludes_token_material "$TMP_DIR/rate-post-${attempt}.json" \
    "rate_post_${attempt}_token_reflected"
done
STATUS="$(http_request POST /auth/email-verifications/confirm \
  "$TMP_DIR/rate-post-blocked.json" "$RATE_REQUEST" 198.51.100.40 \
  "" "" cors rate-post-blocked)"
assert_equal "$STATUS" 429 rate_post_blocked_status
assert_rate_limited_body "$TMP_DIR/rate-post-blocked.json" \
  || fail rate_post_blocked_body
[[ "$(header_value "$TMP_DIR/rate-post-blocked.json.headers" retry-after)" \
  =~ ^[1-9][0-9]*$ ]] || fail rate_post_retry_after
assert_equal "$(header_value "$TMP_DIR/rate-post-blocked.json.headers" \
  access-control-allow-origin)" "https://app.vanrides.test" rate_post_cors_origin
assert_equal "$(header_value "$TMP_DIR/rate-post-blocked.json.headers" \
  x-request-id)" rate-post-blocked rate_post_request_id
assert_equal "$(header_value "$TMP_DIR/rate-post-blocked.json.headers" \
  content-type)" "application/json;charset=UTF-8" rate_post_content_type
assert_response_excludes_token_material "$TMP_DIR/rate-post-blocked.json" \
  rate_post_blocked_token_reflected
assert_equal "$(global_state_fingerprint)" "$RATE_FINGERPRINT" \
  rate_lane_database_mutation
record get_head_quota_free PASS
record options_quota_free PASS
record post_rate_limit_429 PASS
record rate_limit_cors_contract PASS
record generic_invalid_byte_contract PASS
record full_state_fingerprints PASS

kill -0 "$APP_PID" >/dev/null 2>&1 || fail application_not_alive_final
assert_equal "$(sha256_file "$RUNTIME_JAR_PATH")" "$JAR_SHA256" \
  jar_changed_during_smoke
assert_equal "$(git_safe rev-parse HEAD)" "$BACKEND_SHA" \
  backend_sha_changed_during_smoke
assert_equal "$(git_safe rev-parse 'HEAD^{tree}')" "$BACKEND_TREE" \
  backend_tree_changed_during_smoke
assert_equal "$(git_safe hash-object --no-filters "$SCRIPT_PATH")" \
  "$RUNNER_WORKTREE_BLOB" runner_changed_during_smoke
assert_no_hidden_index_flags backend_index_flags_hidden_after
[[ -z "$(git_safe status --porcelain --untracked-files=all)" ]] \
  || fail backend_worktree_changed_during_smoke
record same_jar_runtime PASS
record sensitive_material_in_argv NONE
record token_material_in_evidence NONE
record remote_environment_mutation NONE
MAIN_RESULT=PASS
