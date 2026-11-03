-- VR-WA-040: an additive bounded context. Every reference is schema-qualified;
-- public objects, their search_path and existing accounts remain unchanged.
CREATE SCHEMA wa;

CREATE TABLE wa.test_cohort (
    id UUID PRIMARY KEY,
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    synthetic BOOLEAN NOT NULL DEFAULT FALSE,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_cohort_nonproduction CHECK (lower(environment) NOT IN ('prod', 'production'))
);

CREATE TABLE wa.identity (
    id UUID PRIMARY KEY,
    test_cohort_id UUID REFERENCES wa.test_cohort (id),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);

CREATE TABLE wa.identity_alias (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    provider TEXT NOT NULL CHECK (btrim(provider) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    alias_type TEXT NOT NULL CHECK (alias_type IN ('PN', 'LID', 'JID')),
    alias_value TEXT NOT NULL CHECK (btrim(alias_value) <> ''),
    verified_phone TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_identity_alias UNIQUE (provider, instance_id, alias_type, alias_value)
);
CREATE INDEX ix_identity_alias_identity ON wa.identity_alias (identity_id);

CREATE TABLE wa.profile (
    identity_id UUID PRIMARY KEY REFERENCES wa.identity (id),
    role TEXT NOT NULL CHECK (role IN ('DRIVER', 'PASSENGER')),
    status TEXT NOT NULL DEFAULT 'ONBOARDING'
        CHECK (status IN ('ONBOARDING', 'ACTIVE', 'SUSPENDED', 'CLOSED')),
    display_name TEXT CHECK (display_name IS NULL OR btrim(display_name) <> ''),
    notice_version TEXT,
    notice_accepted_at TIMESTAMPTZ,
    driver_enabled BOOLEAN NOT NULL DEFAULT FALSE,
    preferences_json JSONB NOT NULL DEFAULT '{}'::jsonb
        CHECK (jsonb_typeof(preferences_json) = 'object'),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_profile_role UNIQUE (identity_id, role),
    CONSTRAINT ck_profile_driver_enabled CHECK (NOT driver_enabled OR role = 'DRIVER'),
    CONSTRAINT ck_profile_notice CHECK (
        (notice_version IS NULL) = (notice_accepted_at IS NULL)
        AND (notice_version IS NULL OR btrim(notice_version) <> '')
    )
);

CREATE TABLE wa.service_credential (
    id UUID PRIMARY KEY,
    principal TEXT NOT NULL CHECK (principal IN (
        'CHANNEL', 'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB', 'OPERATOR'
    )),
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    token_hash VARCHAR(64) NOT NULL CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    allowed_instance_id TEXT CHECK (allowed_instance_id IS NULL OR btrim(allowed_instance_id) <> ''),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_service_credential_hash UNIQUE (token_hash),
    CONSTRAINT uk_service_credential_principal UNIQUE (id, principal),
    CONSTRAINT ck_service_credential_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_channel_instance CHECK (principal <> 'CHANNEL' OR allowed_instance_id IS NOT NULL)
);

CREATE TABLE wa.driver_bootstrap_invite (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    code_hash VARCHAR(64) NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by UUID NOT NULL,
    created_by_principal TEXT NOT NULL DEFAULT 'OPERATOR' CHECK (created_by_principal = 'OPERATOR'),
    CONSTRAINT fk_bootstrap_operator FOREIGN KEY (created_by, created_by_principal)
        REFERENCES wa.service_credential (id, principal),
    CONSTRAINT ck_bootstrap_expiry CHECK (expires_at > created_at)
);
CREATE INDEX ix_bootstrap_identity ON wa.driver_bootstrap_invite (identity_id);

CREATE TABLE wa.integration_inbox (
    id UUID PRIMARY KEY,
    identity_id UUID REFERENCES wa.identity (id),
    provider TEXT NOT NULL CHECK (btrim(provider) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    message_id TEXT,
    event_key TEXT,
    category TEXT NOT NULL CHECK (category IN ('MESSAGE', 'STATUS', 'CONNECTION', 'ECHO')),
    normalized_kind TEXT NOT NULL DEFAULT 'UNSUPPORTED'
        CHECK (normalized_kind IN ('TEXT', 'PIN', 'UNSUPPORTED')),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload_json) = 'object'),
    state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'CLAIMED', 'COMPLETED', 'RETRY', 'SKIPPED', 'EXPIRED')),
    lease_credential_id UUID REFERENCES wa.service_credential (id),
    lease_token_hash VARCHAR(64) CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    lease_generation BIGINT NOT NULL DEFAULT 0 CHECK (lease_generation >= 0),
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_inbox_identity UNIQUE (id, identity_id),
    CONSTRAINT ck_inbox_dedup_key CHECK (
        (category = 'MESSAGE' AND message_id IS NOT NULL AND btrim(message_id) <> '')
        OR (category <> 'MESSAGE' AND event_key IS NOT NULL AND btrim(event_key) <> '')
    ),
    CONSTRAINT ck_inbox_lease CHECK (
        (lease_credential_id IS NULL AND lease_token_hash IS NULL AND lease_expires_at IS NULL)
        OR (lease_credential_id IS NOT NULL AND lease_token_hash IS NOT NULL
            AND lease_expires_at IS NOT NULL AND lease_generation > 0)
    ),
    CONSTRAINT ck_inbox_claim CHECK (state <> 'CLAIMED' OR (
        identity_id IS NOT NULL AND lease_credential_id IS NOT NULL
        AND lease_token_hash IS NOT NULL AND lease_expires_at IS NOT NULL AND lease_generation > 0
    ))
);
CREATE UNIQUE INDEX uk_inbox_message ON wa.integration_inbox (instance_id, message_id)
    WHERE category = 'MESSAGE';
