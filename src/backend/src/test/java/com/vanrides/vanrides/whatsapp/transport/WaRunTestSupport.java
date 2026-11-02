package com.vanrides.vanrides.whatsapp.transport;

import static org.junit.jupiter.api.Assertions.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import java.sql.Timestamp;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.regex.Pattern;
import org.testcontainers.containers.PostgreSQLContainer;

/** All actors, coordinates and messages are isolated synthetic fixtures. */
final class WaRunTestSupport {
    static final LocalDate DAY = LocalDate.of(2030, 1, 1);
    record Step(WaConversationTestSupport.Lease lease, ObjectNode context, ObjectNode body, WaDecision decision, String text) { }
    final WaAttendanceConversationTestSupport attendance;
    final WaConversationTestSupport s;
    final WaTransportTestSupport h;
    final UUID driver, passenger;
    final WaAttendanceConversationTestSupport.Item item;
    final WaRunPreparation preparation;
    WaRunTestSupport(PostgreSQLContainer<?> postgres) {
        attendance = new WaAttendanceConversationTestSupport(postgres); s = attendance.s; h = attendance.h;
        driver = attendance.driver; passenger = attendance.passenger();
        item = attendance.question(passenger, DAY, "OUTBOUND", "15:00");
        h.jdbc.update("UPDATE wa.routine SET stop_order_pending=false WHERE id=?", item.routine());
        preparation = h.proxy(new WaRunPreparation(h.jdbc, h.clock, h.settings));
    }
    UUID pair() {
        UUID device = UUID.randomUUID();
        h.jdbc.update("UPDATE wa.driver_authorization SET generation=1 WHERE driver_id=?", driver);
        h.jdbc.update("""
            INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version)
            VALUES (?,?,?,'{}',1,?,'ANDROID','synthetic-run-110')
            """, device, driver, WaCrypto.randomToken(), h.settings.environment());
        return device;
    }
    /** Long calendar jumps expire transport credentials too; renew only this fixture's principals. */
    void renewExpiredTransportCredentials() {
        assertEquals(WaProblem.Code.INVALID_CREDENTIAL, assertThrows(WaProblem.class,
            () -> s.inbox.claim(s.worker, json().put("batchLimit", 10).put("leaseSeconds", 60))).code());
        Timestamp now = Timestamp.from(h.clock.now());
        Timestamp expires = Timestamp.from(h.clock.now().plusSeconds(3600));
        for (var principal : List.of(s.channel, s.worker, s.delivery)) {
            assertEquals(1, h.jdbc.update("""
                UPDATE wa.service_credential SET expires_at=? WHERE id=? AND principal=? AND environment=?
                  AND revoked_at IS NULL AND expires_at<=?
                """, expires, principal.credentialId(), principal.kind().name(), h.settings.environment(), now));
            assertEquals(expires, h.jdbc.queryForObject("SELECT expires_at FROM wa.service_credential WHERE id=?",
                Timestamp.class, principal.credentialId()));
        }
    }
    Step send(String text) { return execute(s.text(driver, text), true); }
    Step execute(WaConversationTestSupport.Lease lease, boolean finish) {
        ObjectNode context = s.commands.context(s.worker, lease.id(), lease.token());
        var commands = new ArrayList<String>();
        context.path("allowedCommands").forEach(value -> {
            if (Set.of("ADVANCE_RUN_SETUP", "REQUEST_START", "REQUEST_RESUME", "SET_OPERATIONAL_ATTENDANCE").contains(value.asText())) commands.add(value.asText());
        });
        assertEquals(1, commands.size(), context.toString());
        ObjectNode payload = json();
        for (var option : context.path("options")) payload.put(switch (option.path("kind").asText()) {
            case "OCCURRENCE" -> "occurrenceRef"; case "PASSENGER" -> "passengerRef"; default -> "confirmationRef";
        }, option.path("ref").asText());
        ObjectNode body = s.body(lease, commands.get(0), payload).put("expectedVersion", context.path("entityVersion").asLong());
        WaDecision decision = s.execute(lease, body, "synthetic-run-command-" + lease.id());
        String text = s.string("SELECT o.payload_json->>'text' FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE r.inbox_event_id=?", lease.id());
        if (finish) s.finish(lease);
        return new Step(lease, context, body, decision, text);
    }
    Step reviewStart() { send("INICIAR SAÍDA"); return send("1"); }
    Step reviewCorrection(String action) {
        send("CORRIGIR LISTA"); send("1"); send("1"); send(action); return send("ajuste operacional sintético");
    }
    String marker(Step step) {
        var match = Pattern.compile("CONFIRMAR ([BCDFGHJKMNPQRSTVWXYZ]{8})").matcher(step.text());
        assertTrue(match.find(), step.text()); return match.group(1);
    }
    ObjectNode draft() { return WaJson.object(s.string("SELECT context_json->'runDraft' FROM wa.conversation_state WHERE identity_id=?", driver)); }
    String digest() { return preparation.rosterDigest(driver, item.occurrence()); }
    static ObjectNode json() { return WaJson.MAPPER.createObjectNode(); }
}
