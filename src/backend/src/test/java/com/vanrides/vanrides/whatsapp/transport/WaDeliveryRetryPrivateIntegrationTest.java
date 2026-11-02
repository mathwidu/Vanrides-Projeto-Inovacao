package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaDeliveryRetryTestSupport.unsent;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaDeliveryRetryPrivateIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_delivery_retry_private");

    @Test void invitationCipherSurvivesOnlySafeRetryAndIsPurgedAfterSubmission() {
        var f = new WaDriverTestSupport(POSTGRES); UUID actor = f.driver(); f.invite(actor);
        var first = f.s.authorize(f.privateItem(actor)); UUID box = WaJson.uuid(first, "outboxId");
        byte[] cipher = cipher(f.h, box, "private_payload_ciphertext");
        var receipt = f.s.outbox.result(f.s.delivery, box, unsent(first));
        assertThat(cipher(f.h, box, "private_payload_ciphertext")).isEqualTo(cipher);
        assertThat(receipt.toString()).doesNotContain("PASSAGEIRO", "content", "recipient");
        f.h.time.advance(5); var next = f.s.authorize(f.privateItem(actor));
        assertThat(next.path("content")).isEqualTo(first.path("content"));
        submitted(f.s, next, "synthetic_invite_safe_retry");
        assertThat(cipher(f.h, box, "private_payload_ciphertext")).isNull();
        assertThat(f.s.outbox.result(f.s.delivery, box, unsent(first))).isEqualTo(receipt);
    }
    @Test void invitationRevokedDuringBackoffIsCanceledAndPurgedAtClaim() {
        var f = new WaDriverTestSupport(POSTGRES); UUID actor = f.driver(); f.invite(actor);
        var first = f.s.authorize(f.privateItem(actor)); UUID box = WaJson.uuid(first, "outboxId");
        f.s.outbox.result(f.s.delivery, box, unsent(first));
        f.h.jdbc.update("UPDATE wa.driver_invite SET revoked_at=? WHERE driver_id=?", Timestamp.from(f.h.clock.now()), actor);
        f.h.time.advance(5); f.s.outboxClaim();
        assertThat(f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box)).isEqualTo("CANCELED");
        assertThat(cipher(f.h, box, "private_payload_ciphertext")).isNull();
        assertThat(f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box)).isEqualTo(1);
    }
    @Test void expirationDuringBackoffPurgesCipherDespiteTheHistoricalSafeAttempt() {
        var f = new WaDriverTestSupport(POSTGRES); UUID actor = f.driver(); f.invite(actor);
        var first = f.s.authorize(f.privateItem(actor)); UUID box = WaJson.uuid(first, "outboxId");
        f.s.outbox.result(f.s.delivery, box, unsent(first)); f.h.time.advance(301); f.s.outboxClaim();
        assertThat(f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box)).isEqualTo("EXPIRED");
        assertThat(cipher(f.h, box, "private_payload_ciphertext")).isNull();
    }
    @Test void startLinkKeepsItsOriginalCipherAndIntentAcrossSafeRetry() {
        var t = new WaTrackingTestSupport(POSTGRES); var intent = t.request(false);
        UUID box = t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE start_intent_id=?", UUID.class, WaJson.uuid(intent, "intentId"));
        var first = t.f.s.authorize(item(t.f.s.outboxClaim(), box)); byte[] cipher = cipher(t.h, box, "start_payload_ciphertext");
        t.f.s.outbox.result(t.f.s.delivery, box, unsent(first));
        assertThat(cipher(t.h, box, "start_payload_ciphertext")).isEqualTo(cipher);
        t.h.time.advance(5); var next = t.f.s.authorize(item(t.f.s.outboxClaim(), box));
        assertThat(next.path("content")).isEqualTo(first.path("content"));
        submitted(t.f.s, next, "synthetic_start_safe_retry");
        assertThat(cipher(t.h, box, "start_payload_ciphertext")).isNull();
        assertThat(t.f.s.number("SELECT count(*) FROM wa.start_intent WHERE id=?", WaJson.uuid(intent, "intentId"))).isEqualTo(1);
    }
    @Test void consumedStartIntentCancelsSafeRetryAndPurgesItsSecretLink() {
        var t = new WaTrackingTestSupport(POSTGRES); var intent = t.request(false); UUID id = WaJson.uuid(intent, "intentId");
        UUID box = t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE start_intent_id=?", UUID.class, id);
        var first = t.f.s.authorize(item(t.f.s.outboxClaim(), box));
        t.tracking.activate(t.actor, id, t.activateBody(intent), "synthetic-retry-consumed-intent");
        assertThat(t.f.s.outbox.result(t.f.s.delivery, box, unsent(first)).path("state").asText()).isEqualTo("CANCELED");
        assertThat(cipher(t.h, box, "start_payload_ciphertext")).isNull();
    }
    private byte[] cipher(WaTransportTestSupport h, UUID box, String field) {
        return h.jdbc.queryForObject("SELECT " + field + " FROM wa.message_outbox WHERE id=?", byte[].class, box);
    }
    private void submitted(WaConversationTestSupport s, ObjectNode reservation, String external) {
        s.outbox.result(s.delivery, WaJson.uuid(reservation, "outboxId"), WaJson.object("{}")
            .put("attemptToken", reservation.path("attemptToken").asText()).put("outcome", "SUBMITTED").put("externalMessageId", external));
    }
    private ObjectNode item(ObjectNode response, UUID box) {
        for (var item : response.path("items")) if (box.toString().equals(item.path("outboxId").asText())) return (ObjectNode) item;
        throw new AssertionError("Expected original private outbox");
    }
}