CREATE UNIQUE INDEX uk_inbox_callback ON wa.integration_inbox (provider, instance_id, category, event_key)
    WHERE category <> 'MESSAGE';
CREATE UNIQUE INDEX uk_inbox_claimed_identity ON wa.integration_inbox (identity_id)
    WHERE state = 'CLAIMED';
CREATE INDEX ix_inbox_pending ON wa.integration_inbox (state, created_at);

CREATE TABLE wa.command_receipt (
    command_id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    inbox_event_id UUID NOT NULL,
    idempotency_key TEXT NOT NULL CHECK (btrim(idempotency_key) <> ''),
    command_type TEXT NOT NULL CHECK (btrim(command_type) <> ''),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    result_json JSONB NOT NULL CHECK (jsonb_typeof(result_json) = 'object'),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_command_actor_key UNIQUE (identity_id, idempotency_key),
    CONSTRAINT uk_command_inbox_event UNIQUE (inbox_event_id),
    CONSTRAINT fk_command_inbox_actor FOREIGN KEY (inbox_event_id, identity_id)
        REFERENCES wa.integration_inbox (id, identity_id),
    CONSTRAINT ck_command_expiry CHECK (expires_at > created_at)
);

CREATE TABLE wa.driver_authorization (
    driver_id UUID PRIMARY KEY,
    driver_role TEXT NOT NULL DEFAULT 'DRIVER' CHECK (driver_role = 'DRIVER'),
    generation BIGINT NOT NULL DEFAULT 0 CHECK (generation >= 0),
    CONSTRAINT fk_authorization_driver FOREIGN KEY (driver_id, driver_role)
        REFERENCES wa.profile (identity_id, role)
);

CREATE TABLE wa.driver_device (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization (driver_id),
    jkt VARCHAR(43) NOT NULL CHECK (jkt ~ '^[A-Za-z0-9_-]{43}$'),
    public_jwk JSONB NOT NULL CHECK (jsonb_typeof(public_jwk) = 'object' AND NOT (public_jwk ? 'd')),
    generation BIGINT NOT NULL CHECK (generation > 0),
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    platform TEXT NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    app_version TEXT NOT NULL CHECK (btrim(app_version) <> ''),
    status TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (status IN ('ACTIVE', 'REVOKED')),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    revoked_at TIMESTAMPTZ,
    CONSTRAINT uk_device_owner UNIQUE (id, driver_id),
    CONSTRAINT uk_device_owner_key UNIQUE (id, driver_id, jkt, environment),
    CONSTRAINT ck_device_revocation CHECK ((status = 'REVOKED') = (revoked_at IS NOT NULL))
);
CREATE UNIQUE INDEX uk_device_active_driver ON wa.driver_device (driver_id) WHERE status = 'ACTIVE';
CREATE UNIQUE INDEX uk_device_active_jkt ON wa.driver_device (jkt) WHERE status = 'ACTIVE';

CREATE TABLE wa.pairing_challenge (
    id UUID PRIMARY KEY,
    public_jwk JSONB NOT NULL CHECK (jsonb_typeof(public_jwk) = 'object' AND NOT (public_jwk ? 'd')),
    jkt VARCHAR(43) NOT NULL CHECK (jkt ~ '^[A-Za-z0-9_-]{43}$'),
    pkce_challenge VARCHAR(43) NOT NULL CHECK (pkce_challenge ~ '^[A-Za-z0-9_-]{43}$'),
    platform TEXT NOT NULL CHECK (platform IN ('ANDROID', 'IOS')),
    app_version TEXT NOT NULL CHECK (btrim(app_version) <> ''),
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    code_hash VARCHAR(64) NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'APPROVED', 'REDEEMED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    driver_id UUID REFERENCES wa.driver_authorization (driver_id),
    approval_generation BIGINT CHECK (approval_generation >= 0),
    failed_attempts INTEGER NOT NULL DEFAULT 0 CHECK (failed_attempts BETWEEN 0 AND 5),
    device_id UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_pairing_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_pairing_approval CHECK (
        (driver_id IS NULL) = (approval_generation IS NULL)
        AND (state NOT IN ('APPROVED', 'REDEEMED') OR driver_id IS NOT NULL)
        AND (state <> 'PENDING' OR driver_id IS NULL)
    ),
    CONSTRAINT ck_pairing_redemption CHECK ((state = 'REDEEMED') = (device_id IS NOT NULL)),
    CONSTRAINT fk_pairing_device_owner FOREIGN KEY (device_id, driver_id, jkt, environment)
        REFERENCES wa.driver_device (id, driver_id, jkt, environment)
);
CREATE INDEX ix_pairing_driver ON wa.pairing_challenge (driver_id);
CREATE INDEX ix_pairing_key_expiry ON wa.pairing_challenge (jkt, expires_at);

