package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static com.vanrides.vanrides.whatsapp.transport.WaInboxTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;

@Testcontainers(disabledWithoutDocker = true)
class WaInboxTransportIntegrationTest {
    @Container
    static PostgreSQLContainer<?> postgres = postgres("vanrides_wa_inbox_transport");
    private WaTransportTestSupport h;
    private WaInboxTestSupport inbox;
    private WaPrincipal worker;

    @BeforeEach
    void reset() {
        inbox = new WaInboxTestSupport(postgres);
        h = inbox.transport;
        worker = inbox.worker;
    }

    @Test
    void shouldLeaseOnlyTheHeadToTwoWorkersRacingForTheSameIdentity() throws Exception {
        UUID actor = h.identity(), first = h.event(actor, json("{\"text\":\"primeira\"}"));
        h.time.advance(1);
        UUID second = h.event(actor, json("{\"text\":\"segunda\"}"));
        WaPrincipal other = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        List<Outcome> results = race(() -> inbox.claim(worker, 10, 30), () -> inbox.claim(other, 10, 30));
        assertThat(results).allSatisfy(result -> assertThat(result.failure()).isNull());
        assertThat(results.stream().mapToInt(result -> result.body().path("items").size()).sum()).isEqualTo(1);
        var item = first(results.stream().map(Outcome::body).filter(body -> !body.path("items").isEmpty()).findFirst().orElseThrow());
        assertThat(item.path("inboxEventId").asText()).isEqualTo(first.toString());
        assertThat(inbox.state(first)).isEqualTo("CLAIMED");
        assertThat(inbox.state(second)).isEqualTo("PENDING");
        assertThat(inbox.number("SELECT lease_generation FROM wa.integration_inbox WHERE id=?", first)).isEqualTo(1);
    }

