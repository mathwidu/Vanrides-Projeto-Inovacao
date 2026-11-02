package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
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
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRecoveryTestSupport.unknown;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryRecoveryFencingIntegrationTest {
    // Expanded origin SQL exceeds the activity query buffer: lock evidence cannot depend on its tail.
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_recovery_fencing")
        .withCommand("postgres", "-c", "track_activity_query_size=256");
    private WaDeliveryConnectionTestSupport f;
    private WaDeliveryRecoveryTestSupport health;
    @BeforeEach void setup() {
        f = new WaDeliveryConnectionTestSupport(POSTGRES); f.connection("open"); health = new WaDeliveryRecoveryTestSupport(f.h);
    }
    @Test void concurrentTicksReturnOneDurableReceiptAndOneOperationalCase() throws Exception {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300);
        var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var action = (java.util.concurrent.Callable<ObjectNode>) () -> { start.await(5, TimeUnit.SECONDS); return health.tick(100, null); };
            var first = executor.submit(action); var second = executor.submit(action); start.countDown();
            assertThat(first.get(10, TimeUnit.SECONDS)).isEqualTo(second.get(10, TimeUnit.SECONDS));
            assertThat(f.state(box.id())).isEqualTo("EXPIRED"); assertThat(f.count("support_case")).isEqualTo(1);
            assertThat(f.count("attendance_job_page")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void lateResultWaitingForAnExpiringHealthPageCannotReopenItsOutbox() throws Exception {
        var box = f.enqueue(); var reservation = f.authorize(f.lease()); f.h.time.advance(301);
        var executor = Executors.newSingleThreadExecutor(); var pending = new AtomicReference<Future<ObjectNode>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                health.tick(100, null);
                pending.set(executor.submit(() -> f.submitted(reservation, "synthetic_after_health_commit")));
                WaDeliveryRetryTestSupport.awaitLock(f.h, "%pg_advisory_xact_lock%");
            });
            assertThat(pending.get().get(10, TimeUnit.SECONDS).path("state").asText()).isEqualTo("EXPIRED");
            assertThat(f.state(box.id())).isEqualTo("EXPIRED");
            assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class)).isEqualTo("SUBMITTED");
            assertThat(f.count("support_case")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void jobCredentialExpiringWhileWaitingForOutboxRollsBackCaseExpiryAndPage() throws Exception {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300);
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(f.h.clock.now().plusSeconds(2)), health.job.credentialId());
        var executor = Executors.newSingleThreadExecutor(); var pending = new AtomicReference<Future<String>>();
        var workerPid = new AtomicReference<Integer>();
        assertThat(f.h.jdbc.queryForObject("SELECT setting::integer FROM pg_settings WHERE name='track_activity_query_size'", Integer.class)).isEqualTo(256);
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                int ownerPid = f.h.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                f.h.jdbc.queryForList("SELECT id FROM wa.message_outbox WHERE id=? FOR UPDATE", box.id());
                pending.set(executor.submit(() -> {
                    try {
                        return f.h.transaction.execute(worker -> {
                            workerPid.set(f.h.jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                            health.tick(100, null); return "COMMITTED";
                        });
                    }
                    catch (WaProblem error) { return error.code().name(); }
                }));
                awaitOwnedOutboxWait(workerPid, ownerPid);
                assertThat(pending.get().isDone()).as("health has not completed before the owned outbox is released").isFalse();
                f.h.time.advance(3);
            });
            assertThat(pending.get().get(10, TimeUnit.SECONDS)).isEqualTo("INVALID_CREDENTIAL");
            assertThat(f.state(box.id())).isEqualTo("UNKNOWN"); assertThat(f.count("support_case")).isZero();
            assertThat(f.count("attendance_job_page")).isZero();
        } finally { executor.shutdownNow(); }
    }
    @Test void revokedJobCannotExpireAnotherPrincipalsOutboxOrCreateOperationalCases() {
        var box = f.enqueue(); unknown(f, f.authorize(f.lease())); f.h.time.advance(300);
        f.h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?", Timestamp.from(f.h.clock.now()), health.job.credentialId());
        assertThatThrownBy(() -> health.tick(100, null)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));
        assertThat(f.state(box.id())).isEqualTo("UNKNOWN"); assertThat(f.count("support_case")).isZero();
    }
    private void awaitOwnedOutboxWait(AtomicReference<Integer> workerPid, int ownerPid) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
        while (System.nanoTime() < end) {
            Integer pid = workerPid.get();
            if (pid != null) {
                f.h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
                waiting = Boolean.TRUE.equals(f.h.jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM pg_stat_activity activity WHERE activity.pid=?
                      AND activity.wait_event_type='Lock' AND ?=ANY(pg_blocking_pids(activity.pid)))
                    """, Boolean.class, pid, ownerPid));
            }
            if (waiting) break;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted owned outbox wait"); }
        }
        assertThat(waiting).as("exact health backend blocked by the owned outbox transaction").isTrue();
    }
}
