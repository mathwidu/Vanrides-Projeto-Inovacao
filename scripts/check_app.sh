#!/usr/bin/env bash
set -euo pipefail
TASK_REPO_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$TASK_REPO_ROOT/src/aplicativo-motorista"
./scripts/ci_quality_gate.sh
python3 -m unittest discover -s integration_test/driver_run -p 'test_*.py'
git diff --exit-code -- pubspec.lock ios/Podfile.lock
