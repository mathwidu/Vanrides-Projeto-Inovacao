#!/bin/sh
set -eu

file_env() {
  var="$1"
  file_var="${var}_FILE"

  var_value="$(printenv "$var" 2>/dev/null || true)"
  file_value="$(printenv "$file_var" 2>/dev/null || true)"

  if [ -n "$var_value" ] && [ -n "$file_value" ]; then
    echo "error: both $var and $file_var are set (but are exclusive)" >&2
    exit 1
  fi

  if [ -n "$file_value" ]; then
    if [ ! -r "$file_value" ]; then
      echo "error: $file_var points to unreadable file: $file_value" >&2
      exit 1
    fi
    value="$(tr -d '\r\n' < "$file_value")"
    export "$var=$value"
    unset "$file_var"
  fi
}

# Permite usar Docker Swarm secrets sem expor valores em env vars
file_env SPRING_DATASOURCE_PASSWORD
file_env GOOGLE_API_KEY
file_env VANRIDES_JWT_SECRET
file_env VANRIDES_EMAIL_RESEND_API_KEY

# Dedicated WhatsApp secrets; unrelated services keep their existing values.
file_env WA_SECRET_HASH_KEY
file_env WA_RESPONSE_ENCRYPTION_KEY
file_env WA_OPERATOR_CREDENTIAL

exec java ${JAVA_OPTS:-} -jar /app/app.jar
