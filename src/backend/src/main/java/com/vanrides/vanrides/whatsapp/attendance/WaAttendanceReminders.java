package com.vanrides.vanrides.whatsapp.attendance;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Date;
import java.sql.Time;
import java.sql.Timestamp;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

final class WaAttendanceReminders {
    static final String POLICY = "attendance-reminder-v1";
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaAttendanceScope scope;

    WaAttendanceReminders(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc;
        this.settings = settings;
        this.clock = clock;
        scope = new WaAttendanceScope(jdbc, settings);
    }

    List<Map<String, Object>> candidates(String instance, String after, int limit) {
        return jdbc.queryForList("""
                SELECT q.id,q.id::text work_key,q.routine_id FROM wa.attendance_question q
                JOIN wa.attendance a ON a.id=q.attendance_id AND a.question_ref=q.id AND a.state='PENDING'
                JOIN wa.routine r ON r.id=q.routine_id AND r.state='ACTIVE'
                JOIN wa.routine_occurrence o ON o.id=q.occurrence_id AND o.state='PLANNED' AND o.calendar_eligible
                JOIN wa.profile profile ON profile.identity_id=q.passenger_id AND profile.status='ACTIVE'
                JOIN wa.profile driver ON driver.identity_id=q.driver_id AND driver.status='ACTIVE' AND driver.driver_enabled
                JOIN wa.identity di ON di.id=q.driver_id JOIN wa.identity pi ON pi.id=q.passenger_id
                LEFT JOIN wa.test_cohort cohort ON cohort.id=pi.test_cohort_id
                JOIN wa.routine_member m ON m.id=q.member_id AND m.state='ACTIVE' AND m.version=q.member_version
                JOIN wa.driver_passenger_link l ON l.id=q.link_id AND l.state='APPROVED' AND l.version=q.link_version
                JOIN wa.pickup_point p ON p.id=q.pickup_point_id AND p.state='APPROVED' AND p.version=q.pickup_version
                WHERE q.environment=? AND q.instance_id=? AND q.state='OPEN' AND q.outbox_id IS NULL
                  AND q.reminder_at<=? AND q.send_until>? AND profile.preferences_json->>'ATTENDANCE_REMINDERS'='true'
                  AND extract(isodow FROM q.local_date)=ANY(r.weekdays)
                  AND extract(isodow FROM q.local_date)=ANY(m.weekdays)
                  AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
                  AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
                  AND ((di.test_cohort_id=pi.test_cohort_id AND cohort.environment=?)
                    OR (?=false AND di.test_cohort_id IS NULL AND pi.test_cohort_id IS NULL))
                  AND EXISTS(SELECT 1 FROM wa.integration_inbox source JOIN wa.identity_alias alias
                    ON alias.id::text=source.payload_json->>'senderAliasId' AND alias.identity_id=source.identity_id
                    WHERE source.identity_id=q.passenger_id AND source.instance_id=q.instance_id
                      AND source.provider='EVOLUTION' AND source.category='MESSAGE'
                      AND alias.instance_id=source.instance_id AND alias.provider=source.provider
                      AND alias.alias_type IN ('PN','LID'))
                  AND NOT EXISTS(SELECT 1 FROM wa.conversation_state paused
                    WHERE paused.identity_id=q.passenger_id AND paused.stage='PAUSED')
                  AND q.schedule_hash=
                """ + WaAttendanceCandidates.HASH + """
                  AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=o.id AND run.state<>'PLANNED')
                  AND NOT EXISTS(SELECT 1 FROM wa.message_outbox box WHERE box.purpose='REMINDER'
                    AND box.occurrence_id=q.occurrence_id AND box.recipient_identity_id=q.passenger_id
                    AND box.reminder_window='previous-day')
                  AND q.id::text>? ORDER BY q.id LIMIT ?
                """, settings.environment(), instance, Timestamp.from(clock.now()), Timestamp.from(clock.now()),
                Timestamp.from(clock.now()), Timestamp.from(clock.now()), Timestamp.from(clock.now()), Timestamp.from(clock.now()),
                settings.environment(), settings.cohortOnly(), after, limit);
    }

