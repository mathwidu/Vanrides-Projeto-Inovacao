package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.tracking.WaFinishService;
import io.jsonwebtoken.Jwts;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Real ingress, ES256/nonce/body proofs and controller; servlet fixtures, not an external HTTP or device test. */
@Testcontainers(disabledWithoutDocker=true)
class WaFinishHttpIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_finish_http");
    private WaTrackingTestSupport t;
    private WaFinishService finishes;
    private WaProofLedger ledger;
    private KeyPair key;
    private ObjectNode jwk,offer;
    private WaDpopVerifier.PublicMaterial material;
    private WaPrincipal actor;
    private String access;
    @BeforeEach void setup() throws Exception {
        t=new WaTrackingTestSupport(POSTGRES);t.activate();t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));key=generator.generateKeyPair();
        ECPublicKey publicKey=(ECPublicKey)key.getPublic();
        jwk=WaJson.object("{}").put("kty","EC").put("crv","P-256").put("x",coordinate(publicKey.getW().getAffineX())).put("y",coordinate(publicKey.getW().getAffineY()));
        material=WaDpopVerifier.publicMaterial(jwk);access=WaCrypto.randomToken();
        t.h.jdbc.update("UPDATE wa.driver_device SET jkt=?,public_jwk=?::jsonb WHERE id=?",material.jkt(),jwk.toString(),t.device);
        t.h.jdbc.update("UPDATE wa.device_token SET token_hash=? WHERE id=?",WaCrypto.digest(access),t.actor.credentialId());
        actor=new WaPrincipal(WaPrincipal.Kind.DEVICE,t.actor.credentialId(),t.device,t.f.driver,null,material.jkt(),1,false);
        ledger=t.h.proxy(new WaProofLedger(t.h.jdbc,t.h.clock,t.h.settings));
        finishes=t.h.proxy(new WaFinishService(t.h.jdbc,t.h.settings,t.h.clock,t.h.crypto));
        offer=finishes.request(t.f.driver,t.run(),t.f.s.number("SELECT version FROM wa.operation_run WHERE id=?",t.run()));
    }
    @Test void previewRequiresTheDeviceCredentialAndExactSignedBody() throws Exception {
        ObjectNode body=readBody();String path=previewPath();
        assertEquals(401,post(path,body,false,null,null).getStatus());
        String signed=sign(path,body,true);
        assertEquals(401,post(path,body.deepCopy().put("expectedVersion",body.path("expectedVersion").asLong()+1),true,signed,null).getStatus());
        var accepted=post(path,body,true,null,null);assertEquals(200,accepted.getStatus(),accepted.getContentAsString());
        ObjectNode content=WaJson.object(accepted.getContentAsString());
        assertEquals("READY",content.path("state").asText());assertEquals("Ida de teste",content.path("trip").path("name").asText());
        assertFalse(content.toString().contains(offer.path("intentToken").asText()));
        assertEquals("TRACKING",t.runState());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.finish_intent_binding WHERE confirmed_at IS NOT NULL"));
    }
    @Test void finishAndProofOnlyReceiptUseSeparateBoundCredentials() throws Exception {
        ObjectNode body=readBody().put("intentId",offer.path("intentId").asText()).put("foregroundConfirmed","CONFIRMED");
        String path="/driver-app/v1/runs/"+t.run()+"/finish";
        assertEquals(400,post(path,body,true,null,null).getStatus());
        var completed=post(path,body,true,null,"synthetic-http-finish-once");assertEquals(200,completed.getStatus(),completed.getContentAsString());
        ObjectNode result=WaJson.object(completed.getContentAsString());assertFalse(result.path("receipt").path("stopAcknowledged").asBoolean());
        String ref=result.path("control").path("stopRequestRef").asText();
        t.tracking.state(actor,t.session(),t.state("STOPPED").put("stopRequestRef",ref),"synthetic-http-stop-ack");
        String receiptPath="/driver-app/v1/finish-intents/"+offer.path("intentId").asText()+"/receipt";
        assertEquals(401,post(receiptPath,readBody(),true,null,null).getStatus());
        String signed=sign(receiptPath,readBody(),false);
        var acknowledged=post(receiptPath,readBody(),false,signed,null);assertEquals(200,acknowledged.getStatus(),acknowledged.getContentAsString());
        ObjectNode receipt=WaJson.object(acknowledged.getContentAsString());
        assertTrue(receipt.path("stopAcknowledged").asBoolean());assertFalse(receipt.has("trip"));
        assertEquals(t.session().toString(),receipt.path("trackingSessionId").asText());
        assertEquals(401,post(receiptPath,readBody(),false,signed,null).getStatus());
    }
    private String previewPath(){return "/driver-app/v1/finish-intents/"+offer.path("intentId").asText()+"/preview";}
    private ObjectNode readBody(){return WaJson.object("{}").put("expectedVersion",offer.path("expectedVersion").asLong()).put("intentToken",offer.path("intentToken").asText());}
    private MockHttpServletResponse post(String path,ObjectNode body,boolean device,String proof,String idempotency) throws Exception {
        var request=new MockHttpServletRequest("POST",path);request.setRemoteAddr("127.0.0.1");request.setContentType("application/json");
        request.setContent(body.toString().getBytes(StandardCharsets.UTF_8));request.addHeader("DPoP",proof==null?sign(path,body,device):proof);
        if(device)request.addHeader("Authorization","DPoP "+access);if(idempotency!=null)request.addHeader("Idempotency-Key",idempotency);
        var response=new MockHttpServletResponse();
        var ingress=new WaIngressFilter(t.h.settings,new WaCredentials(t.h.jdbc,t.h.settings,t.h.clock),new WaRateLimiter(),new TrustedProxyClientAddressResolver("",1024,10));
        var proofs=new WaProofFilter(new WaDpopVerifier(t.h.settings,t.h.clock),ledger);var controller=new WaFinishController(finishes);
        ingress.doFilter(request,response,(r,s)->proofs.doFilter(r,s,(rr,ss)->{
            try {
                ObjectNode result=path.endsWith("/preview")?controller.preview(request,offer.path("intentId").asText())
                    :path.endsWith("/receipt")?controller.receipt(request,offer.path("intentId").asText()):controller.finish(request,t.run().toString());
                response.setContentType("application/json");response.getWriter().write(result.toString());
            } catch(WaProblem error){WaHttp.writeError(request,response,error);}
        }));return response;
    }
    private String sign(String path,ObjectNode body,boolean device){
        var claims=new LinkedHashMap<String,Object>();claims.put("htm","POST");claims.put("htu",t.h.settings.baseUrl()+path);
        claims.put("iat",t.h.clock.now().getEpochSecond());claims.put("jti",WaCrypto.randomToken());claims.put("nonce",ledger.issue(material.jkt()));
        claims.put("vr_body_sha256",WaCrypto.base64(WaCrypto.sha256(body.toString().getBytes(StandardCharsets.UTF_8))));
        if(device)claims.put("ath",WaCrypto.base64(WaCrypto.sha256(access.getBytes(StandardCharsets.US_ASCII))));
        return Jwts.builder().header().type("dpop+jwt").add("jwk",WaJson.MAPPER.convertValue(jwk,Map.class)).and().claims(claims).signWith(key.getPrivate(),Jwts.SIG.ES256).compact();
    }
    private static String coordinate(BigInteger value){
        byte[] bytes=value.toByteArray(),fixed=new byte[32];int length=Math.min(32,bytes.length);
        System.arraycopy(bytes,bytes.length-length,fixed,32-length,length);return WaCrypto.base64(fixed);
    }
}
