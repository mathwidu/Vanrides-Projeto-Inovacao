package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.commands.WaCommandService;
import com.vanrides.vanrides.whatsapp.commands.WaCommandStore;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingService;
import java.math.BigInteger;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

/** Real normalizer/ingress/claim fixtures; aliases are zero-only and all data is isolated synthetic data. */
final class WaConversationTestSupport {
    record Lease(UUID id, String token, ObjectNode message) { }
    final WaTransportTestSupport h;
    final WaCommandService commands;
    final WaInboxService inbox;
    final WaPairingService pairing;
    final WaOutboxService outbox;
    final WaEventService events;
    final WaPrincipal channel, worker, delivery;
    int actors;
    WaConversationTestSupport(PostgreSQLContainer<?> postgres) {
        h = new WaTransportTestSupport(postgres);
        channel = h.principal(WaPrincipal.Kind.CHANNEL); worker = h.principal(WaPrincipal.Kind.CONVERSATION_WORKER);
        delivery = h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        pairing = h.proxy(new WaPairingService(h.jdbc, h.settings, h.clock, h.crypto));
        commands = h.proxy(new WaCommandService(h.jdbc, new WaInboxAccess(h.jdbc, h.settings, h.clock),
            new WaCommandStore(h.jdbc, h.clock), new WaOnboardingCommands(h.jdbc, h.crypto, h.clock), pairing, h.settings, h.clock, h.crypto));
        inbox = h.proxy(new WaInboxService(h.jdbc, h.access, h.settings, h.clock));
        outbox = h.proxy(new WaOutboxService(h.jdbc, h.access, new WaDeliveryReconciler(h.jdbc, h.clock), h.settings, h.clock));
        events = h.proxy(new WaEventService(h.jdbc, h.access, new WaEvolutionNormalizer(h.crypto), h.settings, h.clock));
        h.connection(channel, "open", h.clock.now());
    }
    UUID actor() {
        UUID id = h.identity(); h.alias(id, "PN", "0".repeat(9 + ++actors) + "@s.whatsapp.net"); return id;
    }
    Lease text(UUID actor, String value) { return claim(pending(actor, json().put("conversation", value), h.clock.now())); }
    Lease textAt(UUID actor, String value, Instant occurred) { return claim(pending(actor, json().put("conversation", value), occurred)); }
    UUID pending(UUID actor, ObjectNode message, Instant occurred) {
        String alias = h.jdbc.queryForObject("SELECT alias_value FROM wa.identity_alias WHERE identity_id=? AND provider='EVOLUTION' AND instance_id=?",
            String.class, actor, WaTransportTestSupport.INSTANCE);
        ObjectNode envelope = json().put("event", "messages.upsert").put("instance", WaTransportTestSupport.INSTANCE);
        if (occurred != null) envelope.put("date_time", occurred.toString());
        ObjectNode data = envelope.putObject("data");
        data.putObject("key").put("id", "synthetic-conversation-" + UUID.randomUUID()).put("fromMe", false).put("remoteJid", alias);
        data.set("message", message);
        return WaJson.uuid(events.accept(channel, envelope), "receiptId");
    }
    Lease claim(UUID id) {
        var response = inbox.claim(worker, json().put("batchLimit", 10).put("leaseSeconds", 60));
        for (var item : response.path("items")) if (item.path("inboxEventId").asText().equals(id.toString()))
            return new Lease(id, item.path("inboxLease").asText(), (ObjectNode) item.path("message"));
        throw new AssertionError("Expected synthetic conversation event was not claimed");
    }
    ObjectNode body(Lease lease, String type, ObjectNode payload) {
        UUID actor = h.jdbc.queryForObject("SELECT identity_id FROM wa.integration_inbox WHERE id=?", UUID.class, lease.id());
        Long version = h.jdbc.queryForObject("SELECT COALESCE((SELECT version FROM wa.profile WHERE identity_id=?),0)", Long.class, actor);
        ObjectNode body = json().put("commandType", type).put("inboxEventId", lease.id().toString()).put("expectedVersion", version);
        body.set("payload", payload); return body;
    }
    WaDecision execute(Lease lease, String type, ObjectNode payload) { return execute(lease, body(lease, type, payload), "synthetic-070-" + UUID.randomUUID()); }
    WaDecision execute(Lease lease, ObjectNode body, String key) { return commands.execute(worker, lease.token(), key, body, UUID.randomUUID().toString()); }
    void finish(Lease lease) { inbox.result(worker, lease.id(), lease.token(), json().put("outcome", "COMPLETED")); }
    void registeredName(UUID actor) {
        Lease begin = text(actor, "Quero começar"); execute(begin, "START_ONBOARDING", json()).requireSuccess(); finish(begin);
        Lease name = text(actor, "Pessoa Sintética"); execute(name, "SET_PROFILE_NAME", json().put("name", "Pessoa Sintética")).requireSuccess(); finish(name);
    }
    void driver(UUID actor) {
        h.jdbc.update("""
            INSERT INTO wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at,driver_enabled,version)
            VALUES (?,'DRIVER','ACTIVE','Motorista sintético','wa-mvp-v1',?,true,1)
            """, actor, Timestamp.from(h.clock.now()));
        h.jdbc.update("INSERT INTO wa.driver_authorization(driver_id) VALUES (?)", actor);
    }
    String invite(UUID actor) {
        String code = WaCrypto.randomToken(); UUID operator = h.principal(WaPrincipal.Kind.OPERATOR, null).credentialId();
        h.jdbc.update("""
            INSERT INTO wa.driver_bootstrap_invite(id,identity_id,code_hash,created_at,expires_at,created_by)
            VALUES (?,?,?,?,?,?)
            """, UUID.randomUUID(), actor, h.crypto.secretHash("driver-bootstrap", code), Timestamp.from(h.clock.now()), Timestamp.from(h.clock.now().plusSeconds(300)), operator);
        return code;
    }
    ObjectNode beginPairing() throws Exception {
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        ECPublicKey key = (ECPublicKey) generator.generateKeyPair().getPublic();
        ObjectNode jwk = json().put("kty", "EC").put("crv", "P-256").put("x", coordinate(key.getW().getAffineX())).put("y", coordinate(key.getW().getAffineY()));
        var proof = new WaDpopVerifier.Proof(WaDpopVerifier.publicMaterial(jwk), WaCrypto.randomToken(), null, h.clock.now());
        ObjectNode request = json().put("pkceMethod", "S256").put("pkceChallenge", WaCrypto.randomToken()).put("platform", "ANDROID").put("appVersion", "synthetic-070");
        request.set("publicJwk", jwk);
        return pairing.begin(proof, request, "synthetic-pair-" + UUID.randomUUID()).requireSuccess();
    }
    ObjectNode outboxClaim() { return outbox.claim(delivery, json().put("batchLimit", 10).put("leaseSeconds", 60)); }
    ObjectNode authorize(ObjectNode item) {
        return outbox.authorize(delivery, WaJson.uuid(item, "outboxId"), json().put("leaseToken", item.path("leaseToken").asText())).requireSuccess();
    }
    void status(String external, String status) {
        ObjectNode envelope = json().put("event", "messages.update").put("instance", WaTransportTestSupport.INSTANCE).put("date_time", h.clock.now().toString());
        envelope.putObject("data").put("keyId", external).put("fromMe", true).put("status", status);
        events.accept(channel, envelope);
    }
    long number(String sql, Object... args) { return h.jdbc.queryForObject(sql, Long.class, args); }
    String string(String sql, Object... args) { return h.jdbc.queryForObject(sql, String.class, args); }
    ObjectNode stored(UUID event) { return WaJson.object(string("SELECT payload_json::text FROM wa.integration_inbox WHERE id=?", event)); }
    static ObjectNode json() { return WaJson.MAPPER.createObjectNode(); }
    static ObjectNode reply(String code) { return json().put("responseCode", code); }
    private static String coordinate(BigInteger value) {
        byte[] raw = value.toByteArray(), padded = new byte[32];
        System.arraycopy(raw, Math.max(0, raw.length - 32), padded, Math.max(0, 32 - raw.length), Math.min(32, raw.length));
        return WaCrypto.base64(padded);
    }
}
