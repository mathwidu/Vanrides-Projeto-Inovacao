package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

public final class WaTeamPassengerNotices {
    public static final String POLICY = "team-test-driver-v1";
    static final String ELIGIBLE = """
        (o.purpose='OPERATIONAL' AND o.policy_version='team-test-driver-v1' AND o.command_id IS NULL AND EXISTS (
          SELECT 1 FROM wa.team_passenger_notice n WHERE n.outbox_id=o.id AND n.passenger_id=o.recipient_identity_id
            AND n.instance_id=delivery_scope.instance_id AND n.environment=delivery_scope.environment))
        """;

    private final JdbcTemplate jdbc;
    private final WaSettings settings;

    WaTeamPassengerNotices(JdbcTemplate jdbc, WaSettings settings) {
        this.jdbc = jdbc;
        this.settings = settings;
    }

    public static void save(JdbcTemplate jdbc, UUID operation, UUID link, UUID pickup, String kind, String text,
                            String environment, String instance, Instant now) {
        if (text == null || text.isBlank() || text.codePointCount(0, text.length()) > 2000) throw WaProblem.invalid();
        var links = jdbc.queryForList("""
            SELECT driver_id,passenger_id,state,version,requested_source_event_id FROM wa.driver_passenger_link WHERE id=?
            """, link);
        if (links.size() != 1) throw WaProblem.missing();
        var row = links.get(0);
        UUID passenger = (UUID) row.get("passenger_id"), source = (UUID) row.get("requested_source_event_id");
        if (WaPassengerDelivery.senderAlias(jdbc, source, passenger, instance, false).size() != 1) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        Long pickupVersion = pickup == null ? null
            : jdbc.queryForObject("SELECT version FROM wa.pickup_point WHERE id=? AND link_id=?", Long.class, pickup, link);
        UUID outbox = UUID.randomUUID();
        jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,policy_version,
              payload_kind,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'OPERATIONAL',?,?,'TEXT',?::jsonb,?,?,?)
            """, outbox, passenger, "team-test-driver:" + operation, POLICY,
            WaJson.write(Map.of("type", "TEXT", "text", text, "referenceTime", now.toString())),
            Timestamp.from(now), Timestamp.from(now.plusSeconds(300)), Timestamp.from(now));
        jdbc.update("""
            INSERT INTO wa.team_passenger_notice(outbox_id,operation_id,recipient_source_event_id,environment,instance_id,
              event_kind,link_id,driver_id,passenger_id,link_version,link_state,pickup_id,pickup_version,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?,?)
            """, outbox, operation, source, environment, instance, kind, link, row.get("driver_id"), passenger,
            row.get("version"), row.get("state"), pickup, pickupVersion, Timestamp.from(now));
    }

    boolean current(UUID outbox, String instance, boolean lock) {
        var notices = notice(outbox, instance);
        if (notices.size() != 1) return false;
        var notice = notices.get(0);
        var links = jdbc.queryForList("SELECT state,version FROM wa.driver_passenger_link WHERE id=?" + (lock ? " FOR SHARE" : ""),
            notice.get("link_id"));
        if (links.size() != 1 || !notice.get("link_state").equals(links.get(0).get("state"))
                || ((Number) notice.get("link_version")).longValue() != ((Number) links.get(0).get("version")).longValue())
            return false;
        if (notice.get("pickup_id") != null) {
            Long version = jdbc.query("SELECT version FROM wa.pickup_point WHERE id=?" + (lock ? " FOR SHARE" : ""),
                (rs, i) -> rs.getLong(1), notice.get("pickup_id")).stream().findFirst().orElse(null);
            if (version == null || version != ((Number) notice.get("pickup_version")).longValue()) return false;
        }
        if (WaPassengerDelivery.paused(jdbc, (UUID) notice.get("passenger_id"), lock)) return false;
        return aliases(outbox, instance).size() == 1;
    }

    List<Map<String, Object>> aliases(UUID outbox, String instance) {
        var notices = notice(outbox, instance);
        if (notices.size() != 1) return List.of();
        return WaPassengerDelivery.senderAlias(jdbc, (UUID) notices.get(0).get("recipient_source_event_id"),
            (UUID) notices.get(0).get("passenger_id"), instance, true);
    }

    private List<Map<String, Object>> notice(UUID outbox, String instance) {
        return jdbc.queryForList("SELECT * FROM wa.team_passenger_notice WHERE outbox_id=? AND instance_id=? AND environment=?",
            outbox, instance, settings.environment());
    }
}
