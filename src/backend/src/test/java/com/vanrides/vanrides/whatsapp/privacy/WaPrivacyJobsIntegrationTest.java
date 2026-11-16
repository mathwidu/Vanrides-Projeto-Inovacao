package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.transport.WaTransportAccess;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.env.MockEnvironment;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledOnOs({OS.LINUX,OS.MAC})
@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyJobsIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy_jobs").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    @TempDir Path temporary;
    private WaPrivacyTechnicalFixture t;
    private WaPrivacyTestSupport f;
    private Path journalDirectory;
    private int closureAlias;
    @BeforeEach void setup(){t=new WaPrivacyTechnicalFixture(POSTGRES);f=t.f;}
    private WaPrivacySettings config(boolean enabled,boolean apply,Path path){
        return new WaPrivacySettings(new MockEnvironment().withProperty("WA_PRIVACY_ENABLED",Boolean.toString(enabled))
            .withProperty("WA_PRIVACY_APPLY",Boolean.toString(apply)).withProperty("WA_PRIVACY_JOURNAL_DIR",path==null?"":path.toString()));
    }
    private WaPrivacyJobs jobs(WaPrivacySettings config,WaPrivacyStartupGate gate){return new WaPrivacyJobs(f.jdbc,f.transactions,f.clock,t.settings,config,gate,new WaTransportAccess(f.jdbc,t.settings,f.clock));}
    private WaPrivacyJobs preview(){var config=config(true,false,null);var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();return jobs(config,gate);}
    private WaPrincipal actor(){return f.job(ENV,INSTANCE);}
    private static ObjectNode body(String family,int limit){return WaJson.object("{}").put("family",family).put("batchLimit",limit);}
    private WaPrivacyFileJournal journal() throws Exception {
        journalDirectory=Files.createDirectory(temporary.resolve("journal-"+UUID.randomUUID()),PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        var journal=new WaPrivacyFileJournal(journalDirectory);journal.initializeEmpty();return journal;
    }
    @Test void defaultOffNeedsNeitherPrivacySchemaNorFilesystem(){
        f.jdbc.execute("DROP TABLE wa.privacy_erasure_ledger,wa.privacy_subject CASCADE");
        var config=config(false,false,temporary.resolve("missing"));var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();var jobs=jobs(config,gate);var actor=actor();
        for(var family:WaPrivacyJobs.Family.values())assertThat(jobs.tick(actor,body(family.name(),20)).path("status").asText()).isEqualTo("DISABLED");
        assertThat(temporary.resolve("missing")).doesNotExist();
    }
    @Test void dryRunAcrossAllFiveFamiliesNeverErasesAppendsOrClosesAndResponseContainsOnlyCounters(){
        f.service().close(SCOPE,f.first);f.time.days(31);var actor=actor();var jobs=preview();String before=fingerprint();
        for(var family:WaPrivacyJobs.Family.values()){
            var result=jobs.tick(actor,body(family.name(),20));assertThat(result.path("status").asText()).isEqualTo("DRY_RUN");assertThat(result.size()).isEqualTo(11);
            assertThat(result.path("erased").asInt()+result.path("deleted").asInt()).isZero();
            assertThat(result.toString()).doesNotContain(NAME,ALIAS,f.first.toString(),f.driver.toString());
            if(family==WaPrivacyJobs.Family.PROFILE_30D){assertThat(result.path("wouldChange").asInt()).isEqualTo(1);assertThat(result.path("complete").asBoolean()).isFalse();assertThat(result.path("nextCursor").isNull()).isTrue();}
        }
        assertThat(fingerprint()).isEqualTo(before);assertThat(f.journal.records).hasSize(1);assertThat(Files.exists(temporary.resolve("head.json"))).isFalse();
    }
    @Test void roleAndClosedBodyAreCheckedBeforeAnyPort(){
        var jobs=preview();var actor=actor();
        for(var request:new ObjectNode[]{body("GLOBAL_DELETE",20),body("TECHNICAL_7D",0),body("TECHNICAL_7D",101),
            body("TECHNICAL_7D",20).put("batchLimit",20.0),body("TECHNICAL_7D",20).set("position",WaJson.object("{}")),body("TECHNICAL_7D",20).putNull("cursor")})
            assertThatThrownBy(()->jobs.tick(actor,request)).isInstanceOf(WaProblem.class);
        var channel=new WaPrincipal(WaPrincipal.Kind.CHANNEL,actor.credentialId(),null,null,INSTANCE,null,0,false);
        assertThatThrownBy(()->jobs.tick(channel,body("TECHNICAL_7D",20))).isInstanceOf(WaProblem.class);
    }
    @Test void credentialEnvironmentInstanceExpiryAndAmbientTransactionAreFenced(){
        var jobs=preview();
        for(var actor:new WaPrincipal[]{f.job("other-environment",INSTANCE),new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB,actor().credentialId(),null,null,"other-instance",null,0,false)})
            assertThatThrownBy(()->jobs.tick(actor,body("TECHNICAL_7D",20))).isInstanceOf(WaProblem.class);
        var expired=actor();f.jdbc.update("UPDATE wa.service_credential SET expires_at=created_at+INTERVAL '1 second' WHERE id=?",expired.credentialId());f.time.now=f.time.now.plusSeconds(2);
        assertThatThrownBy(()->jobs.tick(expired,body("TECHNICAL_7D",20))).isInstanceOf(WaProblem.class);
        var current=actor();assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(tx->jobs.tick(current,body("TECHNICAL_7D",20))))
            .isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
    }
    @Test void opaqueCursorCannotChangeFamilyCredentialInstanceEnvironmentOrExpiry(){
        f.time.days(8);var jobs=preview();var actor=actor();String token=jobs.tick(actor,body("TERMINAL_7D",1)).path("nextCursor").asText();assertThat(token).startsWith("privacycursor_");
        int middle=token.length()/2;String changed=token.substring(0,middle)+(token.charAt(middle)=='A'?'B':'A')+token.substring(middle+1);
        assertThatThrownBy(()->jobs.tick(actor,body("TERMINAL_7D",1).put("cursor",changed))).isInstanceOf(WaProblem.class);
        assertThatThrownBy(()->jobs.tick(actor,body("TECHNICAL_7D",1).put("cursor",token))).isInstanceOf(WaProblem.class);
        for(var other:new WaPrincipal[]{actor(),f.job(ENV,"other-instance"),f.job("other-environment",INSTANCE)})
            assertThatThrownBy(()->jobs.tick(other,body("TERMINAL_7D",1).put("cursor",token))).isInstanceOf(WaProblem.class);
        f.time.now=f.time.now.plusSeconds(299);assertThat(jobs.tick(actor,body("TERMINAL_7D",1).put("cursor",token)).path("status").asText()).isEqualTo("DRY_RUN");
        f.time.now=f.time.now.plusSeconds(1);assertThatThrownBy(()->jobs.tick(actor,body("TERMINAL_7D",1).put("cursor",token))).isInstanceOf(WaProblem.class);
    }
    @Test void frozenServerPositionRejectsWrongEnvironmentAndUnknownFieldsEvenInsideAValidEnvelope(){
        var jobs=preview();var actor=actor();var cursors=new WaPrivacyCursor(t.crypto,f.clock);
        String projection=cursors.issue(actor,"HISTORY_PROJECTION_90D",WaJson.object("{}"));assertThat(cursors.read(actor,projection).family()).isEqualTo("HISTORY_PROJECTION_90D");
        var position=WaJson.object("{}").put("credentialId",actor.credentialId().toString()).put("environment","other-environment").put("instanceId",INSTANCE)
            .put("asOf",f.time.now.toString()).put("after","").put("dryRun",true).put("syntheticOnly",true);
        String wrong=cursors.issue(actor,"TERMINAL_7D",position);assertThatThrownBy(()->jobs.tick(actor,body("TERMINAL_7D",1).put("cursor",wrong))).isInstanceOf(WaProblem.class);
        position.put("environment",ENV).put("inventedField",1);String extra=cursors.issue(actor,"TERMINAL_7D",position);
        assertThatThrownBy(()->jobs.tick(actor,body("TERMINAL_7D",1).put("cursor",extra))).isInstanceOf(WaProblem.class);
    }
    @Test void profilePreviewCapsLogicalSubjectsAtTwentyFiveWithoutCallingErase(){
        seedClosures(33,f.journal);f.time.days(31);var actor=actor();var jobs=preview();var result=jobs.tick(actor,body("PROFILE_30D",100));
        assertThat(result.path("examined").asInt()).isEqualTo(25);assertThat(result.path("wouldChange").asInt()).isEqualTo(25);assertThat(result.path("erased").asInt()).isZero();
        assertThat(result.path("nextCursor").isTextual()).isTrue();
        var tail=jobs.tick(actor,body("PROFILE_30D",100).put("cursor",result.path("nextCursor").asText()));
        assertThat(tail.path("examined").asInt()).isEqualTo(8);assertThat(tail.path("wouldChange").asInt()).isEqualTo(8);
        assertThat(tail.path("nextCursor").isNull()).isTrue();assertThat(tail.path("complete").asBoolean()).isFalse();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.privacy_subject WHERE erased_at IS NOT NULL",Long.class)).isZero();
    }
    @Test void nonSyntheticClosuresBeforeTheLimitCannotStarveTheLaterSyntheticSubject() throws Exception {
        var journal=journal();UUID administrative=UUID.randomUUID();
        // Administrative cohort flag is non-synthetic; every person and payload in this fixture is synthetic.
        f.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,?,false)",administrative,ENV);
        for(int index=0;index<20;index++)closedPassenger(administrative,"DADO_SINTETICO_ADMIN_"+index,journal);
        f.time.now=f.time.now.plusSeconds(1);assertThat(f.service(WaPrivacyPolicy.syntheticTests(),journal).close(SCOPE,f.first).status()).isEqualTo("CLOSED");
        f.time.days(31);f.sweepTerminal();var config=config(true,true,journalDirectory);var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();
        var result=jobs(config,gate).tick(actor(),body("PROFILE_30D",20));
        assertThat(result.path("examined").asInt()).isEqualTo(1);assertThat(result.path("erased").asInt()).isEqualTo(1);
        assertThat(result.path("deferred").asInt()).isZero();assertThat(result.path("complete").asBoolean()).isTrue();assertThat(result.path("nextCursor").isNull()).isTrue();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id WHERE i.test_cohort_id=? AND p.display_name IS NOT NULL AND p.privacy_erased_at IS NULL",Long.class,administrative)).isEqualTo(20);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.privacy_erasure_ledger l JOIN wa.identity i ON i.id=l.subject_id WHERE i.test_cohort_id=? AND l.state='JOURNALED'",Long.class,administrative)).isEqualTo(20);
    }
    @Test void deferredPrefixDoesNotStarveLaterSubjectsOrFalselyCompleteAndAgingCutoffSurvivesRenewedEnvelopes() throws Exception {
        var journal=journal();assertThat(f.service(WaPrivacyPolicy.syntheticTests(),journal).close(SCOPE,f.first).status()).isEqualTo("CLOSED");
        f.time.now=f.time.now.plusSeconds(1);UUID second=closedPassenger(f.cohort,"DADO_SINTETICO_SEGUINTE",journal);
        f.time.now=f.time.now.plusSeconds(1);UUID third=closedPassenger(f.cohort,"DADO_SINTETICO_ULTIMO",journal);
        f.time.days(31);f.sweepTerminal();var config=config(true,true,journalDirectory);var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();
        var jobs=jobs(config,gate);var actor=actor();var locked=new java.util.concurrent.CountDownLatch(1);var release=new java.util.concurrent.CountDownLatch(1);
        var pool=java.util.concurrent.Executors.newSingleThreadExecutor();
        try{
            var writer=pool.submit(()->new TransactionTemplate(f.transactions).execute(tx->{
                f.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",f.driver);locked.countDown();
                try{if(!release.await(15,TimeUnit.SECONDS))throw new AssertionError("Synthetic copy writer timeout");}
                catch(InterruptedException interrupted){Thread.currentThread().interrupt();throw new AssertionError(interrupted);}return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var first=jobs.tick(actor,body("PROFILE_30D",1));assertThat(first.path("deferred").asInt()).isEqualTo(1);
            assertThat(first.path("reasonCodes").toString()).contains("COPY_WRITER_BUSY");assertThat(first.path("nextCursor").isTextual()).isTrue();
            var cursors=new WaPrivacyCursor(t.crypto,f.clock);var position=cursors.read(actor,first.path("nextCursor").asText()).position();
            String partial=cursors.issue(actor,"PROFILE_30D",position.deepCopy().putNull("afterSubjectId"));
            assertThatThrownBy(()->jobs.tick(actor,body("PROFILE_30D",1).put("cursor",partial))).isInstanceOf(WaProblem.class);
            String future=cursors.issue(actor,"PROFILE_30D",position.deepCopy().put("asOf",f.time.now.plusSeconds(1).toString()));
            assertThatThrownBy(()->jobs.tick(actor,body("PROFILE_30D",1).put("cursor",future))).isInstanceOf(WaProblem.class);
            f.time.now=f.time.now.plusSeconds(240);
            var middle=jobs.tick(actor,body("PROFILE_30D",1).put("cursor",first.path("nextCursor").asText()));
            assertThat(middle.path("erased").asInt()).isEqualTo(1);assertThat(middle.path("nextCursor").isTextual()).isTrue();
            f.time.now=f.time.now.plusSeconds(240); // asOf is now 480s old; each encrypted envelope is only 240s old.
            var end=jobs.tick(actor,body("PROFILE_30D",1).put("cursor",middle.path("nextCursor").asText()));
            assertThat(end.path("erased").asInt()).isEqualTo(1);assertThat(end.path("complete").asBoolean()).isFalse();assertThat(end.path("nextCursor").isNull()).isTrue();
            assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",second)).isNull();assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",third)).isNull();
            assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
            release.countDown();writer.get(10,TimeUnit.SECONDS);
            var retry=jobs.tick(actor,body("PROFILE_30D",1));assertThat(retry.path("erased").asInt()).isEqualTo(1);assertThat(retry.path("complete").asBoolean()).isTrue();
        }finally{release.countDown();pool.shutdownNow();assertThat(pool.awaitTermination(10,TimeUnit.SECONDS)).isTrue();}
    }
    @Test void applyUsesDurableJournalAndAuthorityForTwentySubjectsAndDoesNotClaimGlobalCompleteness() throws Exception {
        var journal=journal();var config=config(true,true,journalDirectory);
        seedClosures(20,journal);f.time.days(31);f.sweepTerminal();var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();assertThat(gate.status()).isEqualTo("READY");
        var jobs=jobs(config,gate);var actor=actor();long start=System.nanoTime();var result=jobs.tick(actor,body("PROFILE_30D",20));long millis=TimeUnit.NANOSECONDS.toMillis(System.nanoTime()-start);
        org.slf4j.LoggerFactory.getLogger(getClass()).info("PRIVACY_RUNTIME_LATENCY family=PROFILE_30D mode=APPLY examined={} durationMs={}",result.path("examined").asInt(),millis);
        assertThat(result.path("examined").asInt()).isEqualTo(20);assertThat(result.path("erased").asInt()).isEqualTo(20);assertThat(result.path("complete").asBoolean()).isTrue();
        assertThat(result.toString()).doesNotContain(NAME,ALIAS,f.first.toString());assertThat(journal.snapshot().records()).hasSize(20);
        assertThat(jobs.tick(actor,body("PROFILE_30D",20)).path("examined").asInt()).isZero();gate.verify();assertThat(gate.status()).isEqualTo("READY");
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.second)).isEqualTo("PESSOA_BETA_INTACTA");
    }
    @Test void revocationBetweenIntentAndJournalAckBlocksBeforeErasure(){
        f.service(WaPrivacyPolicy.syntheticTests(),WaPrivacyJournal.unavailable()).close(SCOPE,f.first);f.time.days(31);f.sweepTerminal();var actor=actor();
        WaPrivacyJournal revoke=new WaPrivacyJournal(){
            public Ack append(Entry entry){var ack=f.journal.append(entry);f.jdbc.update("UPDATE wa.service_credential SET revoked_at=? WHERE id=?",f.timestamp(),actor.credentialId());return ack;}
            public boolean verify(Entry entry,Ack ack){return f.journal.verify(entry,ack);}
        };
        var config=config(true,true,temporary);var gate=mock(WaPrivacyStartupGate.class);when(gate.journal()).thenReturn(revoke);
        assertThatThrownBy(()->jobs(config,gate).tick(actor,body("PROFILE_30D",20))).isInstanceOf(WaProblem.class);
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("INTENT");assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.privacy_erasure_ledger WHERE purge_started_at IS NOT NULL",Long.class)).isZero();
    }
    @Test void startupDistinguishesPendingIntentFromDatabaseAckMissingInJournal() throws Exception {
        f.service(WaPrivacyPolicy.syntheticTests(),WaPrivacyJournal.unavailable()).close(SCOPE,f.first);var journal=journal();
        var config=config(true,true,journalDirectory);var gate=new WaPrivacyStartupGate(f.jdbc,config,t.settings);gate.verify();assertThat(gate.status()).isEqualTo("INTENT_PENDING");
        assertThat(journal.snapshot().records()).isEmpty();assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
        f.service().close(SCOPE,f.first);assertThatThrownBy(gate::verify).isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_STARTUP_BLOCKED");
    }
    private void seedClosures(int total,WaPrivacyJournal journal){
        var service=f.service(WaPrivacyPolicy.syntheticTests(),journal);assertThat(service.close(SCOPE,f.first).status()).isEqualTo("CLOSED");
        for(int index=1;index<total;index++){
            UUID id=UUID.randomUUID();f.jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",id,f.cohort);
            f.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'PASSENGER','ACTIVE',?)",id,"SUJEITO_SINTETICO_"+index);
            f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN',?)",UUID.randomUUID(),id,INSTANCE,"9000"+index+"@s.whatsapp.net");
            assertThat(service.close(SCOPE,id).status()).isEqualTo("CLOSED");
        }
    }
    private UUID closedPassenger(UUID cohort,String name,WaPrivacyJournal journal){
        UUID id=UUID.randomUUID();f.jdbc.update("INSERT INTO wa.identity(id,test_cohort_id) VALUES (?,?)",id,cohort);
        f.jdbc.update("INSERT INTO wa.profile(identity_id,role,status,display_name) VALUES (?,'PASSENGER','ACTIVE',?)",id,name);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',?,'PN',?)",UUID.randomUUID(),id,INSTANCE,"8000"+(++closureAlias)+"@s.whatsapp.net");
        assertThat(f.service(new WaPrivacyPolicy(true,false),journal).close(SCOPE,id).status()).isEqualTo("CLOSED");return id;
    }
    private String fingerprint(){return f.string("""
        SELECT jsonb_build_object('profile',(SELECT jsonb_agg(to_jsonb(p) ORDER BY p.identity_id) FROM wa.profile p),
          'context',(SELECT jsonb_agg(to_jsonb(c) ORDER BY c.identity_id) FROM wa.conversation_state c),
          'ledger',(SELECT jsonb_agg(to_jsonb(l) ORDER BY l.sequence) FROM wa.privacy_erasure_ledger l),
          'run',(SELECT snapshot_json FROM wa.operation_run WHERE id=?))::text
        """,f.run);}
}
