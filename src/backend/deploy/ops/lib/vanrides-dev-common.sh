#!/usr/bin/env bash
set -Eeuo pipefail

PATH="/usr/local/sbin:/usr/local/bin:/usr/sbin:/usr/bin:/sbin:/bin"

readonly VANRIDES_DEV_STACK="vanrides-dev"
readonly VANRIDES_DEV_API_SERVICE="vanrides-dev_api"
readonly VANRIDES_DEV_DB_SERVICE="vanrides-dev_db"
readonly VANRIDES_DEV_DB_NAME="vanrides_db"
readonly VANRIDES_DEV_DB_USER="vanrides_user"
readonly VANRIDES_DEV_BASE_URL="${VANRIDES_DEV_BASE_URL:-}"
readonly VANRIDES_DEV_BACKUP_DIR="${VANRIDES_DEV_BACKUP_DIR:-/var/backups/vanrides-dev}"

log() {
  printf '[vanrides-dev-ops] %s\n' "$*"
}

die() {
  printf '[vanrides-dev-ops] ERROR: %s\n' "$*" >&2
  exit 1
}

require_root() {
  if [[ "$(id -u)" != "0" ]]; then
    die "execute via sudo/root; this script needs Docker and backup permissions"
  fi
}

require_docker() {
  command -v docker >/dev/null 2>&1 || die "docker is not installed or not in PATH"
  docker info >/dev/null 2>&1 || die "cannot access Docker daemon"
}

assert_dev_guardrails() {
  [[ "$VANRIDES_DEV_STACK" == "vanrides-dev" ]] || die "refusing non-dev stack: $VANRIDES_DEV_STACK"
  [[ "$VANRIDES_DEV_API_SERVICE" == "vanrides-dev_api" ]] || die "refusing non-dev api service: $VANRIDES_DEV_API_SERVICE"
  [[ "$VANRIDES_DEV_DB_SERVICE" == "vanrides-dev_db" ]] || die "refusing non-dev db service: $VANRIDES_DEV_DB_SERVICE"
  [[ "$VANRIDES_DEV_BASE_URL" == https://api-dev.vanrides.matheusduarte.dev.br* ]] || die "refusing non-dev base URL: $VANRIDES_DEV_BASE_URL"
}

confirm_exact_arg() {
  local expected="$1"
  shift
  local found="0"
  while (($#)); do
    if [[ "$1" == "--confirm" && "${2:-}" == "$expected" ]]; then
      found="1"
      break
    fi
    shift
  done
  [[ "$found" == "1" ]] || die "missing confirmation: --confirm $expected"
}

running_container_for_service() {
  local service="$1"
  docker ps \
    --filter "label=com.docker.swarm.service.name=$service" \
    --filter "status=running" \
    --format '{{.ID}}' \
    | head -n 1
}

require_running_container_for_service() {
  local service="$1"
  local container_id
  container_id="$(running_container_for_service "$service")"
  [[ -n "$container_id" ]] || die "no running container found for service $service"
  printf '%s\n' "$container_id"
}

wait_for_service_container() {
  local service="$1"
  local timeout_seconds="${2:-120}"
  local waited="0"
  while (( waited < timeout_seconds )); do
    if [[ -n "$(running_container_for_service "$service")" ]]; then
      return 0
    fi
    sleep 2
    waited=$((waited + 2))
  done
  die "service $service did not get a running container within ${timeout_seconds}s"
}

http_status() {
  local method="$1"
  local url="$2"
  shift 2
  curl -k -sS -o /dev/null -w '%{http_code}' -X "$method" "$url" "$@"
}
