package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Ciphertext is private delivery material, never part of payload_json or a command receipt. */
public final class WaPrivateInviteDelivery {
    static final String PASSENGER_PART = "driver-invite-passenger";
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
    public void queuePassenger(UUID box, UUID command, UUID recipient, UUID invite, String text, Instant expires) {
        Instant now = clock.now();
        Instant deadline = now.plusSeconds(300);
        if (expires.isBefore(deadline)) deadline = expires;
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,command_id,response_part,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at,private_invite_id,private_payload_ciphertext)
            VALUES (?,?,'RESPONSE',?,?,'driver-setup-v1','TEXT',?::jsonb,?,?,?,?,?)
            """, box, recipient, command, PASSENGER_PART,
            WaJson.write(Map.of("type", "TEXT", "text", "Você recebeu um convite do VanRides.", "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(deadline), Timestamp.from(now), invite,
            crypto.encrypt(aad(box, command, recipient), text));
    }
    /** Outbox is already locked; the profile fence precedes the invite fence. Revocation never waits on outbox. */
    public ObjectNode reveal(UUID box, UUID actor, ObjectNode content) {
        var rows = jdbc.queryForList("""
            SELECT o.private_invite_id,o.private_payload_ciphertext,o.command_id,o.response_part,r.identity_id
            FROM wa.message_outbox o LEFT JOIN wa.command_receipt r ON r.command_id=o.command_id WHERE o.id=?
            """, box);
        if (rows.isEmpty()) return content;
        var row = rows.get(0);
        boolean peer = PASSENGER_PART.equals(row.get("response_part"));
        if (row.get("private_invite_id") == null) return peer ? cancel(box) : content;
        var invites = jdbc.queryForList("""
            SELECT expires_at,revoked_at,max_uses,use_count FROM wa.driver_invite WHERE id=? AND driver_id=?
            """ + (peer ? " AND onboarding_identity_id=? AND NOT allow_first_contact FOR SHARE" : " FOR SHARE"),
            peer ? new Object[] {row.get("private_invite_id"), row.get("identity_id"), actor}
                : new Object[] {row.get("private_invite_id"), actor});
        if (invites.size() != 1 || invites.get(0).get("revoked_at") != null
                || !((Timestamp) invites.get(0).get("expires_at")).toInstant().isAfter(clock.now())
                || ((Number) invites.get(0).get("use_count")).intValue() >= ((Number) invites.get(0).get("max_uses")).intValue()
                || row.get("private_payload_ciphertext") == null) {
            return cancel(box);
        }
        try {
            String text = crypto.decrypt(aad(box, (UUID) row.get("command_id"), actor), (byte[]) row.get("private_payload_ciphertext"));
            if (text.length() > 2000 || !text.contains("PASSAGEIRO ")) throw new IllegalArgumentException();
            ObjectNode result = content.deepCopy(); result.put("text", text); return result;
        } catch (RuntimeException invalid) {
            return cancel(box);
        }
    }
    private ObjectNode cancel(UUID box) {
        jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,version=version+1 WHERE id=?", box);
        return null;
    }
    public void purge(UUID box) { jdbc.update("UPDATE wa.message_outbox SET private_payload_ciphertext=NULL WHERE id=?", box); }
    /** Monotonic cancellation, SKIP LOCKED: a concurrent delivery revalidates the invite itself after its profile wait. */
    public void cancelInvalid(UUID actor) {
        var ids = jdbc.query("""
            SELECT o.id FROM wa.message_outbox o JOIN wa.driver_invite i ON i.id=o.private_invite_id
            WHERE (o.recipient_identity_id=? OR i.driver_id=?) AND o.state IN ('PENDING','LEASED')
              AND (i.revoked_at IS NOT NULL OR i.expires_at<=? OR i.use_count>=i.max_uses)
              AND
            """ + WaDeliveryAttempts.UNSENT + """
            ORDER BY o.id FOR UPDATE OF o SKIP LOCKED
            """, (rs, n) -> rs.getObject(1, UUID.class), actor, actor, Timestamp.from(clock.now()));
        for (UUID id : ids) jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',private_payload_ciphertext=NULL,version=version+1 WHERE id=?", id);
    }
}
