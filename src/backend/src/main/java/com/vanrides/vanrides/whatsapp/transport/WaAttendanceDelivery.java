package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceCalendar;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Reminder eligibility is checked at reservation, while its origin survives later changes. */
final class WaAttendanceDelivery {
    static final String POLICY = "attendance-reminder-v1";
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaTransportAccess access;
    private final WaClock clock;

    WaAttendanceDelivery(JdbcTemplate jdbc, WaSettings settings, WaTransportAccess access, WaClock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.access = access;
        this.clock = clock;
    }

    boolean current(UUID outbox, String instance, boolean lock) {
        Map<String, Object> question = question(outbox, instance);
        if (question == null) return false;
        if (lock) {
            lockSubjects(question);
            question = question(outbox, instance);
            if (question == null) return false;
        }
        UUID passenger = (UUID) question.get("passenger_id");
        if (!access.allowedIdentity(passenger) || !access.allowedIdentity((UUID) question.get("driver_id"))) return false;
        String schedule = WaAttendanceCalendar.scheduleHash(jdbc, (UUID) question.get("routine_id"));
        if (schedule == null || !schedule.equals(question.get("schedule_hash"))) return false;
        Boolean eligible = jdbc.queryForObject("""
            SELECT EXISTS(
              SELECT 1 FROM wa.attendance_question q
              JOIN wa.attendance a ON a.id=q.attendance_id AND a.routine_id=q.routine_id
                AND a.passenger_id=q.passenger_id AND a.local_date=q.local_date
              JOIN wa.routine_occurrence occurrence ON occurrence.id=q.occurrence_id
                AND occurrence.routine_id=q.routine_id AND occurrence.driver_id=q.driver_id
                AND occurrence.local_date=q.local_date
              JOIN wa.routine r ON r.id=q.routine_id AND r.driver_id=q.driver_id
              JOIN wa.routine_member m ON m.id=q.member_id AND m.routine_id=q.routine_id
                AND m.driver_id=q.driver_id AND m.passenger_id=q.passenger_id
                AND m.link_id=q.link_id AND m.pickup_point_id=q.pickup_point_id
              JOIN wa.driver_passenger_link link ON link.id=q.link_id
                AND link.driver_id=q.driver_id AND link.passenger_id=q.passenger_id
              JOIN wa.pickup_point point ON point.id=q.pickup_point_id
                AND point.link_id=q.link_id AND point.driver_id=q.driver_id AND point.passenger_id=q.passenger_id
              JOIN wa.profile passenger ON passenger.identity_id=q.passenger_id
              JOIN wa.profile driver ON driver.identity_id=q.driver_id
              JOIN wa.identity pi ON pi.id=q.passenger_id
              JOIN wa.identity di ON di.id=q.driver_id
              LEFT JOIN wa.test_cohort cohort ON cohort.id=pi.test_cohort_id
              WHERE q.id=? AND q.outbox_id=? AND q.instance_id=? AND q.environment=?
                AND q.state='OPEN' AND a.state='PENDING' AND a.question_ref=q.id
                AND q.opens_at<=? AND q.reminder_at<=? AND q.send_until>? AND q.answer_until>?
                AND occurrence.state='PLANNED' AND occurrence.calendar_eligible AND r.state='ACTIVE' AND m.state='ACTIVE'
                AND link.state='APPROVED' AND point.state='APPROVED'
                AND m.version=q.member_version AND link.version=q.link_version AND point.version=q.pickup_version
                AND extract(isodow FROM q.local_date)::smallint=ANY(r.weekdays)
                AND extract(isodow FROM q.local_date)::smallint=ANY(m.weekdays)
                AND link.valid_from<=? AND (link.valid_until IS NULL OR link.valid_until>?)
                AND point.valid_from<=? AND (point.valid_until IS NULL OR point.valid_until>?)
                AND passenger.role='PASSENGER' AND passenger.status='ACTIVE' AND passenger.notice_version=?
                AND passenger.preferences_json->>'ATTENDANCE_REMINDERS'='true'
                AND driver.role='DRIVER' AND driver.status='ACTIVE' AND driver.driver_enabled AND driver.notice_version=?
                AND ((pi.test_cohort_id IS NULL AND di.test_cohort_id IS NULL AND ?=false)
                  OR (pi.test_cohort_id=di.test_cohort_id AND cohort.environment=?))
                AND NOT EXISTS(SELECT 1 FROM wa.conversation_state c WHERE c.identity_id=q.passenger_id AND c.stage='PAUSED')
                AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=q.occurrence_id AND run.state<>'PLANNED')
            )
            """, Boolean.class, question.get("id"), outbox, instance, settings.environment(),
            now(), now(), now(), now(), now(), now(), now(), now(),
            WaOnboardingCommands.NOTICE_VERSION, WaOnboardingCommands.NOTICE_VERSION,
            settings.cohortOnly(), settings.environment());
        return Boolean.TRUE.equals(eligible) && aliases(outbox, instance, lock).size() == 1;
    }

