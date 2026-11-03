#!/usr/bin/env bash

VANRIDES_E2E_EXIT_MISSING_ENV=81
VANRIDES_E2E_EXIT_BACKEND_CONNECTIVITY=82
VANRIDES_E2E_EXIT_MISSING_FIXTURE=83
VANRIDES_E2E_EXIT_MISSING_DEVICE=84
VANRIDES_E2E_EXIT_EMAIL_VERIFICATION=86

e2e_preflight_fail() {
  local exit_code="$1"
  local code="$2"
  shift 2

  echo "$code" >&2
  for line in "$@"; do
    echo "$line" >&2
  done
  exit "$exit_code"
}

e2e_trim_base_url() {
  local value="${1:-}"
  value="${value%/}"
  printf '%s\n' "$value"
}

e2e_fixture_value() {
  local key="$1"
  case "$key" in
    VANRIDES_FIXTURE_DRIVER_1_EMAIL) printf '%s' "${VANRIDES_FIXTURE_DRIVER_1_EMAIL:-${driver_1_email:-}}" ;;
    VANRIDES_FIXTURE_DRIVER_2_EMAIL) printf '%s' "${VANRIDES_FIXTURE_DRIVER_2_EMAIL:-${driver_2_email:-}}" ;;
    VANRIDES_FIXTURE_DRIVER_PASSWORD) printf '%s' "${VANRIDES_FIXTURE_DRIVER_PASSWORD:-${driver_password:-}}" ;;
    VANRIDES_FIXTURE_DRIVER_1_ID) printf '%s' "${VANRIDES_FIXTURE_DRIVER_1_ID:-${driver_1_id:-}}" ;;
    VANRIDES_FIXTURE_DRIVER_2_ID) printf '%s' "${VANRIDES_FIXTURE_DRIVER_2_ID:-${driver_2_id:-}}" ;;
    VANRIDES_FIXTURE_PASSENGER_EMAIL) printf '%s' "${VANRIDES_FIXTURE_PASSENGER_EMAIL:-${passenger_main_email:-}}" ;;
    VANRIDES_FIXTURE_PASSENGER_PASSWORD) printf '%s' "${VANRIDES_FIXTURE_PASSENGER_PASSWORD:-${passenger_password:-}}" ;;
    VANRIDES_FIXTURE_PASSENGER_NAME) printf '%s' "${VANRIDES_FIXTURE_PASSENGER_NAME:-${passenger_main_name:-}}" ;;
    VANRIDES_FIXTURE_PASSENGER_ID) printf '%s' "${VANRIDES_FIXTURE_PASSENGER_ID:-${passenger_main_id:-}}" ;;
    VANRIDES_FIXTURE_SERVICE_MORNING_NAME) printf '%s' "${VANRIDES_FIXTURE_SERVICE_MORNING_NAME:-${service_morning_name:-}}" ;;
    VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME) printf '%s' "${VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME:-${service_afternoon_name:-}}" ;;
    VANRIDES_FIXTURE_SERVICE_NIGHT_NAME) printf '%s' "${VANRIDES_FIXTURE_SERVICE_NIGHT_NAME:-${service_night_name:-}}" ;;
    GUARDIAN_E2E_EMAIL) printf '%s' "${GUARDIAN_E2E_EMAIL:-${guardian_email:-}}" ;;
    GUARDIAN_E2E_PASSWORD) printf '%s' "${GUARDIAN_E2E_PASSWORD:-${guardian_password:-}}" ;;
    GUARDIAN_E2E_PASSAGEIRO) printf '%s' "${GUARDIAN_E2E_PASSAGEIRO:-${guardian_passageiro_name:-}}" ;;
    GUARDIAN_E2E_PASSAGEIRO_ID) printf '%s' "${GUARDIAN_E2E_PASSAGEIRO_ID:-${guardian_passageiro_id:-}}" ;;
    VANRIDES_FIXTURE_DATE) printf '%s' "${VANRIDES_FIXTURE_DATE:-${fixture_date:-$(date +%F)}}" ;;
    *) printf '%s' "${!key:-}" ;;
  esac
}

e2e_suite_uses_guardian_fixture() {
  local suite="$1"
  [[ "$suite" == *guardian* || "$suite" == *operational_fixture* || "$suite" == "integration_test/all.dart" || "$suite" == "all.dart" ]]
}

