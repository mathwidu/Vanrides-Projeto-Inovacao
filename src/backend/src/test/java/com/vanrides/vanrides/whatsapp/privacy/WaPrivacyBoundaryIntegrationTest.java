package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyBoundaryIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine")
        .withDatabaseName("wa_privacy_boundary").withUsername("synthetic_privacy").withPassword("synthetic-privacy-test-only");
    private WaPrivacyTestSupport f;
    @BeforeEach void setup() { f = new WaPrivacyTestSupport(POSTGRES,true); }

    @Test void noCohortIsRejectedEvenWithoutSyntheticOnlyAndWithAliasesInTwoInstances() {
        f.jdbc.update("UPDATE wa.identity SET test_cohort_id=NULL WHERE id=?",f.first);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','another-instance','PN','000000000000003@s.whatsapp.net')",
            UUID.randomUUID(),f.first);
        var service=f.service(new WaPrivacyPolicy(true,false),f.journal);
        assertThat(service.close(SCOPE,f.first).status()).isEqualTo("OUT_OF_SCOPE");
        assertThat(service.close(new WaPrivacyService.Scope(ENV,"another-instance"),f.first).status()).isEqualTo("OUT_OF_SCOPE");
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo("ACTIVE");
    }

    @Test void ambientTransactionIsRejectedBeforeClosureOrJournalAndRollbackHasNoEffects() {
        var outer=new TransactionTemplate(f.transactions);
        assertThatThrownBy(()->outer.execute(tx->{tx.setRollbackOnly();return f.service().close(SCOPE,f.first);}))
            .isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.journal.records).isEmpty();
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo("ACTIVE");
    }

    @Test void validCohortWithAliasesInTwoInstancesCannotCloseOrEraseFromEitherInstance() {
        secondInstanceAlias();
        String before=subjectState();
        for(var scope:java.util.List.of(SCOPE,new WaPrivacyService.Scope(ENV,"another-instance"))){
            assertThat(f.service().close(scope,f.first).status()).isEqualTo("OUT_OF_SCOPE");
            assertThat(f.service().erase(scope,f.first).status()).isEqualTo("OUT_OF_SCOPE");
            assertThat(subjectState()).isEqualTo(before);
        }
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.journal.records).isEmpty();
    }

    @Test void legacyClosedSubjectWithTwoInstancesBlocksErasureWithoutChangingEitherCopy() {
        secondInstanceAlias();
        // Reproduce the already-closed state admitted by the earlier single-scope implementation.
        var ledger=new WaPrivacyLedger(f.jdbc);
        new TransactionTemplate(f.transactions).execute(tx->{
            f.jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id=? FOR UPDATE",f.first);
            new WaPrivacySubjects(f.jdbc,WaPrivacyPolicy.syntheticTests()).close(SCOPE,f.first,f.time.now);
            ledger.intent(f.first,f.time.now);return true;
        });
        var entry=ledger.entry(f.first);var ack=f.journal.append(entry);
        Boolean acknowledged=new TransactionTemplate(f.transactions).execute(tx->ledger.acknowledge(entry,ack,f.time.now));
        assertThat(acknowledged).isTrue();
        f.time.days(31);
        String before=subjectState();
        for(var scope:java.util.List.of(SCOPE,new WaPrivacyService.Scope(ENV,"another-instance"))){
            assertThat(f.service().erase(scope,f.first).status()).isEqualTo("OUT_OF_SCOPE");
            assertThat(subjectState()).isEqualTo(before);
        }
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("JOURNALED");
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
        assertThat(f.journal.records).hasSize(1);
    }

    private void secondInstanceAlias(){
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','another-instance','PN','000000000000003@s.whatsapp.net')",
            UUID.randomUUID(),f.first);
    }
    private String subjectState(){
        return f.string("""
            SELECT jsonb_build_object('profile',(SELECT to_jsonb(p) FROM wa.profile p WHERE identity_id=?),
              'aliases',(SELECT jsonb_agg(to_jsonb(a) ORDER BY a.instance_id,a.id) FROM wa.identity_alias a WHERE identity_id=?),
              'ledger',(SELECT to_jsonb(l) FROM wa.privacy_erasure_ledger l WHERE subject_id=?),
              'points',(SELECT jsonb_agg(to_jsonb(p) ORDER BY p.id) FROM wa.pickup_point p WHERE passenger_id=?))::text
            """,f.first,f.first,f.first,f.first);
    }

    @Test void externalRollbackAfterAcknowledgementCannotUndoIntentOrLeakErasure() {
        assertThat(f.service().close(SCOPE,f.first).status()).isEqualTo("CLOSED");
        f.time.days(31); f.sweepTerminal();
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(tx->{
            tx.setRollbackOnly(); return f.service().erase(SCOPE,f.first);
        })).isInstanceOf(IllegalStateException.class);
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("JOURNALED");
        assertThat(f.journal.records).hasSize(1);
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
        assertThat(f.service().erase(SCOPE,f.first).status()).isEqualTo("ERASED");
    }

    @Test void authorityIsCheckedBeforeCommitAndFailureRollsBackBeforeJournal() {
        var calls=new AtomicInteger();
        var service=new WaPrivacyService(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),f.journal,scope->{
            assertThat(TransactionSynchronizationManager.isActualTransactionActive()).isTrue();
            assertThat(scope).isEqualTo(SCOPE);
            if(calls.incrementAndGet()==2) throw new SecurityException("synthetic-expired-authority");
        });
        assertThatThrownBy(()->service.close(SCOPE,f.first)).isInstanceOf(SecurityException.class);
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.journal.records).isEmpty();
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo("ACTIVE");
    }

    @Test void authorityLostAfterJournalBlocksAcknowledgementAndLaterErasure() {
        var allowed=new AtomicBoolean(true);
        WaPrivacyJournal journal=new WaPrivacyJournal(){
            public Ack append(Entry entry){return f.journal.append(entry);}
            public boolean verify(Entry entry,Ack ack){boolean valid=f.journal.verify(entry,ack);allowed.set(false);return valid;}
        };
        var service=new WaPrivacyService(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),journal,scope->{
            if(!allowed.get())throw new SecurityException("synthetic-revoked-authority");
        });
        assertThat(service.close(SCOPE,f.first).status()).isEqualTo("JOURNAL_PENDING");
        assertThat(f.journal.records).hasSize(1);
        assertThat(f.string("SELECT state FROM wa.privacy_erasure_ledger")).isEqualTo("INTENT");
        f.time.days(31);
        assertThatThrownBy(()->service.erase(SCOPE,f.first)).isInstanceOf(SecurityException.class);
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
    }
}
