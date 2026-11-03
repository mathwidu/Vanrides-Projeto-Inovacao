#!/bin/sh
set -eu
export AUTHENTICATION_API_KEY="$(cat /run/secrets/evo_key)"
db_password="$(cat /run/secrets/pg_pass)"
redis_password="$(cat /run/secrets/redis_pass)"
export DATABASE_CONNECTION_URI="postgresql://vanrides:${db_password}@db:5432/evolution?schema=public"
export CACHE_REDIS_URI="redis://:${redis_password}@cache:6379/0"
exec /bin/bash -c '. ./Docker/scripts/deploy_database.sh && exec npm run start:prod'
