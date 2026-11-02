-- Additive transport receipts. V26 remains immutable; old synthetic/operator rows remain valid.
ALTER TABLE wa.integration_inbox DROP CONSTRAINT integration_inbox_state_check;
ALTER TABLE wa.integration_inbox ADD CONSTRAINT integration_inbox_state_check
    CHECK (state IN ('PENDING','CLAIMED','COMPLETED','RETRY','SKIPPED','EXPIRED','PAUSED'));
ALTER TABLE wa.integration_inbox ADD COLUMN not_before TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP;
ALTER TABLE wa.integration_inbox ADD COLUMN received_order BIGINT GENERATED ALWAYS AS IDENTITY;
ALTER TABLE wa.integration_inbox ADD COLUMN result_request_hash VARCHAR(64);
ALTER TABLE wa.integration_inbox ADD COLUMN result_json JSONB;
ALTER TABLE wa.integration_inbox ADD CONSTRAINT ck_inbox_result CHECK (
    (result_request_hash IS NULL AND result_json IS NULL)
    OR (result_request_hash IS NOT NULL AND result_request_hash ~ '^[0-9a-f]{64}$' AND result_json IS NOT NULL
        AND jsonb_typeof(result_json)='object' AND lease_generation>0)
);
CREATE INDEX ix_inbox_conversation_head ON wa.integration_inbox (identity_id,received_order)
    WHERE category='MESSAGE' AND state IN ('PENDING','CLAIMED','RETRY','PAUSED');
CREATE INDEX ix_inbox_status_pending ON wa.integration_inbox (instance_id,created_at)
    WHERE category='STATUS' AND state='PENDING';

ALTER TABLE wa.message_attempt ADD COLUMN credential_id UUID REFERENCES wa.service_credential(id);
ALTER TABLE wa.message_attempt ADD COLUMN submit_before TIMESTAMPTZ;
ALTER TABLE wa.message_attempt ADD COLUMN result_request_hash VARCHAR(64);
ALTER TABLE wa.message_attempt ADD COLUMN result_json JSONB;
ALTER TABLE wa.message_attempt ADD CONSTRAINT ck_attempt_transport CHECK (
    (credential_id IS NULL AND submit_before IS NULL)
    OR (credential_id IS NOT NULL AND submit_before IS NOT NULL AND submit_before>started_at)
);
ALTER TABLE wa.message_attempt ADD CONSTRAINT ck_attempt_result CHECK (
    (result_request_hash IS NULL AND result_json IS NULL)
    OR (result_request_hash IS NOT NULL AND result_request_hash ~ '^[0-9a-f]{64}$' AND result_json IS NOT NULL
        AND jsonb_typeof(result_json)='object' AND credential_id IS NOT NULL)
);
-- Only transport-owned attempts participate; pre-transport/operator fixture rows are not sendable.
CREATE UNIQUE INDEX uk_attempt_instance_submitting ON wa.message_attempt(instance_id)
    WHERE state='SUBMITTING' AND credential_id IS NOT NULL;
