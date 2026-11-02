#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MATRIX_SCRIPT="$SCRIPT_DIR/qa_e2e_role_matrix_smoke.sh"

MODE="check"
ROLE="all"
API_BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
DEVICE="${VANRIDES_TEST_DEVICE:-${VANRIDES_IOS_DEVICE_ID:-}}"
EVIDENCE_ROOT="output/e2e-role-smoke"
TIMESTAMP="$(date -u '+%Y%m%dT%H%M%SZ')"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_e2e_real_preflight.sh [opcoes]

Modos:
  --check-only     Valida contrato sem criar evidencia, device, backend ou navegador. Padrao.
  --dry-run        Cria evidencia do comando que seria executado por papel.
  --run-preflight  Roda somente preflight/setup por papel; nao roda Flutter E2E longo.
  --list           Lista a matriz canonica usada pelo preflight.

Opcoes:
  --role <id|all>         Papel canonico: admin-web, driver-mobile, passenger-mobile, guardian-mobile.
  --api <url>             API usada quando o preflight real for executado.
  --device <id>           Device Flutter para papeis mobile.
  --evidence-root <dir>   Raiz das evidencias. Padrao: output/e2e-role-smoke.
  --timestamp <id>        Timestamp deterministico para evidencia.
  -h, --help              Mostra esta ajuda.

Notas:
  - O gate local deve usar apenas --check-only.
  - --run-preflight usa VANRIDES_E2E_PREFLIGHT_ONLY=1 nos papeis mobile.
  - Web admin nao roda Playwright neste script; o preflight web valida somente setup local basico.
EOF
}

require_arg() {
  local flag="$1"
  local value="${2:-}"
  if [[ -z "$value" ]]; then
    echo "Valor ausente para $flag" >&2
    usage >&2
    exit 2
  fi
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      MODE="check"
      shift
      ;;
    --dry-run)
      MODE="dry_run"
      shift
      ;;
    --run-preflight)
      MODE="run"
      shift
      ;;
    --list)
      MODE="list"
      shift
      ;;
    --role)
      require_arg "$1" "${2:-}"
      ROLE="$2"
      shift 2
      ;;
    --api)
      require_arg "$1" "${2:-}"
      API_BASE_URL="${2%/}"
      shift 2
      ;;
    --device)
      require_arg "$1" "${2:-}"
      DEVICE="$2"
      shift 2
      ;;
    --evidence-root)
      require_arg "$1" "${2:-}"
      EVIDENCE_ROOT="$2"
      shift 2
      ;;
    --timestamp)
      require_arg "$1" "${2:-}"
      TIMESTAMP="$2"
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

cd "$APP_DIR"

matrix_rows() {
  "$MATRIX_SCRIPT" --list
}

valid_roles_csv() {
  matrix_rows | awk -F'|' '{ if (NR > 1) printf ", "; printf "%s", $1 } END { print "" }'
}

validate_role() {
  if [[ "$ROLE" == "all" ]]; then
    return 0
  fi

  if ! matrix_rows | awk -F'|' -v role="$ROLE" '$1 == role { found = 1 } END { exit found ? 0 : 1 }'; then
    echo "Papel invalido: $ROLE" >&2
    echo "Papeis validos: $(valid_roles_csv)" >&2
    exit 2
  fi
}

selected_rows() {
  if [[ "$ROLE" == "all" ]]; then
    matrix_rows
    return 0
  fi

  matrix_rows | awk -F'|' -v role="$ROLE" '$1 == role { print }'
}

count_rows() {
  local platform="${1:-}"
  if [[ -z "$platform" ]]; then
    matrix_rows | wc -l | tr -d ' '
  else
    matrix_rows | awk -F'|' -v platform="$platform" '$3 == platform { count += 1 } END { print count + 0 }'
  fi
}

command_for_role() {
  local role="$1"
  local platform="$2"
  local runner="$3"
  local target="$4"
  local evidence_dir="$5"

  if [[ "$platform" == "mobile" ]]; then
    if [[ -n "$DEVICE" ]]; then
      printf 'VANRIDES_E2E_PREFLIGHT_ONLY=1 VANRIDES_API_BASE_URL=%s %s %s %s\n' \
        "$API_BASE_URL" "$runner" "$target" "$DEVICE"
    else
      printf 'VANRIDES_E2E_PREFLIGHT_ONLY=1 VANRIDES_API_BASE_URL=%s %s %s\n' \
        "$API_BASE_URL" "$runner" "$target"
    fi
  else
    printf 'VANRIDES_API_BASE_URL=%s ADMIN_PORTAL_E2E_REPORT_DIR=%s ADMIN_PORTAL_E2E_SCREENSHOT_DIR=%s/screenshots %s\n' \
      "$API_BASE_URL" "$evidence_dir" "$evidence_dir" "$runner"
  fi
}

