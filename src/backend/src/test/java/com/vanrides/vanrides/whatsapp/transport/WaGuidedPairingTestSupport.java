package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingLinks;
import java.math.BigInteger;
import java.nio.charset.StandardCharsets;
import java.security.KeyPairGenerator;
import java.security.interfaces.ECPublicKey;
import java.security.spec.ECGenParameterSpec;
import java.time.Instant;
import java.util.UUID;
import java.util.regex.Pattern;
import static org.junit.jupiter.api.Assertions.*;

/** Real ingress/outbox/transactions, with generated test keys and synthetic messages. */
final class WaGuidedPairingTestSupport {
    record Client(WaDpopVerifier.Proof proof,ObjectNode jwk,String verifier) { }
    record Step(WaDecision decision,UUID box) { }
    record Link(UUID id,String token,UUID box) { }
    final WaRunTestSupport f;
    final WaPairingLinks links;
    WaGuidedPairingTestSupport(WaRunTestSupport fixture) {
        f=fixture;links=f.h.proxy(new WaPairingLinks(f.h.jdbc,f.h.settings,f.h.clock,f.h.crypto));
    }
    Step send(String text) { return send(text,f.h.clock.now(),null,true); }
    Step send(String text,Instant sent,String quote,boolean providerTime) {
        var lease=message(text,sent,quote,providerTime);
        ObjectNode context=f.s.commands.context(f.s.worker,lease.id(),lease.token());
        String type=context.path("allowedCommands").toString().contains("\"FINISH_RUN\"")?"FINISH_RUN":"RESPOND_TO_MESSAGE";
        ObjectNode payload=WaJson.object("{}");
        if(type.equals("FINISH_RUN")) payload.put("runRef",context.path("options").get(0).path("ref").asText()).put("reason","MANUAL_COMPLETION");
        else payload.put("responseCode","CONTINUE");
        WaDecision result=f.s.execute(lease,f.s.body(lease,type,payload),"synthetic-guided-"+UUID.randomUUID());
        UUID box=result.error()==null?UUID.fromString(result.body().path("responseIds").get(0).asText())
            :f.h.jdbc.queryForObject("""
              SELECT o.id FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE r.inbox_event_id=?
              """,UUID.class,lease.id());
        f.s.finish(lease);return new Step(result,box);
    }
    WaConversationTestSupport.Lease message(String text,Instant sent,String quote,boolean providerTime) {
        ObjectNode envelope=WaJson.object("{}").put("event","messages.upsert").put("instance",WaTransportTestSupport.INSTANCE)
            .put("date_time",f.h.clock.now().toString());
        var data=envelope.putObject("data");
        data.putObject("key").put("id","synthetic-guided-message-"+UUID.randomUUID()).put("fromMe",false).put("remoteJid",
            f.s.string("SELECT alias_value FROM wa.identity_alias WHERE identity_id=? AND alias_type='PN'",f.driver));
        data.putObject("message").put("conversation",text);
        if(providerTime) data.put("messageTimestamp",sent.getEpochSecond());
        if(quote!=null) data.putObject("contextInfo").put("stanzaId",quote);
        return f.s.claim(WaJson.uuid(f.s.events.accept(f.s.channel,envelope),"receiptId"));
    }
    Link issue() {
        Step step=send("Conectar meu celular");step.decision().requireSuccess();
        String text=deliver(step.box());
        var match=Pattern.compile("#v1\\.([a-f0-9-]{36})\\.([A-Za-z0-9_-]{43})").matcher(text);
        assertTrue(match.find(),text);return new Link(UUID.fromString(match.group(1)),match.group(2),step.box());
    }
    ObjectNode claim(Link link,Client client,String key) {
        ObjectNode body=WaJson.object("{}").put("linkToken",link.token())
            .put("pkceChallenge",WaCrypto.base64(WaCrypto.sha256(client.verifier().getBytes(StandardCharsets.US_ASCII))))
            .put("pkceMethod","S256").put("platform","ANDROID").put("appVersion","synthetic-guided");
        body.set("publicJwk",client.jwk());return links.claim(client.proof(),link.id(),body,key);
    }
    ObjectNode redeem(Client client,ObjectNode claim,String key) {
        return f.s.pairing.redeem(client.proof(),WaJson.object("{}").put("pairingId",claim.path("pairingId").asText())
            .put("pkceVerifier",client.verifier()),key).requireSuccess();
    }
    UUID review(Link link) { return f.h.jdbc.queryForObject("SELECT review_outbox_id FROM wa.pairing_handoff WHERE id=?",UUID.class,link.id()); }
    String external(UUID box) { return f.s.string("SELECT external_message_id FROM wa.message_attempt WHERE outbox_id=? AND state='SUBMITTED'",box); }
    String deliver(UUID box) {
        for(int page=0;page<10;page++) for(var item:f.s.outboxClaim().path("items")) {
            if(!item.path("outboxId").asText().equals(box.toString())) continue;
            var authorized=f.s.authorize((ObjectNode)item);
            f.s.outbox.result(f.s.delivery,box,WaJson.object("{}").put("attemptToken",authorized.path("attemptToken").asText())
                .put("outcome","SUBMITTED").put("externalMessageId","synthetic-guided-reply-"+UUID.randomUUID()));
            return authorized.path("content").path("text").asText();
        }
        throw new AssertionError("Expected guided reply was not delivered");
    }
    static Client client() throws Exception {
        var generator=KeyPairGenerator.getInstance("EC");generator.initialize(new ECGenParameterSpec("secp256r1"));
        var key=(ECPublicKey)generator.generateKeyPair().getPublic();
        var jwk=WaJson.object("{}").put("kty","EC").put("crv","P-256").put("x",coordinate(key.getW().getAffineX()))
            .put("y",coordinate(key.getW().getAffineY()));
        return new Client(new WaDpopVerifier.Proof(WaDpopVerifier.publicMaterial(jwk),"synthetic-proof",null,WaTransportTestSupport.NOW),jwk,WaCrypto.randomToken());
    }
    private static String coordinate(BigInteger value) {
        byte[] raw=value.toByteArray(),out=new byte[32];int n=Math.min(32,raw.length);
        System.arraycopy(raw,raw.length-n,out,32-n,n);return WaCrypto.base64(out);
    }
}
