#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
DOC="$APP_DIR/backlog/QA-Performance-Control.md"
MODE="check"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_performance_smoke.sh [opcoes]

Opcoes:
  --check-only  Valida o contrato de performance sem device/backend. Padrao.
  --list        Lista as superficies criticas monitoradas.
  -h, --help    Mostra esta ajuda.

Notas:
  - Use --check-only no gate rapido.
  - Evidencias pesadas ficam fora do Git em output/performance-smoke/.
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

performance_surfaces() {
  cat <<'EOF'
passenger-tracking-map|TripLiveMapPanel/MapFirstTripTrackingView|map render, route geometry, live location updates|profile/manual smoke when map UI changes
passenger-footer-sheet|PassengerMapFooterSheetScaffold/passenger trip sheets|drag, snap, expanded content, text scale|widget smoke plus visual/performance note
driver-navigation-panel|DriverNavigationPanel/DriverHomeNavigationPanel|navigation instruction refresh, ETA, fallback state|focused widget smoke plus QA log note
driver-active-trip-sheet|driver active trip sheet widgets|next action density, stop list, route context|focused driver widget smoke plus QA log note
guardian-location-surface|Guardian map footer/location surfaces|linked passenger switch, location status, bottom nav|guardian widget or manual smoke note
shared-motion-policy|VanRidesMotionPolicy/TripLiveMapMotionPolicy|animations, reduced motion, camera settle delay|motion unit tests plus reduced-motion note
map-marker-layer|VanRides map markers and deterministic surfaces|marker density, custom paint, repaint cost|marker widget tests plus follow-up if jank appears
EOF
}

require_doc_text() {
  local pattern="$1"
  if ! grep -Fq "$pattern" "$DOC"; then
    echo "Contrato de performance sem termo obrigatorio: $pattern" >&2
    exit 1
  fi
}

validate_surfaces() {
  local tmp_file
  tmp_file="$(mktemp)"
  performance_surfaces > "$tmp_file"

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
      printf "Superficie duplicada: %s\n", $1 > "/dev/stderr"
      exit 1
    }
  ' "$tmp_file"

  if [[ ! -f "$DOC" ]]; then
    echo "Documento ausente: $DOC" >&2
    rm -f "$tmp_file"
    exit 1
  fi

  while IFS='|' read -r surface _owner _risk _evidence; do
    require_doc_text "$surface"
  done < "$tmp_file"

  require_doc_text "output/performance-smoke/"
  require_doc_text "follow-up antes de release"
  require_doc_text "setup"
  require_doc_text "regressao de performance"
  require_doc_text "Gate rapido"

  if grep -Eiq 'TBD|TODO|implement later|fill in|Similar to|\?\?\?' "$DOC"; then
    echo "Documento de performance contem placeholder proibido." >&2
    rm -f "$tmp_file"
    exit 1
  fi

  local total
  total="$(wc -l < "$tmp_file" | tr -d ' ')"
  rm -f "$tmp_file"

  printf 'PERFORMANCE_CONTROL_OK surfaces=%s evidence_root=output/performance-smoke\n' "$total"
}

case "$MODE" in
  check)
    validate_surfaces
    ;;
  list)
    performance_surfaces
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
