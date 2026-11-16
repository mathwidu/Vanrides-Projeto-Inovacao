CREATE TABLE wa.driver_attendance_notice (
    outbox_id UUID PRIMARY KEY REFERENCES wa.message_outbox (id),
    kind TEXT NOT NULL CHECK (kind IN ('DEPARTURE', 'STOP_CANCELED')),
    occurrence_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    run_id UUID,
    stop_id UUID,
    passenger_id UUID,
    recipient_source_event_id UUID NOT NULL,
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    deadline_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_driver_notice_recipient FOREIGN KEY (recipient_source_event_id, driver_id)
        REFERENCES wa.integration_inbox (id, identity_id),
    CONSTRAINT fk_driver_notice_occurrence FOREIGN KEY (occurrence_id, driver_id)
        REFERENCES wa.routine_occurrence (id, driver_id),
    CONSTRAINT fk_driver_notice_stop FOREIGN KEY (stop_id, run_id, passenger_id)
        REFERENCES wa.run_stop (id, run_id, passenger_id),
    CONSTRAINT ck_driver_notice_subject CHECK (
        (kind = 'DEPARTURE' AND run_id IS NULL AND stop_id IS NULL AND passenger_id IS NULL)
        OR (kind = 'STOP_CANCELED' AND run_id IS NOT NULL AND stop_id IS NOT NULL AND passenger_id IS NOT NULL)
    )
);
CREATE UNIQUE INDEX uk_driver_notice_departure ON wa.driver_attendance_notice (occurrence_id)
    WHERE kind = 'DEPARTURE';
CREATE UNIQUE INDEX uk_driver_notice_stop_canceled ON wa.driver_attendance_notice (stop_id)
    WHERE kind = 'STOP_CANCELED';
