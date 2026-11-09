#!/usr/bin/env bash
set -euo pipefail

# Contract tests import sibling Python modules before the clean-checkout gate.
export PYTHONDONTWRITEBYTECODE=1

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() {
  printf '\n[%s] %s\n' "$(date '+%H:%M:%S')" "$1"
}

cd "$ROOT_DIR"

# The partial mode is used by CI before any compiler/container job starts.
case "${1:-}" in
  ""|--quick) [[ $# -le 1 ]] || { echo 'Uso: ci_quality_gate.sh [--quick]' >&2; exit 2; } ;;
  *) echo 'Uso: ci_quality_gate.sh [--quick]' >&2; exit 2 ;;
esac

log "Quality ratchet (antes de compilar ou iniciar contêineres)"
"$ROOT_DIR/scripts/quality_gate.sh"

log "CI quality gate wiring tests"
python3 "$ROOT_DIR/scripts/ci_quality_gate_test.py"

log "QA report evidence validator tests"
python3 "$ROOT_DIR/scripts/qa_report_evidence_test.py"

log "QA evidence retention tests"
python3 "$ROOT_DIR/scripts/qa_evidence_retention_test.py"

log "Maven evidence lock lifecycle tests"
python3 "$ROOT_DIR/scripts/qa_maven_evidence_lock_test.py"

log "Location read same-JAR smoke contract tests"
python3 "$ROOT_DIR/scripts/qa_location_read_smoke_test.py"

log "Rate limit trust load and same-JAR smoke contract tests"
python3 "$ROOT_DIR/scripts/qa_rate_limit_trust_load_test.py"
python3 "$ROOT_DIR/scripts/qa_rate_limit_trust_smoke_test.py"

log "Explicit email confirmation same-JAR smoke contract tests"
python3 "$ROOT_DIR/scripts/qa_email_confirmation_intent_smoke_test.py"

log "Quality ratchet tests"
python3 "$ROOT_DIR/scripts/quality_metrics_test.py"

log "DEV fixture preflight tests"
python3 "$ROOT_DIR/scripts/dev_fixture_preflight_test.py"
python3 "$ROOT_DIR/scripts/seed_operational_service_day_fixture_test.py"

log "Bootstrap fixture contract tests"
python3 "$ROOT_DIR/scripts/bootstrap_fixture_contract_test.py"

log "DEV ops guardrail tests"
python3 "$ROOT_DIR/scripts/vanrides_dev_ops_test.py"

log "PostgreSQL group selection and compact aggregation tests"
python3 "$ROOT_DIR/scripts/qa_test_groups_test.py"

if [[ "${1:-}" == --quick ]]; then
  log "Verificações rápidas aprovadas; PostgreSQL e jornadas HTTP não executados"
  exit 0
fi

log "Backend tests with mandatory PostgreSQL matrix"
"$ROOT_DIR/scripts/qa_integration_profile.sh" --all-tests

log "Gate backend CI finalizado com sucesso"
