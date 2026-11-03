package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

import static org.assertj.core.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerDeliveryHistoryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = postgres("wa_passenger_delivery_history");
    private WaPassengerDeliveryTestSupport f;

    @BeforeEach
    void setup() {
        f = new WaPassengerDeliveryTestSupport(POSTGRES);
    }

    @Test
    void terminalAndRectificationKindsDeriveTheirOwnCounterparty() {
        for (String kind : new String[] {"REJECTED", "RECTIFY", "CLOSED"}) {
            var pair = f.pair(kind.equals("RECTIFY") ? "REQUESTED" : kind);
            f.h.jdbc.update(
                    "UPDATE wa.pickup_point SET state='REJECTED',valid_until=?,version=version+1"
                            + " WHERE id=?",
                    java.sql.Timestamp.from(f.h.clock.now()),
                    pair.point());
            UUID command = f.command(pair.driver(), WaPassengerResponses.commandFor(kind));
            UUID box = f.peer(pair, command, pair.driver(), kind, pair.point());
            assertThat(
                            f.h.jdbc.queryForObject(
                                    "SELECT recipient_identity_id FROM wa.message_outbox WHERE"
                                            + " id=?",
                                    UUID.class,
                                    box))
                    .isEqualTo(pair.passenger());
            var reservation = f.authorize(f.claim(box));
            assertThat(reservation.path("recipient").path("address").asText())
                    .isEqualTo(f.address(pair.passenger()));
            assertThat(reservation.path("content").path("text").asText()).doesNotContain("maps");
            assertThat(f.submitted(reservation, "TERMINAL_" + kind).path("state").asText())
                    .isEqualTo("SUBMITTED");
        }
        var pair = f.pair("CLOSED");
        UUID command = f.command(pair.passenger(), "CLOSE_LINK");
        UUID box = f.peer(pair, command, pair.passenger(), "CLOSED", null);
        assertThat(
                        f.h.jdbc.queryForObject(
                                "SELECT recipient_identity_id FROM wa.message_outbox WHERE id=?",
                                UUID.class,
                                box))
                .isEqualTo(pair.driver());
    }

    @Test
    void resultAndMonotonicCallbacksSurviveSubjectChangesAfterReservation() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var reservation = f.authorize(f.claim(box));
        f.h.jdbc.update(
                "UPDATE wa.driver_passenger_link SET state='REJECTED',version=version+1 WHERE id=?",
                pair.link());
        f.h.jdbc.update(
                "UPDATE wa.profile SET status='SUSPENDED' WHERE identity_id=?", pair.driver());
        f.callback("PASSENGER_LATE_RESULT", "READ");
        var first = f.submitted(reservation, "PASSENGER_LATE_RESULT");
        assertThat(first.path("state").asText()).isEqualTo("READ");
        assertThat(f.submitted(reservation, "PASSENGER_LATE_RESULT")).isEqualTo(first);
        f.callback("PASSENGER_LATE_RESULT", "SERVER_ACK");
        f.claim();
        assertThat(f.state(box)).isEqualTo("READ");
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }

    @Test
    void uncertainAttemptDoesNotRequeueEvenAfterRestartOrLinkRevision() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        var lease = f.claim(box); var reservation = f.authorize(lease);
        assertThatThrownBy(() -> f.authorize(lease)).isInstanceOfSatisfying(WaProblem.class,
            error -> assertThat(error.code()).isEqualTo(WaProblem.Code.IDEMPOTENCY_CONFLICT));
        f.h.time.advance(11);
        f.claim();
        assertThat(f.state(box)).isEqualTo("UNKNOWN");
        f.h.jdbc.update(
                "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", pair.link());
        var restarted =
                f.h.proxy(
                        new WaOutboxService(
                                f.h.jdbc,
                                f.h.access,
                                new WaDeliveryReconciler(f.h.jdbc, f.h.clock),
                                f.h.settings,
                                f.h.clock));
        assertThat(
                        restarted
                                .claim(f.delivery, json("{\"batchLimit\":10,\"leaseSeconds\":60}"))
                                .path("items"))
                .isEmpty();
        assertThat(f.submitted(reservation, "PASSENGER_DELAYED_RESULT").path("state").asText())
                .isEqualTo("SUBMITTED");
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }

    @Test
    void expiredResponseDoesNotExposeAddressOrMap() {
        var pair = f.pair("REQUESTED");
        UUID box = f.peer(pair, "REQUESTED");
        f.h.time.advance(301);
        assertThat(f.claim().path("items")).isEmpty();
        assertThat(f.state(box)).isEqualTo("EXPIRED");
        assertThat(f.count("message_attempt")).isZero();
        var shortLived = f.pair("REQUESTED");
        var until = f.h.clock.now().plusSeconds(5);
        f.h.jdbc.update("UPDATE wa.pickup_point SET valid_until=? WHERE id=?", java.sql.Timestamp.from(until), shortLived.point());
        UUID bounded = f.peer(shortLived, "REQUESTED");
        var reservation = f.authorize(f.claim(bounded));
        assertThat(java.time.Instant.parse(reservation.path("submitBefore").asText())).isEqualTo(until);
    }

    @Test
    void aLaterRevisionStillCancelsHistoricalRectificationNotification() {
        var pair = f.pair("REQUESTED");
        f.h.jdbc.update(
                "UPDATE wa.pickup_point SET state='REJECTED',valid_until=?,version=version+1 WHERE"
                        + " id=?",
                java.sql.Timestamp.from(f.h.clock.now()),
                pair.point());
        UUID box =
                f.peer(
                        pair,
                        f.command(pair.driver(), "ADVANCE_PASSENGER_SETUP"),
                        pair.driver(),
                        "RECTIFY",
                        pair.point());
        var lease = f.claim(box);
        f.h.jdbc.update(
                "UPDATE wa.driver_passenger_link SET version=version+1 WHERE id=?", pair.link());
        f.canceled(lease);
    }
}
