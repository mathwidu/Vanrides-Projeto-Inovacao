#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

cd "$ROOT_DIR"

echo "[qa-guardian] Running guardian domain evidence suite..."
./mvnw -Dtest=GuardianDomainModelTest,GuardianPassageiroAccessWebMvcTest,PresencaRbacWebMvcTest,LocalizacaoMotoristaControllerWebMvcTest,TenantIsolationWebMvcTest test

echo "[qa-guardian] Guardian domain evidence suite passed."
