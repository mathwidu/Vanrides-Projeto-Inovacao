package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.*;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.util.UUID;

@Testcontainers(disabledWithoutDocker = true)
class WaPassengerInputIntegrationTest {
    @Container
    static final PostgreSQLContainer<?> POSTGRES =
            WaTransportTestSupport.postgres("wa_passenger_input");

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
    void noticeStaysAcceptableForTenMinutesAndAnyLaterMessageRenewsItWithoutNewInvite() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.transport.time.advance(599);
        var accepted = fixture.send(passenger, "aceito!");
        assertEquals("PREF_APPROACH", fixture.draft(passenger).path("stage").asText());
        assertFalse(accepted.text().startsWith("O prazo"), accepted.text());

        UUID late = fixture.passenger();
        fixture.open(late, fixture.invite(driver, 10));
        fixture.send(late, "CONTINUAR");
        fixture.send(late, "Outro Passageiro");
        fixture.transport.time.advance(601);
        var renewed = fixture.send(late, "oi");
        assertTrue(renewed.text().startsWith("O prazo para aceitar o aviso terminou."), renewed.text());
        assertEquals("NOTICE", fixture.draft(late).path("stage").asText());
        fixture.send(late, "Aceito");
        assertEquals("PREF_APPROACH", fixture.draft(late).path("stage").asText());
    }

    @Test
    void firstMessageAsksYesOrNoExplainsNavigationAndToleratesSloppyAnswers() {
        UUID passenger = fixture.passenger();
        var opened = fixture.open(passenger, code);
        assertTrue(opened.text().contains("Você quer se cadastrar com este motorista?"), opened.text());
        assertTrue(opened.text().contains("Responda Sim ou Não."), opened.text());
        assertTrue(opened.text().contains("VOLTAR"), opened.text());
        assertTrue(opened.text().contains("ABANDONAR"), opened.text());
        var unclear = fixture.send(passenger, "talvez");
        assertTrue(unclear.text().startsWith("Não entendi sua resposta.\n\n"), unclear.text());
        assertEquals("DRIVER_CONFIRM", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "  SÍM!!  ");
        assertEquals("NAME", fixture.draft(passenger).path("stage").asText());
    }

    @Test
    void answeringNoToTheDriverInviteCancelsTheRegistration() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        var refused = fixture.send(passenger, "nao");
        assertTrue(fixture.draft(passenger).isEmpty());
        assertFalse(refused.text().startsWith("Não entendi"), refused.text());
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void dayQuestionListsNamedDaysAndAcceptsNamesRangesAndAbbreviations() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        fixture.send(passenger, "SIM");
        fixture.send(passenger, "NÃO");
        var question = fixture.send(passenger, "1");
        assertTrue(question.text().contains("• Segunda\n• Terça\n• Quarta\n• Quinta\n• Sexta"), question.text());
        assertTrue(question.text().contains("Segunda a sexta"), question.text());
        assertFalse(question.text().contains("["), question.text());
        fixture.send(passenger, "Seg, QUA e sex-feira");
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        assertEquals("[1,3,5]", fixture.draft(passenger).path("selections").path(0).path("selectedDays").toString());
    }

    @Test
    void passengerConfirmsWithTheWordAloneEvenWithCaseAndPunctuation() {
        UUID passenger = fixture.passenger();
        var review = fixture.review(passenger, code);
        assertTrue(review.text().contains("responda CONFIRMAR."), review.text());
        assertFalse(
                java.util.regex.Pattern.compile("CONFIRMAR [A-Z]{8}").matcher(review.text()).find(),
                review.text());
        assertEquals(0, fixture.count("driver_passenger_link"));
        fixture.deliverSummary(passenger);
        fixture.send(passenger, "  confirmar!! 👍 ");
        assertEquals(1, fixture.count("driver_passenger_link"));
        assertTrue(fixture.draft(passenger).isEmpty());
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
            assertTrue(retry.text().startsWith("Não entendi sua resposta.\n\n"), retry.text());
            assertTrue(retry.text().contains("Responda Sim ou Não."), retry.text());
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
        assertTrue(routines.text().startsWith("Não entendi sua resposta.\n\n"), routines.text());
        assertEquals("ROUTINES", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "1");
        var days = fixture.send(passenger, "dia útil");
        assertTrue(days.text().startsWith("Não entendi sua resposta.\n\n"), days.text());
        assertEquals("DAYS", fixture.draft(passenger).path("stage").asText());
        fixture.send(passenger, "Segunda, Quarta e Sexta");
        var address = fixture.send(passenger, "Rua das Flores 123, perto da padaria");
        assertTrue(address.text().startsWith("Não entendi sua resposta.\n\n"), address.text());
        assertTrue(address.text().contains("pin estático"), address.text());
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
        fixture.pin(passenger);
        var review = fixture.send(passenger, "PULAR");
        var extra = fixture.send(passenger, "CONFIRMAR BCDFGHJK");
        assertTrue(extra.text().startsWith("Não entendi sua resposta.\n\n"), extra.text());
        assertTrue(extra.text().contains("responda CONFIRMAR."), extra.text());
        assertEquals("CONFIRMAR", fixture.confirmationText(review));
        assertEquals(0, fixture.count("driver_passenger_link"));
    }
}
