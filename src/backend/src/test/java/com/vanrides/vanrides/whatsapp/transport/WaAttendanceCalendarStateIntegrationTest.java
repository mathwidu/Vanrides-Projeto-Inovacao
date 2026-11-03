package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.dao.DataIntegrityViolationException;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceCalendarStateIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_calendar_state");
    private WaAttendanceCalendarTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
        f.materialize();
    }

    @Test
    void routineRenameRotatesTheQuestionAndPreservesExplicitGoing() {
        answer("GOING");
        UUID before = f.question(f.passenger, "2030-01-02");
        f.h.jdbc.update("UPDATE wa.routine SET name='Nome revisado',version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertNotEquals(before, f.question(f.passenger, "2030-01-02"));
        assertEquals("GOING", state());
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance_question WHERE id=? AND state='CLOSED'", before));
    }

    @Test
    void materialTimeChangeRequiresGoingToBeConfirmedAgain() {
        answer("GOING");
        UUID before = f.question(f.passenger, "2030-01-02");
        f.h.jdbc.update("UPDATE wa.routine SET local_time='07:00',version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals("PENDING", state());
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance_question WHERE id=? AND state='CLOSED' AND answered_at IS NOT NULL", before));
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance WHERE passenger_id=? AND local_date='2030-01-02' AND answered_at IS NULL", f.passenger));
    }

    @Test
    void changedTimeDoesNotReincludeAnExplicitlyAbsentPassenger() {
        answer("NOT_GOING");
        f.h.jdbc.update("UPDATE wa.routine SET local_time='07:00',version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals("NOT_GOING", state());
    }

    @Test
    void pickupRevisionKeepsTheAttendanceChoice() {
        answer("GOING");
        UUID before = f.question(f.passenger, "2030-01-02");
        f.h.jdbc.update("UPDATE wa.routine_member SET version=version+1 WHERE passenger_id=?", f.passenger);
        f.h.jdbc.update("UPDATE wa.pickup_point SET version=version+1 WHERE passenger_id=?", f.passenger);
        f.nextWindow();
        f.materialize();
        assertEquals("GOING", state());
        assertNotEquals(before, f.question(f.passenger, "2030-01-02"));
    }

    @Test
    void startingCutoffPreservesOccurrenceAndQuestionSnapshots() {
        UUID question = f.question(f.passenger, "2030-01-02");
        UUID occurrence = f.h.jdbc.queryForObject("SELECT occurrence_id FROM wa.attendance_question WHERE id=?", UUID.class, question);
        f.h.jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
                VALUES (?,?,?,?,'2030-01-02','STARTING','attendance-test','{"frozen":true}'::jsonb,?)
                """, UUID.randomUUID(), occurrence, f.routine, f.driver, Timestamp.from(f.h.clock.now()));
        f.h.jdbc.update("UPDATE wa.routine SET local_time='07:00',name='Horário futuro',version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals(question, f.question(f.passenger, "2030-01-02"));
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance_question WHERE id=? AND local_time='06:00' AND routine_name='Ida habitual'", question));
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE id=? AND scheduled_at='2030-01-02T09:00:00Z'", occurrence));
    }

    @Test
    void materializationUsesLocalDateAcrossUtcMidnight() {
        f.h.time.set(Instant.parse("2030-01-02T01:00:00Z"));
        f.materialize();
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-01'"));
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-08'"));
    }

    @Test
    void questionBindingAndClosedStateCannotBeRewritten() {
        UUID question = f.question(f.passenger, "2030-01-02");
        assertThrows(DataIntegrityViolationException.class,
                () -> f.h.jdbc.update("UPDATE wa.attendance_question SET local_date='2030-01-03' WHERE id=?", question));
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='CLOSED' WHERE id=?", question);
        assertThrows(DataIntegrityViolationException.class,
                () -> f.h.jdbc.update("UPDATE wa.attendance_question SET state='OPEN' WHERE id=?", question));
    }

    @Test
    void removingAndRestoringAWeekdayKeepsCancellationSemanticsDistinct() {
        UUID old = f.question(f.passenger, "2030-01-02");
        f.h.jdbc.update("UPDATE wa.routine SET weekdays=ARRAY[1,2,4,5,6,7]::smallint[],version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-02' AND state='PLANNED' AND NOT calendar_eligible"));
        assertEquals(1, f.count("SELECT count(*) FROM wa.attendance_question WHERE id=? AND state='CLOSED'", old));
        f.h.jdbc.update("UPDATE wa.routine SET weekdays=ARRAY[1,2,3,4,5,6,7]::smallint[],version=version+1 WHERE id=?", f.routine);
        f.nextWindow();
        f.materialize();
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-02' AND calendar_eligible"));
        assertNotEquals(old, f.question(f.passenger, "2030-01-02"));
    }

    @Test
    void editingWeekdaysBeforeTheRoutineLockDoesNotCreateTheRemovedDay() throws Exception {
        f.h.jdbc.update("DELETE FROM wa.attendance_question");
        f.h.jdbc.update("DELETE FROM wa.attendance");
        f.h.jdbc.update("DELETE FROM wa.routine_occurrence");
        f.nextWindow();
        var pool = Executors.newSingleThreadExecutor();
        var pending = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        try {
            f.h.transaction.executeWithoutResult(status -> {
                f.h.jdbc.queryForList("SELECT id FROM wa.routine WHERE id=? FOR UPDATE", f.routine);
                pending.set(pool.submit(() -> f.tick("MATERIALIZE_OCCURRENCES", 100, null)));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);
                while (true) {
                    f.h.jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    if (f.count("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' AND query LIKE '%wa.routine%' AND pid<>pg_backend_pid()") > 0) {
                        break;
                    }
                    assertFalse(pending.get().isDone(), "Job ended before the held routine lock");
                    assertTrue(System.nanoTime() < deadline, "Job did not reach the routine lock");
                    try {
                        Thread.sleep(20);
                    } catch (InterruptedException failure) {
                        Thread.currentThread().interrupt();
                        throw new AssertionError(failure);
                    }
                }
                f.h.jdbc.update("UPDATE wa.routine SET weekdays=ARRAY[1,3,4,5,6,7]::smallint[],version=version+1 WHERE id=?", f.routine);
            });
            pending.get().get(20, TimeUnit.SECONDS);
        } finally {
            pool.shutdownNow();
            assertTrue(pool.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(0, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE local_date='2030-01-01'"));
        assertEquals(6, f.count("SELECT count(*) FROM wa.routine_occurrence"));
    }

    @Test
    void emptyStartedOccurrenceKeepsItsDisplaySnapshotAfterFutureEdits() {
        UUID empty = f.routine("Volta sem passageiros", "RETURN", "18:00", "America/Sao_Paulo");
        f.nextWindow();
        f.materialize();
        UUID occurrence = f.h.jdbc.queryForObject("SELECT id FROM wa.routine_occurrence WHERE routine_id=? AND local_date='2030-01-02'", UUID.class, empty);
        f.h.jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
                VALUES (?,?,?,?,'2030-01-02','STARTING','attendance-test','{}'::jsonb,?)
                """, UUID.randomUUID(), occurrence, empty, f.driver, Timestamp.from(f.h.clock.now()));
        f.h.jdbc.update("UPDATE wa.routine SET name='Configuração futura',local_time='19:00',version=version+1 WHERE id=?", empty);
        f.nextWindow();
        f.materialize();
        assertEquals(1, f.count("SELECT count(*) FROM wa.routine_occurrence WHERE id=? AND schedule_snapshot->>'name'='Volta sem passageiros' AND schedule_snapshot->>'local_time'='18:00'", occurrence));
    }

    private void answer(String state) {
        f.h.jdbc.update("""
                UPDATE wa.attendance SET state=?,actor_identity_id=passenger_id,credential_id=?,answered_at=?
                WHERE passenger_id=? AND local_date='2030-01-02'
                """, state, f.job.credentialId(), Timestamp.from(f.h.clock.now()), f.passenger);
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='ANSWERED',answered_at=? WHERE id=?",
                Timestamp.from(f.h.clock.now()), f.question(f.passenger, "2030-01-02"));
    }

    private String state() {
        return f.h.jdbc.queryForObject("SELECT state FROM wa.attendance WHERE passenger_id=? AND local_date='2030-01-02'",
                String.class, f.passenger);
    }
}
