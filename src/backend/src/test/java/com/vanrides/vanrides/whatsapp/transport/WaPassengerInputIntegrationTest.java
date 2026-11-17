package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaPassengerTestSupport.json;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import java.sql.Timestamp;
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
        assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
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
        assertEquals("POINT", fixture.draft(late).path("stage").asText());
    }

    @Test
    void firstMessageNamesTheDriverAndDoesNotRegisterAReplyOfYesAsTheName() {
        UUID passenger = fixture.passenger();
        var opened = fixture.open(passenger, code);
        assertTrue(opened.text().contains("Motorista sintético"), opened.text());
        assertTrue(opened.text().contains("ABANDONAR"), opened.text());
        var unclear = fixture.send(passenger, "  SÍM!!  ");
        assertTrue(unclear.text().startsWith("Não entendi sua resposta."), unclear.text());
        assertEquals("NAME", fixture.draft(passenger).path("stage").asText());
        assertNull(fixture.conversation.string("SELECT display_name FROM wa.profile WHERE identity_id=?", passenger));
        fixture.send(passenger, "Nome sintético");
        assertEquals("NOTICE", fixture.draft(passenger).path("stage").asText());
    }

    @Test
    void abandoningTheInviteCancelsTheRegistration() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "ABANDONAR");
        assertTrue(fixture.draft(passenger).isEmpty());
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void dayQuestionListsNamedDaysAndAcceptsNamesRangesAndAbbreviations() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "CONTINUAR");
        fixture.send(passenger, "Passageiro Sintético");
        fixture.send(passenger, "ACEITO");
        var question = fixture.send(passenger, "ALTERAR DIAS");
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
        assertTrue(review.text().contains("envie CONFIRMAR em"), review.text());
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
            assertEquals("POINT", fixture.draft(passenger).path("stage").asText());
            assertTrue(retry.text().startsWith("Não entendi sua resposta.\n\n"), retry.text());
            assertTrue(retry.text().contains("ponto onde a van deve buscar"), retry.text());
        }
        assertEquals(
                "{}",
                fixture.conversation.string(
                        "SELECT preferences_json::text FROM wa.profile WHERE identity_id=?",
                        passenger));
        fixture.send(passenger, "ALTERAR DIAS");
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
        assertTrue(extra.text().contains("envie CONFIRMAR em"), extra.text());
        assertEquals("CONFIRMAR", fixture.confirmationText(review));
        assertEquals(0, fixture.count("driver_passenger_link"));
    }

    @Test
    void oneRoutineUsesItsSuggestedDaysWithoutOptInOrExtraReplies() {
        UUID passenger = fixture.passenger();
        fixture.open(passenger, code);
        fixture.send(passenger, "Nome sintético");
        var pointPrompt = fixture.send(passenger, "ACEITO");
        assertTrue(pointPrompt.text().contains("ALTERAR DIAS"), pointPrompt.text());
        assertEquals("[1,2,3,4,5]", fixture.draft(passenger).path("selections").path(0).path("selectedDays").toString());
        var review = fixture.pin(passenger);
        assertTrue(review.text().contains("envie CONFIRMAR em"), review.text());
        assertEquals(0, fixture.count("driver_passenger_link"));
        fixture.confirm(passenger, review);
        assertEquals("false", fixture.conversation.string(
                "SELECT preferences_json->>'APPROACH_MESSAGES' FROM wa.profile WHERE identity_id=?", passenger));
        assertEquals("false", fixture.conversation.string(
                "SELECT preferences_json->>'ATTENDANCE_REMINDERS' FROM wa.profile WHERE identity_id=?", passenger));
    }

    @Test
    void replacingAPinClearsItsOldReferenceButAdjustingDaysKeepsIt() {
        UUID passenger = fixture.passenger();
        fixture.review(passenger, code);
        fixture.send(passenger, "REFERÊNCIA Entrada antiga");
        fixture.send(passenger, "ALTERAR DIAS");
        var updatedDays = fixture.send(passenger, "terça");
        assertTrue(updatedDays.text().contains("Entrada antiga"), updatedDays.text());
        fixture.send(passenger, "VOLTAR");
        var updatedPoint = fixture.send(passenger, "PONTO -12.25, +34.5");
        assertFalse(updatedPoint.text().contains("Entrada antiga"), updatedPoint.text());
        assertFalse(fixture.draft(passenger).path("selections").path(0).path("pickup").has("reference"));
    }

    @Test
    void approvingWithAQuotedReplyRequiresTheCurrentReviewOfTheDriver() {
        UUID passenger = fixture.passenger();
        fixture.request(passenger, code);
        var firstReview = fixture.send(driver, "REVISAR PEDIDO");
        assertTrue(firstReview.text().contains("APROVAR PONTO"), firstReview.text());
        String oldSummary = submitReview(firstReview);
        var review = fixture.send(driver, "APROVAR PONTO");
        assertEquals("ADVANCE_PASSENGER_SETUP", review.body().path("commandType").asText());
        String latestSummary = submitReview(review);
        assertTrue(quotedApproval(oldSummary).text().contains("não corresponde à etapa atual"));
        assertEquals(0, fixture.count("routine_member"));
        assertEquals("APPROVE_PICKUP", quotedApproval(latestSummary).body().path("commandType").asText());
        assertEquals(1, fixture.count("routine_member"));
    }

    private WaPassengerTestSupport.Step quotedApproval(String external) {
        var message = json();
        message.putObject("extendedTextMessage").put("text", "APROVAR PONTO")
                .putObject("contextInfo").put("stanzaId", external);
        var event = fixture.conversation.pending(driver, message, fixture.transport.clock.now());
        return fixture.execute(fixture.conversation.claim(event), true);
    }

    private String submitReview(WaPassengerTestSupport.Step review) {
        String external = "synthetic-review-" + UUID.randomUUID();
        UUID outbox = UUID.fromString(review.response().path("responseIds").path(0).asText());
        fixture.transport.jdbc.update("UPDATE wa.message_outbox SET state='SUBMITTED' WHERE id=?", outbox);
        var now = fixture.transport.clock.now();
        fixture.transport.jdbc.update("""
                INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,state,
                    instance_id,external_message_id,started_at,completed_at,credential_id,submit_before)
                VALUES (?,?,1,1,?,'SUBMITTED',?,?,?,?,?,?)
                """, UUID.randomUUID(), outbox, WaCrypto.digest(UUID.randomUUID().toString()),
                WaTransportTestSupport.INSTANCE, external, Timestamp.from(now), Timestamp.from(now),
                fixture.conversation.delivery.credentialId(), Timestamp.from(now.plusSeconds(10)));
        return external;
    }

}
