#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"

DEVICE="${1:-${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}}"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
RUN_GUARDIAN_E2E="${RUN_GUARDIAN_E2E:-1}"
GUARDIAN_EMAIL="${GUARDIAN_E2E_EMAIL:-}"
GUARDIAN_PASSWORD="${GUARDIAN_E2E_PASSWORD:-}"
GUARDIAN_PASSAGEIRO="${GUARDIAN_E2E_PASSAGEIRO:-}"
E2E_GOOGLE_MAPS_API_KEY="${GOOGLE_MAPS_API_KEY:-${VANRIDES_E2E_GOOGLE_MAPS_API_KEY:-REDACTED_GOOGLE_API_KEY__________________}}"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

resolve_device() {
  if [[ -n "$DEVICE" ]]; then
    printf '%s\n' "$DEVICE"
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

cd "$APP_DIR"

log "Guardian mobile: analyze focado"
flutter analyze --no-fatal-infos --no-fatal-warnings \
  lib/features/guardian \
  lib/app/session \
  test/features/guardian \
  test/app/session

log "Guardian mobile: unit/widget tests"
flutter test \
  test/app/session/app_session_user_test.dart \
  test/features/guardian/guardian_home_screen_test.dart \
  test/features/guardian/guardian_shell_screen_test.dart \
  test/features/guardian/guardian_presencas_screen_test.dart \
  test/features/guardian/guardian_presenca_service_test.dart \
  test/features/guardian/guardian_localizacao_screen_test.dart \
  test/features/guardian/guardian_localizacao_service_test.dart

if [[ "$RUN_GUARDIAN_E2E" != "1" ]]; then
  log "Guardian mobile: E2E desabilitado (RUN_GUARDIAN_E2E=$RUN_GUARDIAN_E2E)"
  exit 0
fi

if [[ -z "$GUARDIAN_EMAIL" || -z "$GUARDIAN_PASSWORD" ]]; then
  log "Guardian mobile: E2E pulado (defina GUARDIAN_E2E_EMAIL e GUARDIAN_E2E_PASSWORD para o smoke real)"
  exit 0
fi

DEVICE="$(resolve_device)"
if [[ -z "$DEVICE" ]]; then
  echo "Erro: nenhum device disponivel para E2E guardian. Defina VANRIDES_TEST_DEVICE/VANRIDES_IOS_DEVICE_ID ou rode com RUN_GUARDIAN_E2E=0." >&2
  exit 1
fi

log "Guardian mobile: integration_test/guardian/all.dart no device $DEVICE"
GOOGLE_MAPS_API_KEY="$E2E_GOOGLE_MAPS_API_KEY" flutter test integration_test/guardian/all.dart \
  -d "$DEVICE" \
  --dart-define="VANRIDES_API_BASE_URL=$API_BASE_URL" \
  --dart-define=VANRIDES_E2E_TEST_MODE=true \
  --dart-define="GUARDIAN_E2E_EMAIL=$GUARDIAN_EMAIL" \
  --dart-define="GUARDIAN_E2E_PASSWORD=$GUARDIAN_PASSWORD" \
  --dart-define="GUARDIAN_E2E_PASSAGEIRO=$GUARDIAN_PASSAGEIRO"

log "Guardian mobile suite finalizada com sucesso"
