CREATE TABLE wa.team_test_driver (
    driver_id UUID PRIMARY KEY REFERENCES wa.driver_authorization (driver_id),
    test_cohort_id UUID NOT NULL UNIQUE REFERENCES wa.test_cohort (id),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    routine_id UUID NOT NULL,
    created_by_github_id BIGINT NOT NULL CHECK (created_by_github_id > 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_team_test_driver_routine FOREIGN KEY (routine_id, driver_id) REFERENCES wa.routine (id, driver_id)
);

ALTER TABLE wa.driver_invite
    ADD COLUMN created_by_github_id BIGINT CHECK (created_by_github_id IS NULL OR created_by_github_id > 0);

ALTER TABLE wa.team_admission_operation
    DROP CONSTRAINT team_admission_operation_action_check,
    DROP CONSTRAINT ck_team_admission_invite,
    ADD COLUMN passenger_invite_id UUID REFERENCES wa.driver_invite (id),
    ADD COLUMN link_id UUID REFERENCES wa.driver_passenger_link (id),
    ADD CONSTRAINT ck_team_admission_action CHECK (action IN ('ADMIT_PASSENGER', 'ISSUE_DRIVER_INVITE',
        'RENEW_DRIVER_INVITE', 'ISSUE_TEST_PASSENGER_INVITE', 'APPROVE_TEST_LINK', 'REJECT_TEST_LINK',
        'RECTIFY_TEST_POINT', 'APPROVE_TEST_POINT', 'CLOSE_TEST_LINK')),
    ADD CONSTRAINT ck_team_admission_invite CHECK (
        (action IN ('ISSUE_DRIVER_INVITE', 'RENEW_DRIVER_INVITE')) = (invite_id IS NOT NULL)
        AND (action = 'ISSUE_TEST_PASSENGER_INVITE') = (passenger_invite_id IS NOT NULL)
        AND (action IN ('APPROVE_TEST_LINK', 'REJECT_TEST_LINK', 'RECTIFY_TEST_POINT', 'APPROVE_TEST_POINT',
            'CLOSE_TEST_LINK')) = (link_id IS NOT NULL)
    );

CREATE TABLE wa.team_passenger_notice (
    outbox_id UUID PRIMARY KEY REFERENCES wa.message_outbox (id),
    operation_id UUID NOT NULL UNIQUE REFERENCES wa.team_admission_operation (id),
    recipient_source_event_id UUID NOT NULL,
    environment TEXT NOT NULL CHECK (btrim(environment) <> ''),
    instance_id TEXT NOT NULL CHECK (btrim(instance_id) <> ''),
    event_kind TEXT NOT NULL CHECK (event_kind IN ('APPROVED', 'REJECTED', 'RECTIFY', 'POINT_APPROVED', 'CLOSED')),
    link_id UUID NOT NULL,
    driver_id UUID NOT NULL REFERENCES wa.team_test_driver (driver_id),
    passenger_id UUID NOT NULL,
    link_version BIGINT NOT NULL CHECK (link_version >= 0),
    link_state TEXT NOT NULL CHECK (link_state IN ('REQUESTED', 'APPROVED', 'REJECTED', 'CLOSED')),
    pickup_id UUID,
    pickup_version BIGINT CHECK (pickup_version >= 0),
    created_at TIMESTAMPTZ NOT NULL,
    CONSTRAINT fk_team_notice_recipient FOREIGN KEY (recipient_source_event_id, passenger_id)
        REFERENCES wa.integration_inbox (id, identity_id),
    CONSTRAINT fk_team_notice_link FOREIGN KEY (link_id, driver_id, passenger_id)
        REFERENCES wa.driver_passenger_link (id, driver_id, passenger_id),
    CONSTRAINT fk_team_notice_pickup FOREIGN KEY (pickup_id, link_id, driver_id, passenger_id)
        REFERENCES wa.pickup_point (id, link_id, driver_id, passenger_id),
    CONSTRAINT ck_team_notice_pickup CHECK ((pickup_id IS NULL) = (pickup_version IS NULL))
);
