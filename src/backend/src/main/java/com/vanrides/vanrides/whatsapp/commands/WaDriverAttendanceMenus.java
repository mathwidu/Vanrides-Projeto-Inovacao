package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Instant;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

/** The driver's paged occurrence menus and nominal summaries, kept as drafts between messages. */
final class WaDriverAttendanceMenus implements WaAttendanceConversation.Side {
    private final WaAttendanceQuestions questions;
    private final WaAttendanceSummary summaries;
    private final WaCrypto crypto;

    WaDriverAttendanceMenus(WaAttendanceQuestions questions, WaAttendanceSummary summaries, WaCrypto crypto) {
        this.questions = questions; this.summaries = summaries; this.crypto = crypto;
    }

    @Override
    public WaAttendanceConversation.Offer offer(WaProfileState actor, WaInboxAccess.Context event,
                                                WaAttendanceAnswers.Input input, String owner) {
        ObjectNode draft = currentDraft(actor.identityId());
        boolean entry = WaAttendanceAnswers.entry(input.text());
        if (entry) draft = WaAttendanceQuestions.empty();
        if (owner.equals("ATTENDANCE") && draft.isEmpty() && !entry) return advance(actor, event, input, draft);
        String instance = questions.instance(event);
        ArrayNode live = summaries.occurrences(actor.identityId(), instance);
        UUID id = null;
        if (entry) {
            ArrayNode filtered = questions.filtered(live, input);
            if (filtered.size() == 1) id = WaAttendanceQuestions.id(filtered.get(0), "id");
            else return advance(actor, event, input, draft);
        } else if (draft.path("kind").asText().equals("DRIVER_MENU") && input.number() != null) {
            int index = draft.path("page").asInt() * 8 + input.number() - 1;
            if (input.number() <= 8 && draft.path("choices").isArray() && index < draft.path("choices").size()) {
                ObjectNode row = WaAttendanceQuestions.find(live, draft.path("choices").get(index).asText());
                if (row != null) id = WaAttendanceQuestions.id(row, "id");
            }
            if (id == null) return advance(actor, event, input, draft);
        } else if (draft.path("kind").asText().equals("DRIVER_SUMMARY")
                && Set.of("proxima", "anterior").contains(input.text())) {
            ObjectNode row = WaAttendanceQuestions.find(live, draft.path("selected").asText());
            if (row != null) id = WaAttendanceQuestions.id(row, "id");
            else return advance(actor, event, input, draft);
        } else if (draft.path("kind").asText().equals("DRIVER_MENU")
                && Set.of("proxima", "anterior").contains(input.text())) return advance(actor, event, input, draft);
        else return draft.has("kind") ? advance(actor, event, input, draft) : null;
        String reference = "attendance_summary_" + crypto.secretHash("attendance-summary", actor.identityId() + ":" + event.eventId() + ":" + id);
        UUID target = id;
        ObjectNode current = draft;
        return new WaAttendanceConversation.Offer("GET_SUMMARY", WaAttendanceConversation.options().add(WaAttendanceConversation.option("SUMMARY", reference)),
            null, target, () -> summary(actor, event, input, current, target));
    }

    @Override
    public String help(WaProfileState actor, WaInboxAccess.Context event) {
        ObjectNode draft = currentDraft(actor.identityId());
        if (!draft.has("kind")) return null;
        String instance = questions.instance(event);
        if (draft.path("kind").asText().equals("DRIVER_SUMMARY")) {
            ObjectNode occurrence = summaries.own(actor.identityId(), instance, UUID.fromString(draft.path("selected").asText()));
            return occurrence == null ? "Envie PRESENÇAS para rever as ocorrências atuais."
                : summaries.render(actor.identityId(), occurrence, draft.path("page").asInt());
        }
        return menuText(draft, summaries.occurrences(actor.identityId(), instance));
    }

