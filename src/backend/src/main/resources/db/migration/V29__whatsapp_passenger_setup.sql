-- Passenger admission and published common points. No legacy schema changes.
ALTER TABLE wa.driver_passenger_link ADD COLUMN requested_source_event_id UUID;
ALTER TABLE wa.driver_passenger_link ADD CONSTRAINT fk_link_request_actor
    FOREIGN KEY (requested_source_event_id,passenger_id) REFERENCES wa.integration_inbox(id,identity_id);
CREATE UNIQUE INDEX uk_link_request_event ON wa.driver_passenger_link(requested_source_event_id)
    WHERE requested_source_event_id IS NOT NULL;

CREATE TABLE wa.passenger_link_routine (
    id UUID PRIMARY KEY,
    link_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    weekdays SMALLINT[] NOT NULL CHECK (cardinality(weekdays) BETWEEN 1 AND 7
        AND weekdays <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[] AND array_position(weekdays,NULL) IS NULL),
    routine_version BIGINT NOT NULL CHECK (routine_version>=0),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(link_id,routine_id),
    FOREIGN KEY(link_id,driver_id,passenger_id) REFERENCES wa.driver_passenger_link(id,driver_id,passenger_id),
    FOREIGN KEY(routine_id,driver_id) REFERENCES wa.routine(id,driver_id)
);

CREATE TABLE wa.common_pickup_point (
    id UUID PRIMARY KEY,
    driver_id UUID NOT NULL REFERENCES wa.driver_authorization(driver_id),
    name TEXT NOT NULL CHECK (btrim(name)<>''),
    latitude DOUBLE PRECISION NOT NULL CHECK (latitude BETWEEN -90 AND 90),
    longitude DOUBLE PRECISION NOT NULL CHECK (longitude BETWEEN -180 AND 180),
    source_inbox_event_id UUID NOT NULL,
    state TEXT NOT NULL DEFAULT 'PUBLISHED' CHECK (state IN ('PUBLISHED','RETIRED')),
    version BIGINT NOT NULL DEFAULT 0 CHECK (version>=0),
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    UNIQUE(id,driver_id),
    FOREIGN KEY(source_inbox_event_id,driver_id) REFERENCES wa.integration_inbox(id,identity_id)
);
ALTER TABLE wa.pickup_point ADD COLUMN common_pickup_point_id UUID;
ALTER TABLE wa.pickup_point ADD COLUMN common_pickup_version BIGINT CHECK (common_pickup_version>=0);
ALTER TABLE wa.pickup_point ADD CONSTRAINT fk_pickup_common_driver
    FOREIGN KEY(common_pickup_point_id,driver_id) REFERENCES wa.common_pickup_point(id,driver_id);
ALTER TABLE wa.pickup_point ADD CONSTRAINT ck_pickup_common_version
    CHECK ((common_pickup_point_id IS NULL)=(common_pickup_version IS NULL));
ALTER TABLE wa.routine_member ADD COLUMN weekdays SMALLINT[];
UPDATE wa.routine_member m SET weekdays=r.weekdays FROM wa.routine r WHERE r.id=m.routine_id;
-- Default preserves compatible seed/operator consumers; new admissions always set the explicit subset.
ALTER TABLE wa.routine_member ALTER COLUMN weekdays SET DEFAULT ARRAY[1,2,3,4,5,6,7]::SMALLINT[];
ALTER TABLE wa.routine_member ALTER COLUMN weekdays SET NOT NULL;
ALTER TABLE wa.routine_member ADD CONSTRAINT ck_member_weekdays CHECK (cardinality(weekdays) BETWEEN 1 AND 7
    AND weekdays <@ ARRAY[1,2,3,4,5,6,7]::SMALLINT[] AND array_position(weekdays,NULL) IS NULL);
