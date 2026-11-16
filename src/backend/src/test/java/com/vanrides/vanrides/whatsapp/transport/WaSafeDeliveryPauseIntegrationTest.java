package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorFixtures;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorMode;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorRequest;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorReset;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorRevocation;
import com.vanrides.vanrides.whatsapp.operator.WaOperatorService;
import com.vanrides.vanrides.whatsapp.tracking.WaRunFinishes;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

/** Real transactional services/PG only. No HTTP, provider POST, WhatsApp or physical sensor proof. */
@Testcontainers(disabledWithoutDocker = true)
class WaSafeDeliveryPauseIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_safe_delivery_pause");

    @Test void deliveryRevocationPreservesStopAndReconciliationThenNewCredentialCannotReplayUnknown() throws Exception {
        var tracking = new WaTrackingTestSupport(POSTGRES);
        tracking.activate();
        tracking.tracking.samples(tracking.actor, tracking.session(), tracking.batch(tracking.sample(1, 0, 5)));
        var h = tracking.h; var conversation = tracking.f.s; var outbox = conversation.outbox;
        // The existing tracking fixture builds a driver/roster; its setup replies are outside this scenario.
        h.jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE state IN ('PENDING','LEASED')");
        var health = new WaDeliveryRecoveryTestSupport(h);
        var mode = new WaOperatorMode(null, new MockEnvironment().withProperty("spring.main.web-application-type", "none"));
        var operator = h.proxy(new WaOperatorService(h.jdbc, h.settings, h.crypto, h.clock, mode,
            new WaOperatorFixtures(h.jdbc, h.crypto), new WaOperatorReset(h.jdbc), new WaOperatorRevocation(h.jdbc)));
        UUID batch = h.jdbc.queryForObject("SELECT test_cohort_id FROM wa.identity WHERE id=?", UUID.class, tracking.f.driver);

        UUID uncertain = message(conversation);
        ObjectNode oldLease = claim(outbox, conversation.delivery);
        assertThat(WaJson.uuid(oldLease, "outboxId")).isEqualTo(uncertain);
        h.time.advance(1);
        UUID pending = message(conversation);
        ObjectNode pendingLease = claim(outbox, conversation.delivery);
        assertThat(WaJson.uuid(pendingLease, "outboxId")).isEqualTo(pending);
        ObjectNode reservation = authorize(outbox, conversation.delivery, oldLease);

        ObjectNode revoke = request(h, batch, "revoke-service-credential")
            .put("credentialId", conversation.delivery.credentialId().toString()).put("reasonCode", "OPERATIONAL_PAUSE");
        assertThat(execute(operator, revoke).path("state").asText()).isEqualTo("REVOKED");
        denied(() -> outbox.claim(conversation.delivery, json("{\"batchLimit\":1,\"leaseSeconds\":60}")));
        denied(() -> authorize(outbox, conversation.delivery, pendingLease));
        denied(() -> outbox.result(conversation.delivery, uncertain, json("{}")
            .put("attemptToken", reservation.path("attemptToken").asText()).put("outcome", "UNKNOWN").put("failureCode", "TIMEOUT")));
        assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.service_credential WHERE revoked_at IS NOT NULL", Integer.class)).isEqualTo(1);
        for (var principal : List.of(conversation.channel, conversation.worker, health.job))
            assertThat(h.jdbc.queryForObject("SELECT revoked_at IS NULL FROM wa.service_credential WHERE id=?", Boolean.class, principal.credentialId())).isTrue();

        h.time.advance(11);
        assertThat(health.tick(100, null).path("processed").asInt()).isEqualTo(1);
        assertThat(state(h, uncertain)).isEqualTo("UNKNOWN");
        assertThat(h.jdbc.queryForObject("SELECT state FROM wa.message_attempt WHERE outbox_id=?", String.class, uncertain)).isEqualTo("UNKNOWN");
        long version = h.jdbc.queryForObject("SELECT version FROM wa.operation_run WHERE id=?", Long.class, tracking.run());
        Boolean finished = h.transaction.execute(tx -> new WaRunFinishes(h.jdbc, h.settings)
            .manual(tracking.f.driver, tracking.run(), version, h.clock.now()));
        assertThat(finished).isTrue();
        ObjectNode stop = tracking.tracking.status(tracking.actor);
        assertThat(stop.path("action").asText()).isEqualTo("STOP");
        assertThat(stop.path("sessionState").asText()).isEqualTo("STOP_PENDING");
        String terminal = h.jdbc.queryForObject("SELECT state FROM wa.tracking_session WHERE id=?", String.class, tracking.session());
        for (String state : List.of("STOP_REQUESTED", "REVOKED", "EXPIRED")) {
            h.jdbc.update("UPDATE wa.tracking_session SET state=? WHERE id=?", state, tracking.session());
            ObjectNode metrics = monitor(h);
            assertThat(metrics.path("stopPending").asInt()).as(state).isEqualTo(1);
            assertThat(metrics.path("stopOld").asInt()).as(state).isEqualTo(1);
        }
        h.jdbc.update("UPDATE wa.tracking_session SET state=? WHERE id=?", terminal, tracking.session());
        tracking.tracking.state(tracking.actor, tracking.session(), tracking.state("STOPPED")
            .put("stopRequestRef", stop.path("stopRequestRef").asText()), "safe-pause-stop-ack");
        assertThat(tracking.tracking.status(tracking.actor).path("sessionState").asText()).isEqualTo("STOPPED");
        assertThat(monitor(h).path("stopPending").asInt()).isZero();
        assertThat(monitor(h).path("stopOld").asInt()).isZero();
        assertThat(state(h, uncertain)).isEqualTo("UNKNOWN");

        h.time.advance(50); // The old pending lease expires; the UNKNOWN message is still within its original TTL.
        ObjectNode provision = request(h, batch, "provision-service-credential").put("principal", "DELIVERY_WORKER")
            .put("allowedInstanceId", INSTANCE).put("expiresAt", h.clock.now().plusSeconds(3600).toString());
        ObjectNode created = execute(operator, provision);
        var replacement = new WaPrincipal(WaPrincipal.Kind.DELIVERY_WORKER, WaJson.uuid(created, "credentialId"),
            null, null, INSTANCE, null, 0, false);
        assertThat(replacement.credentialId()).isNotEqualTo(conversation.delivery.credentialId());
        ObjectNode freshLease = claim(outbox, replacement);
        assertThat(WaJson.uuid(freshLease, "outboxId")).isEqualTo(pending);
        ObjectNode fresh = authorize(outbox, replacement, freshLease);
        assertThat(outbox.result(replacement, pending, json("{}")
            .put("attemptToken", fresh.path("attemptToken").asText()).put("outcome", "SUBMITTED")
            .put("externalMessageId", "SYNTHETIC_SAFE_PAUSE_RESUME")).path("state").asText()).isEqualTo("SUBMITTED");
        assertThat(state(h, uncertain)).isEqualTo("UNKNOWN");
        assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", Integer.class, uncertain)).isEqualTo(1);
        assertThat(h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", Integer.class, pending)).isEqualTo(1);
        denied(() -> outbox.claim(conversation.delivery, json("{\"batchLimit\":1,\"leaseSeconds\":60}")));
    }

    private UUID message(WaConversationTestSupport s) {
        UUID actor = s.actor(); var event = s.text(actor, "Olá");
        s.execute(event, "START_ONBOARDING", json("{}")).requireSuccess(); s.finish(event);
        return s.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=?", UUID.class, actor);
    }
    private ObjectNode monitor(WaTransportTestSupport h) throws Exception {
        var root = java.nio.file.Path.of(System.getProperty("basedir", System.getProperty("user.dir")));
        String source = java.nio.file.Files.readString(root.resolve("../../deploy/homologacao/operations.py"));
        int start = source.indexOf("WITH grants AS", source.indexOf("AGGREGATES ="));
        String sql = source.substring(start, source.indexOf("\nROLLBACK;", start))
            .replace("'vanrides-mvp-hml'", "'" + h.settings.environment() + "'")
            .replace("'vanrides-hml'", "'" + INSTANCE + "'")
            .replace("clock_timestamp()", "TIMESTAMPTZ '" + h.clock.now().plusSeconds(61) + "'");
        // Run the helper's real SELECT with only scope/clock adapted to this synthetic fixture.
        return json(h.jdbc.queryForObject(sql, String.class));
    }
    private ObjectNode claim(WaOutboxService outbox, WaPrincipal worker) {
        var result = outbox.claim(worker, json("{\"batchLimit\":1,\"leaseSeconds\":60}"));
        assertThat(result.path("items")).hasSize(1); return (ObjectNode) result.path("items").get(0);
    }
    private ObjectNode authorize(WaOutboxService outbox, WaPrincipal worker, ObjectNode lease) {
        return outbox.authorize(worker, WaJson.uuid(lease, "outboxId"), json("{}")
            .put("leaseToken", lease.path("leaseToken").asText())).requireSuccess();
    }
    private ObjectNode request(WaTransportTestSupport h, UUID batch, String operation) {
        return json("{}").put("operation", operation).put("environment", h.settings.environment())
            .put("batchId", batch.toString()).put("operatorCredential", h.settings.operatorCredential());
    }
    private ObjectNode execute(WaOperatorService operator, ObjectNode request) {
        var result = new AtomicReference<ObjectNode>();
        operator.execute(WaOperatorRequest.parse(WaJson.canonical(request).getBytes(java.nio.charset.StandardCharsets.UTF_8)),
            value -> result.set(value.deepCopy())); return result.get();
    }
    private String state(WaTransportTestSupport h, UUID box) {
        return h.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, box);
    }
    private void denied(Runnable action) {
        assertThatThrownBy(action::run).isInstanceOfSatisfying(WaProblem.class,
            problem -> assertThat(problem.code()).isEqualTo(WaProblem.Code.INVALID_CREDENTIAL));
    }
}
