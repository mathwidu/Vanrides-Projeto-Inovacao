#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
RUN_BACKEND_GATES="${RUN_BACKEND_GATES:-1}"
RUN_CENTRAL_FLOW_SMOKE="${RUN_CENTRAL_FLOW_SMOKE:-1}"
RUN_APP_GATES="${RUN_APP_GATES:-1}"
RUN_PORTAL_E2E="${RUN_PORTAL_E2E:-1}"
RUN_GUARDIAN_MOBILE="${RUN_GUARDIAN_MOBILE:-1}"
RUN_GUARDIAN_E2E="${RUN_GUARDIAN_E2E:-0}"
RUN_FULL_MOBILE_E2E="${RUN_FULL_MOBILE_E2E:-0}"
PRINT_CONFIG=0
DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
TS="$(date '+%Y%m%d-%H%M%S')"
REPORT_DIR="${QA_RELEASE_REPORT_DIR:-$APP_DIR/output/release-readiness/$TS}"
FIXTURE_LOG="$REPORT_DIR/fixture.log"
FIXTURE_ENV="$REPORT_DIR/fixture.env"
SUMMARY_FILE="$REPORT_DIR/SUMMARY.md"
BLOCKED_VALIDATION_EXIT_CODE=86

resolve_backend_dir() {
  local backend_dir="${VANRIDES_BACKEND_DIR:-$APP_DIR/../backend}"
  if [[ ! -d "$backend_dir" ]]; then
    printf 'Backend local ausente: %s\n' "$backend_dir" >&2
    return 2
  fi
  (cd "$backend_dir" && pwd)
}

BACKEND_DIR="$(resolve_backend_dir)"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_release_campaign.sh [opcoes]

Opcoes:
  --api <url>              Backend alvo. Padrao: http://127.0.0.1:8080
  --backend-dir <path>     Caminho do repo backend. Padrao: auto-detect workspace/worktree.
  --device <id>            Device para E2E mobile opcional. Sem valor, os wrappers tentam detectar simulator/macOS.
  --skip-backend-gates     Pula gates backend.
  --skip-central-flow-smoke Pula smoke central de cadastro/vinculo/viagem.
  --skip-app-gates         Pula gates app.
  --skip-portal-e2e        Pula E2E web do portal.
  --mobile-e2e             Roda E2E guardian e a suite operacional da fixture.
  --print-config           Exibe caminhos/opcoes resolvidos e sai.
  -h, --help               Mostra esta ajuda.

Variaveis equivalentes:
  VANRIDES_API_BASE_URL, VANRIDES_BACKEND_DIR, VANRIDES_TEST_DEVICE,
  RUN_BACKEND_GATES, RUN_CENTRAL_FLOW_SMOKE, RUN_APP_GATES, RUN_PORTAL_E2E, RUN_GUARDIAN_E2E,
  RUN_FULL_MOBILE_E2E, QA_RELEASE_REPORT_DIR.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api)
      API_BASE_URL="$2"
      shift 2
      ;;
    --backend-dir)
      BACKEND_DIR="$2"
      shift 2
      ;;
    --device)
      DEVICE="$2"
      shift 2
      ;;
    --skip-backend-gates)
      RUN_BACKEND_GATES=0
      shift
      ;;
    --skip-central-flow-smoke)
      RUN_CENTRAL_FLOW_SMOKE=0
      shift
      ;;
    --skip-app-gates)
      RUN_APP_GATES=0
      shift
      ;;
    --skip-portal-e2e)
      RUN_PORTAL_E2E=0
      shift
      ;;
    --mobile-e2e)
      RUN_GUARDIAN_E2E=1
      RUN_FULL_MOBILE_E2E=1
      shift
      ;;
    --print-config)
      PRINT_CONFIG=1
      shift
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    *)
      echo "Opcao desconhecida: $1" >&2
      usage >&2
      exit 2
      ;;
  esac
done

if [[ ! -d "$BACKEND_DIR" ]]; then
  echo "Backend nao encontrado em $BACKEND_DIR" >&2
  exit 1
