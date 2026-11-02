#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
MODE="check"
OUTPUT_DIR="${VANRIDES_NAV_DRIVE_SIM_DIR:-$APP_DIR/output/navigation-drive-sim/$(date '+%Y%m%d-%H%M%S')}"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_navigation_drive_sim_smoke.sh [opcoes]

Opcoes:
  --check-only          Valida a simulacao sem gerar evidencia. Padrao.
  --run                 Gera markdown/json em output/navigation-drive-sim/.
  --output-dir <path>   Pasta onde salvar evidencias.
  -h, --help            Mostra esta ajuda.

Notas:
  - Nao usa device, backend, audio real, GPS real, parceiro, deploy ou VPS.
  - O modo --run gera evidencia local de uma corrida QA simulada.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      MODE="check"
      shift
      ;;
    --run)
      MODE="run"
      shift
      ;;
    --output-dir)
      if [[ $# -lt 2 ]]; then
        echo "--output-dir requer um caminho" >&2
        usage >&2
        exit 2
      fi
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

cd "$APP_DIR"

if [[ "$MODE" == "check" ]]; then
  dart run tool/navigation_drive_sim_report.dart --check-only
else
  mkdir -p "$OUTPUT_DIR"
  dart run tool/navigation_drive_sim_report.dart --output-dir "$OUTPUT_DIR"
fi
