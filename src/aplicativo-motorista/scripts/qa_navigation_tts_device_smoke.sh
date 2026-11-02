#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOC="$APP_DIR/backlog/QA-Navigation-TTS-Device-Smoke.md"

source "$SCRIPT_DIR/e2e_preflight.sh"

MODE="check"
DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
OUTPUT_DIR="${VANRIDES_NAV_TTS_DEVICE_SMOKE_DIR:-}"
RUN_ID="${VANRIDES_NAV_TTS_RUN_ID:-}"
VANRIDES_NAV_TTS_EXIT_OUTPUT_EXISTS=87

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_navigation_tts_device_smoke.sh [opcoes]

Opcoes:
  --check-only          Valida o contrato sem Flutter/device. Padrao.
  --list                Lista os cenarios canonicos.
  --run-device          Roda o smoke real em iOS Simulator/device.
  --device <id>         iOS Simulator/device alvo.
  --output-dir <path>   Pasta onde salvar evidencias.
  -h, --help            Mostra esta ajuda.

Variaveis:
  VANRIDES_TEST_DEVICE=<id>
  VANRIDES_IOS_DEVICE_ID=<id>
  VANRIDES_NAV_TTS_DEVICE_SMOKE_DIR=output/navigation-tts-device-smoke/<id>
  VANRIDES_NAV_TTS_RUN_ID=<id-unico>

Notas:
  - Use --check-only no gate rapido.
  - Use --run-device somente em macOS com iOS Simulator/device disponivel.
  - O teste automatizado prova wiring nativo; a prova audivel exige operador
    ouvindo a frase e registrando a evidencia local.
EOF
}

require_option_value() {
  local option="$1"
  local remaining="$2"
  local value="$3"
  if [[ "$remaining" -lt 2 || -z "$value" || "$value" == -* ]]; then
    echo "ARGUMENT_ERROR: $option exige um valor" >&2
    usage >&2
    exit 2
  fi
}

select_mode() {
  local requested="$1"
  if [[ -n "$MODE_OPTION_SEEN" ]]; then
    echo "ARGUMENT_ERROR: modos de execucao sao mutuamente exclusivos" >&2
    usage >&2
    exit 2
  fi
  MODE_OPTION_SEEN="$requested"
  MODE="$requested"
}

MODE_OPTION_SEEN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      select_mode "check"
      shift
      ;;
    --list)
      select_mode "list"
      shift
      ;;
    --run-device)
      select_mode "device"
      shift
      ;;
    --device)
      require_option_value "$1" "$#" "${2-}"
      DEVICE="$2"
      shift 2
      ;;
    --output-dir)
      require_option_value "$1" "$#" "${2-}"
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

navigation_tts_smoke_rows() {
  cat <<'EOF'
driver-navigation-repeat|DriverHomeNavigationPanel|flutter_tts native gateway|audible-operator-confirmation
missing-device|qa_navigation_tts_device_smoke.sh|BLOCKED_VALIDATION_DEVICE|setup-classification
EOF
}

require_doc_text() {
  local pattern="$1"
  if ! grep -Fq -- "$pattern" "$DOC"; then
    echo "Contrato Navigation TTS device smoke sem termo obrigatorio: $pattern" >&2
    exit 1
  fi
}

validate_contract() {
  local tmp_file
  tmp_file="$(mktemp)"
  navigation_tts_smoke_rows > "$tmp_file"

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

  require_doc_text "BLOCKED_VALIDATION_DEVICE"
  require_doc_text "output/navigation-tts-device-smoke/"
  require_doc_text "audivel"
  require_doc_text "nao promove campo real"
  require_doc_text "VANRIDES_NAV_TTS_DEVICE_SMOKE=true"
  require_doc_text "source_sha"
  require_doc_text "source_tree"
  require_doc_text "run_id"
  require_doc_text "nao e sobrescrito"

  local total
  total="$(wc -l < "$tmp_file" | tr -d ' ')"
  rm -f "$tmp_file"

  printf 'NAVIGATION_TTS_DEVICE_SMOKE_OK scenarios=%s evidence_root=output/navigation-tts-device-smoke\n' "$total"
}

resolve_navigation_tts_device() {
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
  echo "Navigation TTS device smoke requer iOS Simulator/device visivel para Flutter." >&2
  echo "Defina VANRIDES_TEST_DEVICE/VANRIDES_IOS_DEVICE_ID ou passe --device <id>." >&2
  exit "$VANRIDES_E2E_EXIT_MISSING_DEVICE"
}

fail_existing_output_dir() {
  echo "NAVIGATION_TTS_OUTPUT_DIR_ALREADY_EXISTS" >&2
  echo "A execucao nao sobrescreve evidencia existente: $OUTPUT_DIR" >&2
  exit "$VANRIDES_NAV_TTS_EXIT_OUTPUT_EXISTS"
}

