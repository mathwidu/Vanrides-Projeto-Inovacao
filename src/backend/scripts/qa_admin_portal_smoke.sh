#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BASE_URL="${VANRIDES_BACKEND_BASE_URL:-http://127.0.0.1:8080}"
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

log "Smoke Admin Core em $BASE_URL"
SMOKE_RUN_ID="$(date +%s)-$$"
LEGACY_BOOTSTRAP_SLUG="legacy-bootstrap-must-fail-${SMOKE_RUN_ID}"

# Conceito: esses probes usam respostas 400/401 para provar que a rota existe.
# Se o ambiente responder 404, sabemos que o bloco administrativo ainda não foi
# publicado corretamente em DEV/PROD.

assert_status "400" "POST /company-admins/bootstrap com payload vazio (endpoint existe e valida payload)" \
  -X POST "$BASE_URL/company-admins/bootstrap" \
  -H 'Content-Type: application/json' \
  -d '{}'

assert_status "400" "POST /company-admins/bootstrap com companySlug legado (seletor rejeitado)" \
  -X POST "$BASE_URL/company-admins/bootstrap" \
  -H 'Content-Type: application/json' \
  -d "{\"nome\":\"Legacy Smoke\",\"email\":\"legacy.bootstrap.${SMOKE_RUN_ID}@vanrides.dev\",\"telefone\":\"51999999999\",\"senha\":\"Admin!123\",\"companySlug\":\"${LEGACY_BOOTSTRAP_SLUG}\"}"

assert_status "401" "GET /company-admins/passageiros sem auth (endpoint protegido existe)" \
  "$BASE_URL/company-admins/passageiros"

assert_status "401" "GET /company-admins/responsaveis sem auth (endpoint protegido existe)" \
  "$BASE_URL/company-admins/responsaveis"

assert_status "401" "GET /company-admins/vinculos/passageiros-motoristas sem auth (endpoint protegido existe)" \
  "$BASE_URL/company-admins/vinculos/passageiros-motoristas"

assert_status "401" "GET /company-admins/vinculos/responsaveis-passageiros sem auth (endpoint protegido existe)" \
  "$BASE_URL/company-admins/vinculos/responsaveis-passageiros"

if [[ "$GUARDIAN_AUTH_SMOKE" == "1" ]]; then
  assert_status "401" "PUT /responsaveis/minha-senha sem auth (endpoint protegido existe)" \
    -X PUT "$BASE_URL/responsaveis/minha-senha" \
    -H 'Content-Type: application/json' \
    -d '{"senhaAtual":"teste","novaSenha":"NovaSenha@123"}'
fi

log "Smoke Admin Core finalizado com sucesso"
