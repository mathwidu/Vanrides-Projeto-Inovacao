#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
OUTPUT_DIR="${VANRIDES_VISUAL_MATRIX_DIR:-$APP_DIR/output/visual-smoke/ux-0901/$(date '+%Y%m%d-%H%M%S')}"
MODE="check"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
FIXTURE_ENV="${VANRIDES_VISUAL_FIXTURE_ENV:-}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_visual_matrix_smoke.sh [opcoes]

Opcoes:
  --check-only          Valida a matriz sem rodar Flutter/device. Padrao.
  --list                Lista a matriz canonica de screenshots.
  --run-deterministic   Roda screenshots fake/deterministicos sem backend.
  --run-real            Roda screenshots reais DEV/fixture quando disponivel.
  --device <id>         iOS Simulator para screenshots mobile.
  --api <url>           Backend alvo para smoke real.
  --fixture-env <path>  Reusa fixture.env existente para smoke real.
  --output-dir <path>   Pasta onde salvar evidencias.
  -h, --help            Mostra esta ajuda.

Notas:
  - A matriz inclui estados cobertos e lacunas planejadas.
  - Use --check-only no gate rapido.
  - Use --run-deterministic para screenshots sem backend.
  - Use --run-real apenas com fixture/API estavel.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      MODE="check"
      shift
      ;;
    --list)
      MODE="list"
      shift
      ;;
    --run-deterministic)
      MODE="deterministic"
      shift
      ;;
    --run-real)
      MODE="real"
      shift
      ;;
    --device)
      DEVICE="$2"
      shift 2
      ;;
    --api)
      API_BASE_URL="$2"
      shift 2
      ;;
    --fixture-env)
      FIXTURE_ENV="$2"
      shift 2
      ;;
    --output-dir)
      OUTPUT_DIR="$2"
      shift 2
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

visual_matrix_rows() {
  cat <<'EOF'
admin|dashboard-completo|admin-web/admin-dashboard.png|qa_admin_portal_web_e2e.sh|real
admin|viagens-do-dia|admin-web/admin-trips.png|qa_admin_portal_web_e2e.sh|real
admin|servico-operacional|admin-web/admin-services.png|qa_admin_portal_web_e2e.sh|real
admin|passageiro-completo|admin-web/admin-passenger-detail.png|qa_admin_portal_web_e2e.sh|real
admin|motorista-equipe|admin-web/admin-drivers.png|qa_admin_portal_web_e2e.sh|real
admin|responsavel-vinculado|admin-web/admin-guardian-passengers.png|qa_admin_portal_web_e2e.sh|real
admin|conta|admin-web/admin-account.png|qa_admin_portal_web_e2e.sh|real
admin|login-bootstrap|onboarding/admin-onboarding-portal.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|sem-empresa|onboarding/passenger-onboarding-gate.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|busca-empresa|onboarding/passenger-onboarding-search.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|solicitacao-pendente|onboarding/passenger-onboarding-pending.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|convite|onboarding/passenger-onboarding-invite.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|empresa-vinculada|onboarding/passenger-onboarding-linked.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|email-pendente|onboarding/passenger-onboarding-email-pending.png|qa_passenger_onboarding_visual_smoke.sh|deterministic
passenger|viagem-real|phase/passenger-trip.png|qa_phase_0466_visual_smoke.sh|real
passenger|presenca-real|phase/passenger-presence.png|qa_phase_0466_visual_smoke.sh|real
passenger|perfil-completo|account/account-passenger-complete.png|qa_account_profile_visual_smoke.sh|deterministic
passenger|perfil-incompleto|account/account-passenger-incomplete.png|qa_account_profile_visual_smoke.sh|deterministic
driver|sem-empresa|role-empty/driver-onboarding-pending.png|qa_role_empty_visual_smoke.sh|deterministic
driver|viagem-real|phase/driver-home.png|qa_phase_0466_visual_smoke.sh|real
driver|conta|account/account-driver-complete.png|qa_account_profile_visual_smoke.sh|deterministic
guardian|sem-passageiro|role-empty/guardian-empty.png|qa_role_empty_visual_smoke.sh|deterministic
guardian|home-com-passageiro|phase/guardian-home.png|qa_phase_0466_visual_smoke.sh|real
guardian|localizacao|phase/guardian-location.png|qa_phase_0466_visual_smoke.sh|real
guardian|presencas|phase/guardian-presence.png|qa_phase_0466_visual_smoke.sh|real
guardian|conta|account/account-guardian-complete.png|qa_account_profile_visual_smoke.sh|deterministic
guardian|perfil-admin|account/account-admin-complete.png|qa_account_profile_visual_smoke.sh|deterministic
EOF
}

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

