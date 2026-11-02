package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

@Testcontainers(disabledWithoutDocker = true)
class WaOutboxTransportIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_outbox_transport");
    private static final AtomicInteger ALIAS = new AtomicInteger();
    private WaTransportTestSupport h;
    private WaOutboxService service;
    private WaPrincipal worker;
    private record Box(UUID id, UUID actor, UUID event, String address) { }

    @BeforeEach void setup() {
        h = new WaTransportTestSupport(POSTGRES); service = service(h.settings);
        h.connection(h.principal(WaPrincipal.Kind.CHANNEL), "open", h.clock.now());
        worker = h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
    }
    private WaOutboxService service(WaSettings settings) {
        return h.proxy(new WaOutboxService(h.jdbc, new WaTransportAccess(h.jdbc, settings, h.clock),
            new WaDeliveryReconciler(h.jdbc, h.clock), settings, h.clock));
    }
    private Box enqueue() { return enqueue("PN"); }
    private Box enqueue(String type) {
        UUID actor = h.identity();
        String address = "00000000" + String.format("%04d", ALIAS.incrementAndGet()) + (type.equals("LID") ? "@lid" : "@s.whatsapp.net");
        h.alias(actor, type, address);
        UUID alias = h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=?", UUID.class, actor);
        UUID event = h.event(actor, json(WaJson.write(Map.of("text", "Olá", "senderAliasId", alias.toString()))));
        UUID command = UUID.randomUUID(), id = UUID.randomUUID();
        h.jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?,'START_ONBOARDING',?,'{}',?,?)
            """, command, actor, event, command.toString(), WaCrypto.digest("synthetic"),
            Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now().plusSeconds(86400)));
        WaFoundationResponses.save(h.jdbc, id, command, actor, "START_ONBOARDING", h.clock.now());
        return new Box(id, actor, event, address);
    }
    private ObjectNode claim() { return service.claim(worker, json("{\"batchLimit\":10,\"leaseSeconds\":60}")); }
    private ObjectNode oneClaim() {
        var response = claim(); assertThat(response.path("items")).hasSize(1);
        return (ObjectNode) response.path("items").get(0);
    }
    private ObjectNode authorize(ObjectNode lease) {
        return service.authorize(worker, UUID.fromString(lease.path("outboxId").asText()),
            json(WaJson.write(Map.of("leaseToken", lease.path("leaseToken").asText())))).requireSuccess();
    }
    private ObjectNode submit(ObjectNode reservation, String external) {
        return json(WaJson.write(Map.of("attemptToken", reservation.path("attemptToken").asText(), "outcome", "SUBMITTED", "externalMessageId", external)));
    }
    private UUID callback(String external, String status) {
        UUID id = UUID.randomUUID();
        h.jdbc.update("""
            INSERT INTO wa.integration_inbox(id,provider,instance_id,event_key,category,payload_json,created_at)
            VALUES (?,'EVOLUTION',?,?,'STATUS',?::jsonb,?)
            """, id, INSTANCE, id.toString(), WaJson.write(Map.of("externalMessageId", external, "status", status)), Timestamp.from(h.clock.now()));
        return id;
    }
    private String state(UUID id) { return h.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, id); }
    private void code(Runnable action, WaProblem.Code code) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WaProblem.class, failure -> assertThat(failure.code()).isEqualTo(code));
    }

    @Test void claimHidesContentAndAuthorizationUsesExactSourceAliasIncludingLid() {
        Box box = enqueue("LID");
        h.alias(box.actor(), "PN", "000000000000@s.whatsapp.net"); // old PN must not displace the source LID
        ObjectNode lease = oneClaim();
        assertThat(lease.has("recipient")).isFalse(); assertThat(lease.has("content")).isFalse();
        ObjectNode reservation = authorize(lease);
        assertThat(reservation.path("recipient").path("addressType").asText()).isEqualTo("LID");
        assertThat(reservation.path("recipient").path("address").asText()).isEqualTo(box.address());
        assertThat(reservation.path("content").path("type").asText()).isEqualTo("TEXT");
        assertThat(h.jdbc.queryForObject("SELECT state FROM wa.message_attempt", String.class)).isEqualTo("SUBMITTING");
        assertThat(h.jdbc.queryForObject("SELECT submit_token_hash FROM wa.message_attempt", String.class))
            .isEqualTo(WaCrypto.digest(reservation.path("attemptToken").asText()));
    }
    @Test void expiredUnsubmittedLeaseCanBeReclaimedButOldOwnerTokenCannotSubmit() {
        enqueue(); ObjectNode old = oneClaim(); h.time.advance(61); ObjectNode next = oneClaim();
        assertThat(next.path("leaseToken")).isNotEqualTo(old.path("leaseToken"));
        code(() -> authorize(old), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        assertThat(authorize(next).path("attemptToken").asText()).hasSize(43);
    }
    @Test void expiredResponseIsNotClaimedOrSubmitted() {
        Box box = enqueue(); h.time.advance(301); assertThat(claim().path("items")).isEmpty();
        assertThat(state(box.id())).isEqualTo("EXPIRED");
        assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt", Integer.class)).isZero();
    }
    @Test void consentProfileSuspensionCancelsPreviouslyLeasedResponseBeforeContentLeaves() {
        Box box = enqueue(); ObjectNode lease = oneClaim();
        h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','SUSPENDED')", box.actor());
        code(() -> authorize(lease), WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(state(box.id())).isEqualTo("CANCELED");
    }
    @Test void anotherPrincipalInstanceOrLeaseCannotReadDestination() {
        Box box = enqueue(); ObjectNode lease = oneClaim();
        var other = h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        ObjectNode body = json(WaJson.write(Map.of("leaseToken", lease.path("leaseToken").asText())));
        code(() -> service.authorize(other, box.id(), body), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        code(() -> service.authorize(h.principal(WaPrincipal.Kind.CONVERSATION_WORKER), box.id(), body), WaProblem.Code.ACTION_NOT_ALLOWED);
        code(() -> service.authorize(h.principal(WaPrincipal.Kind.DELIVERY_WORKER, "other-instance"), box.id(), body), WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
    }
    @Test void reservationCannotBeRepeatedEvenWhenOriginalHttpResponseWasLost() {
        Box box = enqueue(); ObjectNode lease = oneClaim(); authorize(lease);
        code(() -> authorize(lease), WaProblem.Code.IDEMPOTENCY_CONFLICT);
        h.time.advance(11); assertThat(claim().path("items")).isEmpty();
        assertThat(state(box.id())).isEqualTo("UNKNOWN");
        assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt", Integer.class)).isEqualTo(1);
    }
    @Test void ambiguousTimeoutIsDurableIdempotentAndNeverRequeued() {
        Box box = enqueue(); ObjectNode reservation = authorize(oneClaim());
        ObjectNode body = json(WaJson.write(Map.of("attemptToken", reservation.path("attemptToken").asText(), "outcome", "UNKNOWN", "failureCode", "TIMEOUT")));
        ObjectNode first = service.result(worker, box.id(), body);
        h.time.advance(61); assertThat(service.result(worker, box.id(), body)).isEqualTo(first);
        assertThat(claim().path("items")).isEmpty(); assertThat(state(box.id())).isEqualTo("UNKNOWN");
        code(() -> service.result(worker, box.id(), submit(reservation, "SYNTHETIC_LATE_ID")), WaProblem.Code.IDEMPOTENCY_CONFLICT);
    }
    @Test void callbackBeforeWorkerResultIsMatchedOnlyAfterExternalIdArrives() {
        Box box = enqueue(); ObjectNode reservation = authorize(oneClaim());
        UUID receipt = callback("SYNTHETIC_DELIVERED_1", "DELIVERY_ACK");
        assertThat(claim().path("items")).isEmpty(); assertThat(state(box.id())).isEqualTo("LEASED");
        ObjectNode result = service.result(worker, box.id(), submit(reservation, "SYNTHETIC_DELIVERED_1"));
        assertThat(result.path("state").asText()).isEqualTo("DELIVERED");
        assertThat(h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, receipt)).isEqualTo("COMPLETED");
    }
    @Test void callbacksAfterSubmissionAreMonotonicAndUnrelatedIdsRemainUnmatched() {
        Box box = enqueue(); ObjectNode reservation = authorize(oneClaim());
        service.result(worker, box.id(), submit(reservation, "SYNTHETIC_READ_1"));
        callback("SYNTHETIC_READ_1", "READ"); claim();
        callback("SYNTHETIC_READ_1", "SERVER_ACK"); callback("SYNTHETIC_READ_1", "ERROR");
        UUID unrelated = callback("UNRELATED_SYNTHETIC_ID", "READ"); claim();
        assertThat(state(box.id())).isEqualTo("READ");
        assertThat(h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, unrelated)).isEqualTo("PENDING");
    }
    @Test void delayedResultCanReconcileAbandonedAttemptWithoutAuthorizingANewSend() {
        Box box = enqueue(); ObjectNode reservation = authorize(oneClaim()); h.time.advance(11); claim();
        assertThat(state(box.id())).isEqualTo("UNKNOWN");
        assertThat(service.result(worker, box.id(), submit(reservation, "SYNTHETIC_LATE_1")).path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(claim().path("items")).isEmpty();
    }
    @Test void onlyOneAttemptPerInstanceCanBeSubmittingAndReceiptReleasesIt() {
        enqueue(); enqueue(); var leases = claim().path("items"); assertThat(leases).hasSize(2);
        ObjectNode first = authorize((ObjectNode) leases.get(0));
        code(() -> authorize((ObjectNode) leases.get(1)), WaProblem.Code.RATE_LIMITED);
        service.result(worker, UUID.fromString(first.path("outboxId").asText()), submit(first, "SYNTHETIC_SERIAL_1"));
        assertThat(authorize((ObjectNode) leases.get(1)).path("attemptToken").asText()).hasSize(43);
    }
    @Test void concurrentAuthorizersReserveExactlyOnce() throws Exception {
        enqueue(); ObjectNode lease = oneClaim(); var executor = Executors.newFixedThreadPool(2); var start = new CountDownLatch(1);
        try {
            var call = (java.util.concurrent.Callable<String>) () -> {
                start.await(5, TimeUnit.SECONDS);
                try { authorize(lease); return "RESERVED"; } catch (WaProblem error) { return error.code().name(); }
            };
            var first = executor.submit(call); var second = executor.submit(call); start.countDown();
            assertThat(java.util.List.of(first.get(10, TimeUnit.SECONDS), second.get(10, TimeUnit.SECONDS)))
                .containsExactlyInAnyOrder("RESERVED", "IDEMPOTENCY_CONFLICT");
            assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt", Integer.class)).isEqualTo(1);
        } finally { executor.shutdownNow(); }
    }
    @Test void integrationDisabledPreventsNewSendButAcceptsSubmittedReceipt() {
        Box box = enqueue(); ObjectNode reservation = authorize(oneClaim()); var disabled = service(settings(false, true));
        code(() -> disabled.claim(worker, json("{\"batchLimit\":1,\"leaseSeconds\":60}")), WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(disabled.result(worker, box.id(), submit(reservation, "SYNTHETIC_DISABLED_1")).path("state").asText()).isEqualTo("SUBMITTED");
    }
    @Test void invalidResultCannotDowngradeOrBorrowAnotherAttemptExternalId() {
        Box firstBox = enqueue(); ObjectNode first = authorize(oneClaim());
        service.result(worker, firstBox.id(), submit(first, "SYNTHETIC_UNIQUE_1"));
        Box secondBox = enqueue(); ObjectNode second = authorize(oneClaim());
        code(() -> service.result(worker, secondBox.id(), submit(second, "SYNTHETIC_UNIQUE_1")), WaProblem.Code.IDEMPOTENCY_CONFLICT);
        ObjectNode invalid = json(WaJson.write(Map.of("attemptToken", second.path("attemptToken").asText(), "outcome", "FAILED", "failureCode", "TIMEOUT")));
        code(() -> service.result(worker, secondBox.id(), invalid), WaProblem.Code.INVALID_PAYLOAD);
        assertThat(state(secondBox.id())).isEqualTo("LEASED");
    }
    @Test void revokedWorkerCannotFinishOrReauthorizeAnAttempt() {
        Box box = enqueue(); ObjectNode lease = oneClaim(), reservation = authorize(lease);
        h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?", Timestamp.from(h.clock.now()), worker.credentialId());
        code(() -> service.result(worker, box.id(), submit(reservation, "SYNTHETIC_REVOKED_1")), WaProblem.Code.INVALID_CREDENTIAL);
        code(() -> authorize(lease), WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(state(box.id())).isEqualTo("LEASED");
    }
    @Test void unimplementedPurposeAndNonCohortRecipientAreNeverClaimed() {
        Box box = enqueue(); h.jdbc.update("UPDATE wa.message_outbox SET purpose='SUPPORT',business_key='synthetic-support' WHERE id=?", box.id());
        Box outsider = enqueue(); h.jdbc.update("UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?", outsider.actor());
        assertThat(claim().path("items")).isEmpty(); assertThat(state(box.id())).isEqualTo("PENDING");
        assertThat(state(outsider.id())).isEqualTo("CANCELED");
    }
    @Test void expiryOfCredentialWhileWaitingForSuspendedProfileRollsBackCancellation() throws Exception {
        Box box = enqueue(); ObjectNode lease = oneClaim();
        h.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?", Timestamp.from(h.clock.now().plusSeconds(2)), worker.credentialId());
        assertThat(expireWhileProfileLocked(box, lease, 3)).isEqualTo("INVALID_CREDENTIAL");
        assertThat(state(box.id())).isEqualTo("LEASED");
    }
    @Test void expiryOfLeaseWhileWaitingForSuspendedProfileRollsBackCancellation() throws Exception {
        Box box = enqueue(); ObjectNode lease = oneClaim();
        assertThat(expireWhileProfileLocked(box, lease, 61)).isEqualTo("RESOURCE_NOT_ACCESSIBLE");
        assertThat(state(box.id())).isEqualTo("LEASED");
    }
    private String expireWhileProfileLocked(Box box, ObjectNode lease, int seconds) throws Exception {
        h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','SUSPENDED')", box.actor());
        var executor = Executors.newSingleThreadExecutor();
        var result = new java.util.concurrent.atomic.AtomicReference<java.util.concurrent.Future<String>>();
        try {
            h.transaction.executeWithoutResult(tx -> {
                h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", box.actor());
                result.set(executor.submit(() -> {
                    try { authorize(lease); return "RESERVED"; } catch (WaProblem error) { return error.code().name(); }
                }));
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                boolean waiting = false;
                while (System.nanoTime() < deadline) {
                    h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
                    waiting = Boolean.TRUE.equals(h.jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity
                          WHERE wait_event_type='Lock' AND query LIKE 'SELECT identity_id FROM wa.profile%FOR SHARE')
                        """, Boolean.class));
                    if (waiting) break;
                    try { Thread.sleep(10); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new AssertionError("Interrupted lock observation");
                    }
                }
                assertThat(waiting).as("real PostgreSQL profile lock observed").isTrue(); h.time.advance(seconds);
            });
            return result.get().get(10, TimeUnit.SECONDS);
        } finally { executor.shutdownNow(); }
    }
    @Test void failedDeliveryDominatesOlderServerAckInEitherOrderAndBatching() {
        for (boolean reverse : new boolean[]{false, true}) for (boolean separate : new boolean[]{false, true}) {
            Box box = enqueue(); ObjectNode reservation = authorize(oneClaim());
            String external = "SYNTHETIC_FAILURE_" + UUID.randomUUID();
            service.result(worker, box.id(), submit(reservation, external));
            callback(external, reverse ? "SERVER_ACK" : "ERROR"); if (separate) claim();
            h.time.advance(1); callback(external, reverse ? "ERROR" : "SERVER_ACK"); claim();
            assertThat(state(box.id())).isEqualTo("FAILED");
            callback(external, "DELIVERY_ACK"); claim();
            assertThat(state(box.id())).isEqualTo("DELIVERED");
        }
    }
}
