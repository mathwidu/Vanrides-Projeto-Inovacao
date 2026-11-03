package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Bounded outbox work units, independent of OPEN/new-effect flags. No network or human command. */
public final class WaDeliveryMaintenance {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    private final WaDeliveryReconciler reconciliation;
    private final WaDeliveryConnection connection;
    public WaDeliveryMaintenance(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
        reconciliation = new WaDeliveryReconciler(jdbc, clock); connection = new WaDeliveryConnection(jdbc, settings, clock);
    }
    public void lock(String instance) { connection.lock(instance, true); }
    public List<Map<String, Object>> candidates(String instance, String after, int limit) {
        return jdbc.queryForList("SELECT o.id::text AS work_key " + WaDeliveryOrigins.SOURCE + """
            AND o.id::text>?
            AND EXISTS(SELECT 1 FROM wa.identity person LEFT JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id
              WHERE person.id=o.recipient_identity_id AND (cohort.environment=? OR (person.test_cohort_id IS NULL AND ?=false)))
            AND (
              EXISTS(SELECT 1 FROM wa.message_attempt a JOIN wa.service_credential c ON c.id=a.credential_id
                WHERE a.outbox_id=o.id AND a.instance_id=delivery_scope.instance_id AND c.environment=delivery_scope.environment
                  AND (a.state='SUBMITTING' AND a.submit_before<=? OR EXISTS(
                    SELECT 1 FROM wa.integration_inbox i WHERE i.instance_id=a.instance_id AND i.category='STATUS'
                      AND i.state='PENDING' AND i.payload_json->>'externalMessageId'=a.external_message_id)))
              OR (o.expires_at<=? AND (o.state='UNKNOWN' OR (o.state IN ('PENDING','LEASED') AND
            """ + WaDeliveryAttempts.UNSENT + """
              )))
            ) ORDER BY o.id::text LIMIT ?
            """, instance, settings.environment(), after, settings.environment(), settings.cohortOnly(),
            Timestamp.from(clock.now()), Timestamp.from(clock.now()), limit);
    }
    public void apply(UUID box, String instance) {
        reconciliation.refresh(instance, settings.environment(), box);
        expire(box, instance);
    }
    public void expireUnsent(String instance) {
        var boxes = jdbc.query("SELECT o.id " + WaDeliveryOrigins.SOURCE + """
            AND o.state IN ('PENDING','LEASED') AND o.expires_at<=? AND
            """ + WaDeliveryAttempts.UNSENT + """
            ORDER BY o.expires_at LIMIT 100 FOR UPDATE OF o SKIP LOCKED
            """, (rs, row) -> rs.getObject(1, UUID.class), instance, settings.environment(), Timestamp.from(clock.now()));
        for (UUID box : boxes) expire(box, instance);
    }
    public boolean expire(UUID box, String instance) {
        var rows = jdbc.queryForList("SELECT o.recipient_identity_id,o.state " + WaDeliveryOrigins.SOURCE + """
            AND o.id=? AND o.expires_at<=? FOR UPDATE OF o
            """, instance, settings.environment(), box, Timestamp.from(clock.now()));
        if (rows.isEmpty()) return true;
        var item = rows.get(0); String state = (String) item.get("state");
        // Reset owns identity before deleting outbox. Never wait on its FK while holding outbox.
        if (state.equals("UNKNOWN") && jdbc.queryForList(
                "SELECT id FROM wa.identity WHERE id=? FOR KEY SHARE SKIP LOCKED", item.get("recipient_identity_id")).isEmpty()) return false;
        if (state.equals("UNKNOWN")) jdbc.update("""
            INSERT INTO wa.support_case(id,identity_id,purpose,delivery_outbox_id,environment,instance_id,
              deduplication_key,reason_code,correlation_id,created_at)
            VALUES (?,?,'DELIVERY',?,?,?,?,'DELIVERY_UNKNOWN_EXPIRED',?,?)
            ON CONFLICT DO NOTHING
            """, UUID.randomUUID(), item.get("recipient_identity_id"), box, settings.environment(), instance,
            "delivery-unknown:" + box, box.toString(), Timestamp.from(clock.now()));
        if (state.equals("UNKNOWN") || List.of("PENDING", "LEASED").contains(state) && Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT " + WaDeliveryAttempts.UNSENT + " FROM wa.message_outbox o WHERE o.id=?", Boolean.class, box)))
            jdbc.update("""
                UPDATE wa.message_outbox SET state='EXPIRED',private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,
                  version=version+1 WHERE id=?
                """, box);
        return true;
    }
}
