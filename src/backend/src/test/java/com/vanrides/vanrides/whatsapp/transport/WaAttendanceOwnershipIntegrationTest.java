package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaConversationTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.time.temporal.ChronoUnit;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceOwnershipIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_ownership");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    private WaPassengerTestSupport f;
    private UUID driver, passenger;

    @BeforeEach void setup() {
        f = new WaPassengerTestSupport(DATABASE.fixture());
        driver = f.driver();
        f.routine(driver);
        passenger = f.passenger();
        f.open(passenger, f.invite(driver, 3));
        for (String text : java.util.List.of("CONTINUAR", "Passageiro sintético", "ACEITO")) {
            f.send(passenger, text);
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

    @Test void anExplicitPresenceInterruptionCanBeAbandonedWithoutLosingPickupCorrection() {
        eligiblePickupCorrection();
        ObjectNode before = f.draft(passenger).deepCopy();
        attendance("PRESENÇA");
        assertEquals(before, f.draft(passenger));
        assertActiveAttendanceSelection();
        attendance("ABANDONAR");
        assertEquals("PASSENGER_SETUP", owner(passenger));
        assertEquals(before, f.draft(passenger));
        f.send(passenger, "Portão lateral");
        assertEquals("REVIEW", f.draft(passenger).path("stage").asText());
        assertEquals("Portão lateral", f.draft(passenger).path("reference").asText());
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

    @Test void quotingTheAbandonAcknowledgementCannotAnswerTheSuspendedPickupCorrection() {
        String correctionQuote = submitted(eligiblePickupCorrection());
        ObjectNode before = f.draft(passenger).deepCopy();
        attendance("PRESENÇA");
        assertActiveAttendanceSelection();
        String acknowledgement = submitted(attendance("ABANDONAR"));
        var rejected = quoted("Portão lateral", acknowledgement);
        assertTrue(rejected.text().contains("não corresponde à etapa atual"));
        assertEquals(before, f.draft(passenger));
        var accepted = quoted("Portão lateral", correctionQuote);
        assertFalse(accepted.text().contains("não corresponde"));
        assertEquals("REVIEW", f.draft(passenger).path("stage").asText());
        assertEquals("Portão lateral", f.draft(passenger).path("reference").asText());
    }

    @Test void anOriginalPickupQuoteRemainsUsableAfterPresenceIsAbandoned() {
        String correctionQuote = submitted(eligiblePickupCorrection());
        attendance("PRESENÇA"); attendance("ABANDONAR");
        quoted("Entrada principal", correctionQuote);
        assertEquals("REVIEW", f.draft(passenger).path("stage").asText());
        assertEquals("Entrada principal", f.draft(passenger).path("reference").asText());
        assertTrue(f.draft(passenger).has("sourceEvent"));
    }

    /** A real approved relationship makes presence eligible before another private wizard is suspended. */
    private UUID eligiblePickupCorrection() {
        for (String answer : java.util.List.of("SIM", "NÃO", "1", "1,3,5")) f.send(passenger, answer);
        f.pin(passenger);
        f.confirm(passenger, f.send(passenger, "PULAR"));
        f.approve(driver);
        assertEquals(1, f.conversation.number(
            "SELECT count(*) FROM wa.driver_passenger_link WHERE passenger_id=? AND state='APPROVED'", passenger));

        // The next minute window includes the link and pickup created after the previous window began.
        f.transport.time.advance(60);
        var jobs = f.transport.proxy(new WaAttendanceJobs(f.transport.jdbc, f.transport.access,
            f.transport.settings, f.transport.clock));
        var job = f.transport.principal(WaPrincipal.Kind.SYSTEM_JOB);
        var page = jobs.tick(job, json().put("jobType", "MATERIALIZE_OCCURRENCES")
            .put("windowKey", f.transport.clock.now().truncatedTo(ChronoUnit.MINUTES).toString()).put("batchLimit", 100));
        assertTrue(page.path("nextCursor").isNull(), "The single routine's seven-day fixture fits one page");
        assertTrue(f.conversation.number("SELECT count(*) FROM wa.attendance_question WHERE passenger_id=? AND state='OPEN'",
            passenger) > 0);

        f.send(passenger, "CORRIGIR PONTO");
        var referenceQuestion = f.pin(passenger);
        assertEquals("REFERENCE", f.draft(passenger).path("stage").asText());
        return UUID.fromString(referenceQuestion.response().path("responseIds").get(0).asText());
    }

    private void assertActiveAttendanceSelection() {
        ObjectNode context = WaJson.object(f.conversation.string(
            "SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", passenger));
        assertEquals("ATTENDANCE", owner(passenger));
        assertTrue(context.path("attendanceDraft").path("choices").size() > 0,
            "The catalogue must actually offer eligible occurrences, not an empty owner placeholder");
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
