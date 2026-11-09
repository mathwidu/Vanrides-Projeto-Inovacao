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
        ObjectNode pin = null;
        if (draft.has("linkId")) {
            draft.put(
                    "selectionSummary",
                    data.selectionSummary(UUID.fromString(draft.path("linkId").asText())));
        }
        if (draft.has("sourceEvent")) {
            pin = data.pin(actor.identityId(), draft.path("sourceEvent").asText(), event.eventId());
        } else {
            if (draft.has("pickupId")) {
                pin =
                        data.point(
                                UUID.fromString(draft.path("linkId").asText()),
                                UUID.fromString(draft.path("pickupId").asText()),
                                false);
                if (pin.hasNonNull("reference_text")) {
                    draft.put("reference", pin.path("reference_text").asText());
                }
                if (pin.hasNonNull("label")) {
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
