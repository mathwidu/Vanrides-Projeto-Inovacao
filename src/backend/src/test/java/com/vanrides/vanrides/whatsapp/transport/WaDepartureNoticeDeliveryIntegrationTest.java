package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.json;
import static org.assertj.core.api.Assertions.assertThat;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDepartureNoticeDeliveryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_departure_notice_delivery");
    private static final String ADDRESS = "000000000000001@s.whatsapp.net";
    private WaAttendanceCalendarTestSupport f;
    private WaPrincipal delivery;
    private WaOutboxService outbox;
    private UUID notice;

    @BeforeEach
    void setup() {
        f = new WaAttendanceCalendarTestSupport(POSTGRES);
        f.materialize();
        f.h.jdbc.update("UPDATE wa.identity_alias SET alias_value=? WHERE identity_id=?", ADDRESS, f.driver);
        f.h.time.set(Instant.parse("2030-01-02T08:31:00Z"));
        f.h.connection(f.h.principal(WaPrincipal.Kind.CHANNEL), "open", f.h.clock.now());
        delivery = f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER);
        outbox = f.h.proxy(new WaOutboxService(f.h.jdbc, f.h.access, new WaDeliveryReconciler(f.h.jdbc, f.h.clock),
                f.h.settings, f.h.clock));
        assertThat(f.tick("DUE_REMINDERS", 100, null).path("processed").asInt()).isEqualTo(1);
        notice = f.h.jdbc.queryForObject("SELECT outbox_id FROM wa.driver_attendance_notice", UUID.class);
    }

    @Test
    void claimAndAuthorizeReturnTheDriverAddressAndTheStoredText() {
        var lease = claim();
        assertThat(lease.path("purpose").asText()).isEqualTo("COMMAND_RESPONSE");
        var reservation = authorize(lease).requireSuccess();
        assertThat(reservation.path("recipient").path("address").asText()).isEqualTo(ADDRESS);
        assertThat(reservation.path("content").path("text").asText()).isEqualTo(
                f.h.jdbc.queryForObject("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?", String.class, notice));
        assertThat(attempts()).isOne();
    }

    @Test
    void aRunStartedAfterTheClaimCancelsTheNotice() {
        var lease = claim();
        f.h.jdbc.update("""
                INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
                SELECT ?,id,routine_id,driver_id,local_date,'STARTING','attendance-test','{}'::jsonb,? FROM wa.routine_occurrence
                WHERE routine_id=? AND local_date='2030-01-02'
                """, UUID.randomUUID(), Timestamp.from(f.h.clock.now()), f.routine);
        assertThat(authorize(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(state()).isEqualTo("CANCELED");
        assertThat(attempts()).isZero();
    }

    @Test
    void aPausedDriverCancelsTheNotice() {
        var lease = claim();
        f.h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage) VALUES (?,'PAUSED')", f.driver);
        assertThat(authorize(lease).error()).isEqualTo(WaProblem.Code.ACTION_NOT_ALLOWED);
        assertThat(state()).isEqualTo("CANCELED");
        assertThat(attempts()).isZero();
    }

    @Test
    void theNoticeExpiresAtDepartureWithoutAnAttempt() {
        f.h.time.set(Instant.parse("2030-01-02T09:00:00Z"));
        assertThat(outbox.claim(delivery, json("{\"batchLimit\":10,\"leaseSeconds\":60}")).path("items")).isEmpty();
        assertThat(state()).isEqualTo("EXPIRED");
        assertThat(attempts()).isZero();
    }

    private ObjectNode claim() {
        var items = outbox.claim(delivery, json("{\"batchLimit\":10,\"leaseSeconds\":60}")).path("items");
        assertThat(items).hasSize(1);
        assertThat(items.get(0).path("outboxId").asText()).isEqualTo(notice.toString());
        return (ObjectNode) items.get(0);
    }

    private WaDecision authorize(ObjectNode lease) {
        return outbox.authorize(delivery, notice, json("{}").put("leaseToken", lease.path("leaseToken").asText()));
    }

    private String state() {
        return f.h.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, notice);
    }

    private long attempts() {
        return f.h.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?", Long.class, notice);
    }
}
