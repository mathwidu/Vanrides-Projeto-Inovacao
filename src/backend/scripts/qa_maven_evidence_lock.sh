#!/usr/bin/env bash

# Sourceable shell adapter for the kernel-backed Maven evidence lock.
# Loading this file only defines state/functions; it never acquires the lock.

_QA_MAVEN_EVIDENCE_LOCK_MODULE_DIR="$(
  cd "$(dirname "${BASH_SOURCE[0]}")" && pwd
)"
_QA_MAVEN_EVIDENCE_LOCK_STATE="idle"
_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN=""
_QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_TOKEN=""
_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID=""
_QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_KEEPER_PID=""
_QA_MAVEN_EVIDENCE_LOCK_OWNER_SHELL_PID=""
_QA_MAVEN_EVIDENCE_LOCK_CONTROL_FD=""

_qa_maven_evidence_lock_error() {
  printf 'FAIL: %s\n' "$*" >&2
  return 1
}

_qa_maven_evidence_lock_validate_value() {
  local label="$1"
  local value="$2"
  local expression="$3"

  if [[ ! "$value" =~ $expression ]]; then
    _qa_maven_evidence_lock_error "Invalid Maven evidence lock $label."
    return 1
  fi
}

_qa_maven_evidence_lock_stat_owner_mode() {
  local path="$1"

  case "$(uname -s)" in
    Darwin)
      stat -f '%u %Lp' "$path" 2>/dev/null
      ;;
    Linux)
      stat -c '%u %a' "$path" 2>/dev/null
      ;;
    *)
      return 1
      ;;
  esac
}

_qa_maven_evidence_lock_validate_root() {
  local root="$1"
  local metadata=""
  local expected_uid=""
  local actual_uid=""
  local actual_mode=""

  if [[ -z "$root" || "$root" != /* || "$root" == "/" \
      || "$root" == *$'\n'* || "$root" == *$'\r'* || "$root" == *$'\t'* \
      || "$root" == */../* || "$root" == */.. ]]; then
    _qa_maven_evidence_lock_error "Unsafe Maven evidence lock root."
    return 1
  fi
  if [[ -L "$root" || ! -d "$root" ]]; then
    _qa_maven_evidence_lock_error "Maven evidence lock root must be a real private directory."
    return 1
  fi
  metadata="$(_qa_maven_evidence_lock_stat_owner_mode "$root")" || {
    _qa_maven_evidence_lock_error "Cannot inspect Maven evidence lock root."
    return 1
  }
  read -r actual_uid actual_mode <<<"$metadata"
  expected_uid="$(id -u)"
  if [[ "$actual_uid" != "$expected_uid" || "$actual_mode" != "700" ]]; then
    _qa_maven_evidence_lock_error "Maven evidence lock root must belong to the current UID with mode 0700."
    return 1
  fi
}

