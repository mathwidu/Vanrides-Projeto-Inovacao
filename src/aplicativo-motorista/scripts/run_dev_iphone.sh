#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.dev.env.local"

if [[ -f "$ENV_FILE" ]]; then
  set -a
  source "$ENV_FILE"
  set +a
fi

DEVICE_ID="${1:-${VANRIDES_IOS_DEVICE_ID:-}}"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
PLACES_API_KEY="${VANRIDES_GOOGLE_PLACES_API_KEY:-}"

if [[ -z "$DEVICE_ID" ]]; then
  echo "Erro: informe o device id do iPhone (arg1) ou VANRIDES_IOS_DEVICE_ID."
  exit 1
fi

if [[ -z "$PLACES_API_KEY" ]]; then
  echo "Erro: defina VANRIDES_GOOGLE_PLACES_API_KEY em $ENV_FILE ou no shell."
  exit 1
fi

cd "$APP_DIR"
flutter pub get
(cd ios && pod install)

flutter run -d "$DEVICE_ID" \
  --dart-define="VANRIDES_API_BASE_URL=$API_BASE_URL" \
  --dart-define="VANRIDES_GOOGLE_PLACES_API_KEY=$PLACES_API_KEY"
