package com.vanrides.vanrides.whatsapp.privacy;

import static org.assertj.core.api.Assertions.*;
import static com.vanrides.vanrides.whatsapp.privacy.WaPrivacyTestSupport.*;

import com.vanrides.vanrides.whatsapp.auth.WaCrypto;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.PosixFilePermissions;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Consumer;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.api.condition.EnabledOnOs;
import org.junit.jupiter.api.condition.OS;
import org.springframework.transaction.support.TransactionTemplate;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

@EnabledOnOs({OS.LINUX,OS.MAC})
@Testcontainers(disabledWithoutDocker=true)
class WaPrivacyRestoreIntegrationTest {
    @Container static final PostgreSQLContainer<?> POSTGRES=new PostgreSQLContainer<>("postgres:16-alpine")
        .withTmpFs(Map.of("/var/lib/postgresql/data","rw,size=256m"))
        .withDatabaseName("wa_privacy_restore").withUsername("synthetic_restore").withPassword("synthetic-restore-only");
    @TempDir Path temporary;
    private WaPrivacyTestSupport f;
    private WaPrivacyFileJournal journal;
    private Path directory;
    private WaPrincipal actor;
    private final AtomicBoolean traffic=new AtomicBoolean(true);

    @BeforeEach void setup() throws Exception {
        fixture(true);
        directory=Files.createDirectory(temporary.resolve("current-private-journal"),
            PosixFilePermissions.asFileAttribute(PosixFilePermissions.fromString("rwx------"))).toRealPath();
        journal=new WaPrivacyFileJournal(directory); journal.initializeEmpty();
    }
    @Test void realCustomDumpRestoresBeforeClosureAndReappliesTheCurrentExternalJournal() throws Exception {
        String beta=beta(); Instant closed=f.time.now;
        String dump="/var/lib/postgresql/data/privacy-before-close.dump";
        command("pg_dump","-U",POSTGRES.getUsername(),"-d",POSTGRES.getDatabaseName(),"-Fc","--no-owner","--no-acl","-f",dump);
        long bytes=Long.parseLong(command("stat","-c","%s",dump).trim());
        assertThat(bytes).isBetween(1L,2L*1024*1024);
        f.jdbc.queryForObject("SELECT setval(pg_get_serial_sequence('wa.privacy_erasure_ledger','sequence'),700,true)",Long.class);
        assertThat(service().close(SCOPE,f.first).status()).isEqualTo("CLOSED");
        var original=journal.snapshot(); var record=original.records().get(0);
        assertThat(record.entry().databaseSequence()).isEqualTo(701);
        f.time.days(31); f.sweepTerminal(); assertThat(service().erase(SCOPE,f.first).status()).isEqualTo("ERASED");
        assertAlphaErased(); assertThat(beta()).isEqualTo(beta);
        command("pg_restore","-U",POSTGRES.getUsername(),"-d",POSTGRES.getDatabaseName(),"--clean","--if-exists","--no-owner","--exit-on-error",dump);
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo(NAME);
        assertThat(f.count("privacy_erasure_ledger")).isZero();
        var restored=reconcile(original,0,100);
        assertThat(restored.status()).isEqualTo("RETENTION_PENDING"); assertThat(restored.ready()).isFalse();
        assertThat(restored.imported()).isEqualTo(1); assertThat(restored.acknowledged()).isEqualTo(1);
        assertThat(new WaPrivacyLedger(f.jdbc).entry(f.first)).isEqualTo(record.entry());
        assertThat(f.jdbc.queryForObject("SELECT closed_at FROM wa.profile WHERE identity_id=?",Timestamp.class,f.first).toInstant()).isEqualTo(closed);
        assertThat(f.jdbc.queryForObject("SELECT erase_after FROM wa.privacy_subject WHERE subject_id=?",Timestamp.class,f.first).toInstant()).isEqualTo(closed.plusSeconds(30L*86400));
        assertThat(f.jdbc.queryForObject("SELECT created_at FROM wa.privacy_erasure_ledger WHERE subject_id=?",Timestamp.class,f.first).toInstant()).isEqualTo(closed);
        assertThat(f.string("SELECT journal_digest FROM wa.privacy_erasure_ledger WHERE subject_id=?",f.first)).isEqualTo(record.acknowledgement().chainDigest());
        assertThat(journal.snapshot()).isEqualTo(original);
        f.sweepTerminal(); assertThat(service().erase(SCOPE,f.first).status()).isEqualTo("ERASED");
        assertAlphaErased(); assertThat(beta()).isEqualTo(beta);
        assertThat(reconcile(original,original.sequence(),100).ready()).isTrue();
        f.jdbc.update("DELETE FROM wa.identity_alias WHERE identity_id=?",f.first);
        String purged=state(); var repeated=reconcile(original,0,100);
        assertThat(repeated.ready()).isTrue(); assertThat(repeated.imported()).isZero(); assertThat(repeated.acknowledged()).isZero();
        assertThat(state()).isEqualTo(purged); assertThat(beta()).isEqualTo(beta);
        assertThat(f.jdbc.queryForObject("SELECT nextval(pg_get_serial_sequence('wa.privacy_erasure_ledger','sequence'))",Long.class)).isGreaterThan(701);
        command("rm","-f",dump);
    }
    @Test void missingTamperedTruncatedAndWrongHeadBlockBeforeDatabaseMutation() throws Exception {
        append(f.first,37,ENV,INSTANCE); var head=journal.snapshot(); String before=state();
        byte[] data=Files.readAllBytes(directory.resolve("erasures.jsonl")),anchor=Files.readAllBytes(directory.resolve("head.json"));
        for (String failure : List.of("missing","tampered","truncated","head")) {
            Files.write(directory.resolve("erasures.jsonl"),data); Files.write(directory.resolve("head.json"),anchor);
            Files.setPosixFilePermissions(directory.resolve("head.json"),PosixFilePermissions.fromString("rw-------"));
            if (failure.equals("missing")) Files.delete(directory.resolve("head.json"));
            if (failure.equals("tampered")) Files.writeString(directory.resolve("erasures.jsonl"),new String(data,java.nio.charset.StandardCharsets.UTF_8).replace("wa-privacy-v1","wa-privacy-v2"));
            if (failure.equals("truncated")) Files.write(directory.resolve("erasures.jsonl"),java.util.Arrays.copyOf(data,data.length-1));
            var result=port(authority()).reconcile(SCOPE,head.sequence(),failure.equals("head") ? "f".repeat(64) : head.digest(),0,100);
            assertThat(result.status()).as(failure).isEqualTo("BLOCKED"); assertThat(result.ready()).isFalse();
            assertThat(result.imported()).isZero(); assertThat(state()).isEqualTo(before);
        }
    }
    @Test void mixedJournalScopeDeniesAllEntriesBeforeAnyClosure() {
        append(f.first,37,ENV,INSTANCE); append(f.second,38,ENV,"foreign-instance");
        var result=reconcile(journal.snapshot(),0,100);
        assertThat(result.pending()).containsExactly("JOURNAL_SCOPE_MISMATCH");
        assertThat(f.count("privacy_erasure_ledger")).isZero(); assertThat(f.count("privacy_subject")).isZero();
        assertThat(f.string("SELECT status FROM wa.profile WHERE identity_id=?",f.first)).isEqualTo("ACTIVE");
    }
    @Test void foreignCohortAndMultiInstanceSubjectCannotBeClosed() {
        append(f.first,37,ENV,INSTANCE); var head=journal.snapshot();
        UUID cohort=UUID.randomUUID();
        f.jdbc.update("INSERT INTO wa.test_cohort(id,environment,synthetic) VALUES (?,'foreign',true)",cohort);
        f.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?",cohort,f.first);
        assertThat(reconcile(head,0,100).pending()).containsExactly("SUBJECT_OUT_OF_SCOPE");
        f.jdbc.update("UPDATE wa.identity SET test_cohort_id=? WHERE id=?",f.cohort,f.first);
        f.jdbc.update("INSERT INTO wa.identity_alias(id,identity_id,provider,instance_id,alias_type,alias_value) VALUES (?,?,'EVOLUTION','foreign-instance','PN','synthetic-foreign')",UUID.randomUUID(),f.first);
        assertThat(reconcile(head,0,100).pending()).containsExactly("SUBJECT_OUT_OF_SCOPE");
        assertThat(f.count("privacy_erasure_ledger")).isZero(); assertThat(f.count("privacy_subject")).isZero();
    }
    @Test void activeReferenceBlocksClosureAndJournalImport() {
        fixture(false); append(f.first,37,ENV,INSTANCE); String before=state();
        assertThat(reconcile(journal.snapshot(),0,100).pending()).containsExactly("ACTIVE_REFERENCE");
        assertThat(state()).isEqualTo(before);
    }
    @Test void trafficOpenExpiredAuthorityAndAmbientTransactionBlock() {
        append(f.first,37,ENV,INSTANCE); var head=journal.snapshot(); String before=state();
        traffic.set(false);
        assertThat(reconcile(head,0,100).pending()).containsExactly("TRAFFIC_NOT_BLOCKED"); traffic.set(true);
        assertThatThrownBy(()->new TransactionTemplate(f.transactions).execute(tx->reconcile(head,0,100)))
            .isInstanceOf(IllegalStateException.class).hasMessage("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        f.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?",Timestamp.from(f.time.now.plusSeconds(1)),actor.credentialId());
        f.time.now=f.time.now.plusSeconds(2);
        assertThat(reconcile(head,0,100).pending()).containsExactly("AUTHORITY_REJECTED");
        assertThat(state()).isEqualTo(before);
    }
    @Test void authorityLossBeforeCommitRollsBackClosureAndImportWhileSequenceOnlyAdvances() {
        append(f.first,37,ENV,INSTANCE); var head=journal.snapshot(); String before=state();
        AtomicInteger checks=new AtomicInteger(); Consumer<WaPrivacyService.Scope> authority=authority();
        var restore=port(scope->{ if (checks.incrementAndGet()==4) throw new IllegalStateException("synthetic-revocation"); authority.accept(scope); });
        var result=restore.reconcile(SCOPE,head.sequence(),head.digest(),0,100);
        assertThat(result.pending()).containsExactly("AUTHORITY_REJECTED"); assertThat(result.imported()).isZero();
        assertThat(state()).isEqualTo(before); assertThat(f.count("privacy_erasure_ledger")).isZero();
        assertThat(f.jdbc.queryForObject("SELECT pg_sequence_last_value(pg_get_serial_sequence('wa.privacy_erasure_ledger','sequence')::regclass)",Long.class)).isGreaterThanOrEqualTo(37);
        checks.set(0);
        var lostTraffic=port(scope->{ authority.accept(scope); if (checks.incrementAndGet()==4) traffic.set(false); });
        var blocked=lostTraffic.reconcile(SCOPE,head.sequence(),head.digest(),0,100);
        assertThat(blocked.pending()).containsExactly("TRAFFIC_NOT_BLOCKED"); assertThat(state()).isEqualTo(before);
        traffic.set(true);
    }
    @Test void advancedCursorCannotHideMissingEntriesAndPagesAreBounded() {
        append(f.first,37,ENV,INSTANCE); append(f.second,38,ENV,INSTANCE); var head=journal.snapshot();
        var forged=reconcile(head,head.sequence(),1);
        assertThat(forged.ready()).isFalse(); assertThat(forged.examined()).isZero(); assertThat(forged.nextAfter()).isZero();
        var first=reconcile(head,0,1); assertThat(first.examined()).isEqualTo(1); assertThat(first.imported()).isEqualTo(1);
        var second=reconcile(head,first.nextAfter(),1); assertThat(second.examined()).isEqualTo(1); assertThat(second.imported()).isEqualTo(1);
        assertThat(second.status()).isEqualTo("RETENTION_PENDING"); assertThat(second.ready()).isFalse();
        var repeated=reconcile(head,0,100); assertThat(repeated.imported()).isZero(); assertThat(repeated.acknowledged()).isZero();
        assertThat(f.count("privacy_erasure_ledger")).isEqualTo(2);
        assertThatThrownBy(()->reconcile(head,0,101)).isInstanceOf(IllegalArgumentException.class);
        var disabled=new WaPrivacyRestore(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.disabled(),journal,authority(),traffic::get);
        assertThat(disabled.reconcile(SCOPE,head.sequence(),head.digest(),0,100).status()).isEqualTo("DISABLED");
    }
    @Test void conflictingExistingLedgerDoesNotOverwriteItsPayloadOrCreationTime() {
        new TransactionTemplate(f.transactions).execute(tx->{
            new WaPrivacySubjects(f.jdbc,WaPrivacyPolicy.syntheticTests()).close(SCOPE,f.first,f.time.now);
            new WaPrivacyLedger(f.jdbc).intent(f.first,f.time.now.plusSeconds(1)); return true;
        });
        append(f.first,37,ENV,INSTANCE); String before=state();
        assertThat(reconcile(journal.snapshot(),0,100).pending()).containsExactly("LEDGER_CONFLICT");
        assertThat(state()).isEqualTo(before);
    }
    private void fixture(boolean terminal) {
        f=new WaPrivacyTestSupport(POSTGRES,terminal); actor=f.job(ENV,INSTANCE);
        f.jdbc.update("UPDATE wa.service_credential SET expires_at=? WHERE id=?",Timestamp.from(f.time.now.plusSeconds(100L*86400)),actor.credentialId());
    }
    private Consumer<WaPrivacyService.Scope> authority() {
        return scope->{
            var grants=f.jdbc.queryForList("""
                SELECT id FROM wa.service_credential WHERE id=? AND principal='SYSTEM_JOB' AND environment=?
                  AND allowed_instance_id=? AND revoked_at IS NULL AND expires_at>? FOR SHARE
                """,actor.credentialId(),scope.environment(),scope.instanceId(),f.timestamp());
            if (grants.size()!=1) throw new IllegalStateException("synthetic-authority-rejected");
        };
    }
    private WaPrivacyRestore port(Consumer<WaPrivacyService.Scope> authority) {
        return new WaPrivacyRestore(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),journal,authority,traffic::get);
    }
    private WaPrivacyService service() { return new WaPrivacyService(f.jdbc,f.transactions,f.clock,WaPrivacyPolicy.syntheticTests(),journal,authority()); }
    private WaPrivacyRestore.Result reconcile(WaPrivacyFileJournal.Snapshot head,long after,int limit) {
        return port(authority()).reconcile(SCOPE,head.sequence(),head.digest(),after,limit);
    }
    private void append(UUID subject,long sequence,String environment,String instance) {
        UUID event=UUID.randomUUID(); Instant closed=f.time.now.minusSeconds(31L*86400);
        String payload=f.string("""
            SELECT jsonb_build_object('policy','wa-privacy-v1','eventId',?::uuid,'subjectId',?::uuid,
              'environment',?::text,'instanceId',?::text,'closedAt',?::timestamptz,'eraseAfter',?::timestamptz)::text
            """,event,subject,environment,instance,Timestamp.from(closed),Timestamp.from(closed.plusSeconds(30L*86400)));
        journal.append(new WaPrivacyJournal.Entry(sequence,event,subject,environment,instance,payload,WaCrypto.digest(payload)));
    }
    private String command(String... arguments) throws Exception {
        var result=POSTGRES.execInContainer(arguments);
        assertThat(result.getExitCode()).as(arguments[0]+" synthetic container only").isZero();
        return result.getStdout();
    }
    private String state() {
        return List.of("profile","privacy_subject","privacy_erasure_ledger","routine_member","driver_passenger_link","pickup_point")
            .stream().map(table->f.string("SELECT coalesce(jsonb_agg(to_jsonb(t) ORDER BY to_jsonb(t)::text),'[]'::jsonb)::text FROM wa."+table+" t"))
            .collect(java.util.stream.Collectors.joining("\n"));
    }
    private String beta() {
        return f.string("SELECT to_jsonb(p)::text FROM wa.profile p WHERE identity_id=?",f.second)
            +f.string("SELECT to_jsonb(p)::text FROM wa.pickup_point p WHERE passenger_id=?",f.second)
            +f.string("SELECT to_jsonb(s)::text FROM wa.run_stop s WHERE passenger_id=?",f.second)
            +f.string("SELECT to_jsonb(m)::text FROM wa.routine_member m WHERE passenger_id=?",f.second);
    }
    private void assertAlphaErased() {
        assertThat(f.string("SELECT display_name FROM wa.profile WHERE identity_id=?",f.first)).isNull();
        assertThat(f.string("SELECT alias_value FROM wa.identity_alias WHERE identity_id=?",f.first)).isNull();
        assertThat(f.jdbc.queryForObject("SELECT latitude FROM wa.pickup_point WHERE passenger_id=?",Double.class,f.first)).isNull();
        assertThat(f.jdbc.queryForObject("SELECT latitude_snapshot FROM wa.run_stop WHERE passenger_id=?",Double.class,f.first)).isNull();
        assertThat(f.string("SELECT snapshot_json::text FROM wa.operation_run WHERE id=?",f.run)).doesNotContain(NAME,REFERENCE,"12.3456");
        assertThat(f.count("attendance")).isEqualTo(2); assertThat(f.count("run_stop")).isEqualTo(2);
    }
}
