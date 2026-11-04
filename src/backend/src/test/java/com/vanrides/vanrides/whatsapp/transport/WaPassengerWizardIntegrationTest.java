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
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);

    WaPassengerTestSupport fixture;
    UUID driver;
    String code;

    @BeforeEach
    void setup() {
        fixture = new WaPassengerTestSupport(DATABASE.fixture());
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
        var expired = fixture.send(passenger, "ACEITO");
        assertFalse(expired.text().startsWith("Não entendi"), expired.text());
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
    void unclearAnswersExplainTheExpectedReplyAndNeverAdvanceOrSaveAnything() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        for (String unclear : new String[] {"talvez", "claro que sim", "sim aceito"}) {
            var retry = fixture.send(passenger, unclear);
            assertEquals("PREF_APPROACH", fixture.draft(passenger).path("stage").asText());
            assertTrue(retry.text().startsWith("Não entendi sua resposta. "), retry.text());
            assertTrue(retry.text().endsWith("Responda SIM ou NÃO."), retry.text());
        }
        assertEquals(
                "{}",
                fixture.conversation.string(
                        "SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                        passenger));
        var accepted = fixture.send(passenger, "SIM");
        assertFalse(accepted.text().startsWith("Não entendi"), accepted.text());
        assertEquals("PREF_REMINDER", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "NÃO");
        var routines = fixture.send(passenger, "3");
        assertTrue(routines.text().startsWith("Não entendi sua resposta. "), routines.text());
        assertEquals("ROUTINES", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "1");
        var days = fixture.send(passenger, "segunda e quarta");
        assertTrue(days.text().startsWith("Não entendi sua resposta. "), days.text());
        assertEquals("DAYS", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "1,3,5");
        var address = fixture.send(passenger, "Rua das Flores 123, perto da padaria");
        assertTrue(address.text().startsWith("Não entendi sua resposta. "), address.text());
        assertTrue(address.text().contains("pin estático"), address.text());
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        var wrongMarker = fixture.send(passenger, "CONFIRMAR BCDFGHJK");
        assertTrue(wrongMarker.text().startsWith("Não entendi sua resposta. "), wrongMarker.text());
        assertTrue(wrongMarker.text().contains("CONFIRMAR "), wrongMarker.text());
        assertNotEquals(fixture.marker(review), fixture.marker(wrongMarker));
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void passengerChoosingTheReturnRoutineNeverReceivesTheOutboundOne() {
        fixture.driverFixture.confirm(driver, fixture.driverFixture.review(driver, "VOLTA"));
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        fixture.send(passenger, "SIM");
        var routines = fixture.send(passenger, "NÃO");
        assertTrue(routines.text().contains("Rotina Sintética (ida)"), routines.text());
        var returnChoice =
                java.util.regex.Pattern.compile("(\\d): Rotina Sintética \\(volta\\)")
                        .matcher(routines.text());
        assertTrue(returnChoice.find(), routines.text());
        fixture.send(passenger, returnChoice.group(1));
        fixture.send(passenger, "1,3,5");
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        assertTrue(review.text().contains("(volta)"), review.text());
        assertFalse(review.text().contains("(ida)"), review.text());
        fixture.confirm(passenger, review);
        assertEquals(
                "RETURN",
                fixture.conversation.string(
                        "SELECT r.direction FROM wa.passenger_link_routine s JOIN wa.routine r ON"
                                + " r.id=s.routine_id JOIN wa.driver_passenger_link l ON"
                                + " l.id=s.link_id WHERE l.passenger_id=?",
                        passenger));
    }

    @Test
    void choosingBothRoutinesAsksDaysForEachOneAndNavigationIsNeverFlagged() {
        fixture.driverFixture.confirm(driver, fixture.driverFixture.review(driver, "VOLTA"));
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        fixture.send(passenger, "SIM");
        fixture.send(passenger, "NÃO");
        var firstDays = fixture.send(passenger, "1,2");
        assertTrue(firstDays.text().contains("Rotina Sintética"), firstDays.text());
        var secondDays = fixture.send(passenger, "1,3,5");
        assertFalse(secondDays.text().startsWith("Não entendi"), secondDays.text());
        assertEquals(1, fixture.draft(passenger).path("dayIndex").asInt());
        var back = fixture.send(passenger, "VOLTAR");
        assertFalse(back.text().startsWith("Não entendi"), back.text());
        assertEquals("ROUTINES", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "1,2");
        fixture.send(passenger, "1,3,5");
        fixture.send(passenger, "2,4");
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        assertTrue(review.text().contains("(ida)"), review.text());
        assertTrue(review.text().contains("(volta)"), review.text());
        assertTrue(review.text().contains("[1,3,5]"), review.text());
        assertTrue(review.text().contains("[2,4]"), review.text());
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
