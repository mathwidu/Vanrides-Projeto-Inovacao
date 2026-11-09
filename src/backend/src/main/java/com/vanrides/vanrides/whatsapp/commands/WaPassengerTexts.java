package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.ArrayList;
import java.util.List;

final class WaPassengerTexts {
    static final String NOT_UNDERSTOOD = "Não entendi sua resposta.\n\n";
    static final String NOTICE_RENEWED =
            "O prazo para aceitar o aviso terminou. Enviamos o aviso novamente.\n\n";
    static final String NAVIGATION_HINT =
            "\n\nA qualquer momento do cadastro, envie VOLTAR para voltar uma etapa ou ABANDONAR"
                    + " para cancelar.";
    static final WaPrompt APPROACH =
            WaPrompt.yesNo("Você deseja receber mensagens quando a van estiver se aproximando?");
    static final WaPrompt REMINDER =
            WaPrompt.yesNo("Você deseja receber lembretes para informar sua presença?");

    private WaPassengerTexts() {}

    static WaPrompt driverConfirm(String driverName) {
        return new WaPrompt(
                "Seu convite é do motorista "
                        + driverName
                        + ".\nVocê quer se cadastrar com este motorista?",
                WaPrompt.Kind.BUTTONS,
                List.of(
                        new WaPrompt.Option("YES", "Sim", List.of()),
                        new WaPrompt.Option("NO", "Não", List.of())));
    }

    static String clock(String time) {
        return time.matches("\\d\\d:\\d\\d:00") ? time.substring(0, 5) : time;
    }

    static String routineLabel(String name, String direction, String time) {
        return name
                + (direction.equals("OUTBOUND") ? " (ida)" : " (volta)")
                + " às "
                + clock(time);
    }

    static String routineLine(String name, String direction, String time, JsonNode days) {
        return "• " + routineLabel(name, direction, time) + ", dias " + WaPassengerAnswers.dayNames(days);
    }

    static String prompt(ObjectNode draft) {
        String stage = draft.path("stage").asText();
        return switch (stage) {
            case "DRIVER_CONFIRM" ->
                    driverConfirm(draft.path("driverName").asText()).text() + NAVIGATION_HINT;
            case "NAME" -> "Como você prefere ser chamado?";
            case "NOTICE" ->
                    "Usaremos seu nome, conversa, ponto de embarque e participação nas rotinas para"
                        + " organizar seu transporte. Os avisos serão escolhidos separadamente."
                        + "\n\nVocê aceita o aviso de privacidade VanRides wa-mvp-v1? Responda"
                        + " ACEITO para continuar.";
            case "PREF_APPROACH" -> APPROACH.text();
            case "PREF_REMINDER" -> REMINDER.text();
            case "ROUTINES" -> routines(draft);
            case "DAYS" -> days(draft);
            case "POINT" -> points(draft);
            case "REFERENCE" ->
                    "É na entrada principal, portão lateral ou outro acesso? Envie uma referência"
                        + " curta, ou PULAR se o pin já estiver claro.\n\nSe o pin não corresponde"
                        + " ao acesso, envie CORRIGIR PONTO.";
            case "DRIVER_MENU" -> menu(draft);
            case "COMMON_NAME" ->
                    "Qual é o nome do ponto comum que você deseja publicar aos seus passageiros?";
            case "COMMON_PIN" ->
                    "Envie um pin estático para esse ponto comum.\n\nLocalização ao vivo não serve"
                            + " para o cadastro.";
            case "REVIEW" -> reviewHelp(draft.path("confirmation"));
            default ->
                    "Envie seu convite para iniciar ou CONTINUAR CADASTRO para retomar.\n\nPara"
                            + " ajuda, envie AJUDA.";
        };
    }

