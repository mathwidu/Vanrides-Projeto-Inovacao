package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.tracking.WaFinishService;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

/** Business/session/transport tests with synthetic actors and GPS; upstream DPoP verification has its own suite. */
@Testcontainers(disabledWithoutDocker=true)
class WaFinishIntentIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_finish_intent");
    private WaTrackingTestSupport t;
    private WaFinishService finishes;
    @BeforeEach void setup() {
        t=new WaTrackingTestSupport(POSTGRES); t.activate();
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        t.h.jdbc.update("UPDATE wa.driver_device SET public_jwk='{\"kty\":\"EC\"}'::jsonb WHERE id=?",t.device);
        finishes=t.h.proxy(new WaFinishService(t.h.jdbc,t.h.settings,t.h.clock,t.h.crypto));
        assertEquals("TRACKING",t.runState());
    }
    @Test void previewIsReadOnlyAndCompletionRequiresTheExactStopAck() {
        ObjectNode offer=offer(); long version=version();
        ObjectNode preview=finishes.preview(t.actor,id(offer),proofBody(offer));
        assertEquals("READY",preview.path("state").asText());
        assertEquals("Ida de teste",preview.path("trip").path("name").asText());
        assertEquals("15:00:00",preview.path("trip").path("localTime").asText());
        assertEquals(version,version()); assertEquals("TRACKING",t.runState());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_session"));
        assertFalse(preview.toString().contains("latitude")); assertFalse(preview.toString().contains("intentToken"));
        assertThrows(WaProblem.class,()->finishes.confirm(t.actor,t.run(),confirmBody(offer).put("foregroundConfirmed","DECLINED"),"denied-foreground"));
        ObjectNode result=finishes.confirm(t.actor,t.run(),confirmBody(offer),"finish-once");
        assertEquals("COMPLETED",t.runState()); assertEquals("STOP",result.path("control").path("action").asText());
        assertFalse(result.path("receipt").path("stopAcknowledged").asBoolean());
        assertThrows(WaProblem.class,()->t.tracking.state(t.actor,t.session(),t.state("STOPPED").put("stopRequestRef",UUID.randomUUID().toString()),"wrong-stop-ref"));
        ack(result.path("control").path("stopRequestRef").asText());
        ObjectNode receipt=finishes.receipt(proof(t.actor.jkt()),id(offer),proofBody(offer));
        assertTrue(receipt.path("stopAcknowledged").asBoolean());
        assertEquals(offer.path("intentId"),receipt.path("intentId"));
        assertEquals(t.session().toString(),receipt.path("trackingSessionId").asText());
        assertEquals(t.generation(),receipt.path("sessionGeneration").asLong());
    }
    @Test void finishCannotBeUsedToStartOrResumeGps() {
        ObjectNode offer=offer();
        assertThrows(WaProblem.class,()->t.tracking.activate(t.actor,id(offer),t.activateBody(offer),"finish-is-not-start"));
        ObjectNode resume=t.activateBody(offer).put("intentId",id(offer).toString());
        assertThrows(WaProblem.class,()->t.tracking.resume(t.actor,t.run(),resume,"finish-is-not-resume"));
        assertEquals("TRACKING",t.runState()); assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_session"));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.finish_intent_binding WHERE confirmed_at IS NOT NULL"));
    }
    @Test void anExpiredOrForeignOfferCannotCompleteAnyRun() {
        ObjectNode offer=offer();
        WaPrincipal foreign=new WaPrincipal(WaPrincipal.Kind.DEVICE,t.actor.credentialId(),t.device,UUID.randomUUID(),null,t.actor.jkt(),1,false);
        assertThrows(WaProblem.class,()->finishes.preview(foreign,id(offer),proofBody(offer)));
        assertThrows(WaProblem.class,()->finishes.confirm(t.actor,UUID.randomUUID(),confirmBody(offer),"foreign-run"));
        assertThrows(WaProblem.class,()->finishes.confirm(t.actor,t.run(),confirmBody(offer).put("expectedVersion",version()+1),"wrong-version"));
        t.h.time.advance(301);
        assertEquals(WaProblem.Code.INTENT_EXPIRED,assertThrows(WaProblem.class,()->finishes.confirm(t.actor,t.run(),confirmBody(offer),"expired")).code());
        assertEquals("TRACKING",t.runState());
    }
    @Test void onlyTheExactOwnStopVersionTransitionCanBeRecovered() {
        ObjectNode offer=offer(); long expected=version();
        t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"local-stop-first");
        assertEquals("DEGRADED",t.runState()); assertEquals(expected+1,version());
        ObjectNode result=finishes.confirm(t.actor,t.run(),confirmBody(offer),"finish-after-stop");
        assertTrue(result.path("receipt").path("stopAcknowledged").asBoolean());
        assertEquals("COMPLETED",t.runState());
    }
    @Test void anUnrelatedVersionAdvanceStillRequiresANewReview() {
        ObjectNode offer=offer();
        t.h.jdbc.update("UPDATE wa.operation_run SET version=version+1 WHERE id=?",t.run());
        assertEquals(WaProblem.Code.VERSION_CONFLICT,assertThrows(WaProblem.class,()->finishes.confirm(t.actor,t.run(),confirmBody(offer),"stale-review")).code());
        assertEquals("TRACKING",t.runState());
    }
    @Test void aNewFinishCanRecognizeTheSameSessionAlreadyStoppedAtIssueTime() {
        t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"prior-ack");
        String stopped=t.f.s.string("SELECT stopped_at::text FROM wa.tracking_session WHERE id=?",t.session());
        t.h.time.advance(5); ObjectNode offer=offer();
        ObjectNode result=finishes.confirm(t.actor,t.run(),confirmBody(offer),"finish-already-stopped");
        assertTrue(result.path("receipt").path("stopAcknowledged").asBoolean());
        assertEquals(stopped,t.f.s.string("SELECT stopped_at::text FROM wa.tracking_session WHERE id=?",t.session()));
        assertFalse(result.path("receipt").path("finishConsumedAt").isNull());
    }
    @Test void receiptAndReplayNeverChangeALaterTripAndSurviveIntentExpiry() {
        UUID completed=t.run(); ObjectNode offer=offer();
        ObjectNode first=finishes.confirm(t.actor,completed,confirmBody(offer),"finish-and-replay");
        ack(first.path("control").path("stopRequestRef").asText());
        var returning=t.f.attendance.question(t.f.passenger,WaRunTestSupport.DAY,"RETURN","19:00");
        t.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?",returning.routine());
        ObjectNode start=t.tracking.requestStart(t.f.driver,returning.occurrence(),false,t.f.preparation.rosterDigest(t.f.driver,returning.occurrence()));
        ObjectNode next=t.tracking.activate(t.actor,id(start),t.activateBody(start),"start-next-trip");
        t.h.time.advance(301);
        ObjectNode replay=finishes.confirm(t.actor,completed,confirmBody(offer),"finish-and-replay");
        assertTrue(replay.path("receipt").path("stopAcknowledged").asBoolean());
        assertTrue(finishes.receipt(proof(t.actor.jkt()),id(offer),proofBody(offer)).path("stopAcknowledged").asBoolean());
        assertThrows(WaProblem.class,()->finishes.receipt(proof("foreign-key"),id(offer),proofBody(offer)));
        assertEquals("STARTING",t.f.s.string("SELECT state FROM wa.operation_run WHERE id=?",WaJson.uuid(next,"runId")));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE id=? AND stopped_at IS NOT NULL",WaJson.uuid(next,"trackingSessionId")));
    }
    @Test void theBotDeliversAFinishLinkWithoutASecondConfirmationCode() {
        var lease=t.f.s.text(t.f.driver,"Quero finalizar a viagem");
        ObjectNode context=t.f.s.commands.context(t.f.s.worker,lease.id(),lease.token());
        ObjectNode payload=WaJson.object("{}").put("runRef",context.path("options").get(0).path("ref").asText()).put("reason","MANUAL_COMPLETION");
        ObjectNode body=t.f.s.body(lease,"FINISH_RUN",payload).put("expectedVersion",context.path("entityVersion").asLong());
        var decision=t.f.s.execute(lease,body,"finish-conversation"); decision.requireSuccess();
        UUID box=UUID.fromString(decision.body().path("responseIds").get(0).asText());
        ObjectNode chosen=null;
        for(int batch=0;batch<10 && chosen==null;batch++)
            for(var item:t.f.s.outboxClaim().path("items")) if(item.path("outboxId").asText().equals(box.toString())) chosen=(ObjectNode)item;
        assertNotNull(chosen);
        ObjectNode sent=t.f.s.outbox.authorize(t.f.s.delivery,box,WaJson.object("{}").put("leaseToken",chosen.path("leaseToken").asText())).requireSuccess();
        String text=sent.path("content").path("text").asText();
        assertTrue(text.contains("/motorista/fim#v1."),text); assertFalse(text.contains("CONFIRMAR"));
        assertTrue(text.contains("A viagem continua em andamento"),text);
        assertEquals("TRACKING",t.runState());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.finish_intent_binding WHERE confirmed_at IS NOT NULL"));
    }
    @Test void aRevokedDeviceOnlyReadsTheMinimalConsumedReceipt() {
        ObjectNode offer=offer();
        ObjectNode result=finishes.confirm(t.actor,t.run(),confirmBody(offer),"finish-before-revocation");
        ack(result.path("control").path("stopRequestRef").asText());
        t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",java.sql.Timestamp.from(t.h.clock.now()),t.device);
        assertThrows(WaProblem.class,()->finishes.preview(t.actor,id(offer),proofBody(offer)));
        ObjectNode receipt=finishes.receipt(proof(t.actor.jkt()),id(offer),proofBody(offer));
        assertTrue(receipt.path("stopAcknowledged").asBoolean());
        assertFalse(receipt.has("trip"));
    }
    private ObjectNode offer() { return finishes.request(t.f.driver,t.run(),version()); }
    private long version() { return t.f.s.number("SELECT version FROM wa.operation_run WHERE id=?",t.run()); }
    private static UUID id(ObjectNode offer) { return WaJson.uuid(offer,"intentId"); }
    private static ObjectNode proofBody(ObjectNode offer) {
        return WaJson.object("{}").put("intentToken",offer.path("intentToken").asText()).put("expectedVersion",offer.path("expectedVersion").asLong());
    }
    private static ObjectNode confirmBody(ObjectNode offer) {
        return proofBody(offer).put("intentId",id(offer).toString()).put("foregroundConfirmed","CONFIRMED");
    }
    private void ack(String ref) { t.tracking.state(t.actor,t.session(),t.state("STOPPED").put("stopRequestRef",ref),"finish-stop-ack"); }
    private WaDpopVerifier.Proof proof(String jkt) {
        return new WaDpopVerifier.Proof(new WaDpopVerifier.PublicMaterial(jkt,"{\"kty\":\"EC\"}",null),"synthetic-proof",null,t.h.clock.now());
    }
}
