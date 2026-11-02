#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_PORT="${ADMIN_PORTAL_E2E_WEB_PORT:-7357}"
APP_HOST="${ADMIN_PORTAL_E2E_WEB_HOST:-127.0.0.1}"
APP_URL="${ADMIN_PORTAL_E2E_URL:-http://${APP_HOST}:${APP_PORT}}"
BASE_URL="${VANRIDES_API_BASE_URL:-http://127.0.0.1:8080}"
TS="$(date +%s)"
RUN_ID="$(uuidgen | tr '[:upper:]' '[:lower:]' | tr -d '-')"
REPORT_RUN_ID="${ADMIN_PORTAL_E2E_RUN_ID:-${TS}-${RUN_ID}}"
REPORT_DIR="${ADMIN_PORTAL_E2E_REPORT_DIR:-output/admin-portal-e2e/${REPORT_RUN_ID}}"
FIXTURE_SOURCE="${ADMIN_PORTAL_E2E_FIXTURE_SOURCE:-${FIXTURE_ENV:-${fixture_env:-}}}"

if [[ -n "$FIXTURE_SOURCE" ]]; then
  if [[ ! -f "$FIXTURE_SOURCE" ]]; then
    echo "fixture.env nao encontrado: $FIXTURE_SOURCE" >&2
    exit 2
  fi
  set -a
  # shellcheck disable=SC1090
  source "$FIXTURE_SOURCE"
  set +a
fi

export ADMIN_PORTAL_E2E_URL="$APP_URL"
export VANRIDES_API_BASE_URL="$BASE_URL"
export ADMIN_PORTAL_E2E_RUN_ID="$REPORT_RUN_ID"
export ADMIN_PORTAL_E2E_REPORT_DIR="$REPORT_DIR"
export ADMIN_PORTAL_E2E_SCREENSHOT_DIR="${ADMIN_PORTAL_E2E_SCREENSHOT_DIR:-${REPORT_DIR}/screenshots}"
export ADMIN_PORTAL_E2E_COMPANY_NAME="${ADMIN_PORTAL_E2E_COMPANY_NAME:-${company_name:-Portal E2E $TS-$RUN_ID}}"
export ADMIN_PORTAL_E2E_NAME="${ADMIN_PORTAL_E2E_NAME:-Admin E2E Portal}"
export ADMIN_PORTAL_E2E_EMAIL="${ADMIN_PORTAL_E2E_EMAIL:-${admin_email:-admin.portal.$TS.$RUN_ID@vanrides.dev}}"
export ADMIN_PORTAL_E2E_PHONE="${ADMIN_PORTAL_E2E_PHONE:-51999$(printf "%06d" $((TS % 1000000)))}"
export ADMIN_PORTAL_E2E_PASSWORD="${ADMIN_PORTAL_E2E_PASSWORD:-${admin_password:-Admin@1234}}"
export ADMIN_PORTAL_E2E_FIXTURE_SOURCE="$FIXTURE_SOURCE"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

