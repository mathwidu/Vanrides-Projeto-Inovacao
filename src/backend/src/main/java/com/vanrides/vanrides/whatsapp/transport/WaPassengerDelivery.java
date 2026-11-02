package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.commands.WaOnboardingCommands;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Relevance belongs to authorization, never to the durable result/callback lookup. */
final class WaPassengerDelivery {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaTransportAccess access;
    private final WaClock clock;
    WaPassengerDelivery(JdbcTemplate jdbc, WaSettings settings, WaTransportAccess access, WaClock clock) {
        this.jdbc = jdbc; this.settings = settings; this.access = access; this.clock = clock;
    }

    boolean current(UUID outbox, String instance, boolean lock) {
        var rows = jdbc.queryForList("SELECT * FROM wa.passenger_response_route WHERE outbox_id=? AND instance_id=? AND environment=?",
            outbox, instance, settings.environment());
        if (rows.size() != 1) return false;
        var route = rows.get(0);
        UUID recipient = (UUID) route.get("recipient_identity_id");
        if (route.get("event_kind").equals("ACTOR")) {
            if (lock) jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE", recipient);
            return access.allowedIdentity(recipient) && !paused(recipient, lock) && alias(route, instance, lock).size() == 1;
        }
        UUID driver = (UUID) route.get("driver_id"), passenger = (UUID) route.get("passenger_id");
        var profiles = jdbc.queryForList("""
            SELECT identity_id,role,status,driver_enabled,notice_version FROM wa.profile
            WHERE identity_id IN (?,?) ORDER BY identity_id
            """ + (lock ? " FOR SHARE" : ""), driver, passenger);
        if (profiles.size() != 2 || !sameScope(driver, passenger)) return false;
        for (var profile : profiles) {
            boolean isDriver = profile.get("identity_id").equals(driver);
            if (!"ACTIVE".equals(profile.get("status")) || !(isDriver ? "DRIVER" : "PASSENGER").equals(profile.get("role"))
                    || isDriver && !Boolean.TRUE.equals(profile.get("driver_enabled"))
                    || !WaOnboardingCommands.NOTICE_VERSION.equals(profile.get("notice_version"))) return false;
        }
        if (paused(recipient, lock)) return false;
        if (lock) jdbc.queryForList("""
            SELECT r.id FROM wa.routine r JOIN wa.passenger_link_routine s ON s.routine_id=r.id AND s.driver_id=r.driver_id
            WHERE s.link_id=? ORDER BY r.id FOR SHARE OF r,s
            """, route.get("link_id"));
        var links = jdbc.queryForList("SELECT driver_id,passenger_id,state,version,valid_from,valid_until FROM wa.driver_passenger_link WHERE id=?"
            + (lock ? " FOR SHARE" : ""), route.get("link_id"));
        if (links.size() != 1) return false;
        var link = links.get(0);
        if (!driver.equals(link.get("driver_id")) || !passenger.equals(link.get("passenger_id"))
                || !route.get("link_state").equals(link.get("state")) || number(route, "link_version") != number(link, "version")
                || !route.get("selection_hash").equals(WaPassengerResponses.selectionHash(jdbc, (UUID) route.get("link_id")))) return false;
        if (link.get("state").equals("APPROVED") && !validNow(link)) return false;
        String pointState = null;
        boolean currentPoint = Set.of("REQUESTED", "POINT_CHANGE", "APPROVED", "POINT_APPROVED").contains(route.get("event_kind"));
        if (route.get("pickup_id") != null) {
            var points = jdbc.queryForList("""
                SELECT state,version,valid_from,valid_until FROM wa.pickup_point WHERE id=? AND link_id=? AND driver_id=? AND passenger_id=?
                """ + (lock ? " FOR SHARE" : ""), route.get("pickup_id"), route.get("link_id"), driver, passenger);
            if (points.size() != 1 || number(route, "pickup_version") != number(points.get(0), "version")
                    || !route.get("pickup_state").equals(points.get(0).get("state")) || currentPoint && !validNow(points.get(0))) return false;
            pointState = (String) points.get(0).get("state");
        }
        return WaPassengerResponses.validState((String) route.get("event_kind"), (String) link.get("state"), pointState)
            && alias(route, instance, lock).size() == 1;
    }

