#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

python3 "$ROOT_DIR/scripts/quality_metrics.py" \
  --root "$ROOT_DIR" \
  --write-baseline "quality/ratchet-baseline.json"
