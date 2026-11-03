package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceCalendar;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.transport.WaAttendanceQuotes;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Owner-scoped question catalogue, with the original quote kept authoritative. */
final class WaAttendanceQuestions {
    private static final String CATALOGUE = """
        SELECT (to_jsonb(q)||jsonb_build_object('routineName',q.routine_name,'direction',q.direction,
          'time',q.local_time::text,'timezone',q.timezone,'attendanceState',a.state,
          'attendanceVersion',a.version,'routineVersion',r.version,'memberVersion',m.version,
          'linkVersion',l.version,'pointVersion',p.version,'occurrenceVersion',o.version,
          'runStarted',EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=q.occurrence_id
             AND run.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')),
          'sent',EXISTS(SELECT 1 FROM wa.message_attempt attempt WHERE attempt.outbox_id=q.outbox_id
             AND attempt.external_message_id IS NOT NULL AND attempt.state IN ('SUBMITTED','DELIVERED','READ'))))::text
        FROM wa.attendance_question q
        JOIN wa.attendance a ON a.id=q.attendance_id AND a.routine_id=q.routine_id
          AND a.passenger_id=q.passenger_id AND a.local_date=q.local_date AND a.question_ref=q.id
        JOIN wa.routine r ON r.id=q.routine_id AND r.driver_id=q.driver_id
        JOIN wa.routine_occurrence o ON o.id=q.occurrence_id AND o.routine_id=q.routine_id
          AND o.local_date=q.local_date AND o.driver_id=q.driver_id AND o.state='PLANNED'
        JOIN wa.routine_member m ON m.id=q.member_id AND m.routine_id=q.routine_id
          AND m.driver_id=q.driver_id AND m.passenger_id=q.passenger_id AND m.link_id=q.link_id
        JOIN wa.driver_passenger_link l ON l.id=q.link_id AND l.driver_id=q.driver_id
          AND l.passenger_id=q.passenger_id
        JOIN wa.pickup_point p ON p.id=q.pickup_point_id AND p.driver_id=q.driver_id
          AND p.passenger_id=q.passenger_id AND p.link_id=q.link_id
        JOIN wa.profile passenger ON passenger.identity_id=q.passenger_id AND passenger.role='PASSENGER'
          AND passenger.status='ACTIVE' AND passenger.notice_version='wa-mvp-v1'
        JOIN wa.profile driver ON driver.identity_id=q.driver_id AND driver.role='DRIVER'
          AND driver.status='ACTIVE' AND driver.driver_enabled AND driver.notice_version='wa-mvp-v1'
        JOIN wa.identity pi ON pi.id=q.passenger_id JOIN wa.identity di ON di.id=q.driver_id
        LEFT JOIN wa.test_cohort cohort ON cohort.id=pi.test_cohort_id
        WHERE q.environment=? AND q.instance_id=? AND q.passenger_id=?
          AND q.state IN ('OPEN','ANSWERED') AND q.opens_at<=? AND q.answer_until>?
          AND (EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=q.occurrence_id
                AND run.state IN ('STARTING','START_FAILED','TRACKING','DEGRADED'))
            OR (o.calendar_eligible AND r.state='ACTIVE' AND m.state='ACTIVE' AND l.state='APPROVED' AND p.state='APPROVED'
              AND m.pickup_point_id=q.pickup_point_id AND m.version=q.member_version
              AND l.version=q.link_version AND p.version=q.pickup_version
              AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
              AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
              AND extract(isodow FROM q.local_date)::smallint=ANY(r.weekdays)
              AND extract(isodow FROM q.local_date)::smallint=ANY(m.weekdays)))
          AND ((pi.test_cohort_id IS NOT NULL AND pi.test_cohort_id=di.test_cohort_id AND cohort.environment=?)
            OR (pi.test_cohort_id IS NULL AND di.test_cohort_id IS NULL AND ?=false))
          AND NOT EXISTS(SELECT 1 FROM wa.operation_run run WHERE run.occurrence_id=q.occurrence_id
            AND run.state IN ('COMPLETED','CANCELED'))
        ORDER BY q.local_date,o.scheduled_at,q.id LIMIT 101
        """;
    final JdbcTemplate jdbc;
    final WaClock clock;
    final WaSettings settings;

    WaAttendanceQuestions(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc; this.clock = clock; this.settings = settings;
    }

