package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaRunGpsDeliveryTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker=true)
class WaRunGpsDeliveryFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_run_gps_delivery_fencing");
    private WaTrackingTestSupport t; private UUID box; private ObjectNode lease;
    @BeforeEach void setup() {
        t=new WaTrackingTestSupport(POSTGRES); t.activate(); t.tracking.samples(t.actor,t.session(),t.batch(t.sample(1,0,5)));
        box=notice(t,"run-gps-confirmed"); lease=lease(t,box);
    }
    @Test void officialStopWinningTheProfileFenceCancelsTheUnreservedNotice() throws Exception {
        mutationFirst(()->t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"stop-before-notice"));
    }
    @Test void reservationWinningTheProfileFenceCanRecordItsResultAfterOfficialStop() throws Exception {
        reservationFirst(()->t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"stop-after-notice"));
    }
    @Test void terminalTransitionWinningTheSameProfileFencePreventsNewReservation() throws Exception {
        mutationFirst(this::terminal);
    }
    @Test void terminalTransitionWaitsForTheAlreadyReservedNotice() throws Exception {
        reservationFirst(this::terminal);
    }
    @Test void freshnessExpiringDuringTheProfileWaitCancelsWithoutAnAttempt() throws Exception {
        assertThat(whileProfileLocked(21)).isEqualTo("ACTION_NOT_ALLOWED"); assertThat(attempts(t,box)).isZero();
        assertThat(t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("CANCELED");
    }
    @Test void intentTtlExpiringDuringTheProfileWaitCannotCreateAnAttempt() throws Exception {
        t.h.jdbc.update("UPDATE wa.message_outbox SET expires_at=? WHERE id=?",Timestamp.from(t.h.clock.now().plusSeconds(2)),box);
        assertThat(whileProfileLocked(3)).isEqualTo("INTENT_EXPIRED"); assertThat(attempts(t,box)).isZero();
        t.f.s.outboxClaim(); assertThat(t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("EXPIRED");
    }
    private void mutationFirst(Runnable mutation) throws Exception {
        var pool=Executors.newSingleThreadExecutor(); var pending=new AtomicReference<Future<String>>();
        try {
            t.h.transaction.executeWithoutResult(tx->{
                mutation.run(); pending.set(pool.submit(this::authorizeOutcome));
                WaDeliveryRetryTestSupport.awaitLock(t.h,"SELECT identity_id FROM wa.profile%FOR SHARE");
            });
            assertThat(pending.get().get(10,TimeUnit.SECONDS)).isEqualTo("ACTION_NOT_ALLOWED");
            assertThat(attempts(t,box)).isZero(); assertThat(t.f.s.string("SELECT state FROM wa.message_outbox WHERE id=?",box)).isEqualTo("CANCELED");
        } finally { pool.shutdownNow(); }
    }
    private void reservationFirst(Runnable mutation) throws Exception {
        var pool=Executors.newSingleThreadExecutor(); var pending=new AtomicReference<Future<?>>(); var reservation=new AtomicReference<ObjectNode>();
        try {
            t.h.transaction.executeWithoutResult(tx->{
                reservation.set(t.f.s.authorize(lease));
                pending.set(pool.submit(()->t.h.transaction.executeWithoutResult(ignored->mutation.run())));
                WaDeliveryRetryTestSupport.awaitLock(t.h,"SELECT identity_id FROM wa.profile%FOR UPDATE");
            });
            pending.get().get(10,TimeUnit.SECONDS);
            assertThat(submitted(t,reservation.get(),"synthetic_notice_before_stop").path("state").asText()).isEqualTo("SUBMITTED");
            assertThat(attempts(t,box)).isEqualTo(1);
        } finally { pool.shutdownNow(); }
    }
    private String whileProfileLocked(int seconds) throws Exception {
        var pool=Executors.newSingleThreadExecutor(); var pending=new AtomicReference<Future<String>>();
        try {
            t.h.transaction.executeWithoutResult(tx->{
                profile(); pending.set(pool.submit(this::authorizeOutcome));
                WaDeliveryRetryTestSupport.awaitLock(t.h,"SELECT identity_id FROM wa.profile%FOR SHARE"); t.h.time.advance(seconds);
            });
            return pending.get().get(10,TimeUnit.SECONDS);
        } finally { pool.shutdownNow(); }
    }
    private String authorizeOutcome() {
        try { t.f.s.authorize(lease); return "RESERVED"; } catch(WaProblem error) { return error.code().name(); }
    }
    private void profile() { t.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",t.f.driver); }
    /** Finish140 remains unavailable; this fixture uses the existing mutators' profile→run order. */
    private void terminal() {
        profile(); t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=?,version=version+1 WHERE id=?",Timestamp.from(t.h.clock.now()),t.run());
    }
}
