package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDriverTestSupport.json;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDriverRoutinesIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_driver_routines");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaDriverTestSupport f;
    @BeforeEach void setup() { f = new WaDriverTestSupport(DATABASE.fixture()); }
    @Test void editsRoutineWithoutMutatingExistingRunSnapshot() {
        UUID actor = f.driver(), routine = f.routine(actor), run = run(actor, routine, "TRACKING");
        String before = f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run);
        f.send(actor, "ROTINAS"); f.send(actor, "EDITAR 1"); f.send(actor, "Rotina alterada"); f.send(actor, "VOLTA");
        f.send(actor, "2,4"); f.send(actor, "08:30"); f.send(actor, "MANAUS"); f.confirm(actor, f.send(actor, "MANTER"));
        assertEquals("RETURN", f.s.string("SELECT direction FROM wa.routine WHERE id=?", routine));
        assertEquals("America/Manaus", f.s.string("SELECT timezone FROM wa.routine WHERE id=?", routine));
        assertEquals(before, f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
        assertEquals("TRACKING", f.s.string("SELECT state FROM wa.operation_run WHERE id=?", run));
    }
    @Test void pausesAndResumesOnlyTheRoutineWhileActiveRunIsPreserved() {
        UUID actor = f.driver(), routine = f.routine(actor), run = run(actor, routine, "TRACKING");
        f.send(actor, "ROTINAS"); f.confirm(actor, f.send(actor, "PAUSAR 1"));
        assertEquals("PAUSED", f.s.string("SELECT state FROM wa.routine WHERE id=?", routine));
        f.send(actor, "ROTINAS"); f.confirm(actor, f.send(actor, "RETOMAR 1"));
        assertEquals("ACTIVE", f.s.string("SELECT state FROM wa.routine WHERE id=?", routine));
        assertEquals("TRACKING", f.s.string("SELECT state FROM wa.operation_run WHERE id=?", run));
    }
    @Test void cancellationCreatesOnlyCanceledOccurrenceAndPreservesExistingPlannedSnapshot() {
        UUID actor = f.driver(), routine = f.routine(actor);
        f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1"); f.confirm(actor, f.send(actor, "2030-01-07"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.routine_occurrence WHERE routine_id=? AND local_date='2030-01-07' AND state='CANCELED'", routine));
        assertEquals(0, f.count("operation_run", actor)); assertEquals(0, f.s.number("SELECT count(*) FROM wa.attendance"));
        UUID run = run(actor, routine, "PLANNED"); String snapshot = f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run);
        f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1"); f.confirm(actor, f.send(actor, "2030-01-08"));
        assertEquals("CANCELED", f.s.string("SELECT state FROM wa.routine_occurrence WHERE routine_id=? AND local_date='2030-01-08'", routine));
        assertEquals(snapshot, f.s.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?", run));
    }
    @Test void cancelingStartedOrClosedOccurrenceIsRejectedBeforeMutation() {
        UUID actor = f.driver(), routine = f.routine(actor); run(actor, routine, "TRACKING");
        f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1");
        assertEquals(WaProblem.Code.ACTIVE_RUN_EXISTS, assertThrows(WaProblem.class, () -> f.send(actor, "2030-01-08")).code());
        assertEquals("PLANNED", f.s.string("SELECT state FROM wa.routine_occurrence WHERE routine_id=?", routine));
    }
    @Test void daylightSavingGapOrOverlapIsRejectedBeforeReviewAndDoesNotBlockTheConversation() {
        for (String[] sample : java.util.List.of(new String[]{"2018-11-04", "00:30"}, new String[]{"2019-02-16", "23:30"})) {
            UUID actor = f.driver(), routine = f.routine(actor);
            f.h.jdbc.update("UPDATE wa.routine SET local_time=?::time,version=version+1 WHERE id=?", sample[1], routine);
            f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1");
            var lease = f.s.text(actor, sample[0]);
            var rejected = f.s.execute(lease, "ADVANCE_DRIVER_SETUP", json());
            assertEquals(WaProblem.Code.AMBIGUOUS_OCCURRENCE, rejected.error());
            assertEquals(0, f.count("routine_occurrence", actor));
            assertEquals("MENU", f.draft(actor).path("stage").asText()); assertFalse(f.draft(actor).has("confirmation"));
            assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=? AND result_json->>'errorCode'='AMBIGUOUS_OCCURRENCE'", lease.id()));
            var ack = f.s.inbox.result(f.s.worker, lease.id(), lease.token(), json().put("outcome", "PAUSED").put("failureCode", "INVALID_MODEL_OUTPUT"));
            assertEquals("PROCESSED", ack.path("state").asText()); assertNotNull(f.send(actor, "ROTINAS"));
        }
    }
    @Test void stopOrderIsExactPermutationOfApprovedActiveMembers() {
        UUID actor = f.driver(), routine = f.routine(actor); var members = f.members(actor, routine);
        f.send(actor, "ROTINAS"); var question = f.send(actor, "ORDEM 1");
        assertTrue(question.text().contains("Passageiro Sintético 1")); assertTrue(question.text().contains("Ponto Sintético 1; Portaria 1"));
        assertTrue(question.text().contains("Passageiro Sintético 2")); f.send(actor, "1,1");
        assertEquals("ORDER", f.draft(actor).path("stage").asText()); f.send(actor, "1");
        assertEquals("ORDER", f.draft(actor).path("stage").asText()); var review = f.send(actor, "2,1");
        assertTrue(review.text().indexOf("Passageiro Sintético 2") < review.text().indexOf("Passageiro Sintético 1"));
        var confirmed = f.confirm(actor, review);
        assertFalse(confirmed.context().toString().contains("Passageiro Sintético")); assertFalse(confirmed.context().toString().contains("Portaria"));
        assertEquals(1, f.s.number("SELECT stop_order FROM wa.routine_member WHERE id=?", members.get(1)));
        assertEquals(2, f.s.number("SELECT stop_order FROM wa.routine_member WHERE id=?", members.get(0)));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.routine WHERE id=? AND stop_order_pending", routine));
    }
    @Test void changedMembershipInvalidatesAConfirmedOrderProposal() {
        UUID actor = f.driver(), routine = f.routine(actor); var members = f.members(actor, routine);
        f.send(actor, "ROTINAS"); f.send(actor, "ORDEM 1"); var review = f.send(actor, "2,1");
        f.h.jdbc.update("UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE id=?", members.get(0));
        assertEquals(WaProblem.Code.VERSION_CONFLICT, assertThrows(WaProblem.class, () -> f.confirm(actor, review)).code());
    }
    @Test void unapprovedPointPreventsReordering() {
        UUID actor = f.driver(), routine = f.routine(actor); f.members(actor, routine);
        f.h.jdbc.update("UPDATE wa.pickup_point SET state='RETIRED',version=version+1 WHERE driver_id=?", actor);
        f.send(actor, "ROTINAS"); f.send(actor, "ORDEM 1");
        assertEquals(WaProblem.Code.POINT_NOT_APPROVED, assertThrows(WaProblem.class, () -> f.send(actor, "2,1")).code());
    }
    @Test void emptyRoutineRemainsValidWithPendingOrder() {
        UUID actor = f.driver(), routine = f.routine(actor); f.send(actor, "ROTINAS"); f.send(actor, "ORDEM 1");
        f.confirm(actor, f.send(actor, "SEM PONTOS"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.routine WHERE id=? AND state='ACTIVE' AND stop_order_pending", routine));
    }
    @Test void summaryListsOnlyTheDriversOwnRoutinesAndNoPresenceOrCodes() {
        UUID actor = f.driver(), other = f.driver(); f.routine(actor); f.routine(other);
        f.h.jdbc.update("UPDATE wa.routine SET name='Nome exclusivo externo' WHERE driver_id=?", other);
        var summary = f.send(actor, "RESUMO");
        assertFalse(summary.text().contains("Nome exclusivo externo")); assertFalse(summary.text().contains("PASSAGEIRO "));
        assertTrue(summary.text().contains("Presença e execução de viagens ainda não estão disponíveis"));
        assertEquals(1, summary.context().path("options").size()); assertEquals("SUMMARY", summary.context().path("options").get(0).path("kind").asText());
    }
    @Test void callerCannotInjectAnotherActorsPinOrFreeRoutineValues() {
        UUID actor = f.driver(); var review = f.review(actor, "IDA"); var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        var payload = f.payload(f.context(lease), "CONFIGURE_ROUTINE").put("destinationMessageId", "event_" + UUID.randomUUID());
        assertEquals(WaProblem.Code.INVALID_PAYLOAD, assertThrows(WaProblem.class, () -> f.s.execute(lease, "CONFIGURE_ROUTINE", payload)).code());
        assertEquals(0, f.count("routine", actor));
    }
    @Test void closedOccurrenceCannotBeCanceled() {
        UUID actor = f.driver(), routine = f.routine(actor); run(actor, routine, "PLANNED");
        f.h.jdbc.update("UPDATE wa.routine_occurrence SET state='CLOSED',version=version+1 WHERE routine_id=?", routine);
        f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1");
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, assertThrows(WaProblem.class, () -> f.send(actor, "2030-01-08")).code());
    }
    @Test void evenCorruptedDraftCannotResolveAnotherActorsPin() {
        UUID actor = f.driver(), other = f.driver(); var review = f.review(actor, "IDA");
        UUID pin = f.s.pending(other, json().set("locationMessage", json().put("degreesLatitude", 0).put("degreesLongitude", 0)), f.h.clock.now());
        f.s.finish(f.s.claim(pin));
        f.h.jdbc.update("UPDATE wa.conversation_state SET context_json=jsonb_set(context_json,'{driverDraft,destinationEvent}',to_jsonb(?::text)) WHERE identity_id=?", pin.toString(), actor);
        assertEquals(WaProblem.Code.RESOURCE_NOT_ACCESSIBLE, assertThrows(WaProblem.class, () -> f.send(actor, "CONTINUAR")).code());
        assertEquals(0, f.count("routine", actor)); assertNotNull(review);
    }
    @Test void homonymousRoutinesAndStateOrDateReviewsIdentifyDirectionTimeAndTarget() {
        UUID actor = f.driver(); f.routine(actor); f.confirm(actor, f.review(actor, "VOLTA"));
        f.h.jdbc.update("UPDATE wa.routine SET local_time='17:00',version=version+1 WHERE driver_id=? AND direction='RETURN'", actor);
        var menu = f.send(actor, "ROTINAS"); assertTrue(menu.text().contains("IDA 06:00")); assertTrue(menu.text().contains("VOLTA 17:00"));
        String selectedDirection = f.draft(actor).path("routineChoices").get(0).path("direction").asText();
        var pause = f.send(actor, "PAUSAR 1");
        assertTrue(pause.text().contains("Rotina Sintética")); assertTrue(pause.text().contains(selectedDirection.equals("OUTBOUND") ? "IDA 06:00" : "VOLTA 17:00"));
        f.send(actor, "ROTINAS"); f.send(actor, "CANCELAR 1"); var cancel = f.send(actor, "2030-01-07");
        assertTrue(cancel.text().contains("Rotina Sintética")); assertTrue(cancel.text().contains("2030-01-07")); assertTrue(cancel.text().contains("fuso Brasília"));
    }
    @Test void oversizedRoutineMenuShowsNoPartialListAndCannotSelectAnInvisibleRoutine() {
        for (String name : java.util.List.of("R".repeat(80), "🚐".repeat(80))) {
        UUID actor = f.driver();
        int count = name.startsWith("R") ? 25 : 12;
        for (int i = 0; i < count; i++) f.h.jdbc.update("""
            INSERT INTO wa.routine(id,driver_id,name,direction,weekdays,local_time,timezone,destination_latitude,destination_longitude,state,policy_version)
            VALUES (?, ?, ?,'OUTBOUND',ARRAY[1]::smallint[],'06:00','America/Sao_Paulo',0,0,'ACTIVE','synthetic-080')
            """, UUID.randomUUID(), actor, name);
        var menu = f.send(actor, "ROTINAS"); assertTrue(menu.text().contains("lista não foi exibida integralmente")); assertTrue(menu.text().length() <= 2000);
        assertTrue(f.draft(actor).path("routineChoices").isEmpty()); f.send(actor, "PAUSAR 1");
        assertEquals("MENU", f.draft(actor).path("stage").asText()); assertEquals(0, f.s.number("SELECT count(*) FROM wa.routine WHERE driver_id=? AND state='PAUSED'", actor));
        }
    }
    @Test void oversizedCompleteStopListCannotPrepareAPartialReorder() {
        UUID actor = f.driver(), routine = f.routine(actor); f.members(actor, routine, 5);
        f.h.jdbc.update("UPDATE wa.pickup_point SET reference_text=? WHERE driver_id=?", "Descrição ".repeat(24).strip(), actor);
        f.send(actor, "ROTINAS"); var rejected = f.send(actor, "ORDEM 1");
        assertTrue(rejected.text().contains("Nenhuma reordenação foi preparada")); assertTrue(rejected.text().length() <= 2000);
        assertEquals("MENU", f.draft(actor).path("stage").asText()); assertFalse(f.draft(actor).has("stopChoices"));
        f.send(actor, "5,4,3,2,1"); assertFalse(f.draft(actor).has("confirmation"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.routine_member WHERE routine_id=? AND version<>0", routine));
    }
    private UUID run(UUID actor, UUID routine, String state) {
        UUID occurrence = UUID.randomUUID(), run = UUID.randomUUID();
        f.h.jdbc.update("INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at) VALUES (?,?,?,'2030-01-08',?)",
            occurrence, routine, actor, Timestamp.from(f.h.clock.now()));
        f.h.jdbc.update("""
            INSERT INTO wa.operation_run(id,occurrence_id,routine_id,driver_id,local_date,state,policy_version,snapshot_json,snapshot_at)
            VALUES (?,?,?,?,'2030-01-08',?,'synthetic-080','{"syntheticSnapshot":"preserved"}'::jsonb,?)
            """, run, occurrence, routine, actor, state, Timestamp.from(f.h.clock.now())); return run;
    }
}
