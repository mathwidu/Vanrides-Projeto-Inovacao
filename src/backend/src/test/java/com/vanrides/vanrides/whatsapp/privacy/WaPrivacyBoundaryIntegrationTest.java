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
