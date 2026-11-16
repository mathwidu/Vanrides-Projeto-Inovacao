-- Additive privacy lifecycle. UUIDs, ownership and operational facts survive erasure.
ALTER TABLE wa.profile ADD COLUMN closed_at TIMESTAMPTZ;
ALTER TABLE wa.profile ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.profile ADD CONSTRAINT ck_profile_privacy_lifecycle CHECK (
    (closed_at IS NULL OR status='CLOSED') AND
    (privacy_erased_at IS NULL OR (closed_at IS NOT NULL AND privacy_erased_at>=closed_at))
);

-- Intentionally no foreign keys: restoring/deleting an operational aggregate must
-- never silently remove the durable deletion intent or its journal acknowledgement.
CREATE TABLE wa.privacy_subject (
    subject_id UUID PRIMARY KEY,
    environment TEXT NOT NULL CHECK(btrim(environment)<>''),
    instance_id TEXT NOT NULL CHECK(btrim(instance_id)<>''),
    closed_at TIMESTAMPTZ NOT NULL,
    erase_after TIMESTAMPTZ NOT NULL,
    erased_at TIMESTAMPTZ,
    CHECK(erase_after>=closed_at+INTERVAL '30 days'),
    CHECK(erased_at IS NULL OR erased_at>=erase_after)
);
CREATE TABLE wa.privacy_erasure_ledger (
    sequence BIGINT GENERATED ALWAYS AS IDENTITY PRIMARY KEY,
    event_id UUID NOT NULL UNIQUE,
    subject_id UUID NOT NULL UNIQUE,
    environment TEXT NOT NULL CHECK(btrim(environment)<>''),
    instance_id TEXT NOT NULL CHECK(btrim(instance_id)<>''),
    payload_json JSONB NOT NULL CHECK(jsonb_typeof(payload_json)='object'),
    payload_hash VARCHAR(64) NOT NULL CHECK(payload_hash ~ '^[0-9a-f]{64}$'),
    state TEXT NOT NULL DEFAULT 'INTENT' CHECK(state IN ('INTENT','JOURNALED','PURGED')),
    created_at TIMESTAMPTZ NOT NULL,
    journal_sequence BIGINT,
    journal_digest VARCHAR(64),
    journaled_at TIMESTAMPTZ,
    purge_started_at TIMESTAMPTZ,
    purged_at TIMESTAMPTZ,
    CHECK(payload_hash=encode(sha256(convert_to(payload_json::text,'UTF8')),'hex')),
    CHECK((state='INTENT' AND journal_sequence IS NULL AND journal_digest IS NULL AND journaled_at IS NULL)
       OR (state IN ('JOURNALED','PURGED') AND journal_sequence>0
           AND journal_digest ~ '^[0-9a-f]{64}$' AND journaled_at IS NOT NULL)),
    CHECK((state='PURGED')=(purged_at IS NOT NULL)),
    CHECK(purged_at IS NULL OR (purge_started_at IS NOT NULL AND purged_at>=purge_started_at))
);
CREATE INDEX ix_privacy_subject_due ON wa.privacy_subject(environment,instance_id,erase_after)
    WHERE erased_at IS NULL;

