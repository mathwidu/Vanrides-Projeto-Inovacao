package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;

import java.util.List;
import java.util.UUID;

/**
 * Stable private pages and driver-owned reviews; never renumber a stored page from a fresh query.
 */
final class WaPassengerDriverWizard {
    private final WaPassengerData data;

    WaPassengerDriverWizard(WaPassengerData data) {
        this.data = data;
    }

    ObjectNode advance(
            WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, String text) {
        if (text.equals("passageiros")
                || text.equals("menu passageiros")
                || draft.isEmpty() && !text.equals("ponto comum")) {
            return driverMenu(actor.identityId());
        }
        if (draft.path("stage").asText().equals("DRIVER_MENU")
                && List.of("proxima", "anterior").contains(text)) {
            int lastPage = Math.max(0, (draft.path("allLinkChoices").size() - 1) / 8);
            int page = draft.path("page").asInt() + (text.equals("proxima") ? 1 : -1);
            draft.put("page", Math.max(0, Math.min(lastPage, page)));
            showPage(draft);
            return draft;
        }
        if (text.equals("ponto comum")) {
            return WaPassengerDraftStore.json()
                    .put("stage", "COMMON_NAME")
                    .put("action", "PUBLISH_COMMON")
                    .put("driverId", actor.identityId().toString());
        }
        if (draft.path("stage").asText().equals("COMMON_NAME")) {
            String name =
                    WaPassengerAnswers.clean(event.normalizedPayload().path("text").asText(), 80);
            if (name != null) {
                draft.put("pointLabel", name).put("stage", "COMMON_PIN");
            }
            return draft;
        }
        if (draft.path("stage").asText().equals("COMMON_PIN")) {
            if (event.normalizedKind().equals("PIN")) {
                data.pin(actor.identityId(), event.eventId().toString(), event.eventId());
                draft.put("sourceEvent", event.eventId().toString()).put("stage", "REVIEW");
            }
            return draft;
        }
        if (draft.path("stage").asText().equals("DRIVER_MENU")
                && text.matches("(?:aprovar|recusar|retificar|encerrar) (?:[1-9][0-9]?|100)")) {
            String[] parts = text.split(" ");
            int index = Integer.parseInt(parts[1]) - 1;
            if (index >= draft.path("linkChoices").size()) {
                return draft;
            }
            JsonNode link = draft.path("linkChoices").get(index);
            ObjectNode current =
                    data.link(actor.identityId(), UUID.fromString(link.path("id").asText()), false);
            if (current.path("version").asLong() != link.path("version").asLong()
                    || !current.path("state").asText().equals(link.path("state").asText())) {
                ObjectNode refreshed = driverMenu(actor.identityId());
                refreshed.put(
                        "notice",
                        "Essa solicitação mudou. Confira a lista atual antes de escolher"
                                + " novamente.");
                return refreshed;
            }
            ObjectNode chosen = WaPassengerWizard.baseLink(link).put("stage", "REVIEW");
            boolean requested = link.path("state").asText().equals("REQUESTED");
            String pickup = link.path("pickupId").asText("");
            if (!pickup.isEmpty()) {
                chosen.put("pickupId", pickup);
            }
            switch (parts[0]) {
                case "aprovar" -> {
                    if (pickup.isEmpty()) {
                        return draft;
                    }
                    chosen.put("action", requested ? "APPROVE_LINK" : "APPROVE_PICKUP");
                }
                case "retificar" -> {
                    if (pickup.isEmpty()) {
                        return draft;
                    }
                    chosen.put("action", "RECTIFY");
                }
                case "recusar" -> {
                    if (!requested) {
                        return draft;
                    }
                    chosen.put("action", "CLOSE_LINK").put("operation", "REJECTED");
                }
                case "encerrar" -> chosen.put("action", "CLOSE_LINK").put("operation", "CLOSED");
                default -> throw WaProblem.invalid();
            }
            return chosen;
        }
        if (text.equals("voltar") || text.equals("corrigir")) {
            return advance(actor, event, WaPassengerDraftStore.json(), "passageiros");
        }
        return draft;
    }

    private ObjectNode driverMenu(UUID driver) {
        ObjectNode menu =
                WaPassengerDraftStore.json()
                        .put("stage", "DRIVER_MENU")
                        .put("driverId", driver.toString())
                        .put("page", 0);
        ArrayNode links = data.links(driver, true);
        menu.put("catalogBlocked", links.size() > 100);
        menu.set("allLinkChoices", links.size() > 100 ? WaDriverDraftStore.options() : links);
        showPage(menu);
        return menu;
    }

    private void showPage(ObjectNode draft) {
        ArrayNode visible = WaDriverDraftStore.options();
        int first = draft.path("page").asInt() * 8;
        JsonNode all = draft.path("allLinkChoices");
        for (int index = first; index < Math.min(first + 8, all.size()); index++) {
            visible.add(all.get(index).deepCopy());
        }
        draft.set("linkChoices", visible);
        draft.put("hasNext", first + 8 < all.size());
        draft.put("hasPrevious", first > 0);
    }
}
