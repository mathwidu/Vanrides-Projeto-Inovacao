package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.util.List;
import java.util.UUID;

/** One explicit pickup answer per selected routine; sharing coordinates never shares a proposal. */
final class WaPassengerPoints {
    static final List<String> FIELDS = List.of("sourceEvent", "commonId", "commonVersion", "selectionEvent",
            "reference", "pointLabel", "mapUrl", "reusePickupId", "reusePickupVersion");
    private final WaPassengerData data;

    WaPassengerPoints(WaPassengerData data) { this.data = data; }

    void open(ObjectNode draft) {
        clear(draft);
        for (int index = 0; index < draft.path("selections").size(); index++) {
            if (!draft.path("selections").path(index).has("pickup")) {
                draft.put("pointIndex", index).put("stage", "POINT");
                WaPassengerAnswers.catalogue(draft, "commonChoices", data.commons(UUID.fromString(draft.path("driverId").asText())));
                if (draft.has("linkId")) WaPassengerAnswers.catalogue(draft, "approvedChoices",
                        new WaPassengerReviewStore(data).approvedPoints(UUID.fromString(draft.path("linkId").asText())));
                return;
            }
        }
        draft.put("stage", "REVIEW");
    }

    void answer(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        ObjectNode pickup = WaPassengerDraftStore.json();
        if (event.normalizedKind().equals("PIN")) {
            data.pin(actor.identityId(), event.eventId().toString(), event.eventId());
            pickup.put("sourceEvent", event.eventId().toString());
        } else if (text.matches("mesmo ponto(?: (?:[1-9][0-9]?|100))?")) {
            int index = text.equals("mesmo ponto") ? -1 : Integer.parseInt(text.substring(12)) - 1;
            JsonNode points = draft.path("approvedChoices");
            if (index < 0 && points.size() == 1) index = 0;
            if (index >= 0 && index < points.size()) {
                JsonNode point = points.path(index);
                pickup.put("reusePickupId", point.path("id").asText()).put("reusePickupVersion", point.path("version").asLong());
                if (point.hasNonNull("label")) pickup.put("pointLabel", point.path("label").asText());
                if (point.hasNonNull("reference_text")) pickup.put("reference", point.path("reference_text").asText());
            } else if (index < 0 && points.isEmpty() && draft.path("pointIndex").asInt() > 0) {
                pickup = draft.path("selections").path(draft.path("pointIndex").asInt() - 1).path("pickup").deepCopy();
            } else return;
            pickup.put("selectionEvent", event.eventId().toString());
        } else if (text.matches("ponto (?:[1-9][0-9]?|100)")) {
            int index = Integer.parseInt(text.substring(6)) - 1;
            if (index >= draft.path("commonChoices").size()) return;
            JsonNode common = draft.path("commonChoices").path(index);
            pickup.put("commonId", common.path("id").asText()).put("commonVersion", common.path("version").asLong())
                    .put("selectionEvent", event.eventId().toString()).put("pointLabel", common.path("name").asText());
        } else return;
        ((ObjectNode) draft.path("selections").path(draft.path("pointIndex").asInt())).set("pickup", pickup);
        open(draft);
    }

    boolean editReference(WaInboxAccess.Context event, ObjectNode draft, String text) {
        if (!draft.path("routePoints").asBoolean() || !draft.path("stage").asText().equals("REVIEW")) return false;
        if (!text.startsWith("referencia ") && !text.equals("sem referencia")) return false;
        int index = 0;
        String raw = event.normalizedPayload().path("text").asText().strip();
        String value = raw.replaceFirst("(?iu)^refer[eê]ncia[\\p{Z}\\s]+", "");
        if (draft.path("selections").size() > 1) {
            var match = java.util.regex.Pattern.compile("^([1-8])\\s+(.+)$").matcher(value);
            if (!match.matches()) return true;
            index = Integer.parseInt(match.group(1)) - 1;
            value = match.group(2);
        }
        if (index >= draft.path("selections").size()) return true;
        ObjectNode pickup = (ObjectNode) draft.path("selections").path(index).path("pickup");
        if (text.equals("sem referencia")) pickup.remove("reference");
        else {
            String clean = WaPassengerAnswers.clean(value, 160);
            if (clean != null) pickup.put("reference", clean);
        }
        pickup.put("selectionEvent", event.eventId().toString());
        return true;
    }

