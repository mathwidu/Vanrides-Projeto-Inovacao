#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
ENV_FILE="$SCRIPT_DIR/.dev.env.local"
source "$SCRIPT_DIR/e2e_preflight.sh"

RUN_E2E="${RUN_E2E:-0}"
CLI_RUN_E2E=""
CLI_E2E_SUITE=""
CLI_DEVICE=""
CLI_API_BASE_URL=""
QA_LOG_DIR="$APP_DIR/output/qa-logs"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_gate_card.sh [--no-e2e|--e2e] [--suite <arquivo>] [--device <id>] [--api <url>]

Perfis:
  --no-e2e   Roda somente flutter analyze + flutter test. Padrao para cards locais/documentais.
  --e2e      Tambem roda integration_test. Use para fluxo critico, contrato backend, permissao,
             websocket ou smoke ponta a ponta.

Variaveis equivalentes:
  RUN_E2E=1
  E2E_SUITE=integration_test/all.dart
  VANRIDES_TEST_DEVICE=<device>
  VANRIDES_API_BASE_URL=<url>
  VANRIDES_FIXTURE_DRIVER_1_EMAIL, VANRIDES_FIXTURE_DRIVER_2_EMAIL,
  VANRIDES_FIXTURE_DRIVER_PASSWORD, VANRIDES_FIXTURE_PASSENGER_EMAIL,
  VANRIDES_FIXTURE_PASSENGER_PASSWORD, VANRIDES_FIXTURE_PASSENGER_NAME
  GUARDIAN_E2E_EMAIL, GUARDIAN_E2E_PASSWORD, GUARDIAN_E2E_PASSAGEIRO
  VANRIDES_E2E_PREFLIGHT_ONLY=1 para validar backend/device/fixture sem rodar Flutter.

Compatibilidade legada:
  Um unico device posicional ASCII simples (ex.: "iPhone 16e") ainda e aceito.
  Novos usos devem preferir --device <id>.
EOF
}

argument_error() {
  printf 'ARGUMENT_ERROR: %s\n\n' "$1" >&2
  usage >&2
  exit 2
}

dotenv_error() {
  local line_number="$1"
  local message="$2"
  printf 'DOTENV_ERROR: %s:%s: %s\n' "$ENV_FILE" "$line_number" "$message" >&2
  return 2
}

is_allowed_local_e2e_key() {
  case "$1" in
    RUN_E2E|E2E_SUITE|VANRIDES_E2E_PREFLIGHT_ONLY|\
    VANRIDES_API_BASE_URL|VANRIDES_TEST_DEVICE|VANRIDES_IOS_DEVICE_ID|\
    GOOGLE_MAPS_API_KEY|VANRIDES_E2E_GOOGLE_MAPS_API_KEY|\
    VANRIDES_GOOGLE_PLACES_API_KEY|\
    VANRIDES_FIXTURE_DRIVER_1_EMAIL|VANRIDES_FIXTURE_DRIVER_2_EMAIL|\
    VANRIDES_FIXTURE_DRIVER_PASSWORD|VANRIDES_FIXTURE_DRIVER_1_ID|\
    VANRIDES_FIXTURE_DRIVER_2_ID|VANRIDES_FIXTURE_PASSENGER_EMAIL|\
    VANRIDES_FIXTURE_PASSENGER_PASSWORD|VANRIDES_FIXTURE_PASSENGER_NAME|\
    VANRIDES_FIXTURE_PASSENGER_ID|VANRIDES_FIXTURE_SERVICE_MORNING_NAME|\
    VANRIDES_FIXTURE_SERVICE_AFTERNOON_NAME|VANRIDES_FIXTURE_SERVICE_NIGHT_NAME|\
    VANRIDES_FIXTURE_DATE|GUARDIAN_E2E_EMAIL|GUARDIAN_E2E_PASSWORD|\
    GUARDIAN_E2E_PASSAGEIRO|GUARDIAN_E2E_PASSAGEIRO_ID|\
    driver_1_email|driver_2_email|driver_password|driver_1_id|driver_2_id|\
    passenger_main_email|passenger_password|passenger_main_name|passenger_main_id|\
    service_morning_name|service_afternoon_name|service_night_name|fixture_date|\
    guardian_email|guardian_password|guardian_passageiro_name|guardian_passageiro_id)
      return 0
      ;;
    *)
      return 1
      ;;
  esac
}

