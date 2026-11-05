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

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerIsolationIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_isolation");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);

    WaPassengerTestSupport fixture;
    UUID driver;
    UUID passenger;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(DATABASE.fixture());
        driver = fixture.driver();
        fixture.routine(driver);
        passenger = fixture.passenger();
        code = fixture.invite(driver, 1);
    }

    @Test
    void invitationFromAnotherCohortIsDeniedBeforeAnyPassengerProfileOrDraft() {
        UUID outsider = fixture.conversation.actor();
        long driverMessages =
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?",
                        driver);
        var lease = fixture.conversation.text(outsider, "PASSAGEIRO " + code);
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.profile WHERE identity_id=?", outsider));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.conversation_state WHERE identity_id=?",
                        outsider));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(
                driverMessages,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?",
                        driver));
    }

    @Test
    void invitationFromAnotherInstanceHasNoDurableAdmissionEffect() {
        fixture.transport.jdbc.update(
                "UPDATE wa.integration_inbox SET instance_id='another-instance' WHERE"
                        + " identity_id=?",
                driver);
        fixture.transport.jdbc.update(
                "UPDATE wa.identity_alias SET instance_id='another-instance' WHERE identity_id=?",
                driver);
        var lease = fixture.conversation.text(passenger, "PASSAGEIRO " + code);
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.profile WHERE identity_id=?", passenger));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
    }

    @Test
    void requestRevalidatesCohortEvenWhenTheInitialBindWasBypassedOrBecameStale() {
        var review = fixture.review(passenger, code);
        UUID cohort = UUID.randomUUID();
        fixture.transport.jdbc.update(
                "INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES"
                        + " (?,'transport-test',true)",
                cohort);
        fixture.transport.jdbc.update(
                "UPDATE wa.identity SET test_cohort_id=? WHERE id=?", cohort, passenger);
        var lease = fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(review));
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "REQUEST_LINK", fixture.payload(fixture.context(lease)))
                        .error());
        assertTrue(fixture.draft(passenger).isEmpty());
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(0, fixture.count("passenger_link_routine"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
    }

    @Test
    void requestRevalidatesInstanceEvenAfterAValidInitialBind() {
        var review = fixture.review(passenger, code);
        fixture.transport.jdbc.update(
                "UPDATE wa.integration_inbox SET instance_id='another-instance' WHERE"
                        + " identity_id=?",
                driver);
        fixture.transport.jdbc.update(
                "UPDATE wa.identity_alias SET instance_id='another-instance' WHERE identity_id=?",
                driver);
        var lease = fixture.conversation.text(passenger, "CONFIRMAR " + fixture.marker(review));
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "REQUEST_LINK", fixture.payload(fixture.context(lease)))
                        .error());
        assertTrue(fixture.draft(passenger).isEmpty());
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
    }
}
