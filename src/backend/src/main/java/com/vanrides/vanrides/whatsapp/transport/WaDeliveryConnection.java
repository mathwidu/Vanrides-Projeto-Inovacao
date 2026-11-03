package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Delivery admission only. An OPEN event is not proof of provider reachability at POST time. */
final class WaDeliveryConnection {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;

    WaDeliveryConnection(JdbcTemplate jdbc, WaSettings settings, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.clock = clock;
    }

    boolean lock(String instance, boolean wait) {
        String key = "wa-delivery:" + settings.environment() + ":" + instance;
        if (wait) {
            jdbc.queryForList("SELECT pg_advisory_xact_lock(hashtextextended(?,0))", key);
            return true;
        }
        return Boolean.TRUE.equals(jdbc.queryForObject(
            "SELECT pg_try_advisory_xact_lock(hashtextextended(?,0))", Boolean.class, key));
    }

    void record(WaTransportAccess.Grant channel, UUID receipt, ObjectNode payload) {
        lock(channel.instance(), true);
        Instant now = clock.now(), occurred = Instant.parse(payload.path("occurredAt").asText());
        // Bound clock skew without letting a future OPEN outrank a subsequent real CLOSE.
        if (occurred.isAfter(now.plusSeconds(30))) throw WaProblem.invalid();
        Instant ordering = occurred.isAfter(now) ? now : occurred;
        String state = "open".equals(payload.path("state").asText()) ? "OPEN" : "SUSPENDED";
        jdbc.update("""
            INSERT INTO wa.delivery_connection(environment,instance_id,state,ordering_at,recorded_at,
              channel_credential_id,source_receipt_id) VALUES (?,?,?,?,?,?,?)
            ON CONFLICT (environment,instance_id) DO UPDATE SET
              state=EXCLUDED.state,ordering_at=EXCLUDED.ordering_at,recorded_at=EXCLUDED.recorded_at,
              channel_credential_id=EXCLUDED.channel_credential_id,source_receipt_id=EXCLUDED.source_receipt_id
            WHERE EXCLUDED.ordering_at>wa.delivery_connection.ordering_at
               OR (EXCLUDED.ordering_at=wa.delivery_connection.ordering_at
                   AND wa.delivery_connection.state='OPEN' AND EXCLUDED.state='SUSPENDED')
            """, settings.environment(), channel.instance(), state, Timestamp.from(ordering), Timestamp.from(now),
            channel.credentialId(), receipt);
    }

    boolean open(String instance) {
        return deadline(instance).isAfter(clock.now());
    }

    Instant deadline(String instance) {
        var expires = jdbc.query("""
            SELECT c.expires_at FROM wa.delivery_connection s JOIN wa.service_credential c
              ON c.id=s.channel_credential_id AND c.principal=s.channel_principal
              AND c.environment=s.environment AND c.allowed_instance_id=s.instance_id
            WHERE s.environment=? AND s.instance_id=? AND s.state='OPEN' AND c.revoked_at IS NULL
            FOR SHARE OF s,c
            """, (rs, row) -> rs.getTimestamp(1).toInstant(), settings.environment(), instance);
        return expires.size() == 1 ? expires.get(0) : Instant.MIN;
    }
}