trim_whitespace() {
  local value="$1"
  value="${value#"${value%%[![:space:]]*}"}"
  value="${value%"${value##*[![:space:]]}"}"
  printf '%s' "$value"
}

DOTENV_PARSED_VALUE=""
parse_dotenv_value() {
  local raw_value="$1"
  local line_number="$2"
  local key="$3"
  local value
  local quoted=0
  local single_quoted_pattern="^'([^']*)'[[:space:]]*(#.*)?$"
  local double_quoted_pattern='^"([^"]*)"[[:space:]]*(#.*)?$'
  local unquoted_comment_pattern='^([^#]*[^[:space:]#])[[:space:]]+#.*$'

  value="$(trim_whitespace "$raw_value")"
  if [[ -z "$value" ]]; then
    DOTENV_PARSED_VALUE=""
    return 0
  fi

  case "${value:0:1}" in
    "'")
      if [[ "$value" =~ $single_quoted_pattern ]]; then
        value="${BASH_REMATCH[1]}"
        quoted=1
      else
        dotenv_error "$line_number" "aspas simples invalidas em $key"
        return 2
      fi
      ;;
    '"')
      if [[ "$value" =~ $double_quoted_pattern ]]; then
        value="${BASH_REMATCH[1]}"
        quoted=1
      else
        dotenv_error "$line_number" "aspas duplas invalidas em $key"
        return 2
      fi
      ;;
    *)
      if [[ "$value" == \#* ]]; then
        value=""
      elif [[ "$value" =~ $unquoted_comment_pattern ]]; then
        value="${BASH_REMATCH[1]}"
      fi
      value="$(trim_whitespace "$value")"
      if [[ "$value" =~ [[:space:]] || "$value" == *"'"* || "$value" == *'"'* ]]; then
        dotenv_error "$line_number" "valor simples invalido em $key; use aspas para espacos"
        return 2
      fi
      ;;
  esac

  if [[ "$value" == *'$('* || "$value" == *'${'* || "$value" == *'`'* ]]; then
    dotenv_error "$line_number" "sintaxe insegura no valor de $key"
    return 2
  fi
  if [[ "$quoted" == "0" ]] && \
    [[ "$value" == *';'* || "$value" == *'|'* || "$value" == *'&'* || \
      "$value" == *'<'* || "$value" == *'>'* ]]; then
    dotenv_error "$line_number" "sintaxe insegura no valor de $key"
    return 2
  fi
  if [[ "$key" == "RUN_E2E" || "$key" == "VANRIDES_E2E_PREFLIGHT_ONLY" ]] && \
    [[ "$value" != "0" && "$value" != "1" ]]; then
    dotenv_error "$line_number" "$key aceita somente 0 ou 1"
    return 2
  fi

  DOTENV_PARSED_VALUE="$value"
}

load_local_e2e_env() {
  local line=""
  local line_number=0
  local key
  local raw_value
  local assignment_pattern='^[[:space:]]*(export[[:space:]]+)?([A-Za-z_][A-Za-z0-9_]*)[[:space:]]*=(.*)$'
  local blank_or_comment_pattern='^[[:space:]]*(#.*)?$'

  while IFS= read -r line || [[ -n "$line" ]]; do
    line_number=$((line_number + 1))
    line="${line%$'\r'}"
    if [[ "$line" =~ $blank_or_comment_pattern ]]; then
      continue
    fi
    if ! [[ "$line" =~ $assignment_pattern ]]; then
      dotenv_error "$line_number" "linha nao e uma atribuicao dotenv valida"
      return 2
    fi

    key="${BASH_REMATCH[2]}"
    raw_value="${BASH_REMATCH[3]}"
    if ! is_allowed_local_e2e_key "$key"; then
      dotenv_error "$line_number" "chave nao permitida: $key"
      return 2
    fi
    parse_dotenv_value "$raw_value" "$line_number" "$key" || return $?
    printf -v "$key" '%s' "$DOTENV_PARSED_VALUE"
    export "$key"
  done < "$ENV_FILE"
}

require_option_value() {
  local option="$1"
  local remaining="$2"
  local value="$3"
  if [[ "$remaining" -lt 2 || -z "$value" || "$value" == -* ]]; then
    argument_error "$option exige um valor"
  fi
}

LEGACY_DEVICE_SEEN=0
LEGACY_DEVICE_PATTERN='^[A-Za-z0-9][A-Za-z0-9._: -]*$'
PROFILE_OPTION_SEEN=""

while [[ $# -gt 0 ]]; do
  case "$1" in
    --e2e)
      if [[ -n "$PROFILE_OPTION_SEEN" ]]; then
        argument_error "--e2e e --no-e2e sao mutuamente exclusivos"
      fi
      PROFILE_OPTION_SEEN="e2e"
      CLI_RUN_E2E=1
      shift
      ;;
    --no-e2e)
      if [[ -n "$PROFILE_OPTION_SEEN" ]]; then
        argument_error "--e2e e --no-e2e sao mutuamente exclusivos"
      fi
      PROFILE_OPTION_SEEN="no-e2e"
      CLI_RUN_E2E=0
      shift
      ;;
    --suite)
      require_option_value "$1" "$#" "${2-}"
      CLI_E2E_SUITE="$2"
      shift 2
      ;;
    --device)
      require_option_value "$1" "$#" "${2-}"
      if [[ "$LEGACY_DEVICE_SEEN" == "1" ]]; then
        argument_error "nao misture device posicional com --device"
      fi
      CLI_DEVICE="$2"
      shift 2
      ;;
    --api)
      require_option_value "$1" "$#" "${2-}"
      CLI_API_BASE_URL="$2"
      shift 2
      ;;
    -h|--help)
      usage
      exit 0
      ;;
    -*)
      argument_error "opcao desconhecida: $1"
      ;;
    *)
      if [[ "$LEGACY_DEVICE_SEEN" == "1" || -n "$CLI_DEVICE" ]]; then
        argument_error "apenas um device pode ser informado"
      fi
      if ! [[ "$1" =~ $LEGACY_DEVICE_PATTERN ]]; then
        argument_error "device posicional inseguro; use --device com um identificador simples"
      fi
      CLI_DEVICE="$1"
      LEGACY_DEVICE_SEEN=1
      shift
      ;;
  esac
