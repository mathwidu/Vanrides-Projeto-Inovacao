#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"

log() {
  printf '\n[codex-cloud-setup] %s\n' "$1"
}

run_sudo() {
  if command -v sudo >/dev/null 2>&1; then
    sudo "$@"
  else
    "$@"
  fi
}

install_apt_packages() {
  if ! command -v apt-get >/dev/null 2>&1; then
    return 0
  fi

  log "Installing backend build packages"
  run_sudo apt-get update
  run_sudo env DEBIAN_FRONTEND=noninteractive apt-get install -y \
    ca-certificates \
    curl \
    git \
    openjdk-17-jdk
}

warm_maven_cache() {
  cd "$ROOT_DIR"
  log "Warming Maven dependency cache"
  ./mvnw -B -DskipTests dependency:go-offline
  ./mvnw -B -DskipTests test-compile
}

install_apt_packages
warm_maven_cache

log "Done. Recommended agent check: ./scripts/ci_quality_gate.sh"
