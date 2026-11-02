package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** The server selects the occurrence and interprets the answer before offering a command. */
final class WaAttendanceConversation {
    static final Set<String> COMMANDS = Set.of("ADVANCE_ATTENDANCE", "SET_ATTENDANCE");
    record Offer(String command, ArrayNode options, String choice, UUID target) { }
    record Result(WaDecision decision, String text) { }
    private final WaAttendanceQuestions questions;
    private final WaAttendanceSelection selection;
    private final WaAttendanceSummary summaries;
    private final WaAttendanceMutation mutations;
    private final WaCrypto crypto;

    WaAttendanceConversation(JdbcTemplate jdbc, WaClock clock, WaSettings settings, WaCrypto crypto) {
        questions = new WaAttendanceQuestions(jdbc, clock, settings);
        selection = new WaAttendanceSelection(questions);
        summaries = new WaAttendanceSummary(questions);
        mutations = new WaAttendanceMutation(questions);
        this.crypto = crypto;
    }

    Offer offer(WaProfileState actor, WaInboxAccess.Context event) {
        if (!eligible(actor) || !event.normalizedKind().equals("TEXT")
                || !event.normalizedPayload().path("text").isTextual()) return null;
        String text = WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText());
        if (WaAttendanceAnswers.interrupt(text)) return null;
        ObjectNode draft = selection.currentDraft(actor.identityId());
        if (WaAttendanceAnswers.entry(text)) draft = WaAttendanceSelection.empty();
        WaAttendanceAnswers.Input input = WaAttendanceAnswers.parse(text);
        String owner = WaConversationOwnership.owner(questions.jdbc, actor.identityId());
        boolean explicitAnswer = input.choice() != null && !input.generic() && input.day() != null && input.direction() != null;
        boolean validAttendanceQuote = event.normalizedPayload().hasNonNull("replyTo") && questions.quoted(event) != null;
        if (!owner.equals("READY") && !owner.equals("ATTENDANCE") && !WaAttendanceAnswers.entry(text)
                && !explicitAnswer && !validAttendanceQuote) return null;
        if (owner.equals("ATTENDANCE") && draft.isEmpty() && !WaAttendanceAnswers.entry(text)) return advance();
        if (actor.role().equals("DRIVER")) return driverOffer(actor, event, input, draft);
        boolean continuation = draft.has("kind");
        if (!WaAttendanceAnswers.entry(text) && input.choice() == null && !input.answerLike() && !continuation) return null;
        WaAttendanceSelection.Selected selected = selection.select(actor, event, input, draft);
        if (!input.invalid() && selected.question() != null && selected.choice() != null) {
            UUID id = WaAttendanceQuestions.id(selected.question(), "id");
            return new Offer("SET_ATTENDANCE", options().add(option("QUESTION", WaAttendanceQuestions.ref(id))), selected.choice(), id);
        }
        return advance();
    }

    boolean command(String type, ObjectNode payload) {
        return COMMANDS.contains(type) || type.equals("GET_SUMMARY")
            && payload.path("summaryRef").asText().startsWith("attendance_summary_");
    }

    Result execute(WaProfileState actor, WaInboxAccess.Context event, String type, ObjectNode payload) {
        Offer offered = offer(actor, event);
        if (offered == null || !offered.command().equals(type)) return unavailable();
        ObjectNode expected = WaJson.MAPPER.createObjectNode();
        if (type.equals("SET_ATTENDANCE")) expected.put("questionRef", WaAttendanceQuestions.ref(offered.target())).put("choice", offered.choice());
        if (type.equals("GET_SUMMARY")) expected.put("summaryRef", offered.options().get(0).path("ref").asText());
        if (!WaJson.canonical(payload).equals(WaJson.canonical(expected))) throw WaProblem.denied();
        WaAttendanceAnswers.Input input = WaAttendanceAnswers.parse(event.normalizedPayload().path("text").asText());
        ObjectNode draft = selection.currentDraft(actor.identityId());
        if (WaAttendanceAnswers.entry(input.text())) draft = WaAttendanceSelection.empty();
        if (input.text().equals("abandonar")) {
            questions.save(actor, event, WaAttendanceSelection.empty());
            return new Result(WaDecision.ok(Map.of("changed", true)),
                "A seleção de presença foi encerrada. Envie PRESENÇA quando quiser escolher uma data e sentido.");
        }
        if (type.equals("SET_ATTENDANCE")) {
            var result = mutations.apply(actor, event, offered.target(), offered.choice());
            return new Result(result.decision(), result.text());
        }
        if (actor.role().equals("DRIVER")) return driverAdvance(actor, event, type, offered, input, draft);
        WaAttendanceSelection.Selected selected = selection.select(actor, event, input, draft);
        if (selected.invalidQuote()) {
            questions.save(actor, event, WaAttendanceSelection.empty());
            return new Result(WaDecision.ok(Map.of("changed", true)),
                "Essa mensagem citada não identifica uma pergunta de presença atual sua. Nenhuma outra data foi alterada. Envie PRESENÇA para escolher a data e o sentido.");
        }
        if (Set.of("proxima", "anterior").contains(input.text()) && draft.path("kind").asText().equals("PASSENGER_MENU")) {
            draft.put("page", WaAttendanceSelection.page(draft, input.text()));
        } else if (selected.question() != null && selected.choice() == null && !input.invalid()) {
            draft = selection.menu(selected.choices(), "PASSENGER_SELECTED", null)
                .put("selected", selected.question().path("id").asText());
            questions.save(actor, event, draft);
            return new Result(WaDecision.ok(Map.of("changed", true)),
                "Para " + WaAttendanceQuestions.label(selected.question()) + ", você vai? Responda VOU ou NÃO VOU.");
        } else {
            draft = selection.menu(selected.choices(), "PASSENGER_MENU", input.invalid() ? null : selected.choice());
        }
        questions.save(actor, event, draft);
        String prefix = input.invalid() ? "Não identifiquei uma resposta inequívoca. " : "";
        return new Result(WaDecision.ok(Map.of("changed", true)),
            prefix + WaAttendanceSelection.menuText(draft, selected.choices(), false));
    }

    String help(WaProfileState actor, WaInboxAccess.Context event) {
        ObjectNode draft = selection.currentDraft(actor.identityId());
        if (!draft.has("kind")) return null;
        String instance = questions.instance(event);
        if (actor.role().equals("DRIVER")) {
            if (draft.path("kind").asText().equals("DRIVER_SUMMARY")) {
                ObjectNode occurrence = summaries.own(actor.identityId(), instance, UUID.fromString(draft.path("selected").asText()));
                return occurrence == null ? "Envie PRESENÇAS para rever as ocorrências atuais."
                    : summaries.render(actor.identityId(), occurrence, draft.path("page").asInt());
            }
            return WaAttendanceSelection.menuText(draft, summaries.occurrences(actor.identityId(), instance), true);
        }
        return WaAttendanceSelection.menuText(draft, questions.catalogue(actor.identityId(), instance), false);
    }

    private Offer driverOffer(WaProfileState actor, WaInboxAccess.Context event, WaAttendanceAnswers.Input input, ObjectNode draft) {
        String instance = questions.instance(event);
        ArrayNode live = summaries.occurrences(actor.identityId(), instance);
        UUID id = null;
        if (WaAttendanceAnswers.entry(input.text())) {
            ArrayNode filtered = questions.filtered(live, input);
            if (filtered.size() == 1) id = WaAttendanceQuestions.id(filtered.get(0), "id");
            else return advance();
        } else if (draft.path("kind").asText().equals("DRIVER_MENU") && input.number() != null) {
            int index = draft.path("page").asInt() * 8 + input.number() - 1;
            if (input.number() <= 8 && draft.path("choices").isArray() && index < draft.path("choices").size()) {
                ObjectNode row = WaAttendanceSelection.find(live, draft.path("choices").get(index).asText());
                if (row != null) id = WaAttendanceQuestions.id(row, "id");
            }
            if (id == null) return advance();
        } else if (draft.path("kind").asText().equals("DRIVER_SUMMARY")
                && Set.of("proxima", "anterior").contains(input.text())) {
            ObjectNode row = WaAttendanceSelection.find(live, draft.path("selected").asText());
            if (row != null) id = WaAttendanceQuestions.id(row, "id");
            else return advance();
        } else if (draft.path("kind").asText().equals("DRIVER_MENU")
                && Set.of("proxima", "anterior").contains(input.text())) return advance();
        else return draft.has("kind") ? advance() : null;
        String reference = "attendance_summary_" + crypto.secretHash("attendance-summary", actor.identityId() + ":" + event.eventId() + ":" + id);
        return new Offer("GET_SUMMARY", options().add(option("SUMMARY", reference)), null, id);
    }

    private Result driverAdvance(WaProfileState actor, WaInboxAccess.Context event, String type, Offer offer,
                                 WaAttendanceAnswers.Input input, ObjectNode draft) {
        String instance = questions.instance(event);
        if (type.equals("GET_SUMMARY")) {
            ObjectNode occurrence = summaries.own(actor.identityId(), instance, offer.target());
            if (occurrence == null) return unavailable();
            int page = draft.path("kind").asText().equals("DRIVER_SUMMARY") && draft.path("selected").asText().equals(offer.target().toString())
                ? Math.max(0, draft.path("page").asInt() + (input.text().equals("proxima") ? 1 : input.text().equals("anterior") ? -1 : 0)) : 0;
            draft = selection.menu(options(), "DRIVER_SUMMARY", null).put("selected", offer.target().toString()).put("page", page);
            questions.save(actor, event, draft);
            return new Result(WaDecision.ok(Map.of("changed", true)), summaries.render(actor.identityId(), occurrence, page));
        }
        ArrayNode live = questions.filtered(summaries.occurrences(actor.identityId(), instance), input);
        if (draft.path("kind").asText().equals("DRIVER_MENU") && Set.of("proxima", "anterior").contains(input.text())) {
            draft.put("page", WaAttendanceSelection.page(draft, input.text()));
        } else draft = selection.menu(live, "DRIVER_MENU", null);
        questions.save(actor, event, draft);
        return new Result(WaDecision.ok(Map.of("changed", true)), WaAttendanceSelection.menuText(draft, live, true));
    }

    private boolean eligible(WaProfileState actor) {
        return Set.of("DRIVER", "PASSENGER").contains(actor.role()) && actor.status().equals("ACTIVE")
            && WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion()) && (actor.role().equals("PASSENGER") || actor.driverEnabled());
    }
    private static Offer advance() { return new Offer("ADVANCE_ATTENDANCE", options(), null, null); }
    private static ArrayNode options() { return WaJson.MAPPER.createArrayNode(); }
    private static ObjectNode option(String kind, String reference) { return WaJson.MAPPER.createObjectNode().put("kind", kind).put("ref", reference).put("label", "Opção autorizada"); }
    static Result unavailable() {
        return new Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED),
            "Essa opção de presença foi atualizada. Nenhuma outra data foi alterada. Envie PRESENÇA para escolher novamente.");
    }
}