    ObjectNode pin(UUID actor, WaInboxAccess.Context event, ObjectNode draft, ObjectNode pickup) {
        if (pickup.has("reusePickupId")) {
            if (!pickup.has("selectionEvent") || !Boolean.TRUE.equals(data.jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM wa.integration_inbox s JOIN wa.integration_inbox e ON e.id=?
                    WHERE s.id=? AND s.identity_id=? AND s.instance_id=e.instance_id AND s.provider=e.provider
                    AND s.category='MESSAGE' AND s.state IN ('CLAIMED','COMPLETED'))
                    """, Boolean.class, event.eventId(), UUID.fromString(pickup.path("selectionEvent").asText()), actor))) throw com.vanrides.vanrides.whatsapp.api.WaProblem.denied();
            return new WaPassengerReviewStore(data).reusable(actor, draft, pickup);
        }
        return new WaPassengerValidation(data).proposedPin(actor, event, pickup.deepCopy()
                .put("driverId", draft.path("driverId").asText()));
    }

    void enrich(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        for (var selected : draft.path("selections")) {
            ObjectNode pickup = (ObjectNode) selected.path("pickup");
            ObjectNode pin = pin(actor.identityId(), event, draft, pickup);
            pickup.put("mapUrl", "https://www.google.com/maps?q=" + pin.path("latitude").asDouble() + "," + pin.path("longitude").asDouble());
        }
    }

    static String details(JsonNode pickup) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        if (pickup.has("pointLabel")) lines.add("Ponto: " + pickup.path("pointLabel").asText());
        if (pickup.has("reference")) lines.add("Referência: " + pickup.path("reference").asText());
        if (pickup.has("mapUrl")) lines.add("Confira o ponto: " + pickup.path("mapUrl").asText());
        return String.join("\n", lines);
    }

    static String prompt(ObjectNode draft) {
        int index = draft.path("pointIndex").asInt();
        JsonNode selected = draft.path("selections").path(index);
        StringBuilder text = new StringBuilder("Embarque " + (index+1) + " de " + draft.path("selections").size() + ":\n"
                + WaPassengerTexts.routineBlock(selected, selected.path("selectedDays"))
                + "\nPara mudar os dias, envie ALTERAR DIAS."
                + "\n\n" + WaPassengerTexts.ATTACH_LOCATION);
        if (draft.path("approvedChoicesBlocked").asBoolean()) text.append("\n\nHá muitos pontos aprovados para mostrar. Envie um ponto fixo ou peça ajuda ao suporte.");
        int pointIndex = 0;
        for (var point : draft.path("approvedChoices")) {
            text.append("\n\nMESMO PONTO ").append(++pointIndex).append(": ")
                    .append(point.path("label").asText("ponto aprovado")).append("\nhttps://www.google.com/maps?q=")
                    .append(point.path("latitude").asDouble()).append(",").append(point.path("longitude").asDouble());
        }
        if (pointIndex==1) text.append("\nEnvie MESMO PONTO para reutilizar esse ponto aprovado.");
        if (pointIndex==0 && !draft.path("approvedChoicesBlocked").asBoolean() && index>0)
            text.append("\n\nPara repetir o ponto do trajeto anterior, envie MESMO PONTO. O motorista ainda vai conferir cada embarque.");
        if (draft.path("commonChoicesBlocked").asBoolean()) text.append("\n\nHá muitos pontos publicados. Envie seu ponto fixo ou peça ajuda ao suporte.");
        int commonIndex = 0;
        for (var point : draft.path("commonChoices")) text.append("\nPONTO ").append(++commonIndex).append(": ").append(point.path("name").asText());
        return text.toString();
    }

    static String editPrompt(ObjectNode draft) {
        if (draft.path("editChoicesBlocked").asBoolean()) return "Há muitos trajetos para mostrar nesta conversa. Peça ajuda ao suporte. Nenhum ponto foi alterado.";
        java.util.List<String> lines = new java.util.ArrayList<>();
        lines.add("De qual trajeto você quer corrigir o ponto? Envie o número.");
        int index = 0;
        for (var routine : draft.path("editChoices")) lines.add(++index + ": " + WaPassengerTexts.routineBlock(routine, routine.path("selectedDays")));
        return String.join("\n\n", lines);
    }

    static void clear(ObjectNode draft) { draft.remove(FIELDS); draft.remove("reusablePoint"); }
}
