package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker = true)
class WaAttendanceTripSelectionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = WaTransportTestSupport.postgres("wa_attendance_trip_selection");
    private static final LocalDate DAY = LocalDate.of(2030, 1, 1);
    private static final String ENDED = "Essa viagem já foi encerrada. Não é possível alterar a presença dela.";
    private static final String NO_TRIP = "Não há nenhuma viagem próxima registrada. Aguarde a chegada do lembrete.";
    private static final String ONE_AT_A_TIME = "Responda uma viagem por vez: responda ao lembrete da viagem com VOU ou NÃO VOU.";
    private WaAttendanceConversationTestSupport f;
    private UUID passenger;

    @BeforeEach void setup() {
        f = new WaAttendanceConversationTestSupport(POSTGRES);
        passenger = f.passenger();
    }

    @Test void aQuotedReminderWinsOverTheFiltersInTheMessage() {
        var today = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var tomorrow = f.question(passenger, DAY.plusDays(1), "RETURN", "18:00");
        var result = f.quote(passenger, "NÃO VOU hoje na ida", f.sent(tomorrow));
        assertEquals("SET_ATTENDANCE", result.body().path("commandType").asText());
        assertEquals("NOT_GOING", f.state(tomorrow));
        assertEquals("PENDING", f.state(today));
        assertTrue(result.text().contains("• Amanhã, quarta (02/01), volta às 18:00 (Volta de teste)"));
    }

    @Test void aQuotedTripThatIsNoLongerAnswerableGetsTheEndedTextAndChangesNothing() {
        var old = f.question(passenger, DAY, "OUTBOUND", "15:00");
        var current = f.question(passenger, DAY.plusDays(1), "OUTBOUND", "15:00");
        String quote = f.sent(old);
        f.h.jdbc.update("UPDATE wa.attendance_question SET state='CLOSED',version=version+1 WHERE id=?", old.question());
        String before = f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", old.attendance());
        var result = f.quote(passenger, "VOU", quote);
        assertEquals("ADVANCE_ATTENDANCE", result.body().path("commandType").asText());
        assertEquals(ENDED, result.text());
        assertEquals(before, f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", old.attendance()));
        assertEquals("PENDING", f.state(current));
    }

    @Test void aQuotedTripWhoseRunEndedGetsTheEndedTextAndTheStopIsUntouched() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        String quote = f.sent(item);
        UUID run = f.starting(item, "GOING");
        f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED' WHERE id=?", run);
        String stop = f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run);
        var result = f.quote(passenger, "NÃO VOU", quote);
        assertEquals(ENDED, result.text());
        assertEquals("PENDING", f.state(item));
        assertEquals(stop, f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run));
    }

    @Test void aQuoteThatIsNotAnAttendanceQuestionIsIgnored() {
        var own = f.question(passenger, DAY, "OUTBOUND", "15:00");
        UUID other = f.passenger();
        var foreign = f.question(other, DAY, "RETURN", "18:00");
        String foreignQuote = f.sent(foreign);
        for (String external : List.of(foreignQuote, "synthetic-unknown-quote")) {
            f.quote(passenger, "NÃO VOU", external);
            assertEquals("NOT_GOING", f.state(own));
            f.h.jdbc.update("UPDATE wa.attendance SET state='PENDING' WHERE id=?", own.attendance());
        }
        assertEquals("PENDING", f.state(foreign));
    }

    @Test void aBareAnswerPicksTheEarliestTripWhoseStopWasNotPassed() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        UUID run = f.starting(outbound, "GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
        var result = f.send(passenger, "NÃO VOU");
        assertEquals("SET_ATTENDANCE", result.body().path("commandType").asText());
        assertEquals("NOT_GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
        assertTrue(result.text().contains("• Hoje, terça (01/01), volta às 18:00 (Volta de teste)"));
    }

    @Test void aBareAnswerPicksTheEarliestTripWhenNothingStartedYet() {
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var later = f.question(passenger, DAY.plusDays(1), "OUTBOUND", "08:00");
        f.send(passenger, "VOU");
        assertEquals("GOING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
        assertEquals("PENDING", f.state(later));
    }

    @Test void anUnstartedTripMoreThanAnHourPastItsTimeIsSkippedByUnquotedAnswers() {
        f.h.time.set(Instant.parse("2030-01-01T11:01:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var outbound = f.question(passenger, DAY, "OUTBOUND", "07:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.send(passenger, "NÃO VOU");
        assertEquals("NOT_GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
        f.h.jdbc.update("UPDATE wa.attendance SET state='PENDING' WHERE id=?", returning.attendance());
        f.send(passenger, "Não vou hoje");
        assertEquals("NOT_GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
        assertEquals(NO_TRIP, f.send(passenger, "Vou hoje na ida").text());
    }

    @Test void aTripIsNotSkippedUntilAnHourAfterItsTime() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:01");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.send(passenger, "NÃO VOU");
        assertEquals("NOT_GOING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
    }

    @Test void aLateStartedTripIsNeverSkippedAsStale() {
        f.h.time.set(Instant.parse("2030-01-01T11:30:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var outbound = f.question(passenger, DAY, "OUTBOUND", "07:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.starting(outbound, "PENDING");
        f.send(passenger, "NÃO VOU");
        assertEquals("NOT_GOING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
    }

    @Test void quotingTheReminderOfAStaleTripStillAnswersIt() {
        f.h.time.set(Instant.parse("2030-01-01T11:01:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var outbound = f.question(passenger, DAY, "OUTBOUND", "07:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.quote(passenger, "NÃO VOU", f.sent(outbound));
        assertEquals("NOT_GOING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
    }

    @Test void theBareYesOrNoGuardIgnoresStaleUnstartedReminders() {
        f.h.time.set(Instant.parse("2030-01-01T11:01:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var outbound = f.question(passenger, DAY, "OUTBOUND", "07:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.sent(outbound); f.sent(returning);
        f.send(passenger, "não");
        assertEquals("NOT_GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
    }

    @Test void filtersPickTheEarliestMatchingTripWhoseStopWasNotPassed() {
        var todayOut = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var tomorrowOut = f.question(passenger, DAY.plusDays(1), "OUTBOUND", "08:00");
        var tomorrowBack = f.question(passenger, DAY.plusDays(1), "RETURN", "18:00");
        f.send(passenger, "Vou amanhã");
        assertEquals("GOING", f.state(tomorrowOut));
        assertEquals("PENDING", f.state(tomorrowBack));
        f.send(passenger, "Não vou na volta");
        assertEquals("NOT_GOING", f.state(tomorrowBack));
        assertEquals("PENDING", f.state(todayOut));
        UUID run = f.starting(tomorrowOut, "GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
        var none = f.send(passenger, "Vou amanhã na ida");
        assertEquals(NO_TRIP, none.text());
    }

    @Test void aTripWhoseStopWasPassedOrSkippedIsOnlyReachableByQuoteAndNeverChanges() {
        for (String terminal : List.of("PASSED", "SKIPPED")) {
            var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
            String quote = f.sent(item);
            UUID run = f.starting(item, "GOING");
            f.h.jdbc.update("UPDATE wa.run_stop SET state=?,version=version+1 WHERE run_id=?", terminal, run);
            String attendance = f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance());
            String stop = f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run);
            assertEquals(NO_TRIP, f.send(passenger, "NÃO VOU").text());
            for (String choice : List.of("NÃO VOU", "VOU")) {
                var result = f.quote(passenger, choice, quote);
                assertEquals("SET_ATTENDANCE", result.body().path("commandType").asText());
                assertEquals("A van já passou pelo seu ponto.\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)\n"
                    + "A presença desta viagem não pode mais ser alterada.", result.text());
                assertEquals(attendance, f.s.string("SELECT row_to_json(a)::text FROM wa.attendance a WHERE id=?", item.attendance()));
                assertEquals(stop, f.s.string("SELECT row_to_json(s)::text FROM wa.run_stop s WHERE run_id=?", run));
            }
            f.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED' WHERE id=?", run);
        }
    }

    @Test void withoutAnyTripTheAnswerGetsTheNoTripText() {
        var none = f.send(passenger, "VOU");
        assertEquals("ADVANCE_ATTENDANCE", none.body().path("commandType").asText());
        assertEquals(NO_TRIP, none.text());
        var tomorrow = f.question(passenger, DAY.plusDays(1), "OUTBOUND", "08:00");
        assertEquals(NO_TRIP, f.send(passenger, "Vou hoje").text());
        assertEquals("PENDING", f.state(tomorrow));
    }

    @Test void answersNamingBothDirectionsOrTwoDaysGetTheOneTripAtATimeTextAndChangeNothing() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        for (String text : List.of("Vou ida e volta", "Não vou ida e volta", "Vou hoje e amanhã", "Vou hoje e 02/01/2030")) {
            var reply = f.send(passenger, text);
            assertEquals("ADVANCE_ATTENDANCE", reply.body().path("commandType").asText(), text);
            assertEquals(ONE_AT_A_TIME, reply.text(), text);
            assertEquals("PENDING", f.state(outbound));
            assertEquals("PENDING", f.state(returning));
        }
    }

    @Test void aQuotedReminderWinsOverAMessageNamingBothDirections() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        var result = f.quote(passenger, "Vou ida e volta", f.sent(returning));
        assertEquals("SET_ATTENDANCE", result.body().path("commandType").asText());
        assertEquals("GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
    }

    @Test void aDayAndMonthWithoutYearPicksTheNearestFutureDate() {
        var today = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var later = f.question(passenger, LocalDate.of(2030, 11, 16), "OUTBOUND", "08:00");
        var assertedBullet = f.send(passenger, "Vou 16/11").text();
        assertTrue(assertedBullet.contains("• Sábado (16/11), ida às 08:00"), assertedBullet);
        assertEquals("GOING", f.state(later));
        assertEquals("PENDING", f.state(today));
        f.send(passenger, "Não vou 01/01");
        assertEquals("NOT_GOING", f.state(today));
    }

    @Test void unsureAndNumberedAnswersGetNoAttendanceReplyAndChangeNothing() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        for (String text : List.of("Acho que vou hoje na ida", "Talvez não vou", "Vou talvez", "VOU 1", "NÃO VOU 2")) {
            assertNoAttendanceCommand(text);
            assertEquals("PENDING", f.state(outbound));
            assertEquals("PENDING", f.state(returning));
        }
    }

    @Test void messagesWithoutAChoiceAndThePresenceKeywordDoNotGetAnAttendanceReply() {
        f.question(passenger, DAY, "OUTBOUND", "08:00");
        for (String text : List.of("talvez", "PRESENÇA", "Presença hoje", "1", "PRÓXIMA", "ANTERIOR", "ABANDONAR", "obrigado",
            "Qual o horário da ida e da volta?", "A volta de hoje atrasa? E amanhã?", "ida e volta?", "hoje e amanhã")) {
            var lease = f.s.text(passenger, text);
            var context = f.s.commands.context(f.s.worker, lease.id(), lease.token());
            f.s.finish(lease);
            var allowed = new ArrayList<String>();
            context.path("allowedCommands").forEach(value -> allowed.add(value.asText()));
            assertFalse(allowed.contains("ADVANCE_ATTENDANCE") || allowed.contains("SET_ATTENDANCE"), text + " " + allowed);
        }
        assertEquals(0, f.s.number("SELECT count(*) FROM wa.conversation_state WHERE identity_id=? "
            + "AND jsonb_exists(context_json,'attendanceDraft')", passenger));
    }

    @Test void theDayPrefixIsComputedInTheRoutineTimezoneNotInUtc() {
        f.h.time.set(Instant.parse("2030-01-02T01:00:00Z"));
        f.h.jdbc.update("UPDATE wa.service_credential SET expires_at=?", java.sql.Timestamp.from(f.h.clock.now().plusSeconds(3600)));
        var today = f.question(passenger, DAY, "RETURN", "23:00");
        var tomorrow = f.question(passenger, DAY.plusDays(1), "RETURN", "23:00");
        var other = f.question(passenger, DAY.plusDays(2), "RETURN", "23:00");
        assertTrue(f.send(passenger, "Não vou hoje na volta").text().contains("• Hoje, terça (01/01), volta às 23:00 (Volta de teste)"));
        assertTrue(f.send(passenger, "Não vou amanhã na volta").text().contains("• Amanhã, quarta (02/01), volta às 23:00 (Volta de teste)"));
        assertTrue(f.quote(passenger, "Não vou", f.sent(other)).text().contains("• Quinta (03/01), volta às 23:00 (Volta de teste)"));
        assertEquals("NOT_GOING", f.state(today));
        assertEquals("NOT_GOING", f.state(tomorrow));
        assertEquals("NOT_GOING", f.state(other));
    }

    @Test void theConfirmationTextsFollowTheProductWording() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        String bullet = "\n• Hoje, terça (01/01), ida às 15:00 (Ida de teste)";
        assertEquals("Presença confirmada" + bullet, f.send(passenger, "VOU").text());
        assertEquals("Sua presença já estava confirmada" + bullet, f.send(passenger, "VOU").text());
        assertEquals("Ausência registrada" + bullet + "\nMudou de ideia? Responda VOU até a saída do motorista.",
            f.send(passenger, "NÃO VOU").text());
        assertEquals("Sua ausência já estava registrada" + bullet, f.send(passenger, "NÃO VOU").text());
        assertEquals("NOT_GOING", f.state(item));
    }

    @Test void aBareYesOrNoWithoutASentOpenReminderGetsNoReplyAndChangesNothing() {
        var item = f.question(passenger, DAY, "OUTBOUND", "15:00");
        for (String text : List.of("não", "sim")) assertNoAttendanceCommand(text);
        assertEquals("PENDING", f.state(item));
    }

    @Test void aBareYesOrNoWithTwoSentOpenRemindersGetsNoReplyAndChangesNothing() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.sent(outbound); f.sent(returning);
        for (String text : List.of("não", "sim")) assertNoAttendanceCommand(text);
        assertEquals("PENDING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
        f.send(passenger, "NÃO VOU");
        assertEquals("NOT_GOING", f.state(outbound));
        assertEquals("PENDING", f.state(returning));
    }

    @Test void aBareNoWithExactlyOneSentOpenReminderAppliesToIt() {
        var outbound = f.question(passenger, DAY, "OUTBOUND", "08:00");
        var returning = f.question(passenger, DAY, "RETURN", "18:00");
        f.sent(returning);
        f.send(passenger, "não");
        assertEquals("NOT_GOING", f.state(returning));
        assertEquals("PENDING", f.state(outbound));
    }

    @Test void aBareNoWhoseOnlySentOpenReminderHasAPassedStopGetsNoReply() {
        var item = f.question(passenger, DAY, "OUTBOUND", "08:00");
        f.sent(item);
        UUID run = f.starting(item, "GOING");
        f.h.jdbc.update("UPDATE wa.run_stop SET state='PASSED',version=version+1 WHERE run_id=?", run);
        assertNoAttendanceCommand("não");
        assertEquals("PENDING", f.state(item));
    }

    private void assertNoAttendanceCommand(String text) {
        var lease = f.s.text(passenger, text);
        var context = f.s.commands.context(f.s.worker, lease.id(), lease.token());
        f.s.finish(lease);
        var allowed = new ArrayList<String>();
        context.path("allowedCommands").forEach(value -> allowed.add(value.asText()));
        assertFalse(allowed.contains("ADVANCE_ATTENDANCE") || allowed.contains("SET_ATTENDANCE"), text + " " + allowed);
    }
}
