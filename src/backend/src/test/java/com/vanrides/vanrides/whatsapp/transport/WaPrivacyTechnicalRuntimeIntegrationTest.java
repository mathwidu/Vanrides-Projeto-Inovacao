package com.vanrides.vanrides.whatsapp.transport;

import static org.assertj.core.api.Assertions.*;
import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.privacy.*;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyTechnicalRuntimeIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=WaTransportTestSupport.postgres("wa_privacy_technical_runtime");
    private WaTrackingTestSupport t;
    @BeforeEach void setup(){t=new WaTrackingTestSupport(POSTGRES);t.h.jdbc.update("UPDATE wa.test_cohort SET synthetic=true WHERE id=?",t.f.attendance.cohort);}
    private WaPrivacyTechnicalRetention port(){return new WaPrivacyTechnicalRetention(t.h.jdbc,t.h.manager,t.h.clock,WaPrivacyPolicy.syntheticTests(),false);}

    @Test void receiptsReplayInsideTtlAndLateBatchAfterPruningCanOnlyRejectAndStop(){
        t.activate();var body=t.batch(t.sample(1,0,5).put("latitude",12.3456).put("longitude",45.6789));
        var first=t.tracking.samples(t.actor,t.session(),body);var duplicate=t.tracking.samples(t.actor,t.session(),body);
        assertThat(first.path("accepted").toString()).isEqualTo(duplicate.path("accepted").toString());assertThat(first.path("accepted").size()).isEqualTo(1);
        assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt")).isEqualTo(1);
        t.h.jdbc.update("UPDATE wa.operation_run SET state='COMPLETED',completed_at=? WHERE id=?",Timestamp.from(t.h.clock.now()),t.run());
        t.tracking.state(t.actor,t.session(),t.state("STOPPED"),"synthetic-stop-before-retention");
        String before=t.f.s.string("SELECT jsonb_build_object('generation',generation,'last_sequence',last_sequence,'stop',stop_request_ref)::text FROM wa.tracking_session WHERE id=?",t.session());
        t.h.time.advance(8L*86400);
        t.h.jdbc.update("UPDATE wa.device_token SET expires_at=? WHERE device_id=?",Timestamp.from(t.h.clock.now().plusSeconds(3600)),t.device);
        var job=t.h.principal(WaPrincipal.Kind.SYSTEM_JOB);var result=port().tick(job,100,null);
        assertThat(result.complete()).isTrue();assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt")).isZero();
        assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_batch_receipt")).isZero();
        var late=t.tracking.samples(t.actor,t.session(),body);
        assertThat(late.path("accepted").size()).isZero();assertThat(late.path("action").asText()).isEqualTo("STOP");
        assertThat(late.path("rejected").get(0).path("code").asText()).isEqualTo("RUN_TERMINAL");
        var stopped=t.state("STOPPED");String stop=t.f.s.string("SELECT stop_request_ref::text FROM wa.tracking_session WHERE id=?",t.session());
        if(stop!=null)stopped.put("stopRequestRef",stop);
        assertThat(t.tracking.state(t.actor,t.session(),stopped,"synthetic-stop-after-retention").path("action").asText()).isEqualTo("STOP");
        assertThat(t.runState()).isEqualTo("COMPLETED");assertThat(t.sessionState()).isEqualTo("STOPPED");
        assertThat(t.f.s.string("SELECT jsonb_build_object('generation',generation,'last_sequence',last_sequence,'stop',stop_request_ref)::text FROM wa.tracking_session WHERE id=?",t.session())).isEqualTo(before);
        assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_session WHERE last_latitude IS NOT NULL OR last_longitude IS NOT NULL")).isZero();
    }
    @Test void activeCollectionCannotLoseOldReceipts(){
        t.activate();var body=t.batch(t.sample(1,0,5));t.tracking.samples(t.actor,t.session(),body);
        t.h.time.advance(8L*86400);var result=port().tick(t.h.principal(WaPrincipal.Kind.SYSTEM_JOB),100,null);
        assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_sample_receipt")).isEqualTo(1);
        assertThat(t.f.s.number("SELECT count(*) FROM wa.tracking_batch_receipt")).isEqualTo(1);
        assertThat(result.deleted()).isZero();
    }
    @Test void realHumanIngressHoldingIdentityDefersThenPreservesTheFreshDraft() throws Exception {
        var h=t.h;UUID driver=t.f.driver;
        h.jdbc.update("DELETE FROM wa.conversation_state");
        h.jdbc.update("INSERT INTO wa.conversation_state(identity_id,context_json,updated_at) VALUES (?,?::jsonb,?)",driver,"{\"runDraft\":{\"name\":\"SINTETICO_RECENTE\"},\"conversationOwner\":\"RUN_SETUP\"}",Timestamp.from(h.clock.now()));
        h.event(driver,WaJson.object("{\"text\":\"Mensagem humana antiga\"}"));h.time.advance(8L*86400);t.f.renewExpiredTransportCredentials();
        var actor=h.principal(WaPrincipal.Kind.SYSTEM_JOB);var locked=new CountDownLatch(1);var release=new CountDownLatch(1);var pool=Executors.newSingleThreadExecutor();
        try{
            var incoming=pool.submit(()->h.transaction.execute(tx->{
                UUID event=t.f.s.pending(driver,WaJson.object("{\"conversation\":\"chegada humana sintética\"}"),h.clock.now());locked.countDown();await(release);return event;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var deferred=port().tick(actor,100,null);
            assertThat(deferred.deferred()).isEqualTo(1);assertThat(deferred.next().after()).isEmpty();
            release.countDown();UUID arrived=incoming.get(10,TimeUnit.SECONDS);
            assertThat(port().tick(actor,100,deferred.next()).complete()).isTrue();
            assertThat(t.f.s.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",driver)).contains("SINTETICO_RECENTE");
            assertThat(t.f.s.number("SELECT count(*) FROM wa.integration_inbox WHERE id=?",arrived)).isEqualTo(1);
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic ingress timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
