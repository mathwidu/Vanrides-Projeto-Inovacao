package com.vanrides.vanrides.whatsapp.pairing;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
/** Test seam exercising the production reader, including its expiry-before-decrypt order. */
public final class WaPrivacyPairingReplayProbe {
    public static ObjectNode read(JdbcTemplate jdbc,WaCrypto crypto,WaClock clock,String kind,UUID scope,String key,ObjectNode request){
        return new WaReplayStore(jdbc,crypto,clock).read(kind,scope,key,request);
    }
}
