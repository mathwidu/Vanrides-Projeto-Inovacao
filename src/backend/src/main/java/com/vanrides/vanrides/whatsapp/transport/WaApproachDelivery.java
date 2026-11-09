package com.vanrides.vanrides.whatsapp.transport;

import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.tracking.WaApproachPolicy;
import com.vanrides.vanrides.whatsapp.tracking.WaVehicleLocation;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Eligibility and current trajectory are rechecked for every submission, including a delayed queue. */
final class WaApproachDelivery {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock; private final WaVehicleLocation location;
    WaApproachDelivery(JdbcTemplate jdbc,WaSettings settings,WaClock clock) { this.jdbc=jdbc; this.settings=settings; this.clock=clock; location=new WaVehicleLocation(jdbc,clock,settings); }
    boolean current(UUID outbox,String instance,boolean lock) {
        if(!settings.approachEnabled()) return false;
        var rows=jdbc.queryForList("""
            SELECT o.recipient_identity_id,o.run_id,s.latitude_snapshot,s.longitude_snapshot,r.routine_id,route.session_id
            FROM wa.message_outbox o JOIN wa.approach_delivery_route route ON route.outbox_id=o.id
            JOIN wa.run_stop s ON s.id=o.stop_id AND s.run_id=o.run_id AND s.passenger_id=o.recipient_identity_id
            JOIN wa.operation_run r ON r.id=s.run_id JOIN wa.routine_occurrence day ON day.id=r.occurrence_id
            JOIN wa.profile p ON p.identity_id=s.passenger_id
            WHERE o.id=? AND route.instance_id=? AND route.environment=? AND o.purpose='APPROACH'
              AND s.state='APPROACHING' AND s.attendance_snapshot<>'NOT_GOING' AND day.state='PLANNED'
              AND p.preferences_json->'APPROACH_MESSAGES'='true'::jsonb
              AND NOT EXISTS(SELECT 1 FROM wa.attendance a WHERE a.routine_id=r.routine_id AND a.local_date=r.local_date
                AND a.passenger_id=s.passenger_id AND a.state='NOT_GOING')
              AND NOT EXISTS(SELECT 1 FROM wa.run_stop before_stop WHERE before_stop.run_id=s.run_id
                AND before_stop.physical_stop_key<>s.physical_stop_key AND before_stop.stop_order<
                  (SELECT min(group_stop.stop_order) FROM wa.run_stop group_stop WHERE group_stop.run_id=s.run_id AND group_stop.physical_stop_key=s.physical_stop_key)
                AND before_stop.state IN ('WAITING','APPROACHING'))
            """,outbox,instance,settings.environment());
        if(rows.size()!=1) return false; var row=rows.get(0);
        UUID passenger=(UUID)row.get("recipient_identity_id"),run=(UUID)row.get("run_id");
        if(!settings.approachReviewed((UUID)row.get("routine_id")) || !location.authorized(passenger,run,lock)) return false;
        if(lock) jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR SHARE",passenger);
        if(location.pin(passenger,run,lock)==null) return false;
        if(lock) return current(outbox,instance,false);
        var fixes=jdbc.query("""
            SELECT fix.* FROM wa.approach_fix fix JOIN wa.tracking_session session ON session.id=fix.session_id
            WHERE session.run_id=? AND session.state='ACTIVE' AND session.generation=(SELECT max(generation) FROM wa.tracking_session WHERE run_id=?)
            ORDER BY fix.sequence
            """,(rs,n)->new WaApproachPolicy.Fix(rs.getLong("sequence"),rs.getTimestamp("captured_at").toInstant(),rs.getDouble("latitude"),rs.getDouble("longitude"),rs.getDouble("accuracy_m"),(Double)rs.getObject("heading_deg")),run,run);
        return WaApproachPolicy.assess(fixes,((Number)row.get("latitude_snapshot")).doubleValue(),((Number)row.get("longitude_snapshot")).doubleValue(),clock.now(),true).approaching();
    }
    List<Map<String,Object>> aliases(UUID box,String instance) {
        return jdbc.queryForList("""
            SELECT a.alias_type,a.alias_value FROM wa.approach_delivery_route route JOIN wa.integration_inbox e
              ON e.id=route.recipient_source_event_id JOIN wa.identity_alias a ON a.id::text=e.payload_json->>'senderAliasId'
              AND a.identity_id=e.identity_id AND a.provider=e.provider AND a.instance_id=e.instance_id
            JOIN wa.message_outbox o ON o.id=route.outbox_id AND o.recipient_identity_id=e.identity_id
            WHERE route.outbox_id=? AND route.instance_id=? AND route.environment=? AND a.alias_type IN ('PN','LID') FOR SHARE OF a
            """,box,instance,settings.environment());
    }
    Instant deadline(UUID box) {
        var rows=jdbc.queryForList("""
            SELECT session.last_captured_at,session.lease_expires_at FROM wa.message_outbox o JOIN wa.tracking_session session ON session.run_id=o.run_id
            WHERE o.id=? ORDER BY session.generation DESC LIMIT 1
            """,box);
        if(rows.isEmpty() || !(rows.get(0).get("last_captured_at") instanceof Timestamp captured)) return Instant.MIN;
        Instant lease=((Timestamp)rows.get(0).get("lease_expires_at")).toInstant();
        return captured.toInstant().plusSeconds(20).isBefore(lease)?captured.toInstant().plusSeconds(20):lease;
    }
}
