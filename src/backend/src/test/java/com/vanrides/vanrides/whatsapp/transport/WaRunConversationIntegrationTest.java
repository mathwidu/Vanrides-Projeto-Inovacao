package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaRunTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaRunConversationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_run_conversation");
    private WaRunTestSupport f;
    @BeforeEach void setup() { f = new WaRunTestSupport(POSTGRES); }

    @Test void requestIsReviewedIdempotentAndContainsNoClearTokenOrCoordinates() {
        f.pair();
        var review = f.reviewStart();
        assertTrue(review.text().contains("pendente"));
        var lease = f.s.text(f.driver, "CONFIRMAR " + f.marker(review));
        var started = f.execute(lease, false);
        assertNull(started.decision().error());
        assertEquals("REQUEST_START", started.body().path("commandType").asText());
        assertFalse(started.context().toString().contains("latitude"));
        assertFalse(started.context().toString().contains(f.passenger.toString()));
        assertFalse(started.decision().body().has("intentToken"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.start_intent WHERE driver_id=?", f.driver));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_outbox WHERE start_payload_ciphertext IS NOT NULL"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE result_json::text LIKE '%intentToken%'"));
        var replay = f.s.execute(lease, started.body(), "synthetic-run-command-" + lease.id());
        assertEquals(started.decision(), replay);
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.start_intent"));
        f.s.finish(lease);
    }

    @Test void correctionPreservesPersonalPresenceAndShowsTheOperationalAuthor() {
        var review = f.reviewCorrection("EXCLUIR");
        assertTrue(review.text().contains("estava previsto; ficará fora da lista"));
        assertTrue(review.text().contains("ajuste operacional sintético"));
        var correction = f.send("CONFIRMAR " + f.marker(review));
        assertNull(correction.decision().error());
        assertEquals("PENDING", f.attendance.state(f.item));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.occurrence_roster_override WHERE actor_identity_id=? AND included=false", f.driver));
        var summary = f.attendance.send(f.driver, "PRESENÇAS");
        assertTrue(summary.text().contains("ajuste do motorista: fora da lista"));
        assertTrue(summary.text().contains("motivo operacional"));
        UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), f.digest());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.run_stop WHERE run_id=?", run));
    }

    @Test void personalRefusalCannotBeOverriddenByTheDriver() {
        f.attendance.send(f.passenger, "Não vou hoje na ida");
        f.send("CORRIGIR LISTA"); f.send("1"); f.send("1");
        var refused = f.send("INCLUIR");
        assertTrue(refused.text().contains("inclusão operacional foi recusada"));
        assertEquals("ACTION", f.draft().path("stage").asText());
        assertEquals("NOT_GOING", f.attendance.state(f.item));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.occurrence_roster_override"));
    }

    @Test void aDifferentAttendanceAnswerInvalidatesTheReviewedStart() {
        f.pair();
        var review = f.reviewStart();
        f.attendance.send(f.passenger, "Não vou hoje na ida");
        var changed = f.send("CONFIRMAR " + f.marker(review));
        assertEquals(WaProblem.Code.VERSION_CONFLICT, changed.decision().error());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void anotherActorCannotUseTheOccurrenceOrConfirmationReference() {
        f.pair();
        var review = f.reviewStart();
        var ownerLease = f.s.text(f.driver, "CONFIRMAR " + f.marker(review));
        var ownerContext = f.s.commands.context(f.s.worker, ownerLease.id(), ownerLease.token());
        var otherLease = f.s.text(f.passenger, "CONFIRMAR " + f.marker(review));
        var payload = json();
        ownerContext.path("options").forEach(option -> payload.put(option.path("kind").asText().equals("OCCURRENCE") ? "occurrenceRef" : "confirmationRef", option.path("ref").asText()));
        var result = f.s.execute(otherLease, "REQUEST_START", payload);
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, result.error());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        f.s.finish(otherLease); f.s.finish(ownerLease);
    }

    @Test void staleConfirmationCannotRenameTheDriverOrChangeTheList() {
        var review = f.reviewCorrection("EXCLUIR");
        f.h.time.advance(301);
        var expired = f.send("CONFIRMAR " + f.marker(review));
        assertEquals("ADVANCE_RUN_SETUP", expired.body().path("commandType").asText());
        assertEquals("Motorista sintético", f.s.string("SELECT display_name FROM wa.profile WHERE identity_id=?", f.driver));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.occurrence_roster_override"));
    }

    @Test void startUsesTheFrozenLocalDayAndNeverOffersTomorrowAcrossUtcMidnight() {
        f.h.jdbc.update("UPDATE wa.routine SET timezone='America/Manaus' WHERE id=?", f.item.routine());
        f.h.jdbc.update("UPDATE wa.routine_occurrence SET scheduled_at=?::timestamptz,schedule_snapshot=jsonb_set(schedule_snapshot,'{timezone}','\"America/Manaus\"') WHERE id=?",
            "2030-01-01T19:00:00Z", f.item.occurrence());
        f.attendance.question(f.passenger, LocalDate.of(2030, 1, 2), "RETURN", "15:00");
        f.h.time.set(Instant.parse("2030-01-02T02:00:00Z"));
        f.renewExpiredTransportCredentials();
        f.send("INICIAR SAÍDA");
        assertEquals(1, f.draft().path("choices").size());
        assertEquals(f.item.occurrence().toString(), f.draft().path("choices").get(0).path("id").asText());
        assertNotNull(f.digest());
    }

    @Test void resumePreservesTheSnapshotAfterTheLocalDayChanges() {
        f.pair();
        UUID run = f.preparation.prepare(f.driver, f.item.occurrence(), f.digest());
        f.h.jdbc.update("UPDATE wa.operation_run SET state='DEGRADED',version=version+1 WHERE id=?", run);
        String snapshot = f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run);
        f.h.time.advance(86400);
        f.renewExpiredTransportCredentials();
        f.send("RETOMAR SAÍDA");
        var review = f.send("1");
        var resumed = f.send("CONFIRMAR " + f.marker(review));
        assertEquals("REQUEST_RESUME", resumed.body().path("commandType").asText());
        assertNull(resumed.decision().error());
        assertEquals(snapshot, f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.start_intent WHERE intent_action='RESUME' AND run_id=?", run));
    }
}
