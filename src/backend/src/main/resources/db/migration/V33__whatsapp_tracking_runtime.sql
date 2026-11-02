ALTER TABLE wa.start_intent ADD COLUMN intent_action TEXT NOT NULL DEFAULT 'START'
    CHECK (intent_action IN ('START','RESUME'));
ALTER TABLE wa.start_intent ADD COLUMN expected_version BIGINT NOT NULL DEFAULT 0 CHECK (expected_version>=0);
ALTER TABLE wa.start_intent ADD COLUMN roster_digest VARCHAR(64) CHECK (roster_digest ~ '^[0-9a-f]{64}$');
ALTER TABLE wa.start_intent ADD COLUMN request_command_id UUID REFERENCES wa.command_receipt(command_id);
CREATE UNIQUE INDEX uk_start_pending_driver ON wa.start_intent(driver_id) WHERE state='PENDING';

ALTER TABLE wa.operation_run ADD COLUMN start_failure_expires_at TIMESTAMPTZ;
ALTER TABLE wa.tracking_session ADD COLUMN origin_intent_id UUID REFERENCES wa.start_intent(id);
ALTER TABLE wa.tracking_session ADD COLUMN start_deadline TIMESTAMPTZ;
ALTER TABLE wa.tracking_session ADD COLUMN stop_request_ref UUID;
ALTER TABLE wa.tracking_session ADD COLUMN gps_state TEXT NOT NULL DEFAULT 'STALE'
    CHECK (gps_state IN ('FRESH','STALE','CLOCK_SKEW'));
ALTER TABLE wa.tracking_sample_receipt ADD COLUMN sample_hash VARCHAR(64)
    CHECK (sample_hash ~ '^[0-9a-f]{64}$');
CREATE INDEX ix_tracking_watchdog ON wa.tracking_session(state,lease_expires_at);

ALTER TABLE wa.operation_replay DROP CONSTRAINT operation_replay_operation_kind_check;
ALTER TABLE wa.operation_replay ADD CONSTRAINT operation_replay_operation_kind_check
    CHECK (operation_kind IN ('PAIR_BEGIN','PAIR_REDEEM','REFRESH','SESSION_REVOKE','TRACK_ACTIVATE','TRACK_RESUME','TRACK_STATE'));
ALTER TABLE wa.message_outbox ADD COLUMN start_intent_id UUID REFERENCES wa.start_intent(id);
ALTER TABLE wa.message_outbox ADD COLUMN start_payload_ciphertext BYTEA;
ALTER TABLE wa.message_outbox ADD CONSTRAINT ck_outbox_private_start CHECK (
    start_payload_ciphertext IS NULL OR (start_intent_id IS NOT NULL AND command_id IS NOT NULL
      AND octet_length(start_payload_ciphertext)>28 AND policy_version='run-start-v1'));
