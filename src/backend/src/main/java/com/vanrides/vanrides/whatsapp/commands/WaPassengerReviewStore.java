package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.util.UUID;

/** Persisted routine targets, shared by a passenger request and the driver's pickup review. */
final class WaPassengerReviewStore {
    private final WaPassengerData data;

    WaPassengerReviewStore(WaPassengerData data) { this.data = data; }

    ArrayNode selections(UUID link, UUID point) {
        return data.array("""
                SELECT (to_jsonb(r)||jsonb_build_object('selectedDays',s.weekdays,'selectionVersion',s.version))::text
                FROM wa.passenger_link_routine s JOIN wa.routine r ON r.id=s.routine_id AND r.driver_id=s.driver_id
                WHERE s.link_id=? AND (?::uuid IS NULL OR s.pickup_point_id=?) ORDER BY r.created_at,r.id
                """, link, point, point);
    }

    ArrayNode approvedPoints(UUID link) {
        return data.array("""
                SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE p.link_id=? AND p.state='APPROVED'
                AND p.valid_until IS NULL ORDER BY p.created_at,p.id LIMIT 9
                """, link);
    }

    ObjectNode reusable(UUID actor, ObjectNode draft, ObjectNode pickup) {
        ObjectNode point = data.point(UUID.fromString(draft.path("linkId").asText()),
                UUID.fromString(pickup.path("reusePickupId").asText()), true);
        if (!point.path("passenger_id").asText().equals(actor.toString())
                || !point.path("state").asText().equals("APPROVED") || !point.path("valid_until").isNull()
                || java.time.OffsetDateTime.parse(point.path("valid_from").asText()).toInstant().isAfter(data.clock.now())
                || point.path("version").asLong() != pickup.path("reusePickupVersion").asLong()) {
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        }
        return point;
    }

    String summary(UUID link, UUID point) {
        java.util.List<String> lines = new java.util.ArrayList<>();
        for (var selected : selections(link, point)) {
            lines.add(WaPassengerTexts.routineLine(selected.path("name").asText(), selected.path("direction").asText(),
                    selected.path("local_time").asText(), selected.path("selectedDays")));
        }
        return String.join("\n", lines);
    }
}
