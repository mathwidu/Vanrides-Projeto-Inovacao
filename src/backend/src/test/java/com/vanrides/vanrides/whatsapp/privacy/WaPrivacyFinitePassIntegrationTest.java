package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyFinitePassIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=256m"))
        .withDatabaseName("wa_finite_privacy").withUsername("synthetic_privacy").withPassword("synthetic-finite-only");

    @Test void oldAsOfRemainsValidAcrossAllFourPortsWhileGrantsAreCurrent() {
        var f=new WaPrivacyTestSupport(POSTGRES,true);f.time.days(8);var actor=f.job(ENV,INSTANCE);Instant old=f.time.now.minusSeconds(601);
        var terminal=f.maintenance(false).tick(actor,1,new WaPrivacyTerminalMaintenance.Position(actor.credentialId(),ENV,INSTANCE,old,"",false,true));
        assertThat(terminal.erased()).isEqualTo(1);assertThat(terminal.next().asOf()).isEqualTo(old);
        var technical=new WaPrivacyTechnicalRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),false)
            .tick(actor,1,new WaPrivacyTechnicalRetention.Position(actor.credentialId(),ENV,INSTANCE,old,"",false,true));
        assertThat(technical.erased()).isEqualTo(1);assertThat(technical.next().asOf()).isEqualTo(old);
        var historical=new WaPrivacyHistoryRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),false)
            .tick(actor,1,new WaPrivacyHistoryRetention.Position(actor.credentialId(),ENV,INSTANCE,old,null,null,null,false,true));
        assertThat(historical.examined()).isZero();assertThat(historical.deleted()).isZero();
        var p=new WaPrivacyHistoryProjectionFixture(POSTGRES);Instant projectionOld=p.observed.minusSeconds(601);
        var projected=p.projection(false).tick(p.actor,1,new WaPrivacyHistoryProjection.Position(p.actor.credentialId(),
            WaPrivacyHistoryProjectionFixture.ENV,WaPrivacyHistoryProjectionFixture.INSTANCE,projectionOld,null,null,null,null,false,true));
        assertThat(projected.examined()).isEqualTo(1);assertThat(projected.next().asOf()).isEqualTo(projectionOld);
        assertThat(p.string("SELECT history_origin_id::text FROM wa.routine_occurrence WHERE id=?",p.occurrence)).isNotNull();
    }
    @Test void terminalSemanticPendingEndsFinitePassAndFreshPassReachesNewlyAgedMessage() {
        var f=new WaPrivacyTestSupport(POSTGRES,true);Instant initial=f.time.now;f.time.days(8);var actor=f.job(ENV,INSTANCE);
        UUID ambiguous=f.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=?",UUID.class,f.first);
        f.jdbc.update("UPDATE wa.message_outbox SET created_at=? WHERE id=?",Timestamp.from(initial.minusSeconds(1)),ambiguous);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','other-instance','PN','synthetic-other')",UUID.randomUUID(),f.first);
        UUID almostOld=UUID.randomUUID();Instant created=f.time.now.minusSeconds(7L*86400).plusSeconds(1);
        outbox(f,almostOld,f.second,created,"NEWLY_DUE_PRIVATE");
        String keep=f.string("SELECT to_jsonb(o)::text FROM wa.message_outbox o WHERE id=?",ambiguous);
        var first=f.maintenance(false).tick(actor,100,null);
        assertThat(first.erased()).isEqualTo(5);assertThat(first.deferred()).isEqualTo(1);assertThat(first.complete()).isFalse();assertThat(first.next()).isNull();
        assertThat(f.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",almostOld)).isEqualTo("NEWLY_DUE_PRIVATE");
        var exact=f.maintenance(false).tick(actor,1,null);assertThat(exact.examined()).isEqualTo(1);assertThat(exact.next()).isNotNull();
        Instant frozen=exact.next().asOf();f.time.now=f.time.now.plusSeconds(601);
        var end=f.maintenance(false).tick(actor,1,exact.next());
        assertThat(end.examined()).isZero();assertThat(end.complete()).isFalse();assertThat(end.next()).isNull();
        assertThat(exact.next().asOf()).isEqualTo(frozen);
        assertThat(f.string("SELECT payload_json->>'text' FROM wa.message_outbox WHERE id=?",almostOld)).isEqualTo("NEWLY_DUE_PRIVATE");
        var fresh=f.maintenance(false).tick(actor,100,null);
        assertThat(fresh.erased()).isEqualTo(1);assertThat(fresh.deferred()).isEqualTo(1);assertThat(fresh.next()).isNull();assertThat(fresh.complete()).isFalse();
        assertThat(f.string("SELECT payload_json::text FROM wa.message_outbox WHERE id=?",almostOld)).isEqualTo("{}");
        assertThat(f.string("SELECT to_jsonb(o)::text FROM wa.message_outbox o WHERE id=?",ambiguous)).isEqualTo(keep);
    }
    @Test void technicalSemanticHeadDoesNotStarveOtherContextsOrTheNextCutoff() {
        var t=new WaPrivacyTechnicalFixture(POSTGRES);var f=t.f;Instant initial=f.time.now;f.time.days(8);var actor=f.job(ENV,INSTANCE);
        UUID ambiguous=new UUID(0,0),event=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",ambiguous,f.cohort);
        f.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'PASSENGER','ACTIVE','SYNTHETIC_AMBIGUOUS')",ambiguous);
        for(String instance:new String[]{INSTANCE,"other-instance"})f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN','synthetic-ambiguous')",UUID.randomUUID(),ambiguous,instance);
        message(f,event,ambiguous,initial);
        f.jdbc.update("INSERT INTO wa.conversation_state(identity_id,stage,context_json,last_event_id,updated_at) VALUES (?,'PAUSED','{\"passengerDraft\":{\"secret\":\"KEEP_A\"}}',?,?)",ambiguous,event,f.timestamp());
        message(f,UUID.randomUUID(),f.second,f.time.now.minusSeconds(7L*86400).plusSeconds(1));
        f.jdbc.update("INSERT INTO wa.conversation_state(identity_id,context_json,updated_at) VALUES (?,'{\"passengerDraft\":{\"secret\":\"NEW_C\"}}',?)",f.second,f.timestamp());
        String keep=f.string("SELECT to_jsonb(c)::text FROM wa.conversation_state c WHERE identity_id=?",ambiguous);
        var first=t.port(false).tick(actor,100,null);
        assertThat(first.erased()).isEqualTo(2);assertThat(first.deferred()).isEqualTo(1);assertThat(first.pending().get(0).reason()).isEqualTo("MULTI_INSTANCE");
        assertThat(first.complete()).isFalse();assertThat(first.next()).isNull();
        assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.second)).contains("NEW_C");
        var exact=t.port(false).tick(actor,1,null);assertThat(exact.next()).isNotNull();Instant frozen=exact.next().asOf();
        f.time.now=f.time.now.plusSeconds(601);var end=t.port(false).tick(actor,1,exact.next());
        assertThat(end.examined()).isZero();assertThat(end.complete()).isFalse();assertThat(end.next()).isNull();assertThat(exact.next().asOf()).isEqualTo(frozen);
        assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.second)).contains("NEW_C");
        var fresh=t.port(false).tick(actor,100,null);assertThat(fresh.erased()).isEqualTo(1);assertThat(fresh.next()).isNull();assertThat(fresh.complete()).isFalse();
        assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.second)).isEqualTo("{}");
        assertThat(f.string("SELECT to_jsonb(c)::text FROM wa.conversation_state c WHERE identity_id=?",ambiguous)).isEqualTo(keep);
    }
    @Test void technicalLeaseUsesOperationalNowAndCanExpireWithoutMovingAsOf() {
        var t=new WaPrivacyTechnicalFixture(POSTGRES);var f=t.f;f.time.days(8);var actor=f.job(ENV,INSTANCE);UUID worker=worker(f,"CONVERSATION_WORKER");
        Timestamp expiry=Timestamp.from(f.time.now.plusSeconds(1));
        f.jdbc.update("UPDATE wa.conversation_state SET lease_credential_id=?,lease_token_hash=?,lease_generation=1,lease_expires_at=? WHERE identity_id=?",worker,"e".repeat(64),expiry,f.first);
        f.jdbc.update("UPDATE wa.integration_inbox SET state='CLAIMED',lease_credential_id=?,lease_token_hash=?,lease_generation=1,lease_expires_at=? WHERE id=?",worker,"f".repeat(64),expiry,f.inboxes.get(f.first));
        var busy=t.port(false).tick(actor,100,null);
        assertThat(busy.pending()).anyMatch(p->p.reason().equals("HUMAN_WORK_BUSY"));assertThat(busy.next()).isNotNull();Instant frozen=busy.next().asOf();
        f.time.now=f.time.now.plusSeconds(601);
        var resumed=t.port(false).tick(actor,100,busy.next());
        assertThat(resumed.complete()).isTrue();assertThat(resumed.next()).isNull();assertThat(resumed.erased()).isPositive();
        assertThat(f.string("SELECT context_json::text FROM wa.conversation_state WHERE identity_id=?",f.first)).isEqualTo("{}");
        assertThat(f.jdbc.queryForObject("SELECT lease_expires_at FROM wa.conversation_state WHERE identity_id=?",Timestamp.class,f.first)).isEqualTo(expiry);
        assertThat(busy.next().asOf()).isEqualTo(frozen);
    }
    @Test void technicalRowBusyKeepsPrefixThenProgressesWithTheSameOldCutoff() throws Exception {
        var t=new WaPrivacyTechnicalFixture(POSTGRES);var f=t.f;f.time.days(8);var actor=f.job(ENV,INSTANCE);
        UUID head=f.jdbc.queryForObject("SELECT identity_id FROM wa.conversation_state ORDER BY identity_id LIMIT 1",UUID.class);
        var pool=Executors.newSingleThreadExecutor();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{
            var blocker=pool.submit(()->new TransactionTemplate(f.transactions).execute(tx->{
                f.jdbc.queryForList("SELECT identity_id FROM wa.conversation_state WHERE identity_id=? FOR UPDATE",head);locked.countDown();
                try{if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Lock timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var busy=t.port(false).tick(actor,100,null);
            assertThat(busy.pending().get(0).reason()).isEqualTo("ROW_BUSY");assertThat(busy.next().after()).isEmpty();assertThat(busy.erased()).isZero();
            release.countDown();blocker.get(10,TimeUnit.SECONDS);f.time.now=f.time.now.plusSeconds(601);
            assertThat(t.port(false).tick(actor,100,busy.next()).complete()).isTrue();
        }finally{release.countDown();pool.shutdownNow();}
    }
    @Test void terminalLeaseAndSubmittingKeepTheirPrefixUntilEachPhysicalBlockClears() {
        var f=new WaPrivacyTestSupport(POSTGRES,true);Instant initial=f.time.now;f.time.days(8);var actor=f.job(ENV,INSTANCE);UUID worker=worker(f,"CONVERSATION_WORKER");
        Timestamp expiry=Timestamp.from(f.time.now.plusSeconds(1));
        f.jdbc.update("UPDATE wa.integration_inbox SET created_at=?,state='CLAIMED',lease_credential_id=?,lease_token_hash=?,lease_generation=1,lease_expires_at=? WHERE id=?",Timestamp.from(initial.minusSeconds(1)),worker,"e".repeat(64),expiry,f.inboxes.get(f.first));
        UUID box=f.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=?",UUID.class,f.first),attempt=UUID.randomUUID();
        f.jdbc.update("UPDATE wa.message_outbox SET created_at=? WHERE id=?",Timestamp.from(initial.minusSeconds(1)),box);
        f.jdbc.update("INSERT INTO wa.message_attempt(id,outbox_id,attempt_number,lease_generation,submit_token_hash,state,instance_id) VALUES (?,?,1,1,?,'SUBMITTING',?)",attempt,box,"d".repeat(64),INSTANCE);
        var lease=f.maintenance(false).tick(actor,100,null);assertThat(lease.erased()).isZero();assertThat(lease.next().after()).isEmpty();
        Instant frozen=lease.next().asOf();f.time.now=f.time.now.plusSeconds(601);
        var submitting=f.maintenance(false).tick(actor,100,lease.next());
        assertThat(submitting.erased()).isEqualTo(3);assertThat(submitting.deferred()).isEqualTo(1);assertThat(submitting.next().after()).startsWith("I:");assertThat(submitting.next().asOf()).isEqualTo(frozen);
        var held=f.maintenance(false).tick(actor,100,submitting.next());assertThat(held.erased()).isZero();assertThat(held.next().after()).isEqualTo(submitting.next().after());
        f.jdbc.update("UPDATE wa.message_attempt SET state='UNKNOWN',completed_at=? WHERE id=?",f.timestamp(),attempt);
        var done=f.maintenance(false).tick(actor,100,held.next());assertThat(done.complete()).isTrue();assertThat(done.next()).isNull();assertThat(done.erased()).isEqualTo(3);
        assertThat(f.count("message_attempt")).isEqualTo(1);
    }
    private static UUID worker(WaPrivacyTestSupport f,String principal){
        UUID id=UUID.randomUUID();f.jdbc.update("INSERT INTO wa.service_credential(id,principal,environment,allowed_instance_id,token_hash,created_at,expires_at) VALUES (?,?,?,?,?,?,?)",
            id,principal,ENV,INSTANCE,WaCrypto.digest(id.toString()),f.timestamp(),Timestamp.from(f.time.now.plusSeconds(3600)));return id;
    }
    private static void message(WaPrivacyTestSupport f,UUID id,UUID person,Instant created){
        f.jdbc.update("INSERT INTO wa.integration_inbox(id,identity_id,provider,instance_id,message_id,category,state,payload_json,created_at) VALUES (?,?,'EVOLUTION',?,?,'MESSAGE','COMPLETED','{}',?)",id,person,INSTANCE,id.toString(),Timestamp.from(created));
    }
    private static void outbox(WaPrivacyTestSupport f,UUID id,UUID person,Instant created,String text){
        f.jdbc.update("INSERT INTO wa.message_outbox(id,recipient_identity_id,purpose,business_key,policy_version,payload_json,state,created_at,not_before,expires_at) VALUES (?,?,'OPERATIONAL',?,'finite-pass',jsonb_build_object('text',?::text),'DELIVERED',?,?,?)",
            id,person,id.toString(),text,Timestamp.from(created),Timestamp.from(created),Timestamp.from(created.plusSeconds(60)));
    }
}
