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
        assertTrue(review.text().contains("Dias: segunda, quarta e sexta"));
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
        assertNull(
                fixture.conversation.string(
                        "SELECT preferences_json->>'ATTENDANCE_REMINDERS' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
    }

    @Test
    void knownNameAndNoticeAreReusedWithApproachMessagesOffByDefault() {
        UUID passenger = fixture.passenger();
        fixture.transport.jdbc.update(
                "INSERT INTO"
                    + " wa.profile(identity_id,role,status,display_name,notice_version,notice_accepted_at)"
                    + " VALUES (?,'PASSENGER','ACTIVE','Nome já aceito','wa-mvp-v1',?)",
                passenger,
                Timestamp.from(fixture.transport.clock.now()));
        var next = fixture.open(passenger, code);
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertTrue(next.text().contains("ALTERAR DIAS"));
        assertFalse(fixture.draft(passenger).path("approachMessages").asBoolean());
        assertFalse(fixture.draft(passenger).has("attendanceReminders"));
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
        fixture.send(passenger, "ACEITO");
        assertEquals("NAME", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "Nome sintético");
        assertEquals("NOTICE", fixture.draft(passenger).path("stage").asText());
        assertNull(
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
        fixture.transport.time.advance(601);
        var expired = fixture.send(passenger, "ACEITO");
        assertTrue(expired.text().startsWith("O prazo para aceitar o aviso terminou."), expired.text());
        assertTrue(
                expired.text()
                        .contains(
                                "Antes de continuar, um aviso sobre seus dados.\n\nPara organizar o seu"
                                        + " transporte e viagens, o VanRides usa seu nome, seu número de"
                                        + " WhatsApp, esta conversa, seu ponto de embarque e as viagens que"
                                        + " você escolher. Depois disso, você escolhe quais avisos quer"
                                        + " receber.\n\nSe você concorda, responda ACEITO para continuar."),
                expired.text());
        assertFalse(expired.text().contains("wa-mvp-v1"), expired.text());
        assertNull(
                fixture.conversation.string(
                        "SELECT notice_version FROM wa.profile WHERE identity_id=?", passenger));
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
        var corrected = fixture.send(passenger, "REFERÊNCIA Portão lateral");
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

    @Test
    void nativePickupPinOpensTheReviewAndIsStoredOnConfirmation() {
        UUID passenger = fixture.passenger();
        fixture.review(passenger, code);
        var prompt = fixture.send(passenger, "CORRIGIR PONTO");
        assertTrue(prompt.text().endsWith("escolha Localização e envie o ponto onde a van deve buscar você."), prompt.text());
        assertFalse(prompt.text().contains("PONTO -29.68123, -51.13123"), prompt.text());

        var point = fixture.pin(passenger, -12.25, 34.5);
        assertEquals(
                json().put("type", "STATIC_PIN_REFERENCE")
                        .put("sourceMessageId", "event_" + point.lease().id()),
                point.lease().message());
        assertEquals("REVIEW", fixture.draft(passenger).path("stage").asText());
        var review = fixture.send(passenger, "REFERÊNCIA Portão lateral");
        assertTrue(review.text().contains("Portão lateral"));
        assertEquals(0, fixture.count("pickup_point"));

        fixture.confirm(passenger, review);
        assertEquals(
                1,
                fixture.conversation.number(
                        """
                        SELECT count(*) FROM wa.pickup_point WHERE passenger_id=? AND latitude=-12.25
                        AND longitude=34.5 AND source_inbox_event_id=? AND reference_text='Portão lateral'
                        """,
                        passenger,
                        point.lease().id()));
    }

    @Test
    void typedPointIsRefusedForPassengersAndARealLocationIsStillAccepted() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "Nome sintético");
        var prompt = fixture.send(passenger, "ACEITO");
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertFalse(prompt.text().contains("PONTO -"), prompt.text());
        assertFalse(prompt.text().contains("google.com"), prompt.text());

        var typed = fixture.send(passenger, "PONTO -12.25, +34.5");
        assertTrue(typed.text().startsWith("Não consegui usar esse ponto. Envie a localização pelo WhatsApp do celular."), typed.text());
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertFalse(fixture.draft(passenger).path("selections").path(0).has("pickup"));
        assertFalse(fixture.draft(passenger).has("sourceEvent"));
        assertEquals(0, fixture.count("pickup_point"));

        var review = fixture.pin(passenger, -12.25, 34.5);
        assertEquals("REVIEW", fixture.draft(passenger).path("stage").asText());
        assertFalse(review.text().startsWith("Não consegui usar esse ponto. Envie a localização pelo WhatsApp do celular."), review.text());
    }

    @Test
    void daysQuestionAndRequestConfirmationUseTheSignupWording() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "Nome sintético");
        fixture.send(passenger, "ACEITO");
        var days = fixture.send(passenger, "ALTERAR DIAS");
        assertTrue(days.text().contains("Em quais dias você irá usar o transporte?"), days.text());
        fixture.send(passenger, "todos os dias");
        var review = fixture.pin(passenger, -12.25, 34.5);
        var request = fixture.confirm(passenger, review);
        assertTrue(request.text().contains("Cadastro solicitado! O pedido foi enviado ao motorista para validação."),
                request.text());
    }

    @Test
    void confirmingWithoutAnyAvisosCommandKeepsApproachMessagesOffAndNeverWritesAttendanceReminders() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "Nome sintético");
        fixture.send(passenger, "ACEITO");
        var review = fixture.pin(passenger, -12.25, 34.5);
        assertTrue(review.text().contains("Avisos de chegada da van: não"), review.text());
        fixture.confirm(passenger, review);
        assertNull(
                fixture.conversation.string(
                        "SELECT preferences_json->>'ATTENDANCE_REMINDERS' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
        assertEquals(
                "false",
                fixture.conversation.string(
                        "SELECT preferences_json->>'APPROACH_MESSAGES' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
    }

    @Test
    void signupReviewOffersOnlyConfirmAndAdjust() {
        UUID passenger = fixture.passenger();
        var review = fixture.review(passenger, code);
        assertTrue(review.text().contains("envie AJUSTAR"), review.text());
        for (String removed : new String[] {"ALTERAR DIAS", "CORRIGIR PONTO", "REFERÊNCIA"}) {
            assertFalse(review.text().contains(removed), review.text());
        }
        assertTrue(review.text().contains("AVISOS"), review.text());
    }

    @Test
    void signupReviewAlwaysStatesTheReminderAndOffersOnlyTheArrivalNoticeChoice() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "Nome sintético");
        fixture.send(passenger, "ACEITO");
        var off = fixture.pin(passenger, -12.25, 34.5);
        assertTrue(off.text().contains("Avisos de chegada da van: não\n"
                + "Você recebe um lembrete antes de cada viagem para informar se vai."), off.text());
        assertTrue(off.text().contains(
                "Para receber um aviso quando a van estiver chegando, envie AVISOS CHEGADA."), off.text());
        assertFalse(off.text().contains("AVISOS NENHUM"), off.text());
        var on = fixture.send(passenger, "AVISOS CHEGADA");
        assertTrue(on.text().contains("Avisos de chegada da van: sim\n"
                + "Você recebe um lembrete antes de cada viagem para informar se vai."), on.text());
        assertTrue(on.text().contains("Para não receber, envie AVISOS NENHUM."), on.text());
        assertFalse(on.text().contains("envie AVISOS CHEGADA"), on.text());
        for (String text : new String[] {off.text(), on.text()}) {
            for (String removed : new String[] {"AVISOS PRESENÇA", "AVISOS TODOS", "Lembretes de presença:"}) {
                assertFalse(text.contains(removed), text);
            }
        }
        for (String removed : new String[] {"AVISOS PRESENÇA", "AVISOS TODOS"}) {
            fixture.send(passenger, removed);
            assertTrue(fixture.draft(passenger).path("approachMessages").asBoolean());
            assertFalse(fixture.draft(passenger).has("attendanceReminders"));
        }
        var back = fixture.send(passenger, "AVISOS NENHUM");
        assertTrue(back.text().contains("Avisos de chegada da van: não"), back.text());
        var confirmed = fixture.send(passenger, "AVISOS CHEGADA");
        fixture.confirm(passenger, confirmed);
        assertEquals(
                "true",
                fixture.conversation.string(
                        "SELECT preferences_json->>'APPROACH_MESSAGES' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
        assertNull(
                fixture.conversation.string(
                        "SELECT preferences_json->>'ATTENDANCE_REMINDERS' FROM wa.profile WHERE"
                                + " identity_id=?",
                        passenger));
    }

    @Test
    void adjustAtSignupReviewRestartsFromTheNameAndKeepsTheSignupGoing() {
        UUID passenger = fixture.passenger();
        fixture.review(passenger, code);
        var restarted = fixture.send(passenger, "AJUSTAR");
        var draft = fixture.draft(passenger);
        assertEquals("NAME", draft.path("stage").asText());
        assertTrue(draft.path("selections").isMissingNode() || draft.path("selections").isEmpty());
        assertFalse(draft.has("confirmation"));
        assertFalse(restarted.text().isBlank());
        assertEquals(0, fixture.count("driver_passenger_link"));

        fixture.send(passenger, "Nome Corrigido");
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertEquals(
                "Nome Corrigido",
                fixture.conversation.string(
                        "SELECT display_name FROM wa.profile WHERE identity_id=?", passenger));
        fixture.pin(passenger, -12.25, 34.5);
        assertEquals("REVIEW", fixture.draft(passenger).path("stage").asText());
    }

    private record ObjectNodeHolder(String value) {}

    @Test
    void switchingFromPassengerMenuToDriverInviteDiscardsTheIncompatibleDraft() {
        fixture.send(driver, "PASSAGEIROS");
        fixture.driverFixture.send(driver, "CONVIDAR PERSONALIZADO");
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
    void passengerChoosingTheReturnRoutineNeverReceivesTheOutboundOne() {
        fixture.driverFixture.confirm(driver, fixture.driverFixture.review(driver, "VOLTA"));
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        var routines = fixture.send(passenger, "ACEITO");
        assertTrue(routines.text().contains("Rotina Sintética\n→ Ida"), routines.text());
        var returnChoice =
                java.util.regex.Pattern.compile("(\\d): Rotina Sintética\\n← Volta")
                        .matcher(routines.text());
        assertTrue(returnChoice.find(), routines.text());
        fixture.send(passenger, returnChoice.group(1));
        fixture.send(passenger, "1,3,5");
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        assertTrue(review.text().contains("← Volta"), review.text());
        assertFalse(review.text().contains("→ Ida"), review.text());
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
        fixture.send(passenger, "terça e quinta");
        fixture.pin(passenger);
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        assertTrue(review.text().contains("→ Ida às 06:00\nDias: segunda, quarta e sexta"), review.text());
        assertTrue(review.text().contains("← Volta às 06:00\nDias: terça e quinta"), review.text());
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
