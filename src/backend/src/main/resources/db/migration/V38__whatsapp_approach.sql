-- Three short-lived fixes support a decision; receipts/audits never contain GPS.
CREATE TABLE wa.approach_fix (
    session_id UUID NOT NULL REFERENCES wa.tracking_session(id) ON DELETE CASCADE,
    sequence BIGINT NOT NULL,
    captured_at TIMESTAMPTZ NOT NULL,
    latitude DOUBLE PRECISION NOT NULL CHECK(latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK(longitude BETWEEN -180 AND 180),
    accuracy_m DOUBLE PRECISION NOT NULL CHECK(accuracy_m BETWEEN 0 AND 50),
    heading_deg DOUBLE PRECISION CHECK(heading_deg>=0 AND heading_deg<360),
    expires_at TIMESTAMPTZ NOT NULL,
    PRIMARY KEY(session_id,sequence),
    CHECK(expires_at=captured_at+INTERVAL '20 seconds')
);
CREATE INDEX ix_approach_fix_expiry ON wa.approach_fix(expires_at);
CREATE TABLE wa.approach_progress (
    run_id UUID NOT NULL REFERENCES wa.operation_run(id) ON DELETE CASCADE,
    physical_stop_key UUID NOT NULL,
    nearest_m DOUBLE PRECISION NOT NULL CHECK(nearest_m>=0),
    nearest_at TIMESTAMPTZ NOT NULL,
    last_session_id UUID REFERENCES wa.tracking_session(id),
    last_sequence BIGINT NOT NULL,
    policy_version TEXT NOT NULL,
    last_reason TEXT NOT NULL,
    suppressed_count BIGINT NOT NULL DEFAULT 0 CHECK(suppressed_count>=0),
    PRIMARY KEY(run_id,physical_stop_key)
);
CREATE TABLE wa.approach_delivery_route (
    outbox_id UUID PRIMARY KEY REFERENCES wa.message_outbox(id) ON DELETE CASCADE,
    environment TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    recipient_source_event_id UUID NOT NULL REFERENCES wa.integration_inbox(id),
    session_id UUID NOT NULL REFERENCES wa.tracking_session(id),
    sequence BIGINT NOT NULL
);
-- Location requests retain only their authorized run and human menu, never coordinates.
ALTER TABLE wa.message_outbox ADD COLUMN location_requested BOOLEAN NOT NULL DEFAULT false;
ALTER TABLE wa.message_outbox ADD CONSTRAINT ck_location_response CHECK (
    NOT location_requested OR (purpose='PIN' AND command_id IS NOT NULL AND policy_version='driver-location-v1'
      AND payload_kind='TEXT'));
CREATE FUNCTION wa.guard_stop_progress() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF (OLD.state IN ('PASSED','SKIPPED','CANCELED') AND NEW.state<>OLD.state)
       OR (OLD.state='APPROACHING' AND NEW.state='WAITING') THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='stop progress cannot regress';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER stop_progress_monotonic BEFORE UPDATE ON wa.run_stop
    FOR EACH ROW EXECUTE FUNCTION wa.guard_stop_progress();
