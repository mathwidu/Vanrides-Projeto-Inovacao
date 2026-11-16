package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.api.WaTrackingController;
import com.vanrides.vanrides.whatsapp.auth.WaCredentials;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaIngressFilter;
import com.vanrides.vanrides.whatsapp.auth.WaProofFilter;
import com.vanrides.vanrides.whatsapp.auth.WaProofLedger;
import com.vanrides.vanrides.whatsapp.auth.WaRateLimiter;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingStopReceipt;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Synthetic fixes use real ingress/DPoP, controller, transactions and PostgreSQL; no sensor or provider is simulated as real. */
@Testcontainers(disabledWithoutDocker = true)
class WaApproachFinishJourneyIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_approach_finish_journey")
        .withTmpFs(Map.of("/var/lib/postgresql/data", "rw,size=128m"));
    private WaTrackingTestSupport t;
    private WaSettings settings;
    private WaTrackingService tracking;
    private WaTrackingController controller;
    private WaIngressFilter ingress;
    private WaProofFilter proofs;
    private WaProofLedger ledger;
    private KeyPair key;
    private ObjectNode jwk;
    private String accessToken, jkt;
    private double direction;
    private long sequence;

    @ParameterizedTest(name = "{0}: accepted GPS produces approach, passage, dwell and terminal ACK")
    @ValueSource(strings = {"OUTBOUND", "RETURN"})
    void acceptedGpsComposesApproachPassageAndAutomaticFinish(String leg) throws Exception {
        start(leg);
        assertEquals(leg, t.f.s.string("SELECT snapshot_json->'routine'->>'direction' FROM wa.operation_run WHERE id=?", t.run()));
        assertStops("WAITING");

        // Even a full dwell at the destination cannot bypass unresolved pickups.
        samples(fix(0, .007, 10, 90));
        for (int i = 0; i <= 6; i++) {
            t.h.time.advance(10);
            assertEquals("CONTINUE", samples(fix(0, .01, 0, 90)).path("action").asText());
        }
        assertEquals("TRACKING", t.runState());
        assertStops("WAITING");
        assertEquals("PROGRESS_REQUIRED", finishReason());
        assertNull(t.h.jdbc.queryForObject("SELECT arrived_since FROM wa.run_finish_progress WHERE run_id=?", Timestamp.class, t.run()));
        assertEquals(0, approachCount());

        // Both passengers share this physical stop; only the opted-in recipient is eligible.
        t.h.time.advance(15);
        var approachBatch = t.batch(fix(-10, .007, 30, 270), fix(-5, .0055, 30, 270), fix(0, .004, 30, 270));
        var approachAck = post(sessionPath("samples"), approachBatch);
        accepted(approachAck, 3);
        assertStops("APPROACHING");
        assertEquals(1, approachCount());
        UUID notice = t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE run_id=? AND purpose='APPROACH'", UUID.class, t.run());
        assertEquals(t.f.passenger, t.h.jdbc.queryForObject("SELECT recipient_identity_id FROM wa.message_outbox WHERE id=?", UUID.class, notice));
        assertEquals(t.f.passenger, t.h.jdbc.queryForObject("""
            SELECT e.identity_id FROM wa.approach_delivery_route r JOIN wa.integration_inbox e ON e.id=r.recipient_source_event_id
            WHERE r.outbox_id=?
            """, UUID.class, notice));
        var replay = post(sessionPath("samples"), approachBatch);
        assertEquals(approachAck, replay);
        var repeatedFixes = post(sessionPath("samples"), t.batch(
            (ObjectNode) approachBatch.path("samples").get(0), (ObjectNode) approachBatch.path("samples").get(1), (ObjectNode) approachBatch.path("samples").get(2)));
        accepted(repeatedFixes, 0);
        assertEquals(3, repeatedFixes.path("duplicate").size());
        assertEquals(sequence, t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt WHERE session_id=?", t.session()));
        t.h.time.advance(5);
        samples(fix(0, .003, 20, 270));
        assertEquals(1, approachCount());
        authorizeApproach(notice);

        t.h.time.advance(10);
        samples(fix(-5, .002, 30, 270), fix(0, .0003, 30, 270));
        assertStops("APPROACHING");
        assertTrue(t.h.jdbc.queryForObject("SELECT nearest_m<80 FROM wa.approach_progress WHERE run_id=?", Boolean.class, t.run()));
        t.h.time.advance(15);
        samples(fix(-10, -.001, 30, 270), fix(-5, -.0025, 30, 270), fix(0, -.004, 30, 270));
        assertStops("PASSED");
        assertEquals("PASSED", t.f.s.string("SELECT last_reason FROM wa.approach_progress WHERE run_id=?", t.run()));
        assertEquals("TRACKING", t.runState());
        assertEquals("OUTSIDE_DESTINATION", finishReason());

        // Return across the same completed pickup without another notice, then approach the frozen destination.
        for (double longitude : new double[]{0, .004, .008, .01}) {
            t.h.time.advance(10);
            assertEquals("CONTINUE", samples(fix(0, longitude, longitude == .01 ? 0 : 30, 90)).path("action").asText());
            assertStops("PASSED");
            assertEquals(1, approachCount());
        }
        var arrived = t.h.clock.now();
        assertEquals("DWELL_PENDING", finishReason());
        assertEquals(Timestamp.from(arrived), t.h.jdbc.queryForObject("SELECT arrived_since FROM wa.run_finish_progress WHERE run_id=?", Timestamp.class, t.run()));
        for (int i = 1; i <= 5; i++) {
            t.h.time.advance(10);
            assertEquals("CONTINUE", samples(fix(0, .01, 0, 90)).path("action").asText());
            assertEquals("TRACKING", t.runState());
            assertEquals("DWELL_PENDING", finishReason());
        }
        t.h.time.advance(9);
        assertEquals("CONTINUE", samples(fix(0, .01, 0, 90)).path("action").asText());
        assertEquals("TRACKING", t.runState());
        assertEquals("DWELL_PENDING", finishReason());
        t.h.time.advance(1);
        var completed = samples(fix(0, .01, 0, 90));
        assertEquals("STOP", completed.path("action").asText());
        assertEquals("COMPLETED", t.runState());
        assertEquals("AUTO_ARRIVAL", t.f.s.string("SELECT completion_reason FROM wa.operation_run WHERE id=?", t.run()));
        assertEquals("REVOKED", t.sessionState());
        assertEquals(0, t.f.s.number("SELECT count(*) FROM wa.start_intent WHERE run_id=? AND state IN ('PENDING','ACTIVATED')", t.run()));
        assertEquals(0, t.f.s.number("SELECT count(*) FROM wa.run_finish_progress WHERE run_id=?", t.run()));
        assertEquals(0, t.f.s.number("SELECT count(*) FROM wa.approach_fix WHERE session_id=?", t.session()));
        assertEquals("STOP_PENDING", controllerStatus().path("sessionState").asText());
        assertEquals(0, t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE id=? AND stopped_at IS NOT NULL", t.session()));
        String stopRef = completed.path("stopRequestRef").asText();
        assertDoesNotThrow(() -> UUID.fromString(stopRef));
        var pendingReceipt = receiptBody(stopRef);
        var pending = send(sessionPath("stop-receipt"), pendingReceipt);
        assertEquals(409, pending.getStatus());
        assertEquals("VERSION_CONFLICT", WaJson.object(pending.getContentAsString()).path("code").asText());

        t.h.time.advance(1);
        var late = post(sessionPath("samples"), t.batch(fix(0, .01, 0, 90)));
        assertTrue(late.path("accepted").isEmpty());
        assertEquals("RUN_TERMINAL", late.path("rejected").get(0).path("code").asText());
        var ack = post(sessionPath("state"), t.state("STOPPED").put("stopRequestRef", stopRef));
        assertEquals("STOP", ack.path("action").asText());
        assertEquals("STOPPED", controllerStatus().path("sessionState").asText());
        var receipt = post(sessionPath("stop-receipt"), pendingReceipt);
        assertTrue(receipt.path("stopConfirmed").booleanValue());
        assertEquals(stopRef, receipt.path("stopRequestRef").asText());
        assertEquals(t.session().toString(), receipt.path("sessionId").asText());
        assertEquals(t.generation(), receipt.path("generation").longValue());
        assertEquals(t.h.clock.now().toString(), receipt.path("stoppedAt").asText());
        assertEquals("COMPLETED", receipt.path("runState").asText());
        assertEquals("COMPLETED", t.runState());
        assertEquals(1, approachCount());
    }

    private void start(String leg) throws Exception {
        t = new WaTrackingTestSupport(POSTGRES);
        direction = leg.equals("OUTBOUND") ? 1 : -1;
        var journey = leg.equals("OUTBOUND") ? t.f.item : t.f.attendance.question(t.f.passenger, WaRunTestSupport.DAY, leg, "16:00");
        UUID optedOut = t.f.attendance.passenger();
        var peer = t.f.attendance.question(optedOut, WaRunTestSupport.DAY, leg, "17:00");
        t.h.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||'{\"APPROACH_MESSAGES\":false}'::jsonb WHERE identity_id=?", optedOut);
        t.h.jdbc.update("""
            INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order)
            VALUES (?,?,?,?,?,?,2)
            """, UUID.randomUUID(), journey.routine(), t.f.driver, optedOut, peer.link(), peer.point());
        t.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false,destination_latitude=0,destination_longitude=? WHERE id=?", direction * .01, journey.routine());
        var original = t.h.settings;
        settings = new WaSettings(new MockEnvironment().withProperty("vanrides.wa.enabled", "true")
            .withProperty("vanrides.wa.environment", original.environment()).withProperty("vanrides.wa.public-base-url", original.baseUrl())
            .withProperty("vanrides.wa.start-link-base-url", original.startLinkBaseUrl())
            .withProperty("vanrides.wa.hash-key", Base64.getUrlEncoder().withoutPadding().encodeToString(original.hashKey()))
            .withProperty("vanrides.wa.response-key", Base64.getUrlEncoder().withoutPadding().encodeToString(original.responseKey()))
            .withProperty("vanrides.wa.operator-credential", original.operatorCredential())
            .withProperty("vanrides.wa.operator-expires-at", original.operatorExpiresAt().toString())
            .withProperty("vanrides.wa.approach-enabled", "true").withProperty("vanrides.wa.auto-finish-enabled", "true")
            .withProperty("vanrides.wa.approach-reviewed-routine-ids", journey.routine().toString()));
        tracking = t.h.proxy(new WaTrackingService(t.h.jdbc, settings, t.h.clock, t.h.crypto));
        controller = new WaTrackingController(tracking, t.h.proxy(new WaTrackingStopReceipt(t.h.jdbc, settings, t.h.clock)));
        var generator = KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        key = generator.generateKeyPair(); var publicKey = (ECPublicKey) key.getPublic();
        jwk = WaJson.object("{}").put("kty", "EC").put("crv", "P-256")
            .put("x", coordinate(publicKey.getW().getAffineX())).put("y", coordinate(publicKey.getW().getAffineY()));
        jkt = WaDpopVerifier.publicMaterial(jwk).jkt(); accessToken = WaCrypto.randomToken();
        t.h.jdbc.update("UPDATE wa.driver_device SET jkt=?,public_jwk=?::jsonb WHERE id=?", jkt, jwk.toString(), t.device);
        t.h.jdbc.update("UPDATE wa.device_token SET token_hash=? WHERE id=?", WaCrypto.digest(accessToken), t.actor.credentialId());
        ledger = t.h.proxy(new WaProofLedger(t.h.jdbc, t.h.clock, settings));
        ingress = new WaIngressFilter(settings, new WaCredentials(t.h.jdbc, settings, t.h.clock), new WaRateLimiter(), new TrustedProxyClientAddressResolver("", 1024, 10));
        proofs = new WaProofFilter(new WaDpopVerifier(settings, t.h.clock), ledger);
        // Reuse the start-link fixture shape; progression and completion below have no direct SQL writes.
        t.intent = tracking.requestStart(t.f.driver, journey.occurrence(), false, t.f.preparation.rosterDigest(t.f.driver, journey.occurrence()));
        UUID event = t.h.event(t.f.driver, WaJson.object("{}").put("text", "Pedido sintético").put("senderAliasId",
            t.h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=?", UUID.class, t.f.driver).toString()));
        UUID command = UUID.randomUUID(), box = UUID.randomUUID();
        t.h.transaction.executeWithoutResult(status -> {
            t.h.jdbc.update("""
                INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
                VALUES (?,?,?,?,?,?,'{}',?,?)
                """, command, t.f.driver, event, command.toString(), "REQUEST_START", WaCrypto.digest("synthetic-journey"),
                Timestamp.from(t.h.clock.now()), Timestamp.from(t.h.clock.now().plusSeconds(86400)));
            WaFoundationResponses.saveRun(t.h.jdbc, box, command, t.f.driver, "Confirme no aplicativo.", t.h.clock.now());
            tracking.attachStartLink(box, command, t.f.driver, t.intent);
        });
        t.activation = post("/driver-app/v1/start-intents/" + t.intent.path("intentId").asText() + "/activate", t.activateBody(t.intent));
    }

    private void authorizeApproach(UUID notice) {
        var outbox = t.h.proxy(new WaOutboxService(t.h.jdbc, new WaTransportAccess(t.h.jdbc, settings, t.h.clock),
            new WaDeliveryReconciler(t.h.jdbc, t.h.clock), settings, t.h.clock));
        ObjectNode selected = null;
        for (var item : outbox.claim(t.f.s.delivery, WaJson.object("{}").put("batchLimit", 10).put("leaseSeconds", 60)).path("items"))
            if (item.path("outboxId").asText().equals(notice.toString())) selected = (ObjectNode) item;
        assertNotNull(selected, "Expected an eligible approach notice");
        var authorized = outbox.authorize(t.f.s.delivery, notice, WaJson.object("{}").put("leaseToken", selected.path("leaseToken").asText())).requireSuccess();
        assertEquals("TEXT", authorized.path("content").path("type").asText());
        assertTrue(authorized.path("content").path("text").asText().contains("se aproximando"));
        // Authorization is the provider boundary: no delivery result or WhatsApp receipt is fabricated.
        assertEquals(0, t.f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=? AND state='SUBMITTED'", notice));
    }
    private ObjectNode fix(long offset, double longitude, double speed, double heading) {
        return t.sample(++sequence, offset, 5).put("longitude", direction * longitude).put("speedMps", speed)
            .put("headingDeg", direction > 0 ? heading : (heading + 180) % 360);
    }
    private ObjectNode samples(ObjectNode... fixes) throws Exception { var result = post(sessionPath("samples"), t.batch(fixes)); accepted(result, fixes.length); return result; }
    private void accepted(ObjectNode result, int count) { assertEquals(count, result.path("accepted").size(), result.toString()); assertTrue(result.path("rejected").isEmpty(), result.toString()); }
    private String sessionPath(String action) { return "/driver-app/v1/tracking-sessions/" + t.session() + "/" + action; }
    private long approachCount() { return t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE run_id=? AND purpose='APPROACH'", t.run()); }
    private String finishReason() { return t.f.s.string("SELECT last_reason FROM wa.run_finish_progress WHERE run_id=?", t.run()); }
    private void assertStops(String state) { assertEquals(2, t.f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=? AND state=?", t.run(), state)); }
    private ObjectNode receiptBody(String ref) { return WaJson.object("{}").put("generation", t.generation()).put("stopRequestRef", ref); }
    private ObjectNode controllerStatus() throws Exception {
        var response = send("/driver-app/v1/tracking-status", null);
        assertEquals(200, response.getStatus(), response.getContentAsString()); return WaJson.object(response.getContentAsString());
    }
    private ObjectNode post(String path, ObjectNode body) throws Exception {
        var response = send(path, body); assertEquals(200, response.getStatus(), response.getContentAsString());
        assertEquals("no-store", response.getHeader("Cache-Control")); return WaJson.object(response.getContentAsString());
    }
    private MockHttpServletResponse send(String path, ObjectNode body) throws Exception {
        boolean receipt = path.endsWith("/stop-receipt");
        String method = body == null ? "GET" : "POST";
        byte[] bytes = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
        var claims = new LinkedHashMap<String, Object>();
        claims.put("htm", method); claims.put("htu", settings.baseUrl() + path); claims.put("iat", t.h.clock.now().getEpochSecond());
        claims.put("jti", WaCrypto.randomToken()); claims.put("nonce", ledger.issue(jkt));
        if (!receipt) claims.put("ath", WaCrypto.base64(WaCrypto.sha256(accessToken.getBytes(StandardCharsets.UTF_8))));
        if (body != null) claims.put("vr_body_sha256", WaCrypto.base64(WaCrypto.sha256(bytes)));
        String proof = Jwts.builder().header().type("dpop+jwt").add("jwk", WaJson.MAPPER.convertValue(jwk, Map.class)).and()
            .claims(claims).signWith(key.getPrivate(), Jwts.SIG.ES256).compact();
        var request = new MockHttpServletRequest(method, path); request.setRemoteAddr("127.0.0.1");
        if (body != null) { request.setContentType("application/json"); request.setContent(bytes); }
        request.addHeader("DPoP", proof); request.addHeader("Idempotency-Key", "synthetic-journey-" + UUID.randomUUID());
        if (!receipt) request.addHeader("Authorization", "DPoP " + accessToken);
        var response = new MockHttpServletResponse();
        ingress.doFilter(request, response, (r, s) -> proofs.doFilter(r, s, (rr, ss) -> {
            try {
                ObjectNode result;
                if (body == null) result = controller.status(request);
                else if (path.endsWith("/activate")) result = controller.activate(request, t.intent.path("intentId").asText());
                else if (path.endsWith("/samples")) result = controller.samples(request, t.session().toString());
                else if (receipt) result = controller.stopReceipt(request, t.session().toString());
                else result = controller.state(request, t.session().toString());
                response.setContentType("application/json"); response.getWriter().write(result.toString());
            } catch (WaProblem error) { WaHttp.writeError(request, response, error); }
        }));
        return response;
    }
    private static String coordinate(BigInteger value) {
        byte[] source = value.toByteArray(), padded = new byte[32]; int length = Math.min(32, source.length);
        System.arraycopy(source, source.length - length, padded, 32 - length, length); return WaCrypto.base64(padded);
    }
}