e2e_suite_uses_operational_fixture() {
  local suite="$1"
  [[ "$suite" == *operational_fixture* ]]
}

e2e_required_fixture_keys() {
  local suite="$1"

  if e2e_suite_uses_guardian_fixture "$suite"; then
    printf '%s\n' \
      GUARDIAN_E2E_EMAIL \
      GUARDIAN_E2E_PASSWORD \
      GUARDIAN_E2E_PASSAGEIRO
  fi

  if e2e_suite_uses_operational_fixture "$suite"; then
    printf '%s\n' \
      VANRIDES_FIXTURE_DRIVER_1_EMAIL \
      VANRIDES_FIXTURE_DRIVER_2_EMAIL \
      VANRIDES_FIXTURE_DRIVER_PASSWORD \
      VANRIDES_FIXTURE_DRIVER_1_ID \
      VANRIDES_FIXTURE_DRIVER_2_ID \
      VANRIDES_FIXTURE_PASSENGER_EMAIL \
      VANRIDES_FIXTURE_PASSENGER_PASSWORD \
      VANRIDES_FIXTURE_PASSENGER_NAME \
      VANRIDES_FIXTURE_PASSENGER_ID
  fi
}

e2e_preflight_fixture_env() {
  local suite="$1"
  local missing=()
  local key

  while IFS= read -r key; do
    [[ -z "$key" ]] && continue
    if [[ -z "$(e2e_fixture_value "$key")" ]]; then
      missing+=("$key")
    fi
  done < <(e2e_required_fixture_keys "$suite")

  if [[ "${#missing[@]}" -gt 0 ]]; then
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_FIXTURE_ENV" \
      "Suite $suite requer fixture DEV antes de iniciar Flutter." \
      "Variaveis ausentes: ${missing[*]}" \
      "Rode a seed backend e carregue o fixture.env ou scripts/.dev.env.local."
  fi
}

e2e_curl_capture() {
  local body_file="$1"
  shift

  set +e
  VANRIDES_E2E_LAST_STATUS="$(curl -sS -o "$body_file" -w "%{http_code}" "$@" 2>"${body_file}.curl")"
  VANRIDES_E2E_LAST_CURL_CODE=$?
  set -e

  if [[ -z "$VANRIDES_E2E_LAST_STATUS" ]]; then
    VANRIDES_E2E_LAST_STATUS="000"
  fi
}

e2e_body_preview() {
  local body_file="$1"
  if [[ -s "$body_file" ]]; then
    sed -n '1,20p' "$body_file"
  fi
}

e2e_curl_preview() {
  local body_file="$1"
  if [[ -s "${body_file}.curl" ]]; then
    sed -n '1,20p' "${body_file}.curl"
  fi
}

e2e_preflight_backend_status() {
  local expected="$1"
  local description="$2"
  shift 2

  local body_file
  body_file="$(mktemp "${TMPDIR:-/tmp}/vanrides-e2e-preflight.XXXXXX")"
  e2e_curl_capture "$body_file" "$@"

  if [[ "$VANRIDES_E2E_LAST_STATUS" != "$expected" ]]; then
    local body_preview
    local curl_preview
    body_preview="$(e2e_body_preview "$body_file")"
    curl_preview="$(e2e_curl_preview "$body_file")"
    rm -f "$body_file" "${body_file}.curl"
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_BACKEND_CONNECTIVITY" \
      "BLOCKED_VALIDATION_BACKEND_CONNECTIVITY" \
      "Preflight backend falhou em: $description" \
      "Esperado HTTP $expected, recebido HTTP $VANRIDES_E2E_LAST_STATUS." \
      "${curl_preview:+curl: $curl_preview}" \
      "${body_preview:+resposta: $body_preview}"
  fi

  echo "PASS: $description -> $VANRIDES_E2E_LAST_STATUS"
  rm -f "$body_file" "${body_file}.curl"
}

e2e_preflight_backend() {
  local api_base_url
  api_base_url="$(e2e_trim_base_url "${1:-}")"

  if [[ -z "$api_base_url" ]]; then
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_ENV" \
      "VANRIDES_API_BASE_URL vazio."
  fi

  if ! command -v curl >/dev/null 2>&1; then
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_ENV" \
      "curl nao encontrado para executar preflight E2E."
  fi

  e2e_preflight_backend_status "200" "GET /motoristas" "$api_base_url/motoristas"
  e2e_preflight_backend_status "200" "OPTIONS /motoristas CORS" \
    -X OPTIONS "$api_base_url/motoristas" \
    -H 'Origin: https://app.exemplo' \
    -H 'Access-Control-Request-Method: GET'
  e2e_preflight_backend_status "400" "POST /auth/refresh existe" \
    -X POST "$api_base_url/auth/refresh" \
    -H 'Content-Type: application/json' \
    -d '{}'
}

