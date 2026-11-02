package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/** Called while the per-instance delivery transaction lock is held; callbacks remain durable until matched. */
@Component
public final class WaDeliveryReconciler {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    public WaDeliveryReconciler(JdbcTemplate jdbc, WaClock clock) { this.jdbc = jdbc; this.clock = clock; }

    public void refresh(String instance, String environment) {
        refresh(instance, environment, null);
    }
    public void refresh(String instance, String environment, UUID box) {
        var callbacks = jdbc.queryForList("""
            SELECT i.id AS receipt_id,a.id AS attempt_id,o.id AS outbox_id,i.payload_json->>'status' AS callback_status
            FROM wa.integration_inbox i JOIN wa.message_attempt a
              ON a.instance_id=i.instance_id AND a.external_message_id=i.payload_json->>'externalMessageId'
            JOIN wa.message_outbox o ON o.id=a.outbox_id
            """ + WaDeliveryOrigins.SCOPE + """
            WHERE i.provider='EVOLUTION' AND i.instance_id=delivery_scope.instance_id
              AND i.category='STATUS' AND i.state='PENDING' AND a.credential_id IS NOT NULL AND
            """ + WaDeliveryOrigins.ELIGIBLE + """
              AND (?::uuid IS NULL OR o.id=?::uuid)
            ORDER BY i.created_at,i.id LIMIT 100 FOR UPDATE OF o,a,i SKIP LOCKED
            """, instance, environment, box, box);
        for (var row : callbacks) {
            String current = jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?", String.class, row.get("outbox_id"));
            String next = advance(current, (String) row.get("callback_status"));
            jdbc.update("UPDATE wa.message_outbox SET state=?,version=version+1 WHERE id=? AND state<>?", next, row.get("outbox_id"), next);
            String attemptState = jdbc.queryForObject("SELECT state FROM wa.message_attempt WHERE id=?", String.class, row.get("attempt_id"));
            String attemptNext = evidence(attemptState, (String) row.get("callback_status"));
            jdbc.update("UPDATE wa.message_attempt SET state=?,completed_at=? WHERE id=? AND state<>?",
                attemptNext, Timestamp.from(clock.now()), row.get("attempt_id"), attemptNext);
            jdbc.update("UPDATE wa.integration_inbox SET state='COMPLETED' WHERE id=?", row.get("receipt_id"));
        }
        var abandoned = jdbc.query("""
            SELECT a.id,o.id FROM wa.message_attempt a JOIN wa.message_outbox o ON o.id=a.outbox_id
            JOIN wa.service_credential c ON c.id=a.credential_id
            WHERE a.instance_id=? AND c.environment=? AND a.state='SUBMITTING' AND a.submit_before<=?
              AND (?::uuid IS NULL OR o.id=?::uuid)
            ORDER BY a.started_at LIMIT 100 FOR UPDATE OF o,a SKIP LOCKED
            """, (rs, i) -> new UUID[]{rs.getObject(1, UUID.class), rs.getObject(2, UUID.class)}, instance, environment, Timestamp.from(clock.now()), box, box);
        for (UUID[] row : abandoned) {
            jdbc.update("UPDATE wa.message_attempt SET state='UNKNOWN',error_code='TIMEOUT',completed_at=? WHERE id=?",
                Timestamp.from(clock.now()), row[0]);
            jdbc.update("UPDATE wa.message_outbox SET state='UNKNOWN',version=version+1 WHERE id=? AND state='LEASED'", row[1]);
        }
        jdbc.update("""
            UPDATE wa.message_outbox o SET private_payload_ciphertext=NULL,start_payload_ciphertext=NULL
            WHERE (o.private_payload_ciphertext IS NOT NULL OR o.start_payload_ciphertext IS NOT NULL) AND o.state IN ('SUBMITTED','DELIVERED','READ','UNKNOWN','FAILED','EXPIRED','CANCELED')
              AND (?::uuid IS NULL OR o.id=?::uuid)
              AND EXISTS(SELECT 1 FROM wa.message_attempt a JOIN wa.service_credential c ON c.id=a.credential_id
                WHERE a.outbox_id=o.id AND a.instance_id=? AND c.environment=?)
            """, box, box, instance, environment);
    }
    private String advance(String current, String callback) {
        if (Set.of("CANCELED", "EXPIRED").contains(current)) return current;
        return evidence(current, callback);
    }
    private String evidence(String current, String callback) {
        String target = switch (callback) {
            case "SERVER_ACK" -> "SUBMITTED";
            case "DELIVERY_ACK" -> "DELIVERED";
            case "READ", "PLAYED" -> "READ";
            case "ERROR" -> "FAILED";
            default -> current;
        };
        if (target.equals("FAILED")) return rank(current) >= 3 ? current : target;
        return rank(target) > rank(current) ? target : current;
    }
    private int rank(String state) {
        return switch (state) { case "SUBMITTED" -> 1; case "FAILED" -> 2; case "DELIVERED" -> 3; case "READ" -> 4; default -> 0; };
    }
}