CREATE TABLE wa.device_token (
    id UUID PRIMARY KEY,
    device_id UUID NOT NULL REFERENCES wa.driver_device (id),
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    kind TEXT NOT NULL CHECK (kind IN ('ACCESS', 'REFRESH')),
    family_id UUID NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    revoked_at TIMESTAMPTZ,
    rotated_to UUID,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_device_token_expiry CHECK (expires_at > created_at),
    CONSTRAINT uk_device_token_family UNIQUE (id, device_id, family_id, kind),
    CONSTRAINT fk_device_token_rotation_family FOREIGN KEY (rotated_to, device_id, family_id, kind)
        REFERENCES wa.device_token (id, device_id, family_id, kind),
    CONSTRAINT ck_device_token_rotation CHECK (
        rotated_to IS NULL OR (kind = 'REFRESH' AND consumed_at IS NOT NULL AND rotated_to <> id)
    )
);
CREATE INDEX ix_device_token_device ON wa.device_token (device_id, family_id);

CREATE TABLE wa.proof_nonce (
    nonce_hash VARCHAR(64) PRIMARY KEY CHECK (nonce_hash ~ '^[0-9a-f]{64}$'),
    jkt VARCHAR(43) NOT NULL CHECK (jkt ~ '^[A-Za-z0-9_-]{43}$'),
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT ck_proof_nonce_expiry CHECK (expires_at > created_at)
);

CREATE TABLE wa.proof_replay (
    jkt VARCHAR(43) NOT NULL CHECK (jkt ~ '^[A-Za-z0-9_-]{43}$'),
    jti TEXT NOT NULL CHECK (btrim(jti) <> ''),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY (jkt, jti)
);

CREATE INDEX ix_proof_nonce_key_expiry ON wa.proof_nonce (jkt, expires_at);

CREATE TABLE wa.operation_replay (
    id UUID PRIMARY KEY,
    operation_kind TEXT NOT NULL CHECK (operation_kind IN ('PAIR_BEGIN', 'PAIR_REDEEM', 'REFRESH', 'SESSION_REVOKE')),
    scope_id UUID NOT NULL,
    request_key TEXT NOT NULL CHECK (btrim(request_key) <> ''),
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    response_ciphertext BYTEA NOT NULL CHECK (octet_length(response_ciphertext) > 0),
    expires_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT uk_operation_replay UNIQUE (operation_kind, scope_id, request_key)
);

CREATE TABLE wa.actor_attempt_window (
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    action TEXT NOT NULL CHECK (btrim(action) <> ''),
    window_start TIMESTAMPTZ NOT NULL,
    attempts INTEGER NOT NULL CHECK (attempts BETWEEN 0 AND 5),
    PRIMARY KEY (identity_id, action, window_start)
);

CREATE TABLE wa.audit_event (
    id UUID PRIMARY KEY,
    actor_identity_id UUID REFERENCES wa.identity (id),
    credential_id UUID REFERENCES wa.service_credential (id),
    actor_kind TEXT NOT NULL CHECK (actor_kind IN (
        'UNREGISTERED', 'PASSENGER', 'DRIVER', 'DEVICE', 'CHANNEL',
        'CONVERSATION_WORKER', 'DELIVERY_WORKER', 'SYSTEM_JOB', 'OPERATOR'
    )),
    action TEXT NOT NULL CHECK (btrim(action) <> ''),
    resource_id UUID,
    correlation_id VARCHAR(128) NOT NULL CHECK (btrim(correlation_id) <> ''),
    reason_code TEXT,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP
);
CREATE INDEX ix_audit_actor_time ON wa.audit_event (actor_identity_id, created_at);

