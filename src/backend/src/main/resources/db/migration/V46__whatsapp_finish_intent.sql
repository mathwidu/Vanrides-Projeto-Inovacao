ALTER TABLE wa.start_intent DROP CONSTRAINT start_intent_intent_action_check;
ALTER TABLE wa.start_intent ADD CONSTRAINT start_intent_intent_action_check
    CHECK (intent_action IN ('START','RESUME','FINISH'));
ALTER TABLE wa.start_intent ADD CONSTRAINT uq_finish_intent_owner
    UNIQUE (id,run_id,driver_id,device_id);
ALTER TABLE wa.tracking_session ADD CONSTRAINT uq_finish_session_owner
    UNIQUE (id,run_id,driver_id,device_id,generation);

CREATE TABLE wa.finish_intent_binding (
    intent_id UUID PRIMARY KEY,
    run_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    device_id UUID NOT NULL,
    session_id UUID NOT NULL,
    session_generation BIGINT NOT NULL CHECK (session_generation>0),
    issued_run_state TEXT NOT NULL CHECK (issued_run_state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')),
    baseline_stopped_at TIMESTAMPTZ,
    baseline_stop_ref UUID,
    confirmed_at TIMESTAMPTZ,
    confirmed_stop_ref UUID,
    FOREIGN KEY (intent_id,run_id,driver_id,device_id)
        REFERENCES wa.start_intent(id,run_id,driver_id,device_id) ON DELETE CASCADE,
    FOREIGN KEY (session_id,run_id,driver_id,device_id,session_generation)
        REFERENCES wa.tracking_session(id,run_id,driver_id,device_id,generation) ON DELETE CASCADE,
    CHECK (baseline_stopped_at IS NOT NULL OR baseline_stop_ref IS NULL)
);

ALTER TABLE wa.operation_replay DROP CONSTRAINT operation_replay_operation_kind_check;
ALTER TABLE wa.operation_replay ADD CONSTRAINT operation_replay_operation_kind_check
    CHECK (operation_kind IN ('PAIR_BEGIN','PAIR_REDEEM','REFRESH','SESSION_REVOKE',
        'TRACK_ACTIVATE','TRACK_RESUME','TRACK_STATE','TRACK_FINISH'));