    @Test
    void shouldHonorReceiveOrderWhenTimestampsMatchAndUuidOrderIsReversed() {
        UUID actor = h.identity();
        UUID first = UUID.fromString("ffffffff-ffff-ffff-ffff-ffffffffffff");
        UUID second = UUID.fromString("00000000-0000-0000-0000-000000000001");
        for (UUID id : List.of(first, second)) h.jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,created_at,not_before)
            VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','TEXT','{}'::jsonb,?,?)
            """, id, actor, INSTANCE, "synthetic-order-" + id, Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now()));
        ObjectNode item = first(inbox.claim(worker, 10, 30));
        assertThat(item.path("inboxEventId").asText()).isEqualTo(first.toString());
        assertThat(inbox.state(second)).isEqualTo("PENDING");
        inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}");
        assertThat(first(inbox.claim(worker, 10, 30)).path("inboxEventId").asText()).isEqualTo(second.toString());
    }

    @Test
    void shouldNeverSkipALockedHeadButStillClaimAnotherIdentity() throws Exception {
        UUID actor = h.identity(), head = h.event(actor, json("{}"));
        h.time.advance(1);
        UUID successor = h.event(actor, json("{}"));
        h.time.advance(1);
        UUID independent = h.event(h.identity(), json("{}"));
        ObjectNode response = inbox.whileHolding("SELECT id FROM wa.integration_inbox WHERE id=? FOR UPDATE", head,
            () -> inbox.claim(worker, 10, 30));
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("inboxEventId").asText()).isEqualTo(independent.toString());
        assertThat(inbox.state(head)).isEqualTo("PENDING");
        assertThat(inbox.state(successor)).isEqualTo("PENDING");
    }

    @Test
    void shouldSkipABusyIdentityWithoutStarvingIndependentConversations() throws Exception {
        UUID actor = h.identity(), head = h.event(actor, json("{}"));
        h.time.advance(1);
        UUID independent = h.event(h.identity(), json("{}"));
        ObjectNode response = inbox.whileHolding("SELECT id FROM wa.identity WHERE id=? FOR UPDATE", actor,
            () -> inbox.claim(worker, 1, 30));
        assertThat(response.path("items")).hasSize(1);
        assertThat(response.path("items").get(0).path("inboxEventId").asText()).isEqualTo(independent.toString());
        assertThat(inbox.state(head)).isEqualTo("PENDING");
    }

    @Test
    void shouldReacquireTheSameExpiredHeadAndFenceItsFormerOwner() {
        UUID actor = h.identity(), head = h.event(actor, json("{}"));
        h.time.advance(1);
        UUID successor = h.event(actor, json("{}"));
        ObjectNode original = first(inbox.claim(worker, 10, 2));
        h.time.advance(2);
        WaPrincipal other = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        ObjectNode reassigned = first(inbox.claim(other, 10, 30));
        assertThat(reassigned.path("inboxEventId").asText()).isEqualTo(head.toString());
        assertThat(reassigned.path("inboxLease").asText()).isNotEqualTo(original.path("inboxLease").asText());
        assertThat(inbox.number("SELECT lease_generation FROM wa.integration_inbox WHERE id=?", head)).isEqualTo(2);
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, original, "{\"outcome\":\"COMPLETED\"}"));
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, reassigned, "{\"outcome\":\"COMPLETED\"}"));
        assertThat(inbox.result(other, reassigned, "{\"outcome\":\"COMPLETED\"}").path("state").asText()).isEqualTo("PROCESSED");
        assertThat(first(inbox.claim(worker, 1, 30)).path("inboxEventId").asText()).isEqualTo(successor.toString());
    }

    @Test
    void shouldRejectTheFirstResultAtItsExactLeaseDeadline() {
        UUID head = h.event(h.identity(), json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 2));
        h.time.advance(2);
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"));
        assertThat(inbox.state(head)).isEqualTo("CLAIMED");
        assertThat(inbox.number("SELECT count(*) FROM wa.integration_inbox WHERE result_json IS NOT NULL")).isZero();
    }

    @Test
    void shouldBlockSuccessorsDuringRetryAndReplayCanonicalResultsUntilReassignment() {
        UUID actor = h.identity(), head = h.event(actor, json("{}"));
        h.time.advance(1);
        UUID successor = h.event(actor, json("{}"));
        ObjectNode item = first(inbox.claim(worker, 10, 2));
        String retry = "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":10,\"failureCode\":\"DEPENDENCY_UNAVAILABLE\"}";
        ObjectNode saved = inbox.result(worker, item, retry);
        assertThat(saved.path("state").asText()).isEqualTo("PENDING");
        h.time.advance(3);
        assertThat(inbox.result(worker, item, "{\"failureCode\":\"DEPENDENCY_UNAVAILABLE\",\"retryAfterSeconds\":10,\"outcome\":\"RETRY\"}"))
            .isEqualTo(saved);
        assertThat(inbox.claim(worker, 10, 30).path("items")).isEmpty();
        problem(WaProblem.Code.IDEMPOTENCY_CONFLICT, () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"));
        h.time.advance(7);
        ObjectNode retried = first(inbox.claim(worker, 10, 30));
        assertThat(retried.path("inboxEventId").asText()).isEqualTo(head.toString());
        assertThat(inbox.state(successor)).isEqualTo("PENDING");
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, item, retry));
        inbox.result(worker, retried, "{\"outcome\":\"COMPLETED\"}");
        assertThat(first(inbox.claim(worker, 1, 30)).path("inboxEventId").asText()).isEqualTo(successor.toString());
    }

    @Test
    void shouldPauseOneConversationWithoutBlockingOtherIdentities() {
        UUID actor = h.identity(), head = h.event(actor, json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        inbox.result(worker, item, "{\"outcome\":\"PAUSED\",\"failureCode\":\"HUMAN_SUPPORT\"}");
        h.time.advance(100);
        UUID successor = h.event(actor, json("{}"));
        UUID independent = h.event(h.identity(), json("{}"));
        var claimed = inbox.claim(worker, 10, 30).path("items");
        assertThat(claimed).hasSize(1);
        assertThat(claimed.get(0).path("inboxEventId").asText()).isEqualTo(independent.toString());
        assertThat(inbox.state(head)).isEqualTo("PAUSED");
        assertThat(inbox.state(successor)).isEqualTo("PENDING");
    }

    @Test
    void shouldRecoverCommittedSuccessAndRejectionReceiptsBeforeAskingForAnotherDecision() {
        UUID actor = h.identity(), applied = h.event(actor, json("{\"text\":\"Iniciar cadastro sintético\"}"));
        ObjectNode item = first(inbox.claim(worker, 1, 2));
        var commands = inbox.commands();
        assertThat(commands.execute(worker, item.path("inboxLease").asText(), "synthetic-inbox-lost-ack",
            json("{\"commandType\":\"START_ONBOARDING\",\"inboxEventId\":\"" + applied + "\",\"expectedVersion\":0,\"payload\":{}}"),
            UUID.randomUUID().toString()).error()).isNull();
        h.time.advance(1);
        UUID rejected = h.event(actor, json("{}"));
        inbox.receipt(actor, rejected, "{\"errorCode\":\"ACTION_NOT_ALLOWED\"}");
        h.time.advance(1);
        UUID fresh = h.event(actor, json("{}"));
        // Recovery also works after the command receipt's replay retention expires.
        h.jdbc.update("UPDATE wa.command_receipt SET expires_at=created_at+INTERVAL '1 second'");
        h.time.advance(3);
        ObjectNode next = first(inbox.claim(worker, 10, 30));
        assertThat(next.path("inboxEventId").asText()).isEqualTo(fresh.toString());
        assertThat(inbox.state(applied)).isEqualTo("COMPLETED");
        assertThat(inbox.state(rejected)).isEqualTo("COMPLETED");
        assertThat(inbox.number("SELECT count(*) FROM wa.command_receipt")).isEqualTo(2);
        assertThat(inbox.number("SELECT version FROM wa.profile WHERE identity_id=?", actor)).isEqualTo(1);
        assertThat(inbox.number("SELECT count(*) FROM wa.message_outbox")).isEqualTo(1);
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"));
    }

    @Test
    void shouldTreatACommittedCommandAsProcessedEvenWhenTheWorkerRequestsRetry() {
        UUID actor = h.identity(), event = h.event(actor, json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        inbox.receipt(actor, event, "{\"outcome\":\"APPLIED\"}");
        ObjectNode response = inbox.result(worker, item,
            "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":1,\"failureCode\":\"INVALID_MODEL_OUTPUT\"}");
        assertThat(response.path("state").asText()).isEqualTo("PROCESSED");
        assertThat(inbox.state(event)).isEqualTo("COMPLETED");
        assertThat(inbox.claim(worker, 10, 30).path("items")).isEmpty();
        assertThat(inbox.number("SELECT count(*) FROM wa.command_receipt")).isEqualTo(1);
    }

    @Test
    void shouldProjectOnlyOpaqueReferencesAndPreserveThe040AuthoritativeLease() {
        UUID pin = h.event(h.identity(), json("{\"pin\":{\"latitude\":1.25,\"longitude\":2.5},\"occurredAt\":\"2030-01-01T12:00:00Z\",\"senderAliasId\":\"00000000-0000-0000-0000-000000000001\"}"));
        h.jdbc.update("UPDATE wa.integration_inbox SET normalized_kind='PIN' WHERE id=?", pin);
        UUID secret = h.event(h.identity(), json("{\"text\":\"synthetic-code-must-not-escape\",\"driverInviteCodeHash\":\"opaque-hash\"}"));
        UUID pairing = h.event(h.identity(), json("{\"pairingCodeHash\":\"opaque-hash\"}"));
        UUID actor = h.identity(), previous = h.event(actor, json("{\"text\":\"anterior\"}"));
        h.jdbc.update("UPDATE wa.integration_inbox SET state='COMPLETED',message_id='synthetic-provider-reference' WHERE id=?", previous);
        UUID reply = h.event(actor, json("{\"text\":\"resposta\",\"replyTo\":\"synthetic-provider-reference\"}"));
        ObjectNode response = inbox.claim(worker, 10, 30);
        assertThat(response.path("items")).hasSize(4);
        assertThat(response.toString()).doesNotContain("latitude", "longitude", "occurredAt", "senderAliasId", "opaque-hash", "synthetic-code", "synthetic-provider-reference");
        ObjectNode pinItem = itemFor(response, pin);
        assertThat(pinItem.path("message").path("type").asText()).isEqualTo("STATIC_PIN_REFERENCE");
        assertThat(pinItem.path("message").size()).isEqualTo(2);
        assertThat(itemFor(response, secret).path("message").path("type").asText()).isEqualTo("UNSUPPORTED");
        assertThat(itemFor(response, pairing).path("message").path("type").asText()).isEqualTo("UNSUPPORTED");
        assertThat(itemFor(response, reply).path("message").path("replyTo").asText()).isEqualTo("event_" + previous);
        for (var claimed : response.path("items")) {
            assertThat(claimed.path("inboxLease").asText()).matches("[A-Za-z0-9_-]{43}");
            assertThat(claimed.path("message").path("sourceMessageId").asText()).isEqualTo("event_" + claimed.path("inboxEventId").asText());
        }
        var context = h.transaction.execute(status -> new WaInboxAccess(h.jdbc, h.settings, h.clock)
            .lock(pin, pinItem.path("inboxLease").asText(), worker));
        assertThat(context.identityId()).isNotNull();
        assertThat(context.normalizedPayload().path("pin").path("latitude").asDouble()).isEqualTo(1.25);
        assertThat(inbox.number("SELECT count(*) FROM wa.conversation_state")).isZero();
    }

    @Test
    void shouldBindResultsToTheCredentialTokenInstanceAndDerivedActor() {
        UUID actor = h.identity(), event = h.event(actor, json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        WaPrincipal other = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(other, item, "{\"outcome\":\"COMPLETED\"}"));
        ObjectNode forgedToken = item.deepCopy().put("inboxLease", WaCrypto.randomToken());
        problem(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, () -> inbox.result(worker, forgedToken, "{\"outcome\":\"COMPLETED\"}"));
        WaPrincipal forgedInstance = new WaPrincipal(worker.kind(), worker.credentialId(), null, null, "another-instance", null, 0, false);
        problem(WaProblem.Code.INVALID_CREDENTIAL, () -> inbox.result(forgedInstance, item, "{\"outcome\":\"COMPLETED\"}"));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> inbox.result(h.principal(WaPrincipal.Kind.DELIVERY_WORKER), item, "{\"outcome\":\"COMPLETED\"}"));
        problem(WaProblem.Code.INVALID_PAYLOAD, () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\",\"identityId\":\"forged-actor\"}"));
        ObjectNode saved = inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}");
        h.time.advance(100);
        assertThat(inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}")).isEqualTo(saved);
        problem(WaProblem.Code.IDEMPOTENCY_CONFLICT, () -> inbox.result(worker, item, "{\"outcome\":\"PAUSED\",\"failureCode\":\"HUMAN_SUPPORT\"}"));
        assertThat(inbox.state(event)).isEqualTo("COMPLETED");
    }

    @Test
    void shouldBlockNewClaimsWhenDisabledButAllowOutstandingResults() {
        UUID event = h.event(h.identity(), json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        var disabledSettings = settings(false, true);
        var disabled = h.proxy(new WaInboxService(h.jdbc, new WaTransportAccess(h.jdbc, disabledSettings, h.clock), disabledSettings, h.clock));
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> disabled.claim(worker, json("{\"batchLimit\":1,\"leaseSeconds\":30}")));
        assertThat(disabled.result(worker, event, item.path("inboxLease").asText(), json("{\"outcome\":\"COMPLETED\"}")).path("state").asText())
            .isEqualTo("PROCESSED");
    }

    @Test
    void shouldRecheckCredentialExpiryAfterWaitingForTheInboxLock() throws Exception {
        UUID event = h.event(h.identity(), json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        inbox.expireCredentialIn(1);
        Outcome response = inbox.whileWaiting("SELECT id FROM wa.integration_inbox WHERE id=? FOR UPDATE", event,
            () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"), () -> h.time.advance(1));
        assertThat(response.failure()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(inbox.state(event)).isEqualTo("CLAIMED");
        assertThat(inbox.number("SELECT count(*) FROM wa.integration_inbox WHERE result_json IS NOT NULL")).isZero();
    }

    @Test
    void shouldRecheckLeaseExpiryAfterWaitingForTheInboxLock() throws Exception {
        UUID event = h.event(h.identity(), json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 1));
        Outcome response = inbox.whileWaiting("SELECT id FROM wa.integration_inbox WHERE id=? FOR UPDATE", event,
            () -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"), () -> h.time.advance(1));
        assertThat(response.failure()).isEqualTo(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE);
        assertThat(inbox.state(event)).isEqualTo("CLAIMED");
        assertThat(inbox.number("SELECT count(*) FROM wa.integration_inbox WHERE result_json IS NOT NULL")).isZero();
    }

    @Test
    void shouldRecheckCredentialExpiryAfterWaitingForCredentialShareLock() throws Exception {
        UUID event = h.event(h.identity(), json("{}"));
        inbox.expireCredentialIn(1);
        Outcome response = inbox.whileWaiting("SELECT id FROM wa.service_credential WHERE id=? FOR UPDATE", worker.credentialId(),
            () -> inbox.claim(worker, 1, 30), () -> h.time.advance(1));
        assertThat(response.failure()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL);
        assertThat(inbox.state(event)).isEqualTo("PENDING");
    }

    @Test
    void shouldIgnoreUnadmittedAndBlockedIdentitiesAndDenyTheirOutstandingResults() {
        UUID unknown = UUID.randomUUID();
        h.jdbc.update("INSERT INTO wa.identity(id) VALUES (?)", unknown);
        UUID unknownEvent = h.event(unknown, json("{}"));
        UUID suspended = h.identity(), closed = h.identity();
        inbox.profile(suspended, "SUSPENDED"); inbox.profile(closed, "CLOSED");
        UUID suspendedEvent = h.event(suspended, json("{}")), closedEvent = h.event(closed, json("{}"));
        UUID admitted = h.identity(), event = h.event(admitted, json("{}"));
        ObjectNode response = inbox.claim(worker, 10, 30);
        assertThat(response.path("items")).hasSize(1);
        assertThat(first(response).path("inboxEventId").asText()).isEqualTo(event.toString());
        inbox.profile(admitted, "CLOSED");
        problem(WaProblem.Code.ACTION_NOT_ALLOWED, () -> inbox.result(worker, first(response), "{\"outcome\":\"COMPLETED\"}"));
        assertThat(inbox.state(unknownEvent)).isEqualTo("PENDING");
        assertThat(inbox.state(suspendedEvent)).isEqualTo("PENDING");
        assertThat(inbox.state(closedEvent)).isEqualTo("PENDING");
        assertThat(inbox.number("SELECT count(*) FROM wa.test_cohort")).isEqualTo(3);
    }

    @Test
    void shouldValidateClosedClaimAndOutcomeSpecificResultShapes() {
        UUID event = h.event(h.identity(), json("{}"));
        for (String invalid : List.of("{}", "{\"batchLimit\":0,\"leaseSeconds\":30}", "{\"batchLimit\":11,\"leaseSeconds\":30}",
                "{\"batchLimit\":1,\"leaseSeconds\":0}", "{\"batchLimit\":1,\"leaseSeconds\":61}",
                "{\"batchLimit\":1.0,\"leaseSeconds\":30}", "{\"batchLimit\":1,\"leaseSeconds\":30,\"actor\":\"forged\"}"))
            problem(WaProblem.Code.INVALID_PAYLOAD, () -> inbox.service.claim(worker, json(invalid)));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        for (String invalid : List.of("{}", "{\"outcome\":\"UNKNOWN\"}", "{\"outcome\":null}",
                "{\"outcome\":\"COMPLETED\",\"failureCode\":\"HUMAN_SUPPORT\"}",
                "{\"outcome\":\"RETRY\"}", "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":1}",
                "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":0,\"failureCode\":\"DEPENDENCY_UNAVAILABLE\"}",
                "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":61,\"failureCode\":\"DEPENDENCY_UNAVAILABLE\"}",
                "{\"outcome\":\"RETRY\",\"retryAfterSeconds\":1,\"failureCode\":\"HUMAN_SUPPORT\"}",
                "{\"outcome\":\"PAUSED\"}", "{\"outcome\":\"PAUSED\",\"failureCode\":\"DEPENDENCY_UNAVAILABLE\"}",
                "{\"outcome\":\"PAUSED\",\"failureCode\":\"HUMAN_SUPPORT\",\"retryAfterSeconds\":1}"))
            problem(WaProblem.Code.INVALID_PAYLOAD, () -> inbox.result(worker, item, invalid));
        assertThat(inbox.state(event)).isEqualTo("CLAIMED");
        assertThat(inbox.number("SELECT count(*) FROM wa.integration_inbox WHERE result_json IS NOT NULL")).isZero();
    }

    @Test
    void shouldSerializeConcurrentResultsAndReplayOnlyTheCommittedRequest() throws Exception {
        UUID event = h.event(h.identity(), json("{}"));
        ObjectNode item = first(inbox.claim(worker, 1, 30));
        List<Outcome> responses = race(() -> inbox.result(worker, item, "{\"outcome\":\"COMPLETED\"}"),
            () -> inbox.result(worker, item, "{\"outcome\":\"PAUSED\",\"failureCode\":\"HUMAN_SUPPORT\"}"));
        assertThat(responses.stream().filter(response -> response.failure() == null)).hasSize(1);
        assertThat(responses.stream().filter(response -> response.failure() == WaProblem.Code.IDEMPOTENCY_CONFLICT)).hasSize(1);
        Outcome winner = responses.stream().filter(response -> response.failure() == null).findFirst().orElseThrow();
        ObjectNode replay = inbox.result(worker, item, winner.body().path("state").asText().equals("PROCESSED")
            ? "{\"outcome\":\"COMPLETED\"}" : "{\"outcome\":\"PAUSED\",\"failureCode\":\"HUMAN_SUPPORT\"}");
        assertThat(replay).isEqualTo(winner.body());
        assertThat(inbox.number("SELECT lease_generation FROM wa.integration_inbox WHERE id=?", event)).isEqualTo(1);
        assertThat(inbox.number("SELECT count(*) FROM wa.command_receipt")).isZero();
    }

}
