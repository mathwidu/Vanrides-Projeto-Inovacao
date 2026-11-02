package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceCalendarIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_calendar");
    private WaAttendanceCalendarTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
    }

    @Test
    void materializesSevenLocalDatesWithPendingLogicalQuestionsBeforeAnyReminder() {
        f.materialize();
        assertEquals(7, f.count("SELECT count(*) FROM wa.routine_occurrence"));
        assertEquals(7, f.count("SELECT count(*) FROM wa.attendance WHERE state='PENDING'"));
        assertEquals(7, f.count("SELECT count(*) FROM wa.attendance_question WHERE outbox_id IS NULL AND state='OPEN'"));
        assertEquals(7, f.count("SELECT count(*) FROM wa.attendance a JOIN wa.attendance_question q ON q.id=a.question_ref"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
        assertEquals(1, f.count("""
                SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-02'
                  AND scheduled_at='2030-01-02T09:00:00Z'
                """));
    }

    @Test
    void remindersOptOutPreservesPendingAndManualQuestion() {
        f.h.jdbc.update("UPDATE wa.profile SET preferences_json='{}'::jsonb WHERE identity_id=?", f.passenger);
        f.materialize();
        assertEquals(0, f.tick("DUE_REMINDERS", 100, null).path("processed").asInt());
        assertNotNull(f.question(f.passenger, "2030-01-02"));
        assertEquals(7, f.count("SELECT count(*) FROM wa.attendance WHERE state='PENDING'"));
    }

    @Test
    void intersectsRoutineAndIndividualWeekdays() {
        f.h.jdbc.update("UPDATE wa.routine SET weekdays=ARRAY[1,3,5]::smallint[] WHERE id=?", f.routine);
        f.h.jdbc.update("UPDATE wa.routine_member SET weekdays=ARRAY[1,5]::smallint[] WHERE routine_id=?", f.routine);
        f.materialize();
        assertEquals(3, f.count("SELECT count(*) FROM wa.routine_occurrence"));
        assertEquals(2, f.count("SELECT count(*) FROM wa.attendance"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance WHERE local_date='2030-01-02'"));
    }

    @Test
    void preservesCancellationMadeBeforeMaterialization() {
        f.h.jdbc.update("""
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,state)
                VALUES (?,?,?,'2030-01-02','2030-01-02T09:00:00Z','CANCELED')
                """, UUID.randomUUID(), f.routine, f.driver);
        f.materialize();
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE state='CANCELED'"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance WHERE local_date='2030-01-02'"));
    }

    @Test
    void pausedRoutineDoesNotMaterializeAndResumeRestoresFutureCalendar() {
        f.h.jdbc.update("UPDATE wa.routine SET state='PAUSED' WHERE id=?", f.routine);
        f.materialize();
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence"));
        f.h.jdbc.update("UPDATE wa.routine SET state='ACTIVE' WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals(7, f.count("SELECT count(*) FROM wa.routine_occurrence"));
    }

    @Test
    void changingAnotherMembersVersionDoesNotRotateTheExistingQuestion() {
        f.materialize();
        UUID before = f.question(f.passenger, "2030-01-02");
        UUID other = f.member(f.routine, true);
        f.h.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?", f.routine);
        f.h.jdbc.update("UPDATE wa.routine_member SET version=version+1 WHERE passenger_id=?", other);
        f.nextWindow();
        f.materialize();
        assertEquals(before, f.question(f.passenger, "2030-01-02"));
        assertEquals(14, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void anotherJobInstanceCannotSelectDriverOrPassenger() {
        var foreign = f.h.principal(com.vanrides.vanrides.whatsapp.auth.WaPrincipal.Kind.SYSTEM_JOB, "other-instance");
        var request = WaAttendanceCalendarTestSupport.json().put("jobType", "MATERIALIZE_OCCURRENCES")
                .put("windowKey", "2030-01-01T23:00:00Z").put("batchLimit", 100);
        assertEquals(0, f.jobs.tick(foreign, request).path("processed").asInt());
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void passengerFromAnotherCohortDoesNotGetAttendance() {
        UUID cohort = UUID.randomUUID();
        f.h.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'transport-test',true)", cohort);
        f.h.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, f.passenger);
        f.materialize();
        assertEquals(0, f.count("SELECT count(*) FROM wa.attendance"));
    }

    @Test
    void springGapDoesNotPreventAnotherValidRoutineFromMaterializing() {
        f.h.time.set(Instant.parse("2030-03-09T12:00:00Z"));
        f.h.jdbc.update("UPDATE wa.routine SET local_time='02:30',timezone='America/New_York' WHERE id=?", f.routine);
        UUID valid = f.routine("Volta válida", "RETURN", "18:00", "America/Sao_Paulo");
        f.materialize();
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE routine_id=? AND local_date='2030-03-10'", f.routine));
        assertEquals(7, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE routine_id=?", valid));
    }

    @Test
    void ambiguousAutumnSlotDoesNotChooseAnOffset() {
        f.h.time.set(Instant.parse("2030-11-02T12:00:00Z"));
        f.h.jdbc.update("UPDATE wa.routine SET local_time='01:30',timezone='America/New_York' WHERE id=?", f.routine);
        f.materialize();
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-11-03'"));
        assertTrue(f.count("SELECT count(*) FROM wa.routine_occurrence") > 0);
    }
}
