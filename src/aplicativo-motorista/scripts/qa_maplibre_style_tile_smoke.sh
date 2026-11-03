#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
STYLE_FILE="$APP_DIR/lib/features/tracking/presentation/widgets/vanrides_maplibre_proof_surface.dart"
DOC="$APP_DIR/backlog/QA-MapLibre-Device-Smoke.md"

MODE="check"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_maplibre_style_tile_smoke.sh [opcoes]

Opcoes:
  --check-only          Valida o contrato style/tile sem device ou tile server. Padrao.
  -h, --help            Mostra esta ajuda.

Notas:
  - Este smoke valida formato e politica de style/tile.
  - Ele nao busca tiles reais, nao exige VPS e nao valida PMTiles/MBTiles.
EOF
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --check-only)
      MODE="check"
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

validate_style_tile_contract() {
  python3 - "$STYLE_FILE" <<'PY'
import json
import re
import sys
from pathlib import Path

style_file = Path(sys.argv[1])
source = style_file.read_text(encoding="utf-8")
match = re.search(
    r"const\s+vanRidesMapLibreProofStyleJson\s*=\s*'''(.*?)''';",
    source,
    flags=re.DOTALL,
)
if match is None:
    raise SystemExit("MapLibre style JSON const not found")

style = json.loads(match.group(1))
metadata = style.get("metadata", {})
sources = style.get("sources", {})
vector_source = sources.get("vanrides-vector-tiles", {})
tiles = vector_source.get("tiles", [])
for forbidden in ("tile.openstreetmap.org", "demotiles.maplibre.org"):
    if forbidden in json.dumps(style):
        raise SystemExit(f"forbidden public tile endpoint found: {forbidden}")

expected_tile = (
    "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf"
)
assert style.get("version") == 8
assert "VanRides MapLibre proof" in style.get("name", "")
assert metadata.get("vanrides:region") == "BR-RS"
assert metadata.get("vanrides:styleVersion") == "style-smoke-v1"
assert metadata.get("vanrides:tileSetVersion") == "br-rs-smoke-v1"
assert vector_source.get("type") == "vector"
assert expected_tile in tiles
assert vector_source.get("attribution") == "VanRides self-hosted tiles smoke"
PY

  for pattern in \
    "MAPLIBRE_STYLE_TILE_SMOKE_OK" \
    "vanrides-vector-tiles" \
    "BR-RS" \
    "nao busca tiles reais"; do
    if ! grep -Fq -- "$pattern" "$DOC"; then
      echo "Contrato style/tile sem termo obrigatorio no QA doc: $pattern" >&2
      exit 1
    fi
  done

  printf 'MAPLIBRE_STYLE_TILE_SMOKE_OK source=vanrides-vector-tiles region=BR-RS tileSet=br-rs-smoke-v1\n'
}

case "$MODE" in
  check)
    validate_style_tile_contract
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
