package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyHistoryProjectionFixture.*;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyHistoryProjectionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=256m"))
        .withDatabaseName("wa_history_projection").withUsername("synthetic_history").withPassword("synthetic-history-only");
    private WaPrivacyHistoryProjectionFixture f;
    @BeforeEach void setup(){f=new WaPrivacyHistoryProjectionFixture(POSTGRES);}
    @Test void projectsActiveProfilesHistoryButPreservesReferencesDigestAndEveryOperationalControl() {
        String profiles=rows("profile"),points=rows("pickup_point"),controls=rows("start_intent")+rows("tracking_session");
        String run=f.string("SELECT (to_jsonb(r)-ARRAY['snapshot_json','completion_reason','history_erased_at'])::text FROM wa.operation_run r WHERE id=?",f.run);
        String stops=f.string("SELECT jsonb_agg(to_jsonb(s)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','attendance_snapshot','state','canceled_at','history_erased_at'] ORDER BY id)::text FROM wa.run_stop s");
        var pin=f.projection(false).tick(f.actor,1,null);assertThat(pin.erased()).isZero();assertThat(pin.complete()).isFalse();
        assertThat(f.string("SELECT history_origin_kind FROM wa.routine_occurrence WHERE id=?",f.occurrence)).isNotBlank();
        f.finish();
        assertThat(f.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",f.run)).isEqualTo("{\"rosterDigest\": \""+DIGEST+"\"}");
        assertThat(f.string("SELECT completion_reason FROM wa.operation_run WHERE id=?",f.run)).isNull();
        assertThat(f.string("SELECT (to_jsonb(r)-ARRAY['snapshot_json','completion_reason','history_erased_at'])::text FROM wa.operation_run r WHERE id=?",f.run)).isEqualTo(run);
        assertThat(f.string("SELECT jsonb_agg(to_jsonb(s)-ARRAY['latitude_snapshot','longitude_snapshot','reference_snapshot','attendance_snapshot','state','canceled_at','history_erased_at'] ORDER BY id)::text FROM wa.run_stop s")).isEqualTo(stops);
        assertThat(rows("profile")).isEqualTo(profiles);assertThat(rows("pickup_point")).isEqualTo(points);
        assertThat(rows("start_intent")+rows("tracking_session")).isEqualTo(controls);
        assertThat(f.count("run_stop")).isEqualTo(2);assertThat(f.count("operation_run")).isEqualTo(1);
        assertThat(f.count("approach_progress")).isZero();assertThat(f.count("run_finish_progress")).isZero();
        assertThat(f.string("SELECT schedule_snapshot::text FROM wa.routine_occurrence WHERE id=?",f.occurrence)).isEqualTo("{}");
        assertThat(f.projection(false).tick(f.actor,100,null).complete()).isTrue();
        assertThat(f.projection(false).tick(f.actor,100,null).erased()).isZero();
    }
    @Test void dryRunOffPolicyAmbientWrongScopeAndCursorCannotMutate() {
        String before=rows("operation_run")+rows("run_stop")+rows("routine_occurrence");
        var dry=new WaPrivacyHistoryProjection(f.jdbc,f.manager,f.clock,WaPrivacyPolicy.syntheticTests(),f::authority);
        var preview=dry.tick(f.actor,100,null);assertThat(preview.wouldChange()).isEqualTo(1);assertThat(preview.erased()).isZero();
        assertThat(new WaPrivacyHistoryProjection(f.jdbc,f.manager,f.clock,WaPrivacyPolicy.disabled(),f::authority,false).tick(f.actor,100,null).status()).isEqualTo("DISABLED");
        assertThatThrownBy(()->new TransactionTemplate(f.manager).execute(tx->f.projection(false).tick(f.actor,100,null)))
            .isInstanceOf(IllegalStateException.class);
        var forged=new WaPrivacyHistoryProjection.Position(f.actor.credentialId(),ENV,INSTANCE,f.observed,WaPrivacyHistoryProjection.Family.OCCURRENCE,
            new UUID(-1,-1),new UUID(-1,-1),new UUID(-1,-1),false,true);
        var result=f.projection(false).tick(f.actor,100,forged);assertThat(result.examined()).isZero();assertThat(result.complete()).isFalse();assertThat(result.next().afterFamily()).isNull();
        assertThat(rows("operation_run")+rows("run_stop")+rows("routine_occurrence")).isEqualTo(before);
        var other=new com.vanrides.vanrides.whatsapp.auth.WaPrincipal(f.actor.kind(),f.actor.credentialId(),null,null,"foreign",null,0,false);
        assertThatThrownBy(()->f.projection(false).tick(other,100,null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->f.projection(false).tick(f.actor,101,null)).isInstanceOf(RuntimeException.class);
    }
    @Test void boundaryAndFutureClockCannotAgeARecentFact() {
        f.jdbc.update("UPDATE wa.operation_run SET completed_at=? WHERE id=?",ts(f.observed.minusSeconds(90L*86400).plusSeconds(1)),f.run);
        f.time.now=f.observed.plusSeconds(365L*86400);
        assertThat(f.projection(false).tick(f.actor,100,null).examined()).isZero();
        assertThat(f.string("SELECT history_erased_at::text FROM wa.operation_run WHERE id=?",f.run)).isNull();
        f.time.now=f.observed;
        f.jdbc.update("UPDATE wa.operation_run SET completed_at=? WHERE id=?",ts(f.observed.minusSeconds(90L*86400)),f.run);
        f.finish();assertThat(f.string("SELECT history_erased_at::text FROM wa.operation_run WHERE id=?",f.run)).isNotNull();
        assertThat(f.jdbc.queryForObject("SELECT history_erased_at<=transaction_timestamp() FROM wa.operation_run WHERE id=?",Boolean.class,f.run)).isTrue();
    }
    @Test void futureOccurrenceNeverExpiresFromItsOldCreationDateAndUnknownTerminalDateDefers() {
        f.jdbc.update("UPDATE wa.routine_occurrence SET scheduled_at=? WHERE id=?",ts(f.observed.plusSeconds(86400)),f.occurrence);
        assertThat(f.projection(false).tick(f.actor,100,null).examined()).isZero();
        f.jdbc.update("UPDATE wa.routine_occurrence SET scheduled_at=? WHERE id=?",ts(f.past),f.occurrence);
        f.jdbc.update("UPDATE wa.operation_run SET completed_at=NULL WHERE id=?",f.run);
        var result=f.projection(false).tick(f.actor,100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("MISSING_TERMINAL_AT");
        assertThat(result.next().afterFamily()).isNull();assertThat(result.complete()).isFalse();
    }
    @Test void mutableRoutineSourceCannotReplaceMissingHistoricalOriginAndAllSourcesMustAgree() {
        f=new WaPrivacyHistoryProjectionFixture(POSTGRES,false);
        assertThat(f.projection(false).tick(f.actor,100,null).pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("ORIGIN_MISSING");
        f=new WaPrivacyHistoryProjectionFixture(POSTGRES);
        f.jdbc.update("""
            INSERT INTO wa.attendance_question SELECT (jsonb_populate_record(NULL::wa.attendance_question,
              to_jsonb(q)||jsonb_build_object('id',?::uuid,'environment','foreign'))).* FROM wa.attendance_question q LIMIT 1
            """,UUID.randomUUID());
        assertThat(f.projection(false).tick(f.actor,100,null).pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("ORIGIN_CONFLICT");
        assertThat(f.string("SELECT history_origin_id::text FROM wa.routine_occurrence WHERE id=?",f.occurrence)).isNull();
    }
    @Test void aLiveCapabilityAndForeignPassengerPreventProjection() {
        f.jdbc.update("UPDATE wa.start_intent SET state='PENDING',expires_at=? WHERE id=?",ts(f.observed.plusSeconds(60)),f.intent);
        assertThat(f.projection(false).tick(f.actor,100,null).pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("ACTIVE_REFERENCE");
        f.jdbc.update("UPDATE wa.start_intent SET state='REVOKED' WHERE id=?",f.intent);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','foreign','PN','synthetic-foreign')",UUID.randomUUID(),f.second);
        assertThat(f.projection(false).tick(f.actor,100,null).pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("SUBJECT_OUT_OF_SCOPE");
        assertThat(f.string("SELECT history_erased_at::text FROM wa.operation_run WHERE id=?",f.run)).isNull();
        f.jdbc.update("DELETE FROM wa.identity_alias WHERE instance_id='foreign'");
        // Counterfactual legacy/corrupt row: production schema stays NOT NULL; restore this test-only relaxation below.
        f.jdbc.execute("ALTER TABLE wa.identity_alias ALTER COLUMN instance_id DROP NOT NULL");
        try{
            f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION',NULL,'PN','synthetic-unknown')",UUID.randomUUID(),f.second);
            assertThat(f.projection(false).tick(f.actor,100,null).pending()).extracting(WaPrivacyHistoryProjection.Deferral::reason).containsExactly("SUBJECT_OUT_OF_SCOPE");
            assertThat(f.string("SELECT history_origin_id::text FROM wa.routine_occurrence WHERE id=?",f.occurrence)).isNull();
        }finally{f.jdbc.update("DELETE FROM wa.identity_alias WHERE instance_id IS NULL");f.jdbc.execute("ALTER TABLE wa.identity_alias ALTER COLUMN instance_id SET NOT NULL");}
    }
    @Test void noRunPlannedOccurrenceKeepsItsStateAndFutureMaterializationPinsSystemJob() {
        UUID old=UUID.randomUUID(),future=UUID.randomUUID();
        for(UUID id:List.of(old,future)){
            Instant scheduled=id.equals(old)?f.past.minusSeconds(86400):f.observed.plusSeconds(3L*86400);
            f.jdbc.update("""
                INSERT INTO wa.routine_occurrence(id,routine_id,driver_id,local_date,scheduled_at,created_at,schedule_snapshot,
                  history_environment,history_instance_id,history_origin_kind,history_origin_id,history_origin_bound_at)
                VALUES (?,?,?,?,?,?,?::jsonb,?,?,'SYSTEM_JOB',?,transaction_timestamp())
                """,id,f.routine,f.driver,scheduled.atZone(java.time.ZoneOffset.UTC).toLocalDate(),ts(scheduled),ts(f.past),"{\"name\":\"OLD_PRIVATE\"}",ENV,INSTANCE,f.credential);
        }
        f.finish();assertThat(f.string("SELECT state FROM wa.routine_occurrence WHERE id=?",old)).isEqualTo("PLANNED");
        assertThat(f.string("SELECT schedule_snapshot::text FROM wa.routine_occurrence WHERE id=?",old)).isEqualTo("{}");
        assertThat(f.string("SELECT history_erased_at::text FROM wa.routine_occurrence WHERE id=?",future)).isNull();
        assertThat(f.string("SELECT history_origin_kind FROM wa.routine_occurrence WHERE id=?",future)).isEqualTo("SYSTEM_JOB");
    }
    @Test void thirtyThenNinetyAllowsPreviouslyPurgedSubjectWithoutAlias() {
        personalErase();f.jdbc.update("DELETE FROM wa.identity_alias WHERE identity_id=?",f.first);
        String marker=f.string("SELECT privacy_erased_at::text FROM wa.run_stop WHERE passenger_id=?",f.first);
        f.finish();assertThat(f.string("SELECT privacy_erased_at::text FROM wa.run_stop WHERE passenger_id=?",f.first)).isEqualTo(marker);
        assertThat(f.string("SELECT state FROM wa.run_stop WHERE passenger_id=?",f.first)).isNull();
    }
    @Test void ninetyThenThirtyOnlyAddsPersonalMarkerAndDoesNotRestoreHistory() {
        f.finish();String marker=f.string("SELECT history_erased_at::text FROM wa.run_stop WHERE passenger_id=?",f.first);
        personalErase();assertThat(f.string("SELECT history_erased_at::text FROM wa.run_stop WHERE passenger_id=?",f.first)).isEqualTo(marker);
        assertThat(f.string("SELECT privacy_erased_at::text FROM wa.run_stop WHERE passenger_id=?",f.first)).isNotNull();
        assertThat(f.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",f.run)).isEqualTo("{\"rosterDigest\": \""+DIGEST+"\"}");
    }
    @Test void narrowGuardsRejectRecompositionMetadataChangesAndNewPersonalFacts() {
        String attendance=f.string("SELECT to_jsonb(a)::text FROM wa.attendance a LIMIT 1");
        String question=f.string("SELECT to_jsonb(a)::text FROM wa.attendance_question a LIMIT 1");
        String override=f.string("SELECT to_jsonb(a)::text FROM wa.occurrence_roster_override a LIMIT 1");
        f.finish();
        for(String sql:List.of("UPDATE wa.operation_run SET snapshot_json='{}'","UPDATE wa.operation_run SET state='TRACKING'",
            "UPDATE wa.operation_run SET history_erased_at=NULL","UPDATE wa.operation_run SET version=version+1",
            "UPDATE wa.run_stop SET state='PASSED'","UPDATE wa.run_stop SET attendance_snapshot='GOING'",
            "UPDATE wa.run_stop SET latitude_snapshot=1","UPDATE wa.run_stop SET history_erased_at=NULL",
            "UPDATE wa.routine_occurrence SET schedule_snapshot='{\"name\":\"RECOMPOSED\"}'",
            "UPDATE wa.routine_occurrence SET history_origin_id='11111111-1111-1111-1111-111111111111'"))
            assertThatThrownBy(()->f.jdbc.update(sql)).isInstanceOf(RuntimeException.class);
        for(var entry:Map.of("attendance",attendance,"attendance_question",question,"occurrence_roster_override",override).entrySet())
            assertThatThrownBy(()->f.jdbc.update("INSERT INTO wa."+entry.getKey()+" SELECT (jsonb_populate_record(NULL::wa."+entry.getKey()+",?::jsonb)).*",entry.getValue())).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->f.jdbc.update("INSERT INTO wa.run_stop SELECT (jsonb_populate_record(NULL::wa.run_stop,to_jsonb(s)||jsonb_build_object('id',?::uuid,'history_erased_at',NULL))).* FROM wa.run_stop s LIMIT 1",UUID.randomUUID())).isInstanceOf(RuntimeException.class);
    }
    @Test void aBusyPhysicalHeadDoesNotAdvanceAndEveryBatchHonorsItsBudget() throws Exception {
        f.pin();UUID head=f.jdbc.queryForObject("SELECT id FROM wa.run_stop ORDER BY id LIMIT 1",UUID.class);
        var pool=Executors.newSingleThreadExecutor();var locked=new CountDownLatch(1);var release=new CountDownLatch(1);
        try{
            var blocker=pool.submit(()->new TransactionTemplate(f.manager).execute(tx->{f.jdbc.queryForList("SELECT id FROM wa.run_stop WHERE id=? FOR UPDATE",head);locked.countDown();
                try{if(!release.await(20,TimeUnit.SECONDS))throw new AssertionError("Lock timeout");}catch(InterruptedException e){Thread.currentThread().interrupt();throw new AssertionError(e);}return true;}));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();var result=f.projection(false).tick(f.actor,1,null);
            assertThat(result.examined()).isEqualTo(1);assertThat(result.erased()).isZero();assertThat(result.deferred()).isEqualTo(1);assertThat(result.next().afterFamily()).isNull();
            release.countDown();blocker.get(10,TimeUnit.SECONDS);
            for(int i=0;i<20;i++){var page=f.projection(false).tick(f.actor,1,null);assertThat(page.examined()).isLessThanOrEqualTo(1);assertThat(page.erased()+page.deleted()).isLessThanOrEqualTo(1);f.leaves();if(page.complete())break;}
            assertThat(f.projection(false).tick(f.actor,1,null).complete()).isTrue();
        }finally{release.countDown();pool.shutdownNow();}
    }
    private void personalErase(){
        var journal=new WaPrivacyTestSupport.Journal();var service=new WaPrivacyService(f.jdbc,f.manager,f.clock,WaPrivacyPolicy.syntheticTests(),journal,f::authority);
        f.time.now=f.observed.minusSeconds(31L*86400);assertThat(service.close(SCOPE,f.first).status()).isEqualTo("CLOSED");f.time.now=f.observed;
        new WaPrivacyTerminalMaintenance(f.jdbc,f.manager,f.clock,WaPrivacyPolicy.syntheticTests(),false).tick(f.actor,100,null);
        assertThat(service.erase(SCOPE,f.first).status()).isEqualTo("ERASED");
    }
    private String rows(String table){return f.string("SELECT jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text)::text FROM wa."+table+" t");}
}
