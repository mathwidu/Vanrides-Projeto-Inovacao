package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class WaRunTexts {
    private WaRunTexts() { }
    static String prompt(ObjectNode draft) {
        StringBuilder text = new StringBuilder();
        switch (draft.path("stage").asText()) {
            case "OCCURRENCE" -> {
                if (draft.path("tooMany").asBoolean()) return "Há muitas saídas para uma revisão completa nesta conversa. Envie FALAR COM SUPORTE para escolher a ocorrência.";
                text.append("Escolha a saída para ").append(draft.path("action").asText().equals("CORRECT") ? "corrigir a lista" : "iniciar ou retomar").append(". ");
                for (int i = 0; i < draft.path("choices").size(); i++) text.append(i + 1).append(" — ")
                    .append(draft.path("choices").get(i).path("label").asText()).append(". ");
                if (draft.path("choices").isEmpty()) text.append(draft.path("action").asText().equals("RESUME")
                    ? "Nenhuma saída está aguardando retomada. " : "Nenhuma saída disponível. Confira as rotinas e a ordem dos pontos. ");
                return text.append("Responda com o número exibido ou ABANDONAR.").toString();
            }
            case "PASSENGER" -> {
                text.append("Escolha o participante de ").append(draft.path("label").asText()).append(". ");
                for (int i = 0; i < draft.path("members").size(); i++) {
                    JsonNode member = draft.path("members").get(i);
                    text.append(i + 1).append(" — ").append(member.path("name").asText()).append(" (")
                        .append(member.path("included").asBoolean() ? "previsto" : "fora da lista").append("). ");
                }
                return text.append("Responda com o número ou ABANDONAR.").toString();
            }
            case "ACTION" -> { return "Para " + draft.path("passengerName").asText() + ", envie INCLUIR ou EXCLUIR. A declaração pessoal de presença será preservada."; }
            case "REASON" -> { return "Informe o motivo operacional desta correção, entre 3 e 160 caracteres. Depois você revisará antes de confirmar."; }
            default -> { return "Envie INICIAR SAÍDA, RETOMAR SAÍDA ou CORRIGIR LISTA para escolher uma ocorrência atual."; }
        }
    }
    static String roster(ObjectNode review) {
        StringBuilder text = new StringBuilder();
        for (JsonNode member : review.path("members")) text.append(member.path("name").asText()).append(" — ")
            .append(member.path("included").asBoolean() ? "previsto" : "fora da lista").append(" (")
            .append(switch (member.path("attendance").asText()) { case "GOING" -> "confirmou"; case "NOT_GOING" -> "não vai"; default -> "pendente"; }).append("). ");
        return text.toString();
    }
    static String review(ObjectNode draft, String marker) {
        String message = draft.path("action").asText().equals("CORRECT")
            ? "Correção de " + draft.path("label").asText() + ": " + draft.path("passengerName").asText() + " estava "
                + (draft.path("beforeIncluded").asBoolean() ? "previsto" : "fora da lista") + "; ficará "
                + (draft.path("included").asBoolean() ? "previsto" : "fora da lista") + ". Motivo operacional: " + draft.path("reason").asText() + ". "
            : "Revise " + draft.path("label").asText() + ". " + draft.path("rosterText").asText()
                + "O acompanhamento será confirmado somente após o aplicativo enviar GPS válido. ";
        return message + "Para confirmar esta revisão, envie CONFIRMAR " + marker + ". Para desistir, ABANDONAR.";
    }
}
