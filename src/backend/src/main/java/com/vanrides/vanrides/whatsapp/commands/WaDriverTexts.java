package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Map;

/** Human-facing copy lives in outbox, never in context labels. */
final class WaDriverTexts {
    private WaDriverTexts() { }
    static final String MENU = "Envie:\nCRIAR — cadastrar um trajeto\nCONVIDAR — receber um link para um passageiro\nRESUMO — conferir trajetos e convites"
        + "\n\nPara gerenciar: EDITAR N, ORDEM N, PAUSAR N, RETOMAR N, CANCELAR N ou REVOGAR N. Use o número da lista."
        + "\nVOLTAR retorna uma etapa. CORRIGIR recomeça. ABANDONAR descarta o rascunho.";
    static final String LIST_LIMIT = "A lista não foi exibida integralmente: excede 2000 caracteres/100 opções ou contém identificação sem formato suportado. "
        + "Nenhuma opção numerada está disponível. Para atendimento, envie FALAR COM SUPORTE.";
    static final String ORDER_LIMIT = "Não foi possível exibir todos os pontos e sua nova ordem dentro do limite da mensagem. "
        + "Nenhuma reordenação foi preparada. Para atendimento, envie FALAR COM SUPORTE.";
    static final Map<String, String> ZONES = Map.of("America/Sao_Paulo", "Brasília", "America/Manaus", "Manaus",
        "America/Rio_Branco", "Rio Branco", "America/Noronha", "Noronha");
    static String prompt(ObjectNode draft) {
        return switch (draft.path("stage").asText("MENU")) {
            case "NAME" -> "Como quer chamar este trajeto que se repete? Pode usar, por exemplo, Faculdade manhã. Em edição, responda MANTER para usar o nome atual.";
            case "DIRECTION" -> "Qual é o sentido desta rotina? Envie IDA ou VOLTA. Se já souber os dias e o horário, pode enviar tudo junto, por exemplo IDA, SEGUNDA A SEXTA, 07:30.";
            case "DAYS" -> "Em quais dias? Use 1=segunda até 7=domingo, como 1,2,3,4,5, ou SEGUNDA A SEXTA.";
            case "TIME" -> "Qual é o horário local? Responda HH:mm, por exemplo 07:30.";
            case "TIMEZONE" -> "Fuso atual: " + ZONES.getOrDefault(draft.path("timezone").asText(), "Brasília")
                + ". Qual fuso usar? MANTER, BRASÍLIA, MANAUS, RIO BRANCO ou NORONHA. O resumo mostrará sua escolha.";
            case "DESTINATION" -> "Onde termina este trajeto? No celular, use o clipe ou o botão +, toque em Localização e envie o ponto fixo do destino.\n\n"
                + WaConversationResponses.COMPUTER_POINT + "\n\nFuso: " + ZONES.getOrDefault(draft.path("timezone").asText(), "Brasília")
                + ". Para mudar, envie FUSO seguido da opção: BRASÍLIA, MANAUS, RIO BRANCO ou NORONHA."
                + (draft.path("creating").asBoolean() ? "" : " Envie MANTER para usar o destino atual.");
            case "ORDER" -> "Pontos de " + routine(draft.path("routineDisplay")) + ": " + stops(draft.path("stopChoices"), null)
                + " Informe todos pelos números, na ordem desejada, sem repetir nem omitir. Exemplo: 2,1. Sem pontos, responda SEM PONTOS.";
            case "DATE" -> "Qual data cancelar? Use AAAA-MM-DD. Isso não inicia viagem nem altera presença.";
            case "USES" -> (draft.path("inviteContactUnavailable").asBoolean() ? shareInstead() + " " : "")
                + "Se o passageiro já escreveu ACEITO RECEBER CONVITES para mim, "
                + "você também pode enviar o WhatsApp dele com DDD e revisar o envio. Para personalizar o convite, informe quantos usos terá, de 1 a 10.";
            case "TTL" -> "Qual validade em minutos? De 5 a 1440. PADRÃO significa 24 horas (1440 minutos).";
            case "REVIEW" -> "Há um resumo aguardando confirmação. Siga a instrução naquele resumo ou envie CONTINUAR para receber um novo.";
            default -> menu(draft);
        };
    }
    static String shareInstead() {
        return "O envio direto está indisponível para esse contato. Envie LINK para compartilhar o convite você mesmo.";
    }
    private static String menu(ObjectNode draft) {
        if (draft.path("catalogBlocked").asBoolean() || !menuFits(draft)) return LIST_LIMIT;
        String notice = draft.path("notice").asText("");
        return bounded((notice.isEmpty() ? "" : notice + " ") + MENU + catalog(draft));
    }
    static boolean menuFits(ObjectNode draft) {
        if (draft.path("routineChoices").size() > 100 || draft.path("inviteChoices").size() > 100) return false;
        try { return (draft.path("notice").asText("") + MENU + catalog(draft)).length() <= 1998; }
        catch (WaProblem invalid) { return false; }
    }
    static boolean orderFits(ArrayNode choices) {
        if (choices.size() > 100) return false;
        try { return stops(choices, null).length() <= 1300; } catch (WaProblem invalid) { return false; }
    }
    private static String catalog(ObjectNode draft) {
        StringBuilder text = new StringBuilder("\n\nSeus trajetos:\n"); int index = 0;
        for (var choice : draft.path("routineChoices")) {
            text.append("\nRotina ").append(++index).append(": ").append(routine(choice)).append("\nEstado: ")
                .append(choice.path("state").asText().equals("ACTIVE") ? "ativa" : "pausada").append(".\n");
        }
        if (index == 0) text.append("Nenhum trajeto cadastrado.\n");
        text.append("\nConvites revogáveis:"); index = 0;
        for (var choice : draft.path("inviteChoices")) text.append("\nConvite ").append(++index).append(": ").append(invite(choice)).append(".");
        if (index == 0) text.append(" nenhum.");
        return text.toString();
    }
    static String summary(ArrayNode routines, ArrayNode invites) {
        ObjectNode draft = WaDriverDraftStore.json(); draft.set("routineChoices", routines); draft.set("inviteChoices", invites);
        if (!menuFits(draft)) return LIST_LIMIT;
        return bounded("Suas rotinas e convites. Presença e execução de viagens ainda não estão disponíveis neste comando."
            + catalog(draft) + "\n\nNenhum código é reexibido.");
    }
    private static String routine(JsonNode row) {
        String direction = row.path("direction").asText();
        if (!java.util.Set.of("OUTBOUND", "RETURN").contains(direction)) throw WaProblem.denied();
        String zone = ZONES.get(row.path("timezone").asText()); if (zone == null) throw WaProblem.denied();
        java.time.LocalTime local = java.time.LocalTime.parse(copy(row.path("localTime").asText(), 8, ""));
        String time = local.format(DateTimeFormatter.ofPattern(local.getSecond() == 0 ? "HH:mm" : "HH:mm:ss"));
        String name = copy(row.path("name").asText(), 80, ""); if (name.isEmpty()) throw WaProblem.denied();
        return name + "\n" + (direction.equals("OUTBOUND") ? "→ IDA" : "← VOLTA")
            + " " + time + "; fuso " + zone
            + (row.path("weekdays").isArray() && !row.path("weekdays").isEmpty()
                ? "\nDias: " + WaPassengerAnswers.dayNames(row.path("weekdays")) : "");
    }
    private static String invite(JsonNode row) {
        return "criado " + time(row.path("createdAt").asText()) + "; válido até " + time(row.path("expiresAt").asText())
            + "; usos " + row.path("useCount").asInt() + "/" + row.path("maxUses").asInt();
    }
    private static String time(String value) {
        return DateTimeFormatter.ofPattern("dd/MM/yyyy HH:mm").withZone(ZoneId.of("America/Sao_Paulo"))
            .format(Instant.parse(value)) + " (Brasília)";
    }
    private static String stops(JsonNode choices, JsonNode order) {
        if (choices.isEmpty()) return "sem pontos; ordem pendente.";
        StringBuilder text = new StringBuilder(); int size = order == null ? choices.size() : order.size();
        for (int i = 0; i < size; i++) {
            JsonNode choice = choices.get(order == null ? i : order.get(i).asInt() - 1);
            if (choice == null) throw WaProblem.denied();
            String name = copy(choice.path("passengerName").asText(""), 80, "Passageiro sem nome");
            String label = copy(choice.path("pointLabel").asText(""), 80, "");
            String reference = copy(choice.path("pointReference").asText(""), 240, "");
            if (name.equals("Passageiro sem nome") && label.isEmpty() && reference.isEmpty()) throw WaProblem.denied();
            if (length(name + label + reference) > 360) throw WaProblem.denied();
            text.append(i + 1).append(": ").append(name).append(" — ")
                .append(label.isEmpty() && reference.isEmpty() ? "ponto sem descrição disponível" : label + (reference.isEmpty() ? "" : "; " + reference)).append(". ");
        }
        return text.toString();
    }
    private static String copy(String value, int max, String fallback) {
        if (value.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.LINE_SEPARATOR || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) throw WaProblem.denied();
        String text = value.replaceAll("[\\p{Z}\\s]+", " ").strip();
        if (length(text) > max) throw WaProblem.denied(); return text.isEmpty() ? fallback : text;
    }
    private static int length(String value) { return value.codePointCount(0, value.length()); }
    // The delivery adapter uses JavaScript string.length, i.e. UTF-16 units.
    private static String bounded(String value) { if (value.length() > 2000) throw WaProblem.denied(); return value; }
    static String review(ObjectNode d, String marker) {
        String description = switch (d.path("action").asText()) {
            case "CONFIGURE_ROUTINE" -> "Confira o trajeto:\n" + d.path("name").asText() + "\n"
                + (d.path("direction").asText().equals("OUTBOUND") ? "→ Ida" : "← Volta") + " às " + d.path("time").asText()
                + "\nDias: " + WaPassengerAnswers.dayNames(d.path("weekdays"))
                + "\nFuso: " + ZONES.getOrDefault(d.path("timezone").asText(), "sua escolha") + ".\n"
                + (d.hasNonNull("destinationEvent") ? "Destino: ponto recebido." : "Destino: ponto atual mantido.");
            case "CREATE_INVITE" -> d.hasNonNull("inviteRecipient")
                ? "Vou enviar um convite individual ao WhatsApp com final " + d.path("inviteLastDigits").asText()
                    + ". O convite vale por 24 horas. Confira o final do número antes de confirmar."
                : "Convite para passageiro: " + d.path("maxUses").asInt() + " uso(s); validade "
                    + d.path("ttlMinutes").asInt() + " minutos. Você receberá a mensagem para encaminhar pelo WhatsApp.";
            case "SET_STOP_ORDER" -> "Nova ordem de " + routine(d.path("routineDisplay")) + ": " + stops(d.path("stopChoices"), d.path("orderNumbers"));
            case "SET_ROUTINE_STATE" -> "A rotina " + routine(d.path("routineDisplay")) + " ficará " + (d.path("routineState").asText().equals("PAUSED") ? "PAUSADA" : "ATIVA") + ". Viagens existentes serão preservadas.";
            case "CANCEL_OCCURRENCE" -> "Cancelar somente " + routine(d.path("routineDisplay")) + " na data " + d.path("date").asText() + ". Nenhuma presença ou viagem será criada.";
            case "REVOKE_INVITE" -> "Revogar o convite " + invite(d.path("inviteDisplay")) + ". O código deixará de permitir novos pedidos; vínculos existentes serão preservados.";
            default -> throw new IllegalStateException("WA_DRIVER_ACTION_INVALID");
        };
        if (d.path("action").asText().equals("CONFIGURE_ROUTINE")) {
            return bounded(description + "\n\nUse Responder nesta mensagem e escreva SALVAR ROTINA. "
                + "Se não conseguir citar, envie CONFIRMAR " + marker + ". Você tem 5 minutos ou pode enviar CONTINUAR para renovar o resumo.");
        }
        return bounded(description + " Para aplicar, responda CONFIRMAR " + marker + ". Esse marcador vale por 5 minutos e somente para este resumo.");
    }
}
