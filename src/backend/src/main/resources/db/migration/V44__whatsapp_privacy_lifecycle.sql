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

-- Ninety-day historical projection. Origin survives source deletion; controls/FKs never disappear.
ALTER TABLE wa.routine_occurrence ADD COLUMN history_environment TEXT;
ALTER TABLE wa.routine_occurrence ADD COLUMN history_instance_id TEXT;
ALTER TABLE wa.routine_occurrence ADD COLUMN history_origin_kind TEXT;
ALTER TABLE wa.routine_occurrence ADD COLUMN history_origin_id UUID;
ALTER TABLE wa.routine_occurrence ADD COLUMN history_origin_bound_at TIMESTAMPTZ;
ALTER TABLE wa.routine_occurrence ADD COLUMN history_erased_at TIMESTAMPTZ;
ALTER TABLE wa.routine_occurrence ADD CONSTRAINT ck_history_origin_shape CHECK (
    num_nonnulls(history_environment,history_instance_id,history_origin_kind,history_origin_id,history_origin_bound_at)=0 OR
    (num_nonnulls(history_environment,history_instance_id,history_origin_kind,history_origin_id,history_origin_bound_at)=5
      AND btrim(history_environment)<>'' AND btrim(history_instance_id)<>''
      AND history_origin_kind IN ('SYSTEM_JOB','START_INTENT','ATTENDANCE_QUESTION','ROSTER_OVERRIDE','SNAPSHOT_SOURCE_EVENT')));
ALTER TABLE wa.operation_run ADD COLUMN history_erased_at TIMESTAMPTZ;
ALTER TABLE wa.run_stop ADD COLUMN history_erased_at TIMESTAMPTZ;
ALTER TABLE wa.run_stop ALTER COLUMN attendance_snapshot DROP NOT NULL;
ALTER TABLE wa.run_stop ALTER COLUMN state DROP NOT NULL;
ALTER TABLE wa.run_stop DROP CONSTRAINT ck_stop_privacy_absence;
ALTER TABLE wa.run_stop ADD CONSTRAINT ck_stop_privacy_absence CHECK (
    (history_erased_at IS NOT NULL AND latitude_snapshot IS NULL AND longitude_snapshot IS NULL
      AND reference_snapshot IS NULL AND attendance_snapshot IS NULL AND state IS NULL AND canceled_at IS NULL)
    OR (history_erased_at IS NULL AND attendance_snapshot IS NOT NULL AND state IS NOT NULL AND
      ((privacy_erased_at IS NULL AND latitude_snapshot IS NOT NULL AND longitude_snapshot IS NOT NULL)
       OR (privacy_erased_at IS NOT NULL AND latitude_snapshot IS NULL AND longitude_snapshot IS NULL AND reference_snapshot IS NULL))));

