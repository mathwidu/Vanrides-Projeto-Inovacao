#!/usr/bin/env bash

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd -P)"
APP_DIR="$(cd "$SCRIPT_DIR/.." && pwd -P)"
DART_TEST="tool/qa_trip_live_location_real_backend_test.dart"

fail() {
  printf 'TRIP_LIVE_LOCATION_REAL_BACKEND_FAIL reason=%s\n' "$1" >&2
  exit 2
}

require_command() {
  command -v "$1" >/dev/null 2>&1 || fail "missing_command_$1"
}

require_env() {
  local name="$1"
  [[ -n "${!name:-}" ]] || fail "missing_env_$name"
}

require_positive_id() {
  local name="$1"
  local value="${!name:-}"
  [[ "$value" =~ ^[1-9][0-9]*$ ]] || fail "invalid_env_$name"
}

git_app() {
  command git \
    --no-replace-objects \
    -c core.fsmonitor=false \
    -c core.untrackedCache=false \
    -C "$APP_DIR" \
    "$@"
}

assert_no_hidden_git_index_flags() {
  local record
  local tag
  while IFS= read -r record; do
    tag="${record:0:1}"
    if [[ "$tag" == "S" || "$tag" =~ [a-z] ]]; then
      fail "app_index_has_assume_unchanged_or_skip_worktree"
    fi
  done < <(git_app ls-files -v -- :/)
}

validate_loopback_base_url() {
  python3 - "$1" <<'PY'
import ipaddress
import sys
from urllib.parse import urlsplit

raw = sys.argv[1]
if not raw or raw != raw.strip():
    raise SystemExit(1)

try:
    parsed = urlsplit(raw)
    address = ipaddress.ip_address(parsed.hostname or "")
    port = parsed.port
except (ValueError, TypeError):
    raise SystemExit(1)

valid = (
    parsed.scheme == "http"
    and address.is_loopback
    and port is not None
    and parsed.username is None
    and parsed.password is None
    and parsed.path in ("", "/")
    and not parsed.query
    and not parsed.fragment
)
raise SystemExit(0 if valid else 1)
PY
}

while IFS= read -r name; do
  [[ "$name" == GIT_* ]] || continue
  unset "$name"
done < <(compgen -A variable GIT_)

require_command git
require_command python3
require_command flutter
require_env VANRIDES_EXPECTED_APP_SHA
require_env VANRIDES_API_BASE_URL
require_env VANRIDES_CONSUMER_PHASE

EXPECTED_APP_SHA="$VANRIDES_EXPECTED_APP_SHA"
API_BASE_URL="$VANRIDES_API_BASE_URL"
PHASE="$VANRIDES_CONSUMER_PHASE"

[[ "$EXPECTED_APP_SHA" =~ ^[0-9a-f]{40}$ ]] || \
  fail "invalid_env_VANRIDES_EXPECTED_APP_SHA"

[[ "$(git_app rev-parse --is-inside-work-tree 2>/dev/null || true)" == "true" ]] || \
  fail "app_not_git_worktree"
GIT_ROOT="$(git_app rev-parse --show-toplevel)"
GIT_ROOT="$(cd "$GIT_ROOT" && pwd -P)"
[[ "$GIT_ROOT" == "$APP_DIR" || "$GIT_ROOT/src/aplicativo-motorista" == "$APP_DIR" ]] || \
  fail "unexpected_app_worktree_root"

ACTUAL_APP_SHA="$(git_app rev-parse --verify HEAD^{commit})"
[[ "$ACTUAL_APP_SHA" == "$EXPECTED_APP_SHA" ]] || fail "app_sha_mismatch"
assert_no_hidden_git_index_flags
[[ -z "$(git_app status --porcelain --untracked-files=all)" ]] || \
  fail "app_worktree_not_clean"

git_app ls-files --error-unmatch "$DART_TEST" >/dev/null 2>&1 || \
  fail "consumer_test_not_tracked"

validate_loopback_base_url "$API_BASE_URL" || \
  fail "VANRIDES_API_BASE_URL_must_be_explicit_http_loopback_with_port"
API_BASE_URL="${API_BASE_URL%/}"

case "$PHASE" in
  positive)
    for name in \
      VANRIDES_CONSUMER_OK_EMAIL \
      VANRIDES_CONSUMER_OK_PASSWORD \
      VANRIDES_CONSUMER_OK_DRIVER_ID \
      VANRIDES_CONSUMER_EMPTY_EMAIL \
      VANRIDES_CONSUMER_EMPTY_PASSWORD \
      VANRIDES_CONSUMER_EMPTY_DRIVER_ID; do
      require_env "$name"
    done
    require_positive_id VANRIDES_CONSUMER_OK_DRIVER_ID
    require_positive_id VANRIDES_CONSUMER_EMPTY_DRIVER_ID
    ;;
  negative)
    for name in \
      VANRIDES_CONSUMER_DENIED_EMAIL \
      VANRIDES_CONSUMER_DENIED_PASSWORD \
      VANRIDES_CONSUMER_DENIED_DRIVER_ID; do
      require_env "$name"
    done
    require_positive_id VANRIDES_CONSUMER_DENIED_DRIVER_ID
    ;;
  *)
    fail "invalid_env_VANRIDES_CONSUMER_PHASE"
    ;;
esac

cd "$APP_DIR"
flutter test "$DART_TEST" \
  --dart-define="VANRIDES_API_BASE_URL=$API_BASE_URL" \
  --dart-define="VANRIDES_CONSUMER_PHASE=$PHASE"

[[ "$(git_app rev-parse --verify HEAD^{commit})" == "$EXPECTED_APP_SHA" ]] || \
  fail "app_sha_changed_during_probe"
assert_no_hidden_git_index_flags
[[ -z "$(git_app status --porcelain --untracked-files=all)" ]] || \
  fail "app_worktree_changed_during_probe"

if [[ "$PHASE" == "positive" ]]; then
  printf 'TRIP_LIVE_LOCATION_REAL_BACKEND_PASS phase=positive http_200=PASS http_204=PASS app_sha=%s attestation_scope=consumer_only backend_binding=coordinator_required\n' \
    "$EXPECTED_APP_SHA"
else
  printf 'TRIP_LIVE_LOCATION_REAL_BACKEND_PASS phase=negative http_404=PASS generic_exception=PASS app_sha=%s attestation_scope=consumer_only backend_binding=coordinator_required\n' \
    "$EXPECTED_APP_SHA"
fi
