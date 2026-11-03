-- One explicit result for proof that no provider POST was invoked. Old rows remain unchanged.
ALTER TABLE wa.message_attempt DROP CONSTRAINT message_attempt_state_check;
ALTER TABLE wa.message_attempt ADD CONSTRAINT message_attempt_state_check CHECK (
    state IN ('SUBMITTING','SUBMITTED','DELIVERED','READ','UNKNOWN','FAILED','EXPIRED','NOT_SUBMITTED')
);
ALTER TABLE wa.message_attempt ADD CONSTRAINT ck_attempt_not_submitted CHECK (
    state <> 'NOT_SUBMITTED' OR (
        error_code IS NOT NULL AND error_code='CHANNEL_UNAVAILABLE' AND external_message_id IS NULL
        AND completed_at IS NOT NULL AND credential_id IS NOT NULL
    )
);