done

if [[ "$RUN_E2E" != "0" && "$RUN_E2E" != "1" ]]; then
  argument_error "RUN_E2E aceita somente 0 ou 1"
fi
REQUESTED_RUN_E2E="${CLI_RUN_E2E:-$RUN_E2E}"
if [[ "$REQUESTED_RUN_E2E" == "1" && -f "$ENV_FILE" ]]; then
  load_local_e2e_env
fi

# A escolha de perfil e congelada antes do dotenv; o arquivo local apenas
# fornece configuracao para um E2E que ja foi solicitado explicitamente.
RUN_E2E="$REQUESTED_RUN_E2E"
API_BASE_URL="${CLI_API_BASE_URL:-${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}}"
E2E_SUITE="${CLI_E2E_SUITE:-${E2E_SUITE:-integration_test/all.dart}}"
DEVICE="${CLI_DEVICE:-${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}}"
E2E_GOOGLE_MAPS_API_KEY="${GOOGLE_MAPS_API_KEY:-${VANRIDES_E2E_GOOGLE_MAPS_API_KEY:-REDACTED_GOOGLE_API_KEY__________________}}"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

is_connectivity_failure() {
  local log_file="$1"
  grep -Eiq \
    'SocketException|Connection refused|Connection reset|Connection closed|Failed host lookup|HandshakeException|HttpException|timed out|TimeoutException|503|502|504|HTTP 5|WebSocket|STOMP' \
    "$log_file"
}

cd "$APP_DIR"
mkdir -p "$QA_LOG_DIR"

log "Quality ratchet tests"
python3 "$SCRIPT_DIR/quality_metrics_test.py"

log "E2E preflight tests"
python3 "$SCRIPT_DIR/e2e_preflight_test.py"

log "E2E real preflight contract tests"
python3 "$SCRIPT_DIR/qa_e2e_real_preflight_test.py"

log "Mobile E2E long-run contract tests"
python3 "$SCRIPT_DIR/qa_mobile_e2e_long_run_test.py"

log "E2E role matrix contract"
"$SCRIPT_DIR/qa_e2e_role_matrix_smoke.sh" --check-only

log "E2E real preflight contract"
"$SCRIPT_DIR/qa_e2e_real_preflight.sh" --check-only

log "Mobile E2E long-run contract"
python3 "$SCRIPT_DIR/qa_mobile_e2e_long_run.py" --check-only

log "Visual matrix smoke contract"
"$SCRIPT_DIR/qa_visual_matrix_smoke.sh" --check-only

log "Performance smoke contract"
"$SCRIPT_DIR/qa_performance_smoke.sh" --check-only

log "Navigation drive simulation smoke contract"
python3 "$SCRIPT_DIR/qa_navigation_drive_sim_smoke_test.py"
"$SCRIPT_DIR/qa_navigation_drive_sim_smoke.sh" --check-only

