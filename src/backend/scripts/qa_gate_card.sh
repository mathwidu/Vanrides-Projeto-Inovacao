#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${VANRIDES_BACKEND_BASE_URL:-http://127.0.0.1:8080}"
DEEP_AUTH_SMOKE="${DEEP_AUTH_SMOKE:-0}"
ADMIN_PORTAL_SMOKE="${ADMIN_PORTAL_SMOKE:-0}"
GUARDIAN_AUTH_SMOKE="${GUARDIAN_AUTH_SMOKE:-0}"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

assert_status() {
  local expected="$1"
  local description="$2"
  shift 2

  local body_file
  body_file="$(mktemp)"
  local status
  status="$(curl -sS -o "$body_file" -w "%{http_code}" "$@")"

  if [[ "$status" != "$expected" ]]; then
    echo "FAIL: $description (esperado=$expected, atual=$status)"
    echo "Resposta:"
    cat "$body_file"
    rm -f "$body_file"
    exit 1
  fi

  echo "PASS: $description -> $status"
  rm -f "$body_file"
}

log "Backend canonical gate without external HTTP smoke"
cd "$ROOT_DIR"
"$ROOT_DIR/scripts/ci_quality_gate.sh"

log "Smoke HTTP básico em $BASE_URL"
assert_status "200" "GET /motoristas" "$BASE_URL/motoristas"
assert_status "200" "OPTIONS /motoristas (CORS preflight)" \
  -X OPTIONS "$BASE_URL/motoristas" \
  -H 'Origin: https://app.exemplo' \
  -H 'Access-Control-Request-Method: GET'
assert_status "400" "POST /auth/refresh com body vazio (endpoint existe)" \
  -X POST "$BASE_URL/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d '{}'

if [[ "$DEEP_AUTH_SMOKE" == "1" ]]; then
  log "Smoke AUTH profundo (create/login/bearer/refresh/basic)"

  ts="$(date +%s)"
  email="qa.gate.${ts}@vanrides.dev"
  pass="VanridesGate123"
  phone="51966$(printf "%06d" $((ts % 1000000)))"

  create_status="$(curl -sS -o /tmp/vr_gate_create.json -w "%{http_code}" \
    -X POST "$BASE_URL/motoristas" \
    -H 'Content-Type: application/json' \
    -d "{\"nome\":\"QA Gate $ts\",\"email\":\"$email\",\"telefone\":\"$phone\",\"senha\":\"$pass\"}")"
  if [[ "$create_status" != "200" ]]; then
    echo "FAIL: criação de motorista de teste ($create_status)"
    cat /tmp/vr_gate_create.json
    exit 1
  fi
  echo "PASS: criação de motorista de teste -> 200"

  login_json="$(curl -sS -X POST "$BASE_URL/login" \
    -H 'Content-Type: application/json' \
    -d "{\"email\":\"$email\",\"senha\":\"$pass\"}")"
  access="$(echo "$login_json" | sed -n 's/.*"accessToken":"\([^"]*\)".*/\1/p')"
  refresh1="$(echo "$login_json" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')"

  if [[ -z "$access" || -z "$refresh1" ]]; then
    echo "FAIL: login sem tokens"
    echo "$login_json"
    exit 1
  fi
  echo "PASS: login com tokens"

  user_id="$(echo "$login_json" | sed -n 's/.*"id":\([0-9][0-9]*\).*/\1/p')"
  hoje="$(date '+%Y-%m-%d')"
  if [[ -z "$user_id" ]]; then
    echo "FAIL: login sem id de usuário"
    echo "$login_json"
    exit 1
  fi

  assert_status "200" "GET /presencas/motorista/{id}?data=hoje com Bearer válido" \
    -H "Authorization: Bearer $access" \
    "$BASE_URL/presencas/motorista/$user_id?data=$hoje"
  assert_status "200" "GET /account/me com Bearer válido" \
    -H "Authorization: Bearer $access" \
    "$BASE_URL/account/me"

  refresh_json="$(curl -sS -X POST "$BASE_URL/auth/refresh" \
    -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$refresh1\"}")"
  refresh2="$(echo "$refresh_json" | sed -n 's/.*"refreshToken":"\([^"]*\)".*/\1/p')"
  if [[ -z "$refresh2" ]]; then
    echo "FAIL: refresh não retornou novo token"
    echo "$refresh_json"
    exit 1
  fi
  echo "PASS: refresh com rotação"

  assert_status "401" "POST /auth/refresh com token antigo" \
    -X POST "$BASE_URL/auth/refresh" \
    -H 'Content-Type: application/json' \
    -d "{\"refreshToken\":\"$refresh1\"}"

  basic="$(printf "%s:%s" "$email" "$pass" | base64)"
  assert_status "200" "GET /presencas/motorista/{id}?data=hoje com Basic (compat)" \
    -H "Authorization: Basic $basic" \
    "$BASE_URL/presencas/motorista/$user_id?data=$hoje"
  assert_status "200" "GET /account/me com Basic (compat)" \
    -H "Authorization: Basic $basic" \
    "$BASE_URL/account/me"
fi

if [[ "$ADMIN_PORTAL_SMOKE" == "1" ]]; then
  log "Smoke Admin Core (portal empresa)"
  GUARDIAN_AUTH_SMOKE="$GUARDIAN_AUTH_SMOKE" bash "$ROOT_DIR/scripts/qa_admin_portal_smoke.sh"
fi

log "Gate backend finalizado com sucesso"
