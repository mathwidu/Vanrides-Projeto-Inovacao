package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;

import java.util.UUID;

/** Adds routine and pickup details to the draft shown for confirmation. */
final class WaPassengerReview {
    private final WaPassengerData data;

    WaPassengerReview(WaPassengerData data) {
        this.data = data;
    }

    void enrich(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        if (draft.path("routePoints").asBoolean()) {
            new WaPassengerPoints(data).enrich(actor, event, draft);
            return;
        }
        ObjectNode pin = null;
        if (draft.has("linkId") && !draft.path("addingRoutines").asBoolean()) {
            draft.put(
                    "selectionSummary",
                    draft.has("pickupId") ? new WaPassengerReviewStore(data).summary(UUID.fromString(draft.path("linkId").asText()),
                            UUID.fromString(draft.path("pickupId").asText())) : data.selectionSummary(UUID.fromString(draft.path("linkId").asText())));
        }
        if (draft.has("sourceEvent")) {
            pin = data.pin(actor.identityId(), draft.path("sourceEvent").asText(), event.eventId());
        } else {
            if (draft.has("pickupId") || draft.has("reusePickupId")) {
                pin =
                        data.point(
                                UUID.fromString(draft.path("linkId").asText()),
                                UUID.fromString(draft.path(draft.has("pickupId") ? "pickupId" : "reusePickupId").asText()),
                                false);
                if (pin.hasNonNull("reference_text") && !draft.has("reusePickupId")) {
                    draft.put("reference", pin.path("reference_text").asText());
                }
                if (pin.hasNonNull("label") && !draft.has("reusePickupId")) {
                    draft.put("pointLabel", pin.path("label").asText());
                }
            } else {
                if (draft.has("commonId")) {
                    var values = data.commons(UUID.fromString(draft.path("driverId").asText()));
                    for (var value : values) {
                        if (value.path("id").asText().equals(draft.path("commonId").asText())) {
                            pin = (ObjectNode) value;
                        }
                    }
                }
            }
        }
        if (pin != null) {
            draft.put(
                    "mapUrl",
                    "https://www.google.com/maps?q="
                            + pin.path("latitude").asDouble()
                            + ","
                            + pin.path("longitude").asDouble());
        }
    }
}
