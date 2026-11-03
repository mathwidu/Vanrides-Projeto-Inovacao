package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceCutoffIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_cutoff");
    private WaAttendanceConversationTestSupport f;
    private UUID passenger;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);

    @BeforeEach void setup() {
        f = new WaAttendanceConversationTestSupport(POSTGRES);
        passenger = f.passenger();
    }

    @Test void lateAbsenceSuppressesFutureAlertsWithoutRewritingTheSnapshot() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "PENDING");
        String before = f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run);
        f.send(passenger, "Não vou hoje na ida");
        assertEquals("NOT_GOING", f.state(item));
        assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertEquals(before, f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
    }

    @Test void aLateRequestToJoinDoesNotReactivateAPersonAlreadyAbsentAtStart() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.send(passenger, "Não vou hoje na ida");
        UUID run = f.starting(item, "NOT_GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='SKIPPED',version=version+1 WHERE run_id=?", run);
        f.send(passenger, "Presença");
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? "
            + "AND jsonb_exists(context_json,'attendanceDraft')", passenger));
        var result = f.send(passenger, "Vou hoje na ida");
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? "
            + "AND jsonb_exists(context_json,'attendanceDraft')", passenger));
        assertEquals("NOT_GOING", f.state(item));
        assertEquals("SKIPPED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertTrue(result.text().contains("embarque ainda precisa ser confirmado"));
        assertEquals(2, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=? AND command_type='SET_ATTENDANCE'", passenger));
    }

    @Test void anAbsenceCannotUndoAPassedStop() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
        f.send(passenger, "Não vou hoje na ida");
        assertEquals("PASSED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertEquals("GOING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
    }

    @Test void activationWinsTheOccurrenceLockAndTheLaterAnswerSeesItsSnapshot() throws Exception {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var lease = f.s.text(passenger, "Não vou hoje na ida");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var activation = executor.submit(() -> f.h.transaction.execute(status -> {
                f.h.jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE", item.occurrence());
                UUID run = f.starting(item, "PENDING");
                locked.countDown(); await(release);
                return run;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var answer = executor.submit(() -> f.execute(lease, true));
            assertThrows(TimeoutException.class, () -> answer.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            UUID run = activation.get(10, TimeUnit.SECONDS);
            assertNull(answer.get(10, TimeUnit.SECONDS).decision().error());
            assertEquals("NOT_GOING", f.state(item));
            assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
        } finally {
            release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void anAnswerCommittedFirstIsSeenBeforeActivationCanCaptureItsList() throws Exception {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var lease = f.s.text(passenger, "Não vou hoje na ida");
        CountDownLatch answered = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var answer = executor.submit(() -> f.h.transaction.execute(status -> {
                var result = f.execute(lease, true);
                answered.countDown(); await(release);
                return result;
            }));
            assertTrue(answered.await(10, TimeUnit.SECONDS));
            var activation = executor.submit(() -> f.h.transaction.execute(status -> {
                f.h.jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE", item.occurrence());
                return f.state(item);
            }));
            assertThrows(TimeoutException.class, () -> activation.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            assertNull(answer.get(10, TimeUnit.SECONDS).decision().error());
            assertEquals("NOT_GOING", activation.get(10, TimeUnit.SECONDS));
            assertEquals(0, f.s.number("SELECT count(*) FROM wa.run_stop"));
        } finally {
            release.countDown(); executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
    }

    @Test void todayAndTomorrowUseTheRoutineTimezoneAcrossUtcMidnight() {
        f.h.time.set(Instant.parse("2030-01-02T01:00:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var today = f.question(passenger, DAY, "RETURN", "23:00");
        var tomorrow = f.question(passenger, DAY.plusDays(1), "RETURN", "23:00");
        f.send(passenger, "Não vou hoje na volta");
        assertEquals("NOT_GOING", f.state(today)); assertEquals("PENDING", f.state(tomorrow));
    }

    @Test void aChangedRoutineCannotBeAnsweredUntilItsQuestionIsRefreshed() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.h.jdbc.update("UPDATE wa.routine SET local_time='16:00',version=version+1 WHERE id=?", item.routine());
        var result = f.send(passenger, "Vou hoje na ida");
        assertEquals("ADVANCE_ATTENDANCE", result.body().path("commandType").asText());
        assertEquals("PENDING", f.state(item));
    }

    @Test void theStartedQuestionKeepsItsOriginalHumanScheduleAfterRoutineEdits() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.starting(item, "PENDING");
        f.h.jdbc.update("UPDATE wa.routine SET local_time='16:00',direction='RETURN',name='Rotina futura',version=version+1 WHERE id=?", item.routine());
        var result = f.send(passenger, "Não vou hoje na ida");
        assertEquals("NOT_GOING", f.state(item));
        assertTrue(result.text().contains("ida às 15:00"));
        assertFalse(result.text().contains("16:00"));
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Synthetic concurrency timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
        }
    }
}
