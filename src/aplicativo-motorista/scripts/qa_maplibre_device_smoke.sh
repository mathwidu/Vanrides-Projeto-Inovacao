#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOC="$APP_DIR/backlog/QA-MapLibre-Device-Smoke.md"

source "$SCRIPT_DIR/e2e_preflight.sh"

MODE="check"
DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
OUTPUT_DIR="${VANRIDES_MAPLIBRE_DEVICE_SMOKE_DIR:-$APP_DIR/output/maplibre-device-smoke/$(date '+%Y%m%d-%H%M%S')}"
PUBLISH_PORT="${VANRIDES_MAPLIBRE_PUBLISH_PORT:-0}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_maplibre_device_smoke.sh [opcoes]

Opcoes:
  --check-only          Valida o contrato sem Flutter/device. Padrao.
  --list                Lista os cenarios canonicos.
  --run-device          Roda o smoke real em device/simulator.
  --device <id>         iOS Simulator/device alvo.
  --publish-port        Passa --publish-port ao flutter drive para iPhone iOS wireless.
  --output-dir <path>   Pasta onde salvar evidencias.
  -h, --help            Mostra esta ajuda.

Notas:
  - Use --check-only no gate rapido.
  - Use --run-device somente em macOS com iOS Simulator/device disponivel.
  - VANRIDES_MAPLIBRE_PUBLISH_PORT=1 habilita o mesmo comportamento de --publish-port.
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
    --run-device)
      MODE="device"
      shift
      ;;
    --device)
      DEVICE="$2"
      shift 2
      ;;
    --publish-port)
      PUBLISH_PORT=1
      shift
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

maplibre_smoke_rows() {
  cat <<'EOF'
tracking-maplibre-proof|TripLiveMapPanel|trip.live.map.maplibre|device-log-or-screenshot
default-fallback|TripLiveMapRenderer|googleLegacy without proof flag|unit-widget-tests
EOF
}

require_doc_text() {
  local pattern="$1"
  if ! grep -Fq -- "$pattern" "$DOC"; then
    echo "Contrato MapLibre device smoke sem termo obrigatorio: $pattern" >&2
    exit 1
  fi
}

publish_port_enabled() {
  local normalized
  normalized="$(printf '%s' "$PUBLISH_PORT" | tr '[:upper:]' '[:lower:]')"
  case "$normalized" in
    1|true|yes|on)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

validate_contract() {
  local tmp_file
  tmp_file="$(mktemp)"
  maplibre_smoke_rows > "$tmp_file"

  awk -F'|' '
    NF != 4 {
      printf "Linha invalida (%d campos): %s\n", NF, $0 > "/dev/stderr"
      exit 1
    }
    $1 == "" || $2 == "" || $3 == "" || $4 == "" {
      printf "Campo vazio: %s\n", $0 > "/dev/stderr"
      exit 1
    }
    seen[$1]++ {
      printf "Cenario duplicado: %s\n", $1 > "/dev/stderr"
      exit 1
    }
  ' "$tmp_file"

  if [[ ! -f "$DOC" ]]; then
    echo "Documento ausente: $DOC" >&2
    rm -f "$tmp_file"
    exit 1
  fi

  require_doc_text "MapLibre nao e default"
  require_doc_text "BLOCKED_VALIDATION_DEVICE"
  require_doc_text "output/maplibre-device-smoke/"
  require_doc_text "style sintetico"
  require_doc_text "tile service"
  require_doc_text "--dart-define=VANRIDES_MAPLIBRE_PROOF=true"
  require_doc_text "--dart-define=VANRIDES_MAPLIBRE_DEVICE_SMOKE=true"
  require_doc_text "--publish-port"
  require_doc_text "VANRIDES_MAPLIBRE_PUBLISH_PORT"

  local total
  total="$(wc -l < "$tmp_file" | tr -d ' ')"
  rm -f "$tmp_file"

  printf 'MAPLIBRE_DEVICE_SMOKE_OK scenarios=%s evidence_root=output/maplibre-device-smoke\n' "$total"
}

is_ios_simulator_device() {
  local device="$1"
  command -v xcrun >/dev/null 2>&1 && xcrun simctl list devices | grep -Fq "$device"
}

