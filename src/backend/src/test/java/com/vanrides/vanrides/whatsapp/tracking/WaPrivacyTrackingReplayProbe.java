package com.vanrides.vanrides.whatsapp.tracking;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
/** Test seam exercising the second production operation-replay reader. */
public final class WaPrivacyTrackingReplayProbe {
    public static ObjectNode read(JdbcTemplate jdbc,WaCrypto crypto,Instant now,String kind,UUID scope,String key,ObjectNode request){
        return new WaTrackingStore(jdbc,crypto).replay(kind,scope,key,request,now);
    }
}
