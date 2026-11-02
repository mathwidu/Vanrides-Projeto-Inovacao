package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
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
                validateSelections(link);
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
        if (action.equals("REQUEST_LINK")) {
            if (!actor.role().equals("PASSENGER")
                    || actor.name() == null
                    || !WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion())
                    || !draft.path("approachMessages").isBoolean()
                    || !draft.path("attendanceReminders").isBoolean()) {
                throw WaProblem.denied();
            }
            if (!data.links(passenger, false).isEmpty()) {
                throw WaProblem.denied();
            }
            ObjectNode invite =
                    data.invite(actor.identityId(), event, draft.path("inviteHash").asText(), true);
            if (!invite.path("driver_id").asText().equals(driver.toString())) {
                throw WaProblem.denied();
            }
            if (draft.path("selections").isEmpty()) {
                throw WaProblem.denied();
            }
            for (JsonNode selected : draft.path("selections")) {
                var rows =
                        data.array(
                                "SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=? AND"
                                        + " driver_id=? AND state='ACTIVE'",
                                UUID.fromString(selected.path("id").asText()),
                                driver);
                if (rows.size() != 1
                        || rows.get(0).path("version").asLong() != selected.path("version").asLong()
                        || !WaPassengerAnswers.subset(
                                selected.path("selectedDays"), rows.get(0).path("weekdays"))) {
                    throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
                }
            }
            return new Prepared(null, null, invite, proposedPin(actor, event, draft));
        }
        if (action.equals("PROPOSE_PICKUP")) {
            if (!actor.role().equals("PASSENGER") || !actor.identityId().equals(passenger)) {
                throw WaProblem.denied();
            }
            return new Prepared(link, null, null, proposedPin(actor, event, draft));
        }
        return new Prepared(link, point, null, null);
    }

    private ObjectNode proposedPin(
            WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        if (draft.has("sourceEvent")) {
            return data.pin(
                    actor.identityId(), draft.path("sourceEvent").asText(), event.eventId());
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
                        actor.identityId()))) {
            throw WaProblem.denied();
        }
        return WaPassengerDraftStore.json()
                .put("latitude", common.get(0).path("latitude").asDouble())
                .put("longitude", common.get(0).path("longitude").asDouble());
    }

    private void validateSelections(ObjectNode link) {
        var rows =
                data.array(
                        """
                        SELECT jsonb_build_object('selected',s.weekdays,'available',r.weekdays,'state',r.state)::text
                        FROM wa.passenger_link_routine s JOIN wa.routine r ON r.id=s.routine_id AND r.driver_id=s.driver_id WHERE s.link_id=?
                        """,
                        UUID.fromString(link.path("id").asText()));
        if (rows.isEmpty()) {
            throw WaProblem.denied();
        }
        for (JsonNode row : rows) {
            if (!row.path("state").asText().equals("ACTIVE")
                    || !WaPassengerAnswers.subset(row.path("selected"), row.path("available"))) {
                throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            }
        }
    }
}