CREATE TABLE wa.driver_invite (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization (driver_id),
    code_hash VARCHAR(64) NOT NULL UNIQUE CHECK (code_hash ~ '^[0-9a-f]{64}$'),
    expires_at TIMESTAMPTZ NOT NULL,
    revoked_at TIMESTAMPTZ,
    max_uses INTEGER NOT NULL CHECK (max_uses > 0),
    use_count INTEGER NOT NULL DEFAULT 0 CHECK (use_count >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uk_driver_invite_owner UNIQUE (id, driver_id),
    CONSTRAINT ck_driver_invite_uses CHECK (use_count <= max_uses),
    CONSTRAINT ck_driver_invite_expiry CHECK (expires_at > created_at)
);

CREATE TABLE wa.driver_passenger_link (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization (driver_id),
    passenger_id UUID NOT NULL,
    passenger_role TEXT NOT NULL DEFAULT 'PASSENGER' CHECK (passenger_role = 'PASSENGER'),
    state TEXT NOT NULL DEFAULT 'REQUESTED'
        CHECK (state IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CLOSED')),
    invite_id UUID,
    approved_by UUID REFERENCES wa.identity (id),
    approved_at TIMESTAMPTZ,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_link_passenger FOREIGN KEY (passenger_id, passenger_role)
        REFERENCES wa.profile (identity_id, role),
    CONSTRAINT fk_link_invite_owner FOREIGN KEY (invite_id, driver_id)
        REFERENCES wa.driver_invite (id, driver_id),
    CONSTRAINT uk_link_owners UNIQUE (id, driver_id, passenger_id),
    CONSTRAINT ck_link_distinct_owners CHECK (driver_id <> passenger_id),
    CONSTRAINT ck_link_approval CHECK (
        (approved_by IS NULL) = (approved_at IS NULL)
        AND (approved_by IS NULL OR approved_by = driver_id)
        AND (state <> 'APPROVED' OR approved_by IS NOT NULL)
    ),
    CONSTRAINT ck_link_validity CHECK (valid_until IS NULL OR valid_until >= valid_from),
    CONSTRAINT ck_link_closed CHECK (state <> 'CLOSED' OR valid_until IS NOT NULL)
);
CREATE UNIQUE INDEX uk_link_active_passenger ON wa.driver_passenger_link (passenger_id)
    WHERE state = 'APPROVED';
CREATE UNIQUE INDEX uk_link_requested_pair ON wa.driver_passenger_link (driver_id, passenger_id)
    WHERE state = 'REQUESTED';

CREATE TABLE wa.pickup_point (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    label TEXT,
    reference_text TEXT,
    source_inbox_event_id UUID NOT NULL,
    state TEXT NOT NULL DEFAULT 'PROPOSED'
        CHECK (state IN ('PROPOSED', 'APPROVED', 'REJECTED', 'RETIRED')),
    approved_by UUID REFERENCES wa.identity (id),
    approved_at TIMESTAMPTZ,
    valid_from TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    valid_until TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_pickup_link_owners FOREIGN KEY (link_id, driver_id, passenger_id)
        REFERENCES wa.driver_passenger_link (id, driver_id, passenger_id),
    CONSTRAINT fk_pickup_pin_actor FOREIGN KEY (source_inbox_event_id, passenger_id)
        REFERENCES wa.integration_inbox (id, identity_id),
    CONSTRAINT uk_pickup_owners UNIQUE (id, driver_id, passenger_id),
    CONSTRAINT ck_pickup_approval CHECK (
        (approved_by IS NULL) = (approved_at IS NULL)
        AND (approved_by IS NULL OR approved_by = driver_id)
        AND (state <> 'APPROVED' OR approved_by IS NOT NULL)
    ),
    CONSTRAINT ck_pickup_validity CHECK (valid_until IS NULL OR valid_until >= valid_from)
);
CREATE UNIQUE INDEX uk_pickup_current_approved ON wa.pickup_point (passenger_id)
    WHERE state = 'APPROVED' AND valid_until IS NULL;

CREATE TABLE wa.routine (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization (driver_id),
    name TEXT NOT NULL CHECK (btrim(name) <> ''),
    direction TEXT NOT NULL CHECK (direction IN ('OUTBOUND', 'RETURN')),
    weekdays SMALLINT[] NOT NULL CHECK (
        cardinality(weekdays) BETWEEN 1 AND 7
        AND weekdays <@ ARRAY[1, 2, 3, 4, 5, 6, 7]::SMALLINT[]
        AND array_position(weekdays, NULL) IS NULL
    ),
    local_time TIME NOT NULL,
    timezone TEXT NOT NULL CHECK (btrim(timezone) <> ''),
    destination_label TEXT,
    destination_latitude DOUBLE PRECISION CHECK (destination_latitude BETWEEN -90 AND 90),
    destination_longitude DOUBLE PRECISION CHECK (destination_longitude BETWEEN -180 AND 180),
    state TEXT NOT NULL DEFAULT 'DRAFT' CHECK (state IN ('DRAFT', 'ACTIVE', 'PAUSED', 'CLOSED')),
    attendance_policy TEXT NOT NULL DEFAULT 'INCLUDE_BY_DEFAULT'
        CHECK (attendance_policy IN ('INCLUDE_BY_DEFAULT', 'EXPLICIT_GOING')),
    policy_version TEXT NOT NULL CHECK (btrim(policy_version) <> ''),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_routine_owner UNIQUE (id, driver_id),
    CONSTRAINT ck_routine_destination CHECK (
        (destination_latitude IS NULL) = (destination_longitude IS NULL)
        AND (state <> 'ACTIVE' OR destination_latitude IS NOT NULL)
    )
);

CREATE TABLE wa.routine_member (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    link_id UUID NOT NULL,
    pickup_point_id UUID NOT NULL,
    stop_order INTEGER NOT NULL CHECK (stop_order > 0),
    state TEXT NOT NULL DEFAULT 'ACTIVE' CHECK (state IN ('ACTIVE', 'REMOVED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_routine_member UNIQUE (routine_id, passenger_id),
    CONSTRAINT fk_member_routine_owner FOREIGN KEY (routine_id, driver_id)
        REFERENCES wa.routine (id, driver_id),
    CONSTRAINT fk_member_link_owners FOREIGN KEY (link_id, driver_id, passenger_id)
        REFERENCES wa.driver_passenger_link (id, driver_id, passenger_id),
    CONSTRAINT fk_member_pickup_owners FOREIGN KEY (pickup_point_id, driver_id, passenger_id)
        REFERENCES wa.pickup_point (id, driver_id, passenger_id)
);

CREATE TABLE wa.routine_occurrence (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    local_date DATE NOT NULL,
    scheduled_at TIMESTAMPTZ NOT NULL,
    state TEXT NOT NULL DEFAULT 'PLANNED' CHECK (state IN ('PLANNED', 'CANCELED', 'CLOSED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_occurrence_routine_date UNIQUE (routine_id, local_date),
    CONSTRAINT uk_occurrence_driver UNIQUE (id, driver_id),
    CONSTRAINT uk_occurrence_owners UNIQUE (id, routine_id, local_date, driver_id),
    CONSTRAINT fk_occurrence_routine_owner FOREIGN KEY (routine_id, driver_id)
        REFERENCES wa.routine (id, driver_id)
);

CREATE TABLE wa.attendance (
    id UUID PRIMARY KEY,
    routine_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    local_date DATE NOT NULL,
    state TEXT NOT NULL DEFAULT 'PENDING' CHECK (state IN ('PENDING', 'GOING', 'NOT_GOING')),
    actor_identity_id UUID REFERENCES wa.identity (id),
    credential_id UUID REFERENCES wa.service_credential (id),
    question_ref UUID,
    answered_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_attendance_occurrence_passenger UNIQUE (routine_id, passenger_id, local_date),
    CONSTRAINT fk_attendance_member FOREIGN KEY (routine_id, passenger_id)
        REFERENCES wa.routine_member (routine_id, passenger_id),
    CONSTRAINT fk_attendance_occurrence FOREIGN KEY (routine_id, local_date)
        REFERENCES wa.routine_occurrence (routine_id, local_date),
    CONSTRAINT ck_attendance_author CHECK (
        state = 'PENDING' OR (answered_at IS NOT NULL
            AND actor_identity_id IS NOT NULL AND actor_identity_id = passenger_id)
    )
);

CREATE TABLE wa.operation_run (
    id UUID PRIMARY KEY,
    occurrence_id UUID NOT NULL UNIQUE,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    local_date DATE NOT NULL,
    state TEXT NOT NULL DEFAULT 'PLANNED'
        CHECK (state IN ('PLANNED', 'STARTING', 'START_FAILED', 'TRACKING', 'DEGRADED', 'COMPLETED', 'CANCELED')),
    policy_version TEXT NOT NULL CHECK (btrim(policy_version) <> ''),
    snapshot_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(snapshot_json) = 'object'),
    snapshot_at TIMESTAMPTZ,
    started_at TIMESTAMPTZ,
    completed_at TIMESTAMPTZ,
    completion_reason TEXT,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_run_routine_date UNIQUE (routine_id, local_date),
    CONSTRAINT uk_run_owner UNIQUE (id, driver_id),
    CONSTRAINT uk_run_occurrence_owner UNIQUE (id, occurrence_id, driver_id),
    CONSTRAINT fk_run_occurrence_owner FOREIGN KEY (occurrence_id, routine_id, local_date, driver_id)
        REFERENCES wa.routine_occurrence (id, routine_id, local_date, driver_id),
    CONSTRAINT ck_run_snapshot CHECK (state = 'PLANNED' OR state = 'CANCELED' OR snapshot_at IS NOT NULL)
);
CREATE UNIQUE INDEX uk_run_active_driver ON wa.operation_run (driver_id)
    WHERE state IN ('STARTING', 'START_FAILED', 'TRACKING', 'DEGRADED');

CREATE TABLE wa.run_stop (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    passenger_role TEXT NOT NULL DEFAULT 'PASSENGER' CHECK (passenger_role = 'PASSENGER'),
    physical_stop_key UUID NOT NULL,
    stop_order INTEGER NOT NULL CHECK (stop_order > 0),
    pickup_point_id UUID NOT NULL,
    pickup_version BIGINT NOT NULL CHECK (pickup_version >= 0),
    latitude_snapshot DOUBLE PRECISION NOT NULL CHECK (latitude_snapshot BETWEEN -90 AND 90),
    longitude_snapshot DOUBLE PRECISION NOT NULL CHECK (longitude_snapshot BETWEEN -180 AND 180),
    reference_snapshot TEXT,
    attendance_snapshot TEXT NOT NULL CHECK (attendance_snapshot IN ('PENDING', 'GOING', 'NOT_GOING')),
    state TEXT NOT NULL DEFAULT 'WAITING'
        CHECK (state IN ('WAITING', 'APPROACHING', 'PASSED', 'SKIPPED', 'CANCELED')),
    canceled_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_run_stop_passenger UNIQUE (run_id, passenger_id),
    CONSTRAINT uk_run_stop_owner UNIQUE (id, run_id, passenger_id),
    CONSTRAINT fk_stop_run_owner FOREIGN KEY (run_id, driver_id)
        REFERENCES wa.operation_run (id, driver_id),
    CONSTRAINT fk_stop_passenger FOREIGN KEY (passenger_id, passenger_role)
        REFERENCES wa.profile (identity_id, role),
    CONSTRAINT fk_stop_pickup_owners FOREIGN KEY (pickup_point_id, driver_id, passenger_id)
        REFERENCES wa.pickup_point (id, driver_id, passenger_id)
);

CREATE TABLE wa.start_intent (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization (driver_id),
    occurrence_id UUID NOT NULL,
    device_id UUID,
    run_id UUID,
    token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (token_hash ~ '^[0-9a-f]{64}$'),
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'ACTIVATED', 'REVOKED', 'EXPIRED')),
    expires_at TIMESTAMPTZ NOT NULL,
    consumed_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_start_device_owner FOREIGN KEY (device_id, driver_id)
        REFERENCES wa.driver_device (id, driver_id),
    CONSTRAINT fk_start_occurrence_owner FOREIGN KEY (occurrence_id, driver_id)
        REFERENCES wa.routine_occurrence (id, driver_id),
    CONSTRAINT fk_start_run_owner FOREIGN KEY (run_id, occurrence_id, driver_id)
        REFERENCES wa.operation_run (id, occurrence_id, driver_id),
    CONSTRAINT ck_start_intent_expiry CHECK (expires_at > created_at),
    CONSTRAINT ck_start_intent_activation CHECK (
        state <> 'ACTIVATED' OR (device_id IS NOT NULL AND run_id IS NOT NULL AND consumed_at IS NOT NULL)
    )
);

CREATE TABLE wa.tracking_session (
    id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    device_id UUID NOT NULL,
    generation BIGINT NOT NULL CHECK (generation > 0),
    state TEXT NOT NULL DEFAULT 'STARTING'
        CHECK (state IN ('STARTING', 'ACTIVE', 'DEGRADED', 'STOP_REQUESTED', 'STOPPED', 'EXPIRED', 'REVOKED')),
    lease_expires_at TIMESTAMPTZ NOT NULL,
    lease_token_hash VARCHAR(64) CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    last_sequence BIGINT NOT NULL DEFAULT -1 CHECK (last_sequence >= -1),
    last_captured_at TIMESTAMPTZ,
    last_received_at TIMESTAMPTZ,
    last_latitude DOUBLE PRECISION CHECK (last_latitude BETWEEN -90 AND 90),
    last_longitude DOUBLE PRECISION CHECK (last_longitude BETWEEN -180 AND 180),
    last_accuracy_m DOUBLE PRECISION CHECK (last_accuracy_m >= 0 AND last_accuracy_m < 'Infinity'::DOUBLE PRECISION),
    stop_requested_at TIMESTAMPTZ,
    stopped_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT uk_tracking_run_generation UNIQUE (run_id, generation),
    CONSTRAINT uk_tracking_session_generation UNIQUE (id, generation),
    CONSTRAINT fk_tracking_run_owner FOREIGN KEY (run_id, driver_id)
        REFERENCES wa.operation_run (id, driver_id),
    CONSTRAINT fk_tracking_device_owner FOREIGN KEY (device_id, driver_id)
        REFERENCES wa.driver_device (id, driver_id),
    CONSTRAINT ck_tracking_position CHECK (
        (last_latitude IS NULL AND last_longitude IS NULL AND last_captured_at IS NULL
            AND last_accuracy_m IS NULL)
        OR (last_latitude IS NOT NULL AND last_longitude IS NOT NULL
            AND last_captured_at IS NOT NULL AND last_accuracy_m IS NOT NULL)
    ),
    CONSTRAINT ck_tracking_stop CHECK (state <> 'STOPPED' OR stopped_at IS NOT NULL)
);
CREATE UNIQUE INDEX uk_tracking_active_run ON wa.tracking_session (run_id)
    WHERE state IN ('STARTING', 'ACTIVE', 'DEGRADED', 'STOP_REQUESTED');
CREATE UNIQUE INDEX uk_tracking_active_device ON wa.tracking_session (device_id)
    WHERE state IN ('STARTING', 'ACTIVE', 'DEGRADED', 'STOP_REQUESTED');

-- Receipts intentionally carry no coordinates. A batch ACK and each sequence
-- survive response loss without treating the receipt time as a new GPS fix.
CREATE TABLE wa.tracking_batch_receipt (
    session_id UUID NOT NULL,
    generation BIGINT NOT NULL,
    batch_id UUID NOT NULL,
    request_hash VARCHAR(64) NOT NULL CHECK (request_hash ~ '^[0-9a-f]{64}$'),
    result_json JSONB NOT NULL CHECK (jsonb_typeof(result_json) = 'object'),
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (session_id, batch_id),
    CONSTRAINT fk_batch_session_generation FOREIGN KEY (session_id, generation)
        REFERENCES wa.tracking_session (id, generation)
);

CREATE TABLE wa.tracking_sample_receipt (
    id UUID PRIMARY KEY,
    session_id UUID NOT NULL,
    generation BIGINT NOT NULL,
    batch_id UUID NOT NULL,
    sequence BIGINT NOT NULL CHECK (sequence >= 0),
    captured_at TIMESTAMPTZ NOT NULL,
    received_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    outcome TEXT NOT NULL CHECK (outcome IN ('ACCEPTED', 'REJECTED')),
    rejection_code TEXT,
    CONSTRAINT uk_tracking_sample_sequence UNIQUE (session_id, sequence),
    CONSTRAINT fk_sample_session_generation FOREIGN KEY (session_id, generation)
        REFERENCES wa.tracking_session (id, generation),
    CONSTRAINT fk_sample_batch FOREIGN KEY (session_id, batch_id)
        REFERENCES wa.tracking_batch_receipt (session_id, batch_id),
    CONSTRAINT ck_sample_rejection CHECK ((outcome = 'REJECTED') = (rejection_code IS NOT NULL))
);

CREATE TABLE wa.conversation_state (
    identity_id UUID PRIMARY KEY REFERENCES wa.identity (id),
    stage TEXT NOT NULL DEFAULT 'NEW' CHECK (btrim(stage) <> ''),
    question_ref UUID,
    question_expires_at TIMESTAMPTZ,
    context_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(context_json) = 'object'),
    last_event_id UUID,
    paused_until TIMESTAMPTZ,
    lease_credential_id UUID REFERENCES wa.service_credential (id),
    lease_token_hash VARCHAR(64) CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    lease_generation BIGINT NOT NULL DEFAULT 0 CHECK (lease_generation >= 0),
    lease_expires_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    updated_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_conversation_event_actor FOREIGN KEY (last_event_id, identity_id)
        REFERENCES wa.integration_inbox (id, identity_id),
    CONSTRAINT ck_conversation_lease CHECK (
        (lease_credential_id IS NULL AND lease_token_hash IS NULL AND lease_expires_at IS NULL)
        OR (lease_credential_id IS NOT NULL AND lease_token_hash IS NOT NULL
            AND lease_expires_at IS NOT NULL AND lease_generation > 0)
    )
);

CREATE TABLE wa.message_outbox (
    id UUID PRIMARY KEY,
    recipient_identity_id UUID NOT NULL REFERENCES wa.identity (id),
    purpose TEXT NOT NULL CHECK (purpose IN ('APPROACH', 'REMINDER', 'RESPONSE', 'PIN', 'OPERATIONAL', 'SUPPORT')),
    run_id UUID REFERENCES wa.operation_run (id),
    stop_id UUID,
    occurrence_id UUID REFERENCES wa.routine_occurrence (id),
    command_id UUID REFERENCES wa.command_receipt (command_id),
    response_part TEXT,
    reminder_window TEXT,
    business_key TEXT,
    policy_version TEXT NOT NULL CHECK (btrim(policy_version) <> ''),
    payload_kind TEXT NOT NULL DEFAULT 'TEXT' CHECK (payload_kind IN ('TEXT', 'PIN')),
    payload_json JSONB NOT NULL DEFAULT '{}'::jsonb CHECK (jsonb_typeof(payload_json) = 'object'),
    position_captured_at TIMESTAMPTZ,
    state TEXT NOT NULL DEFAULT 'PENDING'
        CHECK (state IN ('PENDING', 'LEASED', 'SUBMITTED', 'DELIVERED', 'READ', 'UNKNOWN', 'FAILED', 'EXPIRED', 'CANCELED')),
    not_before TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    expires_at TIMESTAMPTZ NOT NULL,
    lease_credential_id UUID REFERENCES wa.service_credential (id),
    lease_token_hash VARCHAR(64) CHECK (lease_token_hash ~ '^[0-9a-f]{64}$'),
    lease_generation BIGINT NOT NULL DEFAULT 0 CHECK (lease_generation >= 0),
    lease_expires_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT fk_outbox_stop_recipient FOREIGN KEY (stop_id, run_id, recipient_identity_id)
        REFERENCES wa.run_stop (id, run_id, passenger_id),
    CONSTRAINT ck_outbox_business_key CHECK (
        (purpose = 'APPROACH' AND run_id IS NOT NULL AND stop_id IS NOT NULL)
        OR (purpose = 'REMINDER' AND occurrence_id IS NOT NULL
            AND reminder_window IS NOT NULL AND btrim(reminder_window) <> '')
        OR (purpose IN ('RESPONSE', 'PIN') AND command_id IS NOT NULL
            AND response_part IS NOT NULL AND btrim(response_part) <> '')
        OR (purpose IN ('OPERATIONAL', 'SUPPORT') AND business_key IS NOT NULL AND btrim(business_key) <> '')
    ),
    CONSTRAINT ck_outbox_expiry CHECK (expires_at > created_at AND expires_at > not_before),
    CONSTRAINT ck_outbox_pin_deadline CHECK (payload_kind <> 'PIN' OR (
        position_captured_at IS NOT NULL AND expires_at <= position_captured_at + INTERVAL '20 seconds'
    )),
    CONSTRAINT ck_outbox_pin_purge CHECK (
        payload_kind <> 'PIN' OR state NOT IN ('SUBMITTED', 'DELIVERED', 'READ', 'EXPIRED', 'CANCELED')
        OR payload_json = '{}'::jsonb
    ),
    CONSTRAINT ck_outbox_lease CHECK (
        (lease_credential_id IS NULL AND lease_token_hash IS NULL AND lease_expires_at IS NULL)
        OR (lease_credential_id IS NOT NULL AND lease_token_hash IS NOT NULL
            AND lease_expires_at IS NOT NULL AND lease_generation > 0)
    ),
    CONSTRAINT ck_outbox_claim CHECK (state <> 'LEASED' OR (
        lease_credential_id IS NOT NULL AND lease_token_hash IS NOT NULL
        AND lease_expires_at IS NOT NULL AND lease_generation > 0
    ))
);
CREATE UNIQUE INDEX uk_outbox_approach ON wa.message_outbox (run_id, stop_id, recipient_identity_id)
    WHERE purpose = 'APPROACH';
CREATE UNIQUE INDEX uk_outbox_reminder ON wa.message_outbox (occurrence_id, recipient_identity_id, reminder_window)
    WHERE purpose = 'REMINDER';
CREATE UNIQUE INDEX uk_outbox_response ON wa.message_outbox (command_id, recipient_identity_id, response_part)
    WHERE purpose IN ('RESPONSE', 'PIN');
CREATE UNIQUE INDEX uk_outbox_operational ON wa.message_outbox (recipient_identity_id, purpose, business_key)
    WHERE purpose IN ('OPERATIONAL', 'SUPPORT');
CREATE INDEX ix_outbox_due ON wa.message_outbox (state, not_before, expires_at);

CREATE TABLE wa.message_attempt (
    id UUID PRIMARY KEY,
    outbox_id UUID NOT NULL REFERENCES wa.message_outbox (id),
    attempt_number INTEGER NOT NULL CHECK (attempt_number BETWEEN 1 AND 3),
    lease_generation BIGINT NOT NULL CHECK (lease_generation > 0),
    submit_token_hash VARCHAR(64) NOT NULL UNIQUE CHECK (submit_token_hash ~ '^[0-9a-f]{64}$'),
    state TEXT NOT NULL DEFAULT 'SUBMITTING'
        CHECK (state IN ('SUBMITTING', 'SUBMITTED', 'DELIVERED', 'READ', 'UNKNOWN', 'FAILED', 'EXPIRED')),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    external_message_id TEXT,
    error_code TEXT,
    started_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    completed_at TIMESTAMPTZ,
    CONSTRAINT uk_message_attempt_number UNIQUE (outbox_id, attempt_number)
);
CREATE UNIQUE INDEX uk_message_attempt_external ON wa.message_attempt (instance_id, external_message_id)
    WHERE external_message_id IS NOT NULL;
CREATE UNIQUE INDEX uk_message_attempt_inflight ON wa.message_attempt (outbox_id) WHERE state = 'SUBMITTING';

CREATE TABLE wa.support_case (
    id UUID PRIMARY KEY,
    identity_id UUID NOT NULL REFERENCES wa.identity (id),
    deduplication_key TEXT NOT NULL CHECK (btrim(deduplication_key) <> ''),
    reason_code TEXT NOT NULL CHECK (btrim(reason_code) <> ''),
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK (state IN ('OPEN', 'IN_PROGRESS', 'CLOSED')),
    assigned_credential_id UUID REFERENCES wa.service_credential (id),
    access_expires_at TIMESTAMPTZ,
    correlation_id VARCHAR(128) NOT NULL CHECK (btrim(correlation_id) <> ''),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    closed_at TIMESTAMPTZ,
    version BIGINT NOT NULL DEFAULT 0 CHECK (version >= 0),
    CONSTRAINT uk_support_deduplication UNIQUE (identity_id, deduplication_key),
    CONSTRAINT ck_support_closed CHECK ((state = 'CLOSED') = (closed_at IS NOT NULL))
);

CREATE FUNCTION wa.guard_pairing_binding()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.public_jwk, NEW.jkt, NEW.pkce_challenge, NEW.platform,
           NEW.app_version, NEW.environment, NEW.code_hash, NEW.expires_at, NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.public_jwk, OLD.jkt, OLD.pkce_challenge, OLD.platform,
           OLD.app_version, OLD.environment, OLD.code_hash, OLD.expires_at, OLD.created_at) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'pairing request binding is immutable', CONSTRAINT = 'ck_pairing_immutable_binding';
    END IF;
    IF OLD.driver_id IS NOT NULL AND ROW(NEW.driver_id, NEW.approval_generation)
            IS DISTINCT FROM ROW(OLD.driver_id, OLD.approval_generation) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'pairing approval binding is immutable', CONSTRAINT = 'ck_pairing_immutable_approval';
    END IF;
    IF OLD.state IN ('REDEEMED', 'REVOKED', 'EXPIRED') AND NEW.state <> OLD.state THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'terminal pairing cannot reopen', CONSTRAINT = 'ck_pairing_terminal';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_pairing_binding BEFORE UPDATE ON wa.pairing_challenge
    FOR EACH ROW EXECUTE FUNCTION wa.guard_pairing_binding();

CREATE FUNCTION wa.guard_run_binding()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.occurrence_id, NEW.routine_id, NEW.driver_id, NEW.local_date)
       IS DISTINCT FROM ROW(OLD.id, OLD.occurrence_id, OLD.routine_id, OLD.driver_id, OLD.local_date) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'run ownership is immutable', CONSTRAINT = 'ck_run_immutable_owner';
    END IF;
    IF OLD.snapshot_at IS NOT NULL AND ROW(NEW.snapshot_json, NEW.snapshot_at, NEW.policy_version)
            IS DISTINCT FROM ROW(OLD.snapshot_json, OLD.snapshot_at, OLD.policy_version) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'run snapshot is immutable', CONSTRAINT = 'ck_run_immutable_snapshot';
    END IF;
    IF OLD.state IN ('COMPLETED', 'CANCELED') AND NEW.state <> OLD.state THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'terminal run cannot reopen', CONSTRAINT = 'ck_run_terminal';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_run_binding BEFORE UPDATE ON wa.operation_run
    FOR EACH ROW EXECUTE FUNCTION wa.guard_run_binding();

CREATE FUNCTION wa.guard_stop_snapshot()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id, NEW.run_id, NEW.driver_id, NEW.passenger_id, NEW.physical_stop_key,
           NEW.stop_order, NEW.pickup_point_id, NEW.pickup_version,
           NEW.latitude_snapshot, NEW.longitude_snapshot, NEW.reference_snapshot, NEW.attendance_snapshot)
       IS DISTINCT FROM
       ROW(OLD.id, OLD.run_id, OLD.driver_id, OLD.passenger_id, OLD.physical_stop_key,
           OLD.stop_order, OLD.pickup_point_id, OLD.pickup_version,
           OLD.latitude_snapshot, OLD.longitude_snapshot, OLD.reference_snapshot, OLD.attendance_snapshot) THEN
        RAISE EXCEPTION USING ERRCODE = '23514',
            MESSAGE = 'run stop snapshot is immutable', CONSTRAINT = 'ck_stop_immutable_snapshot';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_stop_snapshot BEFORE UPDATE ON wa.run_stop
    FOR EACH ROW EXECUTE FUNCTION wa.guard_stop_snapshot();