log "Navigation action auto-sync smoke contract"
python3 "$SCRIPT_DIR/qa_navigation_action_auto_sync_smoke_test.py"
"$SCRIPT_DIR/qa_navigation_action_auto_sync_smoke.py" --check-only

log "Navigation TTS device smoke contract"
python3 "$SCRIPT_DIR/qa_navigation_tts_device_smoke_test.py"
"$SCRIPT_DIR/qa_navigation_tts_device_smoke.sh" --check-only

log "Navigation TTS evidence provenance contract"
python3 "$SCRIPT_DIR/qa_navigation_tts_evidence_test.py"
python3 "$SCRIPT_DIR/qa_navigation_tts_evidence.py" --check-only

log "Navigation audio confirmation review contract"
python3 "$SCRIPT_DIR/qa_navigation_audio_confirmation_review_test.py"
"$SCRIPT_DIR/qa_navigation_audio_confirmation_review.py" --check-only

log "Navigation audio operator handoff contract"
python3 "$SCRIPT_DIR/qa_navigation_audio_operator_handoff_test.py"

log "Navigation pre-pilot readiness contract"
python3 "$SCRIPT_DIR/qa_navigation_pre_pilot_readiness_test.py"
"$SCRIPT_DIR/qa_navigation_pre_pilot_readiness.py" --check-only

log "MapLibre device smoke contract"
python3 "$SCRIPT_DIR/qa_maplibre_device_smoke_test.py"
"$SCRIPT_DIR/qa_maplibre_device_smoke.sh" --check-only

log "MapLibre style/tile smoke contract"
python3 "$SCRIPT_DIR/qa_maplibre_style_tile_smoke_test.py"
"$SCRIPT_DIR/qa_maplibre_style_tile_smoke.sh" --check-only

log "MapLibre tile fixture fetch smoke contract"
python3 "$SCRIPT_DIR/qa_maplibre_tile_fixture_smoke_test.py"
"$SCRIPT_DIR/qa_maplibre_tile_fixture_smoke.sh" --check-only

log "Flutter analyze (bloqueia erros; infos/warnings seguem como dívida técnica)"
flutter analyze --no-fatal-infos --no-fatal-warnings

log "Flutter unit/widget tests"
flutter test

log "Quality ratchet"
"$SCRIPT_DIR/quality_gate.sh"

if [[ "$RUN_E2E" == "1" ]]; then
  DEVICE="$(e2e_resolve_device "$DEVICE")"
  log "E2E preflight ($E2E_SUITE) no device ${DEVICE:-n/a}"
  e2e_preflight_run "$API_BASE_URL" "$E2E_SUITE" "$DEVICE"
  if [[ "${VANRIDES_E2E_PREFLIGHT_ONLY:-0}" == "1" ]]; then
    log "Preflight E2E concluido; Flutter E2E pulado por VANRIDES_E2E_PREFLIGHT_ONLY=1."
    exit 0
  fi
  log "Flutter integration tests ($E2E_SUITE) no device $DEVICE"
  E2E_LOG="$QA_LOG_DIR/e2e-$(date '+%Y%m%d-%H%M%S').log"
  E2E_DART_DEFINES=()
  e2e_build_dart_defines "$API_BASE_URL"
  e2e_build_device_args "$DEVICE"
  set +e
  GOOGLE_MAPS_API_KEY="$E2E_GOOGLE_MAPS_API_KEY" flutter test "$E2E_SUITE" \
    "${E2E_DEVICE_ARGS[@]}" \
    "${E2E_DART_DEFINES[@]}" \
    2>&1 | tee "$E2E_LOG"
  E2E_CODE=${PIPESTATUS[0]}
  set -e

  if [[ "$E2E_CODE" -ne 0 ]]; then
    if is_connectivity_failure "$E2E_LOG"; then
      echo ""
      echo "BLOCKED_VALIDATION_CONNECTIVITY"
      echo "E2E falhou com sinal de conectividade/servidor/backend. Nao retente em loop."
      echo "Log: $E2E_LOG"
      echo "API: $API_BASE_URL"
      exit 86
    fi
    echo "E2E falhou. Log: $E2E_LOG"
    exit "$E2E_CODE"
  fi
else
  log "E2E desabilitado (RUN_E2E=$RUN_E2E). Use --e2e ou RUN_E2E=1 para fluxo critico/smoke."
fi

log "Gate app finalizado com sucesso"
