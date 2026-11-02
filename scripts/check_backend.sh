#!/usr/bin/env bash
set -euo pipefail
export PYTHONDONTWRITEBYTECODE=1
REPOSITORY_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
BACKEND_DIR="$REPOSITORY_DIR/src/backend"
cd "$BACKEND_DIR"
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
  "")
    ./scripts/ci_quality_gate.sh
    # The tests just passed for this checkout. Packaging reuses that compilation.
    ./mvnw -B -DskipTests package
    node "$REPOSITORY_DIR/src/whatsapp/scripts/academic-integration-smoke.mjs"
    ;;
  *) printf 'Uso: bash scripts/check_backend.sh [--light]\n' >&2; exit 2 ;;
esac
