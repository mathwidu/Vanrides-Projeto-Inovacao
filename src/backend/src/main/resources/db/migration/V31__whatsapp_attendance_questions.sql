-- Logical questions exist independently from optional reminder delivery.
ALTER TABLE wa.routine_occurrence ADD COLUMN materialized_routine_version BIGINT;
ALTER TABLE wa.routine_occurrence ADD COLUMN calendar_eligible BOOLEAN NOT NULL DEFAULT TRUE;
ALTER TABLE wa.routine_occurrence ADD COLUMN schedule_snapshot JSONB
    CHECK(schedule_snapshot IS NULL OR jsonb_typeof(schedule_snapshot)='object');
ALTER TABLE wa.attendance ADD CONSTRAINT uk_attendance_question_owner
    UNIQUE(id,routine_id,passenger_id,local_date);
ALTER TABLE wa.identity_alias ADD CONSTRAINT uk_alias_identity UNIQUE(id,identity_id);

CREATE TABLE wa.attendance_question (
    id UUID PRIMARY KEY,
    attendance_id UUID NOT NULL,
    occurrence_id UUID NOT NULL,
    routine_id UUID NOT NULL,
    driver_id UUID NOT NULL,
    passenger_id UUID NOT NULL,
    local_date DATE NOT NULL,
    routine_name TEXT NOT NULL CHECK(btrim(routine_name)<>''),
    direction TEXT NOT NULL CHECK(direction IN ('OUTBOUND','RETURN')),
    local_time TIME NOT NULL,
    timezone TEXT NOT NULL CHECK(btrim(timezone)<>''),
    member_id UUID NOT NULL REFERENCES wa.routine_member(id),
    link_id UUID NOT NULL,
    pickup_point_id UUID NOT NULL,
    instance_id TEXT NOT NULL CHECK(btrim(instance_id)<>''),
    environment TEXT NOT NULL CHECK(btrim(environment)<>''),
    state TEXT NOT NULL DEFAULT 'OPEN' CHECK(state IN ('OPEN','ANSWERED','CLOSED')),
    outbox_id UUID UNIQUE REFERENCES wa.message_outbox(id),
    source_inbox_event_id UUID,
    sender_alias_id UUID,
    job_credential_id UUID NOT NULL REFERENCES wa.service_credential(id),
    job_principal TEXT NOT NULL DEFAULT 'SYSTEM_JOB' CHECK(job_principal='SYSTEM_JOB'),
    link_version BIGINT NOT NULL CHECK(link_version>=0),
    member_version BIGINT NOT NULL CHECK(member_version>=0),
    pickup_version BIGINT NOT NULL CHECK(pickup_version>=0),
    schedule_hash VARCHAR(64) NOT NULL CHECK(schedule_hash ~ '^[0-9a-f]{64}$'),
    intent_hash VARCHAR(64) NOT NULL CHECK(intent_hash ~ '^[0-9a-f]{64}$'),
    opens_at TIMESTAMPTZ NOT NULL,
    reminder_at TIMESTAMPTZ NOT NULL,
    send_until TIMESTAMPTZ NOT NULL,
    answer_until TIMESTAMPTZ NOT NULL,
    answered_at TIMESTAMPTZ,
    created_at TIMESTAMPTZ NOT NULL DEFAULT CURRENT_TIMESTAMP,
    version BIGINT NOT NULL DEFAULT 0 CHECK(version>=0),
    FOREIGN KEY(attendance_id,routine_id,passenger_id,local_date)
        REFERENCES wa.attendance(id,routine_id,passenger_id,local_date),
    FOREIGN KEY(occurrence_id,routine_id,local_date,driver_id)
        REFERENCES wa.routine_occurrence(id,routine_id,local_date,driver_id),
    FOREIGN KEY(link_id,driver_id,passenger_id)
        REFERENCES wa.driver_passenger_link(id,driver_id,passenger_id),
    FOREIGN KEY(pickup_point_id,driver_id,passenger_id)
        REFERENCES wa.pickup_point(id,driver_id,passenger_id),
    FOREIGN KEY(source_inbox_event_id,passenger_id)
        REFERENCES wa.integration_inbox(id,identity_id),
    FOREIGN KEY(sender_alias_id,passenger_id)
        REFERENCES wa.identity_alias(id,identity_id),
    FOREIGN KEY(job_credential_id,job_principal)
        REFERENCES wa.service_credential(id,principal),
    CHECK(driver_id<>passenger_id),
    CHECK(answer_until>opens_at AND send_until>reminder_at),
    CHECK((state='ANSWERED')=(answered_at IS NOT NULL) OR state='CLOSED'),
    CHECK(outbox_id IS NULL OR (source_inbox_event_id IS NOT NULL AND sender_alias_id IS NOT NULL))
);
CREATE UNIQUE INDEX uk_attendance_question_current ON wa.attendance_question(attendance_id)
    WHERE state IN ('OPEN','ANSWERED');
