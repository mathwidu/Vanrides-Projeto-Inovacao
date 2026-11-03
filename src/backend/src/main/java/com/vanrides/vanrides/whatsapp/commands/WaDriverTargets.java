package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Locks are profile -> conversation -> routine -> members -> links/points -> occurrence/run. */
final class WaDriverTargets {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    WaDriverTargets(JdbcTemplate jdbc, WaClock clock) { this.jdbc = jdbc; this.clock = clock; }
    ObjectNode routine(UUID actor, UUID id) {
        var rows = jdbc.query("SELECT to_jsonb(r)::text FROM wa.routine r WHERE driver_id=? AND id=? FOR UPDATE",
            (rs, i) -> WaJson.object(rs.getString(1)), actor, id);
        if (rows.size() != 1) throw WaProblem.missing();
        if (rows.get(0).path("state").asText().equals("CLOSED")) throw WaProblem.denied();
        return rows.get(0);
    }
    ArrayNode members(UUID actor, UUID routine) {
        jdbc.queryForList("SELECT id FROM wa.routine_member WHERE routine_id=? AND driver_id=? ORDER BY id FOR UPDATE", routine, actor);
        var rows = jdbc.query("""
            SELECT jsonb_build_object('id',m.id,'passenger',m.passenger_id,'version',m.version,'order',m.stop_order,
              'link',l.id,'linkVersion',l.version,'linkState',l.state,'linkFrom',l.valid_from,'linkUntil',l.valid_until,
              'point',p.id,'pointVersion',p.version,'pointState',p.state,'pointFrom',p.valid_from,'pointUntil',p.valid_until,
              'passengerName',profile.display_name,'pointLabel',p.label,'pointReference',p.reference_text)::text
            FROM wa.routine_member m JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.driver_id=m.driver_id AND l.passenger_id=m.passenger_id
            JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.driver_id=m.driver_id AND p.passenger_id=m.passenger_id
            JOIN wa.profile profile ON profile.identity_id=m.passenger_id
            WHERE m.routine_id=? AND m.driver_id=? AND m.state='ACTIVE' ORDER BY m.stop_order,m.id FOR SHARE OF l,p
            """, (rs, i) -> WaJson.object(rs.getString(1)), routine, actor);
        ArrayNode result = WaDriverDraftStore.options();
        rows.forEach(row -> {
            if (!row.path("linkState").asText().equals("APPROVED") || !validAt(row, "linkFrom", "linkUntil")) row.remove("passengerName");
            if (!row.path("pointState").asText().equals("APPROVED") || !validAt(row, "pointFrom", "pointUntil")) row.remove(java.util.List.of("pointLabel", "pointReference"));
            result.add(row);
        }); return result;
    }
    void approved(ArrayNode members) {
        for (var member : members) {
            if (!member.path("linkState").asText().equals("APPROVED") || !member.path("pointState").asText().equals("APPROVED")
                    || !validAt(member, "linkFrom", "linkUntil") || !validAt(member, "pointFrom", "pointUntil"))
                throw new WaProblem(WaProblem.Code.POINT_NOT_APPROVED);
        }
    }
    private boolean validAt(com.fasterxml.jackson.databind.JsonNode node, String from, String until) {
        java.time.Instant start = java.time.OffsetDateTime.parse(node.path(from).asText()).toInstant();
        return !start.isAfter(clock.now()) && (node.path(until).isNull()
            || java.time.OffsetDateTime.parse(node.path(until).asText()).toInstant().isAfter(clock.now()));
    }
    ObjectNode pin(UUID actor, String id) {
        var rows = jdbc.query("""
            SELECT payload_json::text FROM wa.integration_inbox WHERE id=? AND identity_id=? AND provider='EVOLUTION'
              AND category='MESSAGE' AND normalized_kind='PIN' AND state IN ('CLAIMED','COMPLETED') FOR SHARE
            """, (rs, i) -> WaJson.object(rs.getString(1)), UUID.fromString(id), actor);
        if (rows.size() != 1) throw WaProblem.missing();
        var pin = rows.get(0).path("pin");
        if (!pin.path("latitude").isNumber() || !pin.path("longitude").isNumber()
                || !Double.isFinite(pin.path("latitude").asDouble()) || !Double.isFinite(pin.path("longitude").asDouble())
                || Math.abs(pin.path("latitude").asDouble()) > 90 || Math.abs(pin.path("longitude").asDouble()) > 180)
            throw WaProblem.invalid();
        return (ObjectNode) pin.deepCopy();
    }
    String snapshot(UUID actor, ObjectNode draft) {
        ObjectNode snapshot = WaDriverDraftStore.json();
        String action = draft.path("action").asText();
        if (action.equals("CREATE_INVITE")) return WaCrypto.digest("create-invite");
        if (action.equals("REVOKE_INVITE")) {
            var rows = jdbc.query("SELECT to_jsonb(i)::text FROM wa.driver_invite i WHERE id=? AND driver_id=? FOR UPDATE",
                (rs, i) -> WaJson.object(rs.getString(1)), UUID.fromString(draft.path("inviteId").asText()), actor);
            if (rows.size() != 1) throw WaProblem.missing();
            if (rows.get(0).path("version").asLong() != draft.path("inviteVersion").asLong()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            ObjectNode invite = rows.get(0);
            draft.set("inviteDisplay", WaDriverDraftStore.json().put("createdAt", java.time.OffsetDateTime.parse(invite.path("created_at").asText()).toInstant().toString())
                .put("expiresAt", java.time.OffsetDateTime.parse(invite.path("expires_at").asText()).toInstant().toString())
                .put("maxUses", invite.path("max_uses").asInt()).put("useCount", invite.path("use_count").asInt()));
            snapshot.set("invite", rows.get(0));
        } else if (!draft.path("creating").asBoolean()) {
            UUID id = UUID.fromString(draft.path("routineId").asText());
            ObjectNode routine = routine(actor, id);
            if (routine.path("version").asLong() != draft.path("routineVersion").asLong()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            draft.set("routineDisplay", display(routine));
            snapshot.set("routine", routine);
            ArrayNode members = members(actor, id); snapshot.set("members", members);
            if (action.equals("SET_STOP_ORDER")) {
                approved(members);
                if (!WaJson.canonical(members).equals(WaJson.canonical(draft.path("stopChoices"))))
                    throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            }
            if (action.equals("CANCEL_OCCURRENCE")) {
                scheduledAt(routine, draft.path("date").asText());
                var occurrences = jdbc.query("SELECT to_jsonb(o)::text FROM wa.routine_occurrence o WHERE routine_id=? AND local_date=?::date FOR UPDATE",
                    (rs, i) -> WaJson.object(rs.getString(1)), id, draft.path("date").asText());
                if (!occurrences.isEmpty()) {
                    ObjectNode occurrence = occurrences.get(0);
                    if (occurrence.path("state").asText().equals("CLOSED")) throw WaProblem.denied();
                    snapshot.set("occurrence", occurrence);
                    if (!draft.path("occurrenceId").asText().equals(occurrence.path("id").asText())) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
                    var runs = jdbc.queryForList("SELECT state,started_at FROM wa.operation_run WHERE occurrence_id=? FOR UPDATE",
                        UUID.fromString(occurrence.path("id").asText()));
                    if (runs.stream().anyMatch(row -> row.get("started_at") != null || !List.of("PLANNED", "CANCELED").contains(row.get("state"))))
                        throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
                }
            }
        }
        if (draft.has("destinationEvent")) snapshot.set("pin", pin(actor, draft.path("destinationEvent").asText()));
        return WaCrypto.digest(WaJson.canonical(snapshot));
    }
    String occurrence(UUID actor, ObjectNode draft) {
        var rows = jdbc.query("SELECT id FROM wa.routine_occurrence WHERE driver_id=? AND routine_id=? AND local_date=?::date",
            (rs, i) -> rs.getObject(1, UUID.class), actor, UUID.fromString(draft.path("routineId").asText()), draft.path("date").asText());
        return rows.isEmpty() ? UUID.randomUUID().toString() : rows.get(0).toString();
    }
    static java.time.Instant scheduledAt(ObjectNode routine, String date) {
        var local = java.time.LocalDate.parse(date).atTime(java.time.LocalTime.parse(routine.path("local_time").asText()));
        var offsets = java.time.ZoneId.of(routine.path("timezone").asText()).getRules().getValidOffsets(local);
        if (offsets.size() != 1) throw new WaProblem(WaProblem.Code.AMBIGUOUS_OCCURRENCE);
        return local.toInstant(offsets.get(0));
    }
    ArrayNode routines(UUID actor) {
        ArrayNode result = WaDriverDraftStore.options();
        jdbc.query("SELECT id,version,name,state,direction,local_time::text,timezone FROM wa.routine WHERE driver_id=? AND state IN ('ACTIVE','PAUSED') ORDER BY created_at,id LIMIT 101",
            (rs, i) -> WaDriverDraftStore.json().put("id", rs.getObject(1).toString()).put("version", rs.getLong(2))
                .put("name", rs.getString(3)).put("state", rs.getString(4)).put("direction", rs.getString(5))
                .put("localTime", rs.getString(6)).put("timezone", rs.getString(7)), actor).forEach(result::add);
        return result;
    }
    ArrayNode invites(UUID actor) {
        ArrayNode result = WaDriverDraftStore.options();
        jdbc.query("SELECT id,version,created_at,expires_at,max_uses,use_count FROM wa.driver_invite WHERE driver_id=? AND revoked_at IS NULL AND expires_at>? ORDER BY created_at,id LIMIT 101",
            (rs, i) -> WaDriverDraftStore.json().put("id", rs.getObject(1).toString()).put("version", rs.getLong(2))
                .put("createdAt", rs.getTimestamp(3).toInstant().toString()).put("expiresAt", rs.getTimestamp(4).toInstant().toString())
                .put("maxUses", rs.getInt(5)).put("useCount", rs.getInt(6)), actor,
            Timestamp.from(clock.now())).forEach(result::add); return result;
    }
    String summary(UUID actor) {
        return WaDriverTexts.summary(routines(actor), invites(actor));
    }
    ObjectNode display(ObjectNode routine) {
        return WaDriverDraftStore.json().put("name", routine.path("name").asText()).put("direction", routine.path("direction").asText())
            .put("localTime", routine.path("local_time").asText()).put("timezone", routine.path("timezone").asText());
    }
}
