package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaTrackingActivationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_tracking_activation");
    private WaTrackingTestSupport t;
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); }
    @Test void concurrentActivationCreatesOneSnapshotAndOneGeneration() throws Exception {
        var intent=t.request(false); UUID id=WaJson.uuid(intent,"intentId"); var body=t.activateBody(intent);
        var executor=Executors.newFixedThreadPool(2); var start=new CountDownLatch(1);
        try {
            var a=executor.submit(()->{ start.await(); return t.tracking.activate(t.actor,id,body,"activation-race-a"); });
            var b=executor.submit(()->{ start.await(); return t.tracking.activate(t.actor,id,body,"activation-race-b"); }); start.countDown();
            var first=a.get(15,TimeUnit.SECONDS); var second=b.get(15,TimeUnit.SECONDS);
            assertEquals(first.path("trackingSessionId"),second.path("trackingSessionId"));
            assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.operation_run"));
            assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_session"));
            assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.run_stop"));
        } finally { executor.shutdownNow(); }
    }
    @Test void sameKeyDifferentBodyAndForeignIntentFailWithoutAnotherSession() {
        t.activate(); var body=t.activateBody(t.intent).put("expectedVersion",1);
        assertEquals(WaProblem.Code.IDEMPOTENCY_CONFLICT,assertThrows(WaProblem.class,()->t.tracking.activate(t.actor,WaJson.uuid(t.intent,"intentId"),body,"synthetic-activate-110")).code());
        assertThrows(WaProblem.class,()->t.tracking.activate(t.actor,UUID.randomUUID(),t.activateBody(t.intent),"foreign-intent-id"));
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_session"));
    }
    @Test void resumeUsesFrozenSnapshotAndOldIntentReplayNeverAuthorizesItsSuccessor() {
        t.activate(); UUID firstSession=t.session(); var frozen=t.f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",t.run());
        t.h.time.advance(31); t.watchdog.tick(); assertEquals("START_FAILED",t.runState());
        var resume=t.request(true); var response=t.tracking.activate(t.actor,WaJson.uuid(resume,"intentId"),t.activateBody(resume),"resume-from-start-failed");
        assertEquals(2,response.path("generation").longValue()); assertNotEquals(firstSession.toString(),response.path("trackingSessionId").asText());
        assertEquals(frozen,t.f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",t.run()));
        var replay=t.tracking.activate(t.actor,WaJson.uuid(t.intent,"intentId"),t.activateBody(t.intent),"synthetic-activate-110");
        assertEquals(firstSession.toString(),replay.path("trackingSessionId").asText()); assertEquals(1,replay.path("generation").longValue());
        assertEquals("STOP",replay.path("action").asText());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED')"));
    }
    @Test void expiredIntentAndChangedReviewNeverCreateASnapshot() {
        var intent=t.request(false); t.h.time.advance(301);
        assertEquals(WaProblem.Code.INTENT_EXPIRED,assertThrows(WaProblem.class,()->t.tracking.activate(t.actor,WaJson.uuid(intent,"intentId"),t.activateBody(intent),"expired-start-intent")).code());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.operation_run"));
    }
    @Test void tokenIsEncryptedUntilAuthorizedDeliveryAndNotPersistedInPublicReceipt() {
        var intent=t.request(false); String token=intent.path("intentToken").asText();
        assertFalse(t.f.s.string("SELECT result_json::text FROM wa.command_receipt WHERE command_type='REQUEST_START'").contains(token));
        assertFalse(t.f.s.string("SELECT payload_json::text FROM wa.message_outbox WHERE start_intent_id=?",WaJson.uuid(intent,"intentId")).contains(token));
        var claimed=t.f.s.outbox.claim(t.f.s.delivery,WaJson.object("{\"batchLimit\":10,\"leaseSeconds\":60}"));
        UUID box=t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE start_intent_id=?",UUID.class,WaJson.uuid(intent,"intentId"));
        com.fasterxml.jackson.databind.JsonNode item=null; for(var row:claimed.path("items")) if(box.toString().equals(row.path("outboxId").asText())) item=row;
        assertNotNull(item); assertFalse(claimed.toString().contains(token));
        var reserved=t.f.s.outbox.authorize(t.f.s.delivery,box,WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText())).requireSuccess();
        assertTrue(reserved.path("content").path("text").asText().contains("https://vanrides.invalid/motorista/saida#v1."+intent.path("intentId").asText()+"."));
        assertTrue(reserved.path("content").path("text").asText().endsWith(token));
    }
    @Test void changedReviewCancelsTheLinkBeforeTransportCanRevealItsToken() {
        var intent=t.request(false); UUID id=WaJson.uuid(intent,"intentId");
        t.h.jdbc.update("UPDATE wa.routine SET version=version+1 WHERE id=?",t.f.item.routine());
        var claimed=t.f.s.outbox.claim(t.f.s.delivery,WaJson.object("{\"batchLimit\":10,\"leaseSeconds\":60}"));
        UUID box=t.h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE start_intent_id=?",UUID.class,id);
        com.fasterxml.jackson.databind.JsonNode item=null; for(var row:claimed.path("items")) if(box.toString().equals(row.path("outboxId").asText())) item=row;
        assertNotNull(item);
        var denied=t.f.s.outbox.authorize(t.f.s.delivery,box,WaJson.object("{}").put("leaseToken",item.path("leaseToken").asText()));
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED,denied.error());
        assertEquals("CANCELED",t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE id=? AND start_payload_ciphertext IS NOT NULL",box));
    }
}