CREATE FUNCTION wa.privacy_history_sources(occ UUID)
RETURNS TABLE(kind TEXT,origin_id UUID,environment TEXT,instance_id TEXT) LANGUAGE SQL STABLE AS $$
    SELECT 'START_INTENT',intent.id,intent.environment,source.instance_id
    FROM wa.start_intent intent LEFT JOIN wa.command_receipt command ON command.command_id=intent.request_command_id
    LEFT JOIN wa.integration_inbox source ON source.id=command.inbox_event_id AND source.identity_id=intent.driver_id
    WHERE intent.occurrence_id=occ
    UNION ALL SELECT 'ATTENDANCE_QUESTION',q.id,q.environment,q.instance_id FROM wa.attendance_question q WHERE q.occurrence_id=occ
    UNION ALL SELECT 'ROSTER_OVERRIDE',r.id,c.environment,source.instance_id FROM wa.occurrence_roster_override r
    JOIN wa.identity person ON person.id=r.driver_id LEFT JOIN wa.test_cohort c ON c.id=person.test_cohort_id
    JOIN wa.integration_inbox source ON source.id=r.source_inbox_event_id WHERE r.occurrence_id=occ
    UNION ALL SELECT 'SNAPSHOT_SOURCE_EVENT',source.id,c.environment,source.instance_id
    FROM wa.operation_run run JOIN wa.identity person ON person.id=run.driver_id LEFT JOIN wa.test_cohort c ON c.id=person.test_cohort_id
    LEFT JOIN wa.integration_inbox source ON source.id=CASE
      WHEN run.snapshot_json#>>'{routine,destination_source_event_id}' ~* '^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$'
      THEN (run.snapshot_json#>>'{routine,destination_source_event_id}')::uuid END AND source.identity_id=run.driver_id
    WHERE run.occurrence_id=occ AND run.snapshot_at IS NOT NULL
      AND run.snapshot_json#>'{routine,destination_source_event_id}' IS NOT NULL
      AND run.snapshot_json#>'{routine,destination_source_event_id}'<>'null'::jsonb
$$;
CREATE FUNCTION wa.privacy_history_origin_consistent(occ UUID) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.routine_occurrence o WHERE o.id=occ AND o.history_environment IS NOT NULL
      AND NOT EXISTS(SELECT 1 FROM wa.privacy_history_sources(occ) source
        WHERE source.environment IS DISTINCT FROM o.history_environment OR source.instance_id IS DISTINCT FROM o.history_instance_id))
$$;
CREATE FUNCTION wa.privacy_history_subject_scope(subject UUID,env TEXT,inst TEXT) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.identity i JOIN wa.test_cohort c ON c.id=i.test_cohort_id WHERE i.id=subject AND c.environment=env
      AND NOT EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id IS DISTINCT FROM inst)
      AND (EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=i.id AND a.instance_id=inst)
        OR EXISTS(SELECT 1 FROM wa.privacy_subject p JOIN wa.privacy_erasure_ledger l ON l.subject_id=p.subject_id
          WHERE p.subject_id=i.id AND p.environment=env AND p.instance_id=inst AND p.erased_at IS NOT NULL
            AND l.environment=env AND l.instance_id=inst AND l.state='PURGED')))
$$;
CREATE FUNCTION wa.privacy_history_run_due(target UUID,as_of TIMESTAMPTZ) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.operation_run r JOIN wa.routine_occurrence o ON o.id=r.occurrence_id
      WHERE r.id=target AND r.state IN ('COMPLETED','CANCELED') AND r.completed_at IS NOT NULL
        AND greatest(r.completed_at,o.scheduled_at,r.snapshot_at,r.created_at)<=as_of-INTERVAL '90 days'
        AND wa.privacy_history_origin_consistent(o.id)
        AND wa.privacy_history_subject_scope(r.driver_id,o.history_environment,o.history_instance_id)
        AND NOT EXISTS(SELECT 1 FROM wa.run_stop stop WHERE stop.run_id=r.id AND
          NOT wa.privacy_history_subject_scope(stop.passenger_id,o.history_environment,o.history_instance_id))
        AND NOT EXISTS(SELECT 1 FROM wa.tracking_session session WHERE session.run_id=r.id
          AND session.state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED'))
        AND NOT EXISTS(SELECT 1 FROM wa.start_intent intent WHERE intent.occurrence_id=o.id
          AND intent.state IN ('PENDING','ACTIVATED') AND intent.expires_at>as_of))
