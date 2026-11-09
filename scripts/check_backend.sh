#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
REPOSITORY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPOSITORY_DIR/src/backend"
cd "$BACKEND_DIR"
node --test --test-reporter=dot "$REPOSITORY_DIR/scripts/homologacao/team_ui_test.mjs"
run_integration() {
  ./mvnw -B -ntp -DskipTests package
  node "$REPOSITORY_DIR/src/whatsapp/scripts/academic-integration-smoke.mjs"
}
case "${1:-}" in
  --light)
    # Single definition of quick checks, shared with the canonical gate and CI.
    ./scripts/ci_quality_gate.sh --quick
    ;;
  --integration-only)
    # Partial check, never a substitute for the mandatory canonical gate.
    printf "Checagem parcial de integração; a suíte canônica continua obrigatória.\n"
    run_integration
    ;;
  "")
    ./scripts/ci_quality_gate.sh
    # Sequential local gate: every test must pass before integration starts.
    run_integration
    ;;
  *) printf 'Uso: bash scripts/check_backend.sh [--light | --integration-only]\n' >&2; exit 2 ;;
esac