write_setup_failure_report() {
  local step="$1"
  local message="$2"
  local report_abs="$ROOT_DIR/$ADMIN_PORTAL_E2E_REPORT_DIR"
  mkdir -p "$report_abs"
  ROOT_DIR="$ROOT_DIR" REPORT_STEP="$step" REPORT_MESSAGE="$message" python3 - <<'PY'
import json
import os
from datetime import datetime, timezone
from pathlib import Path

report_dir = Path(os.environ["ROOT_DIR"]) / os.environ["ADMIN_PORTAL_E2E_REPORT_DIR"]
step = os.environ["REPORT_STEP"]
message = os.environ["REPORT_MESSAGE"]
report = {
    "schemaVersion": 1,
    "runId": os.environ["ADMIN_PORTAL_E2E_RUN_ID"],
    "status": "FAIL",
    "startedAt": datetime.now(timezone.utc).isoformat(),
    "endedAt": datetime.now(timezone.utc).isoformat(),
    "appUrl": os.environ["ADMIN_PORTAL_E2E_URL"],
    "apiBaseUrl": os.environ["VANRIDES_API_BASE_URL"],
    "fixture": {"mode": "not-loaded", "source": os.environ.get("ADMIN_PORTAL_E2E_FIXTURE_SOURCE") or None},
    "steps": [{"name": step, "kind": "setup", "status": "FAIL", "durationMs": 0, "error": message}],
    "screenshots": [],
    "limits": [],
    "pending": ["Corrigir setup antes de usar as evidencias visuais como aceitacao de UX."],
    "failures": {"setup": [{"step": step, "kind": "setup", "message": message, "stack": []}], "uxContract": []},
}
(report_dir / "manifest.json").write_text(json.dumps(report, indent=2, ensure_ascii=False) + "\n")
(report_dir / "SUMMARY.md").write_text(
    "# Admin Portal Web E2E\n\n"
    "Status: FAIL\n"
    f"Run ID: {report['runId']}\n"
    f"API: {report['apiBaseUrl']}\n"
    f"App: {report['appUrl']}\n\n"
    "## Falhas de setup\n"
    f"- {step}: {message}\n\n"
    "## Falhas de UX/contrato\n"
    "- Nenhuma; o teste não chegou ao navegador.\n"
)
PY
}

cleanup() {
  if [[ -n "${STATIC_SERVER_PID:-}" ]] && kill -0 "$STATIC_SERVER_PID" >/dev/null 2>&1; then
    kill "$STATIC_SERVER_PID" >/dev/null 2>&1 || true
    wait "$STATIC_SERVER_PID" 2>/dev/null || true
  fi
  if [[ -n "${WEB_SERVER_PID:-}" ]] && kill -0 "$WEB_SERVER_PID" >/dev/null 2>&1; then
    kill "$WEB_SERVER_PID" >/dev/null 2>&1 || true
    wait "$WEB_SERVER_PID" 2>/dev/null || true
  fi
}

trap cleanup EXIT

log "Gerando build web estático do portal em $APP_URL"
cd "$ROOT_DIR"
if ! flutter build web \
    --dart-define=VANRIDES_API_BASE_URL="$BASE_URL" \
    --dart-define=VANRIDES_WEB_E2E_SEMANTICS=true \
    > /tmp/vanrides_admin_portal_web_e2e.log 2>&1; then
  write_setup_failure_report "Build web do portal" "flutter build web falhou; veja /tmp/vanrides_admin_portal_web_e2e.log"
  tail -n 100 /tmp/vanrides_admin_portal_web_e2e.log || true
  exit 1
fi

log "Servindo build web estático"
cd "$ROOT_DIR/build/web"
python3 -m http.server "$APP_PORT" --bind "$APP_HOST" \
  > /tmp/vanrides_admin_portal_static_server.log 2>&1 &
STATIC_SERVER_PID=$!

log "Aguardando servidor responder"
for _ in {1..180}; do
  if curl -fsS "$APP_URL" >/dev/null 2>&1; then
    break
  fi
  sleep 1
done

if ! curl -fsS "$APP_URL" >/dev/null 2>&1; then
  echo "FAIL: servidor estático do portal não respondeu em $APP_URL"
  write_setup_failure_report "Servidor estático do portal" "servidor não respondeu em $APP_URL"
  tail -n 100 /tmp/vanrides_admin_portal_web_e2e.log || true
  tail -n 100 /tmp/vanrides_admin_portal_static_server.log || true
  exit 1
fi

log "Garantindo navegador do Playwright"
if [[ ! -f "$ROOT_DIR/node_modules/playwright/package.json" ]]; then
  (cd "$ROOT_DIR" && npm ci)
fi
npx playwright install chromium >/dev/null 2>&1

log "Executando E2E web do portal admin"
node "$ROOT_DIR/tool/admin_portal_web_e2e.mjs"

log "E2E web do portal admin finalizado com sucesso"
