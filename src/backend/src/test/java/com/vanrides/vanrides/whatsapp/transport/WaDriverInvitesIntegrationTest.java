package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDriverTestSupport.json;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDriverInvitesIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_driver_invites");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaDriverTestSupport f;
    @BeforeEach void setup() { f = new WaDriverTestSupport(DATABASE.fixture()); }
    @Test void invitationExistsOnlyAfterExplicitConfirmationWithShownDefaults() {
        UUID actor = f.driver(); f.send(actor, "CONVIDAR"); var review = f.send(actor, "PADRÃO");
        assertTrue(review.text().contains("1 uso(s); validade 1440 minutos")); assertEquals(0, f.count("driver_invite", actor));
        f.confirm(actor, review);
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND max_uses=1 AND use_count=0 AND expires_at=created_at+interval '24 hours'", actor));
    }
    @Test void invitationCiphertextIsNotAReceiptDraftOrPublicOutboxPayload() {
        UUID actor = f.driver(); var result = f.invite(actor); var item = f.privateItem(actor); var reservation = f.s.authorize(item);
        String text = reservation.path("content").path("text").asText(), secret = code(text);
        assertTrue(text.contains("horário de Brasília")); assertFalse(text.contains("T12:00:00Z"));
        assertFalse(result.response().toString().contains(secret)); assertFalse(f.draft(actor).toString().contains(secret));
        assertFalse(f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?", UUID.fromString(item.path("outboxId").asText())).contains(secret));
        assertEquals(f.h.crypto.secretHash("passenger-invite", secret), f.s.string("SELECT code_hash FROM wa.driver_invite WHERE driver_id=?", actor));
        byte[] cipher = f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, UUID.fromString(item.path("outboxId").asText()));
        assertFalse(new String(cipher, StandardCharsets.ISO_8859_1).contains(secret));
    }
    @Test void submitReceiptPurgesCipherAndReplayDoesNotRegenerateCode() {
        UUID actor = f.driver(); f.invite(actor); var item = f.privateItem(actor); var reservation = f.s.authorize(item);
        UUID box = UUID.fromString(item.path("outboxId").asText());
        var result = json().put("outcome", "SUBMITTED").put("attemptToken", reservation.path("attemptToken").asText()).put("externalMessageId", "synthetic_invite_submit");
        var first = f.s.outbox.result(f.s.delivery, box, result); assertEquals(first, f.s.outbox.result(f.s.delivery, box, result));
        assertNull(f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box));
        assertEquals(1, f.count("driver_invite", actor)); assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box));
    }
    @Test void expiryBeforeAuthorizationDoesNotDiscloseCodeAndPurgesCipher() {
        UUID actor = f.driver(); f.send(actor, "CONVIDAR"); f.send(actor, "1"); f.confirm(actor, f.send(actor, "5"));
        var item = f.privateItem(actor); UUID box = UUID.fromString(item.path("outboxId").asText()); f.h.time.advance(301);
        assertThrows(WaProblem.class, () -> f.s.authorize(item));
        f.s.outboxClaim();
        assertNull(f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box));
    }
    @Test void revocationOfLeasedInvitationCancelsDeliveryWithoutConsumption() {
        UUID actor = f.driver(); f.invite(actor); var item = f.privateItem(actor); UUID box = UUID.fromString(item.path("outboxId").asText());
        var menu = f.send(actor, "ROTINAS"); assertTrue(menu.text().contains("criado 01/01/2030 09:00 (Brasília)"));
        assertTrue(menu.text().contains("válido até 02/01/2030 09:00 (Brasília); usos 0/1")); assertFalse(menu.text().contains("PASSAGEIRO "));
        var review = f.send(actor, "REVOGAR 1"); assertTrue(review.text().contains("criado 01/01/2030")); assertTrue(review.text().contains("usos 0/1"));
        f.confirm(actor, review);
        assertThrows(WaProblem.class, () -> f.s.authorize(item));
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box));
        assertNull(f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box));
        assertEquals(0, f.s.number("SELECT use_count FROM wa.driver_invite WHERE driver_id=?", actor));
    }
    @Test void codeFamilyIsRedactedAndDoesNotCollideWithDriverBootstrap() {
        UUID driver = f.driver(); f.invite(driver); String secret = code(f.s.authorize(f.privateItem(driver)).path("content").path("text").asText());
        UUID passenger = f.s.actor(); var lease = f.s.text(passenger, "passageiro\u00a0" + secret + ".");
        var stored = f.s.stored(lease.id()); assertFalse(stored.has("text")); assertFalse(stored.has("driverInviteCodeHash"));
        assertEquals(f.h.crypto.secretHash("passenger-invite", secret), stored.path("passengerInviteCodeHash").asText());
        assertEquals("UNSUPPORTED", lease.message().path("type").asText());
        assertFalse(f.context(lease).path("allowedCommands").toString().contains("CLAIM_DRIVER_INVITE"));
    }
    @Test void embeddedInvitationIsUnsupportedWithoutPersistingTheCode() {
        UUID actor = f.s.actor(); String secret = com.vanrides.vanrides.whatsapp.auth.WaCrypto.randomToken();
        var lease = f.s.text(actor, "mensagem PASSAGEIRO " + secret + " adicional");
        assertEquals("UNSUPPORTED", lease.message().path("type").asText()); assertFalse(f.s.stored(lease.id()).toString().contains(secret));
    }
    @Test void invalidLimitsNeverReachReviewAndCallerSuppliedLimitsAreRejected() {
        UUID actor = f.driver(); f.send(actor, "CONVIDAR"); f.send(actor, "11"); assertEquals("USES", f.draft(actor).path("stage").asText());
        f.send(actor, "10"); f.send(actor, "4"); assertEquals("TTL", f.draft(actor).path("stage").asText());
        var review = f.send(actor, "1440"); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        var payload = f.payload(f.context(lease), "CREATE_INVITE").put("maxUses", 10);
        assertEquals(WaProblem.Code.INVALID_PAYLOAD, assertThrows(WaProblem.class, () -> f.s.execute(lease, "CREATE_INVITE", payload)).code());
        assertEquals(0, f.count("driver_invite", actor));
    }
    @Test void forgedOtherOwnerInviteReferenceCannotBeRevoked() {
        UUID actor = f.driver(), other = f.driver(); f.invite(actor); f.invite(other);
        f.send(actor, "ROTINAS"); var review = f.send(actor, "REVOGAR 1"); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        String otherId = f.s.string("SELECT id::text FROM wa.driver_invite WHERE driver_id=?", other);
        var payload = f.payload(f.context(lease), "REVOKE_INVITE").put("inviteRef", "invite_" + otherId.replace("-", ""));
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, assertThrows(WaProblem.class, () -> f.s.execute(lease, "REVOKE_INVITE", payload)).code());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND revoked_at IS NOT NULL", other));
    }
    @Test void copiedCiphertextFailsAadAndNeverProducesAnAttempt() {
        UUID actor = f.driver(); f.invite(actor); var first = f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE recipient_identity_id=? AND private_invite_id IS NOT NULL", byte[].class, actor);
        UUID other = f.driver(); f.invite(other); var item = f.privateItem(other); UUID box = UUID.fromString(item.path("outboxId").asText());
        f.h.jdbc.update("UPDATE wa.message_outbox SET private_payload_ciphertext=? WHERE id=?", first, box);
        assertNotNull(f.s.outbox.authorize(f.s.delivery, box, json().put("leaseToken", item.path("leaseToken").asText())).error());
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box));
    }
    @Test void oversizedInvitationMenuCannotRevokeAnUnshownInvite() {
        UUID actor = f.driver();
        for (int i = 1; i <= 20; i++) f.h.jdbc.update("""
            INSERT INTO wa.driver_invite(id,driver_id,code_hash,created_at,expires_at,max_uses)
            VALUES (?,?,?,?,?,1)
            """, UUID.randomUUID(), actor, String.format("%064x", i), java.sql.Timestamp.from(f.h.clock.now()),
            java.sql.Timestamp.from(f.h.clock.now().plusSeconds(86400)));
        var menu = f.send(actor, "ROTINAS"); assertTrue(menu.text().contains("Nenhuma opção numerada está disponível"));
        assertTrue(f.draft(actor).path("inviteChoices").isEmpty()); f.send(actor, "REVOGAR 1");
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND revoked_at IS NOT NULL", actor));
        assertTrue(menu.text().length() <= 2000); assertFalse(menu.text().contains("PASSAGEIRO "));
    }
    private static String code(String text) {
        var match = Pattern.compile("PASSAGEIRO ([A-Za-z0-9_-]{43})").matcher(text); assertTrue(match.find()); return match.group(1);
    }
}
