package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceCalendar;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Date;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

/** The calendar's delivery boundary is seeded independently from its scheduling tests. */
public final class WaAttendanceDeliveryTestSupport {
    final WaPassengerDeliveryTestSupport f;
    final WaTransportTestSupport h;
    final WaPrincipal job;

    public WaAttendanceDeliveryTestSupport(PostgreSQLContainer<?> postgres) {
        f = new WaPassengerDeliveryTestSupport(postgres);
        h = f.h;
        job = h.principal(WaPrincipal.Kind.SYSTEM_JOB);
    }

    public JdbcTemplate jdbc() { return h.jdbc; }
    public UUID cohort() { return f.cohort; }
    public Instant now() { return h.clock.now(); }

    public Reminder reminder() {
        var pair = f.pair("APPROVED");
        UUID source = f.event(pair.passenger());
        UUID alias = h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=?", UUID.class, pair.passenger());
        UUID occurrence = UUID.randomUUID(), attendance = UUID.randomUUID(), question = UUID.randomUUID(), outbox = UUID.randomUUID();
        UUID member = h.jdbc.queryForObject("SELECT id FROM wa.routine_member WHERE routine_id=? AND passenger_id=?",
                UUID.class, pair.routine(), pair.passenger());
        LocalDate date = LocalDate.of(2030, 1, 2);
        h.jdbc.update("UPDATE wa.profile SET preferences_json=?::jsonb WHERE identity_id=?",
                json("{}").put("ATTENDANCE_REMINDERS", true).toString(), pair.passenger());
        h.jdbc.update("""
            INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at)
            VALUES (?,?,?,?,?)
            """, occurrence, pair.routine(), pair.driver(), Date.valueOf(date), ts(h.clock.now().plusSeconds(3600)));
        h.jdbc.update("""
            INSERT INTO wa.attendance(id,routine_id,passenger_id,local_date,question_ref)
            VALUES (?,?,?,?,?)
            """, attendance, pair.routine(), pair.passenger(), Date.valueOf(date), question);
        h.jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,occurrence_id,reminder_window,
              policy_version,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'REMINDER',?,'previous-day','attendance-reminder-v1',?::jsonb,?,?,?)
            """, outbox, pair.passenger(), occurrence,
            json("{}").put("type", "TEXT").put("text", "Amanhã, na ida, você vai? Responda VOU ou NÃO VOU.")
                .put("referenceTime", h.clock.now().toString()).toString(),
            ts(h.clock.now().minusSeconds(60)), ts(h.clock.now().plusSeconds(1800)), ts(h.clock.now().minusSeconds(60)));
        h.jdbc.update("""
            INSERT INTO wa.attendance_question(id,attendance_id,occurrence_id,routine_id,driver_id,passenger_id,
              local_date,member_id,link_id,pickup_point_id,instance_id,environment,outbox_id,source_inbox_event_id,
              sender_alias_id,job_credential_id,link_version,member_version,pickup_version,schedule_hash,intent_hash,
              routine_name,direction,local_time,timezone,
              opens_at,reminder_at,send_until,answer_until,created_at)
            SELECT ?,?,?,?,?,?,?,?,?,?,'synthetic-transport','transport-test',?,?,?,?,link.version,m.version,
              point.version,?,?,r.name,r.direction,r.local_time,r.timezone,?,?,?,?,? FROM wa.driver_passenger_link link
              JOIN wa.routine_member m ON m.id=? JOIN wa.pickup_point point ON point.id=?
              JOIN wa.routine r ON r.id=m.routine_id WHERE link.id=?
            """, question, attendance, occurrence, pair.routine(), pair.driver(), pair.passenger(),
            Date.valueOf(date), member, pair.link(), pair.point(), outbox, source, alias, job.credentialId(),
            WaAttendanceCalendar.scheduleHash(h.jdbc, pair.routine()), WaAttendanceCalendar.intentHash(h.jdbc, pair.routine()),
            ts(h.clock.now().minusSeconds(60)),
            ts(h.clock.now().minusSeconds(60)), ts(h.clock.now().plusSeconds(1800)), ts(h.clock.now().plusSeconds(7200)),
            ts(h.clock.now().minusSeconds(60)), member, pair.point(), pair.link());
        return new Reminder(pair.driver(), pair.passenger(), pair.routine(), pair.link(), pair.point(), member,
                occurrence, attendance, question, outbox, source, alias);
    }

    ObjectNode claim(Reminder reminder) { return f.claim(reminder.outbox()); }
    ObjectNode claim() { return f.claim(); }
    WaDecision decision(ObjectNode lease) { return f.decision(lease); }
    ObjectNode authorize(ObjectNode lease) { return f.authorize(lease); }
    ObjectNode submitted(ObjectNode reservation, String external) { return f.submitted(reservation, external); }
    UUID callback(String external, String status) { return f.callback(external, status); }
    String state(Reminder reminder) {
        return h.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, reminder.outbox());
    }
    long attempts(Reminder reminder) {
        return h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", Long.class, reminder.outbox());
    }
    void pause(UUID actor) {
        h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage) VALUES (?,'PAUSED') "
                + "ON CONFLICT(identity_id) DO UPDATE SET stage='PAUSED'", actor);
    }
    void optOut(UUID actor) {
        h.jdbc.update("UPDATE wa.profile SET preferences_json=jsonb_set(preferences_json,'{ATTENDANCE_REMINDERS}','false') WHERE identity_id=?", actor);
    }
    void answer(Reminder reminder) {
        h.jdbc.update("UPDATE wa.attendance SET state='GOING',actor_identity_id=?,answered_at=? WHERE id=?",
                reminder.passenger(), ts(h.clock.now()), reminder.attendance());
        h.jdbc.update("UPDATE wa.attendance_question SET state='ANSWERED',answered_at=? WHERE id=?",
                ts(h.clock.now()), reminder.question());
    }
    void start(Reminder reminder) {
        h.jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_at)
            SELECT ?,id,routine_id,driver_id,local_date,'STARTING','synthetic',? FROM wa.routine_occurrence WHERE id=?
            """, UUID.randomUUID(), ts(h.clock.now()), reminder.occurrence());
    }
    static Timestamp ts(Instant value) { return Timestamp.from(value); }

    public record Reminder(UUID driver, UUID passenger, UUID routine, UUID link, UUID point, UUID member,
            UUID occurrence, UUID attendance, UUID question, UUID outbox, UUID source, UUID alias) {}
}
