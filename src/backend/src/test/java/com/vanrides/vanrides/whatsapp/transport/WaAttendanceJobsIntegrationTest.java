package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceJobsIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_jobs");
    private WaAttendanceCalendarTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
    }

    @Test
    void createsExactlyOneReminderAtTwentyLocalAndReplaysTheSamePage() {
        f.materialize();
        var first = f.tick("DUE_REMINDERS", 100, null);
        assertEquals(1, first.path("processed").asInt());
        assertEquals(first, f.tick("DUE_REMINDERS", 100, null));
        f.nextWindow();
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(1, f.count("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
        assertEquals(1, f.count("""
                SELECT count(*) FROM wa.attendance_question q JOIN wa.message_outbox o ON o.id=q.outbox_id
                WHERE q.local_date='2030-01-02' AND o.command_id IS NULL AND o.reminder_window='previous-day'
                  AND q.source_inbox_event_id IS NOT NULL AND q.sender_alias_id IS NOT NULL
                """));
    }

    @Test
    void logicalQuestionCanExistBeforeTheReminderWindow() {
        f.h.time.set(Instant.parse("2030-01-01T22:59:00Z"));
        f.materialize();
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertNotNull(f.question(f.passenger, "2030-01-02"));
    }

    @Test
    void restartAfterTheSendWindowDoesNotDumpExpiredReminders() {
        f.materialize();
        f.h.time.set(Instant.parse("2030-01-01T23:31:00Z"));
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, f.count("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
        assertNotNull(f.question(f.passenger, "2030-01-02"));
    }

    @Test
    void eitherExplicitAttendanceChoiceSuppressesTheReminder() {
        f.materialize();
        f.h.jdbc.update("""
                UPDATE wa.attendance SET state='GOING',actor_identity_id=passenger_id,answered_at='2030-01-01T23:00:00Z'
                WHERE passenger_id=? AND local_date='2030-01-02'
                """, f.passenger);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertEquals(0, f.count("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
    }

    @Test
    void pausedSupportConversationIsNotSelectedForAReminder() {
        f.materialize();
        f.h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage) VALUES (?,'PAUSED')", f.passenger);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertNotNull(f.question(f.passenger, "2030-01-02"));
    }

    @Test
    void unsupportedJobsStayUnavailable() {
        WaProblem error = assertThrows(WaProblem.class, () -> f.tick("RETENTION", 100, null));
        assertEquals(WaProblem.Code.DEPENDENCY_UNAVAILABLE, error.code());
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance_job_page"));
    }

    @Test
    void rejectsFutureOrExpiredWindowsWithoutBusinessEffects() {
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "MATERIALIZE_OCCURRENCES")
                .put("windowKey", "2030-01-01T23:01:00Z").put("batchLimit", 100);
        assertThrows(WaProblem.class, () -> f.jobs.tick(f.job, request));
        request.put("windowKey", "2030-01-01T22:58:00Z");
        assertThrows(WaProblem.class, () -> f.jobs.tick(f.job, request));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void humanWorkerCannotUseTheJobRouteService() {
        var human = f.h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "MATERIALIZE_OCCURRENCES")
                .put("windowKey", "2030-01-01T23:00:00Z").put("batchLimit", 100);
        assertThrows(WaProblem.class, () -> f.jobs.tick(human, request));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance_job_page"));
    }

    @Test
    void businessEffectsRollBackWhenReminderOutboxInsertionFails() {
        f.materialize();
        f.h.jdbc.execute("""
                CREATE FUNCTION wa.fail_attendance_reminder() RETURNS TRIGGER LANGUAGE plpgsql AS $$
                BEGIN IF NEW.purpose='REMINDER' THEN RAISE EXCEPTION USING ERRCODE='23514',MESSAGE='synthetic failure'; END IF;
                RETURN NEW; END $$
                """);
        f.h.jdbc.execute("CREATE TRIGGER fail_attendance_reminder BEFORE INSERT ON wa.message_outbox FOR EACH ROW EXECUTE FUNCTION wa.fail_attendance_reminder()");
        assertThrows(DataIntegrityViolationException.class, () -> f.tick("DUE_REMINDERS", 100, null));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance_job_page WHERE job_type='DUE_REMINDERS'"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance_question WHERE outbox_id IS NOT NULL"));
        f.h.jdbc.execute("DROP TRIGGER fail_attendance_reminder ON wa.message_outbox");
        assertEquals(1, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
    }

    @Test
    void aStartedOccurrenceNeverQueuesADelayedReminder() {
        f.materialize();
        UUID occurrence = f.h.jdbc.queryForObject("SELECT occurrence_id FROM wa.attendance_question WHERE id=?",
                UUID.class, f.question(f.passenger, "2030-01-02"));
        f.h.jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
                VALUES (?,?,?,?,'2030-01-02','STARTING','attendance-test','{}'::jsonb,'2030-01-01T23:00:00Z')
                """, UUID.randomUUID(), occurrence, f.routine, f.driver);
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
    }
}
