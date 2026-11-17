package com.vanrides.vanrides.whatsapp.commands;

import com.fasterxml.jackson.databind.JsonNode;
import java.sql.Timestamp;
import java.util.UUID;

/** Changes exactly one routine. Approved points used by any membership or run snapshot survive. */
final class WaPassengerMemberships {
    private final WaPassengerData data;
    WaPassengerMemberships(WaPassengerData data) { this.data = data; }

    void activate(UUID link, UUID driver, UUID passenger, UUID routine, UUID point, JsonNode days, Timestamp now) {
        int changed = data.jdbc.update("""
                INSERT INTO wa.routine_member(id,routine_id,driver_id,passenger_id,link_id,pickup_point_id,stop_order,weekdays,version,created_at)
                VALUES (?,?,?,?,?,?,COALESCE((SELECT max(stop_order) FROM wa.routine_member WHERE routine_id=? AND state='ACTIVE'),0)+1,?::smallint[],1,?)
                ON CONFLICT(routine_id,passenger_id) DO UPDATE SET link_id=EXCLUDED.link_id,pickup_point_id=EXCLUDED.pickup_point_id,
                  weekdays=EXCLUDED.weekdays,state='ACTIVE',version=wa.routine_member.version+1,
                  stop_order=CASE WHEN wa.routine_member.state='ACTIVE' THEN wa.routine_member.stop_order ELSE EXCLUDED.stop_order END
                WHERE wa.routine_member.state<>'ACTIVE' OR wa.routine_member.link_id<>EXCLUDED.link_id
                  OR wa.routine_member.pickup_point_id<>EXCLUDED.pickup_point_id OR wa.routine_member.weekdays<>EXCLUDED.weekdays
                """, UUID.randomUUID(), routine, driver, passenger, link, point, routine, sqlDays(days), now);
        if (changed > 0) {
            data.jdbc.update("UPDATE wa.routine SET version=version+1,stop_order_pending=true,updated_at=? WHERE id=? AND driver_id=?", now, routine, driver);
        }
    }

    void retireUnused(UUID point, Timestamp now) {
        if (point == null) return;
        data.jdbc.update("""
                UPDATE wa.pickup_point p SET state=CASE WHEN state='PROPOSED' THEN 'REJECTED' ELSE 'RETIRED' END,
                  valid_until=?,version=version+1 WHERE p.id=? AND p.state IN ('PROPOSED','APPROVED')
                AND NOT EXISTS(SELECT 1 FROM wa.passenger_link_routine s WHERE s.pickup_point_id=p.id)
                AND NOT EXISTS(SELECT 1 FROM wa.routine_member m WHERE m.pickup_point_id=p.id)
                AND NOT EXISTS(SELECT 1 FROM wa.run_stop stop WHERE stop.pickup_point_id=p.id)
                """, now, point);
    }

    static String sqlDays(JsonNode days) {
        java.util.List<String> values = new java.util.ArrayList<>();
        days.forEach(day -> values.add(day.asText()));
        return "{" + String.join(",", values) + "}";
    }
}
