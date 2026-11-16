package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaAttendanceConversationTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaStopCanceledNoticeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_stop_canceled_notice");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);
    private static final String ABSENCE = "Não vou hoje na ida";
    private WaAttendanceConversationTestSupport f;
    private UUID passenger;

    @BeforeEach
    void setup() {
        f = new WaAttendanceConversationTestSupport(POSTGRES);
        passenger = f.passenger();
        f.h.jdbc.update("UPDATE wa.profile SET display_name='Ana' WHERE identity_id=?", passenger);
    }

    @Test
    void aLateAbsenceQueuesExactlyOneNoticeForTheDriver() {
        driverWrote();
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "PENDING");
        f.send(passenger, ABSENCE);
        UUID stop = f.h.jdbc.queryForObject("SELECT id FROM wa.run_stop WHERE run_id=?", UUID.class, run);
        var row = f.h.jdbc.queryForMap("""
                SELECT o.recipient_identity_id,o.purpose,o.policy_version,o.business_key,o.command_id,o.payload_json->>'text' AS text,
                  extract(epoch FROM o.expires_at-o.created_at) AS ttl,n.kind,n.run_id,n.stop_id,n.passenger_id,n.occurrence_id,n.deadline_at=o.expires_at AS same_deadline
                FROM wa.message_outbox o JOIN wa.driver_attendance_notice n ON n.outbox_id=o.id
                """);
        assertThat(row.get("text")).isEqualTo("Ana não vai mais na viagem de hoje, terça (01/01), ida às 15:00 (Ida de teste).\n"
                + "A parada foi retirada da rota.");
        assertThat(row).containsEntry("recipient_identity_id", f.driver).containsEntry("purpose", "OPERATIONAL")
                .containsEntry("policy_version", "driver-attendance-v1").containsEntry("business_key", "attendance-stop-canceled:" + stop)
                .containsEntry("kind", "STOP_CANCELED").containsEntry("run_id", run).containsEntry("stop_id", stop)
                .containsEntry("passenger_id", passenger).containsEntry("occurrence_id", item.occurrence())
                .containsEntry("same_deadline", true);
        assertThat(row.get("command_id")).isNull();
        assertThat(((Number) row.get("ttl")).intValue()).isEqualTo(300);
        f.send(passenger, ABSENCE);
        assertThat(notices()).isOne();
    }

    @Test
    void replayReturnsTheSameReceiptWithoutAnotherCancellationNotice() {
        driverWrote();
        f.starting(f.question(passenger, DAY, "OUTBOUND", "15:00"), "PENDING");
        var lease = f.s.text(passenger, ABSENCE);
        var first = f.execute(lease, false);
        var replay = f.s.execute(lease, first.body(), "synthetic-attendance-command-" + lease.id());
        assertThat(replay.body()).isEqualTo(first.decision().body());
        assertThat(notices()).isOne();
        assertThat(f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?", lease.id())).isOne();
        f.s.finish(lease);
    }

    @Test
    void anAbsenceBeforeTheDepartureQueuesNoDriverNotice() {
        driverWrote();
        f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.send(passenger, ABSENCE);
        assertThat(notices()).isZero();
    }

    @Test
    void aDriverWithoutAnInboundMessageSkipsTheNoticeAndTheAnswerStillWorks() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID run = f.starting(item, "PENDING");
        var result = f.send(passenger, ABSENCE);
        assertThat(result.text()).endsWith("O motorista já saiu e sua parada foi retirada desta viagem.");
        assertThat(f.state(item)).isEqualTo("NOT_GOING");
        assertThat(f.s.string("SELECT state FROM wa.run_stop WHERE run_id=?", run)).isEqualTo("CANCELED");
        assertThat(notices()).isZero();
    }

    @Test
    void claimAndAuthorizeReturnTheDriverAddressAndTheStoredText() {
        var lease = queuedNotice();
        var reservation = authorize(lease).requireSuccess();
        assertThat(lease.path("purpose").asText()).isEqualTo("COMMAND_RESPONSE");
        assertThat(reservation.path("recipient").path("address").asText()).isEqualTo(
                f.s.string("SELECT alias_value FROM wa.identity_alias WHERE identity_id=?", f.driver));
        assertThat(reservation.path("content").path("text").asText()).startsWith("Ana não vai mais na viagem de hoje");
        assertThat(attempts(noticeBox())).isOne();
    }

    @Test
    void aCompletedRunCancelsTheNotice() {
        var lease = queuedNotice();
        f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=? WHERE driver_id=?",
                Timestamp.from(f.h.clock.now()), f.driver);
        assertThat(authorize(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(f.s.string("SELECT state FROM wa.message_outbox WHERE id=?", noticeBox())).isEqualTo("CANCELED");
        assertThat(attempts(noticeBox())).isZero();
    }

    @Test
    void aSuspendedDriverCannotReceiveANoticeQueuedBeforeTheSuspension() {
        var lease = queuedNotice();
        f.h.jdbc.update("UPDATE wa.profile SET status='SUSPENDED',driver_enabled=false WHERE identity_id=?", f.driver);
        assertThat(authorize(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(attempts(noticeBox())).isZero();
    }

    @Test
    void noticeAuthorizationWaitsForTheDriverFenceWithoutHoldingTheRun() throws Exception {
        var lease = queuedNotice();
        f.h.jdbc.update("INSERT INTO wa.conversation_state(identity_id) VALUES (?) ON CONFLICT DO NOTHING", f.driver);
        var executor = java.util.concurrent.Executors.newSingleThreadExecutor();
        try {
            var pending = f.h.transaction.execute(status -> {
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE", f.driver);
                f.h.jdbc.queryForList("SELECT identity_id FROM wa.conversation_state WHERE identity_id=? FOR UPDATE", f.driver);
                var future = executor.submit(() -> authorize(lease));
                long deadline = System.nanoTime() + java.util.concurrent.TimeUnit.SECONDS.toNanos(5);
                boolean fenced = false;
                while (System.nanoTime() < deadline) {
                    f.h.jdbc.execute("SELECT pg_stat_clear_snapshot()");
                    fenced = Boolean.TRUE.equals(f.h.jdbc.queryForObject("""
                        SELECT EXISTS(SELECT 1 FROM pg_stat_activity WHERE datname=current_database()
                          AND pid<>pg_backend_pid() AND wait_event_type='Lock'
                          AND query LIKE 'SELECT identity_id FROM wa.profile%')
                        """, Boolean.class));
                    if (fenced) break;
                    try { Thread.sleep(10); } catch (InterruptedException interrupted) {
                        Thread.currentThread().interrupt(); throw new IllegalStateException(interrupted);
                    }
                }
                assertThat(fenced).as("delivery must wait on profile before it locks the run").isTrue();
                f.h.jdbc.execute("SET LOCAL lock_timeout='1s'");
                f.h.jdbc.queryForList("SELECT id FROM wa.operation_run WHERE driver_id=? FOR UPDATE", f.driver);
                f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=? WHERE driver_id=?",
                    Timestamp.from(f.h.clock.now()), f.driver);
                return future;
            });
            assertThat(pending.get(5, java.util.concurrent.TimeUnit.SECONDS).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
            assertThat(attempts(noticeBox())).isZero();
        } finally { executor.shutdownNow(); }
    }

    private ObjectNode queuedNotice() {
        driverWrote();
        f.starting(f.question(passenger, DAY, "OUTBOUND", "15:00"), "PENDING");
        f.send(passenger, ABSENCE);
        UUID box = noticeBox();
        for (var item : f.s.outboxClaim().path("items")) {
            if (item.path("outboxId").asText().equals(box.toString())) return (ObjectNode) item;
        }
        throw new AssertionError("Expected the driver notice lease");
    }

    private WaDecision authorize(ObjectNode lease) {
        return f.s.outbox.authorize(f.s.delivery, noticeBox(), json().put("leaseToken", lease.path("leaseToken").asText()));
    }

    private void driverWrote() {
        f.s.pending(f.driver, json().put("conversation", "Bom dia"), f.h.clock.now());
    }

    private UUID noticeBox() {
        return f.h.jdbc.queryForObject("SELECT outbox_id FROM wa.driver_attendance_notice", UUID.class);
    }

    private long notices() {
        return f.s.number("SELECT count(*) FROM wa.driver_attendance_notice");
    }

    private long attempts(UUID box) {
        return f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", box);
    }
}
