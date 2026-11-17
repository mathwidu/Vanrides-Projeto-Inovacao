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
    static final String ATTACH_LOCATION =
            "No WhatsApp do celular, toque no clipe ou no +, escolha Localização e envie o ponto onde a van"
                    + " deve buscar você.";
    static final String TYPED_POINT =
            "Não consegui usar esse ponto. Envie a localização pelo WhatsApp do celular.\n\n";
    static final WaPrompt APPROACH =
            WaPrompt.yesNo("Você deseja receber mensagens quando a van estiver se aproximando?");
    static final WaPrompt REMINDER =
            WaPrompt.yesNo("Você deseja receber lembretes para informar sua presença?");

    private WaPassengerTexts() {}

    static WaPrompt driverConfirm(String driverName) {
        return new WaPrompt(
                "Seu convite é do motorista "
                        + driverName
                        + ". Posso ajudar você a escolher os horários, indicar o local de embarque e decidir quais avisos receber."
                        + "\nVocê quer se cadastrar com este motorista?",
                WaPrompt.Kind.BUTTONS,
                List.of(
                        new WaPrompt.Option("YES", "Sim", List.of()),
                        new WaPrompt.Option("NO", "Não", List.of())));
    }

    static String inviteLead(ObjectNode draft) {
        if (draft.path("addingRoutines").asBoolean()) return "Seu cadastro com "
                + draft.path("driverName").asText() + " foi mantido. Escolha os novos trajetos.\n\n";
        return "Seu convite é do motorista " + draft.path("driverName").asText()
                + ". Vou ajudar você a indicar seu embarque. Se o convite não for para você, envie ABANDONAR.\n\n";
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

    static String routineBlock(JsonNode routine, JsonNode days) {
        return routine.path("name").asText() + "\n"
                + (routine.path("direction").asText().equals("OUTBOUND") ? "→ Ida" : "← Volta")
                + " às " + clock(routine.path("local_time").asText())
                + (days.isArray() && !days.isEmpty() ? "\nDias: " + WaPassengerAnswers.dayNames(days) : "");
    }

    static String prompt(ObjectNode draft) {
        String stage = draft.path("stage").asText();
        return switch (stage) {
            case "DRIVER_CONFIRM" ->
                    driverConfirm(draft.path("driverName").asText()).text() + NAVIGATION_HINT;
            case "NAME" -> "Como você prefere ser chamado? Mande seu nome, como Ana Silva.";
            case "NOTICE" ->
                    "Antes de continuar, um aviso sobre seus dados."
                        + "\n\nPara organizar o seu transporte e viagens, o VanRides usa seu nome,"
                        + " seu número de WhatsApp, esta conversa, seu ponto de embarque e as viagens"
                        + " que você escolher. Depois disso, você escolhe quais avisos quer receber."
                        + "\n\nSe você concorda, responda ACEITO para continuar.";
            case "PREF_APPROACH" -> APPROACH.text();
            case "PREF_REMINDER" -> REMINDER.text();
            case "ROUTINES" -> routines(draft);
            case "DAYS" -> days(draft);
            case "POINT" -> points(draft);
            case "EDIT_ROUTINE", "EDIT_POINT_SELECTION" -> WaPassengerPoints.editPrompt(draft);
            case "REFERENCE" ->
                    "É na entrada principal, portão lateral ou outro acesso? Envie uma referência"
                        + " curta, ou PULAR se o pin já estiver claro.\n\nSe o pin não corresponde"
                        + " ao acesso, envie CORRIGIR PONTO.";
            case "DRIVER_MENU" -> menu(draft);
            case "COMMON_NAME" ->
                    "Qual é o nome do ponto comum que você deseja publicar aos seus passageiros?";
            case "COMMON_PIN" ->
                    "Envie um pin estático para esse ponto comum.\n\n"
                            + WaConversationResponses.COMPUTER_POINT;
            case "REVIEW" -> reviewHelp(draft);
            default ->
                    "Envie seu convite para iniciar ou CONTINUAR CADASTRO para retomar.\n\nPara"
                            + " ajuda, envie AJUDA.";
        };
    }

    static String review(ObjectNode draft, String marker) {
        String action = draft.path("addingRoutines").asBoolean() ? "REQUEST_LINK" : draft.path("action").asText();
        List<String> sections = new ArrayList<>();
        switch (action) {
            case "REQUEST_LINK" -> {
                sections.add(
                        (draft.path("addingRoutines").asBoolean() ? "Novos trajetos de " : "Solicitação de ")
                                + draft.path("passengerName").asText()
                                + " ao motorista "
                                + draft.path("driverName").asText()
                                + ".");
                List<String> lines = new ArrayList<>();
                for (JsonNode selection : draft.path("selections")) {
                    lines.add((draft.path("routePoints").asBoolean() ? (lines.size()+1) + ": " : "• ")
                            + routineBlock(selection, selection.path("selectedDays"))
                            + (draft.path("routePoints").asBoolean() ? "\n" + WaPassengerPoints.details(selection.path("pickup")) : ""));
                }
                if (!lines.isEmpty()) {
                    sections.add(String.join("\n\n", lines));
                }
                if (!draft.path("addingRoutines").asBoolean()) sections.add(
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
                            "Conferir o ponto de "
                                    + draft.path("passengerName").asText()
                                    + ".\nConfira o acesso e a segurança física do embarque.");
            case "APPROVE_PICKUP" ->
                    sections.add(
                            (draft.path("keepingPoint").asBoolean() ? "Aprovar os novos trajetos e manter o ponto de " : "Aprovar novo ponto e trajetos pendentes de ")
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
        if (draft.path("routePoints").asBoolean() && !action.equals("REQUEST_LINK")) {
            for (var selected : draft.path("selections")) sections.add(routineBlock(selected, selected.path("selectedDays"))
                    + "\n" + WaPassengerPoints.details(selected.path("pickup")));
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
        if (signupReview(draft)) {
            sections.add("Se estiver tudo certo, envie CONFIRMAR em até 5 minutos. O motorista vai conferir seu pedido.");
            sections.add("Para mudar alguma informação, envie AJUSTAR. O cadastro recomeça pelo seu nome."
                    + (draft.path("addingRoutines").asBoolean() ? " Seus avisos e trajetos anteriores serão mantidos."
                    : " Para escolher avisos, envie AVISOS TODOS, AVISOS CHEGADA, AVISOS PRESENÇA ou AVISOS NENHUM."));
        } else {
            String approval = approvalReply(action);
            String confirm = "Para confirmar este resumo em até 5 minutos, responda CONFIRMAR"
                    + (marker.isEmpty() ? "" : " " + marker) + ".";
            sections.add(approval.isEmpty() ? confirm + "\nUse CORRIGIR ou ABANDONAR para revisar."
                    : "Responda a esta mensagem com " + (approval.equals("pedir correcao")
                            ? "PEDIR CORREÇÃO" : approval.toUpperCase(java.util.Locale.ROOT))
                    + " em até 5 minutos. Se preferir, envie CONFIRMAR " + marker + ".");
            if (action.equals("APPROVE_LINK") || action.equals("APPROVE_PICKUP")) {
                sections.add("Se o ponto precisar mudar, envie PASSAGEIROS e escolha RETIFICAR para o pedido.");
            }
        }
        return String.join("\n\n", sections);
    }

    static boolean signupReview(ObjectNode draft) {
        return draft.path("simpleSignup").asBoolean()
                && (draft.path("addingRoutines").asBoolean() || draft.path("action").asText().equals("REQUEST_LINK"));
    }

    static String approvalReply(String action) {
        return switch (action) {
            case "APPROVE_LINK" -> "aprovar embarque";
            case "APPROVE_PICKUP" -> "aprovar ponto";
            case "RECTIFY" -> "pedir correcao";
            default -> "";
        };
    }

    private static String reviewHelp(ObjectNode draft) {
        JsonNode confirmation = draft.path("confirmation");
        String how =
                confirmation.isMissingNode()
                        ? "Envie CONTINUAR para receber o resumo novamente."
                        : confirmation.has("markerHash")
                                ? "Para confirmar, responda CONFIRMAR com o código do último"
                                        + " resumo que enviei.\nSe o código expirou ou você não o"
                                        + " encontra, envie CONTINUAR para receber um resumo novo."
                                : "Para confirmar, responda CONFIRMAR.\nSe passaram mais de 5"
                                        + " minutos, envie CONTINUAR para receber um resumo novo.";
        return how + (signupReview(draft) ? "\n\nUse AJUSTAR para mudar algo ou ABANDONAR para cancelar."
                : "\n\nUse CORRIGIR para mudar algo ou ABANDONAR para cancelar.");
    }

    private static String days(ObjectNode draft) {
        JsonNode routine = draft.path("selections").path(draft.path("dayIndex").asInt());
        return WaPassengerAnswers.dayPrompt(
                                routineBlock(routine, com.fasterxml.jackson.databind.node.MissingNode.getInstance())
                                        + "\n\nEm quais dias você participa?",
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
                            + routineBlock(routine, routine.path("weekdays")));
        }
        String head = "Quais trajetos você utiliza? Envie os números separados por vírgula, como 1,2.";
        if (draft.path("addingRoutines").asBoolean()) head = lines.isEmpty()
                ? "Não há novos trajetos disponíveis. Seus trajetos e seu pedido atual foram mantidos."
                    + " Para revisar o embarque, envie ABANDONAR e depois CORRIGIR PONTO."
                : "Quais novos trajetos você quer acrescentar? Envie os números separados por vírgula, como 1,2.";
        if (lines.isEmpty()) {
            if (draft.path("addingRoutines").asBoolean()) return head;
            return head
                    + "\n\nNão há uma lista selecionável neste momento. O motorista pode precisar"
                    + " publicar uma rotina ativa ou reduzir o catálogo. Peça orientação ao"
                    + " suporte.";
        }
        return head + "\n\n" + String.join("\n\n", lines);
    }

    private static String points(ObjectNode draft) {
        if (draft.path("routePoints").asBoolean()) return WaPassengerPoints.prompt(draft);
        StringBuilder text =
                new StringBuilder(
                        ATTACH_LOCATION);
        if (draft.has("reusablePoint")) {
            JsonNode point = draft.path("reusablePoint");
            text.insert(0, "Quer usar o mesmo ponto? Confira: https://www.google.com/maps?q="
                    + point.path("latitude").asDouble() + "," + point.path("longitude").asDouble()
                    + "\nEnvie MESMO PONTO para mantê-lo, ou envie outro ponto abaixo.\n\n");
        }
        if (draft.path("simpleSignup").asBoolean() && draft.path("selections").size() == 1) {
            JsonNode selection = draft.path("selections").path(0);
            text.insert(0, "Seu trajeto:\n" + routineBlock(selection, selection.path("selectedDays"))
                    + "\nPara mudar os dias, envie ALTERAR DIAS.\n\n");
        }
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