e2e_preflight_login() {
  local api_base_url
  api_base_url="$(e2e_trim_base_url "${1:-}")"
  local email="${2:-}"
  local password="${3:-}"
  local label="${4:-fixture}"

  if [[ -z "$email" || -z "$password" ]]; then
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_FIXTURE_ENV" \
      "Credenciais ausentes para $label."
  fi

  local body_file
  body_file="$(mktemp "${TMPDIR:-/tmp}/vanrides-e2e-login.XXXXXX")"
  e2e_curl_capture "$body_file" \
    -X POST "$api_base_url/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"${email}\",\"senha\":\"${password}\"}"

  if [[ "$VANRIDES_E2E_LAST_STATUS" == "200" ]]; then
    echo "PASS: login fixture $label -> 200"
    rm -f "$body_file" "${body_file}.curl"
    return 0
  fi

  local body_preview
  local curl_preview
  body_preview="$(e2e_body_preview "$body_file")"
  curl_preview="$(e2e_curl_preview "$body_file")"
  if grep -q 'EMAIL_NOT_VERIFIED' "$body_file" 2>/dev/null; then
    rm -f "$body_file" "${body_file}.curl"
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_EMAIL_VERIFICATION" \
      "BLOCKED_VALIDATION_EMAIL_VERIFICATION" \
      "Fixture $label nao pode logar porque o e-mail ainda nao esta verificado." \
      "Email: $email" \
      "Acao: publicar DEV com VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev ou confirmar o e-mail."
  fi

  rm -f "$body_file" "${body_file}.curl"
  e2e_preflight_fail \
    "$VANRIDES_E2E_EXIT_MISSING_FIXTURE" \
    "BLOCKED_VALIDATION_MISSING_FIXTURE" \
    "Login da fixture $label falhou antes do Flutter." \
    "Email: $email" \
    "HTTP: $VANRIDES_E2E_LAST_STATUS" \
    "${curl_preview:+curl: $curl_preview}" \
    "${body_preview:+resposta: $body_preview}"
}

e2e_preflight_fixture_logins() {
  local api_base_url="$1"
  local suite="$2"

  if e2e_suite_uses_guardian_fixture "$suite"; then
    e2e_preflight_login \
      "$api_base_url" \
      "$(e2e_fixture_value GUARDIAN_E2E_EMAIL)" \
      "$(e2e_fixture_value GUARDIAN_E2E_PASSWORD)" \
      "guardian"
  fi

  if e2e_suite_uses_operational_fixture "$suite"; then
    e2e_preflight_login \
      "$api_base_url" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_1_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_PASSWORD)" \
      "driver_1"
    e2e_preflight_login \
      "$api_base_url" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_2_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_PASSWORD)" \
      "driver_2"
    e2e_preflight_login \
      "$api_base_url" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_PASSWORD)" \
      "passenger_main"
  fi
}

e2e_json_count_at_least() {
  local body_file="$1"
  local minimum="$2"
  local label="$3"
  python3 - "$body_file" "$minimum" "$label" <<'PY'
import json
import sys

body_file, minimum, label = sys.argv[1], int(sys.argv[2]), sys.argv[3]
with open(body_file, encoding="utf-8") as handle:
    payload = json.load(handle)
if not isinstance(payload, list):
    print(f"{label}: resposta nao e lista", file=sys.stderr)
    raise SystemExit(2)
if len(payload) < minimum:
    print(f"{label}: esperado ao menos {minimum}, recebido {len(payload)}", file=sys.stderr)
    raise SystemExit(1)
PY
}

