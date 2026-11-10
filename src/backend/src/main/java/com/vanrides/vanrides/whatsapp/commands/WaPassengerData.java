package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;

import org.springframework.jdbc.core.JdbcTemplate;

import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** Reads bounded by owners; never use personal pickup points as a public catalogue. */
final class WaPassengerData {
    final JdbcTemplate jdbc;
    final WaClock clock;
    private final WaSettings settings;

    WaPassengerData(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc;
        this.clock = clock;
        this.settings = settings;
    }

    void requireScope(UUID passenger, WaInboxAccess.Context event, UUID driver) {
        var identities =
                jdbc.queryForList(
                        """
                        SELECT i.id,i.test_cohort_id,c.environment FROM wa.identity i
                        LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id WHERE i.id IN (?,?) ORDER BY i.id FOR UPDATE OF i
                        """,
                        passenger,
                        driver);
        if (identities.size() != 2) {
            throw WaProblem.denied();
        }
        Object first = identities.get(0).get("test_cohort_id");
        Object second = identities.get(1).get("test_cohort_id");
        boolean sameCohort =
                first == null && second == null && !settings.cohortOnly()
                        || first != null
                                && first.equals(second)
                                && identities.stream()
                                        .allMatch(
                                                row ->
                                                        settings.environment()
                                                                .equals(row.get("environment")));
        boolean sameInstance =
                event == null
                        ? teamTestDriver(driver)
                        : Boolean.TRUE.equals(
                                jdbc.queryForObject(
                                        """
                                        SELECT EXISTS(SELECT 1 FROM wa.team_test_driver t JOIN wa.integration_inbox current ON current.id=?
                                          WHERE t.driver_id=? AND current.provider='EVOLUTION' AND current.instance_id=t.instance_id)
                                        OR EXISTS(SELECT 1 FROM wa.integration_inbox own JOIN wa.integration_inbox current ON current.id=?
                                        JOIN wa.identity_alias alias ON alias.id::text=own.payload_json->>'senderAliasId'
                                          AND alias.identity_id=own.identity_id AND alias.provider=own.provider AND alias.instance_id=own.instance_id
                                        WHERE own.identity_id=? AND own.provider=current.provider AND own.instance_id=current.instance_id
                                          AND own.category='MESSAGE' AND own.state IN ('CLAIMED','COMPLETED') AND alias.alias_type IN ('PN','LID'))
                                        """,
                                        Boolean.class,
                                        event.eventId(),
                                        driver,
                                        event.eventId(),
                                        driver));
        if (!sameCohort || !sameInstance) {
            throw WaProblem.denied();
        }
    }

    boolean teamTestDriver(UUID driver) {
        return Boolean.TRUE.equals(
                jdbc.queryForObject(
                        "SELECT EXISTS(SELECT 1 FROM wa.team_test_driver WHERE driver_id=?)",
                        Boolean.class,
                        driver));
    }

    ObjectNode invite(UUID actor, WaInboxAccess.Context event, String hash, boolean lock) {
        var rows =
                jdbc.query(
                        """
                        SELECT to_jsonb(i)||jsonb_build_object('driverName',p.display_name) FROM wa.driver_invite i
                        JOIN wa.profile p ON p.identity_id=i.driver_id WHERE i.code_hash=? AND i.revoked_at IS NULL AND i.expires_at>?
                        AND i.use_count<i.max_uses AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled
                        """
                                + (lock ? " FOR UPDATE OF i" : ""),
                        (rs, i) -> WaJson.object(rs.getString(1)),
                        hash,
                        Timestamp.from(clock.now()));
        if (rows.size() != 1) {
            throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        }
        requireScope(actor, event, UUID.fromString(rows.get(0).path("driver_id").asText()));
        return rows.get(0);
    }

    ArrayNode routines(UUID driver) {
        return array(
                "SELECT to_jsonb(r)::text FROM wa.routine r WHERE driver_id=? AND state='ACTIVE'"
                        + " ORDER BY created_at,id LIMIT 9",
                driver);
    }

