CREATE FUNCTION vr_login_email_canonical(raw_email TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
    SELECT lower(btrim(raw_email, ' ') COLLATE "C")
$$;

CREATE FUNCTION vr_login_email_is_valid(raw_email TEXT)
RETURNS BOOLEAN
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
    SELECT
        octet_length(canonical) BETWEEN 3 AND 254
        AND (canonical COLLATE "C") ~ '^[!-~]+$'
        AND length(canonical) - length(replace(canonical, '@', '')) = 1
        AND position('@' IN canonical) > 1
        AND position('@' IN canonical) < length(canonical)
    FROM (
        SELECT vr_login_email_canonical(raw_email) AS canonical
    ) normalized
$$;

CREATE FUNCTION vr_normalize_login_email(raw_email TEXT)
RETURNS TEXT
LANGUAGE plpgsql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
DECLARE
    canonical TEXT := vr_login_email_canonical(raw_email);
BEGIN
    IF NOT vr_login_email_is_valid(raw_email) THEN
        RAISE EXCEPTION USING
            ERRCODE = '22023',
            MESSAGE = 'invalid login email format',
            CONSTRAINT = 'ck_login_email_format';
    END IF;

    RETURN canonical;
END
$$;

CREATE FUNCTION vr_login_identity_role_canonical(raw_role TEXT)
RETURNS TEXT
LANGUAGE sql
IMMUTABLE
STRICT
PARALLEL SAFE
AS $$
    -- The legacy alias boundary is intentionally finite. Portuguese aliases
    -- accept NFC or NFD accents and ASCII boundary whitespace; every unknown
    -- spelling still returns NULL and therefore remains fail-closed.
    SELECT CASE upper(
        replace(
            replace(
                translate(
                    regexp_replace(
                        regexp_replace(
                            raw_role COLLATE "C",
                            '^[[:space:]]+',
                            ''
                        ),
                        '[[:space:]]+$',
                        ''
                    ),
                    'ÁÀÂÃÄÉÈÊËÍÌÎÏÓÒÔÕÖÚÙÛÜÇáàâãäéèêëíìîïóòôõöúùûüç'
                        || U&'\0300\0301\0302\0303\0308\0327',
                    'AAAAAEEEEIIIIOOOOOUUUUCaaaaaeeeeiiiiooooouuuuc'
                ),
                '-',
                '_'
            ),
            ' ',
            '_'
        )
    )
        WHEN 'COMPANY_ADMIN' THEN 'COMPANY_ADMIN'
        WHEN 'ADMIN' THEN 'COMPANY_ADMIN'
        WHEN 'EMPRESA_ADMIN' THEN 'COMPANY_ADMIN'
        WHEN 'PASSENGER' THEN 'PASSENGER'
        WHEN 'PASSAGEIRO' THEN 'PASSENGER'
        WHEN 'GUARDIAN' THEN 'GUARDIAN'
        WHEN 'RESPONSAVEL' THEN 'GUARDIAN'
        WHEN 'DRIVER' THEN 'DRIVER'
        WHEN 'MOTORISTA' THEN 'DRIVER'
        ELSE NULL
    END
$$;

-- Keep readers available during the preflight and backfill, while preventing
-- any account writer from crossing the V24/V25 boundary. Later ALTER TABLE
-- statements upgrade these locks only after the potentially long scans.
LOCK TABLE company_admin, guardian, motorista, passageiro
    IN SHARE ROW EXCLUSIVE MODE;

DO $$
DECLARE
    invalid_count BIGINT;
    noncanonical_count BIGINT;
    intra_role_collision_groups BIGINT;
    cross_role_collision_groups BIGINT;
    invalid_password_hashes BIGINT;
BEGIN
    WITH account_emails(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    ),
    classified AS (
        SELECT
            user_role,
            user_id,
            email,
            vr_login_email_canonical(email) AS canonical,
            COALESCE(vr_login_email_is_valid(email), FALSE) AS valid
        FROM account_emails
    )
    SELECT
        count(*) FILTER (WHERE NOT valid),
        count(*) FILTER (
            WHERE valid
              AND (email COLLATE "C") IS DISTINCT FROM (canonical COLLATE "C")
        )
    INTO invalid_count, noncanonical_count
    FROM classified;

    WITH account_emails(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    ),
    classified AS (
        SELECT
            user_role,
            vr_login_email_canonical(email) AS canonical,
            COALESCE(vr_login_email_is_valid(email), FALSE) AS valid
        FROM account_emails
    ),
    collision_groups AS (
        SELECT
            canonical,
            count(DISTINCT user_role) AS role_count
        FROM classified
        WHERE valid
        GROUP BY canonical
        HAVING count(*) > 1
    )
    SELECT
        count(*) FILTER (WHERE role_count = 1),
        count(*) FILTER (WHERE role_count > 1)
    INTO intra_role_collision_groups, cross_role_collision_groups
    FROM collision_groups;

    SELECT
        (
            SELECT count(*)
            FROM (
                SELECT senha FROM company_admin
                UNION ALL
                SELECT senha FROM passageiro
                UNION ALL
                SELECT senha FROM motorista
            ) required_credentials
            WHERE senha IS NULL
               OR senha !~ '^[$]2[aby][$]12[$][./A-Za-z0-9]{53}$'
        ) + (
            SELECT count(*)
            FROM guardian
            WHERE senha IS NOT NULL
              AND btrim(senha) <> ''
              AND senha !~ '^[$]2[aby][$]12[$][./A-Za-z0-9]{53}$'
        )
    INTO invalid_password_hashes;

    IF invalid_count > 0
            OR noncanonical_count > 0
            OR intra_role_collision_groups > 0
            OR cross_role_collision_groups > 0
            OR invalid_password_hashes > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = format(
                'V25 login email preflight failed: invalid=%s noncanonical=%s intra_role_collision_groups=%s cross_role_collision_groups=%s invalid_password_hashes=%s',
                invalid_count,
                noncanonical_count,
                intra_role_collision_groups,
                cross_role_collision_groups,
                invalid_password_hashes
            ),
            CONSTRAINT = 'ck_v25_login_email_preflight';
    END IF;
END
$$;

CREATE TABLE login_identity (
    normalized_email VARCHAR(254) COLLATE "C" NOT NULL,
    user_role VARCHAR(32) COLLATE "C" NOT NULL,
    user_id BIGINT NOT NULL,
    created_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP(6) WITH TIME ZONE NOT NULL DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT pk_login_identity_email
        PRIMARY KEY (normalized_email),
    CONSTRAINT uk_login_identity_owner
        UNIQUE (user_role, user_id),
    CONSTRAINT ck_login_identity_role
        CHECK (user_role IN ('COMPANY_ADMIN', 'PASSENGER', 'GUARDIAN', 'DRIVER')),
    CONSTRAINT ck_login_identity_user_id_positive
        CHECK (user_id > 0),
    CONSTRAINT ck_login_identity_email_canonical
        CHECK (
            (normalized_email COLLATE "C") =
            (vr_normalize_login_email(normalized_email) COLLATE "C")
        )
);

WITH account_emails(user_role, user_id, email) AS (
    SELECT 'COMPANY_ADMIN', id, email FROM company_admin
    UNION ALL
    SELECT 'PASSENGER', id, email FROM passageiro
    UNION ALL
    SELECT 'GUARDIAN', id, email FROM guardian
    UNION ALL
    SELECT 'DRIVER', id, email FROM motorista
)
INSERT INTO login_identity (
    normalized_email,
    user_role,
    user_id,
    created_at,
    updated_at
)
SELECT
    vr_normalize_login_email(email),
    user_role,
    user_id,
    CURRENT_TIMESTAMP,
    CURRENT_TIMESTAMP
FROM account_emails
ORDER BY email, user_role, user_id;

DO $$
DECLARE
    source_count BIGINT;
    identity_count BIGINT;
    missing_identity_count BIGINT;
    orphan_identity_count BIGINT;
BEGIN
    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*) INTO source_count FROM accounts;

    SELECT count(*) INTO identity_count FROM login_identity;

    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*)
    INTO missing_identity_count
    FROM accounts source_account
    LEFT JOIN login_identity registry
        ON (registry.normalized_email COLLATE "C") =
           (source_account.email COLLATE "C")
        AND (registry.user_role COLLATE "C") =
            (source_account.user_role COLLATE "C")
        AND registry.user_id = source_account.user_id
    WHERE registry.normalized_email IS NULL;

    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*)
    INTO orphan_identity_count
    FROM login_identity registry
    LEFT JOIN accounts source_account
        ON (source_account.email COLLATE "C") =
           (registry.normalized_email COLLATE "C")
        AND (source_account.user_role COLLATE "C") =
            (registry.user_role COLLATE "C")
        AND source_account.user_id = registry.user_id
    WHERE source_account.user_id IS NULL;

    IF source_count <> identity_count
            OR missing_identity_count > 0
            OR orphan_identity_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = format(
                'V25 login identity backfill failed: sources=%s identities=%s missing=%s orphan=%s',
                source_count,
                identity_count,
                missing_identity_count,
                orphan_identity_count
            ),
            CONSTRAINT = 'ck_login_identity_backfill_consistency';
    END IF;
