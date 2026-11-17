package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Explicit business completion; merely reading an offer never stops or starts GPS. */
@Service
public class WaFinishService {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock;
    private final WaFinishStore finishes; private final WaTrackingAccess access; private final WaTrackingStore tracking;
    public WaFinishService(JdbcTemplate jdbc,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc; this.settings=settings; this.clock=clock;
        finishes=new WaFinishStore(jdbc); access=new WaTrackingAccess(jdbc,settings,clock);
        tracking=new WaTrackingStore(jdbc,crypto).environment(settings.environment());
    }
    @Transactional public ObjectNode request(UUID driver,UUID runId,long expectedVersion) {
        if(settings.startLinkBaseUrl().isEmpty()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        access.lockDriver(driver); Run run=tracking.run(runId,driver); Session session=tracking.current(runId);
        if(run==null || terminal(run) || run.version()!=expectedVersion || session==null) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        var devices=jdbc.queryForList("""
            SELECT d.id FROM wa.driver_device d JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
            JOIN wa.profile p ON p.identity_id=d.driver_id JOIN wa.identity i ON i.id=p.identity_id
            LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE d.id=? AND d.driver_id=? AND d.environment=? AND d.status='ACTIVE'
              AND d.generation=a.generation AND p.status='ACTIVE' AND p.driver_enabled
              AND p.privacy_erased_at IS NULL AND (?=false OR c.environment=?) FOR UPDATE OF d
            """,session.device(),driver,settings.environment(),settings.cohortOnly(),settings.environment());
        if(devices.size()!=1) throw WaProblem.denied();
        jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE driver_id=? AND state='PENDING'",driver);
        UUID id=UUID.randomUUID(); String token=WaCrypto.randomToken(); Instant now=clock.now(),until=now.plusSeconds(300);
        jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,run_id,token_hash,environment,intent_action,expected_version,expires_at,created_at)
            SELECT ?,driver_id,occurrence_id,?,?,?,?,'FINISH',version,?,? FROM wa.operation_run WHERE id=? AND driver_id=?
            """,id,session.device(),runId,WaCrypto.digest(token),settings.environment(),ts(until),ts(now),runId,driver);
        jdbc.update("""
            INSERT INTO wa.finish_intent_binding(intent_id,run_id,driver_id,device_id,session_id,session_generation,issued_run_state,baseline_stopped_at,baseline_stop_ref)
            VALUES (?,?,?,?,?,?,?,?,?)
            """,id,runId,driver,session.device(),session.id(),session.generation(),run.state(),
            "STOPPED".equals(session.state())?session.stoppedAt()==null?null:ts(session.stoppedAt()):null,
            "STOPPED".equals(session.state()) && session.stoppedAt()!=null?session.stopRef():null);
        return WaJson.object("{}").put("intentId",id.toString()).put("intentToken",token).put("runId",runId.toString())
            .put("expectedVersion",expectedVersion).put("expiresAt",until.toString()).put("action","FINISH");
    }
    @Transactional public ObjectNode preview(WaPrincipal actor,UUID id,ObjectNode body) {
        WaJson.fields(body,"expectedVersion","intentToken"); access.device(actor,false);
        Map<String,Object> row=owned(actor,id); finishes.token(row,body);
        boolean completed=row.get("confirmed_at")!=null;
        if(!completed) finishes.ready(row,clock.now());
        ObjectNode result=finishes.receipt(row);
        result.put("state",completed?"COMPLETED":"READY").put("expiresAt",((Timestamp)row.get("expires_at")).toInstant().toString());
        result.remove(java.util.List.of("finishConsumedAt","stopRequestRef","stoppedAt"));
        result.putObject("trip").put("name",(String)row.get("name")).put("direction",(String)row.get("direction"))
            .put("localDate",row.get("local_date").toString()).put("localTime",(String)row.get("local_time"))
            .put("timezone",(String)row.get("timezone"));
        return result;
    }
    @Transactional public ObjectNode confirm(WaPrincipal actor,UUID runId,ObjectNode body,String key) {
        WaJson.fields(body,"intentId","expectedVersion","intentToken","foregroundConfirmed");
        if(!"CONFIRMED".equals(WaJson.text(body,"foregroundConfirmed",9,9))) throw WaProblem.invalid();
        access.device(actor,true); UUID id=WaJson.uuid(body,"intentId"); Map<String,Object> row=owned(actor,id);
        finishes.token(row,body); if(!runId.equals(row.get("run_id"))) throw WaProblem.missing();
        ObjectNode request=body.deepCopy().put("_runId",runId.toString());
        var prior=jdbc.queryForList("SELECT request_hash FROM wa.operation_replay WHERE operation_kind='TRACK_FINISH' AND scope_id=? AND request_key=?",actor.deviceId(),key);
        if(!prior.isEmpty() && !WaCrypto.same((String)prior.get(0).get("request_hash"),WaCrypto.digest(WaJson.canonical(request))))
            throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
        if(row.get("confirmed_at")==null) {
            access.device(actor,false); finishes.ready(row,clock.now());
            // Preserve the immutable intent version; only the fenced own-STOP transition may differ.
            new WaRunFinishes(jdbc,settings).manual(actor.driverId(),runId,((Number)row.get("run_version")).longValue(),clock.now());
            Session stopped=tracking.session((UUID)row.get("session_id"),actor.driverId());
            jdbc.update("UPDATE wa.finish_intent_binding SET confirmed_at=?,confirmed_stop_ref=? WHERE intent_id=? AND confirmed_at IS NULL",ts(clock.now()),stopped.stopRef(),id);
            jdbc.update("UPDATE wa.start_intent SET consumed_at=? WHERE id=?",ts(clock.now()),id);
            row=finishes.find(id,actor.driverId(),true);
            if(finishes.receipt(row).path("stopAcknowledged").asBoolean()) new WaTrackingNotices(jdbc).stopped(stopped,clock.now());
        }
        ObjectNode result=WaJson.object("{}"); result.set("receipt",finishes.receipt(row));
        result.set("control",WaTrackingResponses.control(tracking.session((UUID)row.get("session_id"),actor.driverId()),tracking.run(runId,actor.driverId()),clock.now(),false));
        if(prior.isEmpty()) tracking.remember("TRACK_FINISH",actor.deviceId(),key,request,result,clock.now());
        return result;
    }
    @Transactional(readOnly=true) public ObjectNode receipt(WaDpopVerifier.Proof proof,UUID id,ObjectNode body) {
        WaJson.fields(body,"expectedVersion","intentToken"); UUID driver=finishes.driver(id);
        Map<String,Object> row=driver==null?null:finishes.find(id,driver,false); finishes.token(row,body);
        if(row.get("confirmed_at")==null || !proof.jkt().equals(row.get("jkt")) || "{}".equals(row.get("public_jwk"))
                || !"DRIVER".equals(row.get("profile_role"))
                || row.get("privacy_erased_at")!=null || !settings.environment().equals(row.get("environment"))
                || settings.cohortOnly() && !settings.environment().equals(row.get("cohort_environment"))) throw WaProblem.missing();
        return finishes.receipt(row);
    }
    /** Delivery already holds its profile SHARE fence; do not upgrade it to UPDATE. */
    public boolean deliveryValid(UUID id,UUID driver) {
        Map<String,Object> row=finishes.find(id,driver,false);
        if(!finishes.active(row,settings.environment(),settings.cohortOnly()) || row.get("confirmed_at")!=null) return false;
        try { finishes.ready(row,clock.now()); return true; } catch(WaProblem stale) { return false; }
    }
    private Map<String,Object> owned(WaPrincipal actor,UUID id) {
        Map<String,Object> row=finishes.find(id,actor.driverId(),false);
        if(row==null || !actor.deviceId().equals(row.get("device_id")) || !settings.environment().equals(row.get("environment"))) throw WaProblem.missing();
        tracking.run((UUID)row.get("run_id"),actor.driverId());
        row=finishes.find(id,actor.driverId(),true);
        if(row==null || row.get("privacy_erased_at")!=null) throw WaProblem.missing();
        return row;
    }
}