CREATE FUNCTION wa.guard_privacy_ledger() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='INSERT' THEN
        IF NEW.state<>'INTENT' OR NEW.purge_started_at IS NOT NULL THEN
            RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='privacy ledger starts with an intent';
        END IF;
        RETURN NEW;
    END IF;
    IF TG_OP='DELETE' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='privacy ledger cannot be deleted';
    END IF;
    IF ROW(NEW.sequence,NEW.event_id,NEW.subject_id,NEW.environment,NEW.instance_id,
           NEW.payload_json,NEW.payload_hash,NEW.created_at)
       IS DISTINCT FROM ROW(OLD.sequence,OLD.event_id,OLD.subject_id,OLD.environment,OLD.instance_id,
           OLD.payload_json,OLD.payload_hash,OLD.created_at)
       OR (OLD.state='INTENT' AND NEW.state NOT IN ('INTENT','JOURNALED'))
       OR (OLD.state='JOURNALED' AND NEW.state NOT IN ('JOURNALED','PURGED'))
       OR (OLD.state='PURGED' AND NEW IS DISTINCT FROM OLD)
       OR (OLD.journaled_at IS NOT NULL AND ROW(NEW.journal_sequence,NEW.journal_digest,NEW.journaled_at)
           IS DISTINCT FROM ROW(OLD.journal_sequence,OLD.journal_digest,OLD.journaled_at))
       OR (OLD.purge_started_at IS NOT NULL AND NEW.purge_started_at IS DISTINCT FROM OLD.purge_started_at)
       OR (NEW.purge_started_at IS NOT NULL AND NOT EXISTS(
           SELECT 1 FROM wa.privacy_subject s WHERE s.subject_id=NEW.subject_id
             AND NEW.purge_started_at>=s.erase_after AND NEW.state IN ('JOURNALED','PURGED'))) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='privacy ledger transition is not monotonic';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_privacy_ledger BEFORE INSERT OR UPDATE OR DELETE ON wa.privacy_erasure_ledger
    FOR EACH ROW EXECUTE FUNCTION wa.guard_privacy_ledger();

CREATE FUNCTION wa.privacy_has_active_reference(subject UUID) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.operation_run r
      WHERE r.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED') AND
        (r.driver_id=subject OR EXISTS(SELECT 1 FROM wa.run_stop s WHERE s.run_id=r.id AND s.passenger_id=subject)))
      OR EXISTS(SELECT 1 FROM wa.tracking_session s WHERE s.driver_id=subject
        AND s.state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED'))
$$;
CREATE FUNCTION wa.privacy_can_erase(subject UUID) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.privacy_subject s JOIN wa.profile p ON p.identity_id=s.subject_id
      JOIN wa.privacy_erasure_ledger l ON l.subject_id=s.subject_id
      WHERE s.subject_id=subject AND p.status='CLOSED' AND p.closed_at=s.closed_at
        AND l.environment=s.environment AND l.instance_id=s.instance_id
        AND l.state IN ('JOURNALED','PURGED') AND l.purge_started_at>=s.erase_after)
      AND NOT wa.privacy_has_active_reference(subject)
$$;

CREATE FUNCTION wa.guard_profile_privacy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.closed_at IS NOT NULL AND (NEW.status<>'CLOSED' OR NEW.closed_at IS DISTINCT FROM OLD.closed_at) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='closed privacy profile cannot reopen';
    END IF;
    IF (NEW.privacy_erased_at IS DISTINCT FROM OLD.privacy_erased_at AND
          (OLD.privacy_erased_at IS NOT NULL OR NOT wa.privacy_can_erase(OLD.identity_id)))
       OR (NEW.privacy_erased_at IS NOT NULL AND
          (NEW.display_name IS NOT NULL OR NEW.preferences_json<>'{}'::jsonb OR NEW.driver_enabled)) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='erased privacy profile cannot regain personal data';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_profile_privacy BEFORE UPDATE ON wa.profile
    FOR EACH ROW EXECUTE FUNCTION wa.guard_profile_privacy();

ALTER TABLE wa.identity_alias ALTER COLUMN alias_value DROP NOT NULL;
ALTER TABLE wa.identity_alias ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.identity_alias ADD CONSTRAINT ck_alias_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND alias_value IS NOT NULL)
    OR (privacy_erased_at IS NOT NULL AND alias_value IS NULL AND verified_phone IS NULL));
ALTER TABLE wa.pickup_point ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE wa.pickup_point ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE wa.pickup_point ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.pickup_point ADD CONSTRAINT ck_pickup_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND latitude IS NOT NULL AND longitude IS NOT NULL)
    OR (privacy_erased_at IS NOT NULL AND latitude IS NULL AND longitude IS NULL
        AND label IS NULL AND reference_text IS NULL));
ALTER TABLE wa.run_stop ALTER COLUMN latitude_snapshot DROP NOT NULL;
ALTER TABLE wa.run_stop ALTER COLUMN longitude_snapshot DROP NOT NULL;
ALTER TABLE wa.run_stop ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.run_stop ADD CONSTRAINT ck_stop_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND latitude_snapshot IS NOT NULL AND longitude_snapshot IS NOT NULL)
    OR (privacy_erased_at IS NOT NULL AND latitude_snapshot IS NULL AND longitude_snapshot IS NULL
        AND reference_snapshot IS NULL));
