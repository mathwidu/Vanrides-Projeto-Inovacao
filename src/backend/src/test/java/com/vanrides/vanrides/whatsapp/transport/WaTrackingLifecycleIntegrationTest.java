package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static org.junit.jupiter.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaTrackingLifecycleIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_tracking_lifecycle");
    private WaTrackingTestSupport t;
    @BeforeEach void setup() { t=new WaTrackingTestSupport(POSTGRES); t.activate(); }
    @Test void missingInitialGpsFailsThenReconciledExpiryReleasesReservationWithoutInventingStopAck() {
        t.h.time.advance(31); t.watchdog.tick(); assertEquals("START_FAILED",t.runState());
        assertEquals("STOP_PENDING",t.tracking.status(t.actor).path("sessionState").asText());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE stopped_at IS NOT NULL"));
        t.h.time.advance(301); t.watchdog.tick(); assertEquals("CANCELED",t.runState());
        assertEquals("STOP_PENDING",t.tracking.status(t.actor).path("sessionState").asText());
        var ack=t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        assertEquals("STOP",ack.path("action").asText()); assertTrue(ack.path("accepted").isEmpty()); assertEquals("CANCELED",t.runState());
    }
    @Test void terminalRunKeepsStopReferenceAccessibleUntilAnExplicitSensorAck() {
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=?,version=version+1 WHERE id=?",Timestamp.from(t.h.clock.now()),t.run());
        var pending=t.tracking.status(t.actor); String ref=pending.path("stopRequestRef").asText();
        assertEquals("STOP_PENDING",pending.path("sessionState").asText()); assertEquals("STOP",pending.path("action").asText()); assertFalse(ref.isEmpty());
        assertEquals(WaProblem.Code.VERSION_CONFLICT,assertThrows(WaProblem.class,()->t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"stop-missing-reference")).code());
        var body=t.state("STOPPED").put("stopRequestRef",ref);
        t.tracking.state(t.actor,t.session(),body,"stop-with-reference");
        assertEquals("STOPPED",t.tracking.status(t.actor).path("sessionState").asText());
        assertEquals("STOP",t.tracking.state(t.actor,t.session(),body,"stop-with-reference").path("action").asText());
        assertEquals("COMPLETED",t.runState());
    }
    @Test void stateRenewalIsIdempotentAndDoesNotClaimGps() {
        t.h.time.advance(10); var body=t.state("ACTIVE");
        var first=t.tracking.state(t.actor,t.session(),body,"heartbeat-stable-key"); String lease=first.path("leaseExpiresAt").asText();
        t.h.time.advance(5); var retry=t.tracking.state(t.actor,t.session(),body,"heartbeat-stable-key");
        assertEquals(lease,retry.path("leaseExpiresAt").asText()); assertEquals("STALE",retry.path("gpsState").asText()); assertEquals("STARTING",t.runState());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE last_captured_at IS NOT NULL"));
        assertEquals(WaProblem.Code.IDEMPOTENCY_CONFLICT,assertThrows(WaProblem.class,()->t.tracking.state(t.actor,t.session(),t.state("UNAVAILABLE"),"heartbeat-stable-key")).code());
    }
    @Test void revokedCredentialCannotCollectButCanAcknowledgeItsOwnStop() {
        t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.device);
        t.h.jdbc.update("UPDATE wa.device_token SET revoked_at=? WHERE device_id=?",Timestamp.from(t.h.clock.now()),t.device);
        assertThrows(WaProblem.class,()->t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5))));
        var pending=t.tracking.status(t.actor);
        assertEquals("STOP_PENDING",pending.path("sessionState").asText());
        t.tracking.state(t.actor,t.session(),t.state("STOPPED").put("stopRequestRef",pending.path("stopRequestRef").asText()),"revoked-stop-ack");
        assertEquals("STOPPED",t.tracking.status(t.actor).path("sessionState").asText());
    }
    @Test void leaseExpirationWithoutPacketsStopsAuthorizationAndDoesNotReopenOnHeartbeat() {
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        t.h.time.advance(301); t.watchdog.tick(); assertEquals("EXPIRED",t.sessionState());
        assertEquals("STOP",t.tracking.state(t.actor,t.session(),t.state("ACTIVE"),"expired-heartbeat").path("action").asText());
        assertEquals("DEGRADED",t.runState()); assertEquals("STOP_PENDING",t.tracking.status(t.actor).path("sessionState").asText());
    }
    @Test void unavailableBeforeFirstGpsRequiresANewIntentAndCannotRenewOrRecoverThroughSamples() {
        var response=t.tracking.state(t.actor,t.session(),t.state("UNAVAILABLE"),"initial-provider-unavailable");
        assertEquals("START_FAILED",t.runState()); assertEquals("STOP",response.path("action").asText());
        String lease=response.path("leaseExpiresAt").asText(); t.h.time.advance(1);
        assertEquals(lease,t.tracking.state(t.actor,t.session(),t.state("ACTIVE"),"failed-start-heartbeat").path("leaseExpiresAt").asText());
        var ack=t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        assertTrue(ack.path("accepted").isEmpty()); assertEquals("STOP",ack.path("action").asText()); assertEquals("START_FAILED",t.runState());
        assertEquals(0,t.f.s.number("SELECT count(*) FROM wa.message_outbox WHERE response_part='run-gps-confirmed'"));
    }
}
