package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.transport.WaDriverTestSupport.json;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaDriverWizardIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_driver_wizard");
    private static final WaTransportTestDatabase DATABASE = new WaTransportTestDatabase(POSTGRES);
    WaDriverTestSupport f;
    @BeforeEach void setup() { f = new WaDriverTestSupport(DATABASE.fixture()); }
    @Test void generatedMenuCanBeAuthorizedForDelivery() {
        UUID actor = f.driver(); var menu = f.send(actor, "ROTINAS");
        var items = f.s.outboxClaim().path("items"); assertEquals(1, items.size());
        var reservation = f.s.authorize(assertInstanceOf(ObjectNode.class, items.get(0)));
        assertEquals(menu.text(), reservation.path("content").path("text").asText());
        assertEquals(menu.text().strip(), menu.text());
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_attempt WHERE outbox_id=?",
            UUID.fromString(items.get(0).path("outboxId").asText())));
    }
    @Test void createsCompleteOutboundRoutineOnlyAfterBoundConfirmationInPairing() {
        UUID actor = f.driver(); var review = f.review(actor, "IDA");
        assertEquals(0, f.count("routine", actor)); assertEquals("PAIRING", review.context().path("step").asText());
        f.confirm(actor, review);
        assertEquals(1, f.s.number("""
            SELECT count(*) FROM wa.routine WHERE driver_id=? AND direction='OUTBOUND' AND state='ACTIVE'
            AND weekdays=ARRAY[1,2,3,4,5]::smallint[] AND local_time='06:00' AND timezone='America/Sao_Paulo'
            AND destination_latitude=0 AND destination_longitude=0 AND stop_order_pending AND destination_source_event_id IS NOT NULL
            """, actor));
    }
    @Test void returnIsIndependentAndNeverInvertedFromOutbound() {
        UUID actor = f.driver(); f.routine(actor); f.confirm(actor, f.review(actor, "VOLTA"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.routine WHERE driver_id=? AND direction='OUTBOUND'", actor));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.routine WHERE driver_id=? AND direction='RETURN'", actor));
    }
    @Test void contextContainsOnlySelectedReferencesAndNeutralLabels() {
        UUID actor = f.driver(); var review = f.review(actor, "IDA");
        var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review)); var context = f.context(lease);
        assertEquals(2, context.path("options").size());
        assertFalse(context.toString().contains("Rotina Sintética")); assertFalse(context.toString().contains(f.marker(review)));
        assertFalse(context.toString().contains("latitude")); assertFalse(context.toString().contains("driverDraft"));
    }
    @Test void helpAndPairingDoNotEraseDraft() {
        UUID actor = f.driver(); f.send(actor, "CRIAR"); f.send(actor, "Rotina recuperável"); var before = f.draft(actor);
        var help = f.s.text(actor, "AJUDA"); f.s.execute(help, "RESPOND_TO_MESSAGE", json().put("responseCode", "HELP")).requireSuccess(); f.s.finish(help);
        assertEquals(before, f.draft(actor)); assertEquals("DIRECTION", f.draft(actor).path("stage").asText());
    }
    @Test void restartedServiceContinuesTheSamePersistedDraft() {
        UUID actor = f.driver(); f.send(actor, "CRIAR"); f.send(actor, "Rotina persistida");
        var lease = f.s.text(actor, "VOLTA"); var context = f.restartedCommands().context(f.s.worker, lease.id(), lease.token());
        assertTrue(context.path("allowedCommands").toString().contains("ADVANCE_DRIVER_SETUP"));
        f.restartedCommands().execute(f.s.worker, lease.token(), "synthetic-restart-080", f.s.body(lease, "ADVANCE_DRIVER_SETUP", json()), "synthetic-restart").requireSuccess();
        assertEquals("RETURN", f.draft(actor).path("direction").asText());
    }
    @Test void backCorrectionAndAbandonNeverPersistIncompleteRoutine() {
        UUID actor = f.driver(); f.send(actor, "CRIAR"); f.send(actor, "Nome anterior"); f.send(actor, "VOLTAR");
        assertEquals("NAME", f.draft(actor).path("stage").asText()); f.send(actor, "Nome corrigido"); f.send(actor, "CORRIGIR");
        assertEquals("NAME", f.draft(actor).path("stage").asText()); f.send(actor, "ABANDONAR");
        assertEquals(0, f.count("routine", actor)); assertTrue(f.draft(actor).isEmpty());
    }
    @Test void rejectsDuplicateDaysInvalidTimeAndImplicitDirection() {
        UUID actor = f.driver(); f.send(actor, "CRIAR"); f.send(actor, "Rotina"); f.send(actor, "qualquer");
        assertEquals("DIRECTION", f.draft(actor).path("stage").asText()); f.send(actor, "IDA"); f.send(actor, "1,1,2");
        assertEquals("DAYS", f.draft(actor).path("stage").asText()); f.send(actor, "1,2"); f.send(actor, "25:00");
        assertEquals("TIME", f.draft(actor).path("stage").asText());
    }
    @Test void simpleYesAndOldMarkerNeverApplyAnotherReview() {
        UUID actor = f.driver(); var first = f.review(actor, "IDA"); var second = f.send(actor, "SIM");
        assertNotEquals(f.marker(first), f.marker(second)); var stale = f.s.text(actor, "CONFIRMAR " + f.marker(first));
        assertFalse(f.context(stale).path("allowedCommands").toString().contains("CONFIGURE_ROUTINE")); assertEquals(0, f.count("routine", actor));
    }
    @Test void expiredConfirmationIsNotAdvertised() {
        UUID actor = f.driver(); var review = f.review(actor, "IDA"); f.h.time.advance(301);
        var lease = f.s.text(actor, "CONFIRMAR " + f.marker(review));
        assertFalse(f.context(lease).path("allowedCommands").toString().contains("CONFIGURE_ROUTINE")); assertEquals(0, f.count("routine", actor));
    }
    @Test void profileVersionConflictLeavesTheDraftAndRoutineUntouched() {
        UUID actor = f.driver(); f.send(actor, "CRIAR"); var lease = f.s.text(actor, "Nome");
        var body = f.s.body(lease, "ADVANCE_DRIVER_SETUP", json()); body.put("expectedVersion", body.path("expectedVersion").asLong() - 1);
        assertEquals(WaProblem.Code.VERSION_CONFLICT, assertThrows(WaProblem.class, () -> f.s.execute(lease, body, "synthetic-stale-version")).code());
        assertEquals("NAME", f.draft(actor).path("stage").asText()); assertEquals(0, f.count("routine", actor));
    }
    @Test void replayKeepsOneAdvanceReceiptResponseAndVersion() {
        UUID actor = f.driver(); var step = f.execute(f.s.text(actor, "CRIAR"), false);
        long version = f.s.number("SELECT version FROM wa.profile WHERE identity_id=?", actor);
        var replay = f.s.execute(step.lease(), step.body(), "synthetic-080-" + step.lease().id()).requireSuccess();
        assertEquals(step.response(), replay); assertEquals(version, f.s.number("SELECT version FROM wa.profile WHERE identity_id=?", actor));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE identity_id=?", actor));
    }
    @Test void supportPausesAndInvalidatesMarkerWhileKeepingRecoverableDraft() {
        UUID actor = f.driver(); f.review(actor, "IDA"); var support = f.s.text(actor, "FALAR\u00a0COM SUPORTE");
        f.s.execute(support, "REQUEST_SUPPORT", json().put("reason", "HELP").put("detail", "Pedido explícito")).requireSuccess();
        assertFalse(f.draft(actor).has("confirmation")); assertEquals("Rotina Sintética", f.draft(actor).path("name").asText());
        assertEquals("PAUSED", f.s.string("SELECT stage FROM wa.conversation_state WHERE identity_id=?", actor));
    }
    @Test void unknownPassengerDisabledDriverAndMissingNoticeGetNoDriverCommand() {
        for (int kind = 0; kind < 4; kind++) {
            UUID actor = f.s.actor();
            if (kind > 0 && kind < 3) f.s.driver(actor);
            if (kind == 1) f.h.jdbc.update("UPDATE wa.profile SET driver_enabled=false WHERE identity_id=?", actor);
            if (kind == 2) f.h.jdbc.update("UPDATE wa.profile SET notice_version=NULL,notice_accepted_at=NULL WHERE identity_id=?", actor);
            if (kind == 3) f.h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status) VALUES (?,'PASSENGER','ACTIVE')", actor);
            var context = f.context(f.s.text(actor, "ROTINAS"));
            context.path("allowedCommands").forEach(command -> assertFalse(WaDriverTestSupport.TYPES.contains(command.asText())));
        }
    }
    @Test void invalidDeclineAndOldMarkerStayInWizardButStaleAndPausedDoNotAdvertiseSetup() {
        UUID actor = f.driver(); f.send(actor, "CRIAR");
        for (String value : java.util.List.of("não", "???", "CONFIRMAR BCDFGHJK")) {
            var lease = f.s.text(actor, value);
            assertTrue(f.context(lease).path("allowedCommands").toString().contains("ADVANCE_DRIVER_SETUP")); f.s.finish(lease);
        }
        var stale = f.s.textAt(actor, "CONTINUAR", f.h.clock.now().minusSeconds(600));
        f.context(stale).path("allowedCommands").forEach(value -> assertFalse(WaDriverTestSupport.TYPES.contains(value.asText()))); f.s.finish(stale);
        var support = f.s.text(actor, "FALAR COM SUPORTE");
        f.s.execute(support, "REQUEST_SUPPORT", json().put("reason", "HELP").put("detail", "Pedido explícito")).requireSuccess();
        assertTrue(f.context(support).path("allowedCommands").isEmpty());
    }
    @Test void nameRejectsInvisibleUnicodeFormattingAndBreaksWhilePreservingAccents() {
        UUID actor = f.driver(); f.send(actor, "CRIAR");
        for (String value : java.util.List.of("\u00a0", Character.toString(0x202e), "Rotina" + Character.toString(0x200b),
                "Rotina" + Character.toString(0x2028) + "Nova", "Rotina\nNova")) {
            f.send(actor, value); assertEquals("NAME", f.draft(actor).path("stage").asText()); assertFalse(f.draft(actor).has("name"));
        }
        f.send(actor, "\u00a0Rotina São José\u00a0");
        assertEquals("Rotina São José", f.draft(actor).path("name").asText()); assertEquals("DIRECTION", f.draft(actor).path("stage").asText());
    }
}