ALTER TABLE wa.occurrence_roster_override ALTER COLUMN reason DROP NOT NULL;
ALTER TABLE wa.occurrence_roster_override ADD COLUMN privacy_erased_at TIMESTAMPTZ;

CREATE FUNCTION wa.guard_personal_projection() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE subject UUID; fields TEXT[];
BEGIN
    IF TG_TABLE_NAME='identity_alias' THEN
        subject=OLD.identity_id; fields=ARRAY['alias_value','verified_phone','privacy_erased_at'];
    ELSIF TG_TABLE_NAME='routine' THEN
        subject=OLD.driver_id; fields=ARRAY['name','destination_label','destination_latitude','destination_longitude','privacy_erased_at'];
    ELSIF TG_TABLE_NAME='common_pickup_point' THEN
        subject=OLD.driver_id; fields=ARRAY['name','latitude','longitude','privacy_erased_at'];
    ELSE
        subject=OLD.passenger_id; fields=ARRAY['latitude','longitude','label','reference_text','privacy_erased_at'];
    END IF;
    IF OLD.privacy_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='erased personal projection is immutable';
    END IF;
    IF NEW.privacy_erased_at IS DISTINCT FROM OLD.privacy_erased_at AND
       (NOT wa.privacy_can_erase(subject) OR
        (to_jsonb(NEW)-fields) IS DISTINCT FROM (to_jsonb(OLD)-fields)) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='personal projection requires a journaled erasure';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_alias_privacy BEFORE UPDATE ON wa.identity_alias
    FOR EACH ROW EXECUTE FUNCTION wa.guard_personal_projection();
CREATE TRIGGER trg_pickup_privacy BEFORE UPDATE ON wa.pickup_point
    FOR EACH ROW EXECUTE FUNCTION wa.guard_personal_projection();

-- Deterministic removal of only the eligible subject's personal fields.
CREATE FUNCTION wa.privacy_project_run(snapshot JSONB, driver UUID DEFAULT NULL) RETURNS JSONB LANGUAGE plpgsql STABLE AS $$
DECLARE result JSONB=snapshot; participant JSONB; projected JSONB='[]'; subject UUID; names JSONB;
BEGIN
    IF jsonb_typeof(snapshot->'participants')='array' THEN
        FOR participant IN SELECT value FROM jsonb_array_elements(snapshot->'participants') LOOP
            IF participant->>'passenger' ~ '^[0-9a-f-]{36}$' THEN
                subject=(participant->>'passenger')::uuid;
                IF wa.privacy_can_erase(subject) THEN
                    participant=participant-ARRAY['name','latitude','longitude','reference','overrideReason'];
                END IF;
            END IF;
            projected=projected||jsonb_build_array(participant);
        END LOOP;
        result=jsonb_set(result,'{participants}',projected);
    END IF;
    IF jsonb_typeof(snapshot->'passengerNames')='object' THEN
        names=snapshot->'passengerNames';
        FOR subject IN SELECT key::uuid FROM jsonb_object_keys(names) key WHERE key ~ '^[0-9a-f-]{36}$' LOOP
            IF wa.privacy_can_erase(subject) THEN names=names-subject::text; END IF;
        END LOOP;
        result=jsonb_set(result,'{passengerNames}',names);
    END IF;
    IF driver IS NOT NULL AND wa.privacy_can_erase(driver) THEN
        IF jsonb_typeof(result->'routine')='object' THEN
            result=jsonb_set(result,'{routine}',(result->'routine')-ARRAY['name','destination_label','destination_latitude','destination_longitude']);
        END IF;
        IF jsonb_typeof(result->'schedule')='object' THEN
            result=jsonb_set(result,'{schedule}',(result->'schedule')-'name');
        END IF;
    END IF;
    RETURN result;
END $$;

