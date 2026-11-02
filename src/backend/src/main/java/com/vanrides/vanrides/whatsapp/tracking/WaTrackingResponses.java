package com.vanrides.vanrides.whatsapp.tracking;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import java.time.Instant;
import static com.vanrides.vanrides.whatsapp.tracking.WaTrackingStore.*;

final class WaTrackingResponses {
    private WaTrackingResponses() { }
    static String action(Session session,Run run,Instant now,boolean enabled) {
        return enabled && run!=null && java.util.Set.of("STARTING","TRACKING","DEGRADED").contains(run.state())
            && collecting(session) && session.lease().isAfter(now)?"CONTINUE":"STOP";
    }
    static ObjectNode control(Session session,Run run,Instant now,boolean enabled) {
        var result=WaJson.object("{}").put("runId",run.id().toString()).put("state",run.state()).put("entityVersion",run.version());
        if(session==null) result.putNull("trackingSessionId"); else result.put("trackingSessionId",session.id().toString());
        return common(result,session,run,now,enabled);
    }
    static ObjectNode common(ObjectNode result,Session session,Run run,Instant now,boolean enabled) {
        result.put("generation",session==null?0:session.generation()).put("serverTime",now.toString())
            .put("action",action(session,run,now,enabled)).put("gpsState",session!=null && WaTrackingPolicy.fresh(session.captured(),now)
                && "FRESH".equals(session.gps()) && "CONTINUE".equals(action(session,run,now,enabled))?"FRESH":session!=null && "CLOCK_SKEW".equals(session.gps())?"CLOCK_SKEW":"STALE");
        if(session==null) result.putNull("leaseExpiresAt"); else result.put("leaseExpiresAt",session.lease().toString());
        if(session==null || session.stopRef()==null) result.putNull("stopRequestRef"); else result.put("stopRequestRef",session.stopRef().toString());
        return result;
    }
}
