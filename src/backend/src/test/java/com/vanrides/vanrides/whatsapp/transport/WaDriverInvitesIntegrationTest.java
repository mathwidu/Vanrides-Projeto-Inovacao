package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDriverTestSupport.json;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.regex.Pattern;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDriverInvitesIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_driver_invites");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaDriverTestSupport f;
    @BeforeEach void setup() { f = new WaDriverTestSupport(DATABASE.fixture()); }
    @Test void migratingFromV40PreservesAnExistingMultiUseInvitation() {
        f.h.jdbc.execute("CREATE DATABASE wa_invites_upgrade");
        String url = POSTGRES.getJdbcUrl().replace("/wa_driver_invites", "/wa_invites_upgrade");
        var source = new DriverManagerDataSource(url, POSTGRES.getUsername(), POSTGRES.getPassword());
        Flyway.configure().dataSource(source).target("40").load().migrate();
        var old = new WaTransportTestSupport(source, true);
        UUID driver = old.identity();
        UUID invite = UUID.randomUUID();
        old.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'DRIVER','ONBOARDING','Motorista sintético')", driver);
        old.jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", driver);
        old.jdbc.update("""
            INSERT INTO wa.driver_invite(id,driver_id,code_hash,expires_at,max_uses,use_count,created_at)
            VALUES (?,?,?,?,3,1,?)
            """, invite, driver, "f".repeat(64), Timestamp.from(old.clock.now().plusSeconds(600)), Timestamp.from(old.clock.now()));
        assertEquals(1, Flyway.configure().dataSource(source).load().migrate().migrationsExecuted);
        assertEquals(1L, old.jdbc.queryForObject("""
            SELECT count(*) FROM wa.driver_invite WHERE id=? AND max_uses=3 AND use_count=1
                AND NOT allow_first_contact AND onboarding_identity_id IS NULL
            """, Long.class, invite));
    }
    @Test void invitationExistsOnlyAfterExplicitConfirmationWithShownDefaults() {
        UUID actor = f.driver(); f.send(actor, "CONVIDAR"); var review = f.send(actor, "PADRÃO");
        assertTrue(review.text().contains("1 uso(s); validade 1440 minutos")); assertEquals(0, f.count("driver_invite", actor));
        f.confirm(actor, review);
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND max_uses=1 AND use_count=0 AND expires_at=created_at+interval '24 hours'", actor));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND allow_first_contact", actor));
    }
    @Test void shareableLinkContainsPrefilledCommandAndPastingTheLinkDoesNotExposeTheCode() {
        f = new WaDriverTestSupport(f.h, "12025550109");
        UUID driver = f.driver();
        var review = f.send(driver, "CONVIDAR LINK");
        assertTrue(review.text().contains("mensagem para encaminhar"));
        f.confirm(driver, review);
        String text = f.s.authorize(f.privateItem(driver)).path("content").path("text").asText();
        String secret = code(text);
        String link = "https://wa.me/12025550109?text=PASSAGEIRO%20" + secret;
        assertTrue(text.contains(link));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND allow_first_contact", driver));
        var pasted = f.s.text(driver, link);
        assertEquals("UNSUPPORTED", pasted.message().path("type").asText());
        assertFalse(f.s.stored(pasted.id()).toString().contains(secret));
    }
    @Test void directInvitationIsSentOnlyToTheOptedInPassengerAndDuplicateConfirmationDoesNotQueueAgain() {
        UUID driver = f.driver();
        UUID passenger = admittedPassenger(driver);
        f.s.finish(f.s.text(passenger, "ACEITO RECEBER CONVITES"));
        var review = f.send(driver, "CONVIDAR +1 (202) 555-0103");
        assertTrue(review.text().contains("final 0103"));
        assertFalse(review.lease().message().toString().contains("555-0103"));
        assertFalse(f.s.stored(review.lease().id()).toString().contains("12025550103"));
        var driverResult = f.confirm(driver, review);
        assertEquals(2, driverResult.response().path("responseIds").size());
        assertTrue(driverResult.text().contains("fila"));
        var repeated = f.confirm(driver, f.send(driver, "CONVIDAR +12025550103"));
        assertTrue(repeated.text().contains("convite ativo"));
        assertEquals(1, f.count("driver_invite", driver));
        var item = f.privateItem(passenger);
        String text = f.s.authorize(item).path("content").path("text").asText();
        assertTrue(text.contains("Seu motorista convidou você"));
        assertFalse(driverResult.text().contains(code(text)));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.driver_invite WHERE driver_id=? AND onboarding_identity_id=?", driver, passenger));
    }
    @Test void optOutAfterQueueingCancelsDeliveryAndOfferFallsBackToShareableLink() {
        UUID driver = f.driver();
        UUID passenger = admittedPassenger(driver);
        assertTrue(f.send(driver, "CONVIDAR +12025550103").text().contains("Envie LINK"));
        f.s.finish(f.s.text(passenger, "ACEITO RECEBER CONVITES"));
        f.confirm(driver, f.send(driver, "+12025550103"));
        var item = f.privateItem(passenger);
        f.h.time.advance(1);
        f.s.finish(f.s.text(passenger, "PARAR CONVITES"));
        UUID box = UUID.fromString(item.path("outboxId").asText());
        var result = f.s.outbox.authorize(f.s.delivery, box, json().put("leaseToken", item.path("leaseToken").asText()));
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, result.error());
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", box));
        assertNull(f.h.jdbc.queryForObject("SELECT private_payload_ciphertext FROM wa.message_outbox WHERE id=?", byte[].class, box));
        assertTrue(f.send(driver, "CONVIDAR +12025550103").text().contains("Envie LINK"));
    }
    private UUID admittedPassenger(UUID driver) {
        UUID passenger = f.h.identity();
        f.h.jdbc.update("UPDATE wa.identity SET test_cohort_id=(SELECT test_cohort_id FROM wa.identity WHERE id=?) WHERE id=?", driver, passenger);
        f.h.alias(passenger, "PN", "12025550103@s.whatsapp.net");
        f.h.jdbc.update("""
            INSERT INTO wa.team_admission_operation(id,github_actor_id,github_login,action,request_fingerprint,identity_id,correlation_id,created_at)
            VALUES (?,1,'synthetic-test','ADMIT_PASSENGER',?, ?,?,?)
            """, UUID.randomUUID(), "0".repeat(64), passenger, UUID.randomUUID(), Timestamp.from(f.h.clock.now()));
        return passenger;
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