END
$$;

-- Close the reconciliation-to-trigger-install window. The lock is held until
-- the migration commits, so legacy registrations either finish before this
-- scan or resume only after the active-owner guard is visible.
LOCK TABLE device_token IN SHARE ROW EXCLUSIVE MODE;

-- Preserve only historical active devices that still belong to the exact
-- current owner. Known role aliases and email representation are canonicalized;
-- stale, malformed or orphaned devices fail closed before the guard is enabled.
UPDATE device_token device
SET user_role = vr_login_identity_role_canonical(device.user_role),
    user_email = registry.normalized_email,
    updated_at = CURRENT_TIMESTAMP
FROM login_identity registry
WHERE device.active = TRUE
  AND vr_login_identity_role_canonical(device.user_role) IS NOT NULL
  AND (registry.user_role COLLATE "C") =
      (vr_login_identity_role_canonical(device.user_role) COLLATE "C")
  AND registry.user_id = device.user_id
  AND COALESCE(vr_login_email_is_valid(device.user_email), FALSE)
  AND (registry.normalized_email COLLATE "C") =
      (vr_login_email_canonical(device.user_email) COLLATE "C")
  AND (
      (device.user_role COLLATE "C") IS DISTINCT FROM
          (vr_login_identity_role_canonical(device.user_role) COLLATE "C")
      OR (device.user_email COLLATE "C") IS DISTINCT FROM
         (registry.normalized_email COLLATE "C")
  );