assert_output_dir_available() {
  if [[ -e "$OUTPUT_DIR" || -L "$OUTPUT_DIR" ]]; then
    fail_existing_output_dir
  fi
}

reserve_output_dir() {
  assert_output_dir_available
  mkdir -p "$(dirname "$OUTPUT_DIR")"
  if ! mkdir "$OUTPUT_DIR"; then
    if [[ -e "$OUTPUT_DIR" || -L "$OUTPUT_DIR" ]]; then
      fail_existing_output_dir
    fi
    echo "Nao foi possivel reservar output-dir: $OUTPUT_DIR" >&2
    exit 85
  fi
  OUTPUT_DIR="$(cd "$OUTPUT_DIR" && pwd)"
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

  if [[ -z "$RUN_ID" ]]; then
    RUN_ID="tts-$(date -u '+%Y%m%dT%H%M%SZ')-$(python3 -c 'import uuid; print(uuid.uuid4().hex)')"
  fi
  if [[ ! "$RUN_ID" =~ ^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$ ]]; then
    echo "VANRIDES_NAV_TTS_RUN_ID invalido: $RUN_ID" >&2
    exit 2
  fi
  if [[ -z "$OUTPUT_DIR" ]]; then
    OUTPUT_DIR="$APP_DIR/output/navigation-tts-device-smoke/$RUN_ID"
  fi
  assert_output_dir_available

  DEVICE="$(resolve_navigation_tts_device "$DEVICE")"
  if [[ -z "$DEVICE" ]]; then
    block_missing_device
  fi
  if ! flutter_ios_device_available "$DEVICE"; then
    block_missing_device
  fi
  e2e_preflight_device "$DEVICE"

  if [[ -n "$(git status --porcelain --untracked-files=no)" ]]; then
    echo "Navigation TTS device smoke requer worktree rastreado limpo." >&2
    exit 85
  fi

  local source_git_sha
  local source_git_tree
  source_git_sha="$(git rev-parse --verify 'HEAD^{commit}')"
  source_git_tree="$(git rev-parse 'HEAD^{tree}')"

  reserve_output_dir

  local log_file="$OUTPUT_DIR/navigation-tts-device-smoke.log"
  local checklist_file="$OUTPUT_DIR/audio-confirmation.md"

  cat > "$checklist_file" <<'EOF'
# Navigation TTS Device Smoke - Audio Confirmation

- Resultado automatizado:
- Operador ouviu a frase em PT-BR:
- Volume/device:
- Observacoes:

Este arquivo e evidencia local e nao deve conter dados reais de passageiro,
empresa parceira, endereco privado ou segredo.
EOF

  local FLUTTER_DRIVE_ARGS=(
    flutter drive
    --driver=test_driver/navigation_tts_device_smoke_driver.dart
    --target=integration_test/navigation_tts_device_smoke_test.dart
    -d "$DEVICE"
    --dart-define=VANRIDES_NAV_TTS_DEVICE_SMOKE=true
    --dart-define=VANRIDES_NAV_TTS_SOURCE_SHA="$source_git_sha"
    --dart-define=VANRIDES_NAV_TTS_SOURCE_TREE="$source_git_tree"
    --dart-define=VANRIDES_NAV_TTS_RUN_ID="$RUN_ID"
  )

  set +e
  "${FLUTTER_DRIVE_ARGS[@]}" 2>&1 | tee "$log_file"
  test_status=${PIPESTATUS[0]}
  set -e

  if [[ "$test_status" -ne 0 ]]; then
    if is_device_start_failure "$log_file"; then
      echo "BLOCKED_VALIDATION_DEVICE" >&2
      echo "Navigation TTS device smoke bloqueado por device/provisioning/conexao." >&2
      echo "Log: $log_file" >&2
      exit "$VANRIDES_E2E_EXIT_MISSING_DEVICE"
    fi
    echo "Navigation TTS device smoke falhou. Log: $log_file" >&2
    exit "$test_status"
  fi

  python3 "$SCRIPT_DIR/qa_navigation_tts_evidence.py" \
    --create \
    --evidence-dir "$OUTPUT_DIR" \
    --device-target-platform ios \
    --run-id "$RUN_ID"

  printf 'NAVIGATION_TTS_DEVICE_SMOKE_DEVICE_OK device=%s run_id=%s evidence=%s audio_confirmation=%s\n' \
    "$DEVICE" "$RUN_ID" "$OUTPUT_DIR" "$checklist_file"
}

case "$MODE" in
  check)
    validate_contract
    ;;
  list)
    navigation_tts_smoke_rows
    ;;
  device)
    run_device_smoke
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
