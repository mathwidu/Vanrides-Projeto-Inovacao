package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaJson;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.BooleanSupplier;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Restore-only reconciliation. It never enables traffic or starts a purge. */
public final class WaPrivacyRestore {
    public record Result(String status, boolean ready, int examined, int imported, int acknowledged,
                         long nextAfter, List<String> pending) { }
    private record Dates(Instant closed, Instant after) { }
    private record Applied(boolean imported, boolean acknowledged) { }
    private static final class Blocked extends RuntimeException {
        final String reason;
        Blocked(String reason) { super(reason); this.reason=reason; }
    }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final WaPrivacyFileJournal journal;
    private final Consumer<WaPrivacyService.Scope> authority;
    private final BooleanSupplier trafficBlocked;
    private final WaPrivacySubjects subjects;
    private final WaPrivacyLedger ledger;

    public WaPrivacyRestore(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                            WaPrivacyPolicy policy, WaPrivacyFileJournal journal,
                            Consumer<WaPrivacyService.Scope> authority, BooleanSupplier trafficBlocked) {
        this.jdbc=java.util.Objects.requireNonNull(jdbc); this.transaction=new TransactionTemplate(manager);
        this.clock=java.util.Objects.requireNonNull(clock); this.policy=java.util.Objects.requireNonNull(policy);
        this.journal=java.util.Objects.requireNonNull(journal); this.authority=java.util.Objects.requireNonNull(authority);
        this.trafficBlocked=java.util.Objects.requireNonNull(trafficBlocked);
        subjects=new WaPrivacySubjects(jdbc,policy); ledger=new WaPrivacyLedger(jdbc);
    }
    /** The expected head must come from the independently retained current journal, never from the old dump. */
    public Result reconcile(WaPrivacyService.Scope scope, long expectedHeadSequence, String expectedHeadDigest,
                            long afterSequence, int limit) {
        ownTransaction(); java.util.Objects.requireNonNull(scope);
        if (limit<1 || limit>100 || afterSequence<0 || expectedHeadSequence<0 || afterSequence>expectedHeadSequence
            || expectedHeadDigest==null || !expectedHeadDigest.matches("[0-9a-f]{64}"))
            throw new IllegalArgumentException("PRIVACY_RESTORE_ARGUMENTS");
        if (!policy.enabled()) return new Result("DISABLED",false,0,0,0,afterSequence,List.of("POLICY_DISABLED"));
        int examined=0,imported=0,acknowledged=0; long next=afterSequence;
        try {
            authorized(scope,()->true);
            var snapshot=snapshot(expectedHeadSequence,expectedHeadDigest);
            Map<UUID,WaPrivacyFileJournal.VerifiedEntry> expected=validate(scope,snapshot);
            for (var record : snapshot.records()) {
                if (record.acknowledgement().journalSequence()<=afterSequence) continue;
                if (examined==limit) break;
                examined++;
                snapshot(expectedHeadSequence,expectedHeadDigest);
                if (!journal.verify(record.entry(),record.acknowledgement())) throw new Blocked("JOURNAL_UNAVAILABLE");
                Applied applied=authorized(scope,()->apply(scope,record));
                if (applied.imported()) imported++;
                if (applied.acknowledged()) acknowledged++;
                next=record.acknowledgement().journalSequence();
            }
            snapshot(expectedHeadSequence,expectedHeadDigest);
            List<String> pending=authorized(scope,()->pending(scope,expected));
            snapshot(expectedHeadSequence,expectedHeadDigest);
            authorized(scope,()->true);
            if (pending.isEmpty()) return new Result("READY",true,examined,imported,acknowledged,next,List.of());
            if (next==snapshot.sequence()) next=0; // A forged/advanced position never hides missing earlier work.
            boolean retention=pending.stream().allMatch("RETENTION_PENDING"::equals);
            return new Result(retention ? "RETENTION_PENDING" : "BLOCKED",false,examined,imported,acknowledged,next,List.copyOf(pending));
        } catch (Blocked failure) {
            return new Result("BLOCKED",false,examined,imported,acknowledged,next,List.of(failure.reason));
        } catch (RuntimeException failure) {
            return new Result("BLOCKED",false,examined,imported,acknowledged,next,List.of("RESTORE_VALIDATION_FAILED"));
        }
    }
    private WaPrivacyFileJournal.Snapshot snapshot(long sequence,String digest) {
        traffic();
        WaPrivacyFileJournal.Snapshot result;
        try { result=journal.snapshot(); } catch (RuntimeException failure) { throw new Blocked("JOURNAL_UNAVAILABLE"); }
        if (result.sequence()!=sequence || !result.digest().equals(digest)) throw new Blocked("JOURNAL_HEAD_MISMATCH");
        return result;
    }
    private Map<UUID,WaPrivacyFileJournal.VerifiedEntry> validate(WaPrivacyService.Scope scope,WaPrivacyFileJournal.Snapshot snapshot) {
        if (snapshot.records().size()>10000) throw new Blocked("JOURNAL_LIMIT");
        var result=new HashMap<UUID,WaPrivacyFileJournal.VerifiedEntry>(); var sequences=new HashSet<Long>();
        for (var record : snapshot.records()) {
            var entry=record.entry();
            if (!entry.environment().equals(scope.environment()) || !entry.instanceId().equals(scope.instanceId()))
                throw new Blocked("JOURNAL_SCOPE_MISMATCH");
            if (result.put(entry.subjectId(),record)!=null || !sequences.add(entry.databaseSequence()))
                throw new Blocked("JOURNAL_IDENTITY_CONFLICT");
            if (dates(entry).closed().isAfter(clock.now())) throw new Blocked("FUTURE_CLOSURE");
        }
        return result;
    }
    private Applied apply(WaPrivacyService.Scope scope,WaPrivacyFileJournal.VerifiedEntry record) {
        var entry=record.entry(); Dates dates=dates(entry);
        var profiles=jdbc.queryForList("""
            SELECT p.status,p.closed_at,p.privacy_erased_at FROM wa.profile p JOIN wa.identity i ON i.id=p.identity_id
            JOIN wa.test_cohort c ON c.id=i.test_cohort_id WHERE p.identity_id=? AND c.environment=?
              AND (?=false OR c.synthetic) FOR UPDATE OF p
            """,entry.subjectId(),scope.environment(),policy.syntheticOnly());
        if (profiles.size()!=1) throw new Blocked("SUBJECT_OUT_OF_SCOPE");
        var existing=jdbc.queryForList("SELECT state FROM wa.privacy_erasure_ledger WHERE subject_id=? FOR UPDATE",entry.subjectId());
        if (!existing.isEmpty() && !ledger.entry(entry.subjectId()).equals(entry)) throw new Blocked("LEDGER_CONFLICT");
        if (subjects.active(entry.subjectId())) throw new Blocked("ACTIVE_REFERENCE");
        if (!existing.isEmpty() && "PURGED".equals(existing.get(0).get("state"))) {
            ledger.importIntent(entry,dates.closed());
            if (!ledger.acknowledge(entry,record.acknowledgement(),clock.now())) throw new Blocked("ACK_CONFLICT");
            return new Applied(false,false); // No alias or personal payload is reconstructed after purge.
        }
        if (!subjects.lock(scope,entry.subjectId())) throw new Blocked("SUBJECT_OUT_OF_SCOPE");
        if (!subjects.lockCopyOwners(entry.subjectId())) throw new Blocked("COPY_WRITER_BUSY");
        subjects.close(scope,entry.subjectId(),dates.closed());
        var subject=jdbc.queryForMap("SELECT environment,instance_id,closed_at,erase_after FROM wa.privacy_subject WHERE subject_id=?",entry.subjectId());
        if (!scope.environment().equals(subject.get("environment")) || !scope.instanceId().equals(subject.get("instance_id"))
            || !dates.closed().equals(instant(subject.get("closed_at"))) || !dates.after().equals(instant(subject.get("erase_after")))
            || !dates.closed().equals(jdbc.queryForObject("SELECT closed_at FROM wa.profile WHERE identity_id=?",Timestamp.class,entry.subjectId()).toInstant()))
            throw new Blocked("CLOSURE_CONFLICT");
        boolean imported=ledger.importIntent(entry,dates.closed());
        boolean acknowledged=existing.isEmpty() || "INTENT".equals(existing.get(0).get("state"));
        if (!ledger.acknowledge(entry,record.acknowledgement(),clock.now())) throw new Blocked("ACK_CONFLICT");
        return new Applied(imported,acknowledged);
    }
    /** This gate checks every journal entry AND every scoped DB ledger row, independently of afterSequence. */
    private List<String> pending(WaPrivacyService.Scope scope,Map<UUID,WaPrivacyFileJournal.VerifiedEntry> expected) {
        var pending=new HashSet<String>();
        var rows=jdbc.queryForList("""
            SELECT l.*,p.status profile_status,p.closed_at profile_closed,p.privacy_erased_at profile_erased,
              s.environment subject_environment,s.instance_id subject_instance,s.closed_at subject_closed,s.erase_after,s.erased_at,
              c.environment cohort_environment,c.synthetic,wa.privacy_has_active_reference(l.subject_id) active,
              EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=l.subject_id AND a.instance_id=?) own_alias,
              EXISTS(SELECT 1 FROM wa.identity_alias a WHERE a.identity_id=l.subject_id AND a.instance_id<>?) other_alias
            FROM wa.privacy_erasure_ledger l LEFT JOIN wa.profile p ON p.identity_id=l.subject_id
            LEFT JOIN wa.privacy_subject s ON s.subject_id=l.subject_id LEFT JOIN wa.identity i ON i.id=l.subject_id
            LEFT JOIN wa.test_cohort c ON c.id=i.test_cohort_id
            WHERE l.environment=? AND l.instance_id=? ORDER BY l.sequence LIMIT 10001
            """,scope.instanceId(),scope.instanceId(),scope.environment(),scope.instanceId());
        if (rows.size()!=expected.size()) pending.add("LEDGER_JOURNAL_DIVERGENCE");
        for (var row : rows) {
            var record=expected.get((UUID)row.get("subject_id"));
            if (record==null) { pending.add("LEDGER_JOURNAL_DIVERGENCE"); continue; }
            var entry=record.entry(); var ack=record.acknowledgement(); Dates dates=dates(entry);
            if (!ledger.entry(entry.subjectId()).equals(entry) || !ack.chainDigest().equals(row.get("journal_digest"))
                || !(row.get("journal_sequence") instanceof Number sequence) || sequence.longValue()!=ack.journalSequence()
                || !("JOURNALED".equals(row.get("state")) || "PURGED".equals(row.get("state")))) pending.add("ACK_CONFLICT");
            if (!scope.environment().equals(row.get("cohort_environment")) || policy.syntheticOnly() && !Boolean.TRUE.equals(row.get("synthetic")))
                pending.add("SUBJECT_OUT_OF_SCOPE");
            if (!"CLOSED".equals(row.get("profile_status")) || !dates.closed().equals(instant(row.get("profile_closed")))
                || !scope.environment().equals(row.get("subject_environment")) || !scope.instanceId().equals(row.get("subject_instance"))
                || !dates.closed().equals(instant(row.get("subject_closed"))) || !dates.after().equals(instant(row.get("erase_after"))))
                pending.add("CLOSURE_CONFLICT");
            if (Boolean.TRUE.equals(row.get("active"))) pending.add("ACTIVE_REFERENCE");
            if (!"PURGED".equals(row.get("state")) && (!Boolean.TRUE.equals(row.get("own_alias")) || Boolean.TRUE.equals(row.get("other_alias"))))
                pending.add("SUBJECT_OUT_OF_SCOPE");
            if (!dates.after().isAfter(clock.now()) && (!"PURGED".equals(row.get("state"))
                || row.get("erased_at")==null || row.get("profile_erased")==null)) pending.add("RETENTION_PENDING");
        }
        return new ArrayList<>(pending.stream().sorted().toList());
    }
    private <T> T authorized(WaPrivacyService.Scope scope,Supplier<T> work) {
        return transaction.execute(tx->{
            guard(scope); T result=work.get(); guard(scope); return result;
        });
    }
    private void guard(WaPrivacyService.Scope scope) {
        traffic();
        try { authority.accept(scope); } catch (RuntimeException failure) { throw new Blocked("AUTHORITY_REJECTED"); }
        traffic();
    }
    private void traffic() { if (!trafficBlocked.getAsBoolean()) throw new Blocked("TRAFFIC_NOT_BLOCKED"); }
    private void ownTransaction() {
        if (TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
    }
    private static Dates dates(WaPrivacyJournal.Entry entry) {
        var payload=WaJson.object(entry.canonicalPayload());
        return new Dates(OffsetDateTime.parse(payload.path("closedAt").asText()).toInstant(),
            OffsetDateTime.parse(payload.path("eraseAfter").asText()).toInstant());
    }
    private static Instant instant(Object value) { return value instanceof Timestamp time ? time.toInstant() : null; }
}
