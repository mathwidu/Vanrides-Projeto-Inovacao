package com.vanrides.vanrides.whatsapp.pairing;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Callers serialize the operation's scope before inspecting or saving replay. */
final class WaReplayStore {
    private record Replay(String requestHash, byte[] ciphertext, Instant expiresAt) { }
    private final JdbcTemplate jdbc;
    private final WaCrypto crypto;
    private final WaClock clock;
    WaReplayStore(JdbcTemplate jdbc, WaCrypto crypto, WaClock clock) {
        this.jdbc = jdbc; this.crypto = crypto; this.clock = clock;
    }
    ObjectNode read(String kind, UUID scope, String key, ObjectNode body) {
        var rows = jdbc.query("""
                SELECT request_hash,response_ciphertext,expires_at FROM wa.operation_replay
                WHERE operation_kind=? AND scope_id=? AND request_key=?
                """, (rs, row) -> new Replay(rs.getString("request_hash"), rs.getBytes("response_ciphertext"),
                rs.getTimestamp("expires_at").toInstant()), kind, scope, key);
        if (rows.isEmpty()) return null;
        Replay replay = rows.get(0);
        if (!WaCrypto.same(replay.requestHash(), requestHash(body)))
            throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        if (!replay.expiresAt().isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        return WaJson.object(crypto.decrypt(context(kind, scope, key), replay.ciphertext()));
    }
    void save(String kind, UUID scope, String key, ObjectNode request, ObjectNode response, Instant expiresAt) {
        jdbc.update("""
                INSERT INTO wa.operation_replay(id,operation_kind,scope_id,request_key,request_hash,response_ciphertext,expires_at)
                VALUES (?,?,?,?,?,?,?)
                """, UUID.randomUUID(), kind, scope, key, requestHash(request),
                crypto.encrypt(context(kind, scope, key), WaJson.write(response)), Timestamp.from(expiresAt));
    }
    private static String context(String kind, UUID scope, String key) { return kind + "\0" + scope + "\0" + key; }
    private static String requestHash(ObjectNode body) { return WaCrypto.digest(WaJson.canonical(body)); }
}
