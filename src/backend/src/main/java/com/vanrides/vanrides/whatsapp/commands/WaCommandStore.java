package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaDecision;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;

@Service
public final class WaCommandStore {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    public WaCommandStore(JdbcTemplate jdbc, WaClock clock) { this.jdbc = jdbc; this.clock = clock; }
    public WaProfileState profile(UUID identity) {
        List<WaProfileState> values = jdbc.query("SELECT * FROM wa.profile WHERE identity_id=? FOR UPDATE", WaProfileState::read, identity);
        return values.isEmpty() ? WaProfileState.absent(identity) : values.get(0);
    }
    public WaDecision replay(WaInboxAccess.Context context, String key, String hash) {
        List<Map<String, Object>> rows = jdbc.queryForList("""
            SELECT inbox_event_id,request_hash,result_json::text,expires_at FROM wa.command_receipt
            WHERE identity_id=? AND (inbox_event_id=? OR idempotency_key=?) FOR UPDATE
            """, context.identityId(), context.eventId(), key);
        if (rows.isEmpty()) return null;
        if (rows.size() != 1 || !context.eventId().equals(rows.get(0).get("inbox_event_id"))
                || !hash.equals(rows.get(0).get("request_hash"))) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        if (!((Timestamp) rows.get(0).get("expires_at")).toInstant().isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        ObjectNode result = WaJson.object((String) rows.get(0).get("result_json"));
        return result.has("errorCode") ? WaDecision.error(WaProblem.Code.valueOf(result.get("errorCode").asText())) : new WaDecision(result, null);
    }
    public void save(UUID commandId, WaInboxAccess.Context context, String key, String command, String hash, WaDecision decision) {
        Object result = decision.error() == null ? decision.body() : Map.of("errorCode", decision.error().name());
        jdbc.update("""
            INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
            VALUES (?,?,?,?,?,?,?::jsonb,?,?)
            """, commandId, context.identityId(), context.eventId(), key, command, hash, WaJson.write(result),
            Timestamp.from(clock.now()), Timestamp.from(clock.now().plusSeconds(86400)));
    }
    public boolean attempt(UUID actor, String action) {
        Instant window = Instant.ofEpochSecond(clock.now().getEpochSecond() / 300 * 300);
        jdbc.update("INSERT INTO wa.actor_attempt_window(identity_id,action,window_start,attempts) VALUES (?,?,?,0) ON CONFLICT DO NOTHING",
            actor, action, Timestamp.from(window));
        Integer attempts = jdbc.queryForObject("SELECT attempts FROM wa.actor_attempt_window WHERE identity_id=? AND action=? AND window_start=? FOR UPDATE",
            Integer.class, actor, action, Timestamp.from(window));
        if (attempts == null || attempts >= 5) return false;
        jdbc.update("UPDATE wa.actor_attempt_window SET attempts=attempts+1 WHERE identity_id=? AND action=? AND window_start=?",
            actor, action, Timestamp.from(window));
        return true;
    }
    public void audit(UUID actor, WaPrincipal worker, String actorKind, String action, UUID resource, String correlation) {
        jdbc.update("""
            INSERT INTO wa.audit_event(id,actor_identity_id,credential_id,actor_kind,action,resource_id,correlation_id,created_at)
            VALUES (?,?,?,?,?,?,?,?)
            """, UUID.randomUUID(), actor, worker.credentialId(), actorKind, action, resource, correlation, Timestamp.from(clock.now()));
    }
}