CREATE OR REPLACE FUNCTION wa.guard_run_binding() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF ROW(NEW.id,NEW.occurrence_id,NEW.routine_id,NEW.driver_id,NEW.local_date)
       IS DISTINCT FROM ROW(OLD.id,OLD.occurrence_id,OLD.routine_id,OLD.driver_id,OLD.local_date) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='run ownership is immutable', CONSTRAINT='ck_run_immutable_owner';
    END IF;
    IF OLD.snapshot_at IS NOT NULL AND ROW(NEW.snapshot_json,NEW.snapshot_at,NEW.policy_version)
       IS DISTINCT FROM ROW(OLD.snapshot_json,OLD.snapshot_at,OLD.policy_version) AND NOT (
          OLD.state IN ('COMPLETED','CANCELED') AND NEW.state=OLD.state
          AND NEW.snapshot_at=OLD.snapshot_at AND NEW.policy_version=OLD.policy_version
          AND NEW.snapshot_json=wa.privacy_project_run(OLD.snapshot_json,OLD.driver_id)
          AND NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='run snapshot is immutable', CONSTRAINT='ck_run_immutable_snapshot';
    END IF;
    IF OLD.state IN ('COMPLETED','CANCELED') AND NEW.state<>OLD.state THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='terminal run cannot reopen', CONSTRAINT='ck_run_terminal';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION wa.guard_stop_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.privacy_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='erased run stop cannot change';
    END IF;
    IF (to_jsonb(NEW)-ARRAY['state','canceled_at','version']) IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['state','canceled_at','version']) AND NOT (
        OLD.privacy_erased_at IS NULL AND NEW.privacy_erased_at IS NOT NULL
        AND NEW.latitude_snapshot IS NULL AND NEW.longitude_snapshot IS NULL AND NEW.reference_snapshot IS NULL
        AND (to_jsonb(NEW)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','privacy_erased_at'])=
            (to_jsonb(OLD)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','privacy_erased_at'])
        AND wa.privacy_can_erase(OLD.passenger_id)
        AND EXISTS(SELECT 1 FROM wa.operation_run r WHERE r.id=OLD.run_id AND r.state IN ('COMPLETED','CANCELED'))) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='run stop snapshot is immutable', CONSTRAINT='ck_stop_immutable_snapshot';
    END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION wa.guard_roster_override() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.privacy_erased_at IS NULL AND NEW.privacy_erased_at IS NOT NULL AND NEW.reason IS NULL
       AND (to_jsonb(NEW)-ARRAY['reason','privacy_erased_at'])=(to_jsonb(OLD)-ARRAY['reason','privacy_erased_at'])
       AND wa.privacy_can_erase(OLD.passenger_id)
       AND NOT EXISTS(SELECT 1 FROM wa.operation_run r WHERE r.occurrence_id=OLD.occurrence_id
         AND r.state NOT IN ('COMPLETED','CANCELED')) THEN RETURN NEW; END IF;
    RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='operational attendance audit is append only';
END $$;


ALTER TABLE wa.routine ALTER COLUMN name DROP NOT NULL;
ALTER TABLE wa.routine ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.routine ADD CONSTRAINT ck_routine_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND name IS NOT NULL) OR
    (privacy_erased_at IS NOT NULL AND name IS NULL AND destination_label IS NULL
      AND destination_latitude IS NULL AND destination_longitude IS NULL AND state='CLOSED'));
ALTER TABLE wa.common_pickup_point ALTER COLUMN name DROP NOT NULL;
ALTER TABLE wa.common_pickup_point ALTER COLUMN latitude DROP NOT NULL;
ALTER TABLE wa.common_pickup_point ALTER COLUMN longitude DROP NOT NULL;
ALTER TABLE wa.common_pickup_point ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.common_pickup_point ADD CONSTRAINT ck_common_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND name IS NOT NULL AND latitude IS NOT NULL AND longitude IS NOT NULL) OR
    (privacy_erased_at IS NOT NULL AND name IS NULL AND latitude IS NULL AND longitude IS NULL AND state='RETIRED'));
CREATE TRIGGER trg_routine_privacy BEFORE UPDATE ON wa.routine
    FOR EACH ROW EXECUTE FUNCTION wa.guard_personal_projection();
