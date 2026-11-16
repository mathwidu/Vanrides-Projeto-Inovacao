package com.vanrides.vanrides.whatsapp.team;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.team.TeamTestDriverTestSupport.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import com.vanrides.vanrides.whatsapp.privacy.*;
import com.vanrides.vanrides.whatsapp.transport.WaTransportTestDatabase;
import java.sql.Timestamp;
import java.time.Clock;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyTechnicalSupportIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy_support").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    @Test void realResumeHoldingProfileAndWaitingIdentityCannotDeadlockMaintenance() throws Exception {
        var s=new TeamTestDriverTestSupport(new WaTransportTestDatabase(POSTGRES).nextDataSource());
        UUID owner=s.normalDriver(),support=UUID.randomUUID(),event=UUID.randomUUID(),credential=UUID.randomUUID();
        // Team admission requires its administrative non-synthetic cohort flag; all fixture data are synthetic.
        String address="12025550101@s.whatsapp.net";
        s.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN',?)",UUID.randomUUID(),owner,INSTANCE,address);
        s.jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,created_at) VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','COMPLETED',?)",event,owner,INSTANCE,event.toString(),Timestamp.from(NOW.minusSeconds(8L*86400)));
        s.jdbc.update("INSERT INTO wa.support_case(id,identity_id,deduplication_key,reason_code,correlation_id) VALUES (?,?,?,'HUMAN','synthetic-retention')",support,owner,support.toString());
        s.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,last_event_id,context_json) VALUES (?,'PAUSED',?,?::jsonb)",owner,event,"{\"supportCaseId\":\""+support+"\",\"driverDraft\":{\"name\":\"SINTETICO_PAUSADO\",\"confirmation\":{}}}");
        s.jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,token_hash,allowed_instance_id,expires_at,created_at) VALUES (?,'SYSTEM_JOB',?,?,?,?,?)",credential,ENV,WaCrypto.digest(credential.toString()),INSTANCE,Timestamp.from(NOW.plusSeconds(3600)),Timestamp.from(NOW));
        var actor=new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB,credential,null,null,INSTANCE,null,0,false);
        var manager=new DataSourceTransactionManager(s.jdbc.getDataSource());var tx=new TransactionTemplate(manager);
        var identityLocked=new CountDownLatch(1);var continueMaintenance=new CountDownLatch(1);var profileLocked=new CountDownLatch(1);
        JdbcTemplate probe=new JdbcTemplate(s.jdbc.getDataSource()){
            @Override public List<Map<String,Object>> queryForList(String sql,Object...args){
                var result=super.queryForList(sql,args);
                if(sql.startsWith("SELECT id FROM wa.identity WHERE") && !result.isEmpty()){identityLocked.countDown();await(continueMaintenance);}
                return result;
            }
        };
        var port=new WaPrivacyTechnicalRetention(probe,manager,new WaClock(Clock.fixed(NOW,ZoneOffset.UTC)),new WaPrivacyPolicy(true,false),false);
        var pool=Executors.newFixedThreadPool(2);
        try{
            var retention=pool.submit(()->port.tick(actor,100,null));assertThat(identityLocked.await(10,TimeUnit.SECONDS)).isTrue();
            var resume=pool.submit(()->tx.execute(status->{
                s.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",owner);profileLocked.countDown();
                return s.admissions.resumeConversation(GITHUB,new WaTeamEvolution.Contact(address,address,null),support,UUID.randomUUID(),LIVE);
            }));
            assertThat(profileLocked.await(10,TimeUnit.SECONDS)).isTrue();continueMaintenance.countDown();
            var deferred=retention.get(10,TimeUnit.SECONDS);assertThat(deferred.deferred()).isEqualTo(1);assertThat(deferred.next().after()).isEmpty();
            assertThat(resume.get(10,TimeUnit.SECONDS).path("conversationPaused").asBoolean()).isFalse();
            assertThat(s.jdbc.queryForObject("SELECT state FROM wa.support_case WHERE id=?",String.class,support)).isEqualTo("CLOSED");
            assertThat(s.jdbc.queryForObject("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",String.class,owner)).contains("SINTETICO_PAUSADO").doesNotContain("confirmation");
        }finally{continueMaintenance.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    private static void await(CountDownLatch latch){try{if(!latch.await(10,TimeUnit.SECONDS))throw new AssertionError("Synthetic resume timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}}
}
