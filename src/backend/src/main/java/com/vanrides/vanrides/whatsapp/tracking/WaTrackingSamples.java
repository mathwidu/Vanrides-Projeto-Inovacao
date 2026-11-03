package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.UUID;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

/** Individual durable receipts do not infer a contiguous ACK from the highest sequence. */
final class WaTrackingSamples {
    private final WaTrackingStore store; private final WaTrackingNotices notices;
    WaTrackingSamples(WaTrackingStore store) { this.store=store; notices=new WaTrackingNotices(store.jdbc); }
    ObjectNode ingest(Session session,Run run,ObjectNode request,Instant now,boolean enabled) {
        WaJson.fields(request,"sessionId","generation","batchId","deviceTime","samples");
        if(!WaJson.uuid(request,"sessionId").equals(session.id()) || WaTrackingPolicy.positive(request,"generation")!=session.generation()) throw WaProblem.missing();
        UUID batch=WaJson.uuid(request,"batchId"); WaTrackingPolicy.instant(request,"deviceTime");
        var samples=request.get("samples");
        if(!samples.isArray() || samples.isEmpty() || samples.size()>30) throw WaProblem.invalid();
        String hash=WaCrypto.digest(WaJson.canonical(request));
        var prior=store.jdbc.queryForList("SELECT request_hash,result_json FROM wa.tracking_batch_receipt WHERE session_id=? AND batch_id=?",session.id(),batch);
        if(!prior.isEmpty()) {
            if(!WaCrypto.same(hash,(String)prior.get(0).get("request_hash"))) throw new WaProblem(WaProblem.Code.IDEMPOTENCY_CONFLICT);
            return response(receipt(WaJson.object(prior.get(0).get("result_json").toString())),session,run,now,enabled);
        }
        var ordered=new ArrayList<JsonNode>(); var sequences=new HashSet<Long>();
        for(var sample:samples) {
            long sequence=WaTrackingPolicy.positive(sample,"sequence");
            if(!sequences.add(sequence)) throw WaProblem.invalid();
            WaTrackingPolicy.rejection(sample,now); ordered.add(sample);
        }
        ordered.sort(Comparator.comparingLong(s->s.path("sequence").longValue()));
        var ack=WaJson.object("{}"); var accepted=ack.putArray("accepted"); var duplicate=ack.putArray("duplicate"); var rejected=ack.putArray("rejected");
        store.jdbc.update("INSERT INTO wa.tracking_batch_receipt(session_id,generation,batch_id,request_hash,result_json,received_at) VALUES (?,?,?,?,'{}'::jsonb,?)",
            session.id(),session.generation(),batch,hash,ts(now));
        long lastSequence=session.sequence(); Instant lastCaptured=session.captured(); JsonNode newest=null;
        boolean continuing="CONTINUE".equals(WaTrackingResponses.action(session,run,now,enabled));
        boolean clockSkew=WaTrackingPolicy.instant(request,"deviceTime").isAfter(now.plusSeconds(10));
        for(var sample:ordered) {
            long seq=sample.path("sequence").longValue(); String sampleHash=WaCrypto.digest(WaJson.canonical(sample));
            var receipt=store.jdbc.queryForList("SELECT sample_hash,outcome,rejection_code FROM wa.tracking_sample_receipt WHERE session_id=? AND sequence=?",session.id(),seq);
            if(!receipt.isEmpty()) {
                var old=receipt.get(0);
                if(!WaCrypto.same(sampleHash,(String)old.get("sample_hash"))) rejected.addObject().put("sequence",seq).put("code","SEQUENCE_CONFLICT");
                else if("ACCEPTED".equals(old.get("outcome"))) duplicate.add(seq);
                else rejected.addObject().put("sequence",seq).put("code",(String)old.get("rejection_code"));
                continue;
            }
            Instant captured=WaTrackingPolicy.instant(sample,"capturedAt");
            String code=continuing?WaTrackingPolicy.rejection(sample,now):terminal(run)?"RUN_TERMINAL":!session.lease().isAfter(now)?"LEASE_EXPIRED":"OLD_GENERATION";
            if(code==null && clockSkew) code="FUTURE";
            if(code==null && (seq<=lastSequence || lastCaptured!=null && !captured.isAfter(lastCaptured))) code="OUT_OF_ORDER";
            store.jdbc.update("""
                INSERT INTO wa.tracking_sample_receipt(id,session_id,generation,batch_id,sequence,captured_at,received_at,outcome,rejection_code,sample_hash)
                VALUES (?,?,?,?,?,?,?,?,?,?)
                """,UUID.randomUUID(),session.id(),session.generation(),batch,seq,ts(captured),ts(now),code==null?"ACCEPTED":"REJECTED",code,sampleHash);
            if(code==null) { accepted.add(seq); lastSequence=seq; lastCaptured=captured; newest=sample; }
            else { rejected.addObject().put("sequence",seq).put("code",code); if("FUTURE".equals(code)) clockSkew=true; }
        }
        if(newest!=null) {
            store.jdbc.update("""
                UPDATE wa.tracking_session SET state='ACTIVE',last_sequence=?,last_captured_at=?,last_received_at=?,
                  last_latitude=?,last_longitude=?,last_accuracy_m=?,gps_state='FRESH',version=version+1 WHERE id=?
                """,lastSequence,ts(lastCaptured),ts(now),newest.path("latitude").doubleValue(),newest.path("longitude").doubleValue(),newest.path("accuracyM").doubleValue(),session.id());
            store.jdbc.update("UPDATE wa.operation_run SET state='TRACKING',started_at=COALESCE(started_at,?),start_failure_expires_at=NULL,version=version+1 WHERE id=? AND state IN ('STARTING','DEGRADED')",ts(now),run.id());
            notices.confirmed(session,now);
        } else if(clockSkew && continuing) store.jdbc.update("UPDATE wa.tracking_session SET gps_state='CLOCK_SKEW',version=version+1 WHERE id=?",session.id());
        Session updated=store.session(session.id(),session.driver()); Run updatedRun=store.run(run.id(),run.driver());
        // Only sequence outcomes are immutable. Authorization and GPS are projected live,
        // including when replaying receipts written by an earlier version with metadata.
        store.jdbc.update("UPDATE wa.tracking_batch_receipt SET result_json=?::jsonb WHERE session_id=? AND batch_id=?",receipt(ack).toString(),session.id(),batch);
        return response(ack,updated,updatedRun,now,enabled);
    }
    private ObjectNode receipt(ObjectNode stored) {
        ObjectNode result=WaJson.object("{}");
        for(String field:new String[]{"accepted","duplicate","rejected"}) {
            var values=stored.get(field);
            if(values==null || !values.isArray()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
            result.set(field,values.deepCopy());
        }
        return result;
    }
    private ObjectNode response(ObjectNode ack,Session session,Run run,Instant now,boolean enabled) {
        ack.put("sessionId",session.id().toString()); return WaTrackingResponses.common(ack,session,run,now,enabled);
    }
}
