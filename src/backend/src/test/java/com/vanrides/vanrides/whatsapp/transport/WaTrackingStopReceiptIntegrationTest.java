package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.api.WaTrackingController;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingStopReceipt;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.sql.Timestamp;
import java.util.HashSet;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real ingress, signature/nonce ledger, controller and SQL read against PostgreSQL. */
@Testcontainers(disabledWithoutDocker=true)
class WaTrackingStopReceiptIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_tracking_stop_receipt")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=128m"));
    private WaTrackingTestSupport t;
    private KeyPair key;
    private ObjectNode jwk;
    private WaDpopVerifier.PublicMaterial material;
    private WaProofLedger ledger;
    private WaPrincipal actor;

    @BeforeEach void setup() throws Exception {
        t=new WaTrackingTestSupport(POSTGRES); t.activate();
        var generator=KeyPairGenerator.getInstance("EC"); generator.initialize(new ECGenParameterSpec("secp256r1"));
        key=generator.generateKeyPair(); var publicKey=(ECPublicKey)key.getPublic();
        jwk=WaJson.object("{}").put("kty","EC").put("crv","P-256")
            .put("x",coordinate(publicKey.getW().getAffineX())).put("y",coordinate(publicKey.getW().getAffineY()));
        material=WaDpopVerifier.publicMaterial(jwk);
        t.h.jdbc.update("UPDATE wa.driver_device SET jkt=?,public_jwk=?::jsonb WHERE id=?",material.jkt(),jwk.toString(),t.device);
        actor=new WaPrincipal(WaPrincipal.Kind.DEVICE,t.actor.credentialId(),t.device,t.actor.driverId(),null,material.jkt(),1,false);
        ledger=t.h.proxy(new WaProofLedger(t.h.jdbc,t.h.clock,t.h.settings));
    }
    @Test void freshProofReadsExactAckAfterCredentialExpiryRevocationAndRebindingWithoutLifecycleWrites() throws Exception {
        String ref=requestStop(); acknowledge(ref);
        t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.device);
        t.h.jdbc.update("UPDATE wa.device_token SET revoked_at=? WHERE device_id=?",Timestamp.from(t.h.clock.now()),t.device);
        t.h.jdbc.update("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=?",t.f.driver);
        t.h.time.advance(3601);
        String before=snapshot();
        var first=send(body(ref)); assertEquals(200,first.getStatus());
        var receipt=WaJson.object(first.getContentAsString());
        var names=new HashSet<String>(); receipt.fieldNames().forEachRemaining(names::add);
        assertEquals(java.util.Set.of("schemaVersion","sessionId","generation","stopRequestRef","stopConfirmed","stoppedAt","serverTime","runState"),names);
        assertEquals(1,receipt.path("schemaVersion").intValue()); assertTrue(receipt.path("stopConfirmed").booleanValue());
        assertEquals(t.session().toString(),receipt.path("sessionId").asText()); assertEquals(t.generation(),receipt.path("generation").longValue());
        assertEquals(ref,receipt.path("stopRequestRef").asText()); assertEquals("COMPLETED",receipt.path("runState").asText());
        assertEquals(t.h.clock.now().minusSeconds(3601).toString(),receipt.path("stoppedAt").asText());
        assertEquals(t.h.clock.now().toString(),receipt.path("serverTime").asText());
        assertEquals("no-store",first.getHeader("Cache-Control"));
        assertEquals(200,send(body(ref)).getStatus()); assertEquals(before,snapshot());
    }
    @Test void nullReferenceAckRemainsReadableForRetainedClosedOrSuspendedProfile() throws Exception {
        t.tracking.samples(actor,t.session(),t.batch(t.sample(1,0,5)));
        t.tracking.state(actor,t.session(),t.state("STOPPED"),"synthetic-local-stop");
        for(String state:new String[]{"SUSPENDED","CLOSED"}) {
            t.h.jdbc.update("UPDATE wa.profile SET status=?,driver_enabled=false WHERE identity_id=?",state,t.f.driver);
            var response=send(body(null)); assertEquals(200,response.getStatus());
            assertTrue(WaJson.object(response.getContentAsString()).path("stopRequestRef").isNull());
        }
    }
    @Test void activeExpiredAndRequestedSessionsCannotBeConfirmedWithoutSensorAck() throws Exception {
        assertError(409,"VERSION_CONFLICT",send(body(null)));
        t.h.jdbc.update("UPDATE wa.tracking_session SET stopped_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.session());
        assertError(409,"VERSION_CONFLICT",send(body(null)));
        t.h.jdbc.update("UPDATE wa.tracking_session SET stopped_at=NULL WHERE id=?",t.session());
        String ref=requestStop();
        assertError(409,"VERSION_CONFLICT",send(body(ref)));
        t.h.jdbc.update("UPDATE wa.tracking_session SET state='EXPIRED' WHERE id=?",t.session());
        assertError(409,"VERSION_CONFLICT",send(body(ref)));
    }
    @Test void generationAndReferenceMustMatchExactlyAndGpsOrExtraFieldsAreRejected() throws Exception {
        String ref=requestStop(); acknowledge(ref);
        assertError(409,"VERSION_CONFLICT",send(body(ref).put("generation",t.generation()+1)));
        assertError(409,"VERSION_CONFLICT",send(body(UUID.randomUUID().toString())));
        assertError(409,"VERSION_CONFLICT",send(body(null)));
        assertError(400,"INVALID_PAYLOAD",send(body(ref).put("latitude",0)));
        var missing=body(ref); missing.remove("stopRequestRef"); assertError(400,"INVALID_PAYLOAD",send(missing));
        assertError(400,"INVALID_PAYLOAD",send(body(ref).put("generation",1.5)));
        assertError(400,"INVALID_PAYLOAD",send(body(ref).put("stopRequestRef","bad-reference")));
    }
    @Test void wrongKeyMissingSessionAndWrongEnvironmentAreIndistinguishable() throws Exception {
        String ref=requestStop(); acknowledge(ref);
        t.h.jdbc.update("UPDATE wa.driver_device SET jkt=? WHERE id=?",WaCrypto.randomToken(),t.device);
        assertError(404,"RESOURCE_NOT_ACCESSIBLE",send(body(ref)));
        t.h.jdbc.update("UPDATE wa.driver_device SET jkt=? WHERE id=?",material.jkt(),t.device);
        var service=t.h.proxy(new WaTrackingStopReceipt(t.h.jdbc,t.h.settings,t.h.clock));
        var foreign=new WaDpopVerifier.Proof(new WaDpopVerifier.PublicMaterial(WaCrypto.randomToken(),"{}",material.key()),"synthetic-proof",null,t.h.clock.now());
        assertEquals(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE,assertThrows(WaProblem.class,()->service.read(foreign,t.session(),body(ref))).code());
        var proof=new WaDpopVerifier.Proof(material,"synthetic-proof",null,t.h.clock.now());
        assertEquals(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE,assertThrows(WaProblem.class,()->service.read(proof,UUID.randomUUID(),body(ref))).code());
        t.h.jdbc.update("UPDATE wa.driver_device SET environment='foreign-test' WHERE id=?",t.device);
        assertError(404,"RESOURCE_NOT_ACCESSIBLE",send(body(ref)));
    }
    @Test void erasedProfileRedactedKeyAndForeignCohortCannotReadRetainedAck() throws Exception {
        String ref=requestStop(); acknowledge(ref);
        t.h.jdbc.update("UPDATE wa.driver_device SET public_jwk='{}' WHERE id=?",t.device);
        assertError(404,"RESOURCE_NOT_ACCESSIBLE",send(body(ref)));
        t.h.jdbc.update("UPDATE wa.driver_device SET public_jwk=?::jsonb WHERE id=?",jwk.toString(),t.device);
        t.h.jdbc.update("UPDATE wa.test_cohort SET environment='foreign-test' WHERE id=(SELECT test_cohort_id FROM wa.identity WHERE id=?)",t.f.driver);
        assertError(404,"RESOURCE_NOT_ACCESSIBLE",send(body(ref)));
        t.h.jdbc.update("UPDATE wa.test_cohort SET environment=? WHERE id=(SELECT test_cohort_id FROM wa.identity WHERE id=?)",t.h.settings.environment(),t.f.driver);
        eraseProfile();
        assertError(404,"RESOURCE_NOT_ACCESSIBLE",send(body(ref)));
    }
    @Test void proofOnlyRouteRejectsAuthorizationAndReplayedOrMissingNonceAndBindsExactBody() throws Exception {
        String ref=requestStop(); acknowledge(ref); ObjectNode body=body(ref);
        String signed=sign(body,ledger.issue(material.jkt()));
        assertError(401,"INVALID_CREDENTIAL",send(body,signed,"DPoP "+WaCrypto.randomToken(),t.h.settings));
        assertEquals(200,send(body,signed,null,t.h.settings).getStatus());
        assertError(401,"PROOF_NONCE_REQUIRED",send(body,signed,null,t.h.settings));
        assertError(401,"PROOF_NONCE_REQUIRED",send(body,sign(body,null),null,t.h.settings));
        assertError(401,"INVALID_CREDENTIAL",send(body.put("generation",t.generation()+1),sign(body(ref),ledger.issue(material.jkt())),null,t.h.settings));
    }
    @Test void receiptRemainsAvailableWhileFeatureIsDisabled() throws Exception {
        String ref=requestStop(); acknowledge(ref);
        var disabled=WaTransportTestSupport.settings(false,true);
        assertEquals(200,send(body(ref),sign(body(ref),ledger.issue(material.jkt())),null,disabled).getStatus());
    }
    private ObjectNode body(String ref) {
        var body=WaJson.object("{}").put("generation",t.generation());
        if(ref==null) body.putNull("stopRequestRef"); else body.put("stopRequestRef",ref); return body;
    }
    private String requestStop() {
        t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=?,version=version+1 WHERE id=?",Timestamp.from(t.h.clock.now()),t.run());
        return t.tracking.status(actor).path("stopRequestRef").asText();
    }
    private void acknowledge(String ref) { t.tracking.state(actor,t.session(),t.state("STOPPED").put("stopRequestRef",ref),"synthetic-stop-ack"); }
    private String path() { return "/driver-app/v1/tracking-sessions/"+t.session()+"/stop-receipt"; }
    private MockHttpServletResponse send(ObjectNode body) throws Exception {
        return send(body,sign(body,ledger.issue(material.jkt())),null,t.h.settings);
    }
    private MockHttpServletResponse send(ObjectNode body,String proof,String authorization,WaSettings settings) throws Exception {
        var request=new MockHttpServletRequest("POST",path()); request.setRemoteAddr("127.0.0.1");
        request.setContentType("application/json"); request.setContent(body.toString().getBytes(StandardCharsets.UTF_8));
        request.addHeader("DPoP",proof); if(authorization!=null) request.addHeader("Authorization",authorization);
        var response=new MockHttpServletResponse();
        var ingress=new WaIngressFilter(settings,new WaCredentials(t.h.jdbc,settings,t.h.clock),new WaRateLimiter(),new TrustedProxyClientAddressResolver("",1024,10));
        var proofFilter=new WaProofFilter(new WaDpopVerifier(settings,t.h.clock),ledger);
        var controller=new WaTrackingController(t.tracking,t.h.proxy(new WaTrackingStopReceipt(t.h.jdbc,settings,t.h.clock)));
        ingress.doFilter(request,response,(r,s)->proofFilter.doFilter(r,s,(rr,ss)->{
            try { response.setContentType("application/json"); response.getWriter().write(controller.stopReceipt(request,t.session().toString()).toString()); }
            catch(WaProblem error) { WaHttp.writeError(request,response,error); }
        }));
        return response;
    }
    private String sign(ObjectNode body,String nonce) {
        var claims=new java.util.LinkedHashMap<String,Object>();
        claims.put("htm","POST"); claims.put("htu",t.h.settings.baseUrl()+path()); claims.put("iat",t.h.clock.now().getEpochSecond());
        claims.put("jti",WaCrypto.randomToken()); if(nonce!=null) claims.put("nonce",nonce);
        claims.put("vr_body_sha256",WaCrypto.base64(WaCrypto.sha256(body.toString().getBytes(StandardCharsets.UTF_8))));
        return Jwts.builder().header().type("dpop+jwt").add("jwk",WaJson.MAPPER.convertValue(jwk,Map.class)).and()
            .claims(claims).signWith(key.getPrivate(),Jwts.SIG.ES256).compact();
    }
    private String snapshot() {
        return t.f.s.string("SELECT row_to_json(s)::text FROM wa.tracking_session s WHERE id=?",t.session())
            +t.f.s.string("SELECT row_to_json(r)::text FROM wa.operation_run r WHERE id=?",t.run())
            +t.f.s.number("SELECT count(*) FROM wa.operation_replay")+t.f.s.number("SELECT count(*) FROM wa.message_outbox");
    }
    private void eraseProfile() {
        Timestamp now=Timestamp.from(t.h.clock.now()),closed=Timestamp.from(t.h.clock.now().minusSeconds(31L*86400));
        t.h.jdbc.update("UPDATE wa.profile SET status='CLOSED',closed_at=? WHERE identity_id=?",closed,t.f.driver);
        t.h.jdbc.update("INSERT INTO wa.privacy_subject(subject_id,environment,instance_id,closed_at,erase_after) VALUES (?,?,?,?,?)",
            t.f.driver,t.h.settings.environment(),WaTransportTestSupport.INSTANCE,closed,Timestamp.from(closed.toInstant().plusSeconds(30L*86400)));
        t.h.jdbc.update("""
            INSERT INTO wa.privacy_erasure_ledger(event_id,subject_id,environment,instance_id,payload_json,payload_hash,created_at)
            VALUES (?,?,?,?,'{}',?,?)
            """,UUID.randomUUID(),t.f.driver,t.h.settings.environment(),WaTransportTestSupport.INSTANCE,WaCrypto.digest("{}"),closed);
        t.h.jdbc.update("""
            UPDATE wa.privacy_erasure_ledger SET state='JOURNALED',journal_sequence=1,journal_digest=?,journaled_at=?,purge_started_at=?
            WHERE subject_id=?
            """,WaCrypto.digest("synthetic-committed-erasure-journal"),now,now,t.f.driver);
        t.h.jdbc.update("UPDATE wa.profile SET privacy_erased_at=?,display_name=NULL,preferences_json='{}',driver_enabled=false WHERE identity_id=?",now,t.f.driver);
    }
    private void assertError(int status,String code,MockHttpServletResponse response) throws Exception {
        assertEquals(status,response.getStatus(),response.getContentAsString());
        assertEquals(code,WaJson.object(response.getContentAsString()).path("code").asText());
    }
    private String coordinate(BigInteger value) {
        byte[] source=value.toByteArray(),padded=new byte[32]; int length=Math.min(32,source.length);
        System.arraycopy(source,source.length-length,padded,32-length,length); return WaCrypto.base64(padded);
    }
}
