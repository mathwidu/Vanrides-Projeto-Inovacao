#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

source "$SCRIPT_DIR/e2e_preflight.sh"

DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
OUTPUT_DIR="${VANRIDES_PASSENGER_ONBOARDING_VISUAL_DIR:-$APP_DIR/output/visual-smoke/passenger-onboarding/$(date '+%Y%m%d-%H%M%S')}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_passenger_onboarding_visual_smoke.sh [opcoes]

Opcoes:
  --device <id>       iOS Simulator para screenshots.
  --output-dir <path> Pasta onde salvar PNGs/logs.
  -h, --help          Mostra esta ajuda.

Notas:
  - O smoke usa estados fake determinísticos, sem backend.
  - As capturas provam estados visuais do onboarding passageiro/portal admin.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --device)
      DEVICE="$2"
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

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

cd "$APP_DIR"
mkdir -p "$OUTPUT_DIR"
OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

DEVICE="$(e2e_resolve_device "$DEVICE")"
e2e_preflight_device "$DEVICE"
if [[ "$DEVICE" == "macos" ]]; then
  echo "Smoke visual de onboarding precisa de iOS Simulator para screenshots via simctl." >&2
  exit 1
fi

log "Rodando visual smoke de onboarding no device $DEVICE"
log_file="$OUTPUT_DIR/passenger-onboarding-visual.log"

set +e
flutter test integration_test/visual_smoke/passenger_onboarding_visual_smoke_test.dart \
  -d "$DEVICE" \
  2>&1 | while IFS= read -r line; do
    printf '%s\n' "$line" | tee -a "$log_file"
    if [[ "$line" =~ VANRIDES_PASSENGER_ONBOARDING_VISUAL_READY:([A-Za-z0-9_-]+) ]]; then
      state="${BASH_REMATCH[1]}"
      screenshot="$OUTPUT_DIR/$state.png"
      sleep 0.25
      xcrun simctl io "$DEVICE" screenshot "$screenshot" >/dev/null
      printf 'VANRIDES_PASSENGER_ONBOARDING_VISUAL_CAPTURED:%s\n' "$screenshot" | tee -a "$log_file"
    fi
  done
test_status=${PIPESTATUS[0]}
set -e

if [[ "$test_status" -ne 0 ]]; then
  echo "Visual smoke de onboarding falhou. Log: $log_file" >&2
  exit "$test_status"
fi

log "Smoke visual de onboarding finalizado: $OUTPUT_DIR"
