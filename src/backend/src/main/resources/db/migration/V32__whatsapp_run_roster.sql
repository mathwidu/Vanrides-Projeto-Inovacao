-- Operational decisions never impersonate a passenger's personal attendance answer.
CREATE TABLE wa.occurrence_roster_override (
    id UUID PRIMARY KEY,
    occurrence_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    local_date DATE NOT NULL,
    included BOOLEAN NOT NULL,
    revision BIGINT NOT NULL CHECK(revision>0),
    reason TEXT NOT NULL CHECK (length(btrim(reason)) BETWEEN 3 AND 160),
    actor_identity_id UUID NOT NULL,
    source_inbox_event_id UUID NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(source_inbox_event_id),
    UNIQUE(occurrence_id,passenger_id,revision),
    FOREIGN KEY(occurrence_id,routine_id,local_date,driver_id)
        REFERENCES wa.routine_occurrence(id,routine_id,local_date,driver_id),
    FOREIGN KEY(routine_id,passenger_id) REFERENCES wa.routine_member(routine_id,passenger_id),
    FOREIGN KEY(source_inbox_event_id,actor_identity_id) REFERENCES wa.integration_inbox(id,identity_id),
    CHECK(actor_identity_id=driver_id AND driver_id<>passenger_id)
);
CREATE INDEX ix_occurrence_roster_current
    ON wa.occurrence_roster_override(occurrence_id,passenger_id,revision DESC);

CREATE FUNCTION wa.guard_roster_override() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operational attendance audit is append only';
END
$$;
CREATE TRIGGER roster_override_immutable BEFORE UPDATE ON wa.occurrence_roster_override
    FOR EACH ROW EXECUTE FUNCTION wa.guard_roster_override();
