package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Private driver state. Neither values nor confirmation markers are context projections. */
final class WaDriverDraftStore {
    record State(ObjectNode context, ObjectNode draft) { }
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    WaDriverDraftStore(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto;
    }
    State load(UUID actor) {
        List<String> rows = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",
            (rs, i) -> rs.getString(1), actor);
        ObjectNode context = rows.isEmpty() ? json() : WaJson.object(rows.get(0));
        return new State(context, context.path("driverDraft") instanceof ObjectNode draft ? draft.deepCopy() : json());
    }
    void save(WaProfileState actor, WaInboxAccess.Context event, State state, ObjectNode draft) {
        ObjectNode context = state.context().deepCopy();
        context.remove("passengerDraft");
        context.remove("attendanceDraft");
        if (draft.isEmpty()) context.remove("driverDraft"); else context.set("driverDraft", draft);
        context.put("conversationOwner", "DRIVER_SETUP");
        context.put("conversationOwner", WaConversationOwnership.owner(context));
        if (!draft.isEmpty()) WaConversationOwnership.prompt(context, "DRIVER_SETUP", event.eventId());
        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,version,updated_at)
            VALUES (?,'DRIVER_SETUP',?::jsonb,?,1,?) ON CONFLICT(identity_id) DO UPDATE SET
            context_json=EXCLUDED.context_json,last_event_id=EXCLUDED.last_event_id,
            version=wa.conversation_state.version+1,updated_at=EXCLUDED.updated_at
            """, actor.identityId(), context.toString(), event.eventId(), Timestamp.from(clock.now()));
        jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?",
            Timestamp.from(clock.now()), actor.identityId());
    }
    String marker(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, UUID outbox, String targetDigest) {
        String marker = WaCrypto.userCode();
        ObjectNode confirmation = json().put("ref", ref("confirmation", UUID.randomUUID())).put("profileVersion", actor.version() + 1)
            .put("draftDigest", digest(draft)).put("targetDigest", targetDigest).put("sourceInbox", event.eventId().toString())
            .put("outboxId", outbox.toString()).put("issuedAt", clock.now().toString()).put("expiresAt", clock.now().plusSeconds(300).toString())
            .put("markerHash", crypto.secretHash("driver-confirmation", actor.identityId() + ":" + marker));
        draft.set("confirmation", confirmation); return marker;
    }
    boolean confirms(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        if (!draft.path("stage").asText().equals("REVIEW") || !draft.path("confirmation").isObject()) return false;
        var confirmation = draft.path("confirmation");
        String text = WaConversationPolicy.normalize(event.normalizedPayload().path("text").asText());
        if (!text.matches("confirmar [bcdfghjkmnpqrstvwxyz]{8}")) return false;
        try {
            Instant issued = Instant.parse(confirmation.path("issuedAt").asText());
            Instant received = Instant.parse(event.normalizedPayload().path("occurredAt").asText());
            return confirmation.path("profileVersion").asLong(-1) == actor.version()
                && Instant.parse(confirmation.path("expiresAt").asText()).isAfter(clock.now())
                && !received.isBefore(issued.truncatedTo(ChronoUnit.SECONDS))
                && !event.eventId().toString().equals(confirmation.path("sourceInbox").asText())
                && digest(draft).equals(confirmation.path("draftDigest").asText())
                && WaCrypto.same(confirmation.path("markerHash").asText(), crypto.secretHash("driver-confirmation",
                    actor.identityId() + ":" + text.substring(10).toUpperCase(java.util.Locale.ROOT)))
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id
                      WHERE o.id=? AND o.recipient_identity_id=? AND r.inbox_event_id=? AND o.policy_version='driver-setup-v1'
                        AND o.state NOT IN ('CANCELED','EXPIRED'))
                    """, Boolean.class, UUID.fromString(confirmation.path("outboxId").asText()), actor.identityId(),
                    UUID.fromString(confirmation.path("sourceInbox").asText())));
        } catch (java.time.DateTimeException | IllegalArgumentException invalid) { return false; }
    }
    static String digest(ObjectNode draft) {
        ObjectNode values = draft.deepCopy(); values.remove("confirmation"); return WaCrypto.digest(WaJson.canonical(values));
    }
    static String ref(String kind, UUID id) { return kind + "_" + id.toString().replace("-", ""); }
    static ObjectNode json() { return WaJson.MAPPER.createObjectNode(); }
    static ObjectNode option(String kind, String ref) { return json().put("kind", kind).put("ref", ref).put("label", "Opção autorizada"); }
    static ArrayNode options() { return WaJson.MAPPER.createArrayNode(); }
}
