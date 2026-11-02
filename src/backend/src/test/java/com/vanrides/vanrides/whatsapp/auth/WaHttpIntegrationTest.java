package com.vanrides.vanrides.whatsapp.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@ActiveProfiles("local")
@ExtendWith(OutputCaptureExtension.class)
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT, properties = {
    "spring.flyway.baseline-on-migrate=false", "spring.jpa.hibernate.ddl-auto=validate", "spring.jpa.show-sql=false",
    "vanrides.auth.jwt-secret=synthetic-http-test-secret-at-least-32-characters", "vanrides.push.provider=none",
    "vanrides.wa.enabled=true", "vanrides.wa.environment=test", "vanrides.wa.public-base-url=http://127.0.0.1:8080",
    "vanrides.wa.operator-expires-at=2035-01-01T00:00:00Z"
})
@Testcontainers(disabledWithoutDocker = true)
class WaHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_http_test").withUsername("vanrides_test").withPassword("synthetic-http-database");
    @DynamicPropertySource static void configure(DynamicPropertyRegistry properties) {
        properties.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        properties.add("spring.datasource.username", POSTGRES::getUsername);
        properties.add("spring.datasource.password", POSTGRES::getPassword);
        properties.add("vanrides.wa.hash-key", () -> key(11));
        properties.add("vanrides.wa.response-key", () -> key(22));
        properties.add("vanrides.wa.operator-credential", () -> key(33));
    }
    @LocalServerPort private int port;
    @Autowired private JdbcTemplate jdbc;
    private final HttpClient client = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private static final String BEGIN = "/driver-app/v1/pairings/begin";

    private HttpResponse<String> send(String method, String path, String body, Map<String, String> headers) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + path)).timeout(Duration.ofSeconds(15));
        headers.forEach(request::header);
        request.method(method, body == null ? HttpRequest.BodyPublishers.noBody() : HttpRequest.BodyPublishers.ofString(body));
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }
    private String proof(KeyPair key, String method, String path, String body, String nonce) {
        var claims = claims(method, path, body == null ? null : body.getBytes(StandardCharsets.UTF_8));
        claims.put("iat", Instant.now().getEpochSecond());
        if (nonce == null) claims.remove("nonce"); else claims.put("nonce", nonce);
        // Public origin is fixed to emulate a proxy; the listener's random port is a test transport detail.
        return sign(key, jwk(key), claims);
    }
    private Map<String, String> proofHeaders(KeyPair key, String body, String nonce, String idempotency) {
        return Map.of("Content-Type", "application/json", "DPoP", proof(key, "POST", BEGIN, body, nonce), "Idempotency-Key", idempotency);
    }
    private String beginBody(KeyPair key) {
        return WaJson.write(Map.of("publicJwk", jwk(key), "pkceChallenge", key(9), "pkceMethod", "S256", "platform", "ANDROID", "appVersion", "0.1.0"));
    }
    private JsonNode json(HttpResponse<String> response) { return WaJson.object(response.body()); }
    @AfterEach void doesNotTriggerServletErrorPageExceptions(CapturedOutput output) {
        assertThat(output.getAll().contains("Servlet.service() for servlet")).as("no servlet error in the real filter chain").isFalse();
        assertThat(output.getAll().contains("Exception Processing [ErrorPage")).as("no second error response").isFalse();
    }

    @Test void preservesLegacyPublicCorsAndProtectedAccountBehavior() throws Exception {
        assertThat(send("GET", "/motoristas", null, Map.of()).statusCode()).isEqualTo(200);
        var preflight = send("OPTIONS", "/motoristas", null, Map.of("Origin", "https://app.exemplo", "Access-Control-Request-Method", "GET"));
        assertThat(preflight.statusCode()).isEqualTo(200);
        assertThat(preflight.headers().firstValue("Access-Control-Allow-Origin")).isPresent();
        assertThat(send("GET", "/account/me", null, Map.of()).statusCode()).isEqualTo(401);
    }
    @Test void dedicatedGuardRejectsUnknownRoutesAndBrowserOriginsBeforeGlobalCors() throws Exception {
        var unknown = send("GET", "/driver-app/v1/hidden-admin", null, Map.of());
        assertThat(unknown.statusCode()).isEqualTo(404); assertThat(json(unknown).path("code").asText()).isEqualTo("RESOURCE_NOT_ACCESSIBLE");
        var origin = send("OPTIONS", BEGIN, null, Map.of("Origin", "https://app.exemplo", "Access-Control-Request-Method", "POST"));
        assertThat(origin.statusCode()).isEqualTo(403); assertThat(origin.headers().firstValue("Access-Control-Allow-Origin")).isEmpty();
        assertThat(send("GET", "/driver-app/v1/status", null, Map.of()).statusCode()).isEqualTo(401);
        var rejected = send("POST", "/integrations/whatsapp/v1/commands", "{}", Map.of("Content-Type", "application/json"));
        assertThat(rejected.statusCode()).isEqualTo(401);
        assertThat(json(rejected).path("code").asText()).isEqualTo("INVALID_CREDENTIAL");
        assertThat(rejected.headers().firstValueAsLong("Content-Length").orElseThrow())
            .isEqualTo(rejected.body().getBytes(StandardCharsets.UTF_8).length);
    }
    @Test void realHttpProofChallengeBeginAndIdempotentRetryShareOnePairing() throws Exception {
        var key = pair(); String body = beginBody(key);
        var challenge = send("POST", BEGIN, body, proofHeaders(key, body, null, "http-begin-request"));
        assertThat(challenge.statusCode()).isEqualTo(401); assertThat(json(challenge).path("code").asText()).isEqualTo("PROOF_NONCE_REQUIRED");
        String nonce = challenge.headers().firstValue("DPoP-Nonce").orElseThrow();
        var first = send("POST", BEGIN, body, proofHeaders(key, body, nonce, "http-begin-request"));
        assertThat(first.statusCode()).isEqualTo(200); assertThat(json(first).path("userCode").asText()).matches("[BCDFGHJKMNPQRSTVWXYZ]{8}");
        var second = send("POST", BEGIN, body, proofHeaders(key, body, first.headers().firstValue("DPoP-Nonce").orElseThrow(), "http-begin-request"));
        assertThat(second.statusCode()).isEqualTo(200); assertThat(json(second)).isEqualTo(json(first));
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.pairing_challenge WHERE jkt=?", Long.class, WaDpopVerifier.publicMaterial(jwk(key)).jkt())).isEqualTo(1L);
        assertThat(first.headers().firstValue("Cache-Control")).contains("no-store");
    }
    @Test void actualBodyGuardAndStrictJsonDoNotCreatePairingForMalformedInput() throws Exception {
        var key = pair(); String malformed = "{\"publicJwk\":{},\"publicJwk\":{}}";
        var challenge = send("POST", BEGIN, malformed, proofHeaders(key, malformed, null, "malformed-request-01"));
        String nonce = challenge.headers().firstValue("DPoP-Nonce").orElseThrow();
        var response = send("POST", BEGIN, malformed, proofHeaders(key, malformed, nonce, "malformed-request-01"));
        assertThat(response.statusCode()).isEqualTo(400); assertThat(json(response).path("code").asText()).isEqualTo("INVALID_PAYLOAD");
        var large = send("POST", BEGIN, "x".repeat(32769), Map.of("Content-Type", "application/json"));
        assertThat(large.statusCode()).isEqualTo(400); assertThat(json(large).path("code").asText()).isEqualTo("INVALID_PAYLOAD");
        var form = send("POST", BEGIN, "value=x", Map.of("Content-Type", "application/x-www-form-urlencoded"));
        assertThat(form.statusCode()).isEqualTo(400);
        assertThat(json(form).path("code").asText()).isEqualTo("INVALID_PAYLOAD");
        for (String type : new String[]{"application/x-www-form-urlencoded", "multipart/form-data; boundary=synthetic", "*/*"}) {
            var invalidGet = send("GET", "/driver-app/v1/pairings/" + UUID.randomUUID() + "/status", null, Map.of("Content-Type", type));
            assertThat(invalidGet.statusCode()).isEqualTo(400);
            assertThat(json(invalidGet).path("code").asText()).isEqualTo("INVALID_PAYLOAD");
        }
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.pairing_challenge WHERE jkt=?", Long.class, WaDpopVerifier.publicMaterial(jwk(key)).jkt())).isZero();
    }
    @Test void signatureCannotAuthorizeAlteredBodyThroughRealFilterChain() throws Exception {
        var key = pair(); String body = beginBody(key);
        var challenge = send("POST", BEGIN, body, proofHeaders(key, body, null, "tampered-request-01"));
        String nonce = challenge.headers().firstValue("DPoP-Nonce").orElseThrow();
        var response = send("POST", BEGIN, body.replace("ANDROID", "IOS"), proofHeaders(key, body, nonce, "tampered-request-01"));
        assertThat(response.statusCode()).isEqualTo(401); assertThat(json(response).path("code").asText()).isEqualTo("INVALID_CREDENTIAL");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.pairing_challenge WHERE jkt=?", Long.class, WaDpopVerifier.publicMaterial(jwk(key)).jkt())).isZero();
    }
    @Test void serviceCredentialAndInboxLeaseReachOnlyTheirHumanCommand() throws Exception {
        UUID cohort = UUID.randomUUID(), actor = UUID.randomUUID(), worker = UUID.randomUUID(), event = UUID.randomUUID();
        String token = WaCrypto.randomToken(), lease = WaCrypto.randomToken(); Instant now = Instant.now();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", cohort);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", actor, cohort);
        jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at) VALUES (?,'CONVERSATION_WORKER','test',?,'http-test',?,?)",
            worker, WaCrypto.digest(token), Timestamp.from(now), Timestamp.from(now.plusSeconds(600)));
        jdbc.update("""
            INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,normalized_kind,payload_json,state,lease_credential_id,lease_token_hash,lease_generation,lease_expires_at)
            VALUES (?,?,'SYNTHETIC','http-test',?,'MESSAGE','TEXT',?::jsonb,'CLAIMED',?,?,1,?)
            """, event, actor, event.toString(), WaJson.write(Map.of("text", "Iniciar cadastro sintético", "occurredAt", now.toString())),
            worker, WaCrypto.digest(lease), Timestamp.from(now.plusSeconds(300)));
        var headers = Map.of("Authorization", "Bearer " + token, "X-Inbox-Lease", lease, "Content-Type", "application/json", "Idempotency-Key", "http-human-command-01");
        var context = send("GET", "/integrations/whatsapp/v1/context?inboxEventId=" + event, null, headers);
        assertThat(context.statusCode()).isEqualTo(200); assertThat(json(context).path("actorState").asText()).isEqualTo("UNREGISTERED");
        String command = WaJson.write(Map.of("commandType", "START_ONBOARDING", "inboxEventId", event.toString(), "expectedVersion", 0, "payload", Map.of()));
        var first = send("POST", "/integrations/whatsapp/v1/commands", command, headers);
        assertThat(first.statusCode()).isEqualTo(200); assertThat(json(first).path("outcome").asText()).isEqualTo("APPLIED");
        var second = send("POST", "/integrations/whatsapp/v1/commands", command, headers);
        assertThat(second.statusCode()).isEqualTo(200); assertThat(json(second)).isEqualTo(json(first));
        assertThat(send("GET", "/integrations/whatsapp/v1/context?inboxEventId=" + UUID.randomUUID(), null, headers).statusCode()).isEqualTo(404);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id=?", Long.class, actor)).isEqualTo(1L);
    }
    private String transportCredential(String kind, String instance) {
        String token = WaCrypto.randomToken(); Instant now = Instant.now();
        jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,created_at,expires_at) VALUES (?,?,'test',?,?,?,?)",
            UUID.randomUUID(), kind, WaCrypto.digest(token), instance, Timestamp.from(now), Timestamp.from(now.plusSeconds(600)));
        return token;
    }
    private Map<String,String> transportHeaders(String token) {
        return Map.of("Authorization", "Bearer " + token, "Content-Type", "application/json");
    }
    private String transportEvent(String instance, String id) {
        return WaJson.write(Map.of("event", "messages.upsert", "instance", instance, "apikey", "SYNTHETIC_PROVIDER_SECRET_MUST_NOT_LEAK",
            "data", Map.of("key", Map.of("id", id, "remoteJid", "000000000001@s.whatsapp.net", "fromMe", false),
                "message", Map.of("conversation", "Olá"), "messageTimestamp", Instant.now().getEpochSecond())));
    }
    @Test void transportRoutesCommitAndDeduplicateThroughRealHttpAndReconcileEarlyCallback() throws Exception {
        String instance = "http-transport-" + UUID.randomUUID();
        UUID cohort = UUID.randomUUID(), actor = UUID.randomUUID();
        jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'test',true)", cohort);
        jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)", actor, cohort);
        jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN','000000000001@s.whatsapp.net')",
            UUID.randomUUID(), actor, instance);
        var channel = transportHeaders(transportCredential("CHANNEL", instance));
        var worker = new java.util.HashMap<>(transportHeaders(transportCredential("CONVERSATION_WORKER", instance)));
        var delivery = transportHeaders(transportCredential("DELIVERY_WORKER", instance));
        String base = "/integrations/whatsapp/v1", event = transportEvent(instance, "SYNTHETIC_HTTP_MESSAGE_1");
        var accepted = send("POST", base + "/events", event, channel);
        assertThat(accepted.statusCode()).isEqualTo(202); String eventId = json(accepted).path("receiptId").asText();
        var repeated = send("POST", base + "/events", event, channel);
        assertThat(json(repeated).path("receiptId").asText()).isEqualTo(eventId);
        assertThat(json(repeated).path("status").asText()).isEqualTo("DUPLICATE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox WHERE instance_id=?", Integer.class, instance)).isEqualTo(1);
        var claimed = send("POST", base + "/inbox/claim", "{\"batchLimit\":1,\"leaseSeconds\":60}", worker);
        assertThat(claimed.statusCode()).isEqualTo(200); var item = json(claimed).path("items").get(0);
        assertThat(item.path("inboxEventId").asText()).isEqualTo(eventId);
        assertThat(claimed.body()).doesNotContain("@", "SYNTHETIC_PROVIDER_SECRET", "senderAliasId");
        worker.put("X-Inbox-Lease", item.path("inboxLease").asText()); worker.put("Idempotency-Key", "transport-http-start");
        String command = WaJson.write(Map.of("commandType", "START_ONBOARDING", "inboxEventId", eventId, "expectedVersion", 0, "payload", Map.of()));
        var effected = send("POST", base + "/commands", command, worker);
        assertThat(effected.statusCode()).isEqualTo(200); assertThat(json(effected).path("responseIds")).hasSize(1);
        var ack = send("POST", base + "/inbox/" + eventId + "/result", "{\"outcome\":\"COMPLETED\"}", worker);
        assertThat(ack.statusCode()).isEqualTo(200); assertThat(json(ack).path("state").asText()).isEqualTo("PROCESSED");
        String open = WaJson.write(Map.of("event", "connection.update", "instance", instance,
            "date_time", Instant.now().toString(), "data", Map.of("state", "open")));
        assertThat(send("POST", base + "/events", open, channel).statusCode()).isEqualTo(202);
        var outbox = send("POST", base + "/outbox/claim", "{\"batchLimit\":1,\"leaseSeconds\":60}", delivery);
        assertThat(outbox.statusCode()).isEqualTo(200); var claim = json(outbox).path("items").get(0);
        assertThat(outbox.body()).doesNotContain("recipient", "content", "@");
        String id = claim.path("outboxId").asText();
        var reservation = send("POST", base + "/outbox/" + id + "/authorize-submit",
            WaJson.write(Map.of("leaseToken", claim.path("leaseToken").asText())), delivery);
        assertThat(reservation.statusCode()).isEqualTo(200);
        String external = "SYNTHETIC_HTTP_SEND_1";
        String callback = WaJson.write(Map.of("event", "messages.update", "instance", instance,
            "data", Map.of("keyId", external, "fromMe", true, "status", "READ")));
        assertThat(send("POST", base + "/events", callback, channel).statusCode()).isEqualTo(202);
        var result = send("POST", base + "/outbox/" + id + "/result", WaJson.write(Map.of(
            "attemptToken", json(reservation).path("attemptToken").asText(), "outcome", "SUBMITTED", "externalMessageId", external)), delivery);
        assertThat(result.statusCode()).isEqualTo(200); assertThat(json(result).path("state").asText()).isEqualTo("READ");
    }
    @Test void transportRejectsWrongPrincipalCrossInstanceOriginAndOversizedBodyBeforePersistence() throws Exception {
        String instance = "http-guard-" + UUID.randomUUID(), base = "/integrations/whatsapp/v1/events";
        var channel = new java.util.HashMap<>(transportHeaders(transportCredential("CHANNEL", instance)));
        String body = transportEvent(instance, "SYNTHETIC_GUARD_1");
        assertThat(send("POST", base, body, transportHeaders(transportCredential("DELIVERY_WORKER", instance))).statusCode()).isEqualTo(403);
        assertThat(send("POST", base, transportEvent("other-instance", "SYNTHETIC_GUARD_1"), channel).statusCode()).isEqualTo(403);
        assertThat(send("POST", base, "x".repeat(32769), channel).statusCode()).isEqualTo(400);
        channel.put("Origin", "https://synthetic.invalid");
        assertThat(send("POST", base, body, channel).statusCode()).isEqualTo(403);
        assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox WHERE instance_id=?", Integer.class, instance)).isZero();
    }
    @Test void databaseFailureBeforeCommitNeverReturnsAcceptedOrLeaksProviderBody(CapturedOutput output) throws Exception {
        String instance = "http-failure-" + UUID.randomUUID();
        var headers = transportHeaders(transportCredential("CHANNEL", instance));
        jdbc.execute("CREATE FUNCTION wa.synthetic_http_failure() RETURNS trigger LANGUAGE plpgsql AS $$ BEGIN IF NEW.instance_id='" + instance
            + "' THEN RAISE EXCEPTION 'SYNTHETIC_DATABASE_PRIVATE_DETAIL'; END IF; RETURN NEW; END $$");
        jdbc.execute("CREATE TRIGGER synthetic_http_failure BEFORE INSERT ON wa.integration_inbox FOR EACH ROW EXECUTE FUNCTION wa.synthetic_http_failure()");
        try {
            var response = send("POST", "/integrations/whatsapp/v1/events", transportEvent(instance, "SYNTHETIC_FAILED_INSERT"), headers);
            assertThat(response.statusCode()).isEqualTo(503); assertThat(json(response).path("code").asText()).isEqualTo("DEPENDENCY_UNAVAILABLE");
            assertThat(response.body()).doesNotContain("SYNTHETIC_PROVIDER_SECRET", "SYNTHETIC_DATABASE_PRIVATE_DETAIL", "@");
            assertThat(output.getAll()).doesNotContain("SYNTHETIC_PROVIDER_SECRET_MUST_NOT_LEAK", "SYNTHETIC_DATABASE_PRIVATE_DETAIL");
            assertThat(jdbc.queryForObject("SELECT count(*) FROM wa.integration_inbox WHERE instance_id=?", Integer.class, instance)).isZero();
        } finally {
            jdbc.execute("DROP TRIGGER synthetic_http_failure ON wa.integration_inbox");
            jdbc.execute("DROP FUNCTION wa.synthetic_http_failure()");
        }
    }
}