    List<Map<String, Object>> aliases(UUID outbox, String instance) {
        var routes = jdbc.queryForList("SELECT * FROM wa.passenger_response_route WHERE outbox_id=? AND environment=? AND instance_id=?",
            outbox, settings.environment(), instance);
        return routes.size() == 1 ? alias(routes.get(0), instance, true) : List.of();
    }
    private List<Map<String, Object>> alias(Map<String, Object> route, String instance, boolean lock) {
        return jdbc.queryForList("""
            SELECT a.alias_type,a.alias_value FROM wa.integration_inbox e JOIN wa.identity_alias a
              ON a.id::text=e.payload_json->>'senderAliasId' AND a.identity_id=e.identity_id
              AND a.provider=e.provider AND a.instance_id=e.instance_id
            WHERE e.id=? AND e.identity_id=? AND e.provider='EVOLUTION' AND e.category='MESSAGE' AND e.instance_id=?
              AND a.alias_type IN ('PN','LID')
            """ + (lock ? " FOR SHARE OF a" : ""), route.get("recipient_source_event_id"), route.get("recipient_identity_id"), instance);
    }
    private boolean sameScope(UUID driver, UUID passenger) {
        var rows = jdbc.queryForList("""
            SELECT i.test_cohort_id,c.environment FROM wa.identity i LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE i.id IN (?,?)
            """, driver, passenger);
        if (rows.size() != 2) return false;
        Object first = rows.get(0).get("test_cohort_id"), second = rows.get(1).get("test_cohort_id");
        if (first == null && second == null) return !settings.cohortOnly();
        return first != null && first.equals(second) && rows.stream().allMatch(row -> settings.environment().equals(row.get("environment")));
    }
    private boolean paused(UUID identity, boolean lock) {
        var stages = jdbc.query("SELECT stage FROM wa.conversation_state WHERE identity_id=?" + (lock ? " FOR SHARE" : ""),
            (rs, i) -> rs.getString(1), identity);
        return !stages.isEmpty() && stages.get(0).equals("PAUSED");
    }

    Instant deadline(UUID outbox) {
        var rows = jdbc.queryForList("""
            SELECT r.event_kind,r.link_state,l.valid_until AS link_until,p.valid_until AS pickup_until
            FROM wa.passenger_response_route r LEFT JOIN wa.driver_passenger_link l ON l.id=r.link_id
            LEFT JOIN wa.pickup_point p ON p.id=r.pickup_id WHERE r.outbox_id=?
            """, outbox);
        if (rows.size() != 1) return Instant.MIN;
        var row = rows.get(0); Instant deadline = Instant.MAX;
        if ("APPROVED".equals(row.get("link_state")) && row.get("link_until") instanceof Timestamp until) deadline = until.toInstant();
        if (Set.of("REQUESTED", "POINT_CHANGE", "APPROVED", "POINT_APPROVED").contains(row.get("event_kind"))
                && row.get("pickup_until") instanceof Timestamp until && until.toInstant().isBefore(deadline)) deadline = until.toInstant();
        return deadline;
    }

    /** Caller holds outbox/profile/subject locks and has revalidated its credential and lease. */
    ObjectNode render(UUID outbox, ObjectNode content) {
        var rows = jdbc.queryForList("""
            SELECT p.latitude,p.longitude FROM wa.passenger_response_route r JOIN wa.pickup_point p
              ON p.id=r.pickup_id AND p.link_id=r.link_id AND p.driver_id=r.driver_id AND p.passenger_id=r.passenger_id
            WHERE r.outbox_id=? AND r.event_kind IN ('REQUESTED','POINT_CHANGE') AND r.recipient_identity_id=r.driver_id
            """, outbox);
        if (rows.isEmpty()) return content;
        if (rows.size() != 1) return null;
        double latitude = ((Number) rows.get(0).get("latitude")).doubleValue();
        double longitude = ((Number) rows.get(0).get("longitude")).doubleValue();
        if (!Double.isFinite(latitude) || !Double.isFinite(longitude) || Math.abs(latitude) > 90 || Math.abs(longitude) > 180) return null;
        String text = content.path("text").asText() + "\nPonto de embarque: https://www.google.com/maps/search/?api=1&query="
            + Double.toString(latitude) + "%2C" + Double.toString(longitude);
        if (text.codePointCount(0, text.length()) > 2000) return null;
        return content.deepCopy().put("text", text);
    }
    private static long number(Map<String, Object> row, String key) { return ((Number) row.get(key)).longValue(); }
    private boolean validNow(Map<String, Object> row) {
        return !((Timestamp) row.get("valid_from")).toInstant().isAfter(clock.now())
            && (row.get("valid_until") == null || ((Timestamp) row.get("valid_until")).toInstant().isAfter(clock.now()));
    }
}
