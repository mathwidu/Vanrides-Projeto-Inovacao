package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import java.sql.Timestamp;
import java.util.UUID;

/** A redeemed invitation authorizes the driver relationship, never a new GPS pickup. */
final class WaPassengerRequests {
    private final WaPassengerData data;
    WaPassengerRequests(WaPassengerData data) { this.data = data; }

    WaPassengerMutations.Prepared prepare(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, ObjectNode link) {
        UUID driver = UUID.fromString(draft.path("driverId").asText());
        if (!actor.role().equals("PASSENGER") || !actor.status().equals("ACTIVE") || actor.name() == null
                || !WaOnboardingCommands.NOTICE_VERSION.equals(actor.noticeVersion()) || draft.path("selections").isEmpty()
                || draft.path("selections").size()>8) throw WaProblem.denied();
        if (!Boolean.TRUE.equals(data.jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.profile WHERE identity_id=? AND role='DRIVER' AND status='ACTIVE' AND driver_enabled AND notice_version=?)",
                Boolean.class, driver, WaOnboardingCommands.NOTICE_VERSION))) throw WaProblem.denied();
        var links = data.links(actor.identityId(), false);
        if (link == null ? !links.isEmpty() : links.size()!=1 || !links.path(0).path("id").equals(link.path("id"))
                || !link.path("passenger_id").asText().equals(actor.identityId().toString())) throw WaProblem.denied();
        ObjectNode invite = draft.has("inviteHash") ? data.invite(actor.identityId(), event, draft.path("inviteHash").asText(), true) : null;
        if (invite == null && (link == null || !draft.path("editingPickup").asBoolean())
                || invite != null && !invite.path("driver_id").asText().equals(driver.toString())) throw WaProblem.denied();
        if (!draft.path("approachMessages").isBoolean()) throw WaProblem.denied();
        ArrayNode selections = WaDriverDraftStore.options();
        java.util.Set<String> seen = new java.util.HashSet<>();
        for (var selected : draft.path("selections")) {
            if (!seen.add(selected.path("id").asText())) throw WaProblem.invalid();
            var rows = data.array("SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=? AND driver_id=? AND state='ACTIVE'",
                    UUID.fromString(selected.path("id").asText()), driver);
            if (rows.size()!=1 || rows.path(0).path("version").asLong()!=selected.path("version").asLong()
                    || !WaPassengerAnswers.subset(selected.path("selectedDays"), rows.path(0).path("weekdays"))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            if (draft.path("editingPickup").asBoolean() && new WaPassengerReviewStore(data).selections(UUID.fromString(link.path("id").asText()), null)
                    .findValuesAsText("id").stream().noneMatch(selected.path("id").asText()::equals)) throw WaProblem.denied();
            if (!selected.path("pickup").isObject()) throw WaProblem.denied();
            if (draft.path("addingRoutines").asBoolean() && Boolean.TRUE.equals(data.jdbc.queryForObject(
                    "SELECT EXISTS(SELECT 1 FROM wa.passenger_link_routine WHERE link_id=? AND routine_id=?)", Boolean.class,
                    UUID.fromString(link.path("id").asText()), UUID.fromString(selected.path("id").asText())))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            ObjectNode copy = selected.deepCopy();
            copy.set("verifiedPin", new WaPassengerPoints(data).pin(actor.identityId(), event, draft, (ObjectNode) selected.path("pickup")));
            selections.add(copy);
        }
        return new WaPassengerMutations.Prepared(link, null, invite, null, selections);
    }

    WaPassengerMutations.Result apply(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, WaPassengerMutations.Prepared prepared) {
        Timestamp now = Timestamp.from(data.clock.now());
        UUID driver = UUID.fromString(draft.path("driverId").asText()), passenger = actor.identityId();
        UUID link = prepared.link()==null ? UUID.randomUUID() : UUID.fromString(prepared.link().path("id").asText());
        if (prepared.link()==null) {
            data.jdbc.update("""
                    INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,invite_id,requested_source_event_id,state,approved_by,approved_at,created_at,valid_from)
                    VALUES (?,?,?,?,?,'APPROVED',?,?,?,?)
                    """, link, driver, passenger, UUID.fromString(prepared.invite().path("id").asText()), event.eventId(), driver, now, now, now);
        } else if (prepared.invite()!=null && !prepared.link().path("state").asText().equals("APPROVED")) {
            data.jdbc.update("UPDATE wa.driver_passenger_link SET state='APPROVED',approved_by=?,approved_at=COALESCE(approved_at,?),version=version+1 WHERE id=?", driver, now, link);
        }
        UUID notification = null;
        boolean pending = false;
        WaPassengerMemberships memberships = new WaPassengerMemberships(data);
        for (var selected : prepared.selections()) {
            ObjectNode pickup = (ObjectNode) selected.path("pickup"), pin = (ObjectNode) selected.path("verifiedPin");
            UUID routine = UUID.fromString(selected.path("id").asText());
            var previous = data.jdbc.query("SELECT pickup_point_id FROM wa.passenger_link_routine WHERE link_id=? AND routine_id=?",
                    (rs,row)->rs.getObject(1,UUID.class), link, routine);
            UUID old = previous.isEmpty() ? null : previous.get(0);
            boolean reuse = pickup.has("reusePickupId") && sameDetails(pickup, pin);
            UUID point = reuse ? UUID.fromString(pickup.path("reusePickupId").asText()) : insertPoint(link, driver, passenger, pickup, pin, now);
            data.jdbc.update("""
                    INSERT INTO wa.passenger_link_routine(id,link_id,routine_id,driver_id,passenger_id,weekdays,routine_version,pickup_point_id,created_at)
                    VALUES (?,?,?,?,?,?::smallint[],?,?,?) ON CONFLICT(link_id,routine_id) DO UPDATE SET weekdays=EXCLUDED.weekdays,
                      routine_version=EXCLUDED.routine_version,pickup_point_id=EXCLUDED.pickup_point_id,version=wa.passenger_link_routine.version+1
                    """, UUID.randomUUID(), link, routine, driver, passenger, WaPassengerMemberships.sqlDays(selected.path("selectedDays")),
                    selected.path("version").asLong(), point, now);
            if (reuse) memberships.activate(link, driver, passenger, routine, point, selected.path("selectedDays"), now);
            else { pending = true; notification = point; }
            if (notification == null) notification = point;
            if (old != null && !old.equals(point)) memberships.retireUnused(old, now);
        }
        if (prepared.invite()!=null) data.jdbc.update("UPDATE wa.driver_invite SET use_count=use_count+1,version=version+1 WHERE id=?", UUID.fromString(prepared.invite().path("id").asText()));
        if (prepared.link()==null) {
            ObjectNode preferences = WaPassengerDraftStore.json().put("APPROACH_MESSAGES", draft.path("approachMessages").asBoolean());
            data.jdbc.update("UPDATE wa.profile SET preferences_json=preferences_json||?::jsonb WHERE identity_id=?", preferences.toString(), passenger);
        }
        return new WaPassengerMutations.Result(link, notification, draft.path("action").asText().equals("REQUEST_LINK") ? "REQUESTED" : "POINT_CHANGE",
                pending ? (draft.path("action").asText().equals("REQUEST_LINK")
                        ? "Cadastro solicitado! O pedido foi enviado ao motorista para validação."
                        : "Você entrou com o motorista. O pedido foi enviado para conferir os pontos e preparar a ordem. Seus trajetos anteriores foram mantidos.")
                        : "Trajetos incluídos com os pontos que você escolheu. Seus avisos foram mantidos. O motorista precisa preparar a ordem dos novos trajetos.",
                (pending ? "Confira os embarques de " : "Prepare a ordem dos novos trajetos de ") + draft.path("passengerName").asText()
                        + ".\n\n" + data.selectionSummary(link) + "\n\n" + (pending ? "Envie REVISAR PEDIDO para conferir um ponto por vez." : "Confira a ordem dos trajetos antes da saída."));
    }

    private static boolean sameDetails(ObjectNode pickup, ObjectNode pin) {
        return pickup.path("reference").asText("").equals(pin.path("reference_text").asText(""))
                && pickup.path("pointLabel").asText("").equals(pin.path("label").asText(""));
    }

    private UUID insertPoint(UUID link, UUID driver, UUID passenger, ObjectNode pickup, ObjectNode pin, Timestamp now) {
        UUID id = UUID.randomUUID();
        boolean copy = pickup.has("reusePickupId");
        String common = copy ? pin.path("common_pickup_point_id").asText("") : pickup.path("commonId").asText("");
        data.jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,label,reference_text,source_inbox_event_id,
                    common_pickup_point_id,common_pickup_version,created_at,valid_from) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?)
                """, id, link, driver, passenger, pin.path("latitude").asDouble(), pin.path("longitude").asDouble(),
                pickup.has("pointLabel") ? pickup.path("pointLabel").asText() : null, pickup.has("reference") ? pickup.path("reference").asText() : null,
                UUID.fromString(pickup.path(pickup.has("sourceEvent") ? "sourceEvent" : "selectionEvent").asText()),
                common.isEmpty() ? null : UUID.fromString(common), common.isEmpty() ? null : copy ? pin.path("common_pickup_version").asLong() : pickup.path("commonVersion").asLong(), now, now);
        return id;
    }
}
