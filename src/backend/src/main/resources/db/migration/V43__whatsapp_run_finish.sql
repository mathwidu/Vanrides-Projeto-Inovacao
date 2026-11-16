-- Arrival evidence contains only progress/times, never a trail of coordinates.
-- A new tracking generation must establish its own continuous arrival window.
CREATE TABLE wa.run_finish_progress (
    run_id UUID PRIMARY KEY REFERENCES wa.operation_run(id) ON DELETE CASCADE,
    session_id UUID NOT NULL REFERENCES wa.tracking_session(id) ON DELETE CASCADE,
    last_sequence BIGINT NOT NULL CHECK(last_sequence>0),
    last_captured_at TIMESTAMPTZ NOT NULL,
    observed_outside BOOLEAN NOT NULL DEFAULT false,
    arrived_since TIMESTAMPTZ,
    policy_version TEXT NOT NULL,
    last_reason TEXT NOT NULL
);