    String instance(WaInboxAccess.Context event) {
        return jdbc.queryForObject("SELECT instance_id FROM wa.integration_inbox WHERE id=? AND identity_id=?",
            String.class, event.eventId(), event.identityId());
    }

    ArrayNode catalogue(UUID passenger, String instance) {
        Timestamp now = Timestamp.from(clock.now());
        ArrayNode result = WaJson.MAPPER.createArrayNode();
        jdbc.query(CATALOGUE, (rs, row) -> WaJson.object(rs.getString(1)), settings.environment(), instance,
            passenger, now, now, now, now, now, now, settings.environment(), settings.cohortOnly()).forEach(row -> {
                if (row.path("runStarted").asBoolean()
                        || row.path("schedule_hash").asText().equals(WaAttendanceCalendar.scheduleHash(jdbc, id(row, "routine_id")))) result.add(row);
            });
        return result;
    }

    UUID quoted(WaInboxAccess.Context event) {
        String quote = event.normalizedPayload().path("replyTo").asText();
        return WaAttendanceQuotes.resolveQuestion(jdbc, event.identityId(), instance(event), settings.environment(), quote).orElse(null);
    }

    ObjectNode own(UUID actor, String instance, UUID question) {
        for (JsonNode value : catalogue(actor, instance)) {
            if (value.path("id").asText().equals(question.toString())) return (ObjectNode) value;
        }
        return null;
    }

    ArrayNode filtered(ArrayNode catalogue, WaAttendanceAnswers.Input input) {
        ArrayNode result = WaJson.MAPPER.createArrayNode();
        for (JsonNode value : catalogue) {
            LocalDate date = LocalDate.parse(value.path("local_date").asText());
            LocalDate today = clock.now().atZone(ZoneId.of(value.path("timezone").asText())).toLocalDate();
            String requested = input.day();
            if (requested != null) {
                LocalDate target = switch (requested) {
                    case "TODAY" -> today;
                    case "TOMORROW" -> today.plusDays(1);
                    default -> LocalDate.parse(requested);
                };
                if (!target.equals(date)) continue;
            }
            if (input.direction() != null && !input.direction().equals(value.path("direction").asText())) continue;
            result.add(value);
        }
        return result;
    }

    ObjectNode draft(UUID actor) {
        List<String> rows = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",
            (rs, row) -> rs.getString(1), actor);
        if (rows.isEmpty()) return WaJson.MAPPER.createObjectNode();
        JsonNode value = WaJson.object(rows.get(0)).path("attendanceDraft");
        return value instanceof ObjectNode object ? object.deepCopy() : WaJson.MAPPER.createObjectNode();
    }

    void save(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        var stored = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",
            (rs, row) -> WaJson.object(rs.getString(1)), actor.identityId());
        ObjectNode context = stored.isEmpty() ? WaJson.MAPPER.createObjectNode() : stored.get(0);
        if (draft.isEmpty()) context.remove("attendanceDraft"); else context.set("attendanceDraft", draft);
        context.put("conversationOwner", draft.isEmpty() ? "READY" : "ATTENDANCE");
        String owner = WaConversationOwnership.owner(context);
        context.put("conversationOwner", owner);
        if (!draft.isEmpty()) WaConversationOwnership.prompt(context, "ATTENDANCE", event.eventId());
        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at)
            VALUES (?,?,?::jsonb,?,?)
            ON CONFLICT(identity_id) DO UPDATE SET stage=EXCLUDED.stage,context_json=EXCLUDED.context_json,
              last_event_id=EXCLUDED.last_event_id,version=wa.conversation_state.version+1,updated_at=EXCLUDED.updated_at
            """, actor.identityId(), owner, context.toString(), event.eventId(), Timestamp.from(clock.now()));
        jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?",
            Timestamp.from(clock.now()), actor.identityId());
    }

    static UUID id(JsonNode row, String field) { return UUID.fromString(row.path(field).asText()); }
    static String ref(UUID id) { return "question_" + id; }
    static UUID question(String reference) {
        if (!reference.matches("question_[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}")) throw WaProblem.denied();
        return UUID.fromString(reference.substring(9));
    }
    static String label(JsonNode row) {
        String direction = row.path("direction").asText().equals("OUTBOUND") ? "ida" : "volta";
        return LocalDate.parse(row.path("local_date").asText()).format(DateTimeFormatter.ofPattern("dd/MM/uuuu"))
            + ", " + direction + " às " + row.path("time").asText().substring(0, 5)
            + " (" + row.path("routineName").asText() + ")";
    }
}
