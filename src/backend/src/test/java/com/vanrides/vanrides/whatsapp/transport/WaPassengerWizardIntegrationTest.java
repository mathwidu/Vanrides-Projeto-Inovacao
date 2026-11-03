package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerWizardIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_wizard");

    WaPassengerTestSupport fixture;
    UUID driver;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(POSTGRES);
        driver = fixture.driver();
        fixture.routine(driver);
        code = fixture.invite(driver, 10);
    }

    @Test
    void firstInviteShowsDriverWithoutConsumptionOrPrematureConsent() {
        UUID passenger = fixture.passenger();
        var opened = fixture.open(passenger, code);
        assertTrue(opened.text().contains("Motorista sintético"));
        assertEquals("UNREGISTERED", opened.context().path("actorState").asText());
        assertEquals("ADVANCE_PASSENGER_SETUP", fixture.command(opened.context()));
        assertTrue(opened.context().path("options").isEmpty());
        assertFalse(opened.context().toString().contains(code));
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(
                0,
                fixture.conversation.number(
                        "SELECT use_count FROM wa.driver_invite WHERE driver_id=?", driver));
        assertNull(
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
    }

    @Test
    void completeWizardPersistsOnlyAfterFinalBoundConfirmationAndChoicesAreExplicit() {
        UUID passenger = fixture.passenger();
        var review = fixture.review(passenger, code);
        assertEquals(0, fixture.count("driver_passenger_link"));
        assertEquals(
                "{}",
                fixture.conversation.string(
                        "SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                        passenger));
        assertTrue(review.text().contains("dias [1,3,5]"));
        var request = fixture.confirm(passenger, review);
        assertEquals("REQUEST_LINK", request.body().path("commandType").asText());
        assertEquals(2, request.response().path("responseIds").size());
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertEquals(
                1,
                fixture.conversation.number(
                        "SELECT count(*) FROM wa.passenger_link_routine WHERE"
                                + " weekdays=ARRAY[1,3,5]::smallint[]"));
        assertEquals(
                "true",
                fixture.conversation.string(
                        "SELECT preferences_json->>'APPROACH_MESSAGES' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
        assertEquals(
                "false",
                fixture.conversation.string(
                        "SELECT preferences_json->>'ATTENDANCE_REMINDERS' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
    }

    @Test
    void knownNameAndNoticeAreReusedWithoutGrantingNotificationPreferences() {
        UUID passenger = fixture.passenger();
        fixture.transport.jdbc.update(
                "INSERT INTO"
                    + " wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at)"
                    + " VALUES (?,'PASSENGER','ACTIVE','Nome já aceito','wa-mvp-v1',?)",
                passenger,
                Timestamp.from(fixture.transport.clock.now()));
        fixture.open(passenger, code);
        var next = fixture.send(passenger, "CONTINUAR");
        assertEquals("PREF_APPROACH", fixture.draft(passenger).path("stage").asText());
        assertTrue(next.text().contains("aproximando"));
        assertEquals("Nome já aceito", fixture.draft(passenger).path("passengerName").asText());
        assertEquals(
                "{}",
                fixture.conversation.string(
                        "SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                        passenger));
    }

    @Test
    void helpAndRestartPreservePrivatePassengerDraft() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        ObjectNodeHolder before = new ObjectNodeHolder(fixture.draft(passenger).toString());
        var help = fixture.conversation.text(passenger, "AJUDA");
        fixture.decide(help, "RESPOND_TO_MESSAGE", json().put("responseCode", "HELP"))
                .requireSuccess();
        fixture.conversation.finish(help);
        assertEquals(before.value(), fixture.draft(passenger).toString());
        var lease = fixture.conversation.text(passenger, "Nome persistido");
        fixture.driverFixture
                .restartedCommands()
                .execute(
                        fixture.conversation.worker,
                        lease.token(),
                        "synthetic-restart-090",
                        fixture.conversation.body(lease, "ADVANCE_PASSENGER_SETUP", json()),
                        "synthetic-restart")
                .requireSuccess();
        assertEquals("NOTICE", fixture.draft(passenger).path("stage").asText());
    }

    @Test
    void noticeMustBeIssuedBeforeAcceptanceAndWithinItsOwnContext() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "ACEITO");
        assertEquals("NOTICE", fixture.draft(passenger).path("stage").asText());
        assertNull(
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
        fixture.transport.time.advance(301);
        fixture.send(passenger, "ACEITO");
        assertNull(
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "ACEITO");
        assertEquals(
                "wa-mvp-v1",
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
    }

    @Test
    void initialPointCanBeCorrectedBeforeAnyDurableLinkExists() {
        UUID passenger = fixture.passenger();
        fixture.review(passenger, code);
        fixture.send(passenger, "CORRIGIR PONTO");
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertFalse(fixture.draft(passenger).has("sourceEvent"));
        fixture.pin(passenger);
        fixture.send(passenger, "Esse pin está errado, o embarque é em outro lugar");
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertFalse(fixture.draft(passenger).has("sourceEvent"));
        assertFalse(fixture.draft(passenger).has("confirmation"));
        fixture.pin(passenger);
        var corrected = fixture.send(passenger, "Portão lateral");
        assertTrue(corrected.text().contains("Portão lateral"));
        assertEquals("REVIEW", fixture.draft(passenger).path("stage").asText());
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void liveLocationAndAmbiguousTextNeverBecomeStoredPickupCoordinates() {
        UUID passenger = fixture.passenger();
        fixture.review(passenger, code);
        fixture.send(passenger, "CORRIGIR PONTO");
        fixture.send(passenger, "Meu endereço fica perto da escola");
        UUID live =
                fixture.conversation.pending(
                        passenger,
                        json().set(
                                        "liveLocationMessage",
                                        json().put("degreesLatitude", 0)
                                                .put("degreesLongitude", 0)),
                        fixture.transport.clock.now());
        var liveLease = fixture.conversation.claim(live);
        var before = fixture.draft(passenger).deepCopy();
        fixture.decide(
                        liveLease,
                        "RESPOND_TO_MESSAGE",
                        json().put("responseCode", "STATIC_PIN_REQUIRED"))
                .requireSuccess();
        fixture.conversation.finish(liveLease);
        assertEquals(before, fixture.draft(passenger));
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertFalse(fixture.draft(passenger).has("sourceEvent"));
        assertEquals(0, fixture.count("pickup_point"));
    }

    private record ObjectNodeHolder(String value) {}

    @Test
    void switchingFromPassengerMenuToDriverInviteDiscardsTheIncompatibleDraft() {
        fixture.send(driver, "PASSAGEIROS");
        fixture.driverFixture.send(driver, "CONVIDAR");
        fixture.driverFixture.send(driver, "PADRÃO");
        assertFalse(
                fixture.conversation
                        .string(
                                "SELECT context_json::text FROM wa.conversation_state WHERE"
                                        + " identity_id=?",
                                driver)
                        .contains("passengerDraft"));
        assertEquals("REVIEW", fixture.driverFixture.draft(driver).path("stage").asText());
    }

    @Test
    void switchingBothWaysStartsTheSelectedWizardWithoutAbandon() {
        fixture.driverFixture.send(driver, "CRIAR");
        fixture.send(driver, "PASSAGEIROS");
        assertFalse(
                fixture.conversation
                        .string(
                                "SELECT context_json::text FROM wa.conversation_state WHERE"
                                        + " identity_id=?",
                                driver)
                        .contains("driverDraft"));
        fixture.driverFixture.send(driver, "ROTINAS");
        fixture.driverFixture.send(driver, "CRIAR");
        fixture.driverFixture.send(driver, "Nova rotina após troca");
        assertEquals("DIRECTION", fixture.driverFixture.draft(driver).path("stage").asText());
        assertFalse(
                fixture.conversation
                        .string(
                                "SELECT context_json::text FROM wa.conversation_state WHERE"
                                        + " identity_id=?",
                                driver)
                        .contains("passengerDraft"));
    }
}