UPDATE device_token device
SET active = FALSE,
    invalidated_at = COALESCE(device.invalidated_at, CURRENT_TIMESTAMP),
    updated_at = CURRENT_TIMESTAMP
WHERE device.active = TRUE
  AND NOT EXISTS (
      SELECT 1
      FROM login_identity registry
      WHERE vr_login_identity_role_canonical(device.user_role) IS NOT NULL
        AND (registry.user_role COLLATE "C") =
            (vr_login_identity_role_canonical(device.user_role) COLLATE "C")
        AND registry.user_id = device.user_id
        AND COALESCE(vr_login_email_is_valid(device.user_email), FALSE)
        AND (registry.normalized_email COLLATE "C") =
            (vr_login_email_canonical(device.user_email) COLLATE "C")
  );

CREATE FUNCTION vr_invalidate_login_artifacts(
    account_role TEXT,
    account_id BIGINT,
    replacement_email TEXT
)
RETURNS VOID
LANGUAGE plpgsql
AS $$
BEGIN
    UPDATE refresh_token
    SET revoked = TRUE,
        updated_at = CURRENT_TIMESTAMP
    WHERE (vr_login_identity_role_canonical(user_role) COLLATE "C") =
          (account_role COLLATE "C")
      AND user_id = account_id
      AND revoked = FALSE;

    UPDATE email_verification_token
    SET consumed_at = CURRENT_TIMESTAMP
    WHERE (vr_login_identity_role_canonical(user_role) COLLATE "C") =
          (account_role COLLATE "C")
      AND user_id = account_id
      AND consumed_at IS NULL;

    UPDATE device_token
    SET active = FALSE,
        invalidated_at = COALESCE(invalidated_at, CURRENT_TIMESTAMP),
        updated_at = CURRENT_TIMESTAMP,
        user_email = replacement_email
    WHERE (vr_login_identity_role_canonical(user_role) COLLATE "C") =
          (account_role COLLATE "C")
      AND user_id = account_id
      AND (
          active = TRUE
          OR (user_email COLLATE "C") IS DISTINCT FROM
             (replacement_email COLLATE "C")
      );
