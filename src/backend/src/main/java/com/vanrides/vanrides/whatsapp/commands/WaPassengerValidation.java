package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.commands.WaPassengerMutations.Prepared;

import java.util.List;
import java.util.UUID;

/** Locks and validates every target before an admission transition writes any domain row. */
final class WaPassengerValidation {
    private final WaPassengerData data;

    WaPassengerValidation(WaPassengerData data) {
        this.data = data;
    }

    Prepared prepare(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        String action = draft.path("action").asText();
        UUID driver = UUID.fromString(draft.path("driverId").asText());
        if (action.equals("PUBLISH_COMMON")) {
            if (!actor.role().equals("DRIVER") || !driver.equals(actor.identityId())) {
                throw WaProblem.denied();
            }
            return new Prepared(
                    null,
                    null,
                    null,
                    data.pin(
                            actor.identityId(),
                            draft.path("sourceEvent").asText(),
                            event.eventId()));
        }
        ObjectNode link =
                draft.has("linkId")
                        ? data.link(
                                actor.identityId(),
                                UUID.fromString(draft.path("linkId").asText()),
                                true)
                        : null;
        UUID passenger =
                link == null
                        ? actor.identityId()
                        : UUID.fromString(link.path("passenger_id").asText());
        data.requireScope(passenger, event, driver);
        // A single passenger identity row serializes different drivers without locking the peer
        // profile.
        data.jdbc.queryForList("SELECT id FROM wa.identity WHERE id=? FOR UPDATE", passenger);
        if (link != null
                && (!link.path("driver_id").asText().equals(driver.toString())
                        || !List.of("REQUESTED", "APPROVED")
                                .contains(link.path("state").asText()))) {
            throw WaProblem.denied();
        }
        if (List.of("REQUEST_LINK", "PROPOSE_PICKUP").contains(action)) {
            if (!draft.path("routePoints").asBoolean()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            return new WaPassengerRequests(data).prepare(actor, event, draft, link);
        }
        ObjectNode point =
                draft.has("pickupId")
                        ? data.point(
                                UUID.fromString(draft.path("linkId").asText()),
                                UUID.fromString(draft.path("pickupId").asText()),
                                true)
                        : null;
        if (List.of("APPROVE_LINK", "APPROVE_PICKUP", "RECTIFY").contains(action)) {
            if (!actor.role().equals("DRIVER")
                    || !actor.identityId().equals(driver)
                    || point == null
                    || !point.path("state").asText().equals("PROPOSED")) {
                throw WaProblem.denied();
            }
            if (action.equals("APPROVE_LINK") && !link.path("state").asText().equals("REQUESTED")) {
                throw WaProblem.denied();
            }
            if (action.equals("APPROVE_PICKUP")
                    && !link.path("state").asText().equals("APPROVED")) {
                throw WaProblem.denied();
            }
            if (action.startsWith("APPROVE")) {
                if (!Boolean.TRUE.equals(
                        data.jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM wa.profile WHERE identity_id=? AND"
                                    + " role='PASSENGER' AND status='ACTIVE' AND notice_version=?)",
                                Boolean.class,
                                passenger,
                                WaOnboardingCommands.NOTICE_VERSION))) {
                    throw WaProblem.denied();
                }
                if (Boolean.TRUE.equals(
                        data.jdbc.queryForObject(
                                "SELECT EXISTS(SELECT 1 FROM wa.driver_passenger_link WHERE"
                                        + " passenger_id=? AND state='APPROVED' AND id<>?)",
                                Boolean.class,
                                passenger,
                                UUID.fromString(link.path("id").asText())))) {
                    throw WaProblem.denied();
                }
                new WaPassengerApprovals(data).validate(link, point);
            }
        }
        if (action.equals("CLOSE_LINK")
                && !(actor.identityId().equals(driver) || actor.identityId().equals(passenger))) {
            throw WaProblem.denied();
        }
        if (action.equals("CLOSE_LINK")
                && draft.path("operation").asText().equals("REJECTED")
                && (!actor.identityId().equals(driver)
                        || !link.path("state").asText().equals("REQUESTED"))) {
            throw WaProblem.denied();
        }
        return new Prepared(link, point, null, null);
    }

    ObjectNode proposedPin(
            UUID actor, WaInboxAccess.Context event, ObjectNode draft) {
        if (draft.has("sourceEvent")) {
            return data.pin(
                    actor, draft.path("sourceEvent").asText(), event.eventId());
        }
        if (!draft.has("commonId") || !draft.has("selectionEvent")) {
            throw WaProblem.denied();
        }
        var common =
                data.array(
                        "SELECT to_jsonb(p)::text FROM wa.common_pickup_point p WHERE id=? AND"
                                + " driver_id=? AND state='PUBLISHED' AND version=?",
                        UUID.fromString(draft.path("commonId").asText()),
                        UUID.fromString(draft.path("driverId").asText()),
                        draft.path("commonVersion").asLong());
        if (common.size() != 1) {
            throw WaProblem.missing();
        }
        if (!Boolean.TRUE.equals(
                data.jdbc.queryForObject(
                        """
                        SELECT EXISTS(SELECT 1 FROM wa.integration_inbox s JOIN wa.integration_inbox e ON e.id=?
                        WHERE s.id=? AND s.identity_id=? AND s.instance_id=e.instance_id AND s.provider=e.provider AND s.category='MESSAGE')
                        """,
                        Boolean.class,
                        event.eventId(),
                        UUID.fromString(draft.path("selectionEvent").asText()),
                        actor))) {
            throw WaProblem.denied();
        }
        return WaPassengerDraftStore.json()
                .put("latitude", common.get(0).path("latitude").asDouble())
                .put("longitude", common.get(0).path("longitude").asDouble());
    }

}
