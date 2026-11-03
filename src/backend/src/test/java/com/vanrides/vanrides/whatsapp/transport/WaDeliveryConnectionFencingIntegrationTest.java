package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryConnectionFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_delivery_connection_fencing");
    private WaDeliveryConnectionTestSupport f;
    @BeforeEach void setup() { f = new WaDeliveryConnectionTestSupport(POSTGRES); }

    @Test void committedCloseWinsBeforeAnAuthorizationWaitingOnTheSameDeliveryLock() throws Exception {
        f.enqueue(); f.connection("open"); var lease = f.lease(); f.h.time.advance(1);
        var executor = Executors.newSingleThreadExecutor();
        var waiting = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.connection("close");
                waiting.set(executor.submit(() -> outcome(() -> f.authorize(lease))));
                awaitLock("%pg_advisory_xact_lock%", true);
            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS)).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(f.count("message_attempt")).isZero();
        } finally { executor.shutdownNow(); }
    }
    @Test void reservationCommittedBeforeCloseKeepsItsOneHistoricalResult() throws Exception {
        f.enqueue(); f.connection("open"); var lease = f.lease(); f.h.time.advance(1);
        var executor = Executors.newSingleThreadExecutor();
        var waiting = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<?>>();
        var reservation = new java.util.concurrent.atomic.AtomicReference<com.fasterxml.jackson.databind.node.ObjectNode>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                reservation.set(f.authorize(lease));
                waiting.set(executor.submit(() -> f.connection("close")));
                awaitLock("%pg_advisory_xact_lock%", true);
            });
            waiting.get().get(10, TimeUnit.SECONDS);
            assertThat(f.submitted(reservation.get(), "synthetic_reserved_before_close").path("state").asText()).isEqualTo("SUBMITTED");
            assertThat(f.claim().path("items")).isEmpty();
            assertThat(f.count("message_attempt")).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void sourceCredentialExpiryDuringSubjectWaitRollsBackTheReservation() throws Exception {
        var box = f.enqueue(); f.connection("open"); var lease = f.lease();
        f.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')", box.actor());
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(2)), f.channel.credentialId());
        var executor = Executors.newSingleThreadExecutor();
        var waiting = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", box.actor());
                waiting.set(executor.submit(() -> outcome(() -> f.authorize(lease))));
                awaitLock("SELECT identity_id FROM wa.profile%FOR SHARE", false); f.h.time.advance(3);
            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS)).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(f.count("message_attempt")).isZero();
            assertThat(f.state(box.id())).isEqualTo("LEASED");
        } finally { executor.shutdownNow(); }
    }
    @Test void connectionCredentialExpiringDuringDeliveryLockWaitRollsBackItsReceipt() throws Exception {
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(NOW.plusSeconds(2)), f.channel.credentialId());
        var executor = Executors.newSingleThreadExecutor();
        var waiting = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                new WaDeliveryConnection(f.h.jdbc, f.h.settings, f.h.clock).lock(INSTANCE, true);
                waiting.set(executor.submit(() -> outcome(() -> f.connection("open"))));
                awaitLock("%pg_advisory_xact_lock%", true); f.h.time.advance(3);
            });
            assertThat(waiting.get().get(10, TimeUnit.SECONDS)).isEqualTo("INVALID_CREDENTIAL");
            assertThat(f.count("delivery_connection")).isZero();
            assertThat(f.count("integration_inbox")).isZero();
        } finally { executor.shutdownNow(); }
    }
    private String outcome(Runnable action) {
        try { action.run(); return "COMMITTED"; }
        catch (WaProblem error) { return error.code().name(); }
    }
    private void awaitLock(String pattern, boolean advisory) {
        long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(5); boolean waiting = false;
        while (System.nanoTime() < end) {
            f.h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
            waiting = Boolean.TRUE.equals(f.h.jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE pid<>pg_backend_pid()
                  AND wait_event_type='Lock' AND query LIKE ? AND (?=false OR wait_event='advisory'))
                """, Boolean.class, pattern, advisory));
            if (waiting) break;
            try { Thread.sleep(10); }
            catch (InterruptedException interrupted) { Thread.currentThread().interrupt(); throw new AssertionError("Interrupted lock observation"); }
        }
        assertThat(waiting).as("real PostgreSQL delivery/subject lock observed").isTrue();
    }
}