    List<Map<String, Object>> aliases(UUID outbox, String instance) {
        return aliases(outbox, instance, true);
    }

    boolean actorCurrent(UUID actor, boolean lock) {
        if (lock) jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE", actor);
        var stages = jdbc.query("SELECT stage FROM wa.conversation_state WHERE identity_id=?" + (lock ? " FOR SHARE" : ""),
                (rs, row) -> rs.getString(1), actor);
        return access.allowedIdentity(actor) && (stages.isEmpty() || !stages.get(0).equals("PAUSED"));
    }

    private List<Map<String, Object>> aliases(UUID outbox, String instance, boolean lock) {
        return jdbc.queryForList("""
            SELECT alias.alias_type,alias.alias_value FROM wa.attendance_question q
            JOIN wa.integration_inbox source ON source.id=q.source_inbox_event_id
              AND source.identity_id=q.passenger_id AND source.provider='EVOLUTION'
              AND source.instance_id=q.instance_id AND source.category='MESSAGE'
            JOIN wa.identity_alias alias ON alias.id=q.sender_alias_id
              AND alias.id::text=source.payload_json->>'senderAliasId'
              AND alias.identity_id=q.passenger_id AND alias.provider=source.provider AND alias.instance_id=q.instance_id
            WHERE q.outbox_id=? AND q.instance_id=? AND q.environment=? AND alias.alias_type IN ('PN','LID')
            """ + (lock ? " FOR SHARE OF alias" : ""), outbox, instance, settings.environment());
    }

    Instant deadline(UUID outbox) {
        return jdbc.query("""
                SELECT LEAST(q.send_until,q.answer_until,
                    COALESCE(link.valid_until,q.send_until),COALESCE(point.valid_until,q.send_until))
                FROM wa.attendance_question q JOIN wa.driver_passenger_link link ON link.id=q.link_id
                JOIN wa.pickup_point point ON point.id=q.pickup_point_id
                WHERE q.outbox_id=? AND q.environment=?
                """,
                (rs, row) -> rs.getTimestamp(1).toInstant(), outbox, settings.environment())
                .stream().findFirst().orElse(Instant.MIN);
    }

    private Map<String, Object> question(UUID outbox, String instance) {
        var rows = jdbc.queryForList("SELECT * FROM wa.attendance_question WHERE outbox_id=? AND instance_id=? AND environment=?",
                outbox, instance, settings.environment());
        return rows.size() == 1 ? rows.get(0) : null;
    }

    private void lockSubjects(Map<String, Object> question) {
        jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id IN (?,?) ORDER BY identity_id FOR SHARE",
                question.get("driver_id"), question.get("passenger_id"));
        jdbc.queryForList("SELECT identity_id FROM wa.conversation_state WHERE identity_id=? FOR SHARE", question.get("passenger_id"));
        lock("wa.routine", question.get("routine_id"));
        lock("wa.routine_occurrence", question.get("occurrence_id"));
        lock("wa.routine_member", question.get("member_id"));
        lock("wa.driver_passenger_link", question.get("link_id"));
        lock("wa.pickup_point", question.get("pickup_point_id"));
        lock("wa.attendance", question.get("attendance_id"));
        lock("wa.attendance_question", question.get("id"));
    }

    private void lock(String table, Object id) {
        jdbc.queryForList("SELECT id FROM " + table + " WHERE id=? FOR SHARE", id);
    }

    private Timestamp now() {
        return Timestamp.from(clock.now());
    }
}
