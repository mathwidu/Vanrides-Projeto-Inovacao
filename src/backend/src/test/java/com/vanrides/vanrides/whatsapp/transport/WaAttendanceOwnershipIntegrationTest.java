package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaConversationTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceOwnershipIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_ownership");
    private WaPassengerTestSupport f;
    private UUID driver, passenger;
    private UUID preferenceResponse;

    @BeforeEach void setup() {
        f = new WaPassengerTestSupport(POSTGRES);
        driver = f.driver();
        f.routine(driver);
        passenger = f.passenger();
        f.open(passenger, f.invite(driver, 3));
        for (String text : java.util.List.of("CONTINUAR", "Passageiro sintético", "ACEITO")) {
            var step = f.send(passenger, text);
            preferenceResponse = UUID.fromString(step.response().path("responseIds").get(0).asText());
        }
        assertEquals("PREF_APPROACH", f.draft(passenger).path("stage").asText());
    }

    @Test void yesAndNoRemainWithThePassengerPreferencesDuringRealOnboarding() {
        var approach = f.send(passenger, "SIM");
        assertEquals("ADVANCE_PASSENGER_SETUP", f.command(approach.context()));
        assertFalse(approach.context().has("attendanceChoice"));
        assertEquals("PREF_REMINDER", f.draft(passenger).path("stage").asText());
        var reminder = f.send(passenger, "NÃO");
        assertEquals("ADVANCE_PASSENGER_SETUP", f.command(reminder.context()));
        assertEquals("ROUTINES", f.draft(passenger).path("stage").asText());
        assertTrue(f.draft(passenger).path("approachMessages").asBoolean());
        assertFalse(f.draft(passenger).path("attendanceReminders").asBoolean());
        assertEquals(0, f.conversation.number("SELECT count(*) FROM wa.attendance"));
    }

    @Test void anUnknownOldQuoteCannotBecomeConsentOrAttendance() {
        ObjectNode old = f.draft(passenger).deepCopy();
        ObjectNode message = json();
        message.putObject("extendedTextMessage").put("text", "SIM").putObject("contextInfo").put("stanzaId", "synthetic-old-attendance-question");
        var lease = f.conversation.claim(f.conversation.pending(passenger, message, f.transport.clock.now()));
        var response = f.execute(lease, true);
        assertEquals("ADVANCE_PASSENGER_SETUP", f.command(response.context()));
        assertTrue(response.text().contains("não corresponde à etapa atual"));
        assertEquals(old, f.draft(passenger));
        assertEquals(0, f.conversation.number("SELECT count(*) FROM wa.attendance"));
        f.send(passenger, "SIM");
        assertEquals("PREF_REMINDER", f.draft(passenger).path("stage").asText());
    }

    @Test void anExplicitPresenceInterruptionCanBeAbandonedWithoutLosingOnboarding() {
        ObjectNode before = f.draft(passenger).deepCopy();
        attendance("PRESENÇA");
        assertEquals(before, f.draft(passenger));
        assertEquals("ATTENDANCE", owner(passenger));
        attendance("ABANDONAR");
        assertEquals("PASSENGER_SETUP", owner(passenger));
        f.send(passenger, "SIM");
        assertEquals("PREF_REMINDER", f.draft(passenger).path("stage").asText());
    }

    @Test void numericDaysAndDriverReviewRemainInTheRoutineWizard() {
        for (String text : java.util.List.of("ROTINAS", "CRIAR", "Outra rotina", "IDA")) f.driverFixture.send(driver, text);
        var weekdays = f.driverFixture.send(driver, "1");
        assertEquals("ADVANCE_DRIVER_SETUP", weekdays.body().path("commandType").asText());
        assertFalse(weekdays.context().has("attendanceChoice"));
        assertEquals("DRIVER_SETUP", owner(driver));
        assertEquals("TIME", f.driverFixture.draft(driver).path("stage").asText());
    }

    @Test void aQuotedConfirmationFromAnotherStepCannotApplyADriverMutation() {
        var review = f.driverFixture.review(driver, "IDA");
        long before = f.conversation.number("SELECT count(*) FROM wa.routine WHERE driver_id=?", driver);
        ObjectNode message = json();
        message.putObject("extendedTextMessage").put("text", "CONFIRMAR " + f.driverFixture.marker(review))
            .putObject("contextInfo").put("stanzaId", "synthetic-old-draft-question");
        var lease = f.conversation.claim(f.conversation.pending(driver, message, f.transport.clock.now()));
        var reply = f.driverFixture.execute(lease, true);
        assertEquals("ADVANCE_DRIVER_SETUP", reply.body().path("commandType").asText());
        assertTrue(reply.text().contains("não corresponde à etapa atual"));
        assertEquals(before, f.conversation.number("SELECT count(*) FROM wa.routine WHERE driver_id=?", driver));
    }

    @Test void quotingTheAbandonAcknowledgementCannotAnswerTheSuspendedPreference() {
        String preferenceQuote = submitted(preferenceResponse);
        attendance("PRESENÇA");
        String acknowledgement = submitted(attendance("ABANDONAR"));
        var rejected = quoted("SIM", acknowledgement);
        assertTrue(rejected.text().contains("não corresponde à etapa atual"));
        assertEquals("PREF_APPROACH", f.draft(passenger).path("stage").asText());
        var accepted = quoted("SIM", preferenceQuote);
        assertFalse(accepted.text().contains("não corresponde"));
        assertEquals("PREF_REMINDER", f.draft(passenger).path("stage").asText());
    }

    @Test void anOriginalPreferenceQuoteRemainsUsableAfterPresenceIsAbandoned() {
        String preferenceQuote = submitted(preferenceResponse);
        attendance("PRESENÇA"); attendance("ABANDONAR");
        quoted("NÃO", preferenceQuote);
        assertEquals("PREF_REMINDER", f.draft(passenger).path("stage").asText());
        assertTrue(f.draft(passenger).has("approachMessages"));
        assertFalse(f.draft(passenger).path("approachMessages").asBoolean());
    }

    private UUID attendance(String text) {
        var lease = f.conversation.text(passenger, text);
        var context = f.conversation.commands.context(f.conversation.worker, lease.id(), lease.token());
        assertTrue(context.path("allowedCommands").toString().contains("ADVANCE_ATTENDANCE"));
        var result = f.conversation.execute(lease, "ADVANCE_ATTENDANCE", json()).requireSuccess();
        f.conversation.finish(lease);
        return UUID.fromString(result.path("responseIds").get(0).asText());
    }

    private WaPassengerTestSupport.Step quoted(String text, String external) {
        ObjectNode message = json();
        message.putObject("extendedTextMessage").put("text", text).putObject("contextInfo").put("stanzaId", external);
        return f.execute(f.conversation.claim(f.conversation.pending(passenger, message, f.transport.clock.now())), true);
    }

    private String submitted(UUID box) {
        String external = "synthetic-owned-prompt-" + UUID.randomUUID();
        f.transport.jdbc.update("UPDATE wa.message_outbox SET state='SUBMITTED' WHERE id=?", box);
        f.transport.jdbc.update("""
            INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,state,instance_id,
              external_message_id,started_at,completed_at,credential_id,submit_before)
            VALUES (?,?,1,1,?,'SUBMITTED',?,?,?,?,?,?)
            """, UUID.randomUUID(), box, WaCrypto.digest(UUID.randomUUID().toString()), WaTransportTestSupport.INSTANCE,
            external, Timestamp.from(f.transport.clock.now()), Timestamp.from(f.transport.clock.now()),
            f.conversation.delivery.credentialId(), Timestamp.from(f.transport.clock.now().plusSeconds(10)));
        return external;
    }

    private String owner(UUID actor) {
        return f.conversation.string("SELECT context_json->>'conversationOwner' FROM wa.conversation_state WHERE identity_id=?", actor);
    }
}
