#!/usr/bin/env bash
set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
STYLE_FILE="$APP_DIR/lib/features/tracking/presentation/widgets/vanrides_maplibre_proof_surface.dart"
FIXTURE="$APP_DIR/test/fixtures/maps/BR-RS/vector/0/0/0.pbf.base64"

MODE="check"

usage() {
  cat <<'EOF'
Uso: ./scripts/qa_maplibre_tile_fixture_smoke.sh [opcoes]

Opcoes:
  --check-only          Sobe servidor local efemero e busca a fixture de tile. Padrao.
  -h, --help            Mostra esta ajuda.

Notas:
  - Este smoke valida fetch local/controlado do contrato style/tile.
  - Ele nao gera tiles reais, nao exige VPS e nao valida PMTiles/MBTiles.
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

validate_tile_fixture_fetch() {
  python3 - "$STYLE_FILE" "$FIXTURE" <<'PY'
import base64
import hashlib
import http.server
import json
import re
import socketserver
import sys
import threading
import urllib.parse
import urllib.request
from pathlib import Path

style_file = Path(sys.argv[1])
fixture_file = Path(sys.argv[2])

expected_template = (
    "http://127.0.0.1:8099/vanrides/maps/BR-RS/vector/{z}/{x}/{y}.pbf"
)
expected_url = expected_template.replace("{z}", "0").replace("{x}", "0").replace(
    "{y}", "0"
)
expected_path = urllib.parse.urlparse(expected_url).path

source = style_file.read_text(encoding="utf-8")
match = re.search(
    r"const\s+vanRidesMapLibreProofStyleJson\s*=\s*'''(.*?)''';",
    source,
    flags=re.DOTALL,
)
if match is None:
    raise SystemExit("MapLibre style JSON const not found")

style = json.loads(match.group(1))
style_json = json.dumps(style)
for forbidden in ("tile.openstreetmap.org", "demotiles.maplibre.org"):
    if forbidden in style_json:
        raise SystemExit(f"forbidden public tile endpoint found: {forbidden}")

vector_source = style.get("sources", {}).get("vanrides-vector-tiles", {})
tiles = vector_source.get("tiles", [])
if expected_template not in tiles:
    raise SystemExit(f"expected tile template not found: {expected_template}")

payload = base64.b64decode(fixture_file.read_text(encoding="utf-8").strip(), validate=True)
if not payload:
    raise SystemExit("tile fixture is empty")
if b"vanrides_smoke" not in payload:
    raise SystemExit("tile fixture does not contain vanrides_smoke layer")
expected_sha = hashlib.sha256(payload).hexdigest()


class FixtureHandler(http.server.BaseHTTPRequestHandler):
    def do_GET(self) -> None:
        if self.path != expected_path:
            self.send_response(404)
            self.end_headers()
            return

        self.send_response(200)
        self.send_header("Content-Type", "application/x-protobuf")
        self.send_header("Access-Control-Allow-Origin", "*")
        self.send_header("Cache-Control", "no-store")
        self.send_header("Content-Length", str(len(payload)))
        self.end_headers()
        self.wfile.write(payload)

    def log_message(self, format: str, *args: object) -> None:
        return


class FixtureServer(socketserver.TCPServer):
    allow_reuse_address = True


parsed = urllib.parse.urlparse(expected_url)
host = parsed.hostname or ""
port = parsed.port or 0
if host != "127.0.0.1" or port != 8099:
    raise SystemExit(f"unexpected tile fixture host/port: {host}:{port}")

try:
    server = FixtureServer((host, port), FixtureHandler)
except OSError as exc:
    raise SystemExit(f"unable to bind tile fixture server on {host}:{port}: {exc}")

thread = threading.Thread(target=server.serve_forever, daemon=True)
thread.start()

try:
    request = urllib.request.Request(
        expected_url,
        headers={"Accept": "application/x-protobuf"},
    )
    with urllib.request.urlopen(request, timeout=5) as response:
        body = response.read()
        status = response.status
        content_type = response.headers.get("Content-Type", "")
        cors = response.headers.get("Access-Control-Allow-Origin", "")
finally:
    server.shutdown()
    server.server_close()

if status != 200:
    raise SystemExit(f"unexpected fixture status: {status}")
if content_type.split(";", maxsplit=1)[0] != "application/x-protobuf":
    raise SystemExit(f"unexpected fixture content-type: {content_type}")
if cors != "*":
    raise SystemExit(f"unexpected fixture CORS header: {cors}")
if body != payload:
    raise SystemExit("fetched tile fixture does not match committed fixture")
actual_sha = hashlib.sha256(body).hexdigest()
if actual_sha != expected_sha:
    raise SystemExit(f"unexpected fixture sha256: {actual_sha}")

print(
    "MAPLIBRE_TILE_FIXTURE_SMOKE_OK "
    f"url={expected_url} bytes={len(body)} sha256={actual_sha}"
)
PY
}

case "$MODE" in
  check)
    validate_tile_fixture_fetch
    ;;
  *)
    echo "Modo invalido: $MODE" >&2
    exit 2
    ;;
esac