    @Override
    public WaAttendanceConversation.Result unavailable() {
        return new WaAttendanceConversation.Result(WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED),
            "Essa opção de presença foi atualizada. Nenhuma outra data foi alterada. Envie PRESENÇA para escolher novamente.");
    }

    private WaAttendanceConversation.Offer advance(WaProfileState actor, WaInboxAccess.Context event,
                                                   WaAttendanceAnswers.Input input, ObjectNode draft) {
        return new WaAttendanceConversation.Offer("ADVANCE_ATTENDANCE", WaAttendanceConversation.options(), null, null,
            () -> advanceMenu(actor, event, input, draft));
    }

    private WaAttendanceConversation.Result summary(WaProfileState actor, WaInboxAccess.Context event,
                                                    WaAttendanceAnswers.Input input, ObjectNode draft, UUID target) {
        ObjectNode occurrence = summaries.own(actor.identityId(), questions.instance(event), target);
        if (occurrence == null) return unavailable();
        int page = draft.path("kind").asText().equals("DRIVER_SUMMARY") && draft.path("selected").asText().equals(target.toString())
            ? Math.max(0, draft.path("page").asInt() + (input.text().equals("proxima") ? 1 : input.text().equals("anterior") ? -1 : 0)) : 0;
        ObjectNode selected = menu(WaAttendanceConversation.options(), "DRIVER_SUMMARY").put("selected", target.toString()).put("page", page);
        questions.save(actor, event, selected);
        return new WaAttendanceConversation.Result(WaDecision.ok(Map.of("changed", true)),
            summaries.render(actor.identityId(), occurrence, page));
    }

    private WaAttendanceConversation.Result advanceMenu(WaProfileState actor, WaInboxAccess.Context event,
                                                        WaAttendanceAnswers.Input input, ObjectNode draft) {
        if (input.text().equals("abandonar")) {
            questions.save(actor, event, WaAttendanceQuestions.empty());
            return new WaAttendanceConversation.Result(WaDecision.ok(Map.of("changed", true)),
                "A seleção de presença foi encerrada. Envie PRESENÇA quando quiser escolher uma data e sentido.");
        }
        ArrayNode live = questions.filtered(summaries.occurrences(actor.identityId(), questions.instance(event)), input);
        if (live.isEmpty()) return noOccurrences(actor, event);
        if (draft.path("kind").asText().equals("DRIVER_MENU") && Set.of("proxima", "anterior").contains(input.text())) {
            draft.put("page", page(draft, input.text()));
        } else draft = menu(live, "DRIVER_MENU");
        questions.save(actor, event, draft);
        return new WaAttendanceConversation.Result(WaDecision.ok(Map.of("changed", true)), menuText(draft, live));
    }

    private WaAttendanceConversation.Result noOccurrences(WaProfileState actor, WaInboxAccess.Context event) {
        questions.save(actor, event, WaAttendanceQuestions.empty());
        return new WaAttendanceConversation.Result(WaDecision.ok(Map.of("changed", true)),
            "Nenhuma ocorrência disponível. Confirme suas rotinas e os pontos dos passageiros. Envie PRESENÇAS para consultar novamente.");
    }

    private ObjectNode currentDraft(UUID actor) {
        ObjectNode draft = questions.draft(actor);
        try {
            if (!Instant.parse(draft.path("expiresAt").asText()).isAfter(questions.clock.now())) return WaAttendanceQuestions.empty();
        } catch (RuntimeException invalid) { return WaAttendanceQuestions.empty(); }
        return draft;
    }

    private ObjectNode menu(ArrayNode choices, String kind) {
        ObjectNode draft = WaAttendanceQuestions.empty().put("kind", kind).put("page", 0)
            .put("expiresAt", questions.clock.now().plusSeconds(300).toString());
        ArrayNode ids = draft.putArray("choices");
        if (choices.size() <= 100) choices.forEach(row -> ids.add(row.path("id").asText()));
        else draft.put("blocked", true);
        return draft;
    }

    static int page(ObjectNode draft, String text) {
        int last = Math.max(0, (draft.path("choices").size() - 1) / 8);
        int change = text.equals("proxima") ? 1 : text.equals("anterior") ? -1 : 0;
        return Math.max(0, Math.min(last, draft.path("page").asInt() + change));
    }

    private String menuText(ObjectNode draft, ArrayNode live) {
        if (draft.path("blocked").asBoolean()) return "Há muitas opções para esta conversa. Informe data e sentido ou envie FALAR COM SUPORTE.";
        JsonNode ids = draft.path("choices");
        if (ids.isEmpty()) return "Nenhuma ocorrência disponível. O motorista precisa confirmar as rotinas e os pontos. Envie PRESENÇA para consultar novamente.";
        int page = draft.path("page").asInt();
        StringBuilder text = new StringBuilder("Escolha qual lista de presença deseja ver. ");
        text.append("Página ").append(page + 1).append(". ");
        for (int index = page * 8; index < Math.min(page * 8 + 8, ids.size()); index++) {
            ObjectNode row = WaAttendanceQuestions.find(live, ids.get(index).asText());
            text.append(index - page * 8 + 1).append(" — ").append(row == null ? "opção atualizada, reabra PRESENÇA" : WaAttendanceQuestions.label(row, questions.clock.now())).append(". ");
        }
        if (page * 8 + 8 < ids.size()) text.append("Envie PRÓXIMA para continuar. ");
        if (page > 0) text.append("Envie ANTERIOR para voltar. ");
        return text.append("Responda com o número da lista.").toString();
    }
}
