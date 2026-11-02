#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
REPOSITORY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPOSITORY_DIR/src/backend"
cd "$BACKEND_DIR"
run_integration() {
  ./mvnw -B -DskipTests package
  node "$REPOSITORY_DIR/src/whatsapp/scripts/academic-integration-smoke.mjs"
}
case "${1:-}" in
  --light)
    # Same contract tests as the canonical gate, without Maven, Docker or a network request.
    for check in ci_quality_gate qa_report_evidence qa_evidence_retention qa_maven_evidence_lock \
      qa_location_read_smoke qa_rate_limit_trust_load qa_rate_limit_trust_smoke \
      qa_email_confirmation_intent_smoke quality_metrics dev_fixture_preflight \
      seed_operational_service_day_fixture bootstrap_fixture_contract vanrides_dev_ops; do
      python3 "scripts/${check}_test.py"
    done
    ./scripts/quality_gate.sh
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