fi

if [[ "$PRINT_CONFIG" == "1" ]]; then
  cat <<EOF
APP_DIR=$APP_DIR
BACKEND_DIR=$BACKEND_DIR
API_BASE_URL=$API_BASE_URL
RUN_BACKEND_GATES=$RUN_BACKEND_GATES
RUN_CENTRAL_FLOW_SMOKE=$RUN_CENTRAL_FLOW_SMOKE
RUN_APP_GATES=$RUN_APP_GATES
RUN_PORTAL_E2E=$RUN_PORTAL_E2E
RUN_GUARDIAN_MOBILE=$RUN_GUARDIAN_MOBILE
RUN_GUARDIAN_E2E=$RUN_GUARDIAN_E2E
RUN_FULL_MOBILE_E2E=$RUN_FULL_MOBILE_E2E
DEVICE=$DEVICE
EOF
  exit 0
fi

mkdir -p "$REPORT_DIR"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

run_step() {
  local name="$1"
  shift
  local log_file="$REPORT_DIR/${name//[^a-zA-Z0-9_-]/_}.log"

  log "$name"
  {
    printf '[qa-release] step=%s\n' "$name"
    printf '[qa-release] started_at=%s\n' "$(date -Iseconds)"
    "$@"
  } 2>&1 | tee "$log_file"
}

append_summary() {
  printf '%s\n' "$1" >> "$SUMMARY_FILE"
}

extract_error_email() {
  local log_file="$1"
  grep -Eo '"email":"[^"]+"' "$log_file" | tail -n 1 | sed 's/^"email":"//;s/"$//' || true
}

cat > "$SUMMARY_FILE" <<EOF
# VanRides Release Readiness Campaign

- Started: $(date -Iseconds)
- API: $API_BASE_URL
- App: $APP_DIR
- Backend: $BACKEND_DIR
- Report dir: $REPORT_DIR

## Steps
EOF

if [[ "$RUN_BACKEND_GATES" == "1" ]]; then
  run_step "backend-qa-gate" env \
    VANRIDES_BACKEND_BASE_URL="$API_BASE_URL" \
    DEEP_AUTH_SMOKE=1 \
    ADMIN_PORTAL_SMOKE=1 \
    GUARDIAN_AUTH_SMOKE=1 \
    bash "$BACKEND_DIR/scripts/qa_gate_card.sh"
  append_summary "- [x] Backend gate completo"

  run_step "backend-guardian-suite" bash "$BACKEND_DIR/scripts/qa_guardian_suite.sh"
  append_summary "- [x] Backend guardian suite"

  run_step "backend-rbac-suite" bash "$BACKEND_DIR/scripts/qa_rbac_suite.sh"
  append_summary "- [x] Backend RBAC suite"
else
  append_summary "- [ ] Backend gates pulados"
fi

if [[ "$RUN_CENTRAL_FLOW_SMOKE" == "1" ]]; then
  if [[ ! -x "$BACKEND_DIR/scripts/qa_central_trip_flow_dev_smoke.sh" ]]; then
    echo "Smoke central nao encontrado ou sem permissao de execucao: $BACKEND_DIR/scripts/qa_central_trip_flow_dev_smoke.sh" >&2
    echo "Publique/aponte para a branch backend que contem o smoke central ou rode com --skip-central-flow-smoke." >&2
    exit 1
  fi

  run_step "backend-central-trip-flow-public-smoke" env \
    VANRIDES_BACKEND_BASE_URL="$API_BASE_URL" \
    bash "$BACKEND_DIR/scripts/qa_central_trip_flow_dev_smoke.sh"
  append_summary "- [x] Smoke central publico de cadastro/vinculo pendente"
else
  append_summary "- [ ] Smoke central publico pulado"
fi

