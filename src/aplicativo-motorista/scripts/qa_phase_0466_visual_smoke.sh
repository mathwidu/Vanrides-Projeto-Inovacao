#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
BACKEND_DIR="$(cd "$APP_DIR/../backend" && pwd)"

source "$SCRIPT_DIR/e2e_preflight.sh"

API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
OUTPUT_DIR="${VANRIDES_VISUAL_SMOKE_DIR:-$APP_DIR/output/visual-smoke/phase-0466/$(date '+%Y%m%d-%H%M%S')}"
FIXTURE_ENV="${VANRIDES_VISUAL_FIXTURE_ENV:-}"
RUN_ADMIN_WEB="${RUN_ADMIN_WEB:-1}"
RUN_MOBILE="${RUN_MOBILE:-1}"
GOOGLE_MAPS_API_KEY_VALUE="${GOOGLE_MAPS_API_KEY:-${VANRIDES_E2E_GOOGLE_MAPS_API_KEY:-}}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_phase_0466_visual_smoke.sh [opcoes]

Opcoes:
  --api <url>             Backend alvo. Padrao: DEV.
  --device <id>           iOS Simulator para screenshots mobile.
  --fixture-env <path>    Reusa fixture.env existente.
  --output-dir <path>     Pasta onde salvar PNGs/logs.
  --skip-mobile           Pula passenger/driver/guardian no simulador.
  --skip-admin-web        Pula screenshot web do portal admin.
  -h, --help              Mostra esta ajuda.

Notas:
  - A fixture e os logs podem conter credenciais de QA. A pasta output/ e ignorada pelo git.
  - O screenshot mobile exige iOS Simulator, pois usa xcrun simctl io.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --api)
      API_BASE_URL="$2"
      shift 2
      ;;
    --device)
      DEVICE="$2"
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
    --skip-mobile)
      RUN_MOBILE=0
      shift
      ;;
    --skip-admin-web)
      RUN_ADMIN_WEB=0
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

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

cd "$APP_DIR"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

if [[ -z "$FIXTURE_ENV" ]]; then
  log "Criando fixture operacional DEV para smoke visual"
  fixture_report_dir="$OUTPUT_DIR/fixture"
  mkdir -p "$fixture_report_dir"
  RUN_BACKEND_GATES=0 \
    RUN_CENTRAL_FLOW_SMOKE=0 \
    RUN_APP_GATES=0 \
    RUN_PORTAL_E2E=0 \
    RUN_GUARDIAN_MOBILE=0 \
    RUN_GUARDIAN_E2E=0 \
    RUN_FULL_MOBILE_E2E=0 \
    QA_RELEASE_REPORT_DIR="$fixture_report_dir" \
    VANRIDES_BACKEND_DIR="$BACKEND_DIR" \
    "$SCRIPT_DIR/qa_release_campaign.sh" --api "$API_BASE_URL" \
    > "$OUTPUT_DIR/fixture-generation.log" 2>&1
  FIXTURE_ENV="$fixture_report_dir/fixture.env"
fi

if [[ ! -f "$FIXTURE_ENV" ]]; then
  echo "fixture.env nao encontrado: $FIXTURE_ENV" >&2
  exit 1
fi

set -a
source "$FIXTURE_ENV"
set +a

capture_on_ready() {
  local role="$1"
  local log_file="$OUTPUT_DIR/mobile-$role.log"

  log "Rodando visual smoke mobile: $role"
  E2E_DART_DEFINES=()
  e2e_build_dart_defines "$API_BASE_URL"

  set +e
  GOOGLE_MAPS_API_KEY="$GOOGLE_MAPS_API_KEY_VALUE" \
    flutter test integration_test/visual_smoke/phase_0466_visual_smoke_test.dart \
      -d "$DEVICE" \
      "${E2E_DART_DEFINES[@]}" \
      --dart-define="VANRIDES_VISUAL_ROLE=$role" \
      2>&1 | while IFS= read -r line; do
        printf '%s\n' "$line" | tee -a "$log_file"
        if [[ "$line" =~ VANRIDES_VISUAL_READY:([A-Za-z0-9_-]+) ]]; then
          local state="${BASH_REMATCH[1]}"
          local screenshot="$OUTPUT_DIR/$state.png"
          sleep 0.25
          xcrun simctl io "$DEVICE" screenshot "$screenshot" >/dev/null
          printf 'VANRIDES_VISUAL_CAPTURED:%s\n' "$screenshot" | tee -a "$log_file"
        fi
      done
  local test_status=${PIPESTATUS[0]}
  set -e

  if [[ "$test_status" -ne 0 ]]; then
    echo "Visual smoke mobile falhou para $role. Log: $log_file" >&2
    exit "$test_status"
  fi
}

if [[ "$RUN_MOBILE" == "1" ]]; then
  DEVICE="$(e2e_resolve_device "$DEVICE")"
  e2e_preflight_device "$DEVICE"
  if [[ "$DEVICE" == "macos" ]]; then
    echo "Smoke visual mobile precisa de iOS Simulator para screenshots via simctl." >&2
    exit 1
  fi

  capture_on_ready passenger
  capture_on_ready driver
  capture_on_ready guardian
fi

if [[ "$RUN_ADMIN_WEB" == "1" ]]; then
  log "Rodando visual smoke web do portal admin"
  mkdir -p "$OUTPUT_DIR/admin-web"
  ADMIN_PORTAL_E2E_SCREENSHOT_DIR="$OUTPUT_DIR/admin-web" \
    VANRIDES_API_BASE_URL="$API_BASE_URL" \
    "$SCRIPT_DIR/qa_admin_portal_web_e2e.sh" \
    > "$OUTPUT_DIR/admin-web.log" 2>&1
fi

log "Smoke visual PHASE-0466 finalizado: $OUTPUT_DIR"
