package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Called only for newly accepted fixes, under the tracking run transaction. */
final class WaApproachDecisions {
    private final JdbcTemplate jdbc; private final WaSettings settings;
    WaApproachDecisions(JdbcTemplate jdbc,WaSettings settings) { this.jdbc=jdbc; this.settings=settings; }
    void accept(UUID session,UUID run,JsonNode fix,Instant now) {
        if(!settings.approachEnabled()) return;
        Instant captured=WaTrackingPolicy.instant(fix,"capturedAt");
        jdbc.update("DELETE FROM wa.approach_fix WHERE session_id=? AND expires_at<=?",session,ts(now));
        jdbc.update("""
            INSERT INTO wa.approach_fix(session_id,sequence,captured_at,latitude,longitude,accuracy_m,heading_deg,expires_at)
            VALUES (?,?,?,?,?,?,?,?) ON CONFLICT DO NOTHING
            """,session,fix.path("sequence").longValue(),ts(captured),fix.path("latitude").doubleValue(),fix.path("longitude").doubleValue(),
            fix.path("accuracyM").doubleValue(),fix.has("headingDeg")?fix.path("headingDeg").doubleValue():null,ts(captured.plusSeconds(20)));
        jdbc.update("DELETE FROM wa.approach_fix WHERE session_id=? AND sequence NOT IN (SELECT sequence FROM wa.approach_fix WHERE session_id=? ORDER BY sequence DESC LIMIT 3)",session,session);
        var runs=jdbc.queryForList("SELECT routine_id,driver_id FROM wa.operation_run WHERE id=? AND state='TRACKING'",run);
        if(runs.size()!=1) return; UUID routine=(UUID)runs.get(0).get("routine_id");
        var stops=jdbc.queryForList("""
            SELECT stop.* FROM wa.run_stop stop WHERE run_id=? AND state IN ('WAITING','APPROACHING')
            ORDER BY (SELECT min(group_stop.stop_order) FROM wa.run_stop group_stop
              WHERE group_stop.run_id=stop.run_id AND group_stop.physical_stop_key=stop.physical_stop_key),id FOR UPDATE
            """,run);
        if(stops.isEmpty()) return; var next=stops.get(0); UUID physical=(UUID)next.get("physical_stop_key");
        List<WaApproachPolicy.Fix> fixes=jdbc.query("SELECT * FROM wa.approach_fix WHERE session_id=? ORDER BY sequence",(rs,n)->
            new WaApproachPolicy.Fix(rs.getLong("sequence"),rs.getTimestamp("captured_at").toInstant(),rs.getDouble("latitude"),rs.getDouble("longitude"),
                rs.getDouble("accuracy_m"),(Double)rs.getObject("heading_deg")),session);
        var decision=WaApproachPolicy.assess(fixes,number(next,"latitude_snapshot"),number(next,"longitude_snapshot"),now,settings.approachReviewed(routine));
        var old=jdbc.queryForList("""
            SELECT progress.*,receipt.captured_at last_evaluated_at FROM wa.approach_progress progress
            LEFT JOIN wa.tracking_sample_receipt receipt ON receipt.session_id=progress.last_session_id AND receipt.sequence=progress.last_sequence
            WHERE progress.run_id=? AND progress.physical_stop_key=? FOR UPDATE OF progress
            """,run,physical);
        double nearest=old.isEmpty()?80000:number(old.get(0),"nearest_m");
        Instant nearestAt=old.isEmpty()?captured:((Timestamp)old.get(0).get("nearest_at")).toInstant();
        if(!old.isEmpty() && (!session.equals(old.get(0).get("last_session_id"))
            || !(old.get(0).get("last_evaluated_at") instanceof Timestamp previous) || captured.isAfter(previous.toInstant().plusSeconds(20)))) nearest=80000;
        if(Set.of("GPS_JUMP","DIRECTION_UNCERTAIN","UNREVIEWED_GEOGRAPHY").contains(decision.reason())) nearest=80000;
        boolean improved=decision.approaching() && decision.distance()<nearest;
        if(improved) { nearest=decision.distance(); nearestAt=captured; }
        String terminal=null;
        if(decision.departing() && nearest<=200 && decision.distance()>nearest+WaApproachPolicy.HYSTERESIS_M && captured.isAfter(nearestAt))
            terminal=nearest<=WaApproachPolicy.PASS_M?"PASSED":"SKIPPED";
        String reason=terminal==null?decision.reason():terminal;
        jdbc.update("""
            INSERT INTO wa.approach_progress(run_id,physical_stop_key,nearest_m,nearest_at,last_session_id,last_sequence,policy_version,last_reason,suppressed_count)
            VALUES (?,?,?,?,?,?,?,?,?) ON CONFLICT(run_id,physical_stop_key) DO UPDATE SET nearest_m=EXCLUDED.nearest_m,
              nearest_at=EXCLUDED.nearest_at,last_session_id=EXCLUDED.last_session_id,last_sequence=EXCLUDED.last_sequence,
              policy_version=EXCLUDED.policy_version,last_reason=EXCLUDED.last_reason,
              suppressed_count=wa.approach_progress.suppressed_count+EXCLUDED.suppressed_count
            """,run,physical,nearest,ts(nearestAt),session,fix.path("sequence").longValue(),WaApproachPolicy.VERSION,reason,
            !decision.approaching() && terminal==null && decision.distance()<=WaApproachPolicy.NOTICE_M?1:0);
        if(terminal!=null) {
            jdbc.update("UPDATE wa.run_stop SET state=?,version=version+1 WHERE run_id=? AND physical_stop_key=? AND state IN ('WAITING','APPROACHING')",terminal,run,physical);
            return;
        }
        if(!decision.approaching()) return;
        jdbc.update("UPDATE wa.run_stop SET state='APPROACHING',version=version+1 WHERE run_id=? AND physical_stop_key=? AND state='WAITING'",run,physical);
        for(var stop:stops) if(physical.equals(stop.get("physical_stop_key"))) enqueue(session,run,stop,fix.path("sequence").longValue(),now,captured);
    }
    private void enqueue(UUID session,UUID run,Map<String,Object> stop,long sequence,Instant now,Instant captured) {
        UUID passenger=(UUID)stop.get("passenger_id");
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.message_outbox WHERE run_id=? AND stop_id=? AND recipient_identity_id=? AND purpose='APPROACH')",Boolean.class,run,stop.get("id"),passenger))) return;
        if(!new WaVehicleLocation(jdbc,new com.vanrides.vanrides.whatsapp.config.WaClock(java.time.Clock.fixed(now,java.time.ZoneOffset.UTC)),settings).authorized(passenger,run,false)) return;
        if(!Boolean.TRUE.equals(jdbc.queryForObject("SELECT COALESCE(preferences_json->'APPROACH_MESSAGES'='true'::jsonb,false) FROM wa.profile WHERE identity_id=?",Boolean.class,passenger))) return;
        var scopes=jdbc.queryForList("""
            SELECT e.instance_id FROM wa.tracking_session s JOIN wa.start_intent intent ON intent.id=s.origin_intent_id
            JOIN wa.command_receipt receipt ON receipt.command_id=intent.request_command_id
            JOIN wa.integration_inbox e ON e.id=receipt.inbox_event_id
            WHERE s.id=? AND intent.environment=? AND e.provider='EVOLUTION'
            """,session,settings.environment());
        if(scopes.size()!=1) return; String instance=(String)scopes.get(0).get("instance_id");
        var sources=jdbc.queryForList("""
            SELECT e.id FROM wa.integration_inbox e JOIN wa.identity_alias a ON a.id::text=e.payload_json->>'senderAliasId'
              AND a.identity_id=e.identity_id AND a.provider=e.provider AND a.instance_id=e.instance_id
            WHERE e.identity_id=? AND e.provider='EVOLUTION' AND e.category='MESSAGE' AND e.instance_id=?
              AND a.alias_type IN ('PN','LID') ORDER BY e.received_order DESC,e.id LIMIT 1 FOR KEY SHARE OF e SKIP LOCKED
            """,passenger,instance);
        if(sources.size()!=1) return;
        UUID box=UUID.randomUUID();
        var payload=WaJson.object("{}").put("type","TEXT").put("text","O veículo está se aproximando do seu ponto. Aguarde no local combinado; a passagem não confirma embarque.")
            .put("referenceTime",captured.toString());
        int inserted=jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,run_id,stop_id,policy_version,payload_json,not_before,expires_at,created_at)
            VALUES (?,?,'APPROACH',?,?,?,?::jsonb,?,?,?) ON CONFLICT DO NOTHING
            """,box,passenger,run,stop.get("id"),WaApproachPolicy.VERSION,payload.toString(),ts(now),ts(now.plusSeconds(60)),ts(now));
        if(inserted==1) jdbc.update("INSERT INTO wa.approach_delivery_route(outbox_id,environment,instance_id,recipient_source_event_id,session_id,sequence) VALUES (?,?,?,?,?,?)",
            box,settings.environment(),instance,sources.get(0).get("id"),session,sequence);
    }
    private static double number(Map<String,Object> row,String name) { return ((Number)row.get(name)).doubleValue(); }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
