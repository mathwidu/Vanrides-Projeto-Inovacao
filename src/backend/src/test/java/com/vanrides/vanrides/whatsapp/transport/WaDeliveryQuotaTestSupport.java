package com.vanrides.vanrides.whatsapp.transport;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import static com.vanrides.vanrides.whatsapp.transport.WaTransportTestSupport.*;

/** Synthetic pre-policy ledger facts for over-limit/mixed-state scope tests; never network submissions. */
final class WaDeliveryQuotaTestSupport {
    static WaPrincipal legacy(WaDeliveryConnectionTestSupport f, String environment, String instance) {
        Instant current=f.h.clock.now(); f.h.time.set(current.minusSeconds(60));
        var principal=f.h.principal(WaPrincipal.Kind.DELIVERY_WORKER,instance,environment); f.h.time.set(current); return principal;
    }
    static void history(WaDeliveryConnectionTestSupport f, UUID actor, WaPrincipal principal, Instant started, String state) {
        Instant current=f.h.clock.now(); f.h.time.set(started);
        var box=actor==null?f.enqueue():f.enqueue(actor); f.h.time.set(current);
        String failure=state.equals("NOT_SUBMITTED")?"CHANNEL_UNAVAILABLE":state.equals("UNKNOWN")?"TIMEOUT":state.equals("FAILED")?"REJECTED":null;
        String external=java.util.Set.of("SUBMITTED","DELIVERED","READ").contains(state)?"synthetic_quota_"+box.id():null;
        String boxState=state.equals("NOT_SUBMITTED")?"CANCELED":state;
        ObjectNode receipt=WaJson.object("{}").put("outboxId",box.id().toString()).put("state",boxState).put("serverTime",started.plusSeconds(1).toString());
        f.h.jdbc.update("""
            INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,state,
              instance_id,external_message_id,error_code,started_at,completed_at,credential_id,submit_before,result_request_hash,result_json)
            VALUES (?,?,1,1,?,?,?,?,?,?,?,?,?,?,?::jsonb)
            """,UUID.randomUUID(),box.id(),WaCrypto.digest(WaCrypto.randomToken()),state,principal.instanceId(),external,failure,
            Timestamp.from(started),Timestamp.from(started.plusSeconds(1)),principal.credentialId(),Timestamp.from(started.plusSeconds(10)),
            WaCrypto.digest("synthetic-quota-"+box.id()),receipt.toString());
        f.h.jdbc.update("UPDATE wa.message_outbox SET state=?,lease_generation=1 WHERE id=?",boxState,box.id());
    }
    static void history(WaDeliveryConnectionTestSupport f, UUID actor, WaPrincipal principal, int count, Instant started) {
        String[] states={"SUBMITTED","DELIVERED","READ","UNKNOWN","FAILED","NOT_SUBMITTED"};
        for(int i=0;i<count;i++) history(f,actor,principal,started,states[i%states.length]);
    }
    static ObjectNode claim(WaDeliveryConnectionTestSupport f,int limit) {
        return f.outbox.claim(f.worker,json("{}").put("batchLimit",limit).put("leaseSeconds",60));
    }
}
