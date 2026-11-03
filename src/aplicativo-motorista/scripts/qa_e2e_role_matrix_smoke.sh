#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOC="$APP_DIR/backlog/QA-E2E-Role-Split.md"
MODE="check"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_e2e_role_matrix_smoke.sh [opcoes]

Opcoes:
  --check-only  Valida a matriz E2E por papel sem device/backend. Padrao.
  --list        Lista a matriz canonica de papeis e runners.
  -h, --help    Mostra esta ajuda.

Notas:
  - Use --check-only no gate rapido.
  - E2E real por papel continua opt-in porque depende de device, backend,
    navegador e fixture.
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

role_matrix_rows() {
  cat <<'EOF'
admin-web|admin|web|scripts/qa_admin_portal_web_e2e.sh|tool/admin_portal_web_e2e.mjs|output/e2e-role-smoke/admin-web/<timestamp>|bootstrap-or-operational-fixture
driver-mobile|motorista|mobile|scripts/run_dev_e2e.sh|integration_test/motorista/all.dart|output/e2e-role-smoke/driver-mobile/<timestamp>|device-and-dev-backend
passenger-mobile|passageiro|mobile|scripts/run_dev_e2e.sh|integration_test/passageiro/all.dart|output/e2e-role-smoke/passenger-mobile/<timestamp>|device-and-dev-backend
guardian-mobile|responsavel|mobile|scripts/run_dev_e2e.sh|integration_test/guardian/all.dart|output/e2e-role-smoke/guardian-mobile/<timestamp>|guardian-fixture-device-and-dev-backend
EOF
}

require_doc_text() {
  local pattern="$1"
  if ! grep -Fq "$pattern" "$DOC"; then
    echo "Matriz E2E por papel sem termo obrigatorio: $pattern" >&2
    exit 1
  fi
}

validate_matrix() {
  local tmp_file
  tmp_file="$(mktemp)"
  role_matrix_rows > "$tmp_file"

  awk -F'|' '
    NF != 7 {
      printf "Linha invalida (%d campos): %s\n", NF, $0 > "/dev/stderr"
      exit 1
    }
    $1 == "" || $2 == "" || $3 == "" || $4 == "" || $5 == "" || $6 == "" || $7 == "" {
      printf "Campo vazio: %s\n", $0 > "/dev/stderr"
      exit 1
    }
    seen[$1]++ {
      printf "Papel duplicado: %s\n", $1 > "/dev/stderr"
      exit 1
    }
    $3 != "mobile" && $3 != "web" {
      printf "Plataforma invalida: %s\n", $0 > "/dev/stderr"
      exit 1
    }
    $6 !~ /^output\/e2e-role-smoke\// {
      printf "Pasta de evidencia invalida: %s\n", $0 > "/dev/stderr"
      exit 1
    }
  ' "$tmp_file"

  while IFS='|' read -r role _label _platform runner target _evidence _setup; do
    if [[ ! -x "$APP_DIR/$runner" ]]; then
      echo "Runner ausente ou sem permissao de execucao: $runner" >&2
      rm -f "$tmp_file"
      exit 1
    fi
    if [[ ! -f "$APP_DIR/$target" ]]; then
      echo "Suite/alvo ausente: $target" >&2
      rm -f "$tmp_file"
      exit 1
    fi
    if [[ ! -f "$DOC" ]]; then
      echo "Documento ausente: $DOC" >&2
      rm -f "$tmp_file"
      exit 1
    fi
    require_doc_text "$role"
  done < "$tmp_file"

  require_doc_text "output/e2e-role-smoke/"
  require_doc_text "setup"
  require_doc_text "UX/contrato"
  require_doc_text "Evidencias por papel"
  require_doc_text "BLOCKED_VALIDATION_"

  if grep -Eiq 'TBD|TODO|implement later|fill in|Similar to|\?\?\?' "$DOC"; then
    echo "Documento E2E por papel contem placeholder proibido." >&2
    rm -f "$tmp_file"
    exit 1
  fi

  local total mobile web
  total="$(wc -l < "$tmp_file" | tr -d ' ')"
  mobile="$(awk -F'|' '$3 == "mobile" { count += 1 } END { print count + 0 }' "$tmp_file")"
  web="$(awk -F'|' '$3 == "web" { count += 1 } END { print count + 0 }' "$tmp_file")"
  rm -f "$tmp_file"

  printf 'E2E_ROLE_MATRIX_OK roles=%s mobile=%s web=%s\n' "$total" "$mobile" "$web"
}

case "$MODE" in
  check)
    validate_matrix
    ;;
  list)
    role_matrix_rows
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
