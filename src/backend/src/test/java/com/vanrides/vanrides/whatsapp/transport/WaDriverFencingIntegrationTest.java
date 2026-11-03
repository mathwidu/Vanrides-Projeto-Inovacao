package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDriverTestSupport.json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDriverFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_driver_fencing");
    WaDriverTestSupport f;
    record Prepared(UUID actor, UUID routine, WaConversationTestSupport.Lease lease, ObjectNode body) { }
    @BeforeEach void setup() { f = new WaDriverTestSupport(POSTGRES); }
    @Test void concurrentRoutineVersionChangeRejectsAnOtherwiseValidMarker() {
        Prepared p = prepare(); f.h.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?", p.routine());
        assertEquals(WaProblem.Code.VERSION_CONFLICT, assertThrows(WaProblem.class, () -> execute(p)).code());
        assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", p.routine()));
    }
    @Test void targetConflictCommitsRecoveryAndReleasesTheSerialInboxWithoutReusingTheMarker() {
        Prepared p = prepare();
        String staleText = f.s.stored(p.lease().id()).path("text").asText();
        UUID next = f.s.pending(p.actor(), json().put("conversation", "ROTINAS"), f.h.clock.now());
        f.h.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?", p.routine());
        var rejected = f.s.execute(p.lease(), p.body(), "synthetic-conflict-recovery");
        assertEquals(WaProblem.Code.VERSION_CONFLICT, rejected.error());
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=? AND result_json->>'errorCode'='VERSION_CONFLICT'", p.lease().id()));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE r.inbox_event_id=? AND o.payload_json->>'text' LIKE '%Envie ROTINAS%'", p.lease().id()));
        assertEquals("MENU", f.draft(p.actor()).path("stage").asText());
        assertFalse(f.draft(p.actor()).has("confirmation"));
        assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", p.routine()));
        var ack = f.s.inbox.result(f.s.worker, p.lease().id(), p.lease().token(), json().put("outcome", "RETRY")
            .put("failureCode", "DEPENDENCY_UNAVAILABLE").put("retryAfterSeconds", 5));
        assertEquals("PROCESSED", ack.path("state").asText());
        f.execute(f.s.claim(next), true);
        var review = f.send(p.actor(), "PAUSAR 1");
        assertNotEquals(staleText, "CONFIRMAR " + f.marker(review));
        var old = f.send(p.actor(), staleText);
        assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", p.routine()));
        f.confirm(p.actor(), old);
        assertEquals("PAUSED", f.s.string("SELECT state FROM wa.routine WHERE id=?", p.routine()));
    }
    @Test void leaseExpiringDuringRoutineLockWaitRollsBackAllMutations() throws Exception {
        expiresWhileWaiting(false);
    }
    @Test void credentialExpiringDuringRoutineLockWaitRollsBackAllMutations() throws Exception {
        expiresWhileWaiting(true);
    }
    @Test void confirmationExpiringBetweenContextAndCommandRejectsDurablyWithoutBlockingTheNextMessage() {
        UUID actor = f.driver(), routine = f.routine(actor); f.send(actor, "ROTINAS"); var review = f.send(actor, "PAUSAR 1");
        f.h.time.advance(299); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        var body = f.s.body(lease, "SET_ROUTINE_STATE", f.payload(f.context(lease), "SET_ROUTINE_STATE"));
        f.h.time.advance(2);
        var rejected = f.s.execute(lease, body, "synthetic-expired-context");
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, rejected.error());
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?", lease.id()));
        assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", routine));
        var ack = f.s.inbox.result(f.s.worker, lease.id(), lease.token(), json().put("outcome", "PAUSED").put("failureCode", "INVALID_MODEL_OUTPUT"));
        assertEquals("PROCESSED", ack.path("state").asText());
        assertNotNull(f.send(actor, "ROTINAS"));
        f.confirm(actor, f.send(actor, "PAUSAR 1"));
        assertEquals("PAUSED", f.s.string("SELECT state FROM wa.routine WHERE id=?", routine));
    }
    @Test void confirmationTtlIsRevalidatedAfterLockWhileLeaseAndMessageRemainCurrent() throws Exception {
        UUID actor = f.driver(), routine = f.routine(actor); f.send(actor, "ROTINAS"); var review = f.send(actor, "PAUSAR 1");
        f.h.time.advance(299); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        var body = f.s.body(lease, "SET_ROUTINE_STATE", f.payload(f.context(lease), "SET_ROUTINE_STATE")); var executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false); long pid = lock(owner, "wa.routine", routine);
            var waiting = executor.submit(() -> f.s.execute(lease, body, "synthetic-confirmation-ttl").requireSuccess());
            awaitWait(pid); f.h.time.advance(2); owner.commit();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
            assertInstanceOf(WaProblem.class, failure.getCause()); assertEquals(WaProblem.Code.INTENT_EXPIRED, ((WaProblem) failure.getCause()).code());
            assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", routine));
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=? AND result_json->>'errorCode'='INTENT_EXPIRED'", lease.id()));
            var ack = f.s.inbox.result(f.s.worker, lease.id(), lease.token(), json().put("outcome", "PAUSED").put("failureCode", "INVALID_MODEL_OUTPUT"));
            assertEquals("PROCESSED", ack.path("state").asText());
            assertNotNull(f.send(actor, "ROTINAS"));
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void blockedDriverDoesNotStarveAnotherIdentity() throws Exception {
        Prepared p = prepare(); UUID other = f.driver(); var executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false); long pid = lock(owner, "wa.routine", p.routine());
            var waiting = executor.submit(() -> execute(p)); awaitWait(pid);
            var independent = f.send(other, "CRIAR"); assertEquals("APPLIED", independent.response().path("outcome").asText());
            owner.commit(); waiting.get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void concurrentConfirmationReplayCreatesOneInvitationAndOneDeliveryCipher() throws Exception {
        UUID actor = f.driver(); f.send(actor, "CONVIDAR"); var review = f.send(actor, "PADRÃO");
        var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review)); var body = f.s.body(lease, "CREATE_INVITE", f.payload(f.context(lease), "CREATE_INVITE"));
        var executor = Executors.newFixedThreadPool(2); CountDownLatch start = new CountDownLatch(1);
        try {
            java.util.concurrent.Callable<ObjectNode> action = () -> {
                assertTrue(start.await(5, TimeUnit.SECONDS)); return f.s.execute(lease, body, "synthetic-shared-invite").requireSuccess();
            };
            var a = executor.submit(action); var b = executor.submit(action); start.countDown();
            assertEquals(a.get(15, TimeUnit.SECONDS), b.get(15, TimeUnit.SECONDS));
            assertEquals(1, f.count("driver_invite", actor));
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=? AND private_payload_ciphertext IS NOT NULL", actor));
        } finally { start.countDown(); executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    @Test void revocationDoesNotWaitOnLockedOutboxAndLaterDeliveryRevalidates() throws Exception {
        UUID actor = f.driver(); f.invite(actor); var item = f.privateItem(actor); UUID box = UUID.fromString(item.path("outboxId").asText());
        f.send(actor, "ROTINAS"); var review = f.send(actor, "REVOGAR 1"); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        var body = f.s.body(lease, "REVOKE_INVITE", f.payload(f.context(lease), "REVOKE_INVITE")); var executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false); lock(owner, "wa.message_outbox", box);
            var revoke = executor.submit(() -> f.s.execute(lease, body, "synthetic-revoke-locked-box").requireSuccess());
            assertNotNull(revoke.get(5, TimeUnit.SECONDS)); owner.commit();
            assertNotNull(f.s.outbox.authorize(f.s.delivery, box, json().put("leaseToken", item.path("leaseToken").asText())).error());
            assertEquals(0, f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box));
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    private Prepared prepare() {
        UUID actor = f.driver(), routine = f.routine(actor); f.send(actor, "ROTINAS"); var review = f.send(actor, "PAUSAR 1");
        var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        return new Prepared(actor, routine, lease, f.s.body(lease, "SET_ROUTINE_STATE", f.payload(f.context(lease), "SET_ROUTINE_STATE")));
    }
    private ObjectNode execute(Prepared p) { return f.s.execute(p.lease(), p.body(), "synthetic-fenced-state").requireSuccess(); }
    private void expiresWhileWaiting(boolean credential) throws Exception {
        Prepared p = prepare();
        if (credential) f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(f.h.clock.now().plusSeconds(1)), f.s.worker.credentialId());
        long before = f.s.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=?", p.actor()); var executor = Executors.newSingleThreadExecutor();
        try (Connection owner = connection()) {
            owner.setAutoCommit(false); long pid = lock(owner, "wa.routine", p.routine());
            var waiting = executor.submit(() -> execute(p)); awaitWait(pid); f.h.time.advance(credential ? 2 : 61); owner.commit();
            var failure = assertThrows(java.util.concurrent.ExecutionException.class, () -> waiting.get(10, TimeUnit.SECONDS));
            assertInstanceOf(WaProblem.class, failure.getCause());
            assertEquals(credential ? WaProblem.Code.INVALID_CREDENTIAL : WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, ((WaProblem) failure.getCause()).code());
            assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", p.routine()));
            assertEquals(before, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=?", p.actor()));
        } finally { executor.shutdownNow(); assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS)); }
    }
    private Connection connection() throws Exception { return DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword()); }
    private long lock(Connection owner, String table, UUID id) throws Exception {
        long pid;
        try (var statement = owner.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) { rows.next(); pid = rows.getLong(1); }
        try (var statement = owner.prepareStatement("SELECT id FROM " + table + " WHERE id=? FOR UPDATE")) {
            statement.setObject(1, id); statement.executeQuery().close();
        }
        return pid;
    }
    private void awaitWait(long pid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (f.s.number("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?::integer=ANY(pg_blocking_pids(pid))", pid) > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Driver command did not reach its intended lock fence");
    }
}
