package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaTrackingSamplesIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_tracking_samples");
    private WaTrackingTestSupport t;
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); t.activate(); }
    @Test void partialAckPersistsEachOutcomeAndConfirmsOnlyTheFirstValidAcquisition() {
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
        var body=t.batch(t.sample(1,0,5),t.sample(2,-21,5),t.sample(3,0,51),t.sample(4,11,5));
        var ack=t.tracking.samples(t.actor,t.session(),body);
        assertEquals("[1]",ack.path("accepted").toString()); assertEquals(3,ack.path("rejected").size());
        assertEquals("STALE",ack.path("rejected").get(0).path("code").asText());
        assertEquals("LOW_ACCURACY",ack.path("rejected").get(1).path("code").asText());
        assertEquals("FUTURE",ack.path("rejected").get(2).path("code").asText());
        assertEquals("TRACKING",t.runState()); assertEquals("FRESH",ack.path("gpsState").asText());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
        assertEquals(4,t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt WHERE session_id=?",t.session()));
        var replay=t.tracking.samples(t.actor,t.session(),body);
        assertEquals(receipt(ack),receipt(replay));
        for(String field:new String[]{"sessionId","generation","serverTime","action","gpsState","leaseExpiresAt","stopRequestRef"})
            assertEquals(WaJson.canonical(ack.path(field)),WaJson.canonical(replay.path(field)),field);
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
    }
    @Test void duplicateAndSequenceConflictDoNotMoveAcquisitionOrRenewTheLease() {
        var sample=t.sample(1,0,5); t.tracking.samples(t.actor,t.session(),t.batch(sample));
        var before=t.h.jdbc.queryForMap("SELECT last_captured_at,last_received_at,lease_expires_at FROM wa.tracking_session WHERE id=?",t.session());
        t.h.time.advance(10);
        var retry=t.tracking.samples(t.actor,t.session(),t.batch(sample));
        assertEquals("[1]",retry.path("duplicate").toString());
        assertEquals(before,t.h.jdbc.queryForMap("SELECT last_captured_at,last_received_at,lease_expires_at FROM wa.tracking_session WHERE id=?",t.session()));
        var conflict=t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        assertEquals("SEQUENCE_CONFLICT",conflict.path("rejected").get(0).path("code").asText());
        var newest=t.tracking.samples(t.actor,t.session(),t.batch(t.sample(3,0,5)));
        assertEquals("[3]",newest.path("accepted").toString());
        assertEquals("OUT_OF_ORDER",t.tracking.samples(t.actor,t.session(),t.batch(t.sample(2,0,5))).path("rejected").get(0).path("code").asText());
    }
    @Test void repeatedBatchWithDifferentBodyFailsAndLeavesDurableReceiptsUnchanged() {
        var body=t.batch(t.sample(1,0,5)); t.tracking.samples(t.actor,t.session(),body);
        ((com.fasterxml.jackson.databind.node.ObjectNode)body.path("samples").get(0)).put("accuracyM",6);
        assertEquals(WaProblem.Code.IDEMPOTENCY_CONFLICT,assertThrows(WaProblem.class,()->t.tracking.samples(t.actor,t.session(),body)).code());
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt"));
    }
    @Test void oldAcquisitionAndHeartbeatNeverPreventWatchdogDegradationWithoutNewPackets() {
        var body=t.batch(t.sample(1,0,5)); t.tracking.samples(t.actor,t.session(),body);
        t.h.time.advance(19); t.tracking.state(t.actor,t.session(),t.state("ACTIVE"),"heartbeat-before-stale");
        Timestamp captured=t.h.jdbc.queryForObject("SELECT last_captured_at FROM wa.tracking_session WHERE id=?",Timestamp.class,t.session());
        t.h.time.advance(2); t.watchdog.tick();
        assertEquals("DEGRADED",t.runState()); assertEquals("STALE",t.tracking.status(t.actor).path("gpsState").asText());
        assertEquals("CONTINUE",t.tracking.samples(t.actor,t.session(),body).path("action").asText());
        assertEquals("DEGRADED",t.runState());
        assertEquals(captured,t.h.jdbc.queryForObject("SELECT last_captured_at FROM wa.tracking_session WHERE id=?",Timestamp.class,t.session()));
        t.watchdog.tick(); assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-unavailable'"));
    }
    @Test void futureClockDoesNotConfirmStartAndSamePlaceWithNewTimestampRecovers() {
        var body=t.batch(t.sample(1,11,5));
        assertEquals("CLOCK_SKEW",t.tracking.samples(t.actor,t.session(),body).path("gpsState").asText());
        assertEquals("STARTING",t.runState()); assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(2,0,5))); t.h.time.advance(1);
        assertEquals("[3]",t.tracking.samples(t.actor,t.session(),t.batch(t.sample(3,0,5))).path("accepted").toString());
        assertEquals("TRACKING",t.runState());
    }
    @Test void wrongOwnerSessionAndGenerationCannotPersistReceipts() {
        var wrong=t.batch(t.sample(1,0,5)).put("generation",2);
        assertThrows(WaProblem.class,()->t.tracking.samples(t.actor,t.session(),wrong));
        assertThrows(WaProblem.class,()->t.tracking.samples(t.actor,UUID.randomUUID(),t.batch(t.sample(1,0,5))));
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_batch_receipt"));
        assertFalse(WaJson.write(t.tracking.status(t.actor)).contains("latitude"));
    }
    @Test void batchReplayKeepsSequenceOutcomesWhileGpsLeaseAndStopReflectCurrentAuthorization() {
        var body=t.batch(t.sample(1,0,5)); var first=t.tracking.samples(t.actor,t.session(),body);
        String persisted=t.f.s.string("SELECT result_json::text FROM wa.tracking_batch_receipt WHERE session_id=?",t.session());
        assertEquals(3,WaJson.object(persisted).size());
        var position=t.h.jdbc.queryForMap("SELECT last_sequence,last_captured_at,last_received_at FROM wa.tracking_session WHERE id=?",t.session());
        t.h.time.advance(21); t.watchdog.tick();
        var stale=t.tracking.samples(t.actor,t.session(),body);
        assertEquals(receipt(first),receipt(stale)); assertEquals("STALE",stale.path("gpsState").asText());
        assertEquals("CONTINUE",stale.path("action").asText()); assertEquals(t.h.clock.now().toString(),stale.path("serverTime").asText());
        assertEquals(first.path("leaseExpiresAt").asText(),stale.path("leaseExpiresAt").asText());
        assertEquals("DEGRADED",t.runState());
        assertEquals(persisted,t.f.s.string("SELECT result_json::text FROM wa.tracking_batch_receipt WHERE session_id=?",t.session()));
        // Simulate an older full receipt. Its stale CONTINUE/FRESH must never be trusted.
        t.h.jdbc.update("UPDATE wa.tracking_batch_receipt SET result_json=?::jsonb WHERE session_id=?",first.toString(),t.session());
        t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=?,version=version+1 WHERE id=?",Timestamp.from(t.h.clock.now()),t.run());
        var stopped=t.tracking.samples(t.actor,t.session(),body);
        assertEquals(receipt(first),receipt(stopped)); assertEquals("STOP",stopped.path("action").asText());
        assertEquals("STALE",stopped.path("gpsState").asText()); assertTrue(stopped.path("stopRequestRef").isTextual());
        assertEquals(t.h.clock.now().toString(),stopped.path("leaseExpiresAt").asText());
        assertEquals("COMPLETED",t.runState());
        assertEquals(position,t.h.jdbc.queryForMap("SELECT last_sequence,last_captured_at,last_received_at FROM wa.tracking_session WHERE id=?",t.session()));
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt WHERE session_id=?",t.session()));
        assertEquals(1,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
    }
    private String receipt(com.fasterxml.jackson.databind.JsonNode body) {
        var outcomes=WaJson.object("{}");
        for(String field:new String[]{"accepted","duplicate","rejected"}) outcomes.set(field,body.path(field));
        return WaJson.canonical(outcomes);
    }
}
