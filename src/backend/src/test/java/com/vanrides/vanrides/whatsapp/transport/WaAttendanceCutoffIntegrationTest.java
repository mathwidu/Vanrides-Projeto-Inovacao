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
        var result = f.send(passenger, "Não vou hoje na ida");
        assertEquals("Ausência registrada\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)\n"
            + "O motorista já saiu e sua parada foi retirada desta viagem.", result.text());
        assertEquals("NOT_GOING", f.state(item));
        assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertEquals(before, f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
    }

    @Test void aLateRequestToJoinDoesNotReactivateAPersonAlreadyAbsentAtStart() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.send(passenger, "Não vou hoje na ida");
        UUID run = f.starting(item, "NOT_GOING");
        f.h.jdbc.update("DELETE FROM wa.run_stop WHERE run_id=?", run);
        String before = f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance());
        var result = f.send(passenger, "Vou hoje na ida");
        assertEquals("O motorista já saiu e você não está na lista desta viagem.\n"
            + "• Hoje, terça (01/01), ida às 15:00 (Ida de teste)\nPara embarcar, fale diretamente com o motorista.", result.text());
        assertEquals(before, f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance()));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=?", run));
        assertEquals(2, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=? AND command_type='SET_ATTENDANCE'", passenger));
    }

    @Test void aLateRequestToJoinAfterALateAbsenceKeepsTheCanceledStopAndTheAbsence() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "PENDING");
        f.send(passenger, "Não vou hoje na ida");
        String attendance = f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance());
        String stop = f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run);
        var join = f.send(passenger, "Vou hoje na ida");
        assertTrue(join.text().startsWith("O motorista já saiu e você não está na lista desta viagem."));
        var repeat = f.send(passenger, "Não vou hoje na ida");
        assertEquals("Sua ausência já estava registrada\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)", repeat.text());
        assertEquals(attendance, f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance()));
        assertEquals(stop, f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run));
    }

    @Test void anAbsenceFromSomeoneNotOnTheDepartureListIsStillRecorded() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "PENDING");
        f.h.jdbc.update("DELETE FROM wa.run_stop WHERE run_id=?", run);
        var result = f.send(passenger, "Não vou hoje na ida");
        assertEquals("Ausência registrada\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)", result.text());
        assertEquals("NOT_GOING", f.state(item));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=?", run));
        var repeat = f.send(passenger, "Não vou hoje na ida");
        assertEquals("Sua ausência já estava registrada\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)", repeat.text());
    }

    @Test void anAbsenceCannotUndoAPassedStop() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        String quote = f.sent(item);
        UUID run = f.starting(item, "GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
        String attendance = f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance());
        String stop = f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run);
        var result = f.quote(passenger, "Não vou", quote);
        assertEquals("A van já passou pelo seu ponto.\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)\n"
            + "A presença desta viagem não pode mais ser alterada.", result.text());
        assertEquals("PASSED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertEquals("PENDING", f.state(item));
        assertEquals(attendance, f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance()));
        assertEquals(stop, f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run));
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

    @Test void aStopPassedWhileTheAnswerWaitsForItsRowIsSeenAsPassed() throws Exception {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "GOING");
        var lease = f.s.text(passenger, "Não vou hoje na ida");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var passing = executor.submit(() -> f.h.transaction.execute(status -> {
                f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
                locked.countDown(); await(release);
                return null;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var answer = executor.submit(() -> f.execute(lease, true));
            awaitWaitingOnTheStopRow();
            release.countDown();
            passing.get(10, TimeUnit.SECONDS);
            assertEquals("A van já passou pelo seu ponto.\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)\n"
                + "A presença desta viagem não pode mais ser alterada.", answer.get(10, TimeUnit.SECONDS).text());
            assertEquals("PENDING", f.state(item));
            assertEquals("PASSED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
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
        assertEquals("Não há nenhuma viagem próxima registrada. Aguarde a chegada do lembrete.", result.text());
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

    private void awaitWaitingOnTheStopRow() throws InterruptedException {
        for (int attempt = 0; attempt < 200; attempt++) {
            if (f.s.number("SELECT count(*) FROM pg_stat_activity WHERE wait_event_type='Lock' "
                + "AND query LIKE 'SELECT state,attendance_snapshot FROM wa.run_stop%FOR UPDATE%'") == 1) return;
            Thread.sleep(50);
        }
        fail("The answer never waited for the stop row");
    }

    private static void await(CountDownLatch latch) {
        try {
            if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Synthetic concurrency timeout");
        } catch (InterruptedException interrupted) {
            Thread.currentThread().interrupt(); throw new AssertionError(interrupted);
        }
    }
}
