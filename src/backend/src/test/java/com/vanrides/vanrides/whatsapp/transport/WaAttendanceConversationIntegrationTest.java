package com.vanrides.vanrides.whatsapp.transport;

import static com.vanrides.vanrides.whatsapp.transport.WaAttendanceConversationTestSupport.json;
import static org.junit.jupiter.api.Assertions.*;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.LocalDate;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceConversationIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_conversation");
    private WaAttendanceConversationTestSupport f;
    private UUID passenger;
    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);

    @BeforeEach void setup() {
        f = new WaAttendanceConversationTestSupport(POSTGRES);
        passenger = f.passenger();
    }

    @Test void spontaneousAnswerWorksBeforeReminderAndAfterReminderOptOut() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var answer = f.send(passenger, "Não vou hoje na ida");
        assertNull(answer.decision().error());
        assertEquals("SET_ATTENDANCE", answer.body().path("commandType").asText());
        assertEquals("NOT_GOING", f.state(item));
        assertTrue(answer.text().contains("01/01/2030, ida às 15:00"));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.message_outbox WHERE purpose='REMINDER'"));
    }

    @Test void isolatedYesNeedsASentQuestionOrAnExplicitSelection() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var initial = f.send(passenger, "SIM");
        assertEquals("ADVANCE_ATTENDANCE", initial.body().path("commandType").asText());
        assertEquals("PENDING", f.state(item));
        assertTrue(initial.text().contains("Escolha a data"));
        f.send(passenger, "VOU 1");
        assertEquals("GOING", f.state(item));
    }

    @Test void yesToTheOnlyOpenDeliveredQuestionIsUnambiguous() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.sent(item);
        f.send(passenger, "SIM");
        assertEquals("GOING", f.state(item));
    }

    @Test void simultaneousOutboundAndReturnRequireChoosingTheQuestion() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.sent(outbound); f.sent(returning);
        var menu = f.send(passenger, "SIM");
        assertEquals("ADVANCE_ATTENDANCE", menu.body().path("commandType").asText());
        assertEquals("PENDING", f.state(outbound)); assertEquals("PENDING", f.state(returning));
        var selected = f.send(passenger, "2");
        assertEquals("GOING", f.state(returning)); assertEquals("PENDING", f.state(outbound));
        assertTrue(selected.text().contains("volta às 18:00"));
    }

    @Test void ownQuotedQuestionSelectsTheExactDateAndDirection() {
        var today = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var tomorrow = f.question(passenger, DAY.plusDays(1), "RETURN", "18:00");
        String quote = f.sent(tomorrow);
        var result = f.quote(passenger, "NÃO VOU", quote);
        assertEquals("NOT_GOING", f.state(tomorrow)); assertEquals("PENDING", f.state(today));
        assertTrue(result.text().contains("02/01/2030, volta às 18:00"));
    }

    @Test void unknownAndBorrowedQuotesNeverFallBackToTheNewestQuestion() {
        var own = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID other = f.passenger();
        var foreign = f.question(other, DAY, "RETURN", "18:00");
        String quote = f.sent(foreign);
        for (String external : java.util.List.of(quote, "synthetic-unknown-quote")) {
            var result = f.quote(passenger, "SIM", external);
            assertEquals("ADVANCE_ATTENDANCE", result.body().path("commandType").asText());
            assertTrue(result.text().contains("não identifica uma pergunta de presença atual sua"));
        }
        assertEquals("PENDING", f.state(own)); assertEquals("PENDING", f.state(foreign));
    }

    @Test void aClosedQuotedQuestionDoesNotAnswerTheNewQuestion() {
        var old = f.question(passenger, DAY, "OUTBOUND", "15:00");
        String quote = f.sent(old);
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='CLOSED',version=version+1 WHERE id=?", old.question());
        var current = f.question(passenger, DAY.plusDays(1), "OUTBOUND", "15:00");
        var result = f.quote(passenger, "VOU", quote);
        assertTrue(result.text().contains("não identifica uma pergunta de presença atual sua"));
        assertEquals("PENDING", f.state(old)); assertEquals("PENDING", f.state(current));
    }

    @Test void helpKeepsTheOfferedQuestionSelection() {
        var one = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var two = f.question(passenger, DAY, "RETURN", "18:00");
        f.send(passenger, "NÃO VOU");
        var help = f.s.text(passenger, "AJUDA");
        f.s.execute(help, "RESPOND_TO_MESSAGE", WaConversationTestSupport.reply("HELP")).requireSuccess();
        f.s.finish(help);
        f.send(passenger, "2");
        assertEquals("PENDING", f.state(one)); assertEquals("NOT_GOING", f.state(two));
    }

    @Test void workerCannotReplaceTheQuestionOrChoiceOfferedByTheServer() {
        var one = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var two = f.question(passenger, DAY.plusDays(1), "RETURN", "18:00");
        var lease = f.s.text(passenger, "NÃO VOU hoje na ida");
        var context = f.s.commands.context(f.s.worker, lease.id(), lease.token());
        var forged = f.s.body(lease, "SET_ATTENDANCE", json().put("questionRef", "question_" + two.question()).put("choice", "GOING"))
            .put("expectedVersion", context.path("entityVersion").asLong());
        assertThrows(WaProblem.class, () -> f.s.execute(lease, forged, "synthetic-tampered-answer"));
        assertEquals("PENDING", f.state(one)); assertEquals("PENDING", f.state(two));
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?", lease.id()));
    }

    @Test void duplicateCommandRecoversTheSameReceiptWithoutAnotherVersion() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var lease = f.s.text(passenger, "VOU hoje na ida");
        var first = f.execute(lease, false);
        var second = f.s.execute(lease, first.body(), "synthetic-attendance-command-" + lease.id());
        assertEquals(first.decision().body(), second.body());
        assertEquals(1, f.s.number("SELECT version FROM wa.attendance WHERE id=?", item.attendance()));
        assertEquals(1, f.s.number("SELECT count(*) FROM wa.command_receipt WHERE inbox_event_id=?", lease.id()));
        f.s.finish(lease);
    }

    @Test void driverSelectsOnlyItsOwnNominalPresenceList() {
        var one = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.question(passenger, DAY, "RETURN", "18:00");
        f.send(passenger, "VOU hoje na ida");
        var menu = f.send(f.driver, "PRESENÇAS");
        assertEquals("ADVANCE_ATTENDANCE", menu.body().path("commandType").asText());
        var result = f.send(f.driver, "1");
        assertEquals("GET_SUMMARY", result.body().path("commandType").asText());
        assertTrue(result.text().contains("Confirmados 1"));
        assertTrue(result.text().contains("Pessoa sintética 1"));
        UUID outsider = f.s.actor(); f.s.driver(outsider);
        var empty = f.send(outsider, "PRESENÇAS");
        assertFalse(empty.text().contains("Pessoa sintética"));
        assertEquals("GOING", f.state(one));
    }

    @Test void uncertainLanguageAndContradictoryDirectionsNeverConfirmPresence() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        for (String text : java.util.List.of("Acho que vou hoje na ida", "Talvez não vou", "Vou na ida e na volta")) {
            var reply = f.send(passenger, text);
            assertEquals("ADVANCE_ATTENDANCE", reply.body().path("commandType").asText());
            assertEquals("PENDING", f.state(item));
        }
    }

    @Test void anEmptyOccurrenceStillHasAnAuthorizedDriverSummary() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.h.jdbc.update("DELETE FROM wa.attendance_question WHERE id=?", item.question());
        f.h.jdbc.update("DELETE FROM wa.attendance WHERE id=?", item.attendance());
        f.h.jdbc.update("UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE passenger_id=?", passenger);
        var summary = f.send(f.driver, "PRESENÇAS");
        assertEquals("GET_SUMMARY", summary.body().path("commandType").asText());
        assertTrue(summary.text().contains("Confirmados 0. Não vão 0. Pendentes 0"));
        assertTrue(summary.text().contains("ida às 15:00"));
        assertFalse(summary.text().contains("Pessoa sintética"));
    }

    @Test void aCanceledOccurrenceDoesNotListPendingPeopleAsExpected() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.h.jdbc.update("UPDATE wa.routine_occurrence SET state='CANCELED',version=version+1 WHERE id=?", item.occurrence());
        var summary = f.send(f.driver, "PRESENÇAS");
        assertTrue(summary.text().contains("foi cancelada"));
        assertFalse(summary.text().contains("Pessoa sintética"));
        assertFalse(summary.text().contains("Os pendentes continuam previstos"));
    }

    @Test void startedSummaryUsesFrozenPeopleNamesAndStatesAfterMembershipAndRoutineChanges() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.starting(item, "PENDING");
        f.h.jdbc.update("UPDATE wa.profile SET display_name='Nome futuro' WHERE identity_id=?", passenger);
        f.h.jdbc.update("UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE passenger_id=?", passenger);
        f.h.jdbc.update("UPDATE wa.routine SET name='Rotina futura',direction='RETURN',local_time='18:00',version=version+1 WHERE id=?", item.routine());
        var summary = f.send(f.driver, "PRESENÇAS");
        assertTrue(summary.text().contains("Pessoa sintética 1"));
        assertFalse(summary.text().contains("Nome futuro"));
        assertTrue(summary.text().contains("ida às 15:00"));
        assertTrue(summary.text().contains("Lista capturada na partida"));
        assertTrue(summary.text().contains("Pendentes 1"));
    }

    @Test void aRemovedMemberIsExcludedFromTheFutureListWithoutDeletingItsAnswer() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        f.send(passenger, "VOU hoje na ida");
        f.h.jdbc.update("UPDATE wa.routine_member SET state='REMOVED',version=version+1 WHERE passenger_id=?", passenger);
        var summary = f.send(f.driver, "PRESENÇAS");
        assertTrue(summary.text().contains("Confirmados 0. Não vão 0. Pendentes 0"));
        assertFalse(summary.text().contains("Pessoa sintética"));
        assertEquals("GOING", f.state(item));
    }
}
