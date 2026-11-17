package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaRunShortStartIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_run_short_start");
    private WaRunTestSupport f;
    @BeforeEach void setup() { f = new WaRunTestSupport(POSTGRES); }

    @Test void oneReadyTripOffersItsIntentInTheFirstMessageAndReplaysOnlyOnce() {
        assertEquals(WaProblem.Code.ACTION_NOT_ALLOWED, f.send("Começar viagem").decision().error());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        f.pair();
        var lease = f.s.text(f.driver, "Quero iniciar a rota");
        var offered = f.execute(lease, false);
        assertNull(offered.decision().error());
        assertEquals("ADVANCE_RUN_SETUP", offered.body().path("commandType").asText());
        assertTrue(offered.context().path("options").isEmpty());
        assertTrue(offered.text().contains("Ida de teste"), offered.text());
        assertTrue(offered.text().contains("15:00"), offered.text());
        assertTrue(offered.text().contains("1 passageiro previsto, incluindo 1 que ainda não respondeu"), offered.text());
        assertTrue(offered.text().contains("Confirme o início no aplicativo"), offered.text());
        assertFalse(offered.text().contains("CONFIRMAR"));
        assertFalse(offered.context().toString().contains("latitude"));
        assertFalse(offered.context().toString().contains(f.passenger.toString()));
        assertFalse(offered.decision().body().has("intentToken"));
        assertEquals(f.item.occurrence().toString(), f.s.string("SELECT occurrence_id::text FROM wa.start_intent"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_outbox WHERE start_payload_ciphertext IS NOT NULL"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.tracking_session"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE result_json::text LIKE '%intentToken%'"));
        assertEquals(offered.decision(), f.s.execute(lease, offered.body(), "synthetic-run-command-" + lease.id()));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.start_intent"));
        String delivered = authorizeStartLink(offered);
        assertTrue(delivered.contains("Ida de teste"), delivered);
        assertTrue(delivered.contains("15:00"), delivered);
        assertTrue(delivered.contains("1 passageiro previsto, incluindo 1 que ainda não respondeu"), delivered);
        assertTrue(delivered.contains("/motorista/saida#v1."), delivered);
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.tracking_session"));
        f.s.finish(lease);
    }

    @Test void severalReadyTripsRequireAnUnambiguousChoiceWithoutAConfirmationCode() {
        f.pair();
        var returning = readyReturn();
        f.h.jdbc.update("UPDATE wa.routine SET name='Trajeto de teste' WHERE driver_id=?", f.driver);
        var menu = f.send("Iniciar a viagem");
        assertTrue(menu.text().contains("Qual viagem você quer começar?"), menu.text());
        assertEquals(2, f.draft().path("choices").size());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        // Repeated route names must never be resolved by nearest time or first item.
        var ambiguous = f.send("Trajeto de teste");
        assertTrue(ambiguous.text().contains("Qual viagem você quer começar?"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        f.h.jdbc.update("UPDATE wa.routine SET name='Faculdade' WHERE id=?", f.item.routine());
        f.h.jdbc.update("UPDATE wa.routine SET name='1' WHERE id=?", returning.routine());
        f.send("Começar viagem");
        assertEquals(f.item.occurrence().toString(), f.draft().path("choices").get(0).path("id").asText());
        assertEquals(returning.occurrence().toString(), f.draft().path("choices").get(1).path("id").asText());
        // "1" now denotes both the first index and the second trip's name.
        var numericName = f.send("1");
        assertTrue(numericName.text().contains("Qual viagem você quer começar?"), numericName.text());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        var selected = f.send("opção 2");
        assertNull(selected.decision().error());
        assertFalse(selected.text().contains("CONFIRMAR"));
        assertEquals(returning.occurrence().toString(), f.s.string("SELECT occurrence_id::text FROM wa.start_intent"));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.message_outbox WHERE start_payload_ciphertext IS NOT NULL"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    @Test void aFinishedTripIsExcludedBeforeAutomaticallyOfferingTheOnlyReadyTrip() {
        f.pair();
        UUID completed = f.preparation.prepare(f.driver, f.item.occurrence(), f.digest());
        f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',version=version+1 WHERE id=?", completed);
        var returning = readyReturn();
        var offered = f.send("Começar a saída");
        assertNull(offered.decision().error());
        assertTrue(offered.text().contains("Volta de teste"), offered.text());
        assertFalse(offered.text().contains("Qual viagem"));
        assertEquals(returning.occurrence().toString(), f.s.string("SELECT occurrence_id::text FROM wa.start_intent"));
        assertEquals("COMPLETED", f.s.string("SELECT state FROM wa.operation_run WHERE id=?", completed));
    }

    @Test void changedRosterAndExpiredSelectionCannotIssueAnIntent() {
        f.pair();
        readyReturn();
        f.send("Começar viagem");
        f.attendance.send(f.passenger, "Não vou hoje na ida");
        var changed = f.send("ida");
        assertEquals(WaProblem.Code.VERSION_CONFLICT, changed.decision().error());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        f.send("Começar viagem");
        f.h.time.advance(301);
        var expired = f.send("volta");
        assertEquals("ADVANCE_RUN_SETUP", expired.body().path("commandType").asText());
        assertTrue(expired.text().contains("Começar viagem"), expired.text());
        assertFalse(expired.text().contains("CONFIRMAR"), expired.text());
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.start_intent"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.operation_run"));
    }

    private String authorizeStartLink(WaRunTestSupport.Step offered) {
        String box = offered.decision().body().path("responseIds").get(0).asText();
        for (var item : f.s.outboxClaim().path("items")) if (box.equals(item.path("outboxId").asText()))
            return f.s.outbox.authorize(f.s.delivery, UUID.fromString(box),
                WaRunTestSupport.json().put("leaseToken", item.path("leaseToken").asText())).requireSuccess()
                .path("content").path("text").asText();
        throw new AssertionError("Expected the current start link to be available for authorized delivery");
    }

    private WaAttendanceConversationTestSupport.Item readyReturn() {
        var returning = f.attendance.question(f.passenger, WaRunTestSupport.DAY, "RETURN", "19:00");
        f.h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?", returning.routine());
        return returning;
    }
}
