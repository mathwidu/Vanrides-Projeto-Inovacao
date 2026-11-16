package com.vanrides.vanrides.whatsapp.privacy;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Explicit copy inventory; no identity deletion, cascades, fake names or fake coordinates. */
final class WaPrivacyCopies {
    private final JdbcTemplate jdbc;
    WaPrivacyCopies(JdbcTemplate jdbc) { this.jdbc = jdbc; }

    Map<String, Integer> erase(UUID subject, Instant now) {
        Timestamp time = Timestamp.from(now);
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("RUN_SNAPSHOTS", jdbc.update("""
            UPDATE wa.operation_run SET snapshot_json=wa.privacy_project_run(snapshot_json,driver_id)
            WHERE state IN ('COMPLETED','CANCELED') AND snapshot_json<>wa.privacy_project_run(snapshot_json,driver_id)
            """));
        counts.put("RUN_STOPS", jdbc.update("""
            UPDATE wa.run_stop stop SET latitude_snapshot=NULL,longitude_snapshot=NULL,reference_snapshot=NULL,privacy_erased_at=?
            FROM wa.operation_run run WHERE run.id=stop.run_id AND run.state IN ('COMPLETED','CANCELED')
              AND stop.passenger_id=? AND stop.privacy_erased_at IS NULL
            """, time, subject));
        counts.put("ROSTER_REASONS", jdbc.update("""
            UPDATE wa.occurrence_roster_override SET reason=NULL,privacy_erased_at=?
            WHERE passenger_id=? AND privacy_erased_at IS NULL
              AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=occurrence_roster_override.occurrence_id
                AND run.state NOT IN ('COMPLETED','CANCELED'))
            """, time, subject));
        counts.put("PICKUP_POINTS", jdbc.update("""
            UPDATE wa.pickup_point SET latitude=NULL,longitude=NULL,label=NULL,reference_text=NULL,privacy_erased_at=?
            WHERE passenger_id=? AND privacy_erased_at IS NULL
            """, time, subject));
        counts.put("DRIVER_ROUTINES", jdbc.update("""
            UPDATE wa.routine SET name=NULL,destination_label=NULL,destination_latitude=NULL,destination_longitude=NULL,privacy_erased_at=?
            WHERE driver_id=? AND privacy_erased_at IS NULL
            """, time, subject));
        counts.put("COMMON_POINTS", jdbc.update("""
            UPDATE wa.common_pickup_point SET name=NULL,latitude=NULL,longitude=NULL,privacy_erased_at=?
            WHERE driver_id=? AND privacy_erased_at IS NULL
            """, time, subject));
        counts.put("SCHEDULE_LABELS", jdbc.update("""
            UPDATE wa.routine_occurrence SET schedule_snapshot=schedule_snapshot-'name'
            WHERE driver_id=? AND jsonb_exists(schedule_snapshot,'name')
            """, subject));
        counts.put("QUESTION_LABELS", jdbc.update("""
            UPDATE wa.attendance_question SET routine_name=NULL,privacy_erased_at=?
            WHERE driver_id=? AND privacy_erased_at IS NULL
            """, time, subject));
        counts.put("ALIASES", jdbc.update("""
            UPDATE wa.identity_alias SET alias_value=NULL,verified_phone=NULL,privacy_erased_at=?
            WHERE identity_id=? AND privacy_erased_at IS NULL
            """, time, subject));
        counts.put("INBOX_CONTENT", jdbc.update("""
            UPDATE wa.integration_inbox SET payload_json=jsonb_strip_nulls(jsonb_build_object('senderAliasId',payload_json->'senderAliasId'))
            WHERE identity_id=? AND payload_json<>jsonb_strip_nulls(jsonb_build_object('senderAliasId',payload_json->'senderAliasId'))
            """, subject));
        counts.put("OUTBOX_CONTENT", messages(subject));
        counts.put("CONVERSATION_CONTEXT", contexts(subject));
        counts.put("PROFILE", jdbc.update("""
            UPDATE wa.profile SET display_name=NULL,preferences_json='{}',privacy_erased_at=?,updated_at=?,version=version+1
            WHERE identity_id=? AND privacy_erased_at IS NULL
            """, time, time, subject));
        return Map.copyOf(counts);
    }

    private int messages(UUID subject) {
        return jdbc.update("""
            WITH subject AS (SELECT ?::uuid id), related_runs AS (
              SELECT DISTINCT run.id,run.occurrence_id,run.driver_id FROM wa.operation_run run
              LEFT JOIN wa.run_stop stop ON stop.run_id=run.id
              WHERE run.state IN ('COMPLETED','CANCELED') AND
                (run.driver_id=(SELECT id FROM subject) OR stop.passenger_id=(SELECT id FROM subject)))
            UPDATE wa.message_outbox box SET payload_json='{}',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,
              state=CASE WHEN box.state IN ('PENDING','LEASED') THEN 'CANCELED' ELSE box.state END,version=version+1
            WHERE (box.payload_json<>'{}' OR box.private_payload_ciphertext IS NOT NULL OR box.start_payload_ciphertext IS NOT NULL)
              AND (box.recipient_identity_id=(SELECT id FROM subject)
                OR EXISTS(SELECT 1 FROM wa.command_receipt command WHERE command.command_id=box.command_id
                  AND command.identity_id=(SELECT id FROM subject))
                OR EXISTS(SELECT 1 FROM wa.passenger_response_route route WHERE route.outbox_id=box.id
                  AND (route.passenger_id=(SELECT id FROM subject) OR route.driver_id=(SELECT id FROM subject)))
                OR EXISTS(SELECT 1 FROM wa.driver_attendance_notice notice WHERE notice.outbox_id=box.id
                  AND (notice.passenger_id=(SELECT id FROM subject) OR notice.driver_id=(SELECT id FROM subject)))
                OR EXISTS(SELECT 1 FROM related_runs run WHERE box.recipient_identity_id=run.driver_id
                  AND (box.run_id=run.id OR box.occurrence_id=run.occurrence_id OR box.policy_version='attendance-v1')))
            """, subject);
    }

    private int contexts(UUID subject) {
        int own = jdbc.update("UPDATE wa.conversation_state SET context_json='{}',version=version+1 WHERE identity_id=? AND context_json<>'{}'", subject);
        return own + jdbc.update("""
            WITH copy_refs AS (
              SELECT ?::uuid::text value UNION SELECT id::text FROM wa.driver_passenger_link WHERE passenger_id=?
              UNION SELECT id::text FROM wa.pickup_point WHERE passenger_id=?
              UNION SELECT run_id::text FROM wa.run_stop WHERE passenger_id=?
              UNION SELECT run.occurrence_id::text FROM wa.operation_run run JOIN wa.run_stop stop ON stop.run_id=run.id
                WHERE stop.passenger_id=?)
            UPDATE wa.conversation_state conversation SET
              context_json=context_json-ARRAY['passengerDraft','attendanceDraft','runDraft','driverDraft'],version=version+1
            WHERE identity_id<>? AND EXISTS(SELECT 1 FROM copy_refs
              WHERE strpos(conversation.context_json::text,copy_refs.value)>0)
              AND context_json IS DISTINCT FROM context_json-ARRAY['passengerDraft','attendanceDraft','runDraft','driverDraft']
            """, subject, subject, subject, subject, subject, subject);
    }
}