    ArrayNode commons(UUID driver) {
        return array(
                "SELECT to_jsonb(p)::text FROM wa.common_pickup_point p WHERE driver_id=? AND"
                        + " state='PUBLISHED' ORDER BY created_at,id LIMIT 9",
                driver);
    }

    ArrayNode links(UUID actor, boolean driver) {
        return array(
                """
                SELECT (to_jsonb(l)||jsonb_build_object('passengerName',p.display_name,'driverName',d.display_name,
                'pickupId',(SELECT id FROM wa.pickup_point x WHERE x.link_id=l.id AND x.state='PROPOSED' ORDER BY x.created_at DESC,x.id LIMIT 1)))::text
                FROM wa.driver_passenger_link l JOIN wa.profile p ON p.identity_id=l.passenger_id
                JOIN wa.profile d ON d.identity_id=l.driver_id WHERE
                """
                        + (driver ? " l.driver_id=?" : " l.passenger_id=?")
                        + " AND l.state IN ('REQUESTED','APPROVED') ORDER BY l.created_at,l.id"
                        + " LIMIT 101",
                actor);
    }

    ObjectNode pin(UUID actor, String source, UUID current) {
        var rows =
                jdbc.query(
                        """
                        SELECT p.payload_json::text FROM wa.integration_inbox p JOIN wa.integration_inbox e ON e.id=?
                        WHERE p.id=? AND p.identity_id=? AND p.instance_id=e.instance_id AND p.provider=e.provider
                          AND p.category='MESSAGE' AND p.normalized_kind='PIN' AND p.state IN ('CLAIMED','COMPLETED') FOR SHARE OF p
                        """,
                        (rs, i) -> WaJson.object(rs.getString(1)),
                        current,
                        UUID.fromString(source),
                        actor);
        if (rows.size() != 1) {
            throw WaProblem.missing();
        }
        var p = rows.get(0).path("pin");
        if (!p.path("latitude").isNumber()
                || !p.path("longitude").isNumber()
                || !Double.isFinite(p.path("latitude").asDouble())
                || !Double.isFinite(p.path("longitude").asDouble())
                || Math.abs(p.path("latitude").asDouble()) > 90
                || Math.abs(p.path("longitude").asDouble()) > 180) {
            throw WaProblem.invalid();
        }
        return (ObjectNode) p.deepCopy();
    }

