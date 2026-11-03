package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The inbox row is the only conversation lease. Identity locks serialize new claims, never ACKs. */
@Service
public class WaInboxService {
    private static final String NONTERMINAL = "('PENDING','CLAIMED','RETRY','PAUSED')";
    private static final Set<String> NONTERMINAL_STATES = Set.of("PENDING", "CLAIMED", "RETRY", "PAUSED");
    private final JdbcTemplate jdbc;
    private final WaTransportAccess access;
    private final WaSettings settings;
    private final WaClock clock;

    public WaInboxService(JdbcTemplate jdbc, WaTransportAccess access, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.access = access; this.settings = settings; this.clock = clock;
    }

    @Transactional
    public ObjectNode claim(WaPrincipal principal, ObjectNode request) {
        if (request == null) throw WaProblem.invalid();
        WaJson.fields(request, "batchLimit", "leaseSeconds");
        int limit = integer(request, "batchLimit", 10), seconds = integer(request, "leaseSeconds", 60);
        WaTransportAccess.Grant grant = access.lock(principal, WaPrincipal.Kind.CONVERSATION_WORKER);
        if (!settings.enabled()) throw WaProblem.denied();
        ObjectNode response = WaJson.object("{}");
        var items = response.putArray("items");
        // Select the head before trying any locks. SKIP LOCKED on an ordered event scan could skip
        // a locked head and incorrectly lease its successor. There is deliberately no overscan cap:
        // an arbitrary number of busy conversations must not starve an independent conversation.
        List<UUID> identities = jdbc.query("""
            SELECT h.identity_id FROM wa.integration_inbox h
            WHERE h.instance_id=? AND h.category='MESSAGE' AND h.identity_id IS NOT NULL
              AND h.state IN %s
              AND NOT EXISTS (SELECT 1 FROM wa.integration_inbox earlier
                WHERE earlier.identity_id=h.identity_id AND earlier.category='MESSAGE'
                  AND earlier.state IN %s AND earlier.received_order<h.received_order)
            ORDER BY h.received_order
            """.formatted(NONTERMINAL, NONTERMINAL), (rs, i) -> rs.getObject(1, UUID.class), grant.instance());
        for (UUID identity : identities) {
            if (items.size() == limit) break;
            var locked = jdbc.query("SELECT id FROM wa.identity WHERE id=? FOR UPDATE SKIP LOCKED",
                (rs, i) -> rs.getObject(1, UUID.class), identity);
            if (locked.isEmpty() || !access.allowedIdentity(identity)) continue;
            ObjectNode item = claimIdentity(identity, grant, seconds);
            if (item != null) items.add(item);
        }
        access.revalidate(grant);
        response.put("serverTime", clock.now().toString());
        return response;
    }

