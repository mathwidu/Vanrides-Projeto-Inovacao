package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

final class WaPassengerTexts {
    private WaPassengerTexts() {}

    static String prompt(ObjectNode draft) {
        String stage = draft.path("stage").asText();
        return switch (stage) {
            case "DRIVER_CONFIRM" ->
                    "Seu convite é do motorista "
                            + draft.path("driverName").asText()
                            + ". Você quer se cadastrar com este motorista? Responda CONTINUAR ou"
                            + " ABANDONAR.";
            case "NAME" -> "Como você prefere ser chamado?";
            case "NOTICE" ->
                    "Usaremos seu nome, conversa, ponto de embarque e participação nas rotinas para"
                        + " organizar seu transporte. Os avisos serão escolhidos separadamente."
                        + " Você aceita o aviso de privacidade VanRides wa-mvp-v1? Responda ACEITO"
                        + " para continuar.";
            case "PREF_APPROACH" ->
                    "Você deseja receber mensagens quando a van estiver se aproximando? Responda"
                            + " SIM ou NÃO.";
            case "PREF_REMINDER" ->
                    "Você deseja receber lembretes para informar sua presença? Responda SIM ou"
                            + " NÃO.";
            case "ROUTINES" -> routines(draft);
            case "DAYS" ->
                    "Para "
                            + draft.path("selections")
                                    .path(draft.path("dayIndex").asInt())
                                    .path("name")
                                    .asText()
                            + ", em quais dias você participa? Use 1=segunda até 7=domingo,"
                            + " separados por vírgula, ou SEGUNDA A SEXTA. Dias disponíveis: "
                            + draft.path("selections")
                                    .path(draft.path("dayIndex").asInt())
                                    .path("weekdays")
                                    .toString()
                            + ".";
            case "POINT" -> points(draft);
            case "REFERENCE" ->
                    "É na entrada principal, portão lateral ou outro acesso? Envie uma referência"
                        + " curta, ou PULAR se o pin já estiver claro. Se o pin não corresponde ao"
                        + " acesso, envie CORRIGIR PONTO.";
            case "DRIVER_MENU" -> menu(draft);
            case "COMMON_NAME" ->
                    "Qual é o nome do ponto comum que você deseja publicar aos seus passageiros?";
            case "COMMON_PIN" ->
                    "Envie um pin estático para esse ponto comum. Localização ao vivo não serve"
                            + " para o cadastro.";
            case "REVIEW" ->
                    "Revise o resumo. Envie CONTINUAR para receber uma confirmação nova, CORRIGIR"
                            + " ou ABANDONAR.";
            default ->
                    "Envie seu convite para iniciar ou CONTINUAR CADASTRO para retomar. Para ajuda,"
                            + " envie AJUDA.";
        };
    }

    static String review(ObjectNode draft, String marker) {
        String action = draft.path("action").asText();
        StringBuilder text = new StringBuilder();
        switch (action) {
            case "REQUEST_LINK" -> {
                text.append("Solicitação de ")
                        .append(draft.path("passengerName").asText())
                        .append(" ao motorista ")
                        .append(draft.path("driverName").asText())
                        .append(". ");
                for (JsonNode selection : draft.path("selections")) {
                    text.append(selection.path("name").asText())
                            .append(
                                    selection.path("direction").asText().equals("OUTBOUND")
                                            ? " (ida)"
                                            : " (volta)")
                            .append(" às ")
                            .append(selection.path("local_time").asText())
                            .append(", dias ")
                            .append(selection.path("selectedDays"))
                            .append(". ");
                }
                text.append("Avisos de aproximação: ")
                        .append(draft.path("approachMessages").asBoolean() ? "sim" : "não")
                        .append("; lembretes de presença: ")
                        .append(draft.path("attendanceReminders").asBoolean() ? "sim" : "não")
                        .append(". ");
            }
            case "PROPOSE_PICKUP" ->
                    text.append("Solicitar correção do seu ponto ao motorista ")
                            .append(draft.path("driverName").asText())
                            .append(
                                    ". O ponto atual continua válido até aprovação. Saídas"
                                            + " iniciadas conservam o roteiro anterior. ");
            case "APPROVE_LINK" ->
                    text.append("Aprovar vínculo e ponto de ")
                            .append(draft.path("passengerName").asText())
                            .append(". Confira o acesso e a segurança física do embarque. ");
            case "APPROVE_PICKUP" ->
                    text.append("Aprovar novo ponto de ")
                            .append(draft.path("passengerName").asText())
                            .append(". Saídas já iniciadas mantêm o ponto anterior. ");
            case "RECTIFY" ->
                    text.append("Pedir a ")
                            .append(draft.path("passengerName").asText())
                            .append(" que corrija o ponto proposto. Nenhum ponto será aprovado. ");
            case "CLOSE_LINK" ->
                    text.append(
                                    draft.path("operation").asText().equals("REJECTED")
                                            ? "Recusar solicitação de "
                                            : "Encerrar vínculo com ")
                            .append(draft.path("passengerName").asText())
                            .append(
                                    ". A participação habitual será encerrada; saídas iniciadas"
                                            + " conservam o snapshot. ");
            case "PUBLISH_COMMON" ->
                    text.append("Publicar o ponto comum ")
                            .append(draft.path("pointLabel").asText())
                            .append(" para seus passageiros. ");
            default -> throw new IllegalStateException("WA_PASSENGER_REVIEW");
        }
        if (draft.has("selectionSummary")) {
            text.append(draft.path("selectionSummary").asText()).append(" ");
        }
        if (draft.has("pointLabel")) {
            text.append("Ponto: ").append(draft.path("pointLabel").asText()).append(". ");
        }
        if (draft.has("reference")) {
            text.append("Referência: ").append(draft.path("reference").asText()).append(". ");
        }
        if (draft.has("mapUrl")) {
            text.append("Confira o pin: ").append(draft.path("mapUrl").asText()).append(". ");
        }
        return text.append("Para confirmar este resumo em até 5 minutos, responda CONFIRMAR ")
                .append(marker)
                .append(". Use CORRIGIR ou ABANDONAR para revisar.")
                .toString();
    }

