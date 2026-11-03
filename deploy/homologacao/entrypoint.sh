#!/bin/sh
set -eu
fail() { printf '%s\n' 'HML_RUNTIME_CONFIGURATION_INVALID' >&2; exit 1; }
[ "${SPRING_PROFILES_ACTIVE:-}" = homolog ] || fail
[ "${WA_TEAM_API_ONLY:-}" = true ] || fail
[ "${VANRIDES_EMAIL_PROVIDER:-}" = disabled ] || fail
[ "${WA_ENVIRONMENT:-}" = vanrides-team-hml ] || fail
case "${VANRIDES_JAR_SHA256:-}" in *[!a-f0-9]*|'') fail ;; esac
[ "${#VANRIDES_JAR_SHA256}" -eq 64 ] || fail
# A bind or accidental image substitution must fail before starting Java.
printf '%s  /app/app.jar\n' "$VANRIDES_JAR_SHA256" | sha256sum -c - >/dev/null 2>&1 || fail
file_env() {
    var="$1"
    file_var="${var}_FILE"
    direct="$(printenv "$var" 2>/dev/null || true)"
    file="$(printenv "$file_var" 2>/dev/null || true)"
    [ -z "$direct" ] || [ -z "$file" ] || fail
    if [ -n "$file" ]; then
        [ -f "$file" ] && [ ! -L "$file" ] && [ -r "$file" ] || fail
        value="$(cat "$file")"
        [ -n "$value" ] || fail
        case "$value" in *"
"*|*"$(printf '\r')"*) fail ;; esac
        export "$var=$value"
        unset "$file_var"
    fi
}
file_env SPRING_DATASOURCE_PASSWORD
file_env VANRIDES_JWT_SECRET
file_env WA_SECRET_HASH_KEY
file_env WA_RESPONSE_ENCRYPTION_KEY
file_env WA_OPERATOR_CREDENTIAL
# Trusted deployment JVM options are deliberately split; operator args stay exact.
exec java ${JAVA_OPTS:-} -jar /app/app.jar "$@"
