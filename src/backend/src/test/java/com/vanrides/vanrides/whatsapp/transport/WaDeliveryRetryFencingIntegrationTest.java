package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRetryTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryRetryFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_retry_fencing");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f = new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); }

    @Test void concurrentIdenticalSafeResultsCommitExactlyOneScheduleAndOneReceipt() throws Exception {
        var box = f.enqueue(); var reservation = f.authorize(f.lease());
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var action = (java.util.concurrent.Callable<ObjectNode>) () -> { start.await(5, TimeUnit.SECONDS); return result(f, reservation); };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(f.h.jdbc.queryForObject("SELECT not_before FROM wa.message_outbox WHERE id=?", Timestamp.class, box.id())).isEqualTo(Timestamp.from(NOW.plusSeconds(5)));
            assertThat(f.count("message_attempt")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void concurrentAuthorizersCanReserveTheSecondAttemptOnlyOnce() throws Exception {
        f.enqueue(); result(f, f.authorize(f.lease())); f.h.time.advance(5); var lease = f.lease();
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var action = (java.util.concurrent.Callable<String>) () -> { start.await(5, TimeUnit.SECONDS); return outcome(() -> f.authorize(lease)); };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertThat(List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("COMMITTED", "IDEMPOTENCY_CONFLICT");
            assertThat(f.count("message_attempt")).isEqualTo(2);
            assertThat(f.h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE attempt_number=2 AND state='SUBMITTING'", Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void oldReceiptWaitingBehindANewReservationCannotInvalidateItsLease() throws Exception {
        var box = f.enqueue(); var first = f.authorize(f.lease()); var receipt = result(f, first);
        f.h.time.advance(5); var lease = f.lease();
        var executor = Executors.newSingleThreadExecutor(); var waiting = new AtomicReference<Future<ObjectNode>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.authorize(lease);
                waiting.set(executor.submit(() -> result(f, first)));
                awaitLock(f.h, "%pg_advisory_xact_lock%");
            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS)).isEqualTo(receipt);
            assertThat(f.state(box.id())).isEqualTo("LEASED");
            assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt WHERE attempt_number=2", String.class)).isEqualTo("SUBMITTING");
            assertThat(f.h.jdbc.queryForObject("SELECT lease_token_hash FROM wa.message_outbox WHERE id=?", String.class, box.id())).isNotNull();
        } finally { executor.shutdownNow(); }
    }
    @Test void ttlExpiringDuringTheSafeResultSubjectWaitExpiresWithoutScheduling() throws Exception {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); profile(box.actor());
        assertThat(whileProfileLocked(box.actor(), reservation, 301)).isEqualTo("EXPIRED");
        assertThat(f.state(box.id())).isEqualTo("EXPIRED"); assertThat(f.claim().path("items")).isEmpty();
    }
    @Test void callerExpiryDuringTheSafeResultWaitRollsBackTheReceiptAndSchedule() throws Exception {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); profile(box.actor());
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(2)), f.worker.credentialId());
        assertThat(whileProfileLocked(box.actor(), reservation, 3)).isEqualTo("INVALID_CREDENTIAL");
        assertThat(f.state(box.id())).isEqualTo("LEASED");
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class)).isEqualTo("SUBMITTING");
        assertThat(f.h.jdbc.queryForObject("SELECT result_request_hash FROM wa.message_attempt", String.class)).isNull();
    }
    @Test void committedConnectionCloseBeforeTheSafeResultDefersWithoutDiscardingIntent() throws Exception {
        f.enqueue(); var reservation = f.authorize(f.lease()); f.h.time.advance(1);
        var executor = Executors.newSingleThreadExecutor(); var waiting = new AtomicReference<Future<ObjectNode>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.connection("close"); waiting.set(executor.submit(() -> result(f, reservation)));
                awaitLock(f.h, "%pg_advisory_xact_lock%");
            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS).path("state").asText()).isEqualTo("PENDING");
            f.h.time.advance(5); assertThat(f.claim().path("items")).isEmpty();
            f.connection("open"); assertThat(f.lease()).isNotNull();
        } finally { executor.shutdownNow(); }
    }
    private void profile(java.util.UUID actor) {
        f.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')", actor);
    }
    private String whileProfileLocked(java.util.UUID actor, ObjectNode reservation, int seconds) throws Exception {
        var executor = Executors.newSingleThreadExecutor(); var waiting = new AtomicReference<Future<String>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", actor);
                waiting.set(executor.submit(() -> {
                    try { return result(f, reservation).path("state").asText(); }
                    catch (WaProblem error) { return error.code().name(); }
                }));
                awaitLock(f.h, "SELECT identity_id FROM wa.profile%FOR SHARE"); f.h.time.advance(seconds);
            });
            return waiting.get().get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    private String outcome(Runnable action) {
        try { action.run(); return "COMMITTED"; }
        catch (WaProblem error) { return error.code().name(); }
    }
}
