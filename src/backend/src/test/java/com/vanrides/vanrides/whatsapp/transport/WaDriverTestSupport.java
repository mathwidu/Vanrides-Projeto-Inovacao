package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.commands.WaCommandService;
import com.vanrides.vanrides.whatsapp.commands.WaCommandStore;
import com.vanrides.vanrides.whatsapp.commands.WaInboxAccess;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import java.sql.Timestamp;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.testcontainers.containers.PostgreSQLContainer;

/** Composition only; fixtures do not pretend to execute passenger admission090 or run110. */
final class WaDriverTestSupport {
    static final Set<String> TYPES = Set.of("ADVANCE_DRIVER_SETUP", "CONFIGURE_ROUTINE", "SET_STOP_ORDER", "SET_ROUTINE_STATE",
        "CREATE_INVITE", "REVOKE_INVITE", "CANCEL_OCCURRENCE", "GET_SUMMARY");
    record Step(WaConversationTestSupport.Lease lease, ObjectNode context, ObjectNode body, ObjectNode response, String text) { }
    final WaConversationTestSupport s;
    final WaTransportTestSupport h;
    WaDriverTestSupport(PostgreSQLContainer<?> postgres) { s = new WaConversationTestSupport(postgres); h = s.h; }
    UUID driver() { UUID actor = s.actor(); s.driver(actor); return actor; }
    ObjectNode context(WaConversationTestSupport.Lease lease) { return s.commands.context(s.worker, lease.id(), lease.token()); }
    ObjectNode payload(ObjectNode context, String type) {
        ObjectNode payload = json();
        for (var option : context.path("options")) {
            String field = switch (option.path("kind").asText()) {
                case "ROUTINE" -> "routineRef"; case "CONFIRMATION" -> "confirmationRef";
                case "OCCURRENCE" -> "occurrenceRef"; case "INVITE" -> "inviteRef"; case "SUMMARY" -> "summaryRef";
                default -> throw new AssertionError("Unexpected context option");
            };
            assertFalse(payload.has(field)); payload.put(field, option.path("ref").asText());
        }
        return payload;
    }
    Step execute(WaConversationTestSupport.Lease lease, boolean finish) {
        ObjectNode context = context(lease);
        List<String> allowed = new java.util.ArrayList<>();
        context.path("allowedCommands").forEach(value -> { if (TYPES.contains(value.asText())) allowed.add(value.asText()); });
        assertEquals(1, allowed.size()); String type = allowed.get(0);
        ObjectNode body = s.body(lease, type, payload(context, type));
        body.put("expectedVersion", context.path("entityVersion").asLong());
        ObjectNode response = s.execute(lease, body, "synthetic-080-" + lease.id()).requireSuccess();
        String text = s.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?", UUID.fromString(response.path("responseIds").get(0).asText()));
        if (finish) s.finish(lease);
        return new Step(lease, context, body, response, text);
    }
    Step send(UUID actor, String text) { return execute(s.text(actor, text), true); }
    Step pin(UUID actor) {
        UUID id = s.pending(actor, json().set("locationMessage", json().put("degreesLatitude", 0).put("degreesLongitude", 0)), h.clock.now());
        return execute(s.claim(id), true);
    }
    Step review(UUID actor, String direction) {
        send(actor, "ROTINAS"); send(actor, "CRIAR"); send(actor, "Rotina Sintética"); send(actor, direction);
        send(actor, "SEGUNDA A SEXTA"); send(actor, "06:00"); send(actor, "MANTER"); return pin(actor);
    }
    String marker(Step review) {
        var match = Pattern.compile("CONFIRMAR ([BCDFGHJKMNPQRSTVWXYZ]{8})").matcher(review.text());
        assertTrue(match.find()); return match.group(1);
    }
    Step confirm(UUID actor, Step review) { return send(actor, "CONFIRMAR " + marker(review)); }
    UUID routine(UUID actor) { confirm(actor, review(actor, "IDA")); return UUID.fromString(s.string("SELECT id::text FROM wa.routine WHERE driver_id=?", actor)); }
    Step invite(UUID actor) { send(actor, "CONVIDAR"); return confirm(actor, send(actor, "PADRÃO")); }
    ObjectNode draft(UUID actor) { return WaJson.object(s.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?", actor)).with("driverDraft"); }
    long count(String table, UUID actor) { return s.number("SELECT count(*) FROM wa." + table + " WHERE " + (table.equals("message_outbox") ? "recipient_identity_id" : "driver_id") + "=?", actor); }
    ObjectNode privateItem(UUID actor) {
        UUID box = h.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=? AND private_invite_id IS NOT NULL", UUID.class, actor);
        for (var item : s.outboxClaim().path("items")) if (item.path("outboxId").asText().equals(box.toString())) return (ObjectNode) item;
        throw new AssertionError("Expected private invitation outbox");
    }
    WaCommandService restartedCommands() {
        return h.proxy(new WaCommandService(h.jdbc, new WaInboxAccess(h.jdbc, h.settings, h.clock), new WaCommandStore(h.jdbc, h.clock),
            new WaOnboardingCommands(h.jdbc, h.crypto, h.clock), s.pairing, h.settings, h.clock, h.crypto));
    }
    List<UUID> members(UUID actor, UUID routine) {
        return members(actor, routine, 2);
    }
    List<UUID> members(UUID actor, UUID routine, int amount) {
        for (int i = 1; i <= amount; i++) {
            UUID passenger = s.actor(), link = UUID.randomUUID(), point = UUID.randomUUID();
            h.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'PASSENGER','ACTIVE',?)", passenger, "Passageiro Sintético " + i);
            UUID pin = s.pending(passenger, json().set("locationMessage", json().put("degreesLatitude", 0).put("degreesLongitude", 0)), h.clock.now());
            s.finish(s.claim(pin));
            h.jdbc.update("INSERT INTO wa.driver_passenger_link(id,driver_id,passenger_id,state,approved_by,approved_at) VALUES (?,?,?,'APPROVED',?,?)",
                link, actor, passenger, actor, Timestamp.from(h.clock.now()));
            h.jdbc.update("""
                INSERT INTO wa.pickup_point(id,link_id,driver_id,passenger_id,latitude,longitude,source_inbox_event_id,state,approved_by,approved_at,label,reference_text)
                VALUES (?,?,?,?,0,0,?,'APPROVED',?,?,?,?)
                """, point, link, actor, passenger, pin, actor, Timestamp.from(h.clock.now()), "Ponto Sintético " + i, "Portaria " + i);
            h.jdbc.update("INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order) VALUES (?,?,?,?,?,?,?)",
                UUID.randomUUID(), routine, actor, passenger, link, point, i);
        }
        return h.jdbc.query("SELECT id FROM wa.routine_member WHERE routine_id=? ORDER BY stop_order,id", (rs, i) -> rs.getObject(1, UUID.class), routine);
    }
    static ObjectNode json() { return WaConversationTestSupport.json(); }
}