write_summary() {
  local evidence_dir="$1"
  local status="$2"
  local role="$3"
  local label="$4"
  local platform="$5"
  local runner="$6"
  local target="$7"
  local setup="$8"
  local command_text="$9"
  local classification="${10}"
  local log_file="${11}"

  cat > "$evidence_dir/SUMMARY.md" <<EOF
# E2E Real Preflight

Status: $status
Papel: $role
Label: $label
Plataforma: $platform
Runner: $runner
Target: $target
Setup esperado: $setup
API: $API_BASE_URL
Device: ${DEVICE:-auto}
Evidencia: $evidence_dir
Classificacao: $classification
Log: ${log_file:-n/a}

## Comando

\`\`\`bash
$command_text
\`\`\`

## Limites

- Este preflight nao roda Flutter E2E longo por padrao.
- Se houver \`BLOCKED_VALIDATION_*\`, tratar como setup ate prova contraria.
- Registrar este path no QA Log quando a execucao real for usada como evidencia.
EOF
}

dry_run_role() {
  local role="$1"
  local label="$2"
  local platform="$3"
  local runner="$4"
  local target="$5"
  local setup="$6"

  local evidence_dir="$EVIDENCE_ROOT/$role/$TIMESTAMP"
  mkdir -p "$evidence_dir"

  local command_text
  command_text="$(command_for_role "$role" "$platform" "$runner" "$target" "$evidence_dir")"
  write_summary "$evidence_dir" "DRY_RUN" "$role" "$label" "$platform" "$runner" "$target" "$setup" "$command_text" "not-run" ""
  printf 'E2E_REAL_PREFLIGHT_DRY_RUN role=%s evidence=%s\n' "$role" "$evidence_dir"
}

run_web_preflight() {
  local evidence_dir="$1"
  local log_file="$evidence_dir/preflight.log"

  {
    echo "Web admin preflight setup check"
    command -v flutter
    command -v node
    test -f "$APP_DIR/tool/admin_portal_web_e2e.mjs"
    test -x "$APP_DIR/scripts/qa_admin_portal_web_e2e.sh"
  } > "$log_file" 2>&1
}

run_mobile_preflight() {
  local runner="$1"
  local target="$2"
  local log_file="$3"

  if [[ -n "$DEVICE" ]]; then
    VANRIDES_E2E_PREFLIGHT_ONLY=1 VANRIDES_API_BASE_URL="$API_BASE_URL" \
      "$APP_DIR/$runner" "$target" "$DEVICE" > "$log_file" 2>&1
  else
    VANRIDES_E2E_PREFLIGHT_ONLY=1 VANRIDES_API_BASE_URL="$API_BASE_URL" \
      "$APP_DIR/$runner" "$target" > "$log_file" 2>&1
  fi
}

run_preflight_role() {
  local role="$1"
  local label="$2"
  local platform="$3"
  local runner="$4"
  local target="$5"
  local setup="$6"

  local evidence_dir="$EVIDENCE_ROOT/$role/$TIMESTAMP"
  local log_file="$evidence_dir/preflight.log"
  mkdir -p "$evidence_dir"

  local command_text
  command_text="$(command_for_role "$role" "$platform" "$runner" "$target" "$evidence_dir")"

  set +e
  if [[ "$platform" == "mobile" ]]; then
    run_mobile_preflight "$runner" "$target" "$log_file"
  else
    run_web_preflight "$evidence_dir"
  fi
  local exit_code=$?
  set -e

  if [[ "$exit_code" -ne 0 ]]; then
    local classification
    classification="$(grep -Eo 'BLOCKED_VALIDATION_[A-Z_]+' "$log_file" | head -n 1 || true)"
    classification="${classification:-BLOCKED_VALIDATION_SETUP}"
    write_summary "$evidence_dir" "BLOCKED" "$role" "$label" "$platform" "$runner" "$target" "$setup" "$command_text" "$classification" "$log_file"
    printf 'E2E_REAL_PREFLIGHT_BLOCKED role=%s classification=%s evidence=%s\n' "$role" "$classification" "$evidence_dir" >&2
    return "$exit_code"
  fi

  write_summary "$evidence_dir" "PASS" "$role" "$label" "$platform" "$runner" "$target" "$setup" "$command_text" "setup-ok" "$log_file"
  printf 'E2E_REAL_PREFLIGHT_PASS role=%s evidence=%s\n' "$role" "$evidence_dir"
}

check_contract() {
  "$MATRIX_SCRIPT" --check-only
  printf 'E2E_REAL_PREFLIGHT_CONTRACT_OK roles=%s mobile=%s web=%s output=%s\n' \
    "$(count_rows)" "$(count_rows mobile)" "$(count_rows web)" "$EVIDENCE_ROOT"
}

if [[ "$MODE" == "list" ]]; then
  matrix_rows
  exit 0
fi

validate_role

if [[ "$MODE" == "check" ]]; then
  check_contract
  exit 0
fi

"$MATRIX_SCRIPT" --check-only >/dev/null

overall=0
while IFS='|' read -r role label platform runner target _evidence setup; do
  case "$MODE" in
    dry_run)
      dry_run_role "$role" "$label" "$platform" "$runner" "$target" "$setup"
      ;;
    run)
      if ! run_preflight_role "$role" "$label" "$platform" "$runner" "$target" "$setup"; then
        overall=1
      fi
      ;;
    *)
      echo "Modo invalido: $MODE" >&2
      exit 2
      ;;
  esac
done < <(selected_rows)

exit "$overall"