$$;
CREATE FUNCTION wa.privacy_history_occurrence_due(target UUID,as_of TIMESTAMPTZ) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT EXISTS(SELECT 1 FROM wa.routine_occurrence o WHERE o.id=target
      AND greatest(o.scheduled_at,o.created_at)<=as_of-INTERVAL '90 days'
      AND wa.privacy_history_origin_consistent(o.id)
      AND wa.privacy_history_subject_scope(o.driver_id,o.history_environment,o.history_instance_id)
      AND NOT EXISTS(SELECT 1 FROM wa.start_intent i WHERE i.occurrence_id=o.id AND i.state IN ('PENDING','ACTIVATED') AND i.expires_at>as_of)
      AND NOT EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.occurrence_id=o.id AND q.state IN ('OPEN','ANSWERED') AND q.answer_until>as_of)
      AND NOT EXISTS(SELECT 1 FROM wa.attendance a WHERE a.routine_id=o.routine_id AND a.local_date=o.local_date)
      AND NOT EXISTS(SELECT 1 FROM wa.attendance_question q WHERE q.occurrence_id=o.id)
      AND NOT EXISTS(SELECT 1 FROM wa.occurrence_roster_override v WHERE v.occurrence_id=o.id)
      AND NOT EXISTS(SELECT 1 FROM wa.operation_run r WHERE r.occurrence_id=o.id AND
        (NOT wa.privacy_history_run_due(r.id,as_of) OR r.history_erased_at IS NULL
          OR EXISTS(SELECT 1 FROM wa.run_stop s WHERE s.run_id=r.id AND s.history_erased_at IS NULL)
          OR EXISTS(SELECT 1 FROM wa.approach_progress p WHERE p.run_id=r.id)
          OR EXISTS(SELECT 1 FROM wa.run_finish_progress p WHERE p.run_id=r.id))))
$$;
CREATE FUNCTION wa.privacy_history_run_snapshot(snapshot JSONB,snapshot_at TIMESTAMPTZ) RETURNS JSONB LANGUAGE SQL IMMUTABLE AS $$
    SELECT CASE WHEN jsonb_typeof(snapshot->'rosterDigest')='string' THEN jsonb_build_object('rosterDigest',snapshot->'rosterDigest')
      WHEN snapshot_at IS NULL AND NOT snapshot?'rosterDigest' THEN '{}'::jsonb ELSE NULL END
