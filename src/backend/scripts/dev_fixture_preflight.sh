#!/usr/bin/env bash

VANRIDES_FIXTURE_EXIT_MISSING_ENV=81
VANRIDES_FIXTURE_EXIT_BACKEND_CONNECTIVITY=82

vanrides_fixture_fail() {
  local exit_code="$1"
  local code="$2"
  shift 2

  echo "$code" >&2
  for line in "$@"; do
    echo "$line" >&2
  done
  exit "$exit_code"
}

vanrides_fixture_trim_base_url() {
  local value="${1:-}"
  value="${value%/}"
  printf '%s\n' "$value"
}

vanrides_fixture_curl_capture() {
  local body_file="$1"
  shift

  set +e
  VANRIDES_FIXTURE_LAST_STATUS="$(curl -sS -o "$body_file" -w "%{http_code}" "$@" 2>"${body_file}.curl")"
  VANRIDES_FIXTURE_LAST_CURL_CODE=$?
  set -e

  if [[ -z "$VANRIDES_FIXTURE_LAST_STATUS" ]]; then
    VANRIDES_FIXTURE_LAST_STATUS="000"
  fi
}

vanrides_fixture_assert_status() {
  local expected="$1"
  local description="$2"
  shift 2

  local body_file
  body_file="$(mktemp "${TMPDIR:-/tmp}/vanrides-fixture-preflight.XXXXXX")"
  vanrides_fixture_curl_capture "$body_file" "$@"

  if [[ "$VANRIDES_FIXTURE_LAST_STATUS" != "$expected" ]]; then
    local body_preview=""
    local curl_preview=""
    if [[ -s "$body_file" ]]; then
      body_preview="$(sed -n '1,20p' "$body_file")"
    fi
    if [[ -s "${body_file}.curl" ]]; then
      curl_preview="$(sed -n '1,20p' "${body_file}.curl")"
    fi
    rm -f "$body_file" "${body_file}.curl"
    vanrides_fixture_fail \
      "$VANRIDES_FIXTURE_EXIT_BACKEND_CONNECTIVITY" \
      "BLOCKED_VALIDATION_BACKEND_CONNECTIVITY" \
      "Fixture preflight falhou em: $description" \
      "Esperado HTTP $expected, recebido HTTP $VANRIDES_FIXTURE_LAST_STATUS." \
      "${curl_preview:+curl: $curl_preview}" \
      "${body_preview:+resposta: $body_preview}"
  fi

  echo "PASS: $description -> $VANRIDES_FIXTURE_LAST_STATUS"
  rm -f "$body_file" "${body_file}.curl"
}

vanrides_fixture_preflight_backend() {
  local base_url
  base_url="$(vanrides_fixture_trim_base_url "${1:-}")"
  local label="${2:-fixture}"

  if [[ -z "$base_url" ]]; then
    vanrides_fixture_fail \
      "$VANRIDES_FIXTURE_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_ENV" \
      "BASE_URL vazio para $label."
  fi

  if ! command -v curl >/dev/null 2>&1; then
    vanrides_fixture_fail \
      "$VANRIDES_FIXTURE_EXIT_MISSING_ENV" \
      "BLOCKED_VALIDATION_MISSING_ENV" \
      "curl nao encontrado para executar preflight de $label."
  fi

  echo "[$label] Preflight backend em $base_url..."
  vanrides_fixture_assert_status "200" "GET /motoristas ($label)" \
    "$base_url/motoristas"
  vanrides_fixture_assert_status "200" "OPTIONS /motoristas CORS ($label)" \
    -X OPTIONS "$base_url/motoristas" \
    -H 'Origin: https://app.exemplo' \
    -H 'Access-Control-Request-Method: GET'
  vanrides_fixture_assert_status "400" "POST /auth/refresh existe ($label)" \
    -X POST "$base_url/auth/refresh" \
    -H 'Content-Type: application/json' \
    -d '{}'
}

if [[ "${BASH_SOURCE[0]}" == "$0" ]]; then
  vanrides_fixture_preflight_backend "${1:-${BASE_URL:-http://127.0.0.1:8080}}" "${2:-fixture}"
fi
