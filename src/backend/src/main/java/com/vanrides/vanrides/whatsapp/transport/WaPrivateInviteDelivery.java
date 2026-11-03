package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ciphertext is private delivery material, never part of payload_json or a command receipt. */
public final class WaPrivateInviteDelivery {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaCrypto crypto;
    public WaPrivateInviteDelivery(JdbcTemplate jdbc, WaClock clock, WaCrypto crypto) {
        this.jdbc = jdbc; this.clock = clock; this.crypto = crypto;
    }
    public static String aad(UUID box, UUID command, UUID actor) { return "driver-invite:" + box + ":" + command + ":" + actor; }
    public void attach(UUID box, UUID command, UUID actor, UUID invite, String text, Instant expires) {
        jdbc.update("UPDATE wa.message_outbox SET private_invite_id=?,private_payload_ciphertext=?,expires_at=LEAST(expires_at,?) WHERE id=? AND recipient_identity_id=?",
            invite, crypto.encrypt(aad(box, command, actor), text), Timestamp.from(expires), box, actor);
    }
    /** Outbox is already locked; the profile fence precedes the invite fence. Revocation never waits on outbox. */
    public ObjectNode reveal(UUID box, UUID actor, ObjectNode content) {
        var rows = jdbc.queryForList("SELECT private_invite_id,private_payload_ciphertext,command_id FROM wa.message_outbox WHERE id=?", box);
        if (rows.isEmpty() || rows.get(0).get("private_invite_id") == null) return content;
        var row = rows.get(0);
        var invites = jdbc.queryForList("""
            SELECT expires_at,revoked_at,max_uses,use_count FROM wa.driver_invite WHERE id=? AND driver_id=? FOR SHARE
            """, row.get("private_invite_id"), actor);
        if (invites.size() != 1 || invites.get(0).get("revoked_at") != null
                || !((Timestamp) invites.get(0).get("expires_at")).toInstant().isAfter(clock.now())
                || ((Number) invites.get(0).get("use_count")).intValue() >= ((Number) invites.get(0).get("max_uses")).intValue()
                || row.get("private_payload_ciphertext") == null) {
            jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,version=version+1 WHERE id=?", box);
            return null;
        }
        try {
            String text = crypto.decrypt(aad(box, (UUID) row.get("command_id"), actor), (byte[]) row.get("private_payload_ciphertext"));
            if (text.length() > 2000 || !text.contains("PASSAGEIRO ")) throw new IllegalArgumentException();
            ObjectNode result = content.deepCopy(); result.put("text", text); return result;
        } catch (RuntimeException invalid) {
            jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,version=version+1 WHERE id=?", box);
            return null;
        }
    }
    public void purge(UUID box) { jdbc.update("UPDATE wa.message_outbox SET private_payload_ciphertext=NULL WHERE id=?", box); }
    /** Monotonic cancellation, SKIP LOCKED: a concurrent delivery revalidates the invite itself after its profile wait. */
    public void cancelInvalid(UUID actor) {
        var ids = jdbc.query("""
            SELECT o.id FROM wa.message_outbox o JOIN wa.driver_invite i ON i.id=o.private_invite_id
            WHERE o.recipient_identity_id=? AND o.state IN ('PENDING','LEASED')
              AND (i.revoked_at IS NOT NULL OR i.expires_at<=? OR i.use_count>=i.max_uses)
              AND
            """ + WaDeliveryAttempts.UNSENT + """
            ORDER BY o.id FOR UPDATE OF o SKIP LOCKED
            """, (rs, n) -> rs.getObject(1, UUID.class), actor, Timestamp.from(clock.now()));
        for (UUID id : ids) jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,version=version+1 WHERE id=?", id);
    }
}