END
$$;

CREATE FUNCTION vr_guard_active_device_token_owner()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    canonical_role TEXT;
    canonical_email TEXT;
    locked_owner_email TEXT;
BEGIN
    IF NEW.active IS NOT TRUE THEN
        RETURN NEW;
    END IF;

    canonical_role := vr_login_identity_role_canonical(NEW.user_role);
    IF canonical_role IS NULL
            OR NOT COALESCE(vr_login_email_is_valid(NEW.user_email), FALSE) THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'active device token owner mismatch',
            CONSTRAINT = 'ck_device_token_login_identity_owner',
            TABLE = 'device_token';
    END IF;

    canonical_email := vr_login_email_canonical(NEW.user_email);

    SELECT registry.normalized_email
    INTO locked_owner_email
    FROM login_identity registry
    WHERE (registry.normalized_email COLLATE "C") =
          (canonical_email COLLATE "C")
      AND (registry.user_role COLLATE "C") = (canonical_role COLLATE "C")
      AND registry.user_id = NEW.user_id
    FOR KEY SHARE;

    IF NOT FOUND OR (locked_owner_email COLLATE "C") IS DISTINCT FROM
            (canonical_email COLLATE "C") THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'active device token owner mismatch',
            CONSTRAINT = 'ck_device_token_login_identity_owner',
            TABLE = 'device_token';
    END IF;

    NEW.user_role := canonical_role;
    NEW.user_email := locked_owner_email;
    RETURN NEW;
END
$$;

CREATE FUNCTION vr_prepare_account_login_email()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    canonical TEXT;
BEGIN
    canonical := vr_normalize_login_email(NEW.email);

    IF TG_OP = 'UPDATE' THEN
        IF (canonical COLLATE "C") IS DISTINCT FROM (OLD.email COLLATE "C") THEN
            NEW.email_verified := FALSE;
            NEW.email_verified_at := NULL;
            NEW.email_verification_last_sent_at := NULL;
        END IF;
    END IF;

    NEW.email := canonical;
    RETURN NEW;
END
$$;

CREATE FUNCTION vr_sync_login_identity()
RETURNS TRIGGER
LANGUAGE plpgsql
AS $$
DECLARE
    account_role TEXT := TG_ARGV[0];
    expected_role TEXT;
    affected_rows INTEGER;
BEGIN
    expected_role := CASE TG_TABLE_NAME
        WHEN 'company_admin' THEN 'COMPANY_ADMIN'
        WHEN 'passageiro' THEN 'PASSENGER'
        WHEN 'guardian' THEN 'GUARDIAN'
        WHEN 'motorista' THEN 'DRIVER'
        ELSE NULL
    END;

    IF expected_role IS NULL
            OR (account_role COLLATE "C") IS DISTINCT FROM
               (expected_role COLLATE "C") THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = 'login identity trigger role mismatch',
            CONSTRAINT = 'ck_login_identity_trigger_role',
            TABLE = TG_TABLE_NAME;
    END IF;

    IF TG_OP = 'INSERT' THEN
        INSERT INTO login_identity (
            normalized_email,
            user_role,
            user_id,
            created_at,
            updated_at
        ) VALUES (
            NEW.email,
            account_role,
            NEW.id,
            CURRENT_TIMESTAMP,
            CURRENT_TIMESTAMP
        );
        RETURN NULL;
    END IF;

    IF TG_OP = 'UPDATE' THEN
        IF (NEW.email COLLATE "C") IS NOT DISTINCT FROM
                (OLD.email COLLATE "C") THEN
            PERFORM 1
            FROM login_identity
            WHERE (normalized_email COLLATE "C") = (NEW.email COLLATE "C")
              AND (user_role COLLATE "C") = (account_role COLLATE "C")
              AND user_id = NEW.id;

            IF NOT FOUND THEN
                RAISE EXCEPTION USING
                    ERRCODE = '23514',
                    MESSAGE = 'login identity owner consistency failure',
                    CONSTRAINT = 'ck_login_identity_owner_consistency',
                    TABLE = TG_TABLE_NAME;
            END IF;

            RETURN NULL;
        END IF;

        UPDATE login_identity
        SET normalized_email = NEW.email,
            updated_at = CURRENT_TIMESTAMP
        WHERE (normalized_email COLLATE "C") = (OLD.email COLLATE "C")
          AND (user_role COLLATE "C") = (account_role COLLATE "C")
          AND user_id = NEW.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        IF affected_rows <> 1 THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'login identity owner consistency failure',
                CONSTRAINT = 'ck_login_identity_owner_consistency',
                TABLE = TG_TABLE_NAME;
        END IF;

        PERFORM vr_invalidate_login_artifacts(
            account_role,
            NEW.id,
            NEW.email
        );
        RETURN NULL;
    END IF;

    IF TG_OP = 'DELETE' THEN
        DELETE FROM login_identity
        WHERE (normalized_email COLLATE "C") = (OLD.email COLLATE "C")
          AND (user_role COLLATE "C") = (account_role COLLATE "C")
          AND user_id = OLD.id;

        GET DIAGNOSTICS affected_rows = ROW_COUNT;
        IF affected_rows <> 1 THEN
            RAISE EXCEPTION USING
                ERRCODE = '23514',
                MESSAGE = 'login identity owner consistency failure',
                CONSTRAINT = 'ck_login_identity_owner_consistency',
                TABLE = TG_TABLE_NAME;
        END IF;

        PERFORM vr_invalidate_login_artifacts(
            account_role,
            OLD.id,
            NULL
        );
        RETURN NULL;
    END IF;

    RAISE EXCEPTION USING
        ERRCODE = '0A000',
        MESSAGE = 'unsupported login identity trigger operation';
