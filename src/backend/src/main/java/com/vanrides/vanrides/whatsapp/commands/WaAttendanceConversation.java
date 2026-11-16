package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.Set;
import java.util.UUID;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;

/** The server selects the occurrence and interprets the answer before offering a command. */
final class WaAttendanceConversation {
    static final Set<String> COMMANDS = Set.of("ADVANCE_ATTENDANCE", "SET_ATTENDANCE");
    record Offer(String command, ArrayNode options, String choice, UUID target, Supplier<Result> run) { }
    record Result(WaDecision decision, String text) { }
    interface Side {
        Offer offer(WaProfileState actor, WaInboxAccess.Context event, WaAttendanceAnswers.Input input, String owner);
        String help(WaProfileState actor, WaInboxAccess.Context event);
        Result unavailable();
    }
    private final WaAttendanceQuestions questions;
    private final Side drivers;
    private final Side passengers;

    WaAttendanceConversation(JdbcTemplate jdbc, WaClock clock, WaSettings settings, WaCrypto crypto) {
        questions = new WaAttendanceQuestions(jdbc, clock, settings);
        drivers = new WaDriverAttendanceMenus(questions, new WaAttendanceSummary(questions), crypto);
        passengers = new WaAttendanceSelection(questions, new WaAttendanceMutation(questions));
    }

    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        if (!eligible(actor) || !event.normalizedKind().equals("TEXT")
                || !event.normalizedPayload().path("text").isTextual()) return null;
        String text = WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText());
        if (WaAttendanceAnswers.interrupt(text)) return null;
        WaAttendanceAnswers.Input input = WaAttendanceAnswers.parse(text);
        String owner = WaConversationOwnership.owner(questions.jdbc, actor.identityId());
        boolean explicitAnswer = input.choice() != null && !input.generic() && input.day() != null && input.direction() != null;
        boolean validAttendanceQuote = event.normalizedPayload().hasNonNull("replyTo") && questions.quoted(event) != null;
        if (!owner.equals("READY") && !owner.equals("ATTENDANCE") && !WaAttendanceAnswers.entry(text)
                && !explicitAnswer && !validAttendanceQuote) return null;
        return side(actor).offer(actor, event, input, owner);
    }

    boolean command(String type, ObjectNode payload) {
        return COMMANDS.contains(type) || type.equals("GET_SUMMARY")
            && payload.path("summaryRef").asText().startsWith("attendance_summary_");
    }

    Result execute(WaProfileState actor, WaInboxAccess.Context event, String type, ObjectNode payload) {
        Offer offered = offer(actor, event);
        if (offered == null || !offered.command().equals(type)) return unavailable(actor);
        ObjectNode expected = WaJson.MAPPER.createObjectNode();
        if (type.equals("SET_ATTENDANCE")) expected.put("questionRef", WaAttendanceQuestions.ref(offered.target())).put("choice", offered.choice());
        if (type.equals("GET_SUMMARY")) expected.put("summaryRef", offered.options().get(0).path("ref").asText());
        if (!WaJson.canonical(payload).equals(WaJson.canonical(expected))) throw WaProblem.denied();
        return offered.run().get();
    }

    String help(WaProfileState actor, WaInboxAccess.Context event) { return side(actor).help(actor, event); }

    Result unavailable(WaProfileState actor) { return side(actor).unavailable(); }

    private Side side(WaProfileState actor) { return actor.role().equals("DRIVER") ? drivers : passengers; }

    private boolean eligible(WaProfileState actor) {
        return Set.of("DRIVER", "PASSENGER").contains(actor.role()) && actor.status().equals("ACTIVE")
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion()) && (actor.role().equals("PASSENGER") || actor.driverEnabled());
    }
    static ArrayNode options() { return WaJson.MAPPER.createArrayNode(); }
    static ObjectNode option(String kind, String reference) { return WaJson.MAPPER.createObjectNode().put("kind", kind).put("ref", reference).put("label", "Opção autorizada"); }
}
