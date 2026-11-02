package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
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
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Explicit response policies only. A reservation is never retried blindly. */
@Service
public class WaOutboxService {
    private static final String SOURCE = WaDeliveryOrigins.SOURCE;
    private final JdbcTemplate jdbc;
    private final WaTransportAccess access;
    private final WaDeliveryReconciler reconciliation;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaDeliveryConnection connection;
    private final WaDeliveryAttempts attempts;
    private final WaDeliveryMaintenance maintenance;
    private final WaDeliveryQuota quota;
    private final WaPrivateInviteDelivery privateInvites;
    private final WaPassengerDelivery passengers;
    private final WaAttendanceDelivery attendance;
    private final com.vanrides.vanrides.whatsapp.tracking.WaStartDelivery starts;
    public WaOutboxService(JdbcTemplate jdbc, WaTransportAccess access, WaDeliveryReconciler reconciliation,
                           WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.access = access; this.reconciliation = reconciliation; this.settings = settings; this.clock = clock;
        this.connection = new WaDeliveryConnection(jdbc, settings, clock);
        this.attempts = new WaDeliveryAttempts(jdbc);
        this.maintenance = new WaDeliveryMaintenance(jdbc, settings, clock);
        this.quota = new WaDeliveryQuota(jdbc, settings.environment());
        this.privateInvites = new WaPrivateInviteDelivery(jdbc, clock, new WaCrypto(settings));
        this.passengers = new WaPassengerDelivery(jdbc, settings, access, clock);
        this.attendance = new WaAttendanceDelivery(jdbc, settings, access, clock);
        this.starts = new com.vanrides.vanrides.whatsapp.tracking.WaStartDelivery(jdbc, clock, settings);
    }
    private record Item(UUID id, UUID actor, String policy, String state, String payload, Instant expires, UUID credential,
                        String tokenHash, long generation, Instant leaseExpires) {
        static Item read(ResultSet rs, int row) throws SQLException {
            Timestamp deadline = rs.getTimestamp("lease_expires_at");
            return new Item(rs.getObject("id", UUID.class), rs.getObject("recipient_identity_id", UUID.class),
                rs.getString("policy_version"), rs.getString("state"), rs.getString("payload_json"), rs.getTimestamp("expires_at").toInstant(),
                rs.getObject("lease_credential_id", UUID.class), rs.getString("lease_token_hash"), rs.getLong("lease_generation"),
                deadline == null ? null : deadline.toInstant());
        }
    }
    private record Retry(String state, Instant notBefore) {
        static Retry terminal(String state) { return new Retry(state, null); }
    }
    @Transactional
    public ObjectNode claim(WaPrincipal worker, ObjectNode request) {
        WaJson.fields(request, "batchLimit", "leaseSeconds");
        int limit = integer(request, "batchLimit", 10), seconds = integer(request, "leaseSeconds", 60);
        var grant = access.lock(worker, WaPrincipal.Kind.DELIVERY_WORKER);
        ObjectNode response = WaJson.object("{}"); var items = response.putArray("items");
        if (!settings.enabled()) throw WaProblem.denied();
        if (connection.lock(grant.instance(), false)) {
            reconciliation.refresh(grant.instance(), settings.environment());
            maintenance.expireUnsent(grant.instance());
            var selected = connection.open(grant.instance()) ? jdbc.query("SELECT o.* " + SOURCE + """
                  AND o.not_before<=? AND o.expires_at>?
                  AND (o.state='PENDING' OR (o.state='LEASED' AND o.lease_expires_at<=?))
                  AND
                """ + WaDeliveryAttempts.AVAILABLE + " AND " + WaDeliveryQuota.AVAILABLE + WaDeliveryQuota.ORDER + " LIMIT ? FOR UPDATE OF o SKIP LOCKED",
                Item::read, grant.instance(), settings.environment(), ts(clock.now()), ts(clock.now()), ts(clock.now()),
                ts(clock.now().minusSeconds(60)), ts(clock.now().minusSeconds(60)), limit) : List.<Item>of();
            for (Item item : selected) {
                privateInvites.cancelInvalid(item.actor());
                if ("CANCELED".equals(jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, item.id()))) continue;
                if (!access.allowedIdentity(item.actor()) || !relevant(item, grant.instance(), false) || !starts.relevant(item.id())) {
                    jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?", item.id());
                    continue;
                }
                access.revalidate(grant);
                Instant until = minimum(clock.now().plusSeconds(seconds), grant.expiresAt(), item.expires());
                if (!until.isAfter(clock.now())) continue;
                String token = WaCrypto.randomToken();
                jdbc.update("""
                    UPDATE wa.message_outbox SET state='LEASED',lease_credential_id=?,lease_token_hash=?,
                      lease_generation=lease_generation+1,lease_expires_at=?,version=version+1 WHERE id=?
                    """, grant.credentialId(), WaCrypto.digest(token), ts(until), item.id());
                items.add(WaJson.object(WaJson.write(Map.of("outboxId", item.id().toString(), "purpose", reminder(item) ? "ATTENDANCE_REMINDER" : "COMMAND_RESPONSE",
                    "leaseToken", token, "leaseExpiresAt", until.toString(), "expiresAt", item.expires().toString()))));
            }
        }
        if (!items.isEmpty() && !connection.open(grant.instance()))
            throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        access.revalidate(grant); response.put("serverTime", clock.now().toString()); return response;
    }
    @Transactional
    public WaDecision authorize(WaPrincipal worker, UUID id, ObjectNode request) {
        WaJson.fields(request, "leaseToken"); String token = opaque(request, "leaseToken");
        var grant = access.lock(worker, WaPrincipal.Kind.DELIVERY_WORKER);
        if (!settings.enabled()) throw WaProblem.denied();
        connection.lock(grant.instance(), true);
        reconciliation.refresh(grant.instance(), settings.environment());
        List<Item> rows = jdbc.query("SELECT o.* " + SOURCE + " AND o.id=? FOR UPDATE OF o", Item::read, grant.instance(), settings.environment(), id);
        if (rows.size() != 1) throw WaProblem.missing();
        Item item = rows.get(0);
        access.revalidate(grant);
        if (!grant.credentialId().equals(item.credential()) || !WaCrypto.same(WaCrypto.digest(token), item.tokenHash())
                || item.leaseExpires() == null || !item.leaseExpires().isAfter(clock.now())) throw WaProblem.missing();
        if (!"LEASED".equals(item.state())) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        int attemptNumber = attempts.next(id, item.generation());
        if (!item.expires().isAfter(clock.now())) {
            jdbc.update("UPDATE wa.message_outbox SET state='EXPIRED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?", id);
            return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        }
        if (!connection.open(grant.instance())) return WaDecision.error(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        // Fence changes to the recipient's eligibility through the reservation commit.
        boolean relevant = true;
        if (passenger(item) || attendancePolicy(item)) relevant = relevant(item, grant.instance(), true);
        else jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE", item.actor());
        fence(grant, item);
        if (!access.allowedIdentity(item.actor()) || !relevant) {
            jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?", id);
            return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        }
        var aliases = reminder(item) ? attendance.aliases(item.id(), grant.instance())
            : passenger(item) ? passengers.aliases(item.id(), grant.instance()) : jdbc.queryForList("""
            SELECT a.alias_type,a.alias_value FROM wa.identity_alias a
            JOIN wa.integration_inbox e ON a.id::text=e.payload_json->>'senderAliasId'
            JOIN wa.command_receipt r ON r.inbox_event_id=e.id
            JOIN wa.message_outbox o ON o.command_id=r.command_id
            WHERE o.id=? AND a.identity_id=? AND a.provider='EVOLUTION' AND a.instance_id=?
              AND a.alias_type IN ('PN','LID') FOR SHARE OF a
            """, id, item.actor(), grant.instance());
        fence(grant, item);
        if (aliases.isEmpty()) return WaDecision.error(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        String address = (String) aliases.get(0).get("alias_value"), type = (String) aliases.get(0).get("alias_type");
        // Alias values are canonical provider JIDs, never a numeric reinterpretation of LID.
        if (!(type.equals("PN") && address.matches("[0-9]{1,32}@s\\.whatsapp\\.net")
                || type.equals("LID") && address.matches("[0-9]{1,32}@lid")))
            return WaDecision.error(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        Boolean inflight = jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.message_attempt WHERE instance_id=? AND credential_id IS NOT NULL AND state='SUBMITTING')
            """, Boolean.class, grant.instance());
        if (Boolean.TRUE.equals(inflight)) return WaDecision.error(WaProblem.Code.RATE_LIMITED);
        ObjectNode content = content(item.payload());
        content = privateInvites.reveal(id, item.actor(), content);
        content = starts.reveal(id, item.actor(), content);
        if (content != null && passenger(item)) content = passengers.render(id, content);
        fence(grant, item);
        if (content == null) return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
        if (passenger(item) || attendancePolicy(item)) {
            boolean stillRelevant = relevant(item, grant.instance(), true);
            fence(grant, item);
            if (!stillRelevant) {
                jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',version=version+1 WHERE id=?", id);
                return WaDecision.error(WaProblem.Code.ACTION_NOT_ALLOWED);
            }
        }
        access.revalidate(grant);
        if (!item.leaseExpires().isAfter(clock.now())) throw WaProblem.missing();
        Instant runDeadline = starts.deadline(id);
        if (!runDeadline.isAfter(clock.now())) {
            jdbc.update("UPDATE wa.message_outbox SET state='EXPIRED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?", id);
            return WaDecision.error(WaProblem.Code.INTENT_EXPIRED);
        }
        Instant intentDeadline = minimum(item.expires(), runDeadline,
            reminder(item) ? attendance.deadline(id) : passenger(item) ? passengers.deadline(id) : Instant.MAX);
        Instant slot = quota.next(grant.instance(), item.actor(), clock.now());
        fence(grant, item);
        if (slot.isAfter(clock.now())) {
            quota.defer(id, slot, intentDeadline);
            return WaDecision.error(WaProblem.Code.RATE_LIMITED);
        }
        Instant before = minimum(clock.now().plusSeconds(10), item.leaseExpires(), intentDeadline, grant.expiresAt(), connection.deadline(grant.instance()));
        if (!before.isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        String attempt = WaCrypto.randomToken();
        jdbc.update("""
            INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,
              instance_id,credential_id,submit_before,started_at) VALUES (?,?,?,?,?,?,?,?,?)
            """, UUID.randomUUID(), id, attemptNumber, item.generation(), WaCrypto.digest(attempt), grant.instance(),
            grant.credentialId(), ts(before), ts(clock.now()));
        fence(grant, item);
        if (!runDeadline.isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        return WaDecision.ok(Map.of("outboxId", id.toString(), "attemptToken", attempt, "submitBefore", before.toString(),
            "recipient", Map.of("addressType", type.equals("LID") ? "LID" : "JID", "address", address),
            "content", content, "serverTime", clock.now().toString()));
    }
    @Transactional
    public ObjectNode result(WaPrincipal worker, UUID id, ObjectNode request) {
        String outcome = WaJson.text(request, "outcome", 1, 16);
        if ("SUBMITTED".equals(outcome)) WaJson.fields(request, "attemptToken", "outcome", "externalMessageId");
        else if (Set.of("UNKNOWN", "FAILED", "NOT_SUBMITTED").contains(outcome)) WaJson.fields(request, "attemptToken", "outcome", "failureCode");
        else throw WaProblem.invalid();
        String token = opaque(request, "attemptToken");
        String external = null, failure = null;
        if (outcome.equals("SUBMITTED")) {
            external = WaJson.text(request, "externalMessageId", 1, 160);
            if (!external.matches("[A-Za-z0-9_-]+")) throw WaProblem.invalid();
        } else {
            failure = WaJson.text(request, "failureCode", 1, 32);
            if (!Set.of("TIMEOUT", "CHANNEL_UNAVAILABLE", "REJECTED").contains(failure)) throw WaProblem.invalid();
            if (outcome.equals("FAILED") && failure.equals("TIMEOUT")) throw WaProblem.invalid();
            if (outcome.equals("NOT_SUBMITTED") && !failure.equals("CHANNEL_UNAVAILABLE")) throw WaProblem.invalid();
        }
        var grant = access.lock(worker, WaPrincipal.Kind.DELIVERY_WORKER);
        connection.lock(grant.instance(), true);
        var boxes = jdbc.query("SELECT o.* " + SOURCE + " AND o.id=? FOR UPDATE OF o", Item::read, grant.instance(), settings.environment(), id);
        if (boxes.size() != 1) throw WaProblem.missing();
        var attempts = jdbc.queryForList("""
            SELECT id,attempt_number,lease_generation,state,result_request_hash,result_json::text FROM wa.message_attempt
            WHERE outbox_id=? AND instance_id=? AND credential_id=? AND submit_token_hash=? FOR UPDATE
            """, id, grant.instance(), grant.credentialId(), WaCrypto.digest(token));
        if (attempts.size() != 1) throw WaProblem.missing();
        access.revalidate(grant); var attempt = attempts.get(0);
        String hash = WaCrypto.digest(WaJson.canonical(request));
        if (attempt.get("result_request_hash") != null) {
            if (!hash.equals(attempt.get("result_request_hash"))) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
            return WaJson.object((String) attempt.get("result_json"));
        }
        if (external != null && Boolean.TRUE.equals(jdbc.queryForObject("""
            SELECT EXISTS(SELECT 1 FROM wa.message_attempt WHERE instance_id=? AND external_message_id=? AND id<>?)
            """, Boolean.class, grant.instance(), external, attempt.get("id"))))
            throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        if (!maintenance.expire(id, grant.instance())) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        Item item = boxes.get(0);
        Retry next = outcome.equals("NOT_SUBMITTED") ? retryState(item, grant, attempt) : Retry.terminal(outcome);
        jdbc.update("UPDATE wa.message_attempt SET state=?,external_message_id=?,error_code=?,completed_at=? WHERE id=?",
            outcome, external, failure, ts(clock.now()), attempt.get("id"));
        if (next.notBefore() != null) {
            jdbc.update("""
                UPDATE wa.message_outbox SET state='PENDING',not_before=?,lease_credential_id=NULL,
                  lease_token_hash=NULL,lease_expires_at=NULL,version=version+1 WHERE id=?
                """, ts(next.notBefore()), id);
        } else {
            jdbc.update("""
                UPDATE wa.message_outbox SET state=CASE WHEN state IN ('EXPIRED','CANCELED') THEN state ELSE ? END,
                  private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?
                """, next.state(), id);
            if (outcome.equals("NOT_SUBMITTED")) jdbc.update("""
                UPDATE wa.message_outbox SET lease_credential_id=NULL,lease_token_hash=NULL,lease_expires_at=NULL WHERE id=?
                """, id);
        }
        reconciliation.refresh(grant.instance(), settings.environment());
        String finalState = jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, id);
        ObjectNode response = WaJson.object(WaJson.write(Map.of("outboxId", id.toString(), "state", finalState, "serverTime", clock.now().toString())));
        jdbc.update("UPDATE wa.message_attempt SET result_request_hash=?,result_json=?::jsonb WHERE id=?", hash, response.toString(), attempt.get("id"));
        access.revalidate(grant); return response;
    }
    private Retry retryState(Item item, WaTransportAccess.Grant grant, Map<String, Object> attempt) {
        // An already abandoned/terminal attempt or outbox cannot be reopened by a late receipt.
        if (!"LEASED".equals(item.state())) return Retry.terminal(item.state());
        if (!"SUBMITTING".equals(attempt.get("state"))
                || item.generation() != ((Number) attempt.get("lease_generation")).longValue()) return Retry.terminal("UNKNOWN");
        if (!item.expires().isAfter(clock.now())) return Retry.terminal("EXPIRED");
        if (passenger(item) || attendancePolicy(item)) {
            if (!relevant(item, grant.instance(), true)) return Retry.terminal("CANCELED");
        } else jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE", item.actor());
        access.revalidate(grant);
        if (!settings.enabled() || !access.allowedIdentity(item.actor()) || !starts.relevant(item.id())) return Retry.terminal("CANCELED");
        ObjectNode payload = starts.reveal(item.id(), item.actor(), privateInvites.reveal(item.id(), item.actor(), content(item.payload())));
        access.revalidate(grant);
        if (payload == null) return Retry.terminal("CANCELED");
        Instant deadline = minimum(item.expires(), reminder(item) ? attendance.deadline(item.id())
            : passenger(item) ? passengers.deadline(item.id()) : Instant.MAX);
        int number = ((Number) attempt.get("attempt_number")).intValue();
        Instant notBefore = clock.now().plusSeconds(number == 1 ? 5 : 15);
        if (!deadline.isAfter(clock.now()) || number < 3 && !deadline.isAfter(notBefore)) return Retry.terminal("EXPIRED");
        return number < 3 ? new Retry("PENDING", notBefore) : Retry.terminal("FAILED");
    }
    private static boolean passenger(Item item) { return item.policy().equals(WaPassengerResponses.POLICY); }
    private static boolean reminder(Item item) { return item.policy().equals(WaAttendanceDelivery.POLICY); }
    private static boolean attendancePolicy(Item item) { return reminder(item) || item.policy().equals("attendance-v1"); }
    private boolean relevant(Item item, String instance, boolean lock) {
        if (reminder(item)) return attendance.current(item.id(), instance, lock);
        if (attendancePolicy(item)) return attendance.actorCurrent(item.actor(), lock);
        return !passenger(item) || passengers.current(item.id(), instance, lock);
    }
    private ObjectNode content(String stored) {
        ObjectNode content = WaJson.object(stored);
        WaJson.fields(content, "type", "text", "referenceTime");
        if (!"TEXT".equals(content.path("type").asText())) throw WaProblem.denied();
        WaJson.text(content, "text", 1, 2000);
        try { Instant.parse(WaJson.text(content, "referenceTime", 20, 40)); }
        catch (RuntimeException invalid) { throw WaProblem.denied(); }
        return content;
    }
    private static int integer(ObjectNode request, String field, int max) {
        var value = request.get(field);
        if (value == null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<1 || value.intValue()>max)
            throw WaProblem.invalid();
        return value.intValue();
    }
    private void fence(WaTransportAccess.Grant grant, Item item) {
        access.revalidate(grant);
        if (item.leaseExpires() == null || !item.leaseExpires().isAfter(clock.now())) throw WaProblem.missing();
        if (!connection.open(grant.instance())) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
    }
    private static String opaque(ObjectNode request, String field) {
        String token = WaJson.text(request, field, 43, 43); WaDpopVerifier.canonical32(token); return token;
    }
    private static Instant minimum(Instant... values) { return java.util.Arrays.stream(values).min(Instant::compareTo).orElseThrow(); }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
