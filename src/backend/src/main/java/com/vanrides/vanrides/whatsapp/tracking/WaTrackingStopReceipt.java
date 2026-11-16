package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** The retained device key can read only an existing sensor ACK, never renew or reconcile tracking. */
@Service
public class WaTrackingStopReceipt {
    private final JdbcTemplate jdbc;
    private final WaSettings settings;
    private final WaClock clock;
    public WaTrackingStopReceipt(JdbcTemplate jdbc,WaSettings settings,WaClock clock) {
        this.jdbc=jdbc; this.settings=settings; this.clock=clock;
    }
    @Transactional(readOnly=true)
    public ObjectNode read(WaDpopVerifier.Proof proof,UUID id,ObjectNode body) {
        // stopRequestRef is required but explicitly nullable for a client-initiated stop.
        if(body.size()!=2 || !body.has("generation") || !body.has("stopRequestRef")) throw WaProblem.invalid();
        long generation=WaTrackingPolicy.positive(body,"generation");
        UUID stopRef=body.get("stopRequestRef").isNull()?null:WaJson.uuid(body,"stopRequestRef");
        var rows=jdbc.queryForList("""
            SELECT s.generation,s.state,s.stop_request_ref,s.stopped_at,r.state run_state
            FROM wa.tracking_session s
            JOIN wa.driver_device d ON d.id=s.device_id AND d.driver_id=s.driver_id
            JOIN wa.operation_run r ON r.id=s.run_id AND r.driver_id=s.driver_id
            JOIN wa.profile p ON p.identity_id=s.driver_id
            JOIN wa.identity i ON i.id=p.identity_id LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE s.id=? AND d.jkt=? AND d.environment=? AND d.public_jwk<>'{}'
              AND p.role='DRIVER' AND p.privacy_erased_at IS NULL
              AND (?=false OR c.environment=?)
            """,id,proof.jkt(),settings.environment(),settings.cohortOnly(),settings.environment());
        if(rows.size()!=1) throw WaProblem.missing();
        var row=rows.get(0);
        if(generation!=((Number)row.get("generation")).longValue()
                || !java.util.Objects.equals(stopRef,row.get("stop_request_ref"))
                || !"STOPPED".equals(row.get("state")) || row.get("stopped_at")==null)
            throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        var result=WaJson.object("{}").put("schemaVersion",1).put("sessionId",id.toString())
            .put("generation",generation).put("stopConfirmed",true)
            .put("stoppedAt",((Timestamp)row.get("stopped_at")).toInstant().toString())
            .put("serverTime",clock.now().toString()).put("runState",(String)row.get("run_state"));
        if(stopRef==null) result.putNull("stopRequestRef"); else result.put("stopRequestRef",stopRef.toString());
        return result;
    }
}
