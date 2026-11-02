package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.time.Instant;
import java.util.UUID;

/** A quote or a displayed menu selects a question before an answer can affect it. */
final class WaAttendanceSelection {
    record Selected(ObjectNode question, String choice, boolean invalidQuote, ArrayNode choices) { }
    private final WaAttendanceQuestions questions;

    WaAttendanceSelection(WaAttendanceQuestions questions) { this.questions = questions; }

    ObjectNode currentDraft(UUID actor) {
        ObjectNode draft = questions.draft(actor);
        try {
            if (!Instant.parse(draft.path("expiresAt").asText()).isAfter(questions.clock.now())) return empty();
        } catch (RuntimeException invalid) { return empty(); }
        return draft;
    }

    Selected select(WaProfileState actor, WaInboxAccess.Context event, WaAttendanceAnswers.Input input, ObjectNode draft) {
        String instance = questions.instance(event);
        ArrayNode choices = questions.filtered(questions.catalogue(actor.identityId(), instance), input);
        boolean quoted = event.normalizedPayload().hasNonNull("replyTo");
        if (quoted) {
            UUID id = questions.quoted(event);
            ObjectNode row = id == null ? null : find(choices, id.toString());
            return new Selected(row, input.choice(), row == null, choices);
        }
        String choice = input.choice();
        if (choice == null && input.number() != null && draft.path("choice").isTextual()) choice = draft.path("choice").asText();
        if (input.number() != null) {
            int index = draft.path("page").asInt() * 8 + input.number() - 1;
            JsonNode offered = draft.path("choices");
            if (input.number() > 8 || !offered.isArray() || index >= offered.size()) return new Selected(null, null, false, choices);
            ObjectNode row = find(choices, offered.get(index).asText());
            return new Selected(row, choice, false, choices);
        }
        if (draft.path("selected").isTextual()) {
            ObjectNode row = find(choices, draft.path("selected").asText());
            return new Selected(row, choice, false, choices);
        }
        if (input.generic() && input.day() == null && input.direction() == null) {
            ArrayNode emitted = WaJson.MAPPER.createArrayNode();
            for (JsonNode row : choices) {
                if (row.path("sent").asBoolean() && row.path("state").asText().equals("OPEN")) emitted.add(row);
            }
            return new Selected(emitted.size() == 1 ? (ObjectNode) emitted.get(0) : null, choice, false, choices);
        }
        return new Selected(choices.size() == 1 ? (ObjectNode) choices.get(0) : null, choice, false, choices);
    }

    static ObjectNode find(ArrayNode values, String id) {
        for (JsonNode value : values) if (value.path("id").asText().equals(id)) return (ObjectNode) value;
        return null;
    }

    ObjectNode menu(ArrayNode choices, String kind, String choice) {
        ObjectNode draft = empty().put("kind", kind).put("page", 0)
            .put("expiresAt", questions.clock.now().plusSeconds(300).toString());
        ArrayNode ids = draft.putArray("choices");
        if (choices.size() <= 100) choices.forEach(row -> ids.add(row.path("id").asText()));
        else draft.put("blocked", true);
        if (choice != null) draft.put("choice", choice);
        return draft;
    }

    static int page(ObjectNode draft, String text) {
        int last = Math.max(0, (draft.path("choices").size() - 1) / 8);
        int change = text.equals("proxima") ? 1 : text.equals("anterior") ? -1 : 0;
        return Math.max(0, Math.min(last, draft.path("page").asInt() + change));
    }

    static String menuText(ObjectNode draft, ArrayNode live, boolean driver) {
        if (draft.path("blocked").asBoolean()) return "Há muitas opções para esta conversa. Informe data e sentido ou envie FALAR COM SUPORTE.";
        JsonNode ids = draft.path("choices");
        if (ids.isEmpty()) return "Nenhuma ocorrência disponível. O motorista precisa confirmar as rotinas e os pontos. Envie PRESENÇA para consultar novamente.";
        int page = draft.path("page").asInt();
        StringBuilder text = new StringBuilder(driver ? "Escolha qual lista de presença deseja ver. " : "Escolha a data e o sentido da sua presença. ");
        text.append("Página ").append(page + 1).append(". ");
        for (int index = page * 8; index < Math.min(page * 8 + 8, ids.size()); index++) {
            ObjectNode row = find(live, ids.get(index).asText());
            text.append(index - page * 8 + 1).append(" — ").append(row == null ? "opção atualizada, reabra PRESENÇA" : WaAttendanceQuestions.label(row)).append(". ");
        }
        if (page * 8 + 8 < ids.size()) text.append("Envie PRÓXIMA para continuar. ");
        if (page > 0) text.append("Envie ANTERIOR para voltar. ");
        return text.append(driver ? "Responda com o número da lista." : "Responda VOU 1 ou NÃO VOU 1, usando o número exibido. Também pode enviar só o número para revisar antes de responder.").toString();
    }

    static ObjectNode empty() { return WaJson.MAPPER.createObjectNode(); }
}
