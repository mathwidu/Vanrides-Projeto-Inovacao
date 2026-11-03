package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import java.util.TreeSet;
import java.util.UUID;

/** Only the authenticated inbox is parsed. No worker-supplied business value enters this wizard. */
final class WaDriverWizard {
    private static final List<String> FIELDS = List.of("NAME", "DIRECTION", "DAYS", "TIME", "TIMEZONE", "DESTINATION", "REVIEW");
    private final WaDriverTargets targets;
    WaDriverWizard(WaDriverTargets targets) { this.targets = targets; }
    static String text(WaInboxAccess.Context event) { return WaConversationPolicy.normalize(event.normalizedPayload().path("text").asText()); }
    static boolean starts(String text) {
        return List.of("rotinas", "menu motorista", "configurar rotina", "criar rotina", "criar", "convidar", "revogar", "resumo").contains(text)
            || text.matches("(?:editar|ordem|pausar|retomar|cancelar|revogar) (?:[1-9][0-9]?|100)");
    }
    ObjectNode advance(UUID actor, WaInboxAccess.Context event, ObjectNode old) {
        ObjectNode d = old.deepCopy(); String text = text(event);
        long revision = d.path("revision").asLong() + 1;
        if (text.equals("abandonar")) return WaDriverDraftStore.json();
        if (d.isEmpty() || text.equals("rotinas") || text.equals("menu motorista") || text.equals("configurar rotina")) {
            d = menu(actor); if (List.of("rotinas", "menu motorista", "configurar rotina").contains(text)) return d.put("revision", revision);
        }
        d.remove("confirmation");
        String stage = d.path("stage").asText("MENU");
        if (stage.equals("MENU")) visibleMenu(d);
        if (text.equals("corrigir")) {
            if (d.path("action").asText().equals("CONFIGURE_ROUTINE")) d.put("stage", "NAME"); else d = menu(actor);
        }
        else if (text.equals("voltar")) {
            int index = FIELDS.indexOf(stage);
            if (!d.path("action").asText().equals("CONFIGURE_ROUTINE")) d.put("stage", switch (d.path("action").asText()) {
                case "CREATE_INVITE" -> stage.equals("REVIEW") ? "TTL" : "USES";
                case "SET_STOP_ORDER" -> "ORDER"; case "CANCEL_OCCURRENCE" -> "DATE"; default -> "MENU";
            }); else d.put("stage", index > 0 ? FIELDS.get(index - 1) : "MENU");
        } else if (text.equals("continuar")) { /* Re-emit this question or issue a fresh review marker. */ }
        else switch (stage) {
            case "MENU" -> select(actor, d, text);
            case "NAME" -> {
                String name = name(event.normalizedPayload().path("text").asText());
                if (text.equals("manter") && d.has("name") || !text.equals("manter") && name != null) {
                    if (!text.equals("manter")) d.put("name", name);
                    d.put("stage", "DIRECTION");
                }
            }
            case "DIRECTION" -> {
                if (List.of("ida", "volta").contains(text)) d.put("direction", text.equals("ida") ? "OUTBOUND" : "RETURN").put("stage", "DAYS");
            }
            case "DAYS" -> {
                ArrayNode days = days(text);
                if (days != null) { d.set("weekdays", days); d.put("stage", "TIME"); }
            }
            case "TIME" -> {
                if (text.matches("(?:[01][0-9]|2[0-3]):[0-5][0-9]")) d.put("time", LocalTime.parse(text).toString()).put("stage", "TIMEZONE");
            }
            case "TIMEZONE" -> {
                String zone = Map.of("manter", d.path("timezone").asText("America/Sao_Paulo"), "brasilia", "America/Sao_Paulo",
                    "manaus", "America/Manaus", "rio branco", "America/Rio_Branco", "noronha", "America/Noronha").get(text);
                if (zone != null) d.put("timezone", zone).put("stage", "DESTINATION");
            }
            case "DESTINATION" -> {
                if (event.normalizedKind().equals("PIN")) {
                    targets.pin(actor, event.eventId().toString()); d.put("destinationEvent", event.eventId().toString()).put("stage", "REVIEW");
                } else if (text.equals("manter") && !d.path("creating").asBoolean()) d.put("stage", "REVIEW");
            }
            case "ORDER" -> order(d, text);
            case "DATE" -> {
                try {
                    LocalDate date = LocalDate.parse(text);
                    if (date.getYear() >= 2000 && date.getYear() <= 2100) {
                        d.put("date", date.toString()).put("stage", "REVIEW");
                        d.put("occurrenceId", targets.occurrence(actor, d));
                    }
                } catch (java.time.DateTimeException invalid) { /* Keep the question. */ }
            }
            case "USES" -> {
                if (text.equals("padrao")) d.put("maxUses", 1).put("ttlMinutes", 1440).put("stage", "REVIEW");
                else if (text.matches("(?:[1-9]|10)")) d.put("maxUses", Integer.parseInt(text)).put("stage", "TTL");
            }
            case "TTL" -> {
                if (text.equals("padrao")) d.put("ttlMinutes", 1440).put("stage", "REVIEW");
                else if (text.matches("[0-9]{1,4}") && Integer.parseInt(text) >= 5 && Integer.parseInt(text) <= 1440)
                    d.put("ttlMinutes", Integer.parseInt(text)).put("stage", "REVIEW");
            }
            default -> { /* REVIEW: SIM or an old marker never applies; emit a new bound review. */ }
        }
        return d.put("revision", revision);
    }
    ObjectNode menu(UUID actor) {
        ObjectNode d = WaDriverDraftStore.json().put("stage", "MENU");
        d.set("routineChoices", targets.routines(actor)); d.set("inviteChoices", targets.invites(actor)); return visibleMenu(d);
    }
    private static ObjectNode visibleMenu(ObjectNode draft) {
        if (draft.path("catalogBlocked").asBoolean() || !WaDriverTexts.menuFits(draft)) {
            draft.putArray("routineChoices"); draft.putArray("inviteChoices"); draft.put("catalogBlocked", true);
        }
        return draft;
    }
    private static String name(String raw) {
        if (raw.codePoints().anyMatch(c -> Character.isISOControl(c) || Character.getType(c) == Character.FORMAT
                || Character.getType(c) == Character.LINE_SEPARATOR || Character.getType(c) == Character.PARAGRAPH_SEPARATOR)) return null;
        String value = java.text.Normalizer.normalize(raw, java.text.Normalizer.Form.NFC)
            .replaceAll("^[\\p{Z}\\s]+|[\\p{Z}\\s]+$", "");
        if (value.isEmpty() || value.codePointCount(0, value.length()) > 80
                || value.codePoints().allMatch(c -> Character.isWhitespace(c) || Character.isSpaceChar(c)
                    || Character.getType(c) == Character.NON_SPACING_MARK || Character.getType(c) == Character.ENCLOSING_MARK)) return null;
        return value;
    }
    private void select(UUID actor, ObjectNode d, String text) {
        if (List.of("criar", "criar rotina").contains(text)) {
            d.removeAll(); d.put("stage", "NAME").put("action", "CONFIGURE_ROUTINE").put("creating", true)
                .put("routineId", UUID.randomUUID().toString()).put("timezone", "America/Sao_Paulo"); return;
        }
        if (text.equals("convidar")) { d.removeAll(); d.put("stage", "USES").put("action", "CREATE_INVITE"); return; }
        if (!text.matches("(?:editar|ordem|pausar|retomar|cancelar|revogar) (?:[1-9][0-9]?|100)")) return;
        String[] parts = text.split(" "); boolean invite = parts[0].equals("revogar");
        JsonNode choices = d.path(invite ? "inviteChoices" : "routineChoices"); int index = Integer.parseInt(parts[1]) - 1;
        if (index >= choices.size()) return;
        JsonNode choice = choices.get(index);
        if (invite) {
            d.removeAll(); d.put("stage", "REVIEW").put("action", "REVOKE_INVITE").put("inviteId", choice.path("id").asText())
                .put("inviteVersion", choice.path("version").asLong()); d.set("inviteDisplay", choice.deepCopy()); return;
        }
        ObjectNode routine = targets.routine(actor, UUID.fromString(choice.path("id").asText()));
        if (routine.path("version").asLong() != choice.path("version").asLong()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        d.removeAll(); d.put("creating", false).put("routineId", routine.path("id").asText()).put("routineVersion", routine.path("version").asLong());
        d.set("routineDisplay", targets.display(routine));
        switch (parts[0]) {
            case "editar" -> {
                d.put("action", "CONFIGURE_ROUTINE").put("stage", "NAME").put("name", routine.path("name").asText())
                    .put("timezone", routine.path("timezone").asText());
            }
            case "ordem" -> {
                var stopChoices = targets.members(actor, UUID.fromString(routine.path("id").asText()));
                if (!WaDriverTexts.orderFits(stopChoices)) {
                    d.removeAll(); d.setAll(menu(actor)); d.put("notice", WaDriverTexts.ORDER_LIMIT); visibleMenu(d);
                } else { d.put("action", "SET_STOP_ORDER").put("stage", "ORDER"); d.set("stopChoices", stopChoices); }
            }
            case "pausar", "retomar" -> d.put("action", "SET_ROUTINE_STATE").put("stage", "REVIEW").put("routineState", parts[0].equals("pausar") ? "PAUSED" : "ACTIVE");
            case "cancelar" -> d.put("action", "CANCEL_OCCURRENCE").put("stage", "DATE").put("occurrenceId", UUID.randomUUID().toString());
            default -> throw WaProblem.invalid();
        }
    }
    private static ArrayNode days(String text) {
        if (text.equals("segunda a sexta")) text = "1,2,3,4,5";
        if (text.equals("todos os dias")) text = "1,2,3,4,5,6,7";
        if (!text.matches("[1-7](?:[, ]+[1-7]){0,6}")) return null;
        List<String> values = Arrays.asList(text.split("[, ]+")); TreeSet<String> unique = new TreeSet<>(values);
        if (unique.size() != values.size()) return null;
        ArrayNode days = WaDriverDraftStore.options(); unique.forEach(day -> days.add(Integer.parseInt(day))); return days;
    }
    private static void order(ObjectNode d, String text) {
        int size = d.path("stopChoices").size();
        if (size == 0 && text.equals("sem pontos")) { d.putArray("memberOrder"); d.putArray("orderNumbers"); d.put("stage", "REVIEW"); return; }
        if (!text.matches("(?:[1-9][0-9]?|100)(?:[, ]+(?:[1-9][0-9]?|100)){0,99}")) return;
        List<Integer> order = Arrays.stream(text.split("[, ]+")).map(Integer::parseInt).toList();
        if (order.size() != size || new TreeSet<>(order).size() != size || order.stream().anyMatch(i -> i > size)) return;
        ArrayNode ids = d.putArray("memberOrder"), numbers = d.putArray("orderNumbers");
        order.forEach(i -> { ids.add(d.path("stopChoices").get(i - 1).path("id").asText()); numbers.add(i); }); d.put("stage", "REVIEW");
    }
}
