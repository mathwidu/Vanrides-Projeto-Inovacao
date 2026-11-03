#!/bin/sh
# Fixed unauthenticated route only; print neither response nor request material.
set -eu
response=$(wget -q -S -O /dev/null --timeout=3 \
  --header='Host: gps-spike.vanrides.matheusduarte.dev.br' \
  http://127.0.0.1:8080/driver-app/v1/status 2>&1 || :)
case "$response" in
  *'HTTP/1.1 401 Unauthorized'*) exit 0 ;;
  *) exit 1 ;;
esac