END
$$;

ALTER TABLE company_admin
    ADD CONSTRAINT ck_company_admin_email_canonical
        CHECK (
            (email COLLATE "C") = (vr_normalize_login_email(email) COLLATE "C")
        );

ALTER TABLE passageiro
    ADD CONSTRAINT ck_passageiro_email_canonical
        CHECK (
            (email COLLATE "C") = (vr_normalize_login_email(email) COLLATE "C")
        );

ALTER TABLE guardian
    ADD CONSTRAINT ck_guardian_email_canonical
        CHECK (
            (email COLLATE "C") = (vr_normalize_login_email(email) COLLATE "C")
        );

ALTER TABLE motorista
    ADD CONSTRAINT ck_motorista_email_canonical
        CHECK (
            (email COLLATE "C") = (vr_normalize_login_email(email) COLLATE "C")
        );

CREATE TRIGGER trg_company_admin_login_email_prepare
BEFORE INSERT OR UPDATE ON company_admin
FOR EACH ROW
EXECUTE FUNCTION vr_prepare_account_login_email();

CREATE TRIGGER trg_company_admin_login_identity_sync
AFTER INSERT OR UPDATE OR DELETE ON company_admin
FOR EACH ROW
EXECUTE FUNCTION vr_sync_login_identity('COMPANY_ADMIN');

CREATE TRIGGER trg_passageiro_login_email_prepare
BEFORE INSERT OR UPDATE ON passageiro
FOR EACH ROW
EXECUTE FUNCTION vr_prepare_account_login_email();

CREATE TRIGGER trg_passageiro_login_identity_sync
AFTER INSERT OR UPDATE OR DELETE ON passageiro
FOR EACH ROW
EXECUTE FUNCTION vr_sync_login_identity('PASSENGER');

CREATE TRIGGER trg_guardian_login_email_prepare
BEFORE INSERT OR UPDATE ON guardian
FOR EACH ROW
EXECUTE FUNCTION vr_prepare_account_login_email();

CREATE TRIGGER trg_guardian_login_identity_sync
AFTER INSERT OR UPDATE OR DELETE ON guardian
FOR EACH ROW
EXECUTE FUNCTION vr_sync_login_identity('GUARDIAN');

CREATE TRIGGER trg_motorista_login_email_prepare
BEFORE INSERT OR UPDATE ON motorista
FOR EACH ROW
EXECUTE FUNCTION vr_prepare_account_login_email();

CREATE TRIGGER trg_motorista_login_identity_sync
AFTER INSERT OR UPDATE OR DELETE ON motorista
FOR EACH ROW
EXECUTE FUNCTION vr_sync_login_identity('DRIVER');