    void apply(Map<String, Object> work, String instance) {
        UUID question = (UUID) work.get("id");
        UUID routine = (UUID) work.get("routine_id");
        var routines = jdbc.queryForList("SELECT * FROM wa.routine WHERE id=? FOR UPDATE", routine);
        if (routines.size() != 1) {
            return;
        }
        Map<String, Object> row = routines.get(0);
        var questions = jdbc.queryForList("SELECT * FROM wa.attendance_question WHERE id=?", question);
        if (questions.size() != 1) {
            return;
        }
        Map<String, Object> candidate = questions.get(0);
        UUID occurrence = (UUID) candidate.get("occurrence_id");
        jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE", occurrence);
        jdbc.queryForList("SELECT id FROM wa.attendance WHERE id=? FOR UPDATE", candidate.get("attendance_id"));
        Map<String, Object> q = jdbc.queryForMap("SELECT * FROM wa.attendance_question WHERE id=? FOR UPDATE", question);
        UUID driver = (UUID) q.get("driver_id");
        UUID passenger = (UUID) q.get("passenger_id");
        if (!scope.driver(driver, instance) || !scope.pair(driver, passenger) || !eligible(q, row, instance)) {
            return;
        }
        var origins = jdbc.queryForList("""
                SELECT e.id source_inbox_event_id,alias.id sender_alias_id FROM wa.integration_inbox e
                JOIN wa.identity_alias alias ON alias.id::text=e.payload_json->>'senderAliasId'
                  AND alias.identity_id=e.identity_id AND alias.provider=e.provider AND alias.instance_id=e.instance_id
                WHERE e.identity_id=? AND e.provider='EVOLUTION' AND e.instance_id=? AND e.category='MESSAGE'
                  AND alias.alias_type IN ('PN','LID') ORDER BY e.created_at DESC,e.id DESC LIMIT 1
                """, passenger, instance);
        if (origins.size() != 1) {
            return;
        }
        Timestamp now = Timestamp.from(clock.now());
        String text = "Para " + row.get("name") + " (" + (row.get("direction").equals("OUTBOUND") ? "ida" : "volta")
                + "), dia " + ((Date) q.get("local_date")).toLocalDate().format(DateTimeFormatter.ofPattern("dd/MM/yyyy"))
                + " às " + ((Time) row.get("local_time")).toLocalTime().format(DateTimeFormatter.ofPattern("HH:mm"))
                + ", você vai? Responda VOU ou NÃO VOU. Se não responder, permanece previsto como pendente.";
        UUID outbox = UUID.randomUUID();
        int inserted = jdbc.update("""
                INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,occurrence_id,reminder_window,
                  policy_version,payload_kind,payload_json,not_before,expires_at,created_at)
                VALUES (?,?,'REMINDER',?,'previous-day',?,'TEXT',?::jsonb,?,?,?)
                ON CONFLICT DO NOTHING
                """, outbox, passenger, occurrence, POLICY,
                WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", clock.now().toString())),
                now, q.get("send_until"), now);
        if (inserted == 1) {
            Map<String, Object> origin = origins.get(0);
            jdbc.update("""
                    UPDATE wa.attendance_question SET outbox_id=?,source_inbox_event_id=?,sender_alias_id=?,version=version+1
                    WHERE id=?
                    """, outbox, origin.get("source_inbox_event_id"), origin.get("sender_alias_id"), question);
        }
    }

    private boolean eligible(Map<String, Object> q, Map<String, Object> routine, String instance) {
        if (!q.get("state").equals("OPEN") || q.get("outbox_id") != null || !q.get("instance_id").equals(instance)
                || !q.get("environment").equals(settings.environment()) || !routine.get("state").equals("ACTIVE")
                || ((Timestamp) q.get("reminder_at")).toInstant().isAfter(clock.now())
                || !((Timestamp) q.get("send_until")).toInstant().isAfter(clock.now())
                || !q.get("schedule_hash").equals(WaAttendanceCalendar.scheduleHash(jdbc, (UUID) routine.get("id")))) {
            return false;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM wa.attendance a JOIN wa.routine_occurrence o
                  ON o.routine_id=a.routine_id AND o.local_date=a.local_date
                  JOIN wa.routine_member m ON m.id=? AND m.state='ACTIVE' AND m.version=?
                  JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.state='APPROVED' AND l.version=?
                  JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.state='APPROVED' AND p.version=?
                  JOIN wa.profile profile ON profile.identity_id=a.passenger_id
                  WHERE a.id=? AND a.state='PENDING' AND a.question_ref=? AND o.state='PLANNED' AND o.calendar_eligible
                    AND extract(isodow FROM o.local_date)=ANY(m.weekdays)
                    AND profile.status='ACTIVE' AND profile.preferences_json->>'ATTENDANCE_REMINDERS'='true'
                    AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
                    AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
                    AND NOT EXISTS(SELECT 1 FROM wa.conversation_state c
                      WHERE c.identity_id=a.passenger_id AND c.stage='PAUSED')
                    AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=o.id AND run.state<>'PLANNED'))
                """, Boolean.class, q.get("member_id"), q.get("member_version"), q.get("link_version"),
                q.get("pickup_version"), q.get("attendance_id"), q.get("id"), Timestamp.from(clock.now()),
                Timestamp.from(clock.now()), Timestamp.from(clock.now()), Timestamp.from(clock.now())));
    }
}
