package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** A consumed finish stays bound to its original session even after a later trip. */
final class WaFinishStore {
    private final JdbcTemplate jdbc;
    WaFinishStore(JdbcTemplate jdbc) { this.jdbc=jdbc; }
    Map<String,Object> find(UUID id,UUID driver,boolean lock) {
        var rows=jdbc.queryForList("""
            SELECT i.*,b.session_id,b.session_generation,b.issued_run_state,b.baseline_stopped_at,
              b.baseline_stop_ref,b.confirmed_at,b.confirmed_stop_ref,r.state run_state,r.version run_version,
              r.local_date,r.snapshot_json->'routine'->>'name' name,
              r.snapshot_json->'routine'->>'direction' direction,
              r.snapshot_json->'routine'->>'local_time' local_time,
              r.snapshot_json->'routine'->>'timezone' timezone,
              s.state session_state,s.stopped_at,s.stop_request_ref,s.generation current_generation,
              d.jkt,d.public_jwk::text public_jwk,d.status device_status,d.generation device_generation,
              a.generation authorization_generation,p.status profile_status,p.role profile_role,p.driver_enabled,p.privacy_erased_at,
              c.environment cohort_environment
            FROM wa.start_intent i JOIN wa.finish_intent_binding b ON b.intent_id=i.id
            JOIN wa.operation_run r ON r.id=b.run_id AND r.driver_id=b.driver_id
            JOIN wa.tracking_session s ON s.id=b.session_id AND s.run_id=b.run_id
            JOIN wa.driver_device d ON d.id=i.device_id AND d.driver_id=i.driver_id
            JOIN wa.driver_authorization a ON a.driver_id=i.driver_id
            JOIN wa.profile p ON p.identity_id=i.driver_id
            JOIN wa.identity identity ON identity.id=i.driver_id
            LEFT JOIN wa.test_cohort c ON c.id=identity.test_cohort_id
            WHERE i.id=? AND i.driver_id=? AND i.intent_action='FINISH'
            """+(lock?" FOR UPDATE OF i,b,s":""),id,driver);
        return rows.size()==1?rows.get(0):null;
    }
    UUID driver(UUID id) {
        var rows=jdbc.queryForList("SELECT driver_id FROM wa.finish_intent_binding WHERE intent_id=?",id);
        return rows.size()==1?(UUID)rows.get(0).get("driver_id"):null;
    }
    void token(Map<String,Object> row,ObjectNode body) {
        String token=WaJson.text(body,"intentToken",43,43);
        com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier.canonical32(token);
        if(row==null || !WaCrypto.same((String)row.get("token_hash"),WaCrypto.digest(token))) throw WaProblem.missing();
        if(((Number)row.get("expected_version")).longValue()!=WaJson.version(body))
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
    }
    boolean current(Map<String,Object> row) {
        var latest=jdbc.queryForList("SELECT id FROM wa.tracking_session WHERE run_id=? ORDER BY generation DESC LIMIT 1",row.get("run_id"));
        return latest.size()==1 && row.get("session_id").equals(latest.get(0).get("id"))
            && row.get("session_generation").equals(row.get("current_generation"));
    }
    boolean versionAllowed(Map<String,Object> row) {
        long expected=((Number)row.get("expected_version")).longValue(),current=((Number)row.get("run_version")).longValue();
        if(current==expected) return true;
        boolean ownStop="STOPPED".equals(row.get("session_state")) && row.get("stopped_at") instanceof Timestamp stopped
            && !stopped.toInstant().isBefore(((Timestamp)row.get("created_at")).toInstant());
        return expected<Long.MAX_VALUE && current==expected+1 && ownStop
            && ("TRACKING".equals(row.get("issued_run_state")) && "DEGRADED".equals(row.get("run_state"))
                || "STARTING".equals(row.get("issued_run_state")) && "START_FAILED".equals(row.get("run_state")));
    }
    void ready(Map<String,Object> row,Instant now) {
        if(!"PENDING".equals(row.get("state")) || !((Timestamp)row.get("expires_at")).toInstant().isAfter(now))
            throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        if(!Set.of("STARTING","START_FAILED","TRACKING","DEGRADED").contains(row.get("run_state"))
                || !current(row) || !versionAllowed(row)) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
    }
    boolean active(Map<String,Object> row,String environment,boolean cohortOnly) {
        return row!=null && environment.equals(row.get("environment")) && row.get("privacy_erased_at")==null
            && "ACTIVE".equals(row.get("device_status")) && "ACTIVE".equals(row.get("profile_status"))
            && Boolean.TRUE.equals(row.get("driver_enabled"))
            && row.get("device_generation").equals(row.get("authorization_generation"))
            && (!cohortOnly || environment.equals(row.get("cohort_environment")));
    }
    ObjectNode receipt(Map<String,Object> row) {
        boolean acknowledged=row.get("confirmed_at")!=null && "COMPLETED".equals(row.get("run_state"))
            && "STOPPED".equals(row.get("session_state")) && row.get("stopped_at")!=null
            && Objects.equals(row.get("confirmed_stop_ref"),row.get("stop_request_ref"));
        if(acknowledged) {
            Timestamp stopped=(Timestamp)row.get("stopped_at");
            acknowledged=row.get("baseline_stopped_at")!=null
                ? stopped.equals(row.get("baseline_stopped_at")) && Objects.equals(row.get("baseline_stop_ref"),row.get("stop_request_ref"))
                : !stopped.toInstant().isBefore(((Timestamp)row.get("created_at")).toInstant());
        }
        ObjectNode result=WaJson.object("{}").put("intentId",row.get("id").toString()).put("runId",row.get("run_id").toString())
            .put("trackingSessionId",row.get("session_id").toString()).put("sessionGeneration",((Number)row.get("session_generation")).longValue())
            .put("expectedVersion",((Number)row.get("expected_version")).longValue()).put("state","COMPLETED")
            .put("stopAcknowledged",acknowledged);
        timestamp(result,"finishConsumedAt",row.get("confirmed_at")); timestamp(result,"stoppedAt",row.get("stopped_at"));
        if(row.get("confirmed_stop_ref")==null) result.putNull("stopRequestRef"); else result.put("stopRequestRef",row.get("confirmed_stop_ref").toString());
        return result;
    }
    private static void timestamp(ObjectNode value,String field,Object raw) {
        if(raw instanceof Timestamp timestamp) value.put(field,timestamp.toInstant().toString()); else value.putNull(field);
    }
}
