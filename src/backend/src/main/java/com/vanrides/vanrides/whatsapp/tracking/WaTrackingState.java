package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Set;
import java.util.UUID;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

final class WaTrackingState {
    private final WaTrackingStore store; private final WaTrackingAccess access; private final WaSettings settings;
    private final WaClock clock; private final WaTrackingLifecycle lifecycle;
    WaTrackingState(WaTrackingStore store,WaTrackingAccess access,WaSettings settings,WaClock clock,WaTrackingLifecycle lifecycle) {
        this.store=store; this.access=access; this.settings=settings; this.clock=clock; this.lifecycle=lifecycle;
    }
    ObjectNode record(WaPrincipal actor,UUID id,ObjectNode body,String key) {
        WaTrackingPolicy.optionalFields(body,Set.of("sessionId","generation","state"),Set.of("stopRequestRef"));
        String state=WaJson.text(body,"state",6,17);
        if(!Set.of("STARTING","ACTIVE","PERMISSION_DENIED","UNAVAILABLE","STOPPED").contains(state)) throw WaProblem.invalid();
        if(!id.equals(WaJson.uuid(body,"sessionId"))) throw WaProblem.invalid();
        boolean stopped="STOPPED".equals(state); access.device(actor,stopped);
        Session session=store.session(id,actor.driverId());
        if(session==null || !actor.deviceId().equals(session.device()) || WaTrackingPolicy.positive(body,"generation")!=session.generation()) throw WaProblem.missing();
        ObjectNode replay=store.replay("TRACK_STATE",actor.deviceId(),key,body,clock.now());
        if(replay!=null) return response(store.session(id,actor.driverId()));
        UUID knownStop=session.stopRef();
        lifecycle.reconcile(session,clock.now()); session=store.session(id,actor.driverId()); Run run=store.run(session.run(),actor.driverId());
        access.device(actor,stopped);
        if(stopped) {
            if(knownStop!=null && (!body.hasNonNull("stopRequestRef") || !knownStop.equals(WaJson.uuid(body,"stopRequestRef")))) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            if(session.stopRef()==null && body.hasNonNull("stopRequestRef")) throw new WaProblem(WaProblem.Code.VERSION_CONFLICT);
            store.jdbc.update("UPDATE wa.tracking_session SET state='STOPPED',stopped_at=COALESCE(stopped_at,?),lease_expires_at=LEAST(lease_expires_at,?),gps_state='STALE',version=version+1 WHERE id=?",ts(clock.now()),ts(clock.now()),id);
            if(collecting(session) && !terminal(run) && store.current(run.id()).id().equals(id)) lifecycle.failOrDegrade(session,run,clock.now());
        } else if("CONTINUE".equals(WaTrackingResponses.action(session,run,clock.now(),settings.enabled()))) {
            if("PERMISSION_DENIED".equals(state)) {
                store.stop(session,"STOP_REQUESTED",clock.now()); lifecycle.failOrDegrade(session,run,clock.now());
            } else {
                var expiry=store.jdbc.queryForObject("SELECT max(expires_at) FROM wa.device_token WHERE device_id=? AND kind='REFRESH' AND revoked_at IS NULL AND consumed_at IS NULL",Timestamp.class,actor.deviceId());
                Instant until=clock.now().plusSeconds(300); if(expiry==null) until=clock.now(); else if(expiry.toInstant().isBefore(until)) until=expiry.toInstant();
                store.jdbc.update("UPDATE wa.tracking_session SET lease_expires_at=?,version=version+1 WHERE id=?",ts(until),id);
                if("UNAVAILABLE".equals(state)) {
                    store.jdbc.update("UPDATE wa.tracking_session SET state='DEGRADED',gps_state='STALE',version=version+1 WHERE id=?",id);
                    lifecycle.failOrDegrade(session,run,clock.now());
                }
            }
        } else if(collecting(session)) store.stop(session,"STOP_REQUESTED",clock.now());
        ObjectNode result=response(store.session(id,actor.driverId()));
        store.remember("TRACK_STATE",actor.deviceId(),key,body,result,clock.now()); return result;
    }
    private ObjectNode response(Session session) {
        return WaTrackingResponses.common(WaJson.object("{}").put("sessionId",session.id().toString()),session,
            store.run(session.run(),session.driver()),clock.now(),settings.enabled());
    }
}
