package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Confirmation is bound to one owner, review, source message and short validity window. */
final class WaRunDraftStore {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    WaRunDraftStore(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto) { this.jdbc = jdbc; this.clock = clock; this.crypto = crypto; }
    ObjectNode load(UUID actor) {
        var rows = jdbc.query("SELECT context_json->'runDraft' FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",
            (rs, row) -> rs.getString(1), actor);
        if (rows.isEmpty() || rows.get(0) == null) return WaDriverDraftStore.json();
        ObjectNode draft = WaJson.object(rows.get(0));
        try { if (Instant.parse(draft.path("expiresAt").asText()).isAfter(clock.now())) return draft; }
        catch (RuntimeException invalid) { /* Expired drafts only allow a fresh selection. */ }
        return WaDriverDraftStore.json();
    }
    void save(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        var rows = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",
            (rs, row) -> WaJson.object(rs.getString(1)), actor.identityId());
        ObjectNode context = rows.isEmpty() ? WaDriverDraftStore.json() : rows.get(0);
        if (draft.isEmpty()) context.remove("runDraft"); else context.set("runDraft", draft);
        context.put("conversationOwner", draft.isEmpty() ? "READY" : "RUN_SETUP");
        context.put("conversationOwner", WaConversationOwnership.owner(context));
        if (!draft.isEmpty()) WaConversationOwnership.prompt(context, "RUN_SETUP", event.eventId());
        jdbc.update("""
            INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at)
            VALUES (?,'RUN_SETUP',?::jsonb,?,?) ON CONFLICT(identity_id) DO UPDATE SET
              stage=EXCLUDED.stage,context_json=EXCLUDED.context_json,last_event_id=EXCLUDED.last_event_id,
              version=wa.conversation_state.version+1,updated_at=EXCLUDED.updated_at
            """, actor.identityId(), context.toString(), event.eventId(), Timestamp.from(clock.now()));
        jdbc.update("UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?", Timestamp.from(clock.now()), actor.identityId());
    }
    String marker(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft, UUID outbox) {
        String marker = WaCrypto.userCode();
        ObjectNode confirmation = WaDriverDraftStore.json().put("ref", "run_confirmation_" + UUID.randomUUID().toString().replace("-", ""))
            .put("profileVersion", actor.version() + 1).put("sourceInbox", event.eventId().toString()).put("outboxId", outbox.toString())
            .put("issuedAt", clock.now().toString()).put("expiresAt", clock.now().plusSeconds(300).toString())
            .put("draftDigest", WaDriverDraftStore.digest(draft))
            .put("markerHash", crypto.secretHash("run-confirmation", actor.identityId() + ":" + marker));
        draft.set("confirmation", confirmation); return marker;
    }
    boolean confirms(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        String text = WaAttendanceAnswers.normalize(event.normalizedPayload().path("text").asText());
        if (!draft.path("stage").asText().equals("REVIEW") || !text.matches("confirmar [bcdfghjkmnpqrstvwxyz]{8}")) return false;
        var confirmation = draft.path("confirmation");
        try {
            Instant received = Instant.parse(event.normalizedPayload().path("occurredAt").asText());
            return actor.version() == confirmation.path("profileVersion").asLong(-1)
                && Instant.parse(confirmation.path("expiresAt").asText()).isAfter(clock.now())
                && !received.isBefore(Instant.parse(confirmation.path("issuedAt").asText()).truncatedTo(ChronoUnit.SECONDS))
                && !event.eventId().toString().equals(confirmation.path("sourceInbox").asText())
                && WaDriverDraftStore.digest(draft).equals(confirmation.path("draftDigest").asText())
                && WaCrypto.same(confirmation.path("markerHash").asText(), crypto.secretHash("run-confirmation",
                    actor.identityId() + ":" + text.substring(10).toUpperCase(Locale.ROOT)))
                && Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id
                      WHERE o.id=? AND o.recipient_identity_id=? AND r.inbox_event_id=? AND o.policy_version='run-start-v1'
                        AND o.state NOT IN ('CANCELED','EXPIRED'))
                    """, Boolean.class, UUID.fromString(confirmation.path("outboxId").asText()), actor.identityId(),
                    UUID.fromString(confirmation.path("sourceInbox").asText())));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) { return false; }
    }
}
