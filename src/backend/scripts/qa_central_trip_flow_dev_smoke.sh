#!/usr/bin/env bash
set -euo pipefail

BASE_URL="${VANRIDES_BACKEND_BASE_URL:-http://127.0.0.1:8080}"
RUN_ADMIN_LINK_SMOKE="${RUN_ADMIN_LINK_SMOKE:-0}"
ADMIN_EMAIL="${VANRIDES_ADMIN_EMAIL:-}"
ADMIN_PASSWORD="${VANRIDES_ADMIN_PASSWORD:-}"

tmp_files=()

cleanup() {
  for file in "${tmp_files[@]}"; do
    rm -f "$file"
  done
}
trap cleanup EXIT

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

json_get_string() {
  python3 -c 'import json,sys; print((json.load(sys.stdin).get(sys.argv[1]) or ""))' "$1"
}

assert_status() {
  local expected="$1"
  local description="$2"
  shift 2

  local body_file
  body_file="$(mktemp)"
  tmp_files+=("$body_file")

  local status
  status="$(curl -sS -o "$body_file" -w "%{http_code}" "$@")"

  if [[ "$status" != "$expected" ]]; then
    echo "FAIL: $description (esperado=$expected, atual=$status)"
    echo "Resposta:"
    cat "$body_file"
    exit 1
  fi

  echo "PASS: $description -> $status"
  cat "$body_file"
}

post_json() {
  local url="$1"
  local body="$2"
  local body_file="$3"

  curl -sS -o "$body_file" -w "%{http_code}" \
    -X POST "$url" \
    -H 'Content-Type: application/json' \
    -d "$body"
}

expect_created_without_500() {
  local description="$1"
  local url="$2"
  local body="$3"

  local body_file
  body_file="$(mktemp)"
  tmp_files+=("$body_file")

  local status
  status="$(post_json "$url" "$body" "$body_file")"

  if [[ "$status" == "500" ]]; then
    echo "FAIL: $description retornou 500"
    cat "$body_file"
    exit 1
  fi

  if [[ "$status" != "200" && "$status" != "201" ]]; then
    echo "FAIL: $description (esperado 200/201 sem 500, atual=$status)"
    cat "$body_file"
    exit 1
  fi

  echo "PASS: $description -> $status"
  cat "$body_file"
}

login() {
  local email="$1"
  local password="$2"
  local body_file="$3"

  post_json "$BASE_URL/login" "{\"email\":\"$email\",\"senha\":\"$password\"}" "$body_file"
}

require_json_field_equals() {
  local body_file="$1"
  local field="$2"
  local expected="$3"
  local description="$4"

  local actual
  actual="$(json_get_string "$field" < "$body_file")"

  if [[ "$actual" != "$expected" ]]; then
    echo "FAIL: $description (esperado=$expected, atual=$actual)"
    cat "$body_file"
    exit 1
  fi

  echo "PASS: $description -> $actual"
}

timestamp="$(date +%s)"
password="VanridesSmoke123!"
passenger_email="qa.public.passenger.${timestamp}@example.com"
driver_real_email="qa.public.driver.${timestamp}@example.com"
driver_qa_email="qa.public.driver.${timestamp}@vanrides.dev"
phone_suffix="$(printf "%06d" $((timestamp % 1000000)))"

log "Smoke central de viagem em $BASE_URL"
assert_status "400" "POST /auth/refresh existe e valida payload vazio" \
  -X POST "$BASE_URL/auth/refresh" \
  -H 'Content-Type: application/json' \
  -d '{}'

log "Cadastro publico nao pode quebrar com email fora de vanrides.dev"
expect_created_without_500 "POST /passageiros com email example.com" \
  "$BASE_URL/passageiros" \
  "{\"nome\":\"QA Passageiro Publico $timestamp\",\"email\":\"$passenger_email\",\"telefone\":\"51991$phone_suffix\",\"senha\":\"$password\",\"endereco\":\"Rua dos Andradas, 1000\",\"bairro\":\"Centro\",\"cidade\":\"Porto Alegre\",\"estado\":\"RS\",\"cep\":\"90020-008\",\"latitude\":-30.0301,\"longitude\":-51.2300}"

