#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.dev.env.local"
source "$SCRIPT_DIR/e2e_preflight.sh"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

PREFLIGHT_ONLY="${VANRIDES_E2E_PREFLIGHT_ONLY:-0}"
if [[ "${1:-}" == "--preflight-only" ]]; then
  PREFLIGHT_ONLY=1
  shift
fi

TARGET="${1:-integration_test/all.dart}"
DEVICE_ID="${2:-${VANRIDES_IOS_DEVICE_ID:-}}"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"

DEVICE_ID="$(e2e_resolve_device "$DEVICE_ID")"
e2e_preflight_run "$API_BASE_URL" "$TARGET" "$DEVICE_ID"
E2E_DART_DEFINES=()
e2e_build_dart_defines "$API_BASE_URL"

if [[ "$PREFLIGHT_ONLY" == "1" ]]; then
  echo "PASS: preflight E2E concluido para $TARGET no device $DEVICE_ID"
  exit 0
fi

cd "$APP_DIR"
e2e_build_device_args "$DEVICE_ID"
flutter test "$TARGET" "${E2E_DEVICE_ARGS[@]}" "${E2E_DART_DEFINES[@]}"
