package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Connection;
import java.sql.DriverManager;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.transport.WaEvolutionNormalizerTest.fixture;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WaEvolutionIngressIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = WaTransportTestSupport.postgres("vanrides_wa_evolution_ingress");
    private static final String PN = "000000000000@s.whatsapp.net";
    private static final String LID = "000000000000000@lid";
    private WaTransportTestSupport h;
    private WaEventService service;
    private WaPrincipal channel;

    @BeforeEach void reset() { reset(true); }
    private void reset(boolean cohortOnly) {
        h = new WaTransportTestSupport(postgres, cohortOnly);
        service = h.proxy(new WaEventService(h.jdbc, h.access, new WaEvolutionNormalizer(h.crypto), h.settings, h.clock));
        channel = h.principal(WaPrincipal.Kind.CHANNEL);
    }

    @Test void shouldCommitOnlyNormalizedPayloadAndReferenceTheExactAuthenticatedSenderAlias() {
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        ObjectNode envelope = message("synthetic-message-ingress-001");
        envelope.put("apikey", "discard-me").put("sender", "discard-me").put("destination", "discard-me");
        ((ObjectNode) envelope.path("data")).put("pushName", "discard-me").put("profilePictureUrl", "discard-me");
        ObjectNode accepted = service.accept(channel, envelope);
        UUID id = id(accepted);
        assertThat(accepted.size()).isEqualTo(3);
        assertThat(accepted.path("status").asText()).isEqualTo("PERSISTED");
        assertThat(state(id)).isEqualTo("PENDING");
        assertThat(h.jdbc.queryForObject("SELECT identity_id FROM wa.integration_inbox WHERE id=?", UUID.class, id)).isEqualTo(actor);
        ObjectNode payload = payload(id);
        UUID sender = WaJson.uuid(payload, "senderAliasId");
        assertThat(h.jdbc.queryForObject("SELECT alias_value FROM wa.identity_alias WHERE id=?", String.class, sender)).isEqualTo(PN);
        assertThat(payload.size()).isEqualTo(3);
        assertThat(payload.toString()).doesNotContain(PN, LID, "discard-me", "apikey", "sender\"", "destination", "profile");
        assertThat(number("SELECT count(*) FROM wa.identity_alias WHERE identity_id=?", actor)).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM wa.profile")).isZero();
        assertThat(number("SELECT count(*) FROM wa.message_outbox")).isZero();
    }

    @Test void shouldReturnExistingReceiptBeforeResolvingChangedAliasesOrPayload() {
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        ObjectNode envelope = message("synthetic-duplicate-001");
        ObjectNode first = service.accept(channel, envelope); ObjectNode original = payload(id(first));
        ((ObjectNode) envelope.path("data").path("key")).put("remoteJid", "0000000000000000@lid").remove("remoteJidAlt");
        ((ObjectNode) envelope.path("data").path("message")).put("conversation", "Texto diferente em retry");
        ObjectNode duplicate = service.accept(channel, envelope);
        assertThat(id(duplicate)).isEqualTo(id(first)); assertThat(duplicate.path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(payload(id(first))).isEqualTo(original);
        assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.identity")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(2);
    }

    @Test void shouldCreateOnlyOneIdentityAndReceiptForConcurrentDuplicateDeliveries() throws Exception {
        reset(false);
        List<ObjectNode> results = race(() -> service.accept(channel, message("synthetic-race-same-001")),
            () -> service.accept(channel, message("synthetic-race-same-001")));
        assertThat(results.stream().map(r -> r.path("status").asText()).toList()).containsExactlyInAnyOrder("PERSISTED", "DUPLICATE");
        assertThat(id(results.get(0))).isEqualTo(id(results.get(1)));
        assertThat(number("SELECT count(*) FROM wa.identity")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.profile")).isZero();
    }

    @Test void shouldResolveConcurrentPnAndLidMessagesToOneActorAndKeepEachPrincipalAlias() throws Exception {
        reset(false);
        ObjectNode pn = message("synthetic-race-pn-001"), lid = message("synthetic-race-lid-001");
        ((ObjectNode) lid.path("data").path("key")).put("remoteJid", LID).put("remoteJidAlt", PN);
        List<ObjectNode> results = race(() -> service.accept(channel, pn), () -> service.accept(channel, lid));
        assertThat(results.stream().map(r -> r.path("status").asText()).toList()).containsOnly("PERSISTED");
        assertThat(number("SELECT count(*) FROM wa.identity")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(2);
        assertThat(number("SELECT count(DISTINCT identity_id) FROM wa.integration_inbox")).isEqualTo(1);
        assertThat(h.jdbc.queryForObject("SELECT alias_value FROM wa.identity_alias WHERE id=?", String.class,
            WaJson.uuid(payload(id(results.get(0))), "senderAliasId"))).isEqualTo(PN);
        assertThat(h.jdbc.queryForObject("SELECT alias_value FROM wa.identity_alias WHERE id=?", String.class,
            WaJson.uuid(payload(id(results.get(1))), "senderAliasId"))).isEqualTo(LID);
    }

    @Test void shouldKeepAliasConflictsAsDurableSkippedReceiptsWithoutMergingActors() {
        UUID first = h.identity(), second = h.identity(); h.alias(first, "PN", PN); h.alias(second, "LID", LID);
        ObjectNode receipt = service.accept(channel, message("synthetic-conflict-001"));
        assertThat(receipt.path("status").asText()).isEqualTo("IGNORED"); assertThat(state(id(receipt))).isEqualTo("SKIPPED");
        assertThat(payload(id(receipt))).isEqualTo(WaJson.object("{\"reason\":\"ALIAS_CONFLICT\"}"));
        assertThat(number("SELECT count(*) FROM wa.identity")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(2);
        assertThat(number("SELECT count(*) FROM wa.integration_inbox WHERE identity_id IS NULL")).isEqualTo(1);
    }

    @Test void shouldSerializeDisjointAliasPairsAgainstTheSameIdentityWithoutAddingAnotherPn() throws Exception {
        UUID actor = h.identity(); h.alias(actor, "PN", PN); h.alias(actor, "LID", LID);
        ObjectNode first = message("synthetic-conflict-pn-001"), second = message("synthetic-conflict-pn-002");
        ((ObjectNode) first.path("data").path("key")).put("remoteJid", "0000000000000@s.whatsapp.net");
        ((ObjectNode) second.path("data").path("key")).put("remoteJidAlt", "0000000000000000@lid");
        List<ObjectNode> results = race(() -> service.accept(channel, first), () -> service.accept(channel, second));
        assertThat(results.stream().map(r -> r.path("status").asText()).toList()).containsOnly("IGNORED");
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(2);
        for (ObjectNode result : results) assertThat(payload(id(result))).isEqualTo(WaJson.object("{\"reason\":\"ALIAS_CONFLICT\"}"));
    }

    @Test void shouldNotTurnUnknownCohortMessagesIntoAReactivatableBacklog() {
        ObjectNode envelope = message("synthetic-cohort-unknown-001");
        ObjectNode ignored = service.accept(channel, envelope);
        assertThat(ignored.path("status").asText()).isEqualTo("IGNORED");
        assertThat(state(id(ignored))).isEqualTo("SKIPPED");
        assertThat(payload(id(ignored))).isEqualTo(WaJson.object("{\"reason\":\"COHORT_REQUIRED\"}"));
        assertThat(number("SELECT count(*) FROM wa.identity")).isZero();
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isZero();
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        ObjectNode retry = service.accept(channel, envelope);
        assertThat(id(retry)).isEqualTo(id(ignored)); assertThat(retry.path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(state(id(ignored))).isEqualTo("SKIPPED");
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(1);
        assertThat(service.accept(channel, message("synthetic-cohort-admitted-002")).path("status").asText()).isEqualTo("PERSISTED");
    }

    @Test void shouldSkipExistingActorsOutsideTheCohortAndSuspendedProfilesWithoutAttachingAliases() {
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        h.jdbc.update("UPDATE wa.test_cohort SET environment='another-test' WHERE id=(SELECT test_cohort_id FROM wa.identity WHERE id=?)", actor);
        ObjectNode outside = service.accept(channel, message("synthetic-cohort-outside-001"));
        assertThat(payload(id(outside))).isEqualTo(WaJson.object("{\"reason\":\"IDENTITY_NOT_ALLOWED\"}"));
        assertThat(state(id(outside))).isEqualTo("SKIPPED");
        h.jdbc.update("UPDATE wa.test_cohort SET environment='transport-test'");
        h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','SUSPENDED')", actor);
        ObjectNode suspended = service.accept(channel, message("synthetic-cohort-suspended-002"));
        assertThat(payload(id(suspended))).isEqualTo(WaJson.object("{\"reason\":\"IDENTITY_NOT_ALLOWED\"}"));
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(1);
    }

    @Test void shouldKeepUnsupportedHumanMessagesPendingWithOnlyAReasonAndSenderReference() {
        UUID actor = h.identity(); h.alias(actor, "LID", LID);
        ObjectNode receipt = service.accept(channel, fixture("message-live-location"));
        assertThat(state(id(receipt))).isEqualTo("PENDING");
        assertThat(payload(id(receipt)).size()).isEqualTo(2);
        assertThat(payload(id(receipt)).path("reason").asText()).isEqualTo("LIVE_LOCATION");
        assertThat(payload(id(receipt)).has("senderAliasId")).isTrue();
        assertThat(payload(id(receipt)).has("pin")).isFalse();
    }

    @Test void shouldPersistAStaticPinAtZeroWithoutKeepingQuotedPayloadOrProviderLocationLabels() {
        UUID actor = h.identity(); h.alias(actor, "LID", LID);
        ObjectNode envelope = fixture("message-pin-zero");
        ((ObjectNode) envelope.path("data").path("message").path("locationMessage"))
            .put("name", "discard-me").put("address", "discard-me").put("jpegThumbnail", "discard-me");
        ((ObjectNode) envelope.path("data")).putObject("contextInfo").putObject("quotedMessage")
            .putObject("locationMessage").put("degreesLatitude", 90).put("degreesLongitude", 180);
        ObjectNode receipt = service.accept(channel, envelope);
        ObjectNode payload = payload(id(receipt));
        assertThat(state(id(receipt))).isEqualTo("PENDING");
        assertThat(payload.path("pin")).isEqualTo(WaJson.object("{\"latitude\":0.0,\"longitude\":0.0}"));
        assertThat(payload.size()).isEqualTo(3); assertThat(payload.has("senderAliasId")).isTrue();
        assertThat(payload.toString()).doesNotContain("discard-me", "quotedMessage", "jpegThumbnail");
    }

    @Test void shouldPersistStatusAndConnectionReceiptsEvenWhenTheFeatureIsDisabled() {
        var disabled = WaTransportTestSupport.settings(false, true);
        WaEventService off = h.proxy(new WaEventService(h.jdbc, new WaTransportAccess(h.jdbc, disabled, h.clock),
            new WaEvolutionNormalizer(h.crypto), disabled, h.clock));
        ObjectNode ignored = off.accept(channel, message("synthetic-disabled-001"));
        assertThat(payload(id(ignored))).isEqualTo(WaJson.object("{\"reason\":\"DISABLED\"}"));
        ObjectNode status = off.accept(channel, fixture("status-read"));
        assertThat(state(id(status))).isEqualTo("PENDING");
        ObjectNode connection = off.accept(channel, fixture("connection-open"));
        assertThat(state(id(connection))).isEqualTo("COMPLETED");
        assertThat(number("SELECT count(*) FROM wa.integration_inbox WHERE category='MESSAGE' AND state='PENDING'")).isZero();
        assertThat(number("SELECT count(*) FROM wa.identity")).isZero();
    }

    @Test void shouldKeepMessageAndEachOutboundStatusInSeparateDedupNamespaces() {
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        ObjectNode message = service.accept(channel, message("synthetic-outbound-001"));
        ObjectNode read = service.accept(channel, fixture("status-read"));
        ObjectNode delivery = fixture("status-read"); ((ObjectNode) delivery.path("data")).put("status", "DELIVERY_ACK");
        ObjectNode delivered = service.accept(channel, delivery);
        ObjectNode duplicate = service.accept(channel, fixture("status-read"));
        assertThat(id(read)).isNotEqualTo(id(message)).isNotEqualTo(id(delivered));
        assertThat(id(duplicate)).isEqualTo(id(read)); assertThat(duplicate.path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isEqualTo(3);
        assertThat(number("SELECT count(*) FROM wa.integration_inbox WHERE category='MESSAGE'")).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.message_outbox")).isZero();
    }

    @Test void shouldPersistGenericUnknownAndEchoReceiptsWithoutCreatingActors() {
        ObjectNode envelope = fixture("connection-open").put("event", "future.provider.event");
        ObjectNode first = service.accept(channel, envelope);
        envelope.put("apikey", "discard-me"); ObjectNode duplicate = service.accept(channel, envelope);
        assertThat(id(duplicate)).isEqualTo(id(first)); assertThat(duplicate.path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(payload(id(first))).isEqualTo(WaJson.object("{\"reason\":\"UNSUPPORTED_EVENT\"}"));
        ObjectNode echo = service.accept(channel, fixture("message-echo"));
        assertThat(state(id(echo))).isEqualTo("SKIPPED"); assertThat(payload(id(echo)).size()).isEqualTo(1);
        assertThat(number("SELECT count(*) FROM wa.identity")).isZero();
    }

    @Test void shouldRejectForgedInstancesWrongPrincipalRevokedCredentialAndInvalidCallbackBeforePersistence() {
        assertProblem(() -> service.accept(channel, message("synthetic-forged-001").put("instance", "forged-instance")), WaProblem.Code.ACTION_NOT_ALLOWED);
        WaPrincipal worker = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        assertProblem(() -> service.accept(worker, message("synthetic-forged-002")), WaProblem.Code.ACTION_NOT_ALLOWED);
        ObjectNode invalid = fixture("status-read"); ((ObjectNode) invalid.path("data")).remove("keyId");
        assertProblem(() -> service.accept(channel, invalid), WaProblem.Code.INVALID_PAYLOAD);
        h.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?", java.sql.Timestamp.from(h.clock.now()), channel.credentialId());
        assertProblem(() -> service.accept(channel, message("synthetic-revoked-003")), WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isZero();
        assertThat(number("SELECT count(*) FROM wa.identity")).isZero();
    }

    @Test void shouldRollBackTheReceiptAndNewActorWhenTheEnclosingTransactionFails() {
        reset(false);
        assertThatThrownBy(() -> h.transaction.execute(status -> {
            service.accept(channel, message("synthetic-rollback-001"));
            throw new IllegalStateException("SYNTHETIC_ROLLBACK");
        })).isInstanceOf(IllegalStateException.class).hasMessage("SYNTHETIC_ROLLBACK");
        assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isZero();
        assertThat(number("SELECT count(*) FROM wa.identity")).isZero();
        assertThat(number("SELECT count(*) FROM wa.identity_alias")).isZero();
        assertThat(service.accept(channel, message("synthetic-rollback-001")).path("status").asText()).isEqualTo("PERSISTED");
    }

    @Test void shouldRevalidateCredentialExpiryAfterWaitingForTheIdentityLock() throws Exception {
        UUID actor = h.identity(); h.alias(actor, "PN", PN);
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = DriverManager.getConnection(postgres.getJdbcUrl(), postgres.getUsername(), postgres.getPassword())) {
            owner.setAutoCommit(false);
            try {
                long ownerPid;
                try (var statement = owner.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) {
                    rows.next(); ownerPid = rows.getLong(1);
                }
                try (var statement = owner.prepareStatement("SELECT id FROM wa.identity WHERE id=? FOR UPDATE")) {
                    statement.setObject(1, actor); statement.executeQuery().close();
                }
                Future<WaProblem.Code> waiting = executor.submit(() -> {
                    try { service.accept(channel, message("synthetic-expiry-wait-001")); return null; }
                    catch (WaProblem failure) { return failure.code(); }
                });
                awaitDatabaseWait(ownerPid); assertThat(waiting.isDone()).isFalse();
                h.time.advance(3600); owner.commit();
                assertThat(waiting.get(10, TimeUnit.SECONDS)).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
                assertThat(number("SELECT count(*) FROM wa.integration_inbox")).isZero();
                assertThat(number("SELECT count(*) FROM wa.identity_alias")).isEqualTo(1);
            } finally { owner.rollback(); }
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }

    private void awaitDatabaseWait(long ownerPid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (number("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?::integer=ANY(pg_blocking_pids(pid))", ownerPid) > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Ingress did not wait on the identity transaction");
    }
    private static List<ObjectNode> race(Supplier<ObjectNode> first, Supplier<ObjectNode> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2);
        CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Future<ObjectNode> a = executor.submit(() -> execute(first, ready, start));
            Future<ObjectNode> b = executor.submit(() -> execute(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally {
            start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }
    private static ObjectNode execute(Supplier<ObjectNode> action, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Concurrent ingress start timed out");
        return action.get();
    }
    private static void assertProblem(Supplier<ObjectNode> action, WaProblem.Code code) {
        assertThatThrownBy(action::get).isInstanceOfSatisfying(WaProblem.class, problem -> assertThat(problem.code()).isEqualTo(code));
    }
    private static ObjectNode message(String id) {
        ObjectNode envelope = fixture("message-text-pn-lid");
        ((ObjectNode) envelope.path("data").path("key")).put("id", id);
        return envelope;
    }
    private static UUID id(ObjectNode receipt) { return WaJson.uuid(receipt, "receiptId"); }
    private long number(String sql, Object... arguments) { return h.jdbc.queryForObject(sql, Long.class, arguments); }
    private String state(UUID id) { return h.jdbc.queryForObject("SELECT state FROM wa.integration_inbox WHERE id=?", String.class, id); }
    private ObjectNode payload(UUID id) { return WaJson.object(h.jdbc.queryForObject("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?", String.class, id)); }
}
