package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerFencingIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_fencing");

    WaPassengerTestSupport fixture;
    UUID driver;
    UUID passenger;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(POSTGRES);
        driver = fixture.driver();
        fixture.routine(driver);
        passenger = fixture.passenger();
        code = fixture.invite(driver, 1);
    }

    @Test
    void stolenLinkAndPickupReferencesCannotApproveAnotherDriversPassenger() {
        UUID link = fixture.request(passenger, code);
        var review = fixture.driverReview(driver, "APROVAR");
        var confirmation = fixture.conversation.text(driver, "CONFIRMAR " + fixture.marker(review));
        var context = fixture.context(confirmation);
        UUID intruder = fixture.driver();
        var attack = fixture.conversation.text(intruder, "APROVAR 1");
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(attack, "APPROVE_LINK", fixture.payload(context)).error());
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(0, fixture.count("routine_member"));
    }

    @Test
    void payloadMustExactlyMatchTheOfferedReferences() {
        UUID link = fixture.request(passenger, code);
        var review = fixture.driverReview(driver, "APROVAR");
        var lease = fixture.conversation.text(driver, "CONFIRMAR " + fixture.marker(review));
        var payload = fixture.payload(fixture.context(lease));
        payload.put("pickupRef", "pickup_" + UUID.randomUUID().toString().replace("-", ""));
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                assertThrows(WaProblem.class, () -> fixture.decide(lease, "APPROVE_LINK", payload))
                        .code());
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?",
                        lease.id()));
    }

    @Test
    void expiredAndSupersededHumanMarkersCannotCreateARequest() {
        var original = fixture.review(passenger, code);
        var current = fixture.send(passenger, "SIM");
        assertNotEquals(fixture.marker(original), fixture.marker(current));
        fixture.send(passenger, "CONFIRMAR " + fixture.marker(original));
        assertEquals(0, fixture.count("driver_passenger_link"));
        fixture.transport.time.advance(301);
        var stale = fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(current));
        assertEquals("ADVANCE_PASSENGER_SETUP", fixture.command(fixture.context(stale)));
        fixture.execute(stale, true);
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void summaryIsInvalidatedWhenRoutineOrProposedPointChanges() {
        UUID link = fixture.request(passenger, code);
        var review = fixture.driverReview(driver, "APROVAR");
        fixture.transport.jdbc.update(
                "UPDATE wa.pickup_point SET reference_text='Nova referência',version=version+1"
                        + " WHERE link_id=?",
                link);
        var lease = fixture.conversation.text(driver, "CONFIRMAR " + fixture.marker(review));
        assertEquals(
                WaProblem.Code.VERSION_CONFLICT,
                fixture.decide(lease, "APPROVE_LINK", fixture.payload(fixture.context(lease)))
                        .error());
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(0, fixture.count("routine_member"));
    }

    @Test
    void supportPreservesDraftButRemovesItsConfirmationAndPausesOffers() {
        fixture.review(passenger, code);
        var support = fixture.conversation.text(passenger, "FALAR COM SUPORTE");
        fixture.decide(
                        support,
                        "REQUEST_SUPPORT",
                        json().put("reason", "HELP").put("detail", "Pedido explícito"))
                .requireSuccess();
        assertEquals("REVIEW", fixture.draft(passenger).path("stage").asText());
        assertFalse(fixture.draft(passenger).has("confirmation"));
        assertTrue(fixture.context(support).path("allowedCommands").isEmpty());
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void replayAfterServiceRestartKeepsOneUseOneRequestAndExactlyTheSameResponseIds() {
        var review = fixture.review(passenger, code);
        var lease = fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(review));
        var result = fixture.execute(lease, false);
        var replay =
                fixture.driverFixture
                        .restartedCommands()
                        .execute(
                                fixture.conversation.worker,
                                lease.token(),
                                "synthetic-090-" + lease.id(),
                                result.body(),
                                "synthetic-replay")
                        .requireSuccess();
        assertEquals(result.response(), replay);
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals(1, fixture.count("pickup_point"));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(
                2,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_outbox o JOIN wa.command_receipt r ON"
                                + " r.command_id=o.command_id WHERE r.inbox_event_id=?",
                        lease.id()));
    }

    @Test
    void lastInviteUseIsReservedOnceAcrossConcurrentPassengers() throws Exception {
        UUID other = fixture.passenger();
        var firstReview = fixture.review(passenger, code);
        var secondReview = fixture.review(other, code);
        var first =
                fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(firstReview));
        var second = fixture.conversation.text(other, "CONFIRMAR " + fixture.marker(secondReview));
        var firstBody =
                fixture.conversation.body(
                        first, "REQUEST_LINK", fixture.payload(fixture.context(first)));
        var secondBody =
                fixture.conversation.body(
                        second, "REQUEST_LINK", fixture.payload(fixture.context(second)));
        CountDownLatch start = new CountDownLatch(1);
        var executor = Executors.newFixedThreadPool(2);
        try {
            var one =
                    executor.submit(
                            () -> {
                                start.await();
                                return fixture.conversation.execute(
                                        first, firstBody, "synthetic-first-last-use");
                            });
            var two =
                    executor.submit(
                            () -> {
                                start.await();
                                return fixture.conversation.execute(
                                        second, secondBody, "synthetic-second-last-use");
                            });
            start.countDown();
            var a = one.get(30, TimeUnit.SECONDS);
            var b = two.get(30, TimeUnit.SECONDS);
            assertNotEquals(a.error() == null, b.error() == null);
        } finally {
            executor.shutdownNow();
            assertTrue(executor.awaitTermination(10, TimeUnit.SECONDS));
        }
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals(1, fixture.count("pickup_point"));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(
                2,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id IN (?,?)",
                        first.id(),
                        second.id()));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id IN (?,?) AND"
                                + " jsonb_exists(result_json,'errorCode')",
                        first.id(),
                        second.id()));
    }

    @Test
    void passengerWithActiveDriverCannotOpenAnotherInviteOrAcquireSecondActiveLink() {
        UUID link = fixture.request(passenger, code);
        fixture.approve(driver);
        UUID otherDriver = fixture.driver();
        fixture.routine(otherDriver);
        String otherCode = fixture.invite(otherDriver, 1);
        var lease = fixture.conversation.text(passenger, "PASSAGEIRO " + otherCode);
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.driver_passenger_link WHERE passenger_id=? AND"
                                + " state='APPROVED'",
                        passenger));
        assertEquals(
                "APPROVED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE id=?", link));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", otherDriver));
    }

    @Test
    void counterpartResponseFailureRollsBackReceiptInviteUseLinkPointSelectionsAndActorResponse() {
        var review = fixture.review(passenger, code);
        long profileVersion =
                fixture.conversation.number(
                        "SELECT version FROM wa.profile WHERE identity_id=?", passenger);
        long responses = fixture.count("message_outbox");
        fixture.transport.jdbc.execute(
                """
                CREATE FUNCTION wa.reject_synthetic_passenger_peer() RETURNS trigger LANGUAGE plpgsql AS $$
                BEGIN
                  IF NEW.event_kind='REQUESTED' THEN RAISE EXCEPTION 'SYNTHETIC_PEER_FAILURE'; END IF;
                  RETURN NEW;
                END $$
                """);
        fixture.transport.jdbc.execute(
                "CREATE TRIGGER reject_synthetic_passenger_peer BEFORE INSERT ON"
                        + " wa.passenger_response_route FOR EACH ROW EXECUTE FUNCTION"
                        + " wa.reject_synthetic_passenger_peer()");
        var lease = fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(review));
        var context = fixture.context(lease);
        assertThrows(
                RuntimeException.class,
                () -> fixture.decide(lease, "REQUEST_LINK", fixture.payload(context)));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(0, fixture.count("passenger_link_routine"));
        assertEquals(0, fixture.count("routine_member"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?",
                        lease.id()));
        assertEquals(
                profileVersion,
                fixture.conversation.number(
                        "SELECT version FROM wa.profile WHERE identity_id=?", passenger));
        assertEquals(responses, fixture.count("message_outbox"));
        assertTrue(fixture.draft(passenger).has("confirmation"));
    }
}
