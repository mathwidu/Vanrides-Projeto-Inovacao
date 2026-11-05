package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Locale;
import java.util.UUID;

/** Private draft and human confirmation; only opaque references leave the API. */
final class WaPassengerDraftStore {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;

    WaPassengerDraftStore(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.crypto = crypto;
    }

    ObjectNode load(UUID actor) {
        var rows =
                jdbc.query(
                        "SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?"
                                + " FOR UPDATE",
                        (rs, i) -> WaJson.object(rs.getString(1)),
                        actor);
        return !rows.isEmpty() && rows.get(0).path("passengerDraft") instanceof ObjectNode draft
                ? draft.deepCopy()
                : json();
    }

    void save(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        var rows = jdbc.query("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",
            (rs, row) -> WaJson.object(rs.getString(1)), actor.identityId());
        ObjectNode context = rows.isEmpty() ? json() : rows.get(0);
        context.remove("driverDraft");
        context.remove("attendanceDraft");
        if (draft.isEmpty()) context.remove("passengerDraft"); else context.set("passengerDraft", draft);
        context.put("conversationOwner", "PASSENGER_SETUP");
        context.put("conversationOwner", WaConversationOwnership.owner(context));
        if (!draft.isEmpty()) WaConversationOwnership.prompt(context, "PASSENGER_SETUP", event.eventId());
        jdbc.update(
                """
                INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,version,updated_at)
                VALUES (?,'PASSENGER_SETUP',?::jsonb,?,1,?)
                ON CONFLICT(identity_id) DO UPDATE SET stage='PASSENGER_SETUP',
                  context_json=EXCLUDED.context_json,
                  last_event_id=EXCLUDED.last_event_id,version=wa.conversation_state.version+1,updated_at=EXCLUDED.updated_at
                """,
                actor.identityId(),
                context.toString(),
                event.eventId(),
                Timestamp.from(clock.now()));
        jdbc.update(
                "UPDATE wa.profile SET version=version+1,updated_at=? WHERE identity_id=?",
                Timestamp.from(clock.now()),
                actor.identityId());
    }

    private static boolean needsCode(WaProfileState actor) {
        return actor.role().equals("DRIVER");
    }

    String marker(
            WaProfileState actor,
            WaInboxAccess.Context event,
            ObjectNode draft,
            UUID outbox,
            String target) {
        draft.remove("confirmation");
        boolean coded = needsCode(actor);
        String marker = coded ? WaCrypto.userCode() : "";
        ObjectNode confirmation =
                json().put("ref", ref("confirmation", UUID.randomUUID()))
                        .put("profileVersion", actor.version() + 1)
                        .put("draftDigest", digest(draft))
                        .put("targetDigest", target)
                        .put("sourceInbox", event.eventId().toString())
                        .put("outboxId", outbox.toString())
                        .put("issuedAt", clock.now().toString())
                        .put("expiresAt", clock.now().plusSeconds(300).toString());
        if (coded) {
            confirmation.put(
                    "markerHash",
                    crypto.secretHash(
                            "passenger-confirmation", actor.identityId() + ":" + marker));
        }
        draft.set("confirmation", confirmation);
        return marker;
    }

    boolean confirms(WaProfileState actor, WaInboxAccess.Context event, ObjectNode draft) {
        if (!draft.path("stage").asText().equals("REVIEW") || !draft.has("confirmation")) {
            return false;
        }
        var confirmation = draft.path("confirmation");
        boolean coded = needsCode(actor) || confirmation.has("markerHash");
        String text = WaDriverWizard.text(event);
        if (coded
                ? !text.matches("confirmar [bcdfghjkmnpqrstvwxyz]{8}")
                : !WaPassengerAnswers.loose(text).equals("confirmar")) {
            return false;
        }
        try {
            Instant issued = Instant.parse(confirmation.path("issuedAt").asText());
            Instant received = Instant.parse(event.normalizedPayload().path("occurredAt").asText());
            return confirmation.path("profileVersion").asLong(-1) == actor.version()
                    && Instant.parse(confirmation.path("expiresAt").asText()).isAfter(clock.now())
                    && (coded
                            ? !received.isBefore(issued.truncatedTo(ChronoUnit.SECONDS))
                            : received.isAfter(issued))
                    && !event.eventId().toString().equals(confirmation.path("sourceInbox").asText())
                    && digest(draft).equals(confirmation.path("draftDigest").asText())
                    && (!coded
                            || WaCrypto.same(
                                    confirmation.path("markerHash").asText(),
                                    crypto.secretHash(
                                            "passenger-confirmation",
                                            actor.identityId()
                                                    + ":"
                                                    + text.substring(10).toUpperCase(Locale.ROOT))))
                    && Boolean.TRUE.equals(
                            jdbc.queryForObject(
                                    """
                                    SELECT EXISTS(SELECT 1 FROM wa.message_outbox o JOIN wa.command_receipt r ON r.command_id=o.command_id
                                    WHERE o.id=? AND o.recipient_identity_id=? AND r.inbox_event_id=?
                                    AND o.policy_version='passenger-setup-v1' AND o.state %s)
                                    """
                                            .formatted(
                                                    coded
                                                            ? "NOT IN ('CANCELED','EXPIRED')"
                                                            : "IN ('SUBMITTED','DELIVERED','READ')"),
                                    Boolean.class,
                                    UUID.fromString(confirmation.path("outboxId").asText()),
                                    actor.identityId(),
                                    UUID.fromString(confirmation.path("sourceInbox").asText())));
        } catch (IllegalArgumentException | java.time.DateTimeException invalid) {
            return false;
        }
    }

    static ObjectNode json() {
        return WaJson.MAPPER.createObjectNode();
    }

    static String ref(String kind, UUID id) {
        return WaDriverDraftStore.ref(kind, id);
    }

    static String digest(ObjectNode draft) {
        ObjectNode copy = draft.deepCopy();
        copy.remove("confirmation");
        return WaCrypto.digest(WaJson.canonical(copy));
    }
}