if ! run_step "backend-operational-service-day-fixture" env \
  BASE_URL="$API_BASE_URL" \
  bash "$BACKEND_DIR/scripts/seed_operational_service_day_fixture.sh"; then
  fixture_step_log="$REPORT_DIR/backend-operational-service-day-fixture.log"
  append_summary "- [ ] Campanha interrompida na fixture operacional de serviços"
  append_summary "- [ ] Fixture operacional de serviços falhou"
  append_summary "  - Log: \`$fixture_step_log\`"
  if grep -q 'EMAIL_NOT_VERIFIED' "$fixture_step_log"; then
    blocked_email="$(extract_error_email "$fixture_step_log")"
    append_summary "  - Codigo: \`EMAIL_NOT_VERIFIED\`"
    append_summary "  - Email bloqueado: ${blocked_email:-n/a}"
    append_summary "  - Diagnostico: DEV ainda bloqueia login do admin bootstrap por e-mail pendente."
    append_summary "  - Acao: publicar backend com \`VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev\` ou confirmar o e-mail manualmente."
    echo "BLOCKED_VALIDATION_EMAIL_VERIFICATION" >&2
    echo "Fixture falhou por EMAIL_NOT_VERIFIED. Publique o backend DEV com VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS=vanrides.dev ou confirme o e-mail manualmente." >&2
    echo "Email bloqueado: ${blocked_email:-n/a}" >&2
    echo "Resumo parcial: $SUMMARY_FILE" >&2
    exit "$BLOCKED_VALIDATION_EXIT_CODE"
  fi
  echo "Resumo parcial: $SUMMARY_FILE" >&2
  exit 1
fi
cp "$REPORT_DIR/backend-operational-service-day-fixture.log" "$FIXTURE_LOG"
grep -E '^(base_url|fixture_date|company_id|admin_id|company_slug|admin_email|admin_password|driver_password|passenger_password|guardian_password|driver_1_id|driver_1_email|driver_2_id|driver_2_email|passenger_main_id|passenger_main_name|passenger_main_email|passenger_support_id|passenger_support_name|passenger_support_email|guardian_id|guardian_email|guardian_passageiro_id|guardian_passageiro_name|service_morning_id|service_afternoon_id|service_night_id|trip_morning_id|trip_afternoon_id|trip_night_id|trip_morning_status|trip_morning_snapshot_operacional|night_auto_ignored_motivo|passenger_services_count|passenger_day_trip_count|support_day_trip_count|guardian_day_trip_count|driver_1_day_trip_count|driver_2_day_trip_count|concept)=' "$FIXTURE_LOG" > "$FIXTURE_ENV"
append_summary "- [x] Fixture operacional de serviços criada: \`$FIXTURE_ENV\`"

set -a
source "$FIXTURE_ENV"
set +a

if [[ "$RUN_CENTRAL_FLOW_SMOKE" == "1" ]]; then
  run_step "backend-central-trip-flow-admin-link-smoke" env \
    VANRIDES_BACKEND_BASE_URL="$API_BASE_URL" \
    RUN_ADMIN_LINK_SMOKE=1 \
    VANRIDES_ADMIN_EMAIL="${admin_email:-}" \
    VANRIDES_ADMIN_PASSWORD="${admin_password:-}" \
    bash "$BACKEND_DIR/scripts/qa_central_trip_flow_dev_smoke.sh"
  append_summary "- [x] Smoke central com vinculo de motorista pela empresa"
else
  append_summary "- [ ] Smoke central com vinculo admin pulado"
fi

if [[ "$RUN_APP_GATES" == "1" ]]; then
  run_step "app-qa-gate-no-e2e" bash "$APP_DIR/scripts/qa_gate_card.sh" --no-e2e --api "$API_BASE_URL"
  append_summary "- [x] App analyze/test gate"
else
  append_summary "- [ ] App gates pulados"
fi

if [[ "$RUN_PORTAL_E2E" == "1" ]]; then
  run_step "app-admin-portal-web-e2e" env \
    VANRIDES_API_BASE_URL="$API_BASE_URL" \
    bash "$APP_DIR/scripts/qa_admin_portal_web_e2e.sh"
  append_summary "- [x] Portal admin web E2E"