    ObjectNode point(UUID link, UUID pickup, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE link_id=? AND id=?"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, i) -> WaJson.object(rs.getString(1)),
                        link,
                        pickup);
        if (rows.size() != 1) {
            throw WaProblem.missing();
        }
        return rows.get(0);
    }

    ObjectNode link(UUID actor, UUID id, boolean lock) {
        var rows =
                jdbc.query(
                        "SELECT to_jsonb(l)::text FROM wa.driver_passenger_link l WHERE id=? AND"
                                + " (driver_id=? OR passenger_id=?)"
                                + (lock ? " FOR UPDATE" : ""),
                        (rs, i) -> WaJson.object(rs.getString(1)),
                        id,
                        actor,
                        actor);
        if (rows.size() != 1) {
            throw WaProblem.missing();
        }
        return rows.get(0);
    }

    /**
     * Consistent lock order shared with 080: routines, members, links, points. No peer profile is
     * locked.
     */
    String snapshot(UUID actor, ObjectNode draft, WaInboxAccess.Context event) {
        ObjectNode result = WaPassengerDraftStore.json();
        UUID driver = UUID.fromString(draft.path("driverId").asText());
        UUID passenger = actor;
        if (draft.has("linkId")) {
            ObjectNode owner = link(actor, UUID.fromString(draft.path("linkId").asText()), false);
            passenger = UUID.fromString(owner.path("passenger_id").asText());
        }
        if (!draft.path("action").asText().equals("PUBLISH_COMMON")) {
            requireScope(passenger, event, driver);
        }
        ArrayNode routineRows = WaJson.MAPPER.createArrayNode();
        if (draft.has("linkId")) {
            jdbc.queryForList(
                    "SELECT r.id FROM wa.routine r JOIN wa.passenger_link_routine s ON"
                            + " s.routine_id=r.id WHERE s.link_id=? ORDER BY r.id FOR UPDATE OF r",
                    UUID.fromString(draft.path("linkId").asText()));
            result.set(
                    "selections",
                    array(
                            "SELECT to_jsonb(s)::text FROM wa.passenger_link_routine s WHERE"
                                    + " s.link_id=? ORDER BY s.routine_id",
                            UUID.fromString(draft.path("linkId").asText())));
            result.set(
                    "routines",
                    array(
                            "SELECT to_jsonb(r)::text FROM wa.routine r JOIN"
                                    + " wa.passenger_link_routine s ON s.routine_id=r.id WHERE"
                                    + " s.link_id=? ORDER BY r.id",
                            UUID.fromString(draft.path("linkId").asText())));
            result.set(
                    "members",
                    array(
                            "SELECT to_jsonb(m)::text FROM wa.routine_member m WHERE link_id=?"
                                    + " ORDER BY routine_id,id FOR UPDATE",
                            UUID.fromString(draft.path("linkId").asText())));
            result.set("link", link(actor, UUID.fromString(draft.path("linkId").asText()), true));
            result.set(
                    "points",
                    array(
                            "SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE link_id=? ORDER"
                                    + " BY id FOR UPDATE",
                            UUID.fromString(draft.path("linkId").asText())));
        } else {
            if (draft.has("selections")) {
                var ids = new java.util.TreeSet<String>();
                draft.path("selections").forEach(s -> ids.add(s.path("id").asText()));
                for (String id : ids) {
                    var rows =
                            array(
                                    "SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=? AND"
                                            + " driver_id=? AND state='ACTIVE' FOR UPDATE",
                                    UUID.fromString(id),
                                    driver);
                    if (rows.size() != 1) {
                        throw WaProblem.missing();
                    }
                    routineRows.add(rows.get(0));
                }
                result.set("routines", routineRows);
            }
        }
        if (draft.has("sourceEvent")) {
            result.set(
                    "sourcePin", pin(actor, draft.path("sourceEvent").asText(), event.eventId()));
        }
        if (draft.has("commonId")) {
            var rows =
                    array(
                            "SELECT to_jsonb(c)::text FROM wa.common_pickup_point c WHERE id=? AND"
                                    + " driver_id=? AND state='PUBLISHED' FOR SHARE",
                            UUID.fromString(draft.path("commonId").asText()),
                            driver);
            if (rows.size() != 1
                    || rows.get(0).path("version").asLong()
                            != draft.path("commonVersion").asLong()) {
                throw WaProblem.missing();
            }
            result.set("common", rows.get(0));
        }
        // Count is deliberately excluded: only the durable request consumes the invite under its
        // row lock.
        return WaCrypto.digest(WaJson.canonical(result));
    }

    String selectionSummary(UUID link) {
        List<String> lines = new ArrayList<>();
        for (JsonNode selection :
                array(
                        """
                        SELECT jsonb_build_object('name',r.name,'direction',r.direction,'time',r.local_time::text,'days',s.weekdays)::text
                        FROM wa.passenger_link_routine s JOIN wa.routine r ON r.id=s.routine_id WHERE s.link_id=? ORDER BY r.created_at,r.id
                        """,
                        link)) {
            lines.add(
                    WaPassengerTexts.routineLine(
                            selection.path("name").asText(),
                            selection.path("direction").asText(),
                            selection.path("time").asText(),
                            selection.path("days")));
        }
        return String.join("\n", lines);
    }

    ArrayNode array(String sql, Object... args) {
        ArrayNode values = WaJson.MAPPER.createArrayNode();
        jdbc.query(sql, (rs, i) -> WaJson.object(rs.getString(1)), args).forEach(values::add);
        return values;
    }
}