$$;
CREATE FUNCTION wa.privacy_history_stop_transition(prior wa.run_stop,next wa.run_stop) RETURNS BOOLEAN LANGUAGE SQL STABLE AS $$
    SELECT coalesce(prior.history_erased_at IS NULL AND next.history_erased_at IS NOT NULL
      AND next.history_erased_at<=transaction_timestamp() AND wa.privacy_history_run_due(prior.run_id,next.history_erased_at)
      AND next.latitude_snapshot IS NULL AND next.longitude_snapshot IS NULL AND next.reference_snapshot IS NULL
      AND next.attendance_snapshot IS NULL AND next.state IS NULL AND next.canceled_at IS NULL
      AND (to_jsonb(next)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','attendance_snapshot','state','canceled_at','history_erased_at'])=
          (to_jsonb(prior)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','attendance_snapshot','state','canceled_at','history_erased_at']),false)
$$;

CREATE OR REPLACE FUNCTION wa.guard_run_binding() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF OLD.history_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical run is immutable'; END IF;
    IF NEW.history_erased_at IS DISTINCT FROM OLD.history_erased_at THEN
      IF OLD.history_erased_at IS NULL AND NEW.history_erased_at IS NOT NULL AND NEW.history_erased_at<=transaction_timestamp()
        AND wa.privacy_history_run_due(OLD.id,NEW.history_erased_at) AND NEW.completion_reason IS NULL
        AND wa.privacy_history_run_snapshot(OLD.snapshot_json,OLD.snapshot_at) IS NOT NULL
        AND NEW.snapshot_json=wa.privacy_history_run_snapshot(OLD.snapshot_json,OLD.snapshot_at)
        AND (to_jsonb(NEW)-ARRAY['snapshot_json','completion_reason','history_erased_at'])=
            (to_jsonb(OLD)-ARRAY['snapshot_json','completion_reason','history_erased_at']) THEN RETURN NEW; END IF;
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='invalid historical run projection';
    END IF;
    IF ROW(NEW.id,NEW.occurrence_id,NEW.routine_id,NEW.driver_id,NEW.local_date)
      IS DISTINCT FROM ROW(OLD.id,OLD.occurrence_id,OLD.routine_id,OLD.driver_id,OLD.local_date) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='run ownership is immutable',CONSTRAINT='ck_run_immutable_owner'; END IF;
    IF OLD.snapshot_at IS NOT NULL AND ROW(NEW.snapshot_json,NEW.snapshot_at,NEW.policy_version)
      IS DISTINCT FROM ROW(OLD.snapshot_json,OLD.snapshot_at,OLD.policy_version) AND NOT (
        OLD.state IN ('COMPLETED','CANCELED') AND NEW.state=OLD.state AND NEW.snapshot_at=OLD.snapshot_at
        AND NEW.policy_version=OLD.policy_version AND NEW.snapshot_json=wa.privacy_project_run(OLD.snapshot_json,OLD.driver_id)
        AND NEW.snapshot_json IS DISTINCT FROM OLD.snapshot_json) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='run snapshot is immutable',CONSTRAINT='ck_run_immutable_snapshot'; END IF;
    IF OLD.state IN ('COMPLETED','CANCELED') AND NEW.state IS DISTINCT FROM OLD.state THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='terminal run cannot reopen',CONSTRAINT='ck_run_terminal'; END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION wa.guard_stop_snapshot() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF wa.privacy_history_stop_transition(OLD,NEW) THEN RETURN NEW; END IF;
    IF OLD.history_erased_at IS NOT NULL THEN
      IF OLD.privacy_erased_at IS NULL AND NEW.privacy_erased_at IS NOT NULL AND wa.privacy_can_erase(OLD.passenger_id)
        AND (to_jsonb(NEW)-'privacy_erased_at')=(to_jsonb(OLD)-'privacy_erased_at') THEN RETURN NEW; END IF;
      IF NEW IS NOT DISTINCT FROM OLD THEN RETURN NEW; END IF;
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical stop is immutable';
    END IF;
    IF NEW.history_erased_at IS NOT NULL THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='invalid historical stop projection'; END IF;
    IF OLD.privacy_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='erased run stop cannot change'; END IF;
    IF (to_jsonb(NEW)-ARRAY['state','canceled_at','version']) IS DISTINCT FROM
       (to_jsonb(OLD)-ARRAY['state','canceled_at','version']) AND NOT (
        OLD.privacy_erased_at IS NULL AND NEW.privacy_erased_at IS NOT NULL AND NEW.latitude_snapshot IS NULL
        AND NEW.longitude_snapshot IS NULL AND NEW.reference_snapshot IS NULL
        AND (to_jsonb(NEW)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','privacy_erased_at'])=
            (to_jsonb(OLD)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','privacy_erased_at'])
        AND wa.privacy_can_erase(OLD.passenger_id)
        AND EXISTS(SELECT 1 FROM wa.operation_run r WHERE r.id=OLD.run_id AND r.state IN ('COMPLETED','CANCELED'))) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='run stop snapshot is immutable',CONSTRAINT='ck_stop_immutable_snapshot'; END IF;
    RETURN NEW;
END $$;
CREATE OR REPLACE FUNCTION wa.guard_stop_progress() RETURNS TRIGGER LANGUAGE plpgsql AS $$
BEGIN
    IF wa.privacy_history_stop_transition(OLD,NEW) THEN RETURN NEW; END IF;
    IF OLD.history_erased_at IS NOT NULL AND NEW.state IS DISTINCT FROM OLD.state
      OR OLD.history_erased_at IS NULL AND NEW.history_erased_at IS NOT NULL
      OR OLD.state IN ('PASSED','SKIPPED','CANCELED') AND NEW.state IS DISTINCT FROM OLD.state
      OR OLD.state='APPROACHING' AND NEW.state='WAITING' THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='stop progress cannot regress'; END IF;
    RETURN NEW;
END $$;

CREATE OR REPLACE FUNCTION wa.guard_occurrence_privacy() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE fields TEXT[]=ARRAY['history_environment','history_instance_id','history_origin_kind','history_origin_id','history_origin_bound_at'];
BEGIN
    IF TG_OP='INSERT' THEN
      IF NEW.history_erased_at IS NOT NULL THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='cannot insert erased history'; END IF;
      IF NEW.history_origin_bound_at IS NOT NULL AND NOT (
        NEW.history_origin_kind='SYSTEM_JOB' AND NEW.history_origin_bound_at=transaction_timestamp()
        AND EXISTS(SELECT 1 FROM wa.identity i JOIN wa.test_cohort cohort ON cohort.id=i.test_cohort_id
          WHERE i.id=NEW.driver_id AND cohort.environment=NEW.history_environment)
        AND EXISTS(SELECT 1 FROM wa.service_credential c WHERE c.id=NEW.history_origin_id AND c.principal='SYSTEM_JOB'
          AND c.environment=NEW.history_environment AND c.allowed_instance_id=NEW.history_instance_id
          AND c.revoked_at IS NULL AND c.expires_at>transaction_timestamp())) THEN
        RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='invalid historical origin insert'; END IF;
      RETURN NEW;
    END IF;
    IF OLD.history_origin_bound_at IS NOT NULL AND
      ROW(NEW.history_environment,NEW.history_instance_id,NEW.history_origin_kind,NEW.history_origin_id,NEW.history_origin_bound_at)
      IS DISTINCT FROM ROW(OLD.history_environment,OLD.history_instance_id,OLD.history_origin_kind,OLD.history_origin_id,OLD.history_origin_bound_at) THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical origin is immutable'; END IF;
    IF OLD.history_origin_bound_at IS NULL AND NEW.history_origin_bound_at IS NOT NULL THEN
      IF (to_jsonb(NEW)-fields)=(to_jsonb(OLD)-fields) AND NEW.history_origin_bound_at=transaction_timestamp()
        AND wa.privacy_history_subject_scope(OLD.driver_id,NEW.history_environment,NEW.history_instance_id)
        AND EXISTS(SELECT 1 FROM wa.privacy_history_sources(OLD.id) s WHERE s.kind=NEW.history_origin_kind AND s.origin_id=NEW.history_origin_id
          AND s.environment=NEW.history_environment AND s.instance_id=NEW.history_instance_id)
        AND NOT EXISTS(SELECT 1 FROM wa.privacy_history_sources(OLD.id) s WHERE
          s.environment IS DISTINCT FROM NEW.history_environment OR s.instance_id IS DISTINCT FROM NEW.history_instance_id) THEN RETURN NEW; END IF;
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical origin requires immutable evidence';
    END IF;
    IF OLD.history_erased_at IS NOT NULL AND NEW IS DISTINCT FROM OLD THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical occurrence is immutable'; END IF;
    IF NEW.history_erased_at IS DISTINCT FROM OLD.history_erased_at THEN
      IF OLD.history_erased_at IS NULL AND NEW.history_erased_at IS NOT NULL AND NEW.history_erased_at<=transaction_timestamp()
        AND wa.privacy_history_occurrence_due(OLD.id,NEW.history_erased_at)
        AND NEW.schedule_snapshot IS NOT DISTINCT FROM (CASE WHEN OLD.schedule_snapshot IS NULL THEN NULL ELSE '{}'::jsonb END)
        AND (to_jsonb(NEW)-ARRAY['schedule_snapshot','history_erased_at'])=(to_jsonb(OLD)-ARRAY['schedule_snapshot','history_erased_at']) THEN RETURN NEW; END IF;
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='invalid historical occurrence projection';
    END IF;
    IF wa.privacy_can_erase(OLD.driver_id) AND NEW.schedule_snapshot?'name' THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='erased routine label cannot return'; END IF;
    RETURN NEW;
END $$;
DROP TRIGGER trg_occurrence_privacy ON wa.routine_occurrence;
CREATE TRIGGER trg_occurrence_privacy BEFORE INSERT OR UPDATE ON wa.routine_occurrence
  FOR EACH ROW EXECUTE FUNCTION wa.guard_occurrence_privacy();

CREATE FUNCTION wa.guard_history_fact() RETURNS TRIGGER LANGUAGE plpgsql AS $$
DECLARE occ UUID; old_occ UUID; marked BOOLEAN; old_marked BOOLEAN; old_row JSONB; new_row JSONB=to_jsonb(NEW);
BEGIN
    IF TG_OP='INSERT' AND new_row->>'history_erased_at' IS NOT NULL THEN
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='cannot insert erased history'; END IF;
    IF TG_TABLE_NAME='attendance' THEN
      SELECT id INTO occ FROM wa.routine_occurrence WHERE routine_id=NEW.routine_id AND local_date=NEW.local_date;
    ELSIF TG_TABLE_NAME IN ('run_stop','approach_progress','run_finish_progress') THEN SELECT occurrence_id INTO occ FROM wa.operation_run WHERE id=NEW.run_id;
    ELSE occ=(new_row->>'occurrence_id')::uuid; END IF;
    SELECT history_erased_at IS NOT NULL INTO marked FROM wa.routine_occurrence WHERE id=occ FOR SHARE;
    IF TG_OP='UPDATE' THEN
      old_row=to_jsonb(OLD);
      IF TG_TABLE_NAME='attendance' THEN SELECT id INTO old_occ FROM wa.routine_occurrence
        WHERE routine_id=(old_row->>'routine_id')::uuid AND local_date=(old_row->>'local_date')::date;
      ELSIF TG_TABLE_NAME IN ('run_stop','approach_progress','run_finish_progress') THEN
        SELECT occurrence_id INTO old_occ FROM wa.operation_run WHERE id=(old_row->>'run_id')::uuid;
      ELSE old_occ=(old_row->>'occurrence_id')::uuid; END IF;
      IF old_occ IS DISTINCT FROM occ THEN SELECT history_erased_at IS NOT NULL INTO old_marked FROM wa.routine_occurrence WHERE id=old_occ FOR SHARE;
        marked=coalesce(marked,false) OR coalesce(old_marked,false); END IF;
    END IF;
    IF TG_TABLE_NAME IN ('run_stop','approach_progress','run_finish_progress') THEN marked=coalesce(marked,false) OR EXISTS(
      SELECT 1 FROM wa.operation_run r WHERE r.id IN ((new_row->>'run_id')::uuid,(old_row->>'run_id')::uuid)
        AND r.history_erased_at IS NOT NULL); END IF;
    IF coalesce(marked,false) THEN
      IF TG_OP='UPDATE' THEN
        old_row=to_jsonb(OLD);
        IF TG_TABLE_NAME='run_stop' THEN
          IF wa.privacy_history_stop_transition(jsonb_populate_record(NULL::wa.run_stop,old_row),jsonb_populate_record(NULL::wa.run_stop,new_row)) THEN RETURN NEW; END IF;
        END IF;
        IF new_row=old_row THEN RETURN NEW; END IF;
        IF TG_TABLE_NAME='run_stop' AND old_row->>'privacy_erased_at' IS NULL AND new_row->>'privacy_erased_at' IS NOT NULL
          AND (new_row-'privacy_erased_at')=(old_row-'privacy_erased_at')
          AND wa.privacy_can_erase((old_row->>'passenger_id')::uuid) THEN RETURN NEW; END IF;
      END IF;
      RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='historical occurrence cannot gain personal facts';
    END IF;
    RETURN NEW;
END $$;
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.attendance FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.attendance_question FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.occurrence_roster_override FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.operation_run FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.run_stop FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();

CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.approach_progress FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
CREATE TRIGGER trg_history_fact BEFORE INSERT OR UPDATE ON wa.run_finish_progress FOR EACH ROW EXECUTE FUNCTION wa.guard_history_fact();
