package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingWatchdog;
import java.sql.Timestamp;
import java.util.UUID;
import org.testcontainers.containers.PostgreSQLContainer;

final class WaTrackingTestSupport {
    final WaRunTestSupport f; final WaTransportTestSupport h;
    final WaTrackingService tracking; final WaTrackingWatchdog watchdog;
    final UUID device; final WaPrincipal actor;
    ObjectNode intent,activation;
    WaTrackingTestSupport(PostgreSQLContainer<?> postgres) {
        f=new WaRunTestSupport(postgres); h=f.h; device=f.pair(); UUID token=UUID.randomUUID(),family=UUID.randomUUID();
        String jkt=h.jdbc.queryForObject("SELECT jkt FROM wa.driver_device WHERE id=?",String.class,device);
        for(String kind:new String[]{"ACCESS","REFRESH"}) h.jdbc.update("""
            INSERT INTO wa.device_token(id,device_id,token_hash,kind,family_id,created_at,expires_at) VALUES (?,?,?,?,?,?,?)
            """,kind.equals("ACCESS")?token:UUID.randomUUID(),device,WaCrypto.digest(WaCrypto.randomToken()),kind,family,
            Timestamp.from(h.clock.now()),Timestamp.from(h.clock.now().plusSeconds(kind.equals("ACCESS")?3600:86400)));
        actor=new WaPrincipal(WaPrincipal.Kind.DEVICE,token,device,f.driver,null,jkt,1,false);
        tracking=h.proxy(new WaTrackingService(h.jdbc,h.settings,h.clock,h.crypto));
        watchdog=h.proxy(new WaTrackingWatchdog(h.jdbc,h.settings,h.clock,h.crypto));
    }
    ObjectNode request(boolean resume) {
        String digest=resume?f.s.string("SELECT snapshot_json->>'rosterDigest' FROM wa.operation_run WHERE id=?",run()):f.digest();
        ObjectNode result=tracking.requestStart(f.driver,f.item.occurrence(),resume,digest);
        UUID event=h.event(f.driver,WaJson.object("{}").put("text","Pedido sintético").put("senderAliasId",
            h.jdbc.queryForObject("SELECT id FROM wa.identity_alias WHERE identity_id=?",UUID.class,f.driver).toString()));
        UUID command=UUID.randomUUID(),box=UUID.randomUUID();
        h.transaction.executeWithoutResult(status->{
            h.jdbc.update("""
                INSERT INTO wa.command_receipt(command_id,identity_id,inbox_event_id,idempotency_key,command_type,request_hash,result_json,created_at,expires_at)
                VALUES (?,?,?,?,?,?,'{}',?,?)
                """,command,f.driver,event,command.toString(),resume?"REQUEST_RESUME":"REQUEST_START",WaCrypto.digest("synthetic-tracking"),Timestamp.from(h.clock.now()),Timestamp.from(h.clock.now().plusSeconds(86400)));
            WaFoundationResponses.saveRun(h.jdbc,box,command,f.driver,"Confirme no aplicativo.",h.clock.now());
            tracking.attachStartLink(box,command,f.driver,result);
        });
        return result;
    }
    ObjectNode activate() { intent=request(false); activation=tracking.activate(actor,WaJson.uuid(intent,"intentId"),activateBody(intent),"synthetic-activate-110"); return activation; }
    ObjectNode activateBody(ObjectNode value) { return WaJson.object("{}").put("expectedVersion",value.path("expectedVersion").longValue()).put("foregroundConfirmed","CONFIRMED").put("intentToken",value.path("intentToken").asText()); }
    UUID run() { return WaJson.uuid(activation,"runId"); }
    UUID session() { return WaJson.uuid(activation,"trackingSessionId"); }
    long generation() { return activation.path("generation").longValue(); }
    ObjectNode sample(long sequence,long seconds,double accuracy) { return WaJson.object("{}").put("sequence",sequence).put("capturedAt",h.clock.now().plusSeconds(seconds).toString()).put("latitude",0).put("longitude",0).put("accuracyM",accuracy); }
    ObjectNode batch(ObjectNode... samples) {
        ObjectNode body=WaJson.object("{}").put("sessionId",session().toString()).put("generation",generation()).put("batchId",UUID.randomUUID().toString()).put("deviceTime",h.clock.now().toString());
        var list=body.putArray("samples"); for(var sample:samples) list.add(sample); return body;
    }
    ObjectNode state(String state) { return WaJson.object("{}").put("sessionId",session().toString()).put("generation",generation()).put("state",state); }
    String sessionState() { return f.s.string("SELECT state FROM wa.tracking_session WHERE id=?",session()); }
    String runState() { return f.s.string("SELECT state FROM wa.operation_run WHERE id=?",run()); }
}
