package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.mock.env.MockEnvironment;
import org.testcontainers.containers.PostgreSQLContainer;

final class WaPrivacyTechnicalFixture {
    final WaPrivacyTestSupport f;
    final WaSettings settings;
    final WaCrypto crypto;
    final UUID device=UUID.randomUUID(),session=UUID.randomUUID(),batch=UUID.randomUUID();
    final WaDpopVerifier.PublicMaterial material;
    final ObjectNode request=WaJson.object("{\"synthetic\":true}");
    WaPrivacyTechnicalFixture(PostgreSQLContainer<?> postgres){
        f=new WaPrivacyTestSupport(postgres,true);
        settings=new WaSettings(new MockEnvironment().withProperty("vanrides.wa.environment",WaPrivacyTestSupport.ENV)
            .withProperty("vanrides.wa.public-base-url","http://127.0.0.1:8080")
            .withProperty("vanrides.wa.hash-key",key((byte)1)).withProperty("vanrides.wa.response-key",key((byte)2))
            .withProperty("vanrides.wa.operator-credential",key((byte)3)).withProperty("vanrides.wa.operator-expires-at","2035-01-01T00:00:00Z"));
        crypto=new WaCrypto(settings);material=material();
        f.jdbc.update("INSERT INTO wa.driver_device(id,driver_id,jkt,public_jwk,generation,environment,platform,app_version) VALUES (?,?,?,?::jsonb,1,?,'ANDROID','synthetic-retention')",
            device,f.driver,material.jkt(),material.json(),WaPrivacyTestSupport.ENV);
    }
    WaPrivacyTechnicalRetention port(boolean dry){return new WaPrivacyTechnicalRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),dry);}
    void gps(){
        f.jdbc.update("INSERT INTO wa.tracking_session(id,run_id,driver_id,device_id,generation,state,lease_expires_at,last_sequence,stop_request_ref,stopped_at) VALUES (?,?,?,?,1,'STOPPED',?,7,?,?)",
            session,f.run,f.driver,device,Timestamp.from(f.time.now.plusSeconds(60)),UUID.randomUUID(),f.timestamp());
        f.jdbc.update("INSERT INTO wa.tracking_batch_receipt(session_id,generation,batch_id,request_hash,result_json,received_at) VALUES (?,1,?,?,'{\"accepted\":[1,7],\"duplicate\":[],\"rejected\":[]}',?)",
            session,batch,"f".repeat(64),f.timestamp());
        for(int sequence:new int[]{1,7})f.jdbc.update("INSERT INTO wa.tracking_sample_receipt(id,session_id,generation,batch_id,sequence,captured_at,received_at,outcome,sample_hash) VALUES (?,?,1,?,?,?,?,'ACCEPTED',?)",
            UUID.randomUUID(),session,batch,sequence,f.timestamp(),f.timestamp(),WaCrypto.digest("synthetic-"+sequence));
    }
    UUID replay(String kind,String key,Instant expires){
        UUID id=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.operation_replay(id,operation_kind,scope_id,request_key,request_hash,response_ciphertext,expires_at) VALUES (?,?,?,?,?,?,?)",
            id,kind,device,key,WaCrypto.digest(WaJson.canonical(request)),crypto.encrypt(kind+"\0"+device+"\0"+key,"{\"secret\":\"SEGREDO_SINTETICO\"}"),Timestamp.from(expires));
        return id;
    }
    WaPrivacyTechnicalRetention.Result sweep(){
        var actor=f.job(WaPrivacyTestSupport.ENV,WaPrivacyTestSupport.INSTANCE);
        WaPrivacyTechnicalRetention.Position cursor=null;
        for(int page=0;page<30;page++){
            var result=port(false).tick(actor,100,cursor);
            if(result.complete() || result.deferred()>0)return result;
            cursor=result.next();
        }
        throw new AssertionError("Synthetic retention did not finish");
    }
    static WaDpopVerifier.PublicMaterial material(){
        try{
            var generator=java.security.KeyPairGenerator.getInstance("EC");generator.initialize(new java.security.spec.ECGenParameterSpec("secp256r1"));
            var key=(java.security.interfaces.ECPublicKey)generator.generateKeyPair().getPublic();
            var jwk=WaJson.object("{}").put("kty","EC").put("crv","P-256").put("x",coordinate(key.getW().getAffineX())).put("y",coordinate(key.getW().getAffineY()));
            return WaDpopVerifier.publicMaterial(jwk);
        }catch(java.security.GeneralSecurityException e){throw new AssertionError(e);}
    }
    private static String coordinate(java.math.BigInteger value){byte[] input=value.toByteArray(),out=new byte[32];System.arraycopy(input,Math.max(0,input.length-32),out,Math.max(0,32-input.length),Math.min(32,input.length));return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(out);}
    private static String key(byte value){byte[] key=new byte[32];java.util.Arrays.fill(key,value);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(key);}
}
