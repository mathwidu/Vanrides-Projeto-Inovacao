package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.assertEquals;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.team.WaTeamTestDriverStore;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaTeamTestDriverPassengerIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_team_test_driver_passenger");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private static final long GITHUB = 314;

    WaPassengerTestSupport fixture;
    WaTeamTestDriverStore store;
    UUID passenger;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(DATABASE.fixture());
        store = new WaTeamTestDriverStore(fixture.transport.jdbc);
        passenger = fixture.passenger();
    }

    @Test
    void passengerWizardReachesTheTestDriverWithoutAnyMessageToIt() {
        UUID driver = testDriver(WaTransportTestSupport.INSTANCE);
        String code = teamInvite(driver);
        var review = fixture.review(passenger, code);
        var request = fixture.confirm(passenger, review);
        assertEquals("REQUEST_LINK", request.body().path("commandType").asText());
        assertEquals(1, request.response().path("responseIds").size());
        assertEquals(
                "REQUESTED",
                fixture.conversation.string(
                        "SELECT state FROM wa.driver_passenger_link WHERE driver_id=? AND"
                                + " passenger_id=?",
                        driver,
                        passenger));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.pickup_point WHERE driver_id=? AND"
                                + " state='PROPOSED'",
                        driver));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.message_outbox WHERE recipient_identity_id=?",
                        driver));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.integration_inbox WHERE identity_id=?", driver));
    }

    @Test
    void anOrdinaryDriverWithoutItsOwnWhatsappMessagesStaysOutOfReach() {
        UUID ordinary = fixture.driver();
        String code = fixture.invite(ordinary, 1);
        var lease = fixture.conversation.text(passenger, "PASSAGEIRO " + code);
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.conversation_state WHERE identity_id=?",
                        passenger));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", ordinary));
    }

    @Test
    void aPassengerOnAnotherEvolutionInstanceCannotUseATeamTestDriverInvite() {
        UUID driver = testDriver("another-instance");
        String code = teamInvite(driver);
        var lease = fixture.conversation.text(passenger, "PASSAGEIRO " + code);
        assertEquals(
                WaProblem.Code.ACTION_NOT_ALLOWED,
                fixture.decide(lease, "ADVANCE_PASSENGER_SETUP", json()).error());
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.conversation_state WHERE identity_id=?",
                        passenger));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(0, fixture.count("pickup_point"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
    }

    private UUID testDriver(String instance) {
        return fixture.transport.transaction.execute(
                status ->
                        store.ensure(
                                fixture.cohort, instance, GITHUB, fixture.transport.clock.now()));
    }

    private String teamInvite(UUID driver) {
        String code = WaCrypto.randomToken();
        store.insertInvite(
                driver,
                UUID.randomUUID(),
                fixture.transport.crypto.secretHash("passenger-invite", code),
                fixture.transport.clock.now().plusSeconds(86400),
                GITHUB,
                fixture.transport.clock.now());
        return code;
    }
}