validate_matrix() {
  local tmp_file
  tmp_file="$(mktemp)"
  visual_matrix_rows > "$tmp_file"
  awk -F'|' '
    NF != 5 {
      printf "Linha invalida (%d campos): %s\n", NF, $0 > "/dev/stderr"
      exit 1
    }
    $1 == "" || $2 == "" || $3 == "" || $4 == "" || $5 == "" {
      printf "Campo vazio: %s\n", $0 > "/dev/stderr"
      exit 1
    }
    $3 !~ /\.png$/ {
      printf "Screenshot sem .png: %s\n", $0 > "/dev/stderr"
      exit 1
    }
    seen[$3]++ {
      printf "Screenshot duplicado: %s\n", $3 > "/dev/stderr"
      exit 1
    }
    $5 != "deterministic" && $5 != "real" && $5 != "planned" {
      printf "Coverage invalida: %s\n", $0 > "/dev/stderr"
      exit 1
    }
  ' "$tmp_file"

  while IFS='|' read -r _role _state _shot runner coverage; do
    if [[ "$coverage" == "planned" ]]; then
      continue
    fi
    if [[ ! -x "$SCRIPT_DIR/$runner" ]]; then
      echo "Runner ausente ou sem permissao de execucao: $runner" >&2
      rm -f "$tmp_file"
      exit 1
    fi
  done < "$tmp_file"

  local total deterministic real planned
  total="$(wc -l < "$tmp_file" | tr -d ' ')"
  deterministic="$(awk -F'|' '$5 == "deterministic" { count += 1 } END { print count + 0 }' "$tmp_file")"
  real="$(awk -F'|' '$5 == "real" { count += 1 } END { print count + 0 }' "$tmp_file")"
  planned="$(awk -F'|' '$5 == "planned" { count += 1 } END { print count + 0 }' "$tmp_file")"
  rm -f "$tmp_file"

  printf 'VISUAL_MATRIX_OK total=%s deterministic=%s real=%s planned=%s\n' \
    "$total" "$deterministic" "$real" "$planned"
}

write_manifest() {
  mkdir -p "$OUTPUT_DIR"
  OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
  visual_matrix_rows > "$OUTPUT_DIR/visual-matrix.tsv"
  printf '%s\n' "$OUTPUT_DIR/visual-matrix.tsv"
}

run_deterministic() {
  validate_matrix
  local matrix_path
  matrix_path="$(write_manifest)"
  log "Matriz visual salva em $matrix_path"

  "$SCRIPT_DIR/qa_account_profile_visual_smoke.sh" \
    --device "$DEVICE" \
    --output-dir "$OUTPUT_DIR/account"

  "$SCRIPT_DIR/qa_passenger_onboarding_visual_smoke.sh" \
    --device "$DEVICE" \
    --output-dir "$OUTPUT_DIR/onboarding"

  "$SCRIPT_DIR/qa_role_empty_visual_smoke.sh" \
    --device "$DEVICE" \
    --output-dir "$OUTPUT_DIR/role-empty"

  log "Smokes deterministicos finalizados em $OUTPUT_DIR"
}

run_real() {
  validate_matrix
  local matrix_path
  matrix_path="$(write_manifest)"
  log "Matriz visual salva em $matrix_path"

  phase_args=(--api "$API_BASE_URL" --device "$DEVICE" --output-dir "$OUTPUT_DIR/phase")
  if [[ -n "$FIXTURE_ENV" ]]; then
    phase_args+=(--fixture-env "$FIXTURE_ENV")
  fi
  "$SCRIPT_DIR/qa_phase_0466_visual_smoke.sh" "${phase_args[@]}"

  admin_args=()
  if [[ -n "$FIXTURE_ENV" ]]; then
    admin_args+=(ADMIN_PORTAL_E2E_FIXTURE_SOURCE="$FIXTURE_ENV")
  fi
  env \
    VANRIDES_API_BASE_URL="$API_BASE_URL" \
    ADMIN_PORTAL_E2E_REPORT_DIR="$OUTPUT_DIR/admin-web" \
    ADMIN_PORTAL_E2E_SCREENSHOT_DIR="$OUTPUT_DIR/admin-web" \
    "${admin_args[@]}" \
    "$SCRIPT_DIR/qa_admin_portal_web_e2e.sh"

  log "Smokes reais finalizados em $OUTPUT_DIR"
}

case "$MODE" in
  check)
    validate_matrix
    ;;
  list)
    visual_matrix_rows
    ;;
  deterministic)
    run_deterministic
    ;;
  real)
    run_real
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
