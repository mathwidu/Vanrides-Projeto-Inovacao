package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;

/** Secret start links are revealed only at the authorized transport boundary. */
public final class WaStartDelivery {
    private final JdbcTemplate jdbc; private final WaClock clock; private final WaSettings settings; private final WaCrypto crypto;
    public WaStartDelivery(JdbcTemplate jdbc,WaClock clock,WaSettings settings) {
        this.jdbc=jdbc; this.clock=clock; this.settings=settings; crypto=new WaCrypto(settings);
    }
    public static String aad(UUID box,UUID command,UUID actor) { return "run-start:"+box+":"+command+":"+actor; }
    public ObjectNode reveal(UUID box,UUID actor,ObjectNode content) {
        if(content==null) return null;
        var boxes=jdbc.queryForList("SELECT start_intent_id,start_payload_ciphertext,command_id,policy_version FROM wa.message_outbox WHERE id=?",box);
        if(boxes.isEmpty()) return null; var row=boxes.get(0);
        if(row.get("start_intent_id")==null) {
            if(relevant(box)) return content;
            cancel(box); return null;
        }
        var intents=jdbc.queryForList("""
            SELECT i.* FROM wa.start_intent i JOIN wa.driver_device d ON d.id=i.device_id
            JOIN wa.driver_authorization a ON a.driver_id=i.driver_id WHERE i.id=? AND i.driver_id=?
              AND i.request_command_id=? AND i.environment=? AND i.state='PENDING' AND i.expires_at>?
              AND d.status='ACTIVE' AND d.generation=a.generation FOR SHARE OF i,d,a
            """,row.get("start_intent_id"),actor,row.get("command_id"),settings.environment(),Timestamp.from(clock.now()));
        if(!settings.newStartsEnabled() || intents.size()!=1 || row.get("start_payload_ciphertext")==null) { cancel(box); return null; }
        try {
            var intent=intents.get(0);
            if("START".equals(intent.get("intent_action"))) {
                String digest=new WaRunPreparation(jdbc,clock,settings).rosterDigestForDelivery(actor,(UUID)intent.get("occurrence_id"));
                if(!WaCrypto.same(digest,(String)intent.get("roster_digest"))) { cancel(box); return null; }
            } else {
                var runs=jdbc.queryForList("SELECT state,version,snapshot_json->>'rosterDigest' digest FROM wa.operation_run WHERE id=? AND driver_id=? FOR SHARE",intent.get("run_id"),actor);
                if(runs.size()!=1 || !java.util.Set.of("START_FAILED","DEGRADED").contains(runs.get(0).get("state"))
                        || ((Number)runs.get(0).get("version")).longValue()!=((Number)intent.get("expected_version")).longValue()
                        || !WaCrypto.same((String)runs.get(0).get("digest"),(String)intent.get("roster_digest"))) { cancel(box); return null; }
            }
            String fragment=crypto.decrypt(aad(box,(UUID)row.get("command_id"),actor),(byte[])row.get("start_payload_ciphertext"));
            if(settings.startLinkBaseUrl().isEmpty() || !fragment.matches("#v1\\."+row.get("start_intent_id")+"\\.[0-9]{1,16}\\.[A-Za-z0-9_-]{43}")) throw new IllegalArgumentException();
            return content.deepCopy().put("text","Abra o aplicativo VanRides Motorista e confirme a coleta: "+settings.startLinkBaseUrl()+"/motorista/saida"+fragment);
        } catch(RuntimeException invalid) { cancel(box); return null; }
    }
    public boolean relevant(UUID box) {
        var row=notice(box);
        if(row==null) return false;
        if(!"run-gps-v1".equals(row.get("policy_version"))) return true;
        if(row.get("notice_intent")==null || !row.get("notice_intent").equals(row.get("session_intent"))) return false;
        boolean valid=confirmed(row);
        return "run-gps-confirmed".equals(row.get("response_part"))?valid:
            ("DEGRADED".equals(row.get("state")) || "START_FAILED".equals(row.get("state"))) && !valid;
    }
    /** authorize retains the driver's profile SHARE fence; official session/run mutations take profile UPDATE first. */
    public Instant deadline(UUID box) {
        var row=notice(box);
        if(row==null) return Instant.MIN;
        if(!"run-gps-v1".equals(row.get("policy_version")) || !"run-gps-confirmed".equals(row.get("response_part"))) return Instant.MAX;
        if(!(row.get("last_captured_at") instanceof Timestamp captured) || !(row.get("lease_expires_at") instanceof Timestamp lease)) return Instant.MIN;
        Instant freshUntil=captured.toInstant().plusSeconds(20);
        return freshUntil.isBefore(lease.toInstant())?freshUntil:lease.toInstant();
    }
    private Map<String,Object> notice(UUID box) {
        var rows=jdbc.queryForList("""
            SELECT o.policy_version,o.response_part,r.state,s.gps_state,s.last_captured_at,s.lease_expires_at,
              s.state session_state,s.stopped_at,s.origin_intent_id session_intent,i.id notice_intent,
              d.status device_status,d.environment,d.generation device_generation,a.generation current_generation
            FROM wa.message_outbox o LEFT JOIN wa.operation_run r ON r.id=o.run_id
            LEFT JOIN LATERAL (SELECT * FROM wa.tracking_session t WHERE t.run_id=r.id ORDER BY generation DESC LIMIT 1) s ON true
            LEFT JOIN wa.start_intent i ON i.request_command_id=o.command_id AND i.run_id=o.run_id AND i.driver_id=o.recipient_identity_id
            LEFT JOIN wa.driver_device d ON d.id=s.device_id AND d.driver_id=r.driver_id
            LEFT JOIN wa.driver_authorization a ON a.driver_id=r.driver_id
            WHERE o.id=?
            """,box);
        return rows.size()==1?rows.get(0):null;
    }
    private boolean confirmed(Map<String,Object> row) {
        return "TRACKING".equals(row.get("state")) && "ACTIVE".equals(row.get("session_state"))
            && row.get("stopped_at")==null && "FRESH".equals(row.get("gps_state"))
            && "ACTIVE".equals(row.get("device_status")) && settings.environment().equals(row.get("environment"))
            && row.get("device_generation")!=null && row.get("device_generation").equals(row.get("current_generation"))
            && row.get("lease_expires_at") instanceof Timestamp lease && lease.toInstant().isAfter(clock.now())
            && row.get("last_captured_at") instanceof Timestamp captured && WaTrackingPolicy.fresh(captured.toInstant(),clock.now());
    }
    private void cancel(UUID box) { jdbc.update("UPDATE wa.message_outbox SET state='CANCELED',start_payload_ciphertext=NULL,version=version+1 WHERE id=?",box); }
}
