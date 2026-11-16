package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import java.sql.Timestamp;
import java.util.List;
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
class WaPrivacyHistoryRetentionIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_history_privacy").withUsername("synthetic_history").withPassword("synthetic-history-only");
    private WaPrivacyHistoryFixture h;
    private WaPrivacyTestSupport f;
    @BeforeEach void setup() { h=new WaPrivacyHistoryFixture(POSTGRES); f=h.f; }

    @Test void deletesOnlySafePhysicalLeavesAndReportsUnfinishedGraphInsteadOfComplete() {
        String controls=controls(); long before=leaves();
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.deleted()).isEqualTo(7);
        assertThat(before-leaves()).isEqualTo(result.deleted());
        assertThat(result.erased()).isZero(); assertThat(result.examined()).isLessThanOrEqualTo(100);
        assertThat(result.complete()).isFalse(); assertThat(result.status()).isEqualTo("DEFERRED");
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason)
            .contains("AUDIT_ORIGIN_UNATTRIBUTABLE","HISTORY_GRAPH_PENDING");
        assertThat(f.count("attendance_question")).isZero(); assertThat(f.count("occurrence_roster_override")).isZero();
        assertThat(f.count("attendance")).isEqualTo(4); // recent + future, for both passengers
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE id=?",Long.class,h.oldClosed)).isZero();
        for (UUID support : List.of(h.open,h.recentClosed,h.deliveryOpen,h.deliveryRecent))
            assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE id=?",Long.class,support)).isEqualTo(1);
        assertThat(controls()).isEqualTo(controls);
    }
    @Test void repeatIsIdempotentAndBatchesCountPhysicalRows() {
        var actor=f.job(ENV,INSTANCE); WaPrivacyHistoryRetention.Position cursor=null;
        int deleted=0; boolean sawPending=false;
        for (int page=0;page<15;page++) {
            var result=h.retention(false).tick(actor,1,cursor);
            assertThat(result.examined()).isLessThanOrEqualTo(1); assertThat(result.deleted()).isLessThanOrEqualTo(1);
            deleted+=result.deleted(); sawPending|=result.deferred()>0; cursor=result.next();
            if (sawPending && cursor!=null && cursor.afterFamily()==null) break;
        }
        assertThat(deleted).isEqualTo(7); assertThat(sawPending).isTrue();
        String after=controls(); var again=h.retention(false).tick(actor,100,null);
        assertThat(again.deleted()).isZero(); assertThat(again.complete()).isFalse(); assertThat(controls()).isEqualTo(after);
    }
    @Test void defaultDryRunAndDisabledPolicyMakeNoPersistentChange() {
        long before=leaves(); String controls=controls(); var actor=f.job(ENV,INSTANCE);
        var dry=new WaPrivacyHistoryRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests());
        var result=dry.tick(actor,100,null);
        assertThat(result.status()).isEqualTo("DRY_RUN"); assertThat(result.deleted()).isZero();
        assertThat(result.erased()).isZero(); assertThat(result.wouldDelete()).isPositive(); assertThat(result.complete()).isFalse();
        assertThat(leaves()).isEqualTo(before); assertThat(controls()).isEqualTo(controls);
        var disabled=new WaPrivacyHistoryRetention(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.disabled(),false);
        assertThat(disabled.tick(actor,100,null).status()).isEqualTo("DISABLED"); assertThat(leaves()).isEqualTo(before);
    }
    @Test void wrongScopeAndCredentialInstanceNeverReachForeignRows() {
        long before=leaves();
        assertThat(h.retention(false).tick(f.job("another-environment",INSTANCE),100,null).examined()).isZero();
        var unknown=h.retention(false).tick(f.job(ENV,"another-instance"),100,null);
        assertThat(unknown.deleted()).isZero();
        assertThat(unknown.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason).containsOnly("AUDIT_ORIGIN_UNATTRIBUTABLE");
        var actor=f.job(ENV,INSTANCE);
        var wrong=new com.vanrides.vanrides.whatsapp.auth.WaPrincipal(actor.kind(),actor.credentialId(),null,null,"wrong",null,0,false);
        assertThatThrownBy(()->h.retention(false).tick(wrong,100,null)).isInstanceOf(RuntimeException.class);
        assertThat(leaves()).isEqualTo(before);
    }
    @Test void cursorBindsActorModeScopeTimeAndTypedPosition() {
        var actor=f.job(ENV,INSTANCE); var cursor=h.retention(false).tick(actor,1,null).next();
        assertThat(cursor).isNotNull();
        assertThatThrownBy(()->h.retention(false).tick(f.job(ENV,INSTANCE),1,cursor)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->h.retention(true).tick(actor,1,cursor)).isInstanceOf(RuntimeException.class);
        var scope=new WaPrivacyHistoryRetention.Position(cursor.credentialId(),"foreign",cursor.instanceId(),cursor.asOf(),
            cursor.afterFamily(),cursor.afterAt(),cursor.afterId(),false,true);
        assertThatThrownBy(()->h.retention(false).tick(actor,1,scope)).isInstanceOf(RuntimeException.class);
        var broken=new WaPrivacyHistoryRetention.Position(cursor.credentialId(),ENV,INSTANCE,cursor.asOf(),
            cursor.afterFamily(),null,cursor.afterId(),false,true);
        assertThatThrownBy(()->h.retention(false).tick(actor,1,broken)).isInstanceOf(RuntimeException.class);
        f.time.now=f.time.now.plusSeconds(301);
        assertThatThrownBy(()->h.retention(false).tick(actor,1,cursor)).isInstanceOf(RuntimeException.class);
    }
    @Test void completionDoesNotTrustAnAdvancedCursorAndReturnsToPendingRows() {
        var actor=f.job(ENV,INSTANCE);
        var cursor=new WaPrivacyHistoryRetention.Position(actor.credentialId(),ENV,INSTANCE,f.time.now,
            WaPrivacyHistoryRetention.Family.OCCURRENCE_GRAPH,f.time.now,new UUID(-1,-1),false,true);
        var result=h.retention(false).tick(actor,100,cursor);
        assertThat(result.examined()).isZero(); assertThat(result.complete()).isFalse();
        assertThat(result.next()).isNotNull(); assertThat(result.next().afterFamily()).isNull();
    }
    @Test void aForeignCohortCannotBeDeletedThroughAnInScopeDriverOrAlias() {
        UUID foreign=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'another-environment',true)",foreign);
        f.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?",foreign,f.second);
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.deleted()).isEqualTo(5);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance_question WHERE passenger_id=?",Long.class,f.second)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance WHERE passenger_id=? AND local_date='2030-01-01'",Long.class,f.second)).isEqualTo(1);
    }
    @Test void ambientTransactionIsRejectedBeforeAnyEffect() {
        long before=leaves(); var actor=f.job(ENV,INSTANCE);
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(tx->h.retention(false).tick(actor,100,null)))
            .isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        assertThat(leaves()).isEqualTo(before);
        assertThatThrownBy(()->h.retention(false).tick(actor,101,null)).isInstanceOf(RuntimeException.class);
        assertThatThrownBy(()->h.retention(false).tick(actor,0,null)).isInstanceOf(RuntimeException.class);
    }
    @Test void lockedHeadDoesNotAdvancePrefixOrDeleteLaterLeaves() throws Exception {
        UUID head=f.jdbc.queryForObject("SELECT id FROM wa.attendance_question ORDER BY greatest(created_at,answer_until,answered_at),id LIMIT 1",UUID.class);
        var pool=Executors.newSingleThreadExecutor(); var locked=new CountDownLatch(1); var release=new CountDownLatch(1);
        long before=leaves();
        try {
            var blocker=pool.submit(()->new TransactionTemplate(f.transactions).execute(tx->{
                f.jdbc.queryForList("SELECT id FROM wa.attendance_question WHERE id=? FOR UPDATE",head); locked.countDown();
                try { if (!release.await(20,TimeUnit.SECONDS)) throw new AssertionError("Synthetic lock timeout"); }
                catch (InterruptedException error) { Thread.currentThread().interrupt(); throw new AssertionError(error); } return true;
            }));
            assertThat(locked.await(10,TimeUnit.SECONDS)).isTrue();
            var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
            assertThat(result.examined()).isEqualTo(1); assertThat(result.deleted()).isZero(); assertThat(result.deferred()).isEqualTo(1);
            assertThat(result.pending().get(0).reason()).isEqualTo("ROW_BUSY");
            assertThat(result.next().afterFamily()).isNull(); assertThat(result.complete()).isFalse(); assertThat(leaves()).isEqualTo(before);
            release.countDown(); blocker.get(10,TimeUnit.SECONDS);
            assertThat(h.retention(false).tick(f.job(ENV,INSTANCE),100,null).deleted()).isEqualTo(7);
        } finally { release.countDown(); pool.shutdownNow(); }
    }
    @Test void questionThatCarriesOutboxOriginAndItsAttendanceRemainWhileOtherSafeLeavesProgress() {
        UUID outbox=f.jdbc.queryForObject("SELECT id FROM wa.message_outbox WHERE recipient_identity_id=?",UUID.class,f.first);
        f.jdbc.update("""
            UPDATE wa.attendance_question SET outbox_id=?,source_inbox_event_id=?,sender_alias_id=? WHERE passenger_id=?
            """,outbox,f.inboxes.get(f.first),f.aliases.get(f.first),f.first);
        String queues=rows("message_outbox");
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason)
            .contains("QUESTION_OUTBOX_PROVENANCE","QUESTION_REFERENCE");
        assertThat(result.deleted()).isEqualTo(5); assertThat(f.count("attendance_question")).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance WHERE passenger_id=? AND local_date='2030-01-01'",Long.class,f.first)).isEqualTo(1);
        assertThat(rows("message_outbox")).isEqualTo(queues);
    }
    @Test void ambiguousAliasesCannotOverrideProvenanceAndUnprovenAuditOrSupportStayDeferred() {
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','other-instance','PN','synthetic-other')",UUID.randomUUID(),f.first);
        UUID support=UUID.randomUUID(); h.support(support,"CLOSED",h.old,h.old,"not-a-source");
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason)
            .contains("AUDIT_ORIGIN_UNATTRIBUTABLE","SUPPORT_ORIGIN_UNATTRIBUTABLE");
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.attendance WHERE local_date='2030-01-01'",Long.class)).isZero();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE id=?",Long.class,support)).isEqualTo(1);
        assertThat(result.complete()).isFalse();
    }
    @Test void historyWithoutAnyCurrentAliasStillReportsItsUnattributableOrigin() {
        UUID support=UUID.randomUUID(); h.support(support,"CLOSED",h.old,h.old,"no-immutable-origin");
        var operator=f.job(ENV,null);
        f.jdbc.update("UPDATE wa.service_credential SET principal='OPERATOR' WHERE id=?",operator.credentialId());
        f.jdbc.update("""
            INSERT INTO wa.audit_event(id,credential_id,actor_kind,action,correlation_id,created_at)
            VALUES (?,?,'OPERATOR','SYNTHETIC_HISTORY','operator-without-instance',?)
            """,UUID.randomUUID(),operator.credentialId(),Timestamp.from(h.old));
        f.jdbc.update("DELETE FROM wa.identity_alias WHERE identity_id=?",f.first);
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason)
            .contains("AUDIT_ORIGIN_UNATTRIBUTABLE","SUPPORT_ORIGIN_UNATTRIBUTABLE","AUDIT_COHORT_UNATTRIBUTABLE");
        assertThat(result.deleted()).isEqualTo(7); assertThat(result.complete()).isFalse();
        assertThat(f.jdbc.queryForObject("SELECT count(*) FROM wa.support_case WHERE id=?",Long.class,support)).isEqualTo(1);
    }
    @Test void theOnlyOccurrenceEvidenceCannotBeDeletedBeforeOriginIsAnchored() {
        h=new WaPrivacyHistoryFixture(POSTGRES,false); f=h.f;
        f.jdbc.update("UPDATE wa.start_intent SET request_command_id=NULL WHERE occurrence_id=?",f.occurrence);
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason).contains("HISTORY_ORIGIN_UNATTRIBUTABLE");
        assertThat(f.count("attendance_question")).isEqualTo(2);
        assertThat(f.count("occurrence_roster_override")).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(2);
    }
    @Test void missingTerminalTimestampAndRecentClosureNeverBorrowTheOldCreationTime() {
        f.jdbc.update("UPDATE wa.operation_run SET completed_at=NULL WHERE id=?",f.run);
        long before=leaves();
        var result=h.retention(false).tick(f.job(ENV,INSTANCE),100,null);
        assertThat(result.pending()).extracting(WaPrivacyHistoryRetention.Deferral::reason).contains("MISSING_TERMINAL_AT");
        assertThat(f.count("attendance_question")).isEqualTo(2);
        assertThat(f.count("occurrence_roster_override")).isEqualTo(1);
        assertThat(result.deleted()).isEqualTo(2); assertThat(before-leaves()).isEqualTo(2);
        f.jdbc.update("UPDATE wa.operation_run SET completed_at=? WHERE id=?",Timestamp.from(f.time.now),f.run);
        assertThat(h.retention(false).tick(f.job(ENV,INSTANCE),100,null).pending())
            .noneMatch(p->p.family()==WaPrivacyHistoryRetention.Family.QUESTION);
        assertThat(f.count("attendance_question")).isEqualTo(2);
    }
    private long leaves() {
        return List.of("attendance_question","attendance","occurrence_roster_override","support_case","audit_event")
            .stream().mapToLong(f::count).sum();
    }
    private String controls() {
        return List.of("profile","identity_alias","pickup_point","routine","routine_member","routine_occurrence",
            "operation_run","run_stop","message_outbox","integration_inbox","start_intent","command_receipt").stream().map(this::rows).collect(java.util.stream.Collectors.joining("\n"));
    }
    private String rows(String table) {
        return f.string("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM wa."+table+" t");
    }
}
