-- Passenger responses retain an immutable actor, subject and authenticated return route.
-- Runtime relevance is checked again before submission; completed attempts keep this history.
ALTER TABLE wa.command_receipt ADD CONSTRAINT uk_command_delivery_actor UNIQUE (command_id,identity_id);
ALTER TABLE wa.message_outbox ADD CONSTRAINT uk_outbox_delivery_route UNIQUE (id,command_id,recipient_identity_id);
ALTER TABLE wa.pickup_point ADD CONSTRAINT uk_pickup_delivery_subject UNIQUE (id,link_id,driver_id,passenger_id);

CREATE TABLE wa.passenger_response_route (
    outbox_id UUID PRIMARY KEY,
    command_id UUID NOT NULL,
    actor_identity_id UUID NOT NULL,
    recipient_identity_id UUID NOT NULL,
    recipient_source_event_id UUID NOT NULL,
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    event_kind TEXT NOT NULL CHECK (event_kind IN (
        'ACTOR','REQUESTED','POINT_CHANGE','APPROVED','REJECTED','RECTIFY','POINT_APPROVED','CLOSED'
    )),
    link_id UUID,
    driver_id UUID,
    passenger_id UUID,
    link_version BIGINT CHECK (link_version >= 0),
    link_state TEXT CHECK (link_state IN ('REQUESTED','APPROVED','REJECTED','CLOSED')),
    selection_hash VARCHAR(64) CHECK (selection_hash ~ '^[0-9a-f]{64}$'),
    pickup_id UUID,
    pickup_version BIGINT CHECK (pickup_version >= 0),
    pickup_state TEXT CHECK (pickup_state IN ('PROPOSED','APPROVED','REJECTED','RETIRED')),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_passenger_route_outbox FOREIGN KEY (outbox_id,command_id,recipient_identity_id)
        REFERENCES wa.message_outbox (id,command_id,recipient_identity_id),
    CONSTRAINT fk_passenger_route_actor FOREIGN KEY (command_id,actor_identity_id)
        REFERENCES wa.command_receipt (command_id,identity_id),
    CONSTRAINT fk_passenger_route_recipient FOREIGN KEY (recipient_source_event_id,recipient_identity_id)
        REFERENCES wa.integration_inbox (id,identity_id),
    CONSTRAINT fk_passenger_route_link FOREIGN KEY (link_id,driver_id,passenger_id)
        REFERENCES wa.driver_passenger_link (id,driver_id,passenger_id),
    CONSTRAINT fk_passenger_route_pickup FOREIGN KEY (pickup_id,link_id,driver_id,passenger_id)
        REFERENCES wa.pickup_point (id,link_id,driver_id,passenger_id),
    CONSTRAINT ck_passenger_route_subject CHECK (
        (event_kind='ACTOR' AND actor_identity_id=recipient_identity_id
            AND link_id IS NULL AND driver_id IS NULL AND passenger_id IS NULL
            AND link_version IS NULL AND link_state IS NULL AND selection_hash IS NULL AND pickup_id IS NULL)
        OR (event_kind<>'ACTOR' AND link_id IS NOT NULL AND driver_id IS NOT NULL AND passenger_id IS NOT NULL
            AND driver_id<>passenger_id AND link_version IS NOT NULL AND link_state IS NOT NULL AND selection_hash IS NOT NULL
            AND ((event_kind IN ('REQUESTED','POINT_CHANGE') AND actor_identity_id=passenger_id AND recipient_identity_id=driver_id)
              OR (event_kind IN ('APPROVED','REJECTED','RECTIFY','POINT_APPROVED') AND actor_identity_id=driver_id AND recipient_identity_id=passenger_id)
              OR (event_kind='CLOSED' AND ((actor_identity_id=driver_id AND recipient_identity_id=passenger_id)
                OR (actor_identity_id=passenger_id AND recipient_identity_id=driver_id)))))
    ),
    CONSTRAINT ck_passenger_route_pickup CHECK (
        (pickup_id IS NULL AND pickup_version IS NULL AND pickup_state IS NULL)
        OR (pickup_id IS NOT NULL AND pickup_version IS NOT NULL AND pickup_state IS NOT NULL)
    ),
    CONSTRAINT ck_passenger_route_event_state CHECK (
        event_kind='ACTOR'
        OR (event_kind='REQUESTED' AND link_state='REQUESTED' AND pickup_state='PROPOSED' AND pickup_id IS NOT NULL)
        OR (event_kind='POINT_CHANGE' AND link_state IN ('REQUESTED','APPROVED') AND pickup_state='PROPOSED' AND pickup_id IS NOT NULL)
        OR (event_kind IN ('APPROVED','POINT_APPROVED') AND link_state='APPROVED' AND pickup_state='APPROVED' AND pickup_id IS NOT NULL)
        OR (event_kind='REJECTED' AND link_state='REJECTED')
        OR (event_kind='RECTIFY' AND link_state IN ('REQUESTED','APPROVED'))
        OR (event_kind='CLOSED' AND link_state='CLOSED')
    )
);
CREATE INDEX ix_passenger_response_link ON wa.passenger_response_route (link_id);

CREATE FUNCTION wa.guard_passenger_response_route()
RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION USING ERRCODE = '23514', MESSAGE = 'passenger response route is immutable',
            CONSTRAINT = 'ck_passenger_response_route_immutable';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER trg_passenger_response_route BEFORE UPDATE ON wa.passenger_response_route
    FOR EACH ROW EXECUTE FUNCTION wa.guard_passenger_response_route();
