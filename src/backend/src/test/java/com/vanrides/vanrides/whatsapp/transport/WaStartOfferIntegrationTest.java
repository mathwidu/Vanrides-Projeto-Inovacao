package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.tracking.WaStartDelivery;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaStartOfferIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_start_offers");
    private WaTrackingTestSupport t; private WaRunTestSupport f;
    private record Link(UUID id,ObjectNode body) { }
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); f=t.f; }

    @Test void fourTripsAreDeliveredFromOneMessageWithNoSnapshotOrPublicSecretAndReplayCreatesNothing() {
        ready(4);
        var lease=f.s.text(f.driver,"Começar viagem"); var offered=f.execute(lease,false);
        assertNull(offered.decision().error());
        assertEquals(2,offered.decision().body().path("responseIds").size());
        assertEquals(4,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        assertEquals(1,f.s.number("SELECT count(*) FROM wa.start_offer"));
        Map<UUID,Link> links=links(offered);
        String receipt=f.s.string("SELECT string_agg(result_json::text,'') FROM wa.command_receipt");
        String plain=f.s.string("SELECT string_agg(payload_json::text,'') FROM wa.message_outbox");
        for(Link link:links.values()) {
            assertFalse(receipt.contains(link.body().path("intentToken").asText()));
            assertFalse(plain.contains(link.body().path("intentToken").asText()));
        }
        assertEquals(offered.decision(),f.s.execute(lease,offered.body(),"synthetic-run-command-"+lease.id()));
        assertEquals(4,f.s.number("SELECT count(*) FROM wa.start_intent"));
        List<String> delivered=deliver(offered);
        assertEquals(2,delivered.size());
        assertEquals(4,delivered.stream().mapToInt(text->text.split("/motorista/saida#v1\\.",-1).length-1).sum());
        assertTrue(delivered.stream().allMatch(text->text.contains("confirme no aplicativo")));
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.operation_run"));
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.tracking_session"));
        f.s.finish(lease);
    }

    @Test void thirtyTripsUseTenCompleteMessagesWithinTheFinalTextLimit() {
        ready(30); var offered=f.send("Começar viagem");
        assertNull(offered.decision().error());
        assertEquals(10,offered.decision().body().path("responseIds").size());
        assertEquals(30,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        assertEquals(30,f.s.number("SELECT count(*) FROM wa.start_intent WHERE delivery_outbox_id IS NOT NULL"));
        var delivered=deliver(offered); var ids=new HashSet<String>();
        var pattern=java.util.regex.Pattern.compile("/motorista/saida#v1\\.([0-9a-f-]{36})\\.");
        for(String text:delivered) {
            assertTrue(text.codePointCount(0,text.length())<=2000);
            var matcher=pattern.matcher(text); int count=0;
            while(matcher.find()) { assertTrue(ids.add(matcher.group(1))); count++; }
            assertEquals(3,count);
        }
        assertEquals(10,delivered.size()); assertEquals(30,ids.size());
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void changedOptionCannotActivateAndDoesNotConsumeOrInvalidateItsUnchangedSibling() {
        ready(2); var offered=f.send("Começar viagem"); Map<UUID,Link> links=links(offered);
        Link changed=links.get(f.item.occurrence());
        Link valid=links.entrySet().stream().filter(entry->!entry.getKey().equals(f.item.occurrence())).findFirst().orElseThrow().getValue();
        f.h.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?",f.item.routine());
        var delivered=deliver(offered);
        assertTrue(delivered.get(0).contains("Esta viagem mudou"));
        assertFalse(delivered.get(0).contains(changed.body().path("intentToken").asText()));
        assertTrue(delivered.get(0).contains(valid.body().path("intentToken").asText()));
        assertEquals(WaProblem.Code.VERSION_CONFLICT,assertThrows(WaProblem.class,
            ()->t.tracking.activate(t.actor,changed.id(),changed.body(),"changed-offer-option")).code());
        assertEquals(2,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        var activation=t.tracking.activate(t.actor,valid.id(),valid.body(),"unchanged-offer-option");
        assertNotNull(activation.get("trackingSessionId"));
        assertEquals("REVOKED",f.s.string("SELECT state FROM wa.start_intent WHERE id=?",changed.id()));
        assertEquals(1,f.s.number("SELECT count(*) FROM wa.operation_run"));
        assertEquals(1,f.s.number("SELECT count(*) FROM wa.run_stop"));
    }

    @Test void simultaneousSiblingConfirmationsHaveOneWinnerAndOpeningOrInvalidConfirmationConsumesNothing() throws Exception {
        ready(2); var offered=f.send("Começar viagem"); var links=new ArrayList<>(links(offered).values());
        assertEquals(WaProblem.Code.INVALID_PAYLOAD,assertThrows(WaProblem.class,()->t.tracking.activate(t.actor,links.get(0).id(),
            links.get(0).body().deepCopy().put("foregroundConfirmed","DECLINED"),"not-confirmed-offer")).code());
        assertEquals(2,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->activateAtOnce(links.get(0),"offer-race-a",start));
            var b=executor.submit(()->activateAtOnce(links.get(1),"offer-race-b",start)); start.countDown();
            var outcomes=new ArrayList<WaProblem.Code>(); outcomes.add(a.get(15,TimeUnit.SECONDS)); outcomes.add(b.get(15,TimeUnit.SECONDS));
            assertEquals(1,outcomes.stream().filter(java.util.Objects::isNull).count());
            assertEquals(1,outcomes.stream().filter(WaProblem.Code.INTENT_EXPIRED::equals).count());
            assertEquals(1,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='ACTIVATED'"));
            assertEquals(1,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='REVOKED'"));
            assertEquals(1,f.s.number("SELECT count(*) FROM wa.operation_run"));
            assertEquals(1,f.s.number("SELECT count(*) FROM wa.tracking_session"));
            assertEquals(1,f.s.number("SELECT count(*) FROM wa.run_stop"));
        } finally { executor.shutdownNow(); }
    }

    @Test void renewalLegacyRequestAndExpirationInvalidateWholeOldOffersAndEraseEncryptedPackets() {
        ready(2); var first=f.send("Começar viagem"); Link old=links(first).values().iterator().next();
        t.request(false);
        assertEquals(2,f.s.number("SELECT count(*) FROM wa.start_intent WHERE offer_id IS NOT NULL AND state='REVOKED'"));
        var current=f.send("Começar viagem"); Link expiring=links(current).values().iterator().next();
        assertEquals(WaProblem.Code.INTENT_EXPIRED,assertThrows(WaProblem.class,
            ()->t.tracking.activate(t.actor,old.id(),old.body(),"old-start-offer")).code());
        assertEquals(2,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        f.h.time.advance(301);
        assertEquals(WaProblem.Code.INTENT_EXPIRED,assertThrows(WaProblem.class,
            ()->t.tracking.activate(t.actor,expiring.id(),expiring.body(),"expired-start-offer")).code());
        t.watchdog.tick();
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.start_intent WHERE state='PENDING'"));
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.message_outbox WHERE start_payload_ciphertext IS NOT NULL"));
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void encryptedEnvelopeCannotBorrowAnOptionFromAnotherPage() {
        ready(4); var offered=f.send("Começar viagem");
        UUID first=UUID.fromString(offered.decision().body().path("responseIds").get(0).asText());
        UUID second=UUID.fromString(offered.decision().body().path("responseIds").get(1).asText());
        var firstRow=f.h.jdbc.queryForMap("SELECT command_id,start_payload_ciphertext FROM wa.message_outbox WHERE id=?",first);
        ObjectNode packet=packet(first); var foreign=packet(second).path("options").get(0).deepCopy();
        ((ArrayNode)packet.path("options")).set(0,foreign);
        f.h.jdbc.update("UPDATE wa.message_outbox SET start_payload_ciphertext=? WHERE id=?",
            f.h.crypto.encrypt(WaStartDelivery.aad(first,(UUID)firstRow.get("command_id"),f.driver),WaJson.write(packet)),first);
        var claimed=f.s.outboxClaim(); boolean rejected=false,delivered=false;
        for(var item:claimed.path("items")) {
            UUID box=WaJson.uuid(item,"outboxId");
            if(!box.equals(first) && !box.equals(second)) continue;
            var result=f.s.outbox.authorize(f.s.delivery,box,WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText()));
            if(box.equals(first)) { assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED,result.error()); rejected=true; }
            else {
                var authorized=result.requireSuccess(); delivered=true;
                assertTrue(authorized.path("content").path("text").asText().contains(foreign.path("fragment").asText()));
                f.s.outbox.result(f.s.delivery,box,WaJson.object("{}").put("attemptToken",authorized.path("attemptToken").asText())
                    .put("outcome","SUBMITTED").put("externalMessageId","synthetic-bound-page"));
            }
        }
        assertTrue(rejected); assertTrue(delivered);
        assertEquals("CANCELED",f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",first));
        assertEquals(0,f.s.number("SELECT count(*) FROM wa.message_outbox WHERE id=? AND start_payload_ciphertext IS NOT NULL",first));
    }

    private WaProblem.Code activateAtOnce(Link link,String key,CountDownLatch start) throws InterruptedException {
        start.await();
        try { t.tracking.activate(t.actor,link.id(),link.body(),key); return null; }
        catch(WaProblem conflict) { return conflict.code(); }
    }
    private void ready(int count) {
        for(int i=1;i<count;i++) {
            var item=f.attendance.question(f.passenger,WaRunTestSupport.DAY,i%2==0?"OUTBOUND":"RETURN",LocalTime.MIDNIGHT.plusMinutes(i*20L).toString());
            String prefix="Trajeto "+i+" "; String name=prefix+"Ô".repeat(80-prefix.length());
            f.h.jdbc.update("UPDATE wa.routine SET name=?,stop_order_pending=false WHERE id=?",name,item.routine());
            f.h.jdbc.update("UPDATE wa.routine_occurrence SET schedule_snapshot=jsonb_set(schedule_snapshot,'{name}',to_jsonb(?::text)) WHERE id=?",name,item.occurrence());
        }
    }
    private Map<UUID,Link> links(WaRunTestSupport.Step offered) {
        var result=new HashMap<UUID,Link>();
        for(var value:offered.decision().body().path("responseIds")) {
            UUID box=UUID.fromString(value.asText()); var packet=packet(box);
            for(var option:packet.path("options")) {
                String[] fragment=option.path("fragment").asText().split("\\."); UUID id=WaJson.uuid(option,"intentId");
                UUID occurrence=f.h.jdbc.queryForObject("SELECT occurrence_id FROM wa.start_intent WHERE id=?",UUID.class,id);
                result.put(occurrence,new Link(id,WaJson.object("{}").put("expectedVersion",Long.parseLong(fragment[2]))
                    .put("foregroundConfirmed","CONFIRMED").put("intentToken",fragment[3])));
            }
        }
        return result;
    }
    private ObjectNode packet(UUID box) {
        var row=f.h.jdbc.queryForMap("SELECT command_id,start_payload_ciphertext FROM wa.message_outbox WHERE id=?",box);
        return WaJson.object(f.h.crypto.decrypt(WaStartDelivery.aad(box,(UUID)row.get("command_id"),f.driver),(byte[])row.get("start_payload_ciphertext")));
    }
    private List<String> deliver(WaRunTestSupport.Step offered) {
        var remaining=new HashSet<UUID>(); offered.decision().body().path("responseIds").forEach(value->remaining.add(UUID.fromString(value.asText())));
        var result=new ArrayList<String>();
        for(int batch=0;batch<10 && !remaining.isEmpty();batch++) for(var item:f.s.outboxClaim().path("items")) {
            UUID box=WaJson.uuid(item,"outboxId"); if(!remaining.remove(box)) continue;
            var authorized=f.s.outbox.authorize(f.s.delivery,box,WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).requireSuccess();
            result.add(authorized.path("content").path("text").asText());
            f.s.outbox.result(f.s.delivery,box,WaJson.object("{}").put("attemptToken",authorized.path("attemptToken").asText())
                .put("outcome","SUBMITTED").put("externalMessageId","synthetic-offer-"+UUID.randomUUID()));
        }
        assertTrue(remaining.isEmpty(),"Every offered page must reach authorized delivery"); return result;
    }
}
