package com.vanrides.vanrides.whatsapp.transport;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** VanRides delivery-v1: 30 authorizations/60s per instance and 12 per recipient, not provider capacity. */
final class WaDeliveryQuota {
    static final String AVAILABLE = """
        (SELECT count(*) FROM wa.message_attempt qa JOIN wa.service_credential qc ON qc.id=qa.credential_id
          WHERE qa.instance_id=delivery_scope.instance_id AND qc.environment=delivery_scope.environment
            AND qa.started_at>?)<30
        AND (SELECT count(*) FROM wa.message_attempt qa JOIN wa.service_credential qc ON qc.id=qa.credential_id
          JOIN wa.message_outbox qo ON qo.id=qa.outbox_id
          WHERE qa.instance_id=delivery_scope.instance_id AND qc.environment=delivery_scope.environment
            AND qo.recipient_identity_id=o.recipient_identity_id AND qa.started_at>?)<12
        """;
    static final String ORDER = """
        ORDER BY CASE o.purpose WHEN 'APPROACH' THEN 0 WHEN 'RESPONSE' THEN 10 WHEN 'REMINDER' THEN 20 ELSE 30 END,
          o.created_at,o.id
        """;
    private final JdbcTemplate jdbc;
    private final String environment;
    WaDeliveryQuota(JdbcTemplate jdbc, String environment) { this.jdbc=jdbc; this.environment=environment; }

    Instant next(String instance, UUID recipient, Instant now) {
        Instant global = slot(instance, null, 30, now), personal = slot(instance, recipient, 12, now);
        return global.isAfter(personal) ? global : personal;
    }
    private Instant slot(String instance, UUID recipient, int limit, Instant now) {
        var rows = jdbc.query("""
            SELECT a.started_at FROM wa.message_attempt a JOIN wa.service_credential c ON c.id=a.credential_id
            JOIN wa.message_outbox o ON o.id=a.outbox_id
            WHERE a.instance_id=? AND c.environment=? AND a.started_at>?
              AND (?::uuid IS NULL OR o.recipient_identity_id=?::uuid)
            ORDER BY a.started_at DESC,a.id DESC OFFSET ? LIMIT 1
            """, (rs, index) -> rs.getTimestamp(1).toInstant(), instance, environment,
            Timestamp.from(now.minusSeconds(60)), recipient, recipient, limit-1);
        return rows.isEmpty() ? now : rows.get(0).plusSeconds(60);
    }
    void defer(UUID box, Instant slot, Instant deadline) {
        if (slot.isBefore(deadline)) jdbc.update("""
            UPDATE wa.message_outbox SET state='PENDING',not_before=?,lease_credential_id=NULL,lease_token_hash=NULL,
              lease_expires_at=NULL,version=version+1 WHERE id=?
            """, Timestamp.from(slot), box);
        else jdbc.update("""
            UPDATE wa.message_outbox SET state='EXPIRED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,
              lease_credential_id=NULL,lease_token_hash=NULL,lease_expires_at=NULL,version=version+1 WHERE id=?
            """, box);
    }
}
