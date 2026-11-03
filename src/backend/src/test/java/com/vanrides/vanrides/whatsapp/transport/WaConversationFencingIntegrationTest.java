package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
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

import static com.vanrides.vanrides.whatsapp.transport.WaConversationTestSupport.*;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@Testcontainers(disabledWithoutDocker = true)
class WaConversationFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_conversation_fencing");
    private WaConversationTestSupport c;
    @BeforeEach void reset() { c = new WaConversationTestSupport(POSTGRES); }

    @Test void freshnessHasInclusiveFiveMinuteAndThirtySecondBoundsAndNeverTrustsMissingTime() {
        for (long offset : new long[]{-300, 30, -301, 31}) {
            UUID actor = c.actor(); Lease event = c.textAt(actor, "Quero começar", c.h.clock.now().plusSeconds(offset));
            String expected = offset == -300 || offset == 30 ? "CURRENT" : "STALE";
            assertThat(c.commands.context(c.worker, event.id(), event.token()).path("eventFreshness").asText()).isEqualTo(expected);
            if (expected.equals("CURRENT")) c.execute(event, "START_ONBOARDING", json()).requireSuccess();
            else assertThat(c.execute(event, "START_ONBOARDING", json()).error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
            c.finish(event);
        }
        UUID unknown = c.actor(); Lease noTime = c.textAt(unknown, "Quero começar", null);
        assertThat(c.commands.context(c.worker, noTime.id(), noTime.token()).path("eventFreshness").asText()).isEqualTo("UNKNOWN");
        assertThat(c.execute(noTime, "START_ONBOARDING", json()).error()).isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(c.number("SELECT count(*) FROM wa.profile")).isEqualTo(2);
    }

    @Test void oldHelpCanBeAnsweredWithoutStartingAProfileOrCreatingSupport() {
        UUID actor = c.actor(); Lease event = c.textAt(actor, "AJUDA", c.h.clock.now().minusSeconds(301));
        c.execute(event, "RESPOND_TO_MESSAGE", reply("HELP")).requireSuccess();
        assertThat(c.number("SELECT count(*) FROM wa.profile")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.support_case")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isEqualTo(1);
    }

    @Test void supportIsExplicitAtomicAndDeduplicatedUnderConcurrentRetries() throws Exception {
        UUID actor = c.actor();
        c.h.jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,version)
            VALUES (?,'PASSENGER','ACTIVE','Pessoa sintética','wa-mvp-v1',?,1)
            """, actor, Timestamp.from(c.h.clock.now()));
        Lease event = c.text(actor, "FALAR COM SUPORTE");
        ObjectNode body = c.body(event, "REQUEST_SUPPORT", json().put("reason", "OTHER").put("detail", "Texto livre não deve ser retido"));
        List<ObjectNode> results = race(() -> c.execute(event, body, "synthetic-support-a").requireSuccess(),
            () -> c.execute(event, body, "synthetic-support-b").requireSuccess());
        assertThat(results.get(0)).isEqualTo(results.get(1));
        assertThat(c.number("SELECT count(*) FROM wa.support_case WHERE identity_id=? AND state='OPEN'", actor)).isEqualTo(1);
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isEqualTo(1);
        assertThat(c.string("SELECT status FROM wa.profile WHERE identity_id=?", actor)).isEqualTo("ACTIVE");
        assertThat(c.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor))
            .contains(event.id().toString()).doesNotContain("Texto livre", "@", "latitude", "Hash");
        assertThat(c.commands.context(c.worker, event.id(), event.token()).path("registrationStep").asText()).isEqualTo("PAUSED");
        c.finish(event);
        UUID queued = c.pending(actor, json().put("conversation", "mensagem posterior"), c.h.clock.now());
        UUID independent = c.pending(c.actor(), json().put("conversation", "conversa independente"), c.h.clock.now());
        ObjectNode items = c.inbox.claim(c.worker, json().put("batchLimit", 10).put("leaseSeconds", 60));
        assertThat(items.path("items")).hasSize(1);
        assertThat(items.path("items").get(0).path("inboxEventId").asText()).isEqualTo(independent.toString());
        assertThat(c.string("SELECT state FROM wa.integration_inbox WHERE id=?", queued)).isEqualTo("PENDING");

        ObjectNode outbox = c.outboxClaim(); assertThat(outbox.path("items")).hasSize(1);
        ObjectNode item = (ObjectNode) outbox.path("items").get(0), reserved = c.authorize(item);
        assertThat(reserved.path("recipient").path("address").asText()).isEqualTo(c.string("SELECT alias_value FROM wa.identity_alias WHERE identity_id=?", actor));
        c.status("SYNTHETIC_SUPPORT_SENT", "READ");
        ObjectNode result = c.outbox.result(c.delivery, WaJson.uuid(item, "outboxId"), json().put("attemptToken", reserved.path("attemptToken").asText())
            .put("outcome", "SUBMITTED").put("externalMessageId", "SYNTHETIC_SUPPORT_SENT"));
        assertThat(result.path("state").asText()).isEqualTo("READ");
        c.status("SYNTHETIC_SUPPORT_SENT", "SERVER_ACK"); c.outboxClaim();
        assertThat(c.string("SELECT state FROM wa.message_outbox WHERE id=?", WaJson.uuid(item, "outboxId"))).isEqualTo("READ");
    }

    @Test void helpOrAStaleSupportRequestCannotPauseAConversation() {
        UUID actor = c.actor(); Lease help = c.text(actor, "AJUDA");
        assertThat(c.execute(help, "REQUEST_SUPPORT", json().put("reason", "HELP").put("detail", "Pedido forjado pelo worker")).error())
            .isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED); c.finish(help);
        Lease old = c.textAt(actor, "FALAR COM SUPORTE", c.h.clock.now().minusSeconds(301));
        assertThat(c.execute(old, "REQUEST_SUPPORT", json().put("reason", "OTHER").put("detail", "Pedido antigo")).error())
            .isEqualTo(WaProblem.Code.INTENT_EXPIRED);
        assertThat(c.number("SELECT count(*) FROM wa.support_case")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.conversation_state WHERE stage='PAUSED'")).isZero();
    }

    @Test void aLeaseExpiringWhileConsentWaitsForItsQuestionRollsBackEveryProvisionalEffect() throws Exception {
        consentWhileWaiting(61, WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, false);
    }

    @Test void freshnessExpiringDuringALockWaitRollsBackEvenWhenTheLeaseRemainsValid() throws Exception {
        consentWhileWaiting(301, WaProblem.Code.INTENT_EXPIRED, true);
    }

    @Test void forgedPrincipalAndReassignedLeaseCannotProduceEvenInformationalReplies() {
        UUID actor = c.actor(); Lease event = c.text(actor, "AJUDA");
        assertThatThrownBy(() -> c.commands.execute(c.delivery, event.token(), "synthetic-wrong-principal",
            c.body(event, "RESPOND_TO_MESSAGE", reply("HELP")), UUID.randomUUID().toString())).isInstanceOf(WaProblem.class);
        c.h.jdbc.update("UPDATE wa.integration_inbox SET lease_token_hash=?,lease_generation=lease_generation+1 WHERE id=?",
            com.vanrides.vanrides.whatsapp.auth.WaCrypto.digest(com.vanrides.vanrides.whatsapp.auth.WaCrypto.randomToken()), event.id());
        assertThatThrownBy(() -> c.execute(event, "RESPOND_TO_MESSAGE", reply("HELP"))).isInstanceOf(WaProblem.class);
        assertThat(c.number("SELECT count(*) FROM wa.command_receipt")).isZero();
        assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isZero();
    }

    private void consentWhileWaiting(int seconds, WaProblem.Code expected, boolean extendLease) throws Exception {
        UUID actor = c.actor(); c.registeredName(actor); Lease event = c.text(actor, "ACEITO");
        if (extendLease) c.h.jdbc.update("UPDATE wa.integration_inbox SET lease_expires_at=? WHERE id=?", Timestamp.from(c.h.clock.now().plusSeconds(600)), event.id());
        ExecutorService executor = Executors.newSingleThreadExecutor();
        try (Connection owner = DriverManager.getConnection(POSTGRES.getJdbcUrl(), POSTGRES.getUsername(), POSTGRES.getPassword())) {
            owner.setAutoCommit(false);
            try {
                long pid;
                try (var statement = owner.createStatement(); var rows = statement.executeQuery("SELECT pg_backend_pid()")) { rows.next(); pid = rows.getLong(1); }
                try (var statement = owner.prepareStatement("SELECT identity_id FROM wa.conversation_state WHERE identity_id=? FOR UPDATE")) {
                    statement.setObject(1, actor); statement.executeQuery().close();
                }
                Future<ObjectNode> waiting = executor.submit(() -> c.execute(event, "ACCEPT_NOTICE", json().put("noticeVersion", "wa-mvp-v1")).requireSuccess());
                awaitWait(pid); c.h.time.advance(seconds); owner.commit();
                assertThatThrownBy(() -> waiting.get(10, TimeUnit.SECONDS)).hasCauseInstanceOf(WaProblem.class)
                    .satisfies(failure -> assertThat(((WaProblem) failure.getCause()).code()).isEqualTo(expected));
                assertThat(c.number("SELECT count(*) FROM wa.command_receipt")).isEqualTo(2);
                assertThat(c.number("SELECT count(*) FROM wa.message_outbox")).isEqualTo(2);
                assertThat(c.number("SELECT count(*) FROM wa.profile WHERE identity_id=? AND notice_accepted_at IS NOT NULL", actor)).isZero();
            } finally { owner.rollback(); }
        } finally { executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    private void awaitWait(long pid) throws InterruptedException {
        long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
        while (System.nanoTime() < deadline) {
            if (c.number("SELECT count(*) FROM pg_stat_activity WHERE datname=current_database() AND wait_event_type='Lock' AND ?::integer=ANY(pg_blocking_pids(pid))", pid) > 0) return;
            Thread.sleep(20);
        }
        throw new AssertionError("Consent did not wait for its question lock");
    }
    private static List<ObjectNode> race(Supplier<ObjectNode> first, Supplier<ObjectNode> second) throws Exception {
        ExecutorService executor = Executors.newFixedThreadPool(2); CountDownLatch ready = new CountDownLatch(2), start = new CountDownLatch(1);
        try {
            Future<ObjectNode> a = executor.submit(() -> simultaneous(first, ready, start));
            Future<ObjectNode> b = executor.submit(() -> simultaneous(second, ready, start));
            assertThat(ready.await(5, TimeUnit.SECONDS)).isTrue(); start.countDown();
            return List.of(a.get(20, TimeUnit.SECONDS), b.get(20, TimeUnit.SECONDS));
        } finally { start.countDown(); executor.shutdownNow(); assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue(); }
    }
    private static ObjectNode simultaneous(Supplier<ObjectNode> action, CountDownLatch ready, CountDownLatch start) throws InterruptedException {
        ready.countDown(); if (!start.await(5, TimeUnit.SECONDS)) throw new AssertionError("Conversation race start timed out"); return action.get();
    }
}