    private static String routines(ObjectNode draft) {
        if (draft.path("routineChoicesBlocked").asBoolean()) {
            return "Há mais de oito rotinas no catálogo. Nenhuma seleção foi oferecida nesta"
                       + " pergunta. Peça suporte para revisar quais rotinas participam deste"
                       + " cadastro.";
        }
        StringBuilder text =
                new StringBuilder(
                        "Quais rotinas você utiliza? Envie os números separados por vírgula. ");
        int index = 0;
        for (JsonNode routine : draft.path("routineChoices")) {
            text.append(++index)
                    .append(": ")
                    .append(routine.path("name").asText())
                    .append(
                            routine.path("direction").asText().equals("OUTBOUND")
                                    ? " (ida)"
                                    : " (volta)")
                    .append(" às ")
                    .append(routine.path("local_time").asText())
                    .append(". ");
        }
        if (index == 0) {
            text.append(
                    "Não há uma lista selecionável neste momento. O motorista pode precisar"
                            + " publicar uma rotina ativa ou reduzir o catálogo. Peça orientação ao"
                            + " suporte.");
        }
        return text.toString().strip();
    }

    private static String points(ObjectNode draft) {
        StringBuilder text =
                new StringBuilder(
                        "Envie um pin estático do ponto onde a van deve buscar você. Se estiver em"
                            + " outro lugar, escolha o ponto no mapa antes de enviar. Localização"
                            + " ao vivo e endereço ambíguo precisam de um pin fixo. ");
        if (draft.path("commonChoicesBlocked").asBoolean()) {
            text.append(
                    "O catálogo tem mais de oito pontos e não cabe nesta pergunta. Envie seu"
                        + " próprio pin fixo ou peça ajuda ao suporte para selecionar um ponto"
                        + " publicado. ");
        }
        int index = 0;
        for (JsonNode point : draft.path("commonChoices")) {
            text.append("PONTO ")
                    .append(++index)
                    .append(": ")
                    .append(point.path("name").asText())
                    .append(". ");
        }
        return text.toString().strip();
    }

    private static String menu(ObjectNode draft) {
        if (draft.path("catalogBlocked").asBoolean()) {
            return "A lista excede o tamanho seguro desta conversa. Nenhuma opção foi oferecida."
                    + " Peça suporte para organizar as pendências.";
        }
        StringBuilder text = new StringBuilder();
        if (draft.has("notice")) {
            text.append(draft.path("notice").asText()).append(" ");
        }
        text.append("Passageiros, página ").append(draft.path("page").asInt() + 1).append(": ");
        int index = 0;
        for (JsonNode link : draft.path("linkChoices")) {
            text.append(++index)
                    .append(": ")
                    .append(link.path("passengerName").asText())
                    .append(
                            link.path("state").asText().equals("REQUESTED")
                                    ? " (solicitação)"
                                    : " (vínculo ativo)")
                    .append(link.path("pickupId").isNull() ? "" : " (ponto pendente)")
                    .append(". ");
        }
        if (index == 0) {
            text.append("nenhuma solicitação ou vínculo ativo. ");
        }
        if (draft.path("hasNext").asBoolean()) {
            text.append("Há mais passageiros: envie PRÓXIMA. ");
        }
        if (draft.path("hasPrevious").asBoolean()) {
            text.append("Para voltar à página anterior, envie ANTERIOR. ");
        }
        return text.append(
                        "Use APROVAR, RECUSAR, RETIFICAR ou ENCERRAR seguido do número. Para"
                                + " publicar um ponto compartilhado, envie PONTO COMUM.")
                .toString();
    }
}