_qa_maven_evidence_lock_default_root() {
  local common_dir=""
  local common_parent=""
  local common_name=""

  common_dir="$(git rev-parse --git-common-dir 2>/dev/null)" || return 1
  [[ -n "$common_dir" ]] || return 1
  if [[ "$common_dir" != /* ]]; then
    common_parent="$(dirname "$common_dir")"
    common_name="$(basename "$common_dir")"
    common_parent="$(cd "$common_parent" 2>/dev/null && pwd -P)" || return 1
    common_dir="$common_parent/$common_name"
  fi
  printf '%s/vanrides-qa-lock\n' "$common_dir"
}

_qa_maven_evidence_lock_prepare_root() {
  local root=""

  if [[ "${VANRIDES_QA_LOCK_ROOT+x}" == "x" ]]; then
    root="${VANRIDES_QA_LOCK_ROOT}"
    _qa_maven_evidence_lock_validate_root "$root" || return 1
  else
    root="$(_qa_maven_evidence_lock_default_root)" || {
      _qa_maven_evidence_lock_error "Cannot resolve git common dir for Maven evidence lock."
      return 1
    }
    if [[ ! -e "$root" && ! -L "$root" ]]; then
      if ! (umask 077 && mkdir "$root") 2>/dev/null; then
        [[ -d "$root" && ! -L "$root" ]] || {
          _qa_maven_evidence_lock_error "Cannot create private Maven evidence lock root."
          return 1
        }
      fi
    fi
    _qa_maven_evidence_lock_validate_root "$root" || return 1
  fi
  printf '%s\n' "$root"
}

_qa_maven_evidence_lock_random_token() {
  python3 -c 'import secrets; print(secrets.token_hex(16))'
}

_qa_maven_evidence_lock_cleanup_startup() {
  local control_path="$1"
  local status_path="$2"
  local keeper_pid="$3"
  local control_open="$4"

  if [[ "$control_open" == "1" ]]; then
    exec 9>&-
  fi
  if [[ -n "$keeper_pid" ]] && kill -0 "$keeper_pid" 2>/dev/null; then
    kill -TERM "$keeper_pid" 2>/dev/null || true
  fi
  if [[ -n "$keeper_pid" ]]; then
    wait "$keeper_pid" 2>/dev/null || true
  fi
  rm -f "$control_path" "$status_path"
}

_qa_maven_evidence_lock_reset_state() {
  _QA_MAVEN_EVIDENCE_LOCK_STATE="idle"
  _QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN=""
  _QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_TOKEN=""
  _QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID=""
  _QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_KEEPER_PID=""
  _QA_MAVEN_EVIDENCE_LOCK_OWNER_SHELL_PID=""
  _QA_MAVEN_EVIDENCE_LOCK_CONTROL_FD=""
}

qa_maven_evidence_lock_acquire() {
  local suite="${1:-}"
  local run_id="${2:-}"
  local git_sha="${3:-}"
  local root=""
  local token=""
  local control_path=""
  local status_path=""
  local keeper_pid=""
  local control_open=0
  local status_line=""
  local cycle=0
  local helper="$_QA_MAVEN_EVIDENCE_LOCK_MODULE_DIR/qa_maven_evidence_lock.py"

  if (( $# != 3 )); then
    _qa_maven_evidence_lock_error \
      "Usage: qa_maven_evidence_lock_acquire <suite> <run-id> <git-sha>."
    return 1
  fi
  if [[ "$_QA_MAVEN_EVIDENCE_LOCK_STATE" != "idle" ]]; then
    _qa_maven_evidence_lock_error "Maven evidence lock is already acquired by this shell."
    return 1
  fi
  _qa_maven_evidence_lock_validate_value \
    "suite" "$suite" '^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$' || return 1
  _qa_maven_evidence_lock_validate_value \
    "run-id" "$run_id" '^[A-Za-z0-9][A-Za-z0-9._-]{0,127}$' || return 1
  _qa_maven_evidence_lock_validate_value \
    "git SHA" "$git_sha" '^([0-9a-f]{40}|[0-9a-f]{64})$' || return 1
  if [[ ! -f "$helper" || -L "$helper" ]]; then
    _qa_maven_evidence_lock_error "Maven evidence lock keeper is unavailable."
    return 1
  fi
  root="$(_qa_maven_evidence_lock_prepare_root)" || return 1
  token="$(_qa_maven_evidence_lock_random_token)" || {
    _qa_maven_evidence_lock_error "Cannot generate Maven evidence lock owner token."
    return 1
  }
  if [[ ! "$token" =~ ^[0-9a-f]{32}$ ]]; then
    _qa_maven_evidence_lock_error "Invalid Maven evidence lock owner token."
    return 1
  fi

  control_path="$root/control.$$.$token.fifo"
  status_path="$root/status.$$.$token"
  if [[ -e "$control_path" || -L "$control_path" \
      || -e "$status_path" || -L "$status_path" ]]; then
    _qa_maven_evidence_lock_error "Maven evidence lock channel collision."
    return 1
  fi
  if ! (umask 077 && mkfifo "$control_path") 2>/dev/null; then
    _qa_maven_evidence_lock_error "Cannot create Maven evidence lock control channel."
    return 1
  fi
  chmod 0600 "$control_path" 2>/dev/null || {
    rm -f "$control_path"
    _qa_maven_evidence_lock_error "Cannot secure Maven evidence lock control channel."
    return 1
  }
  if (true >&9) 2>/dev/null; then
    rm -f "$control_path"
    _qa_maven_evidence_lock_error "File descriptor 9 is already in use; lock inheritance is ambiguous."
    return 1
  fi

  python3 "$helper" \
    --root "$root" \
    --control "$control_path" \
    --status "$status_path" \
    --owner-pid "$$" \
    --owner-token "$token" \
    --suite "$suite" \
    --run-id "$run_id" \
    --git-sha "$git_sha" \
    >/dev/null 2>&1 &
  keeper_pid=$!
  if ! exec 9<>"$control_path"; then
    _qa_maven_evidence_lock_cleanup_startup \
      "$control_path" "$status_path" "$keeper_pid" "$control_open"
    _qa_maven_evidence_lock_error "Cannot open Maven evidence lock control channel."
    return 1
  fi
  control_open=1

  while (( cycle < 50 )); do
    if [[ -s "$status_path" ]]; then
      IFS= read -r status_line < "$status_path" || status_line=""
      break
    fi
    if ! kill -0 "$keeper_pid" 2>/dev/null; then
      break
    fi
    sleep 0.1
    cycle=$((cycle + 1))
  done

  if [[ "$status_line" != "ACQUIRED $token" ]]; then
    _qa_maven_evidence_lock_cleanup_startup \
      "$control_path" "$status_path" "$keeper_pid" "$control_open"
    case "$status_line" in
      BLOCKED\ owner=unknown)
        _qa_maven_evidence_lock_error \
          "Maven evidence lock BLOCKED (owner=unknown)."
        ;;
      BLOCKED\ suite=*\ run_id=*\ pid=*)
        _qa_maven_evidence_lock_error \
          "Maven evidence lock BLOCKED (${status_line#BLOCKED })."
        ;;
      ERROR\ *)
        _qa_maven_evidence_lock_error \
          "Maven evidence lock keeper rejected acquisition (${status_line#ERROR })."
        ;;
      *)
        _qa_maven_evidence_lock_error \
          "Maven evidence lock keeper handshake failed or exceeded 5 seconds."
        ;;
    esac
    return 1
  fi

  rm -f "$control_path" "$status_path"
  _QA_MAVEN_EVIDENCE_LOCK_STATE="acquired"
  _QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN="$token"
  _QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_TOKEN="$token"
  _QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID="$keeper_pid"
  _QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_KEEPER_PID="$keeper_pid"
  _QA_MAVEN_EVIDENCE_LOCK_OWNER_SHELL_PID="$$"
  _QA_MAVEN_EVIDENCE_LOCK_CONTROL_FD="9"
  return 0
}

qa_maven_evidence_lock_release() {
  local keeper_status=0

  if [[ "$_QA_MAVEN_EVIDENCE_LOCK_STATE" != "acquired" ]]; then
    _qa_maven_evidence_lock_error "This shell does not own the Maven evidence lock."
    return 1
  fi
  if [[ -z "$_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN" \
      || "$_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN" \
        != "$_QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_TOKEN" ]]; then
    _qa_maven_evidence_lock_error \
      "Maven evidence lock release token does not match acquisition."
    return 1
  fi
  if [[ -z "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" \
      || "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" \
        != "$_QA_MAVEN_EVIDENCE_LOCK_ACQUIRED_KEEPER_PID" \
      || "$_QA_MAVEN_EVIDENCE_LOCK_OWNER_SHELL_PID" != "$$" \
      || "$_QA_MAVEN_EVIDENCE_LOCK_CONTROL_FD" != "9" ]]; then
    _qa_maven_evidence_lock_error \
      "Maven evidence lock keeper does not match acquisition."
    return 1
  fi
  if ! kill -0 "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" 2>/dev/null; then
    exec 9>&-
    wait "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" 2>/dev/null || true
    _qa_maven_evidence_lock_reset_state
    _qa_maven_evidence_lock_error "Maven evidence lock keeper exited before release."
    return 1
  fi
  if ! printf 'RELEASE %s\n' \
      "$_QA_MAVEN_EVIDENCE_LOCK_OWNER_TOKEN" >&9; then
    exec 9>&-
    wait "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" 2>/dev/null || true
    _qa_maven_evidence_lock_reset_state
    _qa_maven_evidence_lock_error "Cannot signal Maven evidence lock keeper."
    return 1
  fi
  exec 9>&-
  wait "$_QA_MAVEN_EVIDENCE_LOCK_KEEPER_PID" || keeper_status=$?
  _qa_maven_evidence_lock_reset_state
  if (( keeper_status != 0 )); then
    _qa_maven_evidence_lock_error \
      "Maven evidence lock keeper failed during release."
    return 1
  fi
  return 0
}
