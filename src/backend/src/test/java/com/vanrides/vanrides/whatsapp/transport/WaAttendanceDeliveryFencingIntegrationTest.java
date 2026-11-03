package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Duration;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;
import static org.assertj.core.api.Assertions.*;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceDeliveryFencingIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_attendance_delivery_fencing");
    private WaAttendanceDeliveryTestSupport f;

    @BeforeEach
    void setup() { f = new WaAttendanceDeliveryTestSupport(POSTGRES); }

    @Test
    void occurrenceRemovedFromTheCalendarAfterClaimCannotBeSubmitted() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        f.h.jdbc.update("UPDATE wa.routine_occurrence SET calendar_eligible=false WHERE id=?", reminder.occurrence());
        assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.state(reminder)).isEqualTo("CANCELED");
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void optOutBetweenClaimAndAuthorizationCancelsWithoutAnAttempt() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        f.optOut(reminder.passenger());
        assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.state(reminder)).isEqualTo("CANCELED");
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void answerOrSupportAfterLeaseCannotSubmitAnObsoleteQuestion() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        f.h.transaction.executeWithoutResult(tx -> {
            f.answer(reminder);
            assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
            assertThat(f.attempts(reminder)).isZero();
            tx.setRollbackOnly();
        });
        f.pause(reminder.passenger());
        assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void startAfterClaimPreventsAuthorizationWithoutRewritingAttendance() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        f.start(reminder);
        assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.h.jdbc.queryForObject("SELECT state FROM wa.attendance WHERE id=?", String.class, reminder.attendance())).isEqualTo("PENDING");
        assertThat(f.attempts(reminder)).isZero();
    }

    @Test
    void waitingForAProfileRechecksItsCommittedOptOut() throws Exception {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        var executor = Executors.newSingleThreadExecutor();
        var future = new AtomicReference<Future<WaDecision>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", reminder.passenger());
                future.set(executor.submit(() -> f.decision(lease)));
                awaitProfileWait();
                f.optOut(reminder.passenger());
            });
            assertThat(future.get().get(10, TimeUnit.SECONDS).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
            assertThat(f.state(reminder)).isEqualTo("CANCELED");
            assertThat(f.attempts(reminder)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aLeaseExpiringDuringProfileWaitRollsBackTheReservation() throws Exception {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        var executor = Executors.newSingleThreadExecutor();
        var future = new AtomicReference<Future<WaDecision>>();
        try {
            f.h.transaction.executeWithoutResult(tx -> {
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", reminder.passenger());
                future.set(executor.submit(() -> f.decision(lease)));
                awaitProfileWait();
                f.h.time.advance(61);
            });
            assertThatThrownBy(() -> future.get().get(10, TimeUnit.SECONDS))
                    .hasRootCauseInstanceOf(WaProblem.class).hasRootCauseMessage("RESOURCE_NOT_ACCESSIBLE");
            assertThat(f.state(reminder)).isEqualTo("LEASED");
            assertThat(f.attempts(reminder)).isZero();
        } finally {
            executor.shutdownNow();
            assertThat(executor.awaitTermination(10, TimeUnit.SECONDS)).isTrue();
        }
    }

    @Test
    void aliasOriginChangingAfterClaimDoesNotSelectAnotherAddress() {
        var reminder = f.reminder();
        var lease = f.claim(reminder);
        f.h.jdbc.update("UPDATE wa.integration_inbox SET payload_json=payload_json-'senderAliasId' WHERE id=?", reminder.source());
        assertThat(f.decision(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.attempts(reminder)).isZero();
    }

    private void awaitProfileWait() {
        long end = System.nanoTime() + Duration.ofSeconds(5).toNanos();
        while (System.nanoTime() < end) {
            f.h.jdbc.queryForList("SELECT pg_stat_clear_snapshot()");
            Boolean waiting = f.h.jdbc.queryForObject("""
                SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                  AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                  AND query LIKE '%FROM wa.profile%FOR SHARE%')
                """, Boolean.class);
            if (Boolean.TRUE.equals(waiting)) return;
            try {
                Thread.sleep(10);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new AssertionError("Interrupted while waiting for the profile lock", interrupted);
            }
        }
        throw new AssertionError("Submission did not wait for the profile row");
    }
}
