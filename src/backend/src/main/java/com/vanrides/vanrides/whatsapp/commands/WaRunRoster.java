package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Locks configuration before the occurrence; personal answers serialize on that same occurrence. */
final class WaRunRoster {
    private final JdbcTemplate jdbc;
    private final WaClock clock;
    private final WaSettings settings;
    WaRunRoster(JdbcTemplate jdbc, WaClock clock, WaSettings settings) {
        this.jdbc = jdbc; this.clock = clock; this.settings = settings;
    }
    ObjectNode review(UUID driver, UUID occurrence, boolean starting) {
        return review(driver, occurrence, starting, false);
    }
    ObjectNode review(UUID driver, UUID occurrence, boolean starting, boolean shared) {
        String fence = shared ? " FOR SHARE" : " FOR UPDATE";
        var owners = jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? AND role='DRIVER' "
            + "AND status='ACTIVE' AND driver_enabled AND notice_version='wa-mvp-v1'" + fence, driver);
        if (owners.isEmpty() || !settings.enabled() || !settings.newStartsEnabled()) throw WaProblem.denied();
        var ids = jdbc.query("SELECT routine_id FROM wa.routine_occurrence WHERE id=? AND driver_id=?",
            (rs, row) -> rs.getObject(1, UUID.class), occurrence, driver);
        if (ids.size() != 1) throw WaProblem.missing();
        ObjectNode routine = one("SELECT to_jsonb(r)::text FROM wa.routine r WHERE id=? AND driver_id=?" + fence, ids.get(0), driver);
        jdbc.queryForList("SELECT id FROM wa.routine_member WHERE routine_id=? AND driver_id=? ORDER BY id" + fence, ids.get(0), driver);
        ObjectNode day = one("SELECT to_jsonb(o)::text FROM wa.routine_occurrence o WHERE id=? AND driver_id=?" + fence, occurrence, driver);
        LocalDate date = LocalDate.parse(day.path("local_date").asText());
        String timezone = day.path("schedule_snapshot").path("timezone").asText(routine.path("timezone").asText());
        LocalDate today = clock.now().atZone(ZoneId.of(timezone)).toLocalDate();
        if (!routine.path("state").asText().equals("ACTIVE") || routine.path("stop_order_pending").asBoolean()
                || !day.path("state").asText().equals("PLANNED") || !day.path("calendar_eligible").asBoolean()
                || date.isBefore(today) || !date.isBefore(today.plusDays(7)) || starting && !date.equals(today)
                || !containsDay(routine.path("weekdays"), date.getDayOfWeek().getValue())) throw WaProblem.denied();
        if (!WaDriverTargets.scheduledAt(routine, date.toString()).equals(Instant.parse(day.path("scheduled_at").asText())))
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        var runs = jdbc.queryForList("SELECT id,state FROM wa.operation_run WHERE occurrence_id=?" + fence, occurrence);
        if (!runs.isEmpty() && !runs.get(0).get("state").equals("PLANNED")) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
        if (Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.operation_run WHERE driver_id=? "
                + "AND occurrence_id<>? AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED'))", Boolean.class, driver, occurrence)))
            throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
        jdbc.queryForList("SELECT id FROM wa.attendance WHERE routine_id=? AND local_date=?::date ORDER BY id" + fence, ids.get(0), date.toString());
        ArrayNode members = WaJson.MAPPER.createArrayNode();
        Timestamp now = Timestamp.from(clock.now());
        jdbc.query("""
            SELECT jsonb_build_object('id',m.id,'passenger',m.passenger_id,'memberVersion',m.version,'linkVersion',l.version,'order',m.stop_order,
              'point',p.id,'pointVersion',p.version,'latitude',p.latitude,'longitude',p.longitude,'reference',p.reference_text,
              'commonPoint',p.common_pickup_point_id,'name',profile.display_name,'attendance',COALESCE(a.state,'PENDING'),
              'attendanceVersion',COALESCE(a.version,0),'overrideId',ov.id,'overrideIncluded',ov.included,'overrideReason',ov.reason,
              'eligible',l.state='APPROVED' AND p.state='APPROVED' AND profile.status='ACTIVE' AND profile.notice_version='wa-mvp-v1'
                AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
                AND p.valid_from<=? AND (p.valid_until IS NULL OR p.valid_until>?)
                AND ((pi.test_cohort_id IS NOT NULL AND pi.test_cohort_id=di.test_cohort_id AND cohort.environment=?)
                  OR (pi.test_cohort_id IS NULL AND di.test_cohort_id IS NULL AND ?=false)))::text
            FROM wa.routine_member m JOIN wa.driver_passenger_link l ON l.id=m.link_id AND l.driver_id=m.driver_id AND l.passenger_id=m.passenger_id
            JOIN wa.pickup_point p ON p.id=m.pickup_point_id AND p.driver_id=m.driver_id AND p.passenger_id=m.passenger_id AND p.link_id=l.id
            JOIN wa.profile profile ON profile.identity_id=m.passenger_id AND profile.role='PASSENGER'
            JOIN wa.identity pi ON pi.id=m.passenger_id JOIN wa.identity di ON di.id=m.driver_id
            LEFT JOIN wa.test_cohort cohort ON cohort.id=pi.test_cohort_id
            LEFT JOIN wa.attendance a ON a.routine_id=m.routine_id AND a.passenger_id=m.passenger_id AND a.local_date=?::date
            LEFT JOIN LATERAL (SELECT v.id,v.included,v.reason FROM wa.occurrence_roster_override v
              WHERE v.occurrence_id=? AND v.passenger_id=m.passenger_id ORDER BY v.revision DESC LIMIT 1) ov ON true
            WHERE m.routine_id=? AND m.driver_id=? AND m.state='ACTIVE' AND ?=ANY(m.weekdays)
            ORDER BY m.stop_order,m.id FOR SHARE OF l,p
            """, (rs, row) -> WaJson.object(rs.getString(1)), now, now, now, now, settings.environment(), settings.cohortOnly(),
            date.toString(), occurrence, ids.get(0), driver, date.getDayOfWeek().getValue()).forEach(member -> {
                member.put("included", included(member, routine.path("attendance_policy").asText()));
                if (member.path("included").asBoolean() && !member.path("eligible").asBoolean())
                    throw new WaProblem(WaProblem.Code.POINT_NOT_APPROVED);
                members.add(member);
            });
        if (members.isEmpty()) throw new WaProblem(WaProblem.Code.POINT_NOT_APPROVED);
        ObjectNode result = WaJson.MAPPER.createObjectNode(); result.set("routine", routine); result.set("occurrence", day); result.set("members", members);
        return result;
    }
    static boolean included(JsonNode member, String policy) {
        if (member.path("attendance").asText().equals("NOT_GOING")) return false;
        if (member.path("overrideIncluded").isBoolean()) return member.path("overrideIncluded").asBoolean();
        return policy.equals("INCLUDE_BY_DEFAULT") || member.path("attendance").asText().equals("GOING");
    }
    private static boolean containsDay(JsonNode days, int day) {
        for (JsonNode value : days) if (value.asInt() == day) return true;
        return false;
    }
    private ObjectNode one(String sql, Object... args) {
        var values = jdbc.query(sql, (rs, row) -> WaJson.object(rs.getString(1)), args);
        if (values.size() != 1) throw WaProblem.missing();
        return values.get(0);
    }
}