    static String review(ObjectNode draft, String marker) {
        String action = draft.path("action").asText();
        List<String> sections = new ArrayList<>();
        switch (action) {
            case "REQUEST_LINK" -> {
                sections.add(
                        "Solicitação de "
                                + draft.path("passengerName").asText()
                                + " ao motorista "
                                + draft.path("driverName").asText()
                                + ".");
                List<String> lines = new ArrayList<>();
                for (JsonNode selection : draft.path("selections")) {
                    lines.add(
                            routineLine(
                                    selection.path("name").asText(),
                                    selection.path("direction").asText(),
                                    selection.path("local_time").asText(),
                                    selection.path("selectedDays")));
                }
                if (!lines.isEmpty()) {
                    sections.add(String.join("\n", lines));
                }
                sections.add(
                        "Avisos de aproximação: "
                                + (draft.path("approachMessages").asBoolean() ? "sim" : "não")
                                + "\nLembretes de presença: "
                                + (draft.path("attendanceReminders").asBoolean() ? "sim" : "não"));
            }
            case "PROPOSE_PICKUP" ->
                    sections.add(
                            "Solicitar correção do seu ponto ao motorista "
                                    + draft.path("driverName").asText()
                                    + ".\nO ponto atual continua válido até aprovação. Saídas"
                                    + " iniciadas conservam o roteiro anterior.");
            case "APPROVE_LINK" ->
                    sections.add(
                            "Aprovar vínculo e ponto de "
                                    + draft.path("passengerName").asText()
                                    + ".\nConfira o acesso e a segurança física do embarque.");
            case "APPROVE_PICKUP" ->
                    sections.add(
                            "Aprovar novo ponto de "
                                    + draft.path("passengerName").asText()
                                    + ".\nSaídas já iniciadas mantêm o ponto anterior.");
            case "RECTIFY" ->
                    sections.add(
                            "Pedir a "
                                    + draft.path("passengerName").asText()
                                    + " que corrija o ponto proposto.\nNenhum ponto será"
                                    + " aprovado.");
            case "CLOSE_LINK" ->
                    sections.add(
                            (draft.path("operation").asText().equals("REJECTED")
                                            ? "Recusar solicitação de "
                                            : "Encerrar vínculo com ")
                                    + draft.path("passengerName").asText()
                                    + ".\nA participação habitual será encerrada; saídas iniciadas"
                                    + " conservam o snapshot.");
            case "PUBLISH_COMMON" ->
                    sections.add(
                            "Publicar o ponto comum "
                                    + draft.path("pointLabel").asText()
                                    + " para seus passageiros.");
            default -> throw new IllegalStateException("WA_PASSENGER_REVIEW");
        }
        if (draft.has("selectionSummary")) {
            sections.add(draft.path("selectionSummary").asText());
        }
        List<String> details = new ArrayList<>();
        if (draft.has("pointLabel")) {
            details.add("Ponto: " + draft.path("pointLabel").asText());
        }
        if (draft.has("reference")) {
            details.add("Referência: " + draft.path("reference").asText());
        }
        if (draft.has("mapUrl")) {
            details.add("Confira o pin: " + draft.path("mapUrl").asText());
        }
        if (!details.isEmpty()) {
            sections.add(String.join("\n", details));
        }
        sections.add(
                "Para confirmar este resumo em até 5 minutos, responda CONFIRMAR"
                        + (marker.isEmpty() ? "" : " " + marker)
                        + ".\nUse CORRIGIR ou ABANDONAR para revisar.");
        return String.join("\n\n", sections);
    }

    private static String reviewHelp(JsonNode confirmation) {
        String how =
                confirmation.isMissingNode()
                        ? "Envie CONTINUAR para receber o resumo novamente."
                        : confirmation.has("markerHash")
                                ? "Para confirmar, responda CONFIRMAR com o código do último"
                                        + " resumo que enviei.\nSe o código expirou ou você não o"
                                        + " encontra, envie CONTINUAR para receber um resumo novo."
                                : "Para confirmar, responda CONFIRMAR.\nSe passaram mais de 5"
                                        + " minutos, envie CONTINUAR para receber um resumo novo.";
        return how + "\n\nUse CORRIGIR para mudar algo ou ABANDONAR para cancelar.";
    }

