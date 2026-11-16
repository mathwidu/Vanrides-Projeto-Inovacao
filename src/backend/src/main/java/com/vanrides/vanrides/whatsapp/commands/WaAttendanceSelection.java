package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.Map;
import java.util.UUID;

/** The passenger side: decides once per message which trip it answers, or why no trip is touched. */
final class WaAttendanceSelection implements WaAttendanceConversation.Side {
    enum Outcome {
        TRIP(null),
        ENDED(WaAttendanceMutation.TRIP_ENDED),
        NO_TRIP("Não há nenhuma viagem próxima registrada. Aguarde a chegada do lembrete."),
        ONE_AT_A_TIME("Responda uma viagem por vez: responda ao lembrete da viagem com VOU ou NÃO VOU."),
        IGNORE(null);

        private final String text;

        Outcome(String text) { this.text = text; }
    }

    record Selected(Outcome outcome, ObjectNode question) { }
    private static final Selected IGNORE = new Selected(Outcome.IGNORE, null);
    private final WaAttendanceQuestions questions;
    private final WaAttendanceMutation mutations;

    WaAttendanceSelection(WaAttendanceQuestions questions, WaAttendanceMutation mutations) {
        this.questions = questions; this.mutations = mutations;
    }

    @Override
    public WaAttendanceConversation.Offer offer(WaProfileState actor, WaInboxAccess.Context event,
                                                WaAttendanceAnswers.Input input, String owner) {
        Selected selected = select(actor, event, input);
        return switch (selected.outcome()) {
            case TRIP -> {
                UUID id = WaAttendanceQuestions.id(selected.question(), "id");
                yield new WaAttendanceConversation.Offer("SET_ATTENDANCE",
                    WaAttendanceConversation.options().add(WaAttendanceConversation.option("QUESTION", WaAttendanceQuestions.ref(id))),
                    input.choice(), id, () -> answer(actor, event, id, input.choice()));
            }
            case ENDED, NO_TRIP, ONE_AT_A_TIME -> new WaAttendanceConversation.Offer("ADVANCE_ATTENDANCE",
                WaAttendanceConversation.options(), null, null, () -> reply(actor, event, selected.outcome()));
            case IGNORE -> null;
        };
    }

    @Override
    public String help(WaProfileState actor, WaInboxAccess.Context event) { return null; }

    @Override
    public WaAttendanceConversation.Result unavailable() {
        return new WaAttendanceConversation.Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED), WaAttendanceMutation.TRIP_ENDED);
    }

    private WaAttendanceConversation.Result answer(WaProfileState actor, WaInboxAccess.Context event, UUID question, String choice) {
        var result = mutations.apply(actor, event, question, choice);
        return new WaAttendanceConversation.Result(result.decision(), result.text());
    }

    private WaAttendanceConversation.Result reply(WaProfileState actor, WaInboxAccess.Context event, Outcome outcome) {
        questions.save(actor, event, WaAttendanceQuestions.empty());
        return new WaAttendanceConversation.Result(WaDecision.ok(Map.of("changed", true)), outcome.text);
    }

    Selected select(WaProfileState actor, WaInboxAccess.Context event, WaAttendanceAnswers.Input input) {
        if (WaAttendanceAnswers.entry(input.text()) || input.choice() == null || input.number() != null) return IGNORE;
        ArrayNode catalogue = questions.catalogue(actor.identityId(), questions.instance(event));
        UUID quoted = event.normalizedPayload().hasNonNull("replyTo") ? questions.quoted(event) : null;
        if (quoted != null) {
            ObjectNode row = WaAttendanceQuestions.find(catalogue, quoted.toString());
            return row == null ? new Selected(Outcome.ENDED, null) : new Selected(Outcome.TRIP, row);
        }
        if (input.multiple()) return new Selected(Outcome.ONE_AT_A_TIME, null);
        if (bare(input)) {
            ObjectNode sent = null;
            int open = 0;
            for (JsonNode row : catalogue) {
                if (row.path("sent").asBoolean() && row.path("state").asText().equals("OPEN") && !row.path("stale").asBoolean()) { sent = (ObjectNode) row; open++; }
            }
            return open == 1 && !sent.path("stopLocked").asBoolean() ? new Selected(Outcome.TRIP, sent) : IGNORE;
        }
        for (JsonNode row : questions.filtered(catalogue, input)) {
            if (!row.path("stopLocked").asBoolean() && !row.path("stale").asBoolean()) return new Selected(Outcome.TRIP, (ObjectNode) row);
        }
        return new Selected(Outcome.NO_TRIP, null);
    }

    private static boolean bare(WaAttendanceAnswers.Input input) {
        return input.generic() && input.day() == null && input.direction() == null;
    }
}