else
  append_summary "- [ ] Portal admin web E2E pulado"
fi

if [[ "$RUN_GUARDIAN_MOBILE" == "1" ]]; then
  guardian_cmd=(bash "$APP_DIR/scripts/qa_guardian_mobile_suite.sh")
  if [[ -n "$DEVICE" ]]; then
    guardian_cmd+=("$DEVICE")
  fi

  run_step "app-guardian-mobile-suite" env \
    VANRIDES_API_BASE_URL="$API_BASE_URL" \
    RUN_GUARDIAN_E2E="$RUN_GUARDIAN_E2E" \
    GUARDIAN_E2E_EMAIL="${guardian_email:-}" \
    GUARDIAN_E2E_PASSWORD="${guardian_password:-}" \
    GUARDIAN_E2E_PASSAGEIRO="${guardian_passageiro_name:-}" \
    "${guardian_cmd[@]}"
  append_summary "- [x] Guardian mobile suite (RUN_GUARDIAN_E2E=$RUN_GUARDIAN_E2E)"
else
  append_summary "- [ ] Guardian mobile suite pulada"
fi

if [[ "$RUN_FULL_MOBILE_E2E" == "1" ]]; then
  mobile_cmd=(
    bash "$APP_DIR/scripts/qa_gate_card.sh"
    --e2e
    --suite integration_test/operational_fixture/all.dart
    --api "$API_BASE_URL"
  )
  if [[ -n "$DEVICE" ]]; then
    mobile_cmd+=(--device "$DEVICE")
  fi

  run_step "app-operational-fixture-mobile-e2e" env \
    VANRIDES_FIXTURE_DRIVER_1_EMAIL="${driver_1_email:-}" \
    VANRIDES_FIXTURE_DRIVER_2_EMAIL="${driver_2_email:-}" \
    VANRIDES_FIXTURE_DRIVER_PASSWORD="${driver_password:-}" \
    VANRIDES_FIXTURE_PASSENGER_EMAIL="${passenger_main_email:-}" \
    VANRIDES_FIXTURE_PASSENGER_PASSWORD="${passenger_password:-}" \
    VANRIDES_FIXTURE_PASSENGER_NAME="${passenger_main_name:-}" \
    GUARDIAN_E2E_EMAIL="${guardian_email:-}" \
    GUARDIAN_E2E_PASSWORD="${guardian_password:-}" \
    GUARDIAN_E2E_PASSAGEIRO="${guardian_passageiro_name:-}" \
    "${mobile_cmd[@]}"
  append_summary "- [x] Operational fixture mobile E2E"
else
  append_summary "- [ ] Operational fixture mobile E2E pulado"
fi

cat >> "$SUMMARY_FILE" <<EOF

## Fixture

- Admin: ${admin_email:-n/a} / ${admin_password:-n/a}
- Driver M1: ${driver_1_email:-n/a} / ${driver_password:-n/a}
- Driver M2: ${driver_2_email:-n/a} / ${driver_password:-n/a}
- Passenger principal: ${passenger_main_email:-n/a} / ${passenger_password:-n/a}
- Guardian: ${guardian_email:-n/a} / ${guardian_password:-n/a}
- Serviços: manhã=${service_morning_id:-n/a}, tarde=${service_afternoon_id:-n/a}, noite=${service_night_id:-n/a}
- Viagens: manhã=${trip_morning_id:-n/a}, tarde=${trip_afternoon_id:-n/a}, noite=${trip_night_id:-n/a}
- Checks: passenger_day_trip_count=${passenger_day_trip_count:-n/a}, support_day_trip_count=${support_day_trip_count:-n/a}, night_auto_ignored_motivo=${night_auto_ignored_motivo:-n/a}

## Manual Remaining

- Smoke em iPhone fisico para permissao real de localizacao/background.
- Revisao visual rapida do mapa em corrida, se o E2E rodou em simulator/macOS.
- Push/deploy continuam manuais por regra operacional do projeto.
EOF

log "Campanha finalizada. Resumo: $SUMMARY_FILE"
