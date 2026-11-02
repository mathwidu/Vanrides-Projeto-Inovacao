package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
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
class WaRunPreparationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_run_preparation");
    private WaRunTestSupport f;
    @BeforeEach void setup() { f = new WaRunTestSupport(POSTGRES); }

    @Test void activationFreezesTheCurrentNamesPinsOrderPolicyAndPendingPresence() {
        String digest = f.digest();
        UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), digest);
        var snapshot = WaJson.object(f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
        assertEquals(digest, snapshot.path("rosterDigest").asText());
        assertEquals("Pessoa sintética 1", snapshot.path("passengerNames").path(f.passenger.toString()).asText());
        assertEquals("STARTING", f.s.string("SELECT state FROM wa.operation_run WHERE id=?", run));
        assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
        assertEquals(1, f.s.number("SELECT stop_order FROM wa.run_stop WHERE run_id=?", run));
        f.h.jdbc.update("UPDATE wa.profile SET display_name='Nome posterior' WHERE identity_id=?", f.passenger);
        assertEquals("Pessoa sintética 1", snapshot.path("passengerNames").path(f.passenger.toString()).asText());
        assertEquals(snapshot, WaJson.object(f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run)));
        var late = f.attendance.send(f.passenger, "Não vou hoje na ida");
        assertTrue(late.text().contains("próximos avisos"));
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
        assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
        assertEquals(snapshot, WaJson.object(f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run)));
    }

    @Test void readinessAndOwnerAreRevalidatedRatherThanTrustingTheReview() {
        String digest = f.digest();
        UUID other = f.s.actor(); f.s.driver(other);
        assertEquals(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE,
            assertThrows(WaProblem.class, () -> f.preparation.prepare(other, f.item.occurrence(), digest)).code());
        f.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=true WHERE id=?", f.item.routine());
        assertThrows(WaProblem.class, () -> f.preparation.prepare(f.driver, f.item.occurrence(), digest));
        f.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false,state='PAUSED' WHERE id=?", f.item.routine());
        assertThrows(WaProblem.class, () -> f.preparation.prepare(f.driver, f.item.occurrence(), digest));
        f.h.jdbc.update("UPDATE wa.routine SET state='ACTIVE' WHERE id=?", f.item.routine());
        f.h.jdbc.update("UPDATE wa.routine_occurrence SET state='CANCELED' WHERE id=?", f.item.occurrence());
        assertThrows(WaProblem.class, () -> f.preparation.prepare(f.driver, f.item.occurrence(), digest));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void aPersonalRefusalCommittedFirstInvalidatesTheDigestAndNeverCreatesAStop() {
        String old = f.digest();
        f.attendance.send(f.passenger, "Não vou hoje na ida");
        assertEquals(WaProblem.Code.VERSION_CONFLICT,
            assertThrows(WaProblem.class, () -> f.preparation.prepare(f.driver, f.item.occurrence(), old)).code());
        UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), f.digest());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=?", run));
    }

    @Test void correctionCommittedFirstMakesTheOldActivationReviewFail() {
        String old = f.digest();
        var review = f.reviewCorrection("EXCLUIR");
        f.send("CONFIRMAR " + f.marker(review)).decision().requireSuccess();
        assertEquals(WaProblem.Code.VERSION_CONFLICT,
            assertThrows(WaProblem.class, () -> f.preparation.prepare(f.driver, f.item.occurrence(), old)).code());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void activationWinsTheFenceAndTheWaitingCorrectionCannotRewriteTheSnapshot() throws Exception {
        var review = f.reviewCorrection("EXCLUIR");
        var lease = f.s.text(f.driver, "CONFIRMAR " + f.marker(review));
        String digest = f.digest();
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var activate = executor.submit(() -> f.h.transaction.execute(status -> {
                UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), digest);
                locked.countDown(); await(release); return run;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var correction = executor.submit(() -> f.execute(lease, true));
            assertThrows(TimeoutException.class, () -> correction.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            UUID run = activate.get(10, TimeUnit.SECONDS);
            assertEquals(WaProblem.Code.ACTIVE_RUN_EXISTS, correction.get(10, TimeUnit.SECONDS).decision().error());
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=?", run));
            assertEquals(0, f.s.number("SELECT count(*) FROM wa.occurrence_roster_override"));
            assertEquals("PENDING", f.attendance.state(f.item));
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }

    @Test void simultaneousPreparationsReserveOnlyOneRunAndOnePhysicalList() throws Exception {
        String digest = f.digest();
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var a = executor.submit(() -> prepare(start, digest));
            var b = executor.submit(() -> prepare(start, digest));
            start.countDown();
            Object left = a.get(10, TimeUnit.SECONDS), right = b.get(10, TimeUnit.SECONDS);
            assertTrue(left instanceof UUID && right == WaProblem.Code.ACTIVE_RUN_EXISTS
                || right instanceof UUID && left == WaProblem.Code.ACTIVE_RUN_EXISTS);
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.operation_run WHERE state='STARTING'"));
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.run_stop"));
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void personalAbsenceWaitsForTheRealSnapshotThenCancelsOnlyItsStop() throws Exception {
        String digest = f.digest();
        var lease = f.s.text(f.passenger, "Não vou hoje na ida");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var activate = executor.submit(() -> f.h.transaction.execute(status -> {
                UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), digest);
                locked.countDown(); await(release); return run;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var absence = executor.submit(() -> f.attendance.execute(lease, true));
            assertThrows(TimeoutException.class, () -> absence.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            UUID run = activate.get(10, TimeUnit.SECONDS);
            assertNull(absence.get(10, TimeUnit.SECONDS).decision().error());
            assertEquals("NOT_GOING", f.attendance.state(f.item));
            assertEquals("CANCELED", f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run));
            assertEquals("PENDING", f.s.string("SELECT attendance_snapshot FROM wa.run_stop WHERE run_id=?", run));
            assertEquals(digest, f.s.string("SELECT snapshot_json->>'rosterDigest' FROM wa.operation_run WHERE id=?", run));
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void personalAbsenceHoldingTheOccurrenceFenceMakesAWaitingActivationReviewStale() throws Exception {
        String digest = f.digest();
        var lease = f.s.text(f.passenger, "Não vou hoje na ida");
        CountDownLatch locked = new CountDownLatch(1), release = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var absence = executor.submit(() -> f.h.transaction.execute(status -> {
                f.h.jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE", f.item.occurrence());
                var result = f.attendance.execute(lease, true);
                locked.countDown(); await(release); return result;
            }));
            assertTrue(locked.await(10, TimeUnit.SECONDS));
            var activate = executor.submit(() -> {
                try { return (Object) f.preparation.prepare(f.driver, f.item.occurrence(), digest); }
                catch (WaProblem conflict) { return (Object) conflict.code(); }
            });
            assertThrows(TimeoutException.class, () -> activate.get(150, TimeUnit.MILLISECONDS));
            release.countDown();
            assertNull(absence.get(10, TimeUnit.SECONDS).decision().error());
            assertEquals(WaProblem.Code.VERSION_CONFLICT, activate.get(10, TimeUnit.SECONDS));
            assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
        } finally { release.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    private Object prepare(CountDownLatch start, String digest) {
        await(start);
        try { return f.preparation.prepare(f.driver, f.item.occurrence(), digest); }
        catch (WaProblem conflict) { return conflict.code(); }
    }
    private static void await(CountDownLatch latch) {
        try { if (!latch.await(10, TimeUnit.SECONDS)) throw new AssertionError("Synthetic race did not release"); }
        catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError(interrupted); }
    }
}