CREATE INDEX ix_attendance_question_due ON wa.attendance_question(instance_id,environment,reminder_at)
    WHERE state='OPEN' AND outbox_id IS NULL;

CREATE TABLE wa.attendance_job_page (
    id UUID PRIMARY KEY,
    environment TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    job_type TEXT NOT NULL CHECK(job_type IN ('MATERIALIZE_OCCURRENCES','DUE_REMINDERS')),
    window_key TEXT NOT NULL,
    page_key TEXT NOT NULL,
    batch_limit INTEGER NOT NULL CHECK(batch_limit BETWEEN 1 AND 100),
    credential_id UUID NOT NULL REFERENCES wa.service_credential(id),
    response_json JSONB CHECK(response_json IS NULL OR jsonb_typeof(response_json)='object'),
    created_at TIMESTAMPTZ NOT NULL,
    UNIQUE(environment,instance_id,job_type,window_key,page_key)
);
CREATE TABLE wa.attendance_job_cursor (
    id UUID PRIMARY KEY,
    environment TEXT NOT NULL,
    instance_id TEXT NOT NULL,
    job_type TEXT NOT NULL,
    window_key TEXT NOT NULL,
    last_key TEXT NOT NULL,
    expires_at TIMESTAMPTZ NOT NULL,
    created_at TIMESTAMPTZ NOT NULL,
    CHECK(expires_at>created_at)
);

CREATE FUNCTION wa.guard_attendance_question() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.attendance_id,NEW.occurrence_id,NEW.routine_id,NEW.driver_id,NEW.passenger_id,
           NEW.local_date,NEW.routine_name,NEW.direction,NEW.local_time,NEW.timezone,
           NEW.member_id,NEW.link_id,NEW.pickup_point_id,NEW.instance_id,NEW.environment,
           NEW.job_credential_id,NEW.job_principal,NEW.link_version,NEW.member_version,NEW.pickup_version,NEW.schedule_hash,NEW.intent_hash,
           NEW.opens_at,NEW.reminder_at,NEW.send_until,NEW.answer_until,NEW.created_at)
       IS DISTINCT FROM
       ROW(OLD.id,OLD.attendance_id,OLD.occurrence_id,OLD.routine_id,OLD.driver_id,OLD.passenger_id,
           OLD.local_date,OLD.routine_name,OLD.direction,OLD.local_time,OLD.timezone,
           OLD.member_id,OLD.link_id,OLD.pickup_point_id,OLD.instance_id,OLD.environment,
           OLD.job_credential_id,OLD.job_principal,OLD.link_version,OLD.member_version,OLD.pickup_version,OLD.schedule_hash,OLD.intent_hash,
           OLD.opens_at,OLD.reminder_at,OLD.send_until,OLD.answer_until,OLD.created_at)
       OR (OLD.outbox_id IS NOT NULL AND ROW(NEW.outbox_id,NEW.source_inbox_event_id,NEW.sender_alias_id)
           IS DISTINCT FROM ROW(OLD.outbox_id,OLD.source_inbox_event_id,OLD.sender_alias_id))
       OR (OLD.state='CLOSED' AND NEW.state<>'CLOSED') THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='attendance question binding is immutable';
    END IF;
    RETURN NEW;
END
$$;
CREATE TRIGGER attendance_question_binding BEFORE UPDATE ON wa.attendance_question
    FOR EACH ROW EXECUTE FUNCTION wa.guard_attendance_question();