    private ObjectNode claimIdentity(UUID identity, WaTransportAccess.Grant grant, int seconds) {
        while (true) {
            List<UUID> heads = jdbc.query("""
                SELECT id FROM wa.integration_inbox WHERE identity_id=? AND category='MESSAGE'
                  AND state IN %s ORDER BY received_order LIMIT 1
                """.formatted(NONTERMINAL), (rs, i) -> rs.getObject(1, UUID.class), identity);
            if (heads.isEmpty()) return null;
            List<Event> rows = jdbc.query("SELECT * FROM wa.integration_inbox WHERE id=? FOR UPDATE SKIP LOCKED",
                WaInboxService::event, heads.get(0));
            if (rows.isEmpty()) return null;
            Event event = rows.get(0);
            access.revalidate(grant);
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM wa.conversation_state WHERE identity_id=? AND stage='PAUSED')
                    """, Boolean.class, identity))) return null;
            // An ACK can commit between the head read and its nonblocking row lock.
            if (!NONTERMINAL_STATES.contains(event.state())) continue;
            if (!grant.instance().equals(event.instance())) return null;
            // A command transaction holds this same row through its receipt commit. Once acquired,
            // a receipt is authoritative even when it represents a controlled rejection or is old.
            if (hasCommandReceipt(event)) {
                jdbc.update("UPDATE wa.integration_inbox SET state='COMPLETED' WHERE id=?", event.id());
                continue;
            }
            Instant now = clock.now();
            if (event.state().equals("PAUSED") || event.notBefore().isAfter(now)
                    || event.state().equals("CLAIMED") && event.expiresAt().isAfter(now)) return null;
            // The uniqueness fence also protects inconsistent pre-transport/operator fixtures.
            if (Boolean.TRUE.equals(jdbc.queryForObject("""
                    SELECT EXISTS(SELECT 1 FROM wa.integration_inbox
                      WHERE identity_id=? AND state='CLAIMED' AND id<>?)
                    """, Boolean.class, identity, event.id()))) return null;
            String token = WaCrypto.randomToken();
            Instant expiry = now.plusSeconds(seconds);
            access.revalidate(grant);
            jdbc.update("""
                UPDATE wa.integration_inbox SET state='CLAIMED',lease_credential_id=?,lease_token_hash=?,
                  lease_generation=lease_generation+1,lease_expires_at=?,result_request_hash=NULL,result_json=NULL
                WHERE id=?
                """, grant.credentialId(), WaCrypto.digest(token), Timestamp.from(expiry), event.id());
            ObjectNode item = WaJson.object("{}");
            item.put("inboxEventId", event.id().toString());
            item.put("conversationRef", "conversation_" + identity);
            item.put("inboxLease", token);
            item.put("leaseExpiresAt", expiry.toString());
            item.set("message", message(event));
            return item;
        }
    }

    @Transactional
    public ObjectNode result(WaPrincipal principal, UUID eventId, String lease, ObjectNode request) {
        String outcome = validateResult(request);
        if (eventId == null) throw WaProblem.invalid();
        try { WaDpopVerifier.canonical32(lease); } catch (RuntimeException failure) { throw WaProblem.unauthorized(); }
        String hash = WaCrypto.digest(WaJson.canonical(request));
        WaTransportAccess.Grant grant = access.lock(principal, WaPrincipal.Kind.CONVERSATION_WORKER);
        List<Event> rows = jdbc.query("""
            SELECT * FROM wa.integration_inbox WHERE id=? AND instance_id=? AND category='MESSAGE'
              AND lease_credential_id=? AND lease_token_hash=? AND lease_generation>0 FOR UPDATE
            """, WaInboxService::event, eventId, grant.instance(), grant.credentialId(), WaCrypto.digest(lease));
        access.revalidate(grant);
        if (rows.size() != 1) throw WaProblem.missing();
        Event event = rows.get(0);
        if (!access.allowedIdentity(event.identity())) throw WaProblem.denied();
        if (event.resultHash() != null) {
            if (!WaCrypto.same(event.resultHash(), hash)) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
            access.revalidate(grant);
            return event.result();
        }
        boolean committed = hasCommandReceipt(event);
        if (!event.state().equals("CLAIMED") && !(event.state().equals("COMPLETED") && committed)) throw WaProblem.missing();
        revalidateLease(event, grant);
        String state = committed || outcome.equals("COMPLETED") ? "COMPLETED" : outcome;
        ObjectNode response = WaJson.object("{}");
        response.put("inboxEventId", eventId.toString());
        response.put("state", state.equals("COMPLETED") ? "PROCESSED" : state.equals("RETRY") ? "PENDING" : "PAUSED");
        response.put("serverTime", clock.now().toString());
        Instant notBefore = state.equals("RETRY") ? clock.now().plusSeconds(request.get("retryAfterSeconds").intValue()) : clock.now();
        revalidateLease(event, grant);
        // Keep credential, generation and token hash: a lost ACK can replay this exact committed
        // response after its lease deadline, until a later claim replaces all ownership fields.
        jdbc.update("""
            UPDATE wa.integration_inbox SET state=?,not_before=?,result_request_hash=?,result_json=?::jsonb WHERE id=?
            """, state, Timestamp.from(notBefore), hash, response.toString(), eventId);
        return response;
    }

    private void revalidateLease(Event event, WaTransportAccess.Grant grant) {
        access.revalidate(grant);
        if (event.expiresAt() == null || !event.expiresAt().isAfter(clock.now())) throw WaProblem.missing();
    }

    private boolean hasCommandReceipt(Event event) {
        return Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.command_receipt WHERE inbox_event_id=? AND identity_id=?)
            """, Boolean.class, event.id(), event.identity()));
    }

    private ObjectNode message(Event event) {
        ObjectNode message = WaJson.object("{}");
        ObjectNode payload = event.payload();
        message.put("sourceMessageId", reference(event.id()));
        // Exact invite/pairing codes are represented only by hashes internally and never reach a model.
        if (payload.has("driverInviteCodeHash") || payload.has("pairingCodeHash") || payload.has("passengerInviteCodeHash") || event.kind().equals("UNSUPPORTED")) {
            message.put("type", "UNSUPPORTED");
        } else if (event.kind().equals("PIN")) {
            message.put("type", "STATIC_PIN_REFERENCE");
        } else {
            message.put("type", payload.hasNonNull("replyTo") ? "REPLY" : "TEXT");
            JsonNode text = payload.get("text");
            if (text != null && text.isTextual() && !text.textValue().isEmpty()
                    && text.textValue().codePointCount(0, text.textValue().length()) <= 2000) message.put("text", text.textValue());
            if (payload.path("replyTo").isTextual()) {
                var references = jdbc.query("""
                    SELECT id FROM wa.integration_inbox
                    WHERE instance_id=? AND identity_id=? AND category='MESSAGE' AND message_id=?
                    """, (rs, i) -> reference(rs.getObject(1, UUID.class)), event.instance(), event.identity(), payload.get("replyTo").textValue());
                if (references.size() == 1) message.put("replyTo", references.get(0));
            }
        }
        return message;
    }

    private static String reference(UUID id) { return "event_" + id; }

    private static int integer(ObjectNode request, String field, int maximum) {
        JsonNode value = request.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue() < 1
                || value.intValue() > maximum) throw WaProblem.invalid();
        return value.intValue();
    }

    private static String validateResult(ObjectNode request) {
        if (request == null) throw WaProblem.invalid();
        request.fieldNames().forEachRemaining(name -> {
            if (!Set.of("outcome", "retryAfterSeconds", "failureCode").contains(name)) throw WaProblem.invalid();
        });
        String outcome = WaJson.text(request, "outcome", 1, 20);
        switch (outcome) {
            case "COMPLETED" -> WaJson.fields(request, "outcome");
            case "RETRY" -> {
                WaJson.fields(request, "outcome", "retryAfterSeconds", "failureCode");
                integer(request, "retryAfterSeconds", 60);
                if (!Set.of("DEPENDENCY_UNAVAILABLE", "INVALID_MODEL_OUTPUT").contains(WaJson.text(request, "failureCode", 1, 32)))
                    throw WaProblem.invalid();
            }
            case "PAUSED" -> {
                WaJson.fields(request, "outcome", "failureCode");
                if (!Set.of("HUMAN_SUPPORT", "INVALID_MODEL_OUTPUT").contains(WaJson.text(request, "failureCode", 1, 32)))
                    throw WaProblem.invalid();
            }
            default -> throw WaProblem.invalid();
        }
        return outcome;
    }

    private static Event event(ResultSet rs, int row) throws SQLException {
        Timestamp expiry = rs.getTimestamp("lease_expires_at");
        String result = rs.getString("result_json");
        return new Event(rs.getObject("id", UUID.class), rs.getObject("identity_id", UUID.class), rs.getString("instance_id"),
            rs.getString("state"), rs.getString("normalized_kind"), WaJson.object(rs.getString("payload_json")),
            rs.getTimestamp("not_before").toInstant(), expiry == null ? null : expiry.toInstant(),
            rs.getString("result_request_hash"), result == null ? null : WaJson.object(result));
    }

    private record Event(UUID id, UUID identity, String instance, String state, String kind, ObjectNode payload,
                         Instant notBefore, Instant expiresAt, String resultHash, ObjectNode result) { }
}
