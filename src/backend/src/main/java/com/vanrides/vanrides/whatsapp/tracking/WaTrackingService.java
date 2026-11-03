package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.commands.WaRunPreparation;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Device ownership is derived from the proof-bound credential, never a body or a link. */
@Service
public class WaTrackingService {
    private final JdbcTemplate jdbc; private final WaSettings settings; private final WaClock clock;
    private final WaCrypto crypto; private final WaTrackingAccess access; private final WaTrackingStore store;
    private final WaRunPreparation preparation; private final WaTrackingLifecycle lifecycle;
    public WaTrackingService(JdbcTemplate jdbc,WaSettings settings,WaClock clock,WaCrypto crypto) {
        this.jdbc=jdbc; this.settings=settings; this.clock=clock; this.crypto=crypto;
        access=new WaTrackingAccess(jdbc,settings,clock); store=new WaTrackingStore(jdbc,crypto).environment(settings.environment());
        preparation=new WaRunPreparation(jdbc,clock,settings); lifecycle=new WaTrackingLifecycle(store,clock);
    }
    /** Private in-memory result; only attachStartLink may persist its token, encrypted for delivery. */
    @Transactional public ObjectNode requestStart(UUID driver,UUID occurrence,boolean resume,String rosterDigest) {
        if(!settings.newStartsEnabled()) throw WaProblem.denied();
        if(settings.startLinkBaseUrl().isEmpty()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
        access.lockDriver(driver);
        if(!resume && !WaCrypto.same(preparation.rosterDigest(driver,occurrence),rosterDigest)) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        var devices=jdbc.queryForList("""
            SELECT d.id FROM wa.driver_device d JOIN wa.driver_authorization a ON a.driver_id=d.driver_id
            JOIN wa.profile p ON p.identity_id=d.driver_id WHERE d.driver_id=? AND d.environment=?
              AND d.status='ACTIVE' AND d.generation=a.generation AND p.status='ACTIVE' AND p.driver_enabled FOR UPDATE OF d
            """,driver,settings.environment());
        if(devices.size()!=1) throw WaProblem.denied();
        var days=jdbc.queryForList("SELECT version FROM wa.routine_occurrence WHERE id=? AND driver_id=?",occurrence,driver);
        if(days.size()!=1) throw WaProblem.missing();
        UUID run=null; long version=((Number)days.get(0).get("version")).longValue();
        var runs=jdbc.queryForList("SELECT id,version,state,snapshot_json->>'rosterDigest' digest FROM wa.operation_run WHERE occurrence_id=? AND driver_id=? FOR UPDATE",occurrence,driver);
        if(resume) {
            if(runs.size()!=1 || !Set.of("START_FAILED","DEGRADED").contains(runs.get(0).get("state"))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            if(!WaCrypto.same(rosterDigest,(String)runs.get(0).get("digest"))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            run=(UUID)runs.get(0).get("id"); version=((Number)runs.get(0).get("version")).longValue();
        } else if(!runs.isEmpty() && !"PLANNED".equals(runs.get(0).get("state"))) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
        jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE driver_id=? AND state='PENDING'",driver);
        UUID intent=UUID.randomUUID(); String token=WaCrypto.randomToken(); Instant now=clock.now(),until=now.plusSeconds(300);
        jdbc.update("""
            INSERT INTO wa.start_intent(id,driver_id,occurrence_id,device_id,run_id,token_hash,environment,intent_action,expected_version,roster_digest,expires_at,created_at)
            VALUES (?,?,?,?,?,?,?,?,?,?,?,?)
            """,intent,driver,occurrence,devices.get(0).get("id"),run,WaCrypto.digest(token),settings.environment(),resume?"RESUME":"START",version,rosterDigest,ts(until),ts(now));
        var result=WaJson.object("{}").put("intentId",intent.toString()).put("intentToken",token).put("expiresAt",until.toString())
            .put("expectedVersion",version).put("action",resume?"RESUME":"START");
        if(run==null) result.putNull("runId"); else result.put("runId",run.toString()); return result;
    }
    public void attachStartLink(UUID box,UUID command,UUID driver,ObjectNode intent) {
        UUID id=WaJson.uuid(intent,"intentId"); String token=WaJson.text(intent,"intentToken",43,43);
        String fragment="#v1."+id+"."+intent.path("expectedVersion").longValue()+"."+token;
        jdbc.update("UPDATE wa.start_intent SET request_command_id=? WHERE id=? AND driver_id=? AND state='PENDING'",command,id,driver);
        jdbc.update("UPDATE wa.message_outbox SET start_intent_id=?,start_payload_ciphertext=?,expires_at=LEAST(expires_at,?) WHERE id=? AND recipient_identity_id=? AND command_id=?",
            id,crypto.encrypt(WaStartDelivery.aad(box,command,driver),fragment),ts(WaTrackingPolicy.instant(intent,"expiresAt")),box,driver,command);
    }
    @Transactional public ObjectNode activate(WaPrincipal actor,UUID intentId,ObjectNode body,String key) {
        WaJson.fields(body,"expectedVersion","foregroundConfirmed","intentToken"); return activateIntent(actor,intentId,null,body,key,"TRACK_ACTIVATE");
    }
    @Transactional public ObjectNode resume(WaPrincipal actor,UUID run,ObjectNode body,String key) {
        WaJson.fields(body,"expectedVersion","foregroundConfirmed","intentId","intentToken");
        return activateIntent(actor,WaJson.uuid(body,"intentId"),run,body,key,"TRACK_RESUME");
    }
    private ObjectNode activateIntent(WaPrincipal actor,UUID id,UUID requestedRun,ObjectNode body,String key,String kind) {
        if(!"CONFIRMED".equals(WaJson.text(body,"foregroundConfirmed",9,9))) throw WaProblem.invalid();
        String token=WaJson.text(body,"intentToken",43,43); com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier.canonical32(token);
        access.device(actor,false); var identity=body.deepCopy().put("_intentId",id.toString());
        if(requestedRun!=null) identity.put("_requestedRun",requestedRun.toString());
        var replay=store.replay(kind,actor.deviceId(),key,identity,clock.now());
        if(replay!=null) return controlIntent(actor,id,WaJson.uuid(replay,"runId"));
        if(!settings.newStartsEnabled()) throw WaProblem.denied();
        Intent intent=store.intent(id,actor.driverId());
        if(intent==null || !actor.deviceId().equals(intent.device()) || !WaCrypto.same(intent.hash(),WaCrypto.digest(token))
                || requestedRun!=null && (!requestedRun.equals(intent.run()) || !"RESUME".equals(intent.action()))) throw WaProblem.missing();
        if(!intent.expiry().isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        if(intent.version()!=WaJson.version(body)) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
        if("ACTIVATED".equals(intent.state())) {
            ObjectNode response=controlIntent(actor,id,intent.run()); store.remember(kind,actor.deviceId(),key,identity,response,clock.now()); return response;
        }
        if(!"PENDING".equals(intent.state())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        UUID run;
        if("START".equals(intent.action())) {
            var day=jdbc.queryForList("SELECT version FROM wa.routine_occurrence WHERE id=? AND driver_id=?",intent.occurrence(),actor.driverId());
            if(day.size()!=1 || ((Number)day.get(0).get("version")).longValue()!=intent.version()) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            if(!jdbc.queryForList("SELECT id FROM wa.operation_run WHERE driver_id=? AND state IN ('STARTING','START_FAILED','TRACKING','DEGRADED')",actor.driverId()).isEmpty()) throw new WaProblem(WaProblem.Code.ACTIVE_RUN_EXISTS);
            run=preparation.prepare(actor.driverId(),intent.occurrence(),intent.roster());
        } else {
            Run existing=store.run(intent.run(),actor.driverId());
            if(existing==null || existing.version()!=intent.version() || !Set.of("START_FAILED","DEGRADED").contains(existing.state())) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            run=existing.id();
        }
        // Re-read credentials and time after the occurrence/membership locks.
        access.device(actor,false); if(!intent.expiry().isAfter(clock.now())) throw new WaProblem(WaProblem.Code.INTENT_EXPIRED);
        Session session=store.current(run);
        if(session!=null && !session.lease().isAfter(clock.now()) && Set.of("STARTING","ACTIVE","DEGRADED","STOP_REQUESTED").contains(session.state())) store.stop(session,"EXPIRED",clock.now());
        session=store.current(run);
        if(session!=null && Set.of("STARTING","ACTIVE","DEGRADED","STOP_REQUESTED").contains(session.state())) store.stop(session,"REVOKED",clock.now());
        session=store.create(run,actor.driverId(),actor.deviceId(),id,clock.now());
        Instant until=credentialExpiry(actor.deviceId());
        if(!until.isAfter(clock.now())) throw WaProblem.unauthorized();
        jdbc.update("UPDATE wa.tracking_session SET lease_expires_at=LEAST(lease_expires_at,?) WHERE id=?",ts(until),session.id());
        jdbc.update("UPDATE wa.operation_run SET state='STARTING',start_failure_expires_at=NULL,version=version+1 WHERE id=?",run);
        jdbc.update("UPDATE wa.start_intent SET state='ACTIVATED',run_id=?,consumed_at=? WHERE id=?",run,ts(clock.now()),id);
        ObjectNode response=control(actor,run); store.remember(kind,actor.deviceId(),key,identity,response,clock.now()); return response;
    }
    @Transactional public ObjectNode samples(WaPrincipal actor,UUID id,ObjectNode body) {
        access.device(actor,false); Session session=owned(actor,id); lifecycle.reconcile(session,clock.now()); access.device(actor,false);
        return new WaTrackingSamples(store).ingest(store.session(id,actor.driverId()),store.run(session.run(),actor.driverId()),body,clock.now(),settings.enabled());
    }
    @Transactional public ObjectNode state(WaPrincipal actor,UUID id,ObjectNode body,String key) {
        return new WaTrackingState(store,access,settings,clock,lifecycle).record(actor,id,body,key);
    }
    @Transactional public ObjectNode status(WaPrincipal actor) {
        access.device(actor,true); Instant credential=credentialExpiry(actor.deviceId());
        var rows=jdbc.queryForList("""
            SELECT r.id FROM wa.operation_run r JOIN wa.tracking_session s ON s.run_id=r.id
            WHERE r.driver_id=? AND s.device_id=? ORDER BY s.created_at DESC,s.generation DESC LIMIT 1
            """,actor.driverId(),actor.deviceId());
        Run run=rows.isEmpty()?null:store.run((UUID)rows.get(0).get("id"),actor.driverId()); Session session=run==null?null:store.current(run.id());
        if(run!=null) session=store.forDevice(run.id(),actor.deviceId());
        if(session!=null) { lifecycle.reconcile(session,clock.now()); session=store.forDevice(run.id(),actor.deviceId()); run=store.run(run.id(),actor.driverId()); }
        var result=WaJson.object("{}").put("deviceId",actor.deviceId().toString()).put("driverId",actor.driverId().toString())
            .put("credentialExpiresAt",credential.toString()).put("entityVersion",run==null?0:run.version());
        if(run==null) { result.putNull("runId"); result.putNull("runState"); } else result.put("runId",run.id().toString()).put("runState",run.state());
        if(session==null || !actor.deviceId().equals(session.device())) { session=null; result.putNull("trackingSessionId"); }
        else result.put("trackingSessionId",session.id().toString());
        WaTrackingResponses.common(result,session,run,clock.now(),settings.enabled() && credential.isAfter(clock.now()) && !actor.revoked());
        return result.put("sessionState",session==null?"IDLE":session.stoppedAt()!=null?"STOPPED":
            "CONTINUE".equals(result.path("action").asText())?"RUNNING":"STOP_PENDING");
    }
    private ObjectNode control(WaPrincipal actor,UUID id) {
        Run run=store.run(id,actor.driverId()); if(run==null) throw WaProblem.missing(); Session session=store.current(id);
        if(session!=null && !session.device().equals(actor.deviceId())) throw WaProblem.missing();
        return WaTrackingResponses.control(session,run,clock.now(),settings.enabled());
    }
    private ObjectNode controlIntent(WaPrincipal actor,UUID intent,UUID runId) {
        Run run=store.run(runId,actor.driverId()); Session session=store.forIntent(intent);
        if(run==null || session==null || !session.device().equals(actor.deviceId())) throw WaProblem.missing();
        lifecycle.reconcile(session,clock.now());
        return WaTrackingResponses.control(store.session(session.id(),actor.driverId()),store.run(runId,actor.driverId()),clock.now(),settings.enabled());
    }
    private Session owned(WaPrincipal actor,UUID id) {
        Session session=store.session(id,actor.driverId()); if(session==null || !session.device().equals(actor.deviceId())) throw WaProblem.missing(); return session;
    }
    Instant credentialExpiry(UUID device) {
        var value=jdbc.queryForObject("SELECT max(expires_at) FROM wa.device_token WHERE device_id=? AND kind='REFRESH' AND revoked_at IS NULL AND consumed_at IS NULL",Timestamp.class,device);
        return value==null?clock.now():value.toInstant();
    }
}