CREATE TRIGGER trg_device_token_owner_guard
BEFORE INSERT OR UPDATE ON device_token
FOR EACH ROW
EXECUTE FUNCTION vr_guard_active_device_token_owner();

DO $$
DECLARE
    source_count BIGINT;
    identity_count BIGINT;
    missing_identity_count BIGINT;
    orphan_identity_count BIGINT;
    installed_trigger_count BIGINT;
    installed_device_guard_count BIGINT;
    active_device_owner_mismatch_count BIGINT;
BEGIN
    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*) INTO source_count FROM accounts;

    SELECT count(*) INTO identity_count FROM login_identity;

    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*)
    INTO missing_identity_count
    FROM accounts source_account
    LEFT JOIN login_identity registry
        ON (registry.normalized_email COLLATE "C") =
           (source_account.email COLLATE "C")
        AND (registry.user_role COLLATE "C") =
            (source_account.user_role COLLATE "C")
        AND registry.user_id = source_account.user_id
    WHERE registry.normalized_email IS NULL;

    WITH accounts(user_role, user_id, email) AS (
        SELECT 'COMPANY_ADMIN', id, email FROM company_admin
        UNION ALL
        SELECT 'PASSENGER', id, email FROM passageiro
        UNION ALL
        SELECT 'GUARDIAN', id, email FROM guardian
        UNION ALL
        SELECT 'DRIVER', id, email FROM motorista
    )
    SELECT count(*)
    INTO orphan_identity_count
    FROM login_identity registry
    LEFT JOIN accounts source_account
        ON (source_account.email COLLATE "C") =
           (registry.normalized_email COLLATE "C")
        AND (source_account.user_role COLLATE "C") =
            (registry.user_role COLLATE "C")
        AND source_account.user_id = registry.user_id
    WHERE source_account.user_id IS NULL;

    SELECT count(*)
    INTO installed_trigger_count
    FROM pg_trigger
    WHERE NOT tgisinternal
      AND (
          (tgrelid = 'company_admin'::regclass AND tgname IN (
              'trg_company_admin_login_email_prepare',
              'trg_company_admin_login_identity_sync'
          ))
          OR (tgrelid = 'passageiro'::regclass AND tgname IN (
              'trg_passageiro_login_email_prepare',
              'trg_passageiro_login_identity_sync'
          ))
          OR (tgrelid = 'guardian'::regclass AND tgname IN (
              'trg_guardian_login_email_prepare',
              'trg_guardian_login_identity_sync'
          ))
          OR (tgrelid = 'motorista'::regclass AND tgname IN (
              'trg_motorista_login_email_prepare',
              'trg_motorista_login_identity_sync'
          ))
      );

    SELECT count(*)
    INTO installed_device_guard_count
    FROM pg_trigger
    WHERE NOT tgisinternal
      AND tgrelid = 'device_token'::regclass
      AND tgname = 'trg_device_token_owner_guard';

    SELECT count(*)
    INTO active_device_owner_mismatch_count
    FROM device_token device
    LEFT JOIN login_identity registry
        ON (registry.user_role COLLATE "C") = (device.user_role COLLATE "C")
        AND registry.user_id = device.user_id
        AND (registry.normalized_email COLLATE "C") =
            (device.user_email COLLATE "C")
    WHERE device.active = TRUE
      AND registry.normalized_email IS NULL;

    IF source_count <> identity_count
            OR missing_identity_count > 0
            OR orphan_identity_count > 0
            OR installed_trigger_count <> 8
            OR installed_device_guard_count <> 1
            OR active_device_owner_mismatch_count > 0 THEN
        RAISE EXCEPTION USING
            ERRCODE = '23514',
            MESSAGE = format(
                'V25 login identity validation failed: sources=%s identities=%s missing=%s orphan=%s account_triggers=%s device_guards=%s active_device_owner_mismatches=%s',
                source_count,
                identity_count,
                missing_identity_count,
                orphan_identity_count,
                installed_trigger_count,
                installed_device_guard_count,
                active_device_owner_mismatch_count
            ),
            CONSTRAINT = 'ck_login_identity_final_consistency';
    END IF;
END
$$;
