package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Live authorization for a vehicle, never a passenger pickup or a model-facing position. */
public final class WaVehicleLocation {
    public static final String POLICY="driver-location-v1";
    public static final String UNAVAILABLE="A localização atual do veículo está indisponível. Consulte novamente quando o acompanhamento estiver ativo.";
    private final JdbcTemplate jdbc; private final WaClock clock; private final WaSettings settings;
    public WaVehicleLocation(JdbcTemplate jdbc,WaClock clock,WaSettings settings) { this.jdbc=jdbc; this.clock=clock; this.settings=settings; }

    public List<Map<String,Object>> runs(UUID passenger,boolean lock) {
        if(lock) jdbc.queryForList("""
            SELECT p.identity_id FROM wa.profile p WHERE p.identity_id=? OR p.identity_id IN
              (SELECT driver_id FROM wa.driver_passenger_link WHERE passenger_id=?) ORDER BY p.identity_id FOR SHARE
            """,passenger,passenger);
        if(Boolean.TRUE.equals(jdbc.queryForObject("SELECT EXISTS(SELECT 1 FROM wa.profile WHERE identity_id=? AND role='DRIVER')",Boolean.class,passenger))) return driverRuns(passenger,lock);
        return jdbc.queryForList("""
            SELECT r.id,r.driver_id,r.routine_id,rt.name,rt.direction,r.local_date,l.valid_until
            FROM wa.operation_run r JOIN wa.routine rt ON rt.id=r.routine_id
            JOIN wa.run_stop s ON s.run_id=r.id AND s.passenger_id=?
            JOIN wa.driver_passenger_link l ON l.driver_id=r.driver_id AND l.passenger_id=s.passenger_id
            JOIN wa.profile p ON p.identity_id=s.passenger_id JOIN wa.profile d ON d.identity_id=r.driver_id
            JOIN wa.identity pi ON pi.id=p.identity_id JOIN wa.identity di ON di.id=d.identity_id
            LEFT JOIN wa.test_cohort c ON c.id=pi.test_cohort_id
            WHERE r.state IN ('STARTING','TRACKING','DEGRADED') AND s.state<>'CANCELED'
              AND l.state='APPROVED' AND l.valid_from<=? AND (l.valid_until IS NULL OR l.valid_until>?)
              AND p.status='ACTIVE' AND p.role='PASSENGER' AND p.notice_version='wa-mvp-v1'
              AND d.status='ACTIVE' AND d.role='DRIVER' AND d.driver_enabled AND d.notice_version='wa-mvp-v1'
              AND ((pi.test_cohort_id=di.test_cohort_id AND c.environment=?)
                OR (pi.test_cohort_id IS NULL AND di.test_cohort_id IS NULL AND ?=false))
              AND NOT EXISTS(SELECT 1 FROM wa.conversation_state cs WHERE cs.identity_id IN (p.identity_id,d.identity_id) AND cs.stage='PAUSED')
            ORDER BY r.local_date,r.id
            """+(lock?" FOR SHARE OF r,s,l,rt":""),passenger,ts(clock.now()),ts(clock.now()),settings.environment(),settings.cohortOnly());
    }
    private List<Map<String,Object>> driverRuns(UUID driver,boolean lock) {
        return jdbc.queryForList("""
            SELECT r.id,r.driver_id,r.routine_id,rt.name,rt.direction,r.local_date,NULL::timestamptz valid_until
            FROM wa.operation_run r JOIN wa.routine rt ON rt.id=r.routine_id AND rt.driver_id=r.driver_id
            JOIN wa.routine_occurrence day ON day.id=r.occurrence_id JOIN wa.profile p ON p.identity_id=r.driver_id
            JOIN wa.driver_authorization auth ON auth.driver_id=r.driver_id
            JOIN wa.identity person ON person.id=p.identity_id LEFT JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id
            WHERE r.driver_id=? AND r.state IN ('STARTING','TRACKING','DEGRADED') AND day.state='PLANNED'
              AND p.role='DRIVER' AND p.status='ACTIVE' AND p.driver_enabled AND p.notice_version='wa-mvp-v1'
              AND (cohort.environment=? OR (person.test_cohort_id IS NULL AND ?=false))
              AND NOT EXISTS(SELECT 1 FROM wa.conversation_state conversation WHERE conversation.identity_id=p.identity_id AND conversation.stage='PAUSED')
            ORDER BY r.local_date,r.id
            """+(lock?" FOR SHARE OF r,rt,day,p,auth":""),driver,settings.environment(),settings.cohortOnly());
    }
    public boolean authorized(UUID passenger,UUID run,boolean lock) {
        return run!=null && settings.enabled() && runs(passenger,lock).stream().anyMatch(row->run.equals(row.get("id")));
    }
    public ObjectNode pin(UUID passenger,UUID run) {
        return pin(passenger,run,true);
    }
    public ObjectNode pin(UUID passenger,UUID run,boolean lock) {
        if(!authorized(passenger,run,lock)) return null;
        var rows=jdbc.queryForList("""
            SELECT s.last_latitude,s.last_longitude,s.last_captured_at,s.last_accuracy_m,s.lease_expires_at
            FROM wa.tracking_session s JOIN wa.operation_run r ON r.id=s.run_id
            JOIN wa.driver_device d ON d.id=s.device_id JOIN wa.driver_authorization a ON a.driver_id=s.driver_id
            WHERE r.id=? AND r.state='TRACKING' AND s.state='ACTIVE' AND s.gps_state='FRESH'
              AND s.generation=(SELECT max(current.generation) FROM wa.tracking_session current WHERE current.run_id=r.id)
              AND s.lease_expires_at>? AND d.status='ACTIVE' AND d.generation=a.generation AND d.environment=?
              AND EXISTS(SELECT 1 FROM wa.device_token token WHERE token.device_id=d.id AND token.kind='REFRESH'
                AND token.revoked_at IS NULL AND token.consumed_at IS NULL AND token.expires_at>?)
            """+(lock?" FOR SHARE OF s,d,a":""),run,ts(clock.now()),settings.environment(),ts(clock.now()));
        if(rows.size()!=1) return null; var row=rows.get(0);
        if(!(row.get("last_captured_at") instanceof Timestamp captured) || !WaTrackingPolicy.fresh(captured.toInstant(),clock.now())
                || !captured.toInstant().plusSeconds(20).isAfter(clock.now()) || captured.toInstant().isAfter(clock.now())
                || !(row.get("last_accuracy_m") instanceof Number accuracy) || accuracy.doubleValue()>50
                || !(row.get("last_latitude") instanceof Number lat) || !(row.get("last_longitude") instanceof Number lon)) return null;
        return WaJson.object("{}").put("type","STATIC_PIN").put("latitude",lat.doubleValue()).put("longitude",lon.doubleValue())
            .put("capturedAt",captured.toInstant().toString()).put("label","Localização atual do veículo");
    }
    public ObjectNode unavailable() { return WaJson.object("{}").put("type","TEXT").put("text",UNAVAILABLE).put("referenceTime",clock.now().toString()); }
    public Instant deadline(UUID passenger,UUID run) {
        Instant link=runs(passenger,false).stream().filter(row->run!=null && run.equals(row.get("id")))
            .map(row->row.get("valid_until") instanceof Timestamp t?t.toInstant():Instant.MAX).findFirst().orElse(clock.now().plusSeconds(10));
        if(run==null) return link;
        var deadlines=jdbc.query("""
            SELECT LEAST(s.lease_expires_at,(SELECT max(t.expires_at) FROM wa.device_token t WHERE t.device_id=s.device_id
              AND t.kind='REFRESH' AND t.revoked_at IS NULL AND t.consumed_at IS NULL))
            FROM wa.tracking_session s WHERE s.run_id=? ORDER BY s.generation DESC LIMIT 1
            """,(rs,n)->rs.getTimestamp(1).toInstant(),run);
        return deadlines.isEmpty() || link.isBefore(deadlines.get(0))?link:deadlines.get(0);
    }
    private static Timestamp ts(Instant value) { return Timestamp.from(value); }
}