e2e_preflight_collection() {
  local api_base_url="$1"
  local path="$2"
  local email="$3"
  local password="$4"
  local minimum="$5"
  local label="$6"

  local body_file
  body_file="$(mktemp "${TMPDIR:-/tmp}/vanrides-e2e-collection.XXXXXX")"
  e2e_curl_capture "$body_file" \
    -u "${email}:${password}" \
    "$api_base_url$path"

  if [[ "$VANRIDES_E2E_LAST_STATUS" != "200" ]]; then
    local body_preview
    local curl_preview
    body_preview="$(e2e_body_preview "$body_file")"
    curl_preview="$(e2e_curl_preview "$body_file")"
    rm -f "$body_file" "${body_file}.curl"
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_FIXTURE" \
      "BLOCKED_VALIDATION_MISSING_FIXTURE" \
      "Fixture $label nao retornou dados esperados." \
      "Endpoint: $path" \
      "HTTP: $VANRIDES_E2E_LAST_STATUS" \
      "${curl_preview:+curl: $curl_preview}" \
      "${body_preview:+resposta: $body_preview}"
  fi

  if ! e2e_json_count_at_least "$body_file" "$minimum" "$label"; then
    local body_preview
    body_preview="$(e2e_body_preview "$body_file")"
    rm -f "$body_file" "${body_file}.curl"
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_FIXTURE" \
      "BLOCKED_VALIDATION_MISSING_FIXTURE" \
      "Fixture $label existe, mas nao tem viagem disponivel para a data." \
      "Endpoint: $path" \
      "${body_preview:+resposta: $body_preview}"
  fi

  echo "PASS: fixture $label com viagens disponiveis"
  rm -f "$body_file" "${body_file}.curl"
}

e2e_preflight_trip_availability() {
  local api_base_url
  api_base_url="$(e2e_trim_base_url "$1")"
  local suite="$2"
  local date_value
  date_value="$(e2e_fixture_value VANRIDES_FIXTURE_DATE)"

  if e2e_suite_uses_operational_fixture "$suite"; then
    e2e_preflight_collection \
      "$api_base_url" \
      "/viagens/motorista/$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_1_ID)/dia?data=$date_value" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_1_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_PASSWORD)" \
      1 \
      "driver_1_day_trips"
    e2e_preflight_collection \
      "$api_base_url" \
      "/viagens/motorista/$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_2_ID)/dia?data=$date_value" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_2_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_PASSWORD)" \
      2 \
      "driver_2_day_trips"
    e2e_preflight_collection \
      "$api_base_url" \
      "/viagens/passageiro/$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_ID)/dia?data=$date_value" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_EMAIL)" \
      "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_PASSWORD)" \
      3 \
      "passenger_day_trips"
  elif [[ -n "$(e2e_fixture_value GUARDIAN_E2E_PASSAGEIRO_ID)" ]]; then
    e2e_preflight_collection \
      "$api_base_url" \
      "/viagens/passageiro/$(e2e_fixture_value GUARDIAN_E2E_PASSAGEIRO_ID)/dia?data=$date_value" \
      "$(e2e_fixture_value GUARDIAN_E2E_EMAIL)" \
      "$(e2e_fixture_value GUARDIAN_E2E_PASSWORD)" \
      2 \
      "guardian_day_trips"
  fi
}

e2e_preflight_device() {
  local device="${1:-}"
  if [[ -z "$device" ]]; then
    e2e_preflight_fail \
      "$VANRIDES_E2E_EXIT_MISSING_DEVICE" \
      "BLOCKED_VALIDATION_MISSING_DEVICE" \
      "Nenhum device disponivel para E2E." \
      "Defina VANRIDES_IOS_DEVICE_ID/VANRIDES_TEST_DEVICE ou passe --device <id>."
  fi
}

e2e_resolve_device() {
  local requested="${1:-}"
  if [[ -n "$requested" ]]; then
    printf '%s\n' "$requested"
    return 0
  fi

  if ! command -v flutter >/dev/null 2>&1; then
    return 0
  fi

  python3 - <<'PY'
import json
import subprocess
import sys

result = subprocess.run(
    ["flutter", "devices", "--machine"],
    check=False,
    capture_output=True,
    text=True,
)

if result.returncode != 0:
    sys.exit(0)

try:
    devices = json.loads(result.stdout)
except json.JSONDecodeError:
    sys.exit(0)

preferred = None

for device in devices:
    if device.get("targetPlatform") == "ios" and device.get("emulator"):
        preferred = device["id"]
        break

if preferred is None:
    for device in devices:
        if device.get("id") == "macos":
            preferred = device["id"]
            break

if preferred is None:
    for device in devices:
        if device.get("targetPlatform") == "ios":
            preferred = device["id"]
            break

if preferred:
    print(preferred)
PY
}

