#!/bin/sh
set -eu
# NEW volume only. The API never receives the administrative secret.
# pg_read_file keeps the password out of argv and generated SQL files.
psql --no-psqlrc --set ON_ERROR_STOP=1 --username postgres --dbname postgres <<'SQL'
DO $body$
DECLARE
    app_password text := btrim(pg_read_file('/run/secrets/db_password'), E'\r\n');
BEGIN
    IF length(app_password) < 32 OR app_password ~ E'[\r\n]' THEN
        RAISE EXCEPTION 'HML_DATABASE_CONFIGURATION_INVALID';
    END IF;
    EXECUTE format('CREATE ROLE vanrides_hml_api LOGIN NOSUPERUSER NOCREATEDB NOCREATEROLE NOREPLICATION PASSWORD %L', app_password);
END $body$;
ALTER DATABASE vanrides_hml_api OWNER TO vanrides_hml_api;
REVOKE CONNECT ON DATABASE postgres FROM PUBLIC;
REVOKE CONNECT ON DATABASE template1 FROM PUBLIC;
SQL
