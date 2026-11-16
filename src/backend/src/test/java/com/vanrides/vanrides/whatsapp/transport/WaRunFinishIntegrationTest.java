package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaRunFinishes;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.UUID;
import java.util.regex.Pattern;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaRunFinishIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_run_finish_140");
    private WaTrackingTestSupport t;
    private WaTrackingService tracking;
    @BeforeEach void setup() {
        t=new WaTrackingTestSupport(POSTGRES);
        t.h.jdbc.update("UPDATE wa.routine SET destination_latitude=0,destination_longitude=0 WHERE id=?",t.f.item.routine());
        t.activate(); enable(true,true);
    }
    private void enable(boolean enabled,boolean reviewed) {
        var original=t.h.settings;
        var env=new MockEnvironment().withProperty("vanrides.wa.enabled","true")
            .withProperty("vanrides.wa.environment",original.environment()).withProperty("vanrides.wa.public-base-url",original.baseUrl())
            .withProperty("vanrides.wa.start-link-base-url",original.startLinkBaseUrl())
            .withProperty("vanrides.wa.hash-key",Base64.getUrlEncoder().withoutPadding().encodeToString(original.hashKey()))
            .withProperty("vanrides.wa.response-key",Base64.getUrlEncoder().withoutPadding().encodeToString(original.responseKey()))
            .withProperty("vanrides.wa.operator-credential",original.operatorCredential())
            .withProperty("vanrides.wa.operator-expires-at",original.operatorExpiresAt().toString())
            .withProperty("vanrides.wa.auto-finish-enabled",Boolean.toString(enabled))
            .withProperty("vanrides.wa.approach-reviewed-routine-ids",reviewed?t.f.item.routine().toString():"");
        tracking=t.h.proxy(new WaTrackingService(t.h.jdbc,new WaSettings(env),t.h.clock,t.h.crypto));
    }
    private ObjectNode fix(long seq,double lon,Double speed) {
        var fix=t.sample(seq,0,5).put("longitude",lon); if(speed!=null) fix.put("speedMps",speed); return fix;
    }
    private ObjectNode sample(long sequence,double lon,Double speed) { return tracking.samples(t.actor,t.session(),t.batch(fix(sequence,lon,speed))); }
    private void pickupsComplete() { t.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?",t.run()); }
    private ObjectNode arrive() {
        sample(1,.005,10.0); ObjectNode result=null;
        for(int i=2;i<=8;i++) { t.h.time.advance(10); result=sample(i,0,0.0); }
        return result;
    }
    @Test void automaticArrivalUsesSnapshotAndRequiresAnActualStopAck() {
        pickupsComplete();
        t.h.jdbc.update("UPDATE wa.routine SET destination_longitude=1 WHERE id=?",t.f.item.routine());
        String frozen=t.f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",t.run());
        assertEquals("STOP",arrive().path("action").asText());
        assertEquals("COMPLETED",t.runState()); assertEquals("AUTO_ARRIVAL",t.f.s.string("SELECT completion_reason FROM wa.operation_run WHERE id=?",t.run()));
        assertEquals(frozen,t.f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",t.run()));
        assertEquals("STOP_PENDING",tracking.status(t.actor).path("sessionState").asText());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE stopped_at IS NOT NULL"));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.start_intent WHERE run_id=? AND state='ACTIVATED'",t.run()));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.run_finish_progress WHERE run_id=?",t.run()));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE run_id=? AND last_latitude IS NOT NULL",t.run()));
        String ref=tracking.status(t.actor).path("stopRequestRef").asText();
        tracking.state(t.actor,t.session(),t.state("STOPPED").put("stopRequestRef",ref),"finished-stop-ack");
        assertEquals("STOPPED",tracking.status(t.actor).path("sessionState").asText());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-stop-confirmed'"));
        assertEquals("STOP",tracking.state(t.actor,t.session(),t.state("ACTIVE"),"late-finished-heartbeat").path("action").asText());
        assertEquals("COMPLETED",t.runState());
    }
    @Test void defaultFlagAndUnreviewedRoutineNeverGatherArrivalEvidence() {
        assertFalse(t.h.settings.autoFinishEnabled()); pickupsComplete(); enable(true,false); arrive();
        assertEquals("TRACKING",t.runState()); assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.run_finish_progress"));
        enable(false,true); t.h.time.advance(1); sample(9,0,0.0); assertEquals("TRACKING",t.runState());
    }
    @Test void destinationWithoutProgressAndPendingPickupNeverFinish() {
        arrive(); assertEquals("TRACKING",t.runState());
        pickupsComplete();
        for(int i=9;i<=15;i++) { t.h.time.advance(10); sample(i,0,0.0); }
        assertEquals("COMPLETED",t.runState());
    }
    @Test void shortPassageGpsLossAndMissingSpeedNeverCompleteARun() {
        pickupsComplete(); sample(1,.005,10.0); t.h.time.advance(10); sample(2,0,0.0);
        t.h.time.advance(10); sample(3,.001,10.0); assertEquals("TRACKING",t.runState());
        t.h.time.advance(90); t.watchdog.tick(); assertEquals("DEGRADED",t.runState());
        for(int i=4;i<=11;i++) { t.h.time.advance(10); sample(i,0,null); }
        assertEquals("TRACKING",t.runState()); assertNull(t.h.jdbc.queryForObject("SELECT arrived_since FROM wa.run_finish_progress WHERE run_id=?",Timestamp.class,t.run()));
    }
    @Test void anEmptyEligibleRosterStillRequiresTravelOutsideThenDwell() {
        t.h.jdbc.update("UPDATE wa.run_stop SET state='CANCELED',version=version+1 WHERE run_id=?",t.run());
        for(int i=1;i<=8;i++) { t.h.time.advance(10); sample(i,0,0.0); }
        assertEquals("TRACKING",t.runState());
        t.h.time.advance(10); sample(9,.005,10.0);
        for(int i=10;i<=16;i++) { t.h.time.advance(10); sample(i,0,0.0); }
        assertEquals("COMPLETED",t.runState());
    }
    @Test void manualFinishIsReviewedVersionBoundAndIdempotent() {
        sample(1,.005,10.0);
        var review=send("FINALIZAR"); assertNull(review.error()); assertEquals("TRACKING",t.runState());
        String marker=marker(); assertFalse(t.f.s.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",t.f.driver).contains("latitude"));
        var lease=t.f.s.text(t.f.driver,"CONFIRMAR "+marker);
        var context=t.f.s.commands.context(t.f.s.worker,lease.id(),lease.token());
        var body=t.f.s.body(lease,"FINISH_RUN",payload(context)).put("expectedVersion",context.path("entityVersion").asLong());
        var result=t.f.s.execute(lease,body,"manual-finish-idempotent"); assertNull(result.error());
        assertEquals(result,t.f.s.execute(lease,body,"manual-finish-idempotent"));
        t.f.s.finish(lease); assertEquals("COMPLETED",t.runState()); assertEquals("MANUAL_COMPLETION",t.f.s.string("SELECT completion_reason FROM wa.operation_run WHERE id=?",t.run()));
        assertEquals("STOP_PENDING",tracking.status(t.actor).path("sessionState").asText());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE state IN ('STARTING','ACTIVE','DEGRADED','STOP_REQUESTED')"));
        assertEquals("NO_CHANGE",send("FINALIZAR VIAGEM").body().path("outcome").asText());
    }
    @Test void changedRunVersionInvalidatesManualReview() {
        sample(1,.005,10.0); send("FINALIZAR"); String marker=marker();
        t.h.jdbc.update("UPDATE wa.operation_run SET state='DEGRADED',version=version+1 WHERE id=?",t.run());
        assertEquals(WaProblem.Code.VERSION_CONFLICT,send("CONFIRMAR "+marker).error()); assertEquals("DEGRADED",t.runState());
    }
    @Test void anotherActorCannotUseTheFinishCapability() {
        var lease=t.f.s.text(t.f.passenger,"FINALIZAR");
        var result=t.f.s.execute(lease,"FINISH_RUN",WaJson.object("{}").put("runRef","finish_someone_else").put("reason","MANUAL_COMPLETION"));
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED,result.error()); t.f.s.finish(lease); assertEquals("STARTING",t.runState());
    }
    @Test void manualFinalizationRevokesPendingResumeAndCancelsFutureMessages() {
        sample(1,.005,10.0); t.h.time.advance(21); t.watchdog.tick();
        var resume=t.request(true); assertNotNull(resume); send("FINALIZAR VIAGEM"); String marker=marker();
        assertNull(send("CONFIRMAR "+marker).error());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.start_intent WHERE run_id=? AND state IN ('PENDING','ACTIVATED')",t.run()));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE run_id=? AND state IN ('PENDING','LEASED')",t.run()));
    }
    @Test void physicalGpsStopDoesNotCompleteTheBusinessTrip() {
        sample(1,.005,10.0); tracking.state(t.actor,t.session(),t.state("STOPPED"),"technical-stop-only");
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE id=? AND last_latitude IS NOT NULL",t.session()));
        assertEquals("DEGRADED",t.runState()); assertNull(t.h.jdbc.queryForObject("SELECT completed_at FROM wa.operation_run WHERE id=?",Timestamp.class,t.run()));
    }
    @Test void manualFinishRecognizesAnAlreadyAcknowledgedTechnicalStop() {
        sample(1,.005,10.0); tracking.state(t.actor,t.session(),t.state("STOPPED"),"ack-before-finish");
        send("FINALIZAR"); String marker=marker(); assertNull(send("CONFIRMAR "+marker).error());
        String text=t.f.s.string("SELECT o.payload_json->>'text' FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE r.command_type='FINISH_RUN' AND o.payload_json->>'text' LIKE 'Viagem encerrada manualmente.%'");
        assertTrue(text.contains("já confirmou a parada"),text); assertFalse(text.contains("ainda aguarda"),text);
    }
    @Test void progressIsRemovedBySessionCleanupWithoutAnExtraOperatorDelete() {
        sample(1,.005,10.0); assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.run_finish_progress"));
        t.h.jdbc.update("DELETE FROM wa.tracking_sample_receipt WHERE session_id=?",t.session());
        t.h.jdbc.update("DELETE FROM wa.tracking_batch_receipt WHERE session_id=?",t.session());
        t.h.jdbc.update("DELETE FROM wa.tracking_session WHERE id=?",t.session());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.run_finish_progress"));
    }
    private WaDecision send(String text) {
        var lease=t.f.s.text(t.f.driver,text); var context=t.f.s.commands.context(t.f.s.worker,lease.id(),lease.token());
        assertTrue(context.path("allowedCommands").toString().contains("FINISH_RUN"),context.toString());
        var body=t.f.s.body(lease,"FINISH_RUN",payload(context)).put("expectedVersion",context.path("entityVersion").asLong());
        var result=t.f.s.execute(lease,body,"synthetic-finish-"+lease.id()); t.f.s.finish(lease); return result;
    }
    private static ObjectNode payload(ObjectNode context) { return WaJson.object("{}").put("runRef",context.path("options").get(0).path("ref").asText()).put("reason","MANUAL_COMPLETION"); }
    private String marker() {
        String text=t.f.s.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE response_part='run-start' AND payload_json->>'text' LIKE '%CONFIRMAR %' ORDER BY created_at DESC,id DESC LIMIT 1");
        var matcher=Pattern.compile("CONFIRMAR ([BCDFGHJKMNPQRSTVWXYZ]{8})").matcher(text); assertTrue(matcher.find(),text); return matcher.group(1);
    }
}