e2e_build_device_args() {
  local device="$1"
  local platform
  platform="$(python3 - "$device" <<'PY'
import json
import subprocess
import sys

try:
    result = subprocess.run(
        ["flutter", "devices", "--machine"], capture_output=True, text=True, timeout=60,
    )
    devices = json.loads(result.stdout) if result.returncode == 0 else []
    for device in devices:
        if device.get("id") == sys.argv[1] and device.get("isSupported", True):
            print(device.get("targetPlatform", ""))
            break
except (OSError, subprocess.TimeoutExpired, ValueError, AttributeError):
    pass
PY
)"
  if [[ -z "$platform" ]]; then
    e2e_preflight_fail "$VANRIDES_E2E_EXIT_MISSING_DEVICE" \
      "BLOCKED_VALIDATION_MISSING_DEVICE" \
      "Nao foi possivel confirmar a plataforma do device E2E selecionado."
  fi
  E2E_DEVICE_ARGS=("-d" "$device")
  if [[ "$platform" == android-* ]]; then
    E2E_DEVICE_ARGS+=("--flavor" "legacy")
  fi
}

e2e_add_dart_define() {
  local name="$1"
  local value="$2"

  if [[ -n "$value" ]]; then
    E2E_DART_DEFINES+=("--dart-define=$name=$value")
  fi
}

e2e_build_dart_defines() {
  local api_base_url="$1"
  E2E_DART_DEFINES=(
    "--dart-define=VANRIDES_API_BASE_URL=$api_base_url"
    "--dart-define=VANRIDES_E2E_TEST_MODE=true"
  )

  e2e_add_dart_define "VANRIDES_FIXTURE_DRIVER_1_EMAIL" "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_1_EMAIL)"
  e2e_add_dart_define "VANRIDES_FIXTURE_DRIVER_2_EMAIL" "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_2_EMAIL)"
  e2e_add_dart_define "VANRIDES_FIXTURE_DRIVER_PASSWORD" "$(e2e_fixture_value VANRIDES_FIXTURE_DRIVER_PASSWORD)"
  e2e_add_dart_define "VANRIDES_FIXTURE_PASSENGER_EMAIL" "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_EMAIL)"
  e2e_add_dart_define "VANRIDES_FIXTURE_PASSENGER_PASSWORD" "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_PASSWORD)"
  e2e_add_dart_define "VANRIDES_FIXTURE_PASSENGER_NAME" "$(e2e_fixture_value VANRIDES_FIXTURE_PASSENGER_NAME)"
  e2e_add_dart_define "VANRIDES_FIXTURE_SERVICE_MORNING_NAME" "$(e2e_fixture_value VANRIDES_FIXTURE_SERVICE_MORNING_NAME)"
  e2e_add_dart_define "VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME" "$(e2e_fixture_value VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME)"
  e2e_add_dart_define "VANRIDES_FIXTURE_SERVICE_NIGHT_NAME" "$(e2e_fixture_value VANRIDES_FIXTURE_SERVICE_NIGHT_NAME)"
  e2e_add_dart_define "GUARDIAN_E2E_EMAIL" "$(e2e_fixture_value GUARDIAN_E2E_EMAIL)"
  e2e_add_dart_define "GUARDIAN_E2E_PASSWORD" "$(e2e_fixture_value GUARDIAN_E2E_PASSWORD)"
  e2e_add_dart_define "GUARDIAN_E2E_PASSAGEIRO" "$(e2e_fixture_value GUARDIAN_E2E_PASSAGEIRO)"
  e2e_add_dart_define "VANRIDES_E2E_OPERATION_DATE" "$(e2e_fixture_value VANRIDES_FIXTURE_DATE)"
}

e2e_preflight_run() {
  local api_base_url="$1"
  local suite="$2"
  local device="$3"

  e2e_preflight_device "$device"
  e2e_preflight_backend "$api_base_url"
  e2e_preflight_fixture_env "$suite"
  e2e_preflight_fixture_logins "$api_base_url" "$suite"
  e2e_preflight_trip_availability "$api_base_url" "$suite"
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  e2e_preflight_run \
    "${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}" \
    "${1:-integration_test/all.dart}" \
    "$(e2e_resolve_device "${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}")"
fi