CREATE TRIGGER trg_common_privacy BEFORE UPDATE ON wa.common_pickup_point
    FOR EACH ROW EXECUTE FUNCTION wa.guard_personal_projection();

CREATE FUNCTION wa.guard_privacy_subject() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF TG_OP='DELETE' THEN RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='privacy lifecycle cannot be deleted'; END IF;
    IF (to_jsonb(NEW)-'erased_at')<>(to_jsonb(OLD)-'erased_at')
       OR OLD.erased_at IS NOT NULL OR NEW.erased_at IS NULL OR NOT wa.privacy_can_erase(OLD.subject_id) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='privacy lifecycle is monotonic';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_privacy_subject BEFORE UPDATE OR DELETE ON wa.privacy_subject
    FOR EACH ROW EXECUTE FUNCTION wa.guard_privacy_subject();

CREATE FUNCTION wa.guard_closed_subject_insert() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE subject UUID;
BEGIN
    subject=CASE WHEN TG_TABLE_NAME='identity_alias' THEN (to_jsonb(NEW)->>'identity_id')::uuid
      WHEN TG_TABLE_NAME IN ('pickup_point','run_stop') THEN (to_jsonb(NEW)->>'passenger_id')::uuid
      ELSE (to_jsonb(NEW)->>'driver_id')::uuid END;
    IF EXISTS(SELECT 1 FROM wa.profile p WHERE p.identity_id=subject AND p.closed_at IS NOT NULL) THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='closed privacy subject cannot gain a new personal copy';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_alias_closed_insert BEFORE INSERT ON wa.identity_alias FOR EACH ROW EXECUTE FUNCTION wa.guard_closed_subject_insert();
CREATE TRIGGER trg_pickup_closed_insert BEFORE INSERT ON wa.pickup_point FOR EACH ROW EXECUTE FUNCTION wa.guard_closed_subject_insert();
CREATE TRIGGER trg_stop_closed_insert BEFORE INSERT ON wa.run_stop FOR EACH ROW EXECUTE FUNCTION wa.guard_closed_subject_insert();
CREATE TRIGGER trg_routine_closed_insert BEFORE INSERT ON wa.routine FOR EACH ROW EXECUTE FUNCTION wa.guard_closed_subject_insert();
CREATE TRIGGER trg_common_closed_insert BEFORE INSERT ON wa.common_pickup_point FOR EACH ROW EXECUTE FUNCTION wa.guard_closed_subject_insert();


ALTER TABLE wa.attendance_question ALTER COLUMN routine_name DROP NOT NULL;
ALTER TABLE wa.attendance_question ADD COLUMN privacy_erased_at TIMESTAMPTZ;
ALTER TABLE wa.attendance_question ADD CONSTRAINT ck_question_privacy_absence CHECK(
    (privacy_erased_at IS NULL AND routine_name IS NOT NULL) OR
    (privacy_erased_at IS NOT NULL AND routine_name IS NULL));
CREATE OR REPLACE FUNCTION wa.guard_attendance_question() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.privacy_erased_at IS NULL AND NEW.privacy_erased_at IS NOT NULL AND NEW.routine_name IS NULL
       AND (to_jsonb(NEW)-ARRAY['routine_name','privacy_erased_at'])=(to_jsonb(OLD)-ARRAY['routine_name','privacy_erased_at'])
       AND wa.privacy_can_erase(OLD.driver_id)
       AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=OLD.occurrence_id
           AND run.state NOT IN ('COMPLETED','CANCELED')) THEN RETURN NEW; END IF;
    IF OLD.privacy_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
       RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='erased question projection is immutable';
    END IF;
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

CREATE FUNCTION wa.guard_occurrence_privacy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF wa.privacy_can_erase(OLD.driver_id) AND NEW.schedule_snapshot ? 'name' THEN
        RAISE EXCEPTION USING ERRCODE='23514', MESSAGE='erased routine label cannot return';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_occurrence_privacy BEFORE UPDATE ON wa.routine_occurrence
    FOR EACH ROW EXECUTE FUNCTION wa.guard_occurrence_privacy();
