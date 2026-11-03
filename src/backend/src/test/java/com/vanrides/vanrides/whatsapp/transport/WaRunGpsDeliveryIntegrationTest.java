package com.vanrides.vanrides.whatsapp.transport;

import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaRunGpsDeliveryTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaRunGpsDeliveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_run_gps_delivery");
    private WaTrackingTestSupport t;
    @BeforeEach void setup() {
        t=new WaTrackingTestSupport(POSTGRES); t.activate(); t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
    }
    @Test void confirmationDeadlineCannotOutliveTheAcquisitionFreshnessWindow() {
        var captured=t.h.clock.now(); t.h.time.advance(19); UUID box=notice(t,"run-gps-confirmed");
        var reservation=t.f.s.authorize(lease(t,box));
        assertThat(reservation.path("submitBefore").asText()).isEqualTo(captured.plusSeconds(20).toString());
    }
    @Test void confirmationDeadlineCannotOutliveTheCurrentSessionLease() {
        var until=t.h.clock.now().plusSeconds(2);
        t.h.jdbc.update("UPDATE wa.tracking_session SET lease_expires_at=? WHERE id=?",Timestamp.from(until),t.session());
        var reservation=t.f.s.authorize(lease(t,notice(t,"run-gps-confirmed")));
        assertThat(reservation.path("submitBefore").asText()).isEqualTo(until.toString());
    }
    @Test void freshResidueCannotAuthorizeStoppedRevokedOrOldDeviceGeneration() {
        UUID box=notice(t,"run-gps-confirmed"); var lease=lease(t,box);
        for (Runnable change:new Runnable[]{
                ()->t.h.jdbc.update("UPDATE wa.tracking_session SET state='STOP_REQUESTED' WHERE id=?",t.session()),
                ()->t.h.jdbc.update("UPDATE wa.driver_device SET status='REVOKED',revoked_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.device),
                ()->t.h.jdbc.update("UPDATE wa.driver_authorization SET generation=generation+1 WHERE driver_id=?",t.f.driver)}) {
            t.h.transaction.executeWithoutResult(tx->{
                change.run(); var result=t.f.s.outbox.authorize(t.f.s.delivery,box,WaTransportTestSupport.json("{}").put("leaseToken",lease.path("leaseToken").asText()));
                assertThat(result.error()).isEqualTo(com.vanrides.vanrides.whatsapp.api.WaProblem.Code.ACTION_NOT_ALLOWED);
                assertThat(t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("CANCELED");
                assertThat(attempts(t,box)).isZero(); tx.setRollbackOnly();
            });
        }
    }
    @Test void anOldGenerationNoticeCannotBorrowFreshGpsFromTheResumedSession() {
        UUID old=notice(t,"run-gps-confirmed"); var oldLease=lease(t,old);
        t.h.time.advance(21); t.watchdog.tick(); var intent=t.request(true);
        t.activation=t.tracking.activate(t.actor,com.vanrides.vanrides.whatsapp.api.WaJson.uuid(intent,"intentId"),t.activateBody(intent),"delivery-resume-current-generation");
        t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        var result=t.f.s.outbox.authorize(t.f.s.delivery,old,WaTransportTestSupport.json("{}").put("leaseToken",oldLease.path("leaseToken").asText()));
        assertThat(result.error()).isEqualTo(com.vanrides.vanrides.whatsapp.api.WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(attempts(t,old)).isZero();
        assertThat(t.f.s.authorize(lease(t,notice(t,"run-gps-confirmed"))).path("content").path("text").asText()).isNotBlank();
    }
    @Test void unavailableNoticeStillWorksAfterTheSessionLeaseExpired() {
        t.h.time.advance(301); t.watchdog.tick(); UUID box=notice(t,"run-gps-unavailable");
        assertThat(t.h.jdbc.queryForObject("SELECT lease_expires_at FROM wa.tracking_session WHERE id=?",Timestamp.class,t.session()).toInstant()).isBeforeOrEqualTo(t.h.clock.now());
        assertThat(t.f.s.authorize(lease(t,box)).path("submitBefore").asText()).isEqualTo(t.h.clock.now().plusSeconds(10).toString());
    }
}