expect_created_without_500 "POST /motoristas com email example.com" \
  "$BASE_URL/motoristas" \
  "{\"nome\":\"QA Motorista Publico $timestamp\",\"email\":\"$driver_real_email\",\"telefone\":\"51992$phone_suffix\",\"senha\":\"$password\"}"

log "Motorista publico QA deve nascer sem empresa operacional"
expect_created_without_500 "POST /motoristas com dominio auto-verificado QA" \
  "$BASE_URL/motoristas" \
  "{\"nome\":\"QA Motorista Sem Empresa $timestamp\",\"email\":\"$driver_qa_email\",\"telefone\":\"51993$phone_suffix\",\"senha\":\"$password\"}"

driver_login_body="$(mktemp)"
tmp_files+=("$driver_login_body")
driver_login_status="$(login "$driver_qa_email" "$password" "$driver_login_body")"

if [[ "$driver_login_status" != "200" ]]; then
  echo "FAIL: login do motorista QA sem empresa (esperado=200, atual=$driver_login_status)"
  cat "$driver_login_body"
  exit 1
fi

echo "PASS: login do motorista QA sem empresa -> 200"
require_json_field_equals "$driver_login_body" "tipoUsuario" "Motorista" "tipo de usuario do motorista"
require_json_field_equals "$driver_login_body" "companyLinkStatus" "NO_COMPANY" "motorista publico sem empresa"

if [[ "$RUN_ADMIN_LINK_SMOKE" == "1" ]]; then
  if [[ -z "$ADMIN_EMAIL" || -z "$ADMIN_PASSWORD" ]]; then
    echo "FAIL: RUN_ADMIN_LINK_SMOKE=1 exige VANRIDES_ADMIN_EMAIL e VANRIDES_ADMIN_PASSWORD"
    exit 1
  fi

  log "Vinculo opcional de motorista existente pela empresa"
  admin_login_body="$(mktemp)"
  tmp_files+=("$admin_login_body")
  admin_login_status="$(login "$ADMIN_EMAIL" "$ADMIN_PASSWORD" "$admin_login_body")"

  if [[ "$admin_login_status" != "200" ]]; then
    echo "FAIL: login admin (esperado=200, atual=$admin_login_status)"
    cat "$admin_login_body"
    exit 1
  fi

  admin_token="$(json_get_string "accessToken" < "$admin_login_body")"
  if [[ -z "$admin_token" ]]; then
    echo "FAIL: login admin nao retornou accessToken"
    cat "$admin_login_body"
    exit 1
  fi
  echo "PASS: login admin com token"

  link_body="$(mktemp)"
  tmp_files+=("$link_body")
  link_status="$(curl -sS -o "$link_body" -w "%{http_code}" \
    -X POST "$BASE_URL/company-admins/motoristas" \
    -H 'Content-Type: application/json' \
    -H "Authorization: Bearer $admin_token" \
    -d "{\"nome\":\"QA Motorista Sem Empresa $timestamp\",\"email\":\"$driver_qa_email\",\"telefone\":\"51993$phone_suffix\"}")"

  if [[ "$link_status" != "201" ]]; then
    echo "FAIL: vincular motorista existente sem senha (esperado=201, atual=$link_status)"
    cat "$link_body"
    exit 1
  fi
  echo "PASS: vincular motorista existente sem senha -> 201"

  linked_login_body="$(mktemp)"
  tmp_files+=("$linked_login_body")
  linked_login_status="$(login "$driver_qa_email" "$password" "$linked_login_body")"

  if [[ "$linked_login_status" != "200" ]]; then
    echo "FAIL: login do motorista apos vinculo (esperado=200, atual=$linked_login_status)"
    cat "$linked_login_body"
    exit 1
  fi

  require_json_field_equals "$linked_login_body" "companyLinkStatus" "COMPANY_LINKED" "motorista vinculado pela empresa"
fi

log "Smoke central finalizado com sucesso"