resolve_maplibre_device() {
  local requested="${1:-}"
  if [[ -n "$requested" ]]; then
    printf '%s\n' "$requested"
    return 0
  fi

  if ! command -v flutter >/dev/null 2>&1; then
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
        preferred = device.get("id")
        break

if preferred is None:
    for device in devices:
        if device.get("targetPlatform") == "ios":
            preferred = device.get("id")
            break

if preferred:
    print(preferred)
PY
}

flutter_ios_device_available() {
  local requested="$1"
  python3 - "$requested" <<'PY'
import json
import subprocess
import sys

requested = sys.argv[1]
result = subprocess.run(
    ["flutter", "devices", "--machine"],
    check=False,
    capture_output=True,
    text=True,
)
if result.returncode != 0:
    sys.exit(1)

try:
    devices = json.loads(result.stdout)
except json.JSONDecodeError:
    sys.exit(1)

for device in devices:
    if requested in {device.get("id"), device.get("name")}:
        sys.exit(0 if device.get("targetPlatform") == "ios" else 1)

sys.exit(1)
PY
}

block_missing_device() {
  echo "BLOCKED_VALIDATION_DEVICE" >&2
  echo "MapLibre device smoke requer iOS Simulator/device visivel para flutter test." >&2
  echo "Defina VANRIDES_TEST_DEVICE/VANRIDES_IOS_DEVICE_ID ou passe --device <id>." >&2
  exit "$VANRIDES_E2E_EXIT_MISSING_DEVICE"
}

is_device_start_failure() {
  local log_file="$1"
  grep -Eiq \
    'wirelessly tethered iOS device|No supported devices found|No devices are connected|Device is locked|not trusted|Developer Mode|Cannot start app|developer disk image could not be mounted|Timed out waiting for all destinations|Could not find the built application bundle' \
    "$log_file"
}

run_device_smoke() {
  validate_contract
  cd "$APP_DIR"
  mkdir -p "$OUTPUT_DIR"
  OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"

  DEVICE="$(resolve_maplibre_device "$DEVICE")"
  if [[ -z "$DEVICE" ]]; then
    block_missing_device
  fi
  if ! flutter_ios_device_available "$DEVICE"; then
    block_missing_device
  fi
  e2e_preflight_device "$DEVICE"

  local log_file="$OUTPUT_DIR/maplibre-device-smoke.log"
  local FLUTTER_DRIVE_ARGS=(
    flutter drive
    --driver=test_driver/maplibre_device_smoke_driver.dart
    --target=integration_test/maplibre_device_smoke_test.dart
    -d "$DEVICE"
    --dart-define=VANRIDES_MAPLIBRE_PROOF=true
    --dart-define=VANRIDES_MAPLIBRE_DEVICE_SMOKE=true
  )
  if publish_port_enabled; then
    FLUTTER_DRIVE_ARGS+=("--publish-port")
  fi

  set +e
  "${FLUTTER_DRIVE_ARGS[@]}" \
    2>&1 | while IFS= read -r line; do
      printf '%s\n' "$line" | tee -a "$log_file"
      if [[ "$line" =~ VANRIDES_MAPLIBRE_DEVICE_SMOKE_VISUAL_READY:([A-Za-z0-9_-]+) ]]; then
        state="${BASH_REMATCH[1]}"
        if is_ios_simulator_device "$DEVICE"; then
          screenshot="$OUTPUT_DIR/$state.png"
          xcrun simctl io "$DEVICE" screenshot "$screenshot" >/dev/null || true
          printf 'VANRIDES_MAPLIBRE_DEVICE_SMOKE_CAPTURED:%s\n' "$screenshot" | tee -a "$log_file"
        fi
      fi
    done
  test_status=${PIPESTATUS[0]}
  set -e

  if [[ "$test_status" -ne 0 ]]; then
    if is_device_start_failure "$log_file"; then
      echo "BLOCKED_VALIDATION_DEVICE" >&2
      echo "MapLibre device smoke bloqueado por device/provisioning/conexao." >&2
      echo "Log: $log_file" >&2
      exit "$VANRIDES_E2E_EXIT_MISSING_DEVICE"
    fi
    echo "MapLibre device smoke falhou. Log: $log_file" >&2
    exit "$test_status"
  fi

  printf 'MAPLIBRE_DEVICE_SMOKE_DEVICE_OK device=%s evidence=%s\n' "$DEVICE" "$OUTPUT_DIR"
}

case "$MODE" in
  check)
    validate_contract
    ;;
  list)
    maplibre_smoke_rows
    ;;
  device)
    run_device_smoke
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
