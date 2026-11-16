package com.vanrides.vanrides.whatsapp.tracking;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyHistoryProjectionFixture.*;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.privacy.WaPrivacyHistoryProjectionFixture;
import com.vanrides.vanrides.whatsapp.transport.WaDeliveryReconciler;
import com.vanrides.vanrides.whatsapp.transport.WaOutboxService;
import com.vanrides.vanrides.whatsapp.transport.WaTransportAccess;
import java.sql.Timestamp;
import java.util.Base64;
import java.util.Map;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

/** Real production services and durable replay records; no HTTP/device proof or external provider is claimed. */
@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyHistoricalControlIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=256m"))
        .withDatabaseName("wa_history_control").withUsername("synthetic_history").withPassword("synthetic-history-only");
    @Test void oldAndNewDeviceKeysReturnStopOrDomainErrorAndExpiredDeliveryNeverSends() {
        var f=new WaPrivacyHistoryProjectionFixture(POSTGRES);var settings=settings();var crypto=new WaCrypto(settings);
        UUID device=f.jdbc.queryForObject("SELECT id FROM wa.driver_device WHERE driver_id=?",UUID.class,f.driver),token=UUID.randomUUID();
        f.jdbc.update("UPDATE wa.driver_authorization SET generation=1 WHERE driver_id=?",f.driver);
        f.jdbc.update("UPDATE wa.driver_device SET status='ACTIVE',revoked_at=NULL WHERE id=?",device);
        for(String kind:new String[]{"ACCESS","REFRESH"})f.jdbc.update("""
            INSERT INTO wa.device_token(id,device_id,token_hash,kind,family_id,created_at,expires_at) VALUES (?,?,?,?,?,?,?)
            """,kind.equals("ACCESS")?token:UUID.randomUUID(),device,WaCrypto.digest(UUID.randomUUID().toString()),kind,UUID.randomUUID(),
            Timestamp.from(f.clock.now()),Timestamp.from(f.clock.now().plusSeconds(3600)));
        var actor=new WaPrincipal(WaPrincipal.Kind.DEVICE,token,device,f.driver,null,"A".repeat(43),1,false);
        var tracking=proxy(f,new WaTrackingService(f.jdbc,settings,f.clock,crypto));
        String intentToken=WaCrypto.randomToken();
        f.jdbc.update("UPDATE wa.start_intent SET device_id=?,token_hash=?,intent_action='RESUME',expected_version=7,roster_digest=? WHERE id=?",
            device,WaCrypto.digest(intentToken),DIGEST,f.intent);
        ObjectNode activation=WaJson.object("{}").put("expectedVersion",7).put("foregroundConfirmed","CONFIRMED").put("intentToken",intentToken);
        ObjectNode resume=activation.deepCopy().put("intentId",f.intent.toString());
        var store=new WaTrackingStore(f.jdbc,crypto).environment(ENV);
        new TransactionTemplate(f.manager).executeWithoutResult(tx->{
            var response=WaTrackingResponses.control(store.session(f.session,f.driver),store.run(f.run,f.driver),f.clock.now(),true);
            store.remember("TRACK_ACTIVATE",device,"old-activate",activation.deepCopy().put("_intentId",f.intent.toString()),response,f.clock.now());
            store.remember("TRACK_RESUME",device,"old-resume",resume.deepCopy().put("_intentId",f.intent.toString()).put("_requestedRun",f.run.toString()),response,f.clock.now());
        });
        ObjectNode stopped=WaJson.object("{}").put("sessionId",f.session.toString()).put("generation",1).put("state","STOPPED");
        assertThat(tracking.state(actor,f.session,stopped,"old-stop").path("action").asText()).isEqualTo("STOP");
        WaPrincipal delivery=principal(f,"DELIVERY_WORKER"),channel=principal(f,"CHANNEL");
        UUID callback=UUID.randomUUID(),box=UUID.randomUUID();String lease=WaCrypto.randomToken();
        f.jdbc.update("INSERT INTO wa.integration_inbox(id,provider,instance_id,event_key,category,state) VALUES (?,'EVOLUTION',?,?,'CONNECTION','COMPLETED')",callback,INSTANCE,callback.toString());
        f.jdbc.update("INSERT INTO wa.delivery_connection(environment,instance_id,state,ordering_at,recorded_at,channel_credential_id,source_receipt_id) VALUES (?,?,'OPEN',?,?,?,?)",
            ENV,INSTANCE,Timestamp.from(f.clock.now()),Timestamp.from(f.clock.now()),channel.credentialId(),callback);
        f.jdbc.update("""
            INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,occurrence_id,reminder_window,policy_version,payload_json,state,
              created_at,not_before,expires_at,lease_credential_id,lease_token_hash,lease_generation,lease_expires_at)
            VALUES (?,?,'REMINDER',?,'historical','attendance-v1','{}','EXPIRED',?,?,?,?,?,1,?)
            """,box,f.first,f.occurrence,Timestamp.from(f.clock.now().minusSeconds(91L*86400)),Timestamp.from(f.clock.now().minusSeconds(91L*86400)),
            Timestamp.from(f.clock.now().minusSeconds(90L*86400)),delivery.credentialId(),WaCrypto.digest(lease),Timestamp.from(f.clock.now().minusSeconds(90L*86400)));
        f.jdbc.update("""
            UPDATE wa.attendance_question SET outbox_id=?,source_inbox_event_id=(SELECT id FROM wa.integration_inbox WHERE identity_id=? LIMIT 1),
              sender_alias_id=(SELECT id FROM wa.identity_alias WHERE identity_id=?) WHERE passenger_id=?
            """,box,f.first,f.first,f.first);
        String receipt=f.jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(r) ORDER BY id)::text FROM wa.operation_replay r",String.class);
        f.finish();
        assertThat(f.jdbc.queryForObject("SELECT jsonb_agg(to_jsonb(r) ORDER BY id)::text FROM wa.operation_replay r",String.class)).isEqualTo(receipt);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance_question",Long.class)).isZero();
        assertThat(tracking.status(actor).path("sessionState").asText()).isEqualTo("STOPPED");
        assertThat(tracking.activate(actor,f.intent,activation,"old-activate").path("action").asText()).isEqualTo("STOP");
        assertThat(tracking.resume(actor,f.run,resume,"old-resume").path("action").asText()).isEqualTo("STOP");
        assertThatThrownBy(()->tracking.activate(actor,f.intent,activation,"new-activate")).isInstanceOf(WaProblem.class);
        assertThatThrownBy(()->tracking.resume(actor,f.run,resume,"new-resume")).isInstanceOf(WaProblem.class);
        assertThatThrownBy(()->tracking.requestStart(f.driver,f.occurrence,true,DIGEST)).isInstanceOf(WaProblem.class);
        assertThat(tracking.state(actor,f.session,stopped,"old-stop").path("action").asText()).isEqualTo("STOP");
        assertThat(tracking.state(actor,f.session,stopped,"new-stop").path("action").asText()).isEqualTo("STOP");
        ObjectNode batch=WaJson.object("{}").put("sessionId",f.session.toString()).put("generation",1).put("batchId",UUID.randomUUID().toString()).put("deviceTime",f.clock.now().toString());
        batch.putArray("samples").addObject().put("sequence",6).put("capturedAt",f.clock.now().toString()).put("latitude",0).put("longitude",0).put("accuracyM",5);
        var samples=tracking.samples(actor,f.session,batch);assertThat(samples.path("action").asText()).isEqualTo("STOP");assertThat(samples.path("accepted").isEmpty()).isTrue();
        var outbox=proxy(f,new WaOutboxService(f.jdbc,new WaTransportAccess(f.jdbc,settings,f.clock),new WaDeliveryReconciler(f.jdbc,f.clock),settings,f.clock));
        assertThatThrownBy(()->outbox.authorize(delivery,box,WaJson.object("{}").put("leaseToken",lease))).isInstanceOf(WaProblem.class);
        assertThat(f.jdbc.queryForObject("SELECT state FROM wa.message_outbox WHERE id=?",String.class,box)).isEqualTo("EXPIRED");
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.message_attempt",Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.message_outbox",Long.class)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT state FROM wa.operation_run WHERE id=?",String.class,f.run)).isEqualTo("COMPLETED");
    }
    private WaPrincipal principal(WaPrivacyHistoryProjectionFixture f,String kind){
        UUID id=UUID.randomUUID();f.jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,allowed_instance_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?,?,?)",
            id,kind,ENV,INSTANCE,WaCrypto.digest(id.toString()),Timestamp.from(f.clock.now()),Timestamp.from(f.clock.now().plusSeconds(3600)));
        return new WaPrincipal(WaPrincipal.Kind.valueOf(kind),id,null,null,INSTANCE,null,0,false);
    }
    private WaSettings settings(){
        String key=Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[32]);
        byte[] responseBytes=new byte[32];java.util.Arrays.fill(responseBytes,(byte)2);String response=Base64.getUrlEncoder().withoutPadding().encodeToString(responseBytes);
        return new WaSettings(new MockEnvironment().withProperty("vanrides.wa.enabled","true").withProperty("vanrides.wa.environment",ENV)
            .withProperty("vanrides.wa.test-cohort-only","true").withProperty("vanrides.wa.public-base-url","http://127.0.0.1:8080")
            .withProperty("vanrides.wa.start-link-base-url","https://synthetic.invalid").withProperty("vanrides.wa.hash-key",key)
            .withProperty("vanrides.wa.response-key",response).withProperty("vanrides.wa.operator-credential",key).withProperty("vanrides.wa.operator-expires-at","2035-01-01T00:00:00Z"));
    }
    @SuppressWarnings("unchecked") private <T>T proxy(WaPrivacyHistoryProjectionFixture f,T target){
        var proxy=new ProxyFactory(target);proxy.setProxyTargetClass(true);proxy.addAdvice(new TransactionInterceptor(f.manager,new AnnotationTransactionAttributeSource()));return(T)proxy.getProxy();
    }
}
