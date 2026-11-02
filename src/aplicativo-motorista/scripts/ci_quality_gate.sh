#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
SCRIPT_DIR="$ROOT_DIR/scripts"

resolve_node_bin() {
  if [[ -n "${NODE_BIN:-}" ]]; then
    if [[ -x "$NODE_BIN" ]]; then
      printf '%s\n' "$NODE_BIN"
      return 0
    fi
    command -v "$NODE_BIN" 2>/dev/null && return 0
  fi

  local path_node
  path_node="$(command -v node 2>/dev/null || true)"
  if [[ -n "$path_node" ]]; then
    printf '%s\n' "$path_node"
    return 0
  fi

  if [[ -n "${HOME:-}" ]]; then
    local cache_root="${XDG_CACHE_HOME:-$HOME/.cache}"
    local codex_node="$cache_root/codex-runtimes/codex-primary-runtime/dependencies/node/bin/node"
    if [[ -x "$codex_node" ]]; then
      printf '%s\n' "$codex_node"
      return 0
    fi
  fi

  return 1
}

NODE_BIN="$(resolve_node_bin || true)"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

cd "$ROOT_DIR"

log "CI quality gate wiring tests"
python3 "$SCRIPT_DIR/ci_quality_gate_test.py"

log "Trip live location real-backend probe contract tests"
python3 "$SCRIPT_DIR/qa_trip_live_location_real_backend_test.py"

log "Admin bootstrap consumer contract tests"
if [[ -z "$NODE_BIN" ]]; then
  echo "Node.js nao encontrado no NODE_BIN, PATH ou runtime local do Codex." >&2
  exit 1
fi
"$NODE_BIN" --test "$ROOT_DIR/tool/admin_portal_bootstrap_contract_test.mjs"

log "App card quality gate (CI profile, sem E2E)"
"$SCRIPT_DIR/qa_gate_card.sh" --no-e2e

log "Gate app CI finalizado com sucesso"