    private static String days(ObjectNode draft) {
        JsonNode routine = draft.path("selections").path(draft.path("dayIndex").asInt());
        return WaPassengerAnswers.dayPrompt(
                                "Para "
                                        + routine.path("name").asText()
                                        + ", em quais dias você participa?",
                                routine.path("weekdays"))
                        .text()
                + "\n\nVocê pode enviar vários dias separados por vírgula, por exemplo: Segunda,"
                + " Quarta.";
    }

    private static String routines(ObjectNode draft) {
        if (draft.path("routineChoicesBlocked").asBoolean()) {
            return "Há mais de oito rotinas no catálogo. Nenhuma seleção foi oferecida nesta"
                    + " pergunta.\n\nPeça suporte para revisar quais rotinas participam deste"
                    + " cadastro.";
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode routine : draft.path("routineChoices")) {
            lines.add(
                    (lines.size() + 1)
                            + ": "
                            + routineLabel(
                                    routine.path("name").asText(),
                                    routine.path("direction").asText(),
                                    routine.path("local_time").asText()));
        }
        String head = "Quais rotinas você utiliza? Envie os números separados por vírgula.";
        if (lines.isEmpty()) {
            return head
                    + "\n\nNão há uma lista selecionável neste momento. O motorista pode precisar"
                    + " publicar uma rotina ativa ou reduzir o catálogo. Peça orientação ao"
                    + " suporte.";
        }
        return head + "\n\n" + String.join("\n", lines);
    }

    private static String points(ObjectNode draft) {
        StringBuilder text =
                new StringBuilder(
                        "Envie um pin estático do ponto onde a van deve buscar você. Se estiver em"
                            + " outro lugar, escolha o ponto no mapa antes de enviar.\n\nLocalização"
                            + " ao vivo e endereço ambíguo precisam de um pin fixo.");
        if (draft.path("commonChoicesBlocked").asBoolean()) {
            text.append(
                    "\n\nO catálogo tem mais de oito pontos e não cabe nesta pergunta. Envie seu"
                        + " próprio pin fixo ou peça ajuda ao suporte para selecionar um ponto"
                        + " publicado.");
        }
        List<String> lines = new ArrayList<>();
        for (JsonNode point : draft.path("commonChoices")) {
            lines.add("PONTO " + (lines.size() + 1) + ": " + point.path("name").asText());
        }
        if (!lines.isEmpty()) {
            text.append("\n\nPontos já publicados (responda PONTO e o número):\n")
                    .append(String.join("\n", lines));
        }
        return text.toString();
    }

    private static String menu(ObjectNode draft) {
        if (draft.path("catalogBlocked").asBoolean()) {
            return "A lista excede o tamanho seguro desta conversa. Nenhuma opção foi oferecida."
                    + "\n\nPeça suporte para organizar as pendências.";
        }
        List<String> sections = new ArrayList<>();
        if (draft.has("notice")) {
            sections.add(draft.path("notice").asText());
        }
        List<String> lines = new ArrayList<>();
        lines.add("Passageiros, página " + (draft.path("page").asInt() + 1) + ":");
        int index = 0;
        for (JsonNode link : draft.path("linkChoices")) {
            lines.add(
                    ++index
                            + ": "
                            + link.path("passengerName").asText()
                            + (link.path("state").asText().equals("REQUESTED")
                                    ? " (solicitação)"
                                    : " (vínculo ativo)")
                            + (link.path("pickupId").isNull() ? "" : " (ponto pendente)"));
        }
        if (index == 0) {
            lines.add("Nenhuma solicitação ou vínculo ativo.");
        }
        sections.add(String.join("\n", lines));
        List<String> paging = new ArrayList<>();
        if (draft.path("hasNext").asBoolean()) {
            paging.add("Há mais passageiros: envie PRÓXIMA.");
        }
        if (draft.path("hasPrevious").asBoolean()) {
            paging.add("Para voltar à página anterior, envie ANTERIOR.");
        }
        if (!paging.isEmpty()) {
            sections.add(String.join("\n", paging));
        }
        sections.add(
                "Use APROVAR, RECUSAR, RETIFICAR ou ENCERRAR seguido do número.\nPara publicar um"
                        + " ponto compartilhado, envie PONTO COMUM.");
        return String.join("\n\n", sections);
    }
}
