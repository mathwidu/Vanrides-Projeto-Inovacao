package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;
import java.util.function.Supplier;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.transaction.support.TransactionSynchronizationManager;

/** Integration port only: no scheduler, HTTP route or enabled bean is registered by this increment. */
public final class WaPrivacyService {
    public record Scope(String environment, String instanceId) {
        public Scope {
            if (environment == null || environment.isBlank() || instanceId == null || instanceId.isBlank())
                throw new IllegalArgumentException("PRIVACY_SCOPE_REQUIRED");
        }
    }
    public record Result(String status, Map<String, Integer> erased, List<String> deferred) {
        static Result status(String status) { return new Result(status, Map.of(), List.of()); }
    }
    public record Candidate(UUID subjectId,Instant eraseAfter) { }
    private static final String CANDIDATE_BASE="""
        FROM wa.privacy_subject subject JOIN wa.identity person ON person.id=subject.subject_id
        JOIN wa.test_cohort cohort ON cohort.id=person.test_cohort_id
        WHERE subject.environment=? AND subject.instance_id=? AND subject.erased_at IS NULL
          AND subject.erase_after<=? AND cohort.environment=? AND (?=false OR cohort.synthetic)
        """;
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final WaPrivacyJournal journal;
    private final WaPrivacySubjects subjects;
    private final WaPrivacyLedger ledger;
    private final Consumer<Scope> authority;

    public WaPrivacyService(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                            WaPrivacyPolicy policy, WaPrivacyJournal journal) {
        this(jdbc, manager, clock, policy, journal, scope -> { });
    }
    /** Runtime callers supply an authority fence; the five-argument constructor is laboratory-only. */
    public WaPrivacyService(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                            WaPrivacyPolicy policy, WaPrivacyJournal journal, Consumer<Scope> authority) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager); this.clock = clock;
        this.policy = policy; this.journal = journal; subjects = new WaPrivacySubjects(jdbc, policy);
        ledger = new WaPrivacyLedger(jdbc);
        this.authority = java.util.Objects.requireNonNull(authority);
    }

    public Result close(Scope scope, UUID subject) {
        requireOwnTransactionBoundary();
        if (!policy.enabled()) return Result.status("DISABLED");
        Result result = authorized(scope, () -> {
            if (!subjects.lock(scope, subject)) return Result.status("OUT_OF_SCOPE");
            if (!subjects.lockCopyOwners(subject)) return Result.status("COPY_WRITER_BUSY");
            if (subjects.active(subject)) return Result.status("ACTIVE_REFERENCE");
            var old = jdbc.queryForList("SELECT environment,instance_id FROM wa.privacy_subject WHERE subject_id=?", subject);
            if (!old.isEmpty() && (!scope.environment().equals(old.get(0).get("environment"))
                || !scope.instanceId().equals(old.get(0).get("instance_id")))) return Result.status("OUT_OF_SCOPE");
            subjects.close(scope, subject, clock.now());
            ledger.intent(subject, clock.now());
            return Result.status("CLOSED");
        });
        return result.status().equals("CLOSED") && !journal(subject) ? Result.status("JOURNAL_PENDING") : result;
    }

    public List<UUID> candidates(Scope scope, int limit) {
        requireOwnTransactionBoundary();
        if (limit < 1 || limit > 100) throw new IllegalArgumentException("PRIVACY_BATCH_LIMIT");
        if (!policy.enabled()) return List.of();
        return authorized(scope, () -> jdbc.query("""
            SELECT subject_id FROM wa.privacy_subject WHERE environment=? AND instance_id=?
              AND erased_at IS NULL AND erase_after<=? ORDER BY erase_after,subject_id LIMIT ?
            """, (rs, row) -> rs.getObject(1, UUID.class), scope.environment(), scope.instanceId(), Timestamp.from(clock.now()), limit));
    }

    /** Server-cutoff keyset page. Permission predicates precede LIMIT; semantic deferrals stay in this base. */
    public List<Candidate> candidates(Scope scope,int limit,Instant asOf,Instant afterEraseAfter,UUID afterSubjectId){
        requireOwnTransactionBoundary();candidateArguments(scope,limit,asOf,afterEraseAfter,afterSubjectId);
        if(!policy.enabled())return List.of();
        Timestamp after=afterEraseAfter==null?null:Timestamp.from(afterEraseAfter);
        return authorized(scope,()->jdbc.query("SELECT subject.subject_id,subject.erase_after "+CANDIDATE_BASE+"""
            AND (?::timestamptz IS NULL OR (subject.erase_after,subject.subject_id)>(?::timestamptz,?::uuid))
            ORDER BY subject.erase_after,subject.subject_id LIMIT ?
            """,(rs,row)->new Candidate(rs.getObject(1,UUID.class),rs.getTimestamp(2).toInstant()),
            scope.environment(),scope.instanceId(),Timestamp.from(asOf),scope.environment(),policy.syntheticOnly(),after,after,afterSubjectId,limit));
    }

    /** Independent completeness: never apply the continuation tuple or hide a deferred subject. */
    public boolean candidatesComplete(Scope scope,Instant asOf){
        requireOwnTransactionBoundary();candidateArguments(scope,1,asOf,null,null);
        if(!policy.enabled())return false;
        return authorized(scope,()->Boolean.TRUE.equals(jdbc.queryForObject("SELECT NOT EXISTS(SELECT 1 "+CANDIDATE_BASE+")",Boolean.class,
            scope.environment(),scope.instanceId(),Timestamp.from(asOf),scope.environment(),policy.syntheticOnly())));
    }

    private void candidateArguments(Scope scope,int limit,Instant asOf,Instant afterEraseAfter,UUID afterSubjectId){
        if(scope==null || limit<1 || limit>100 || asOf==null || asOf.isAfter(clock.now())
            || (afterEraseAfter==null)!=(afterSubjectId==null) || afterEraseAfter!=null && afterEraseAfter.isAfter(asOf))
            throw new IllegalArgumentException("PRIVACY_PAGE_ARGUMENTS");
    }

    public Result erase(Scope scope, UUID subject) {
        requireOwnTransactionBoundary();
        if (!policy.enabled()) return Result.status("DISABLED");
        var eligibility = authorized(scope, () -> {
            if (!subjects.lock(scope, subject)) return "OUT_OF_SCOPE";
            if (subjects.active(subject)) return "ACTIVE_REFERENCE";
            var rows = jdbc.queryForList("""
                SELECT erase_after,erased_at FROM wa.privacy_subject
                WHERE subject_id=? AND environment=? AND instance_id=? FOR UPDATE
                """, subject, scope.environment(), scope.instanceId());
            if (rows.isEmpty()) return "NOT_CLOSED";
            if (rows.get(0).get("erased_at") != null) return "ALREADY_ERASED";
            return ((Timestamp) rows.get(0).get("erase_after")).toInstant().isAfter(clock.now()) ? "NOT_DUE" : "DUE";
        });
        if (!"DUE".equals(eligibility)) return Result.status(eligibility);
        if (!journal(subject)) return Result.status("JOURNAL_PENDING");
        return authorized(scope, () -> {
            if (!subjects.lock(scope, subject) || subjects.active(subject)) return Result.status("ACTIVE_REFERENCE");
            if (!subjects.lockCopyOwners(subject)) return Result.status("COPY_WRITER_BUSY");
            if (!new WaPrivacyTerminalContent(jdbc).complete(scope, clock.now(), policy.syntheticOnly()))
                return new Result("TERMINAL_CONTENT_PENDING", Map.of(), List.of("TERMINAL_CONTENT_7D"));
            var row = jdbc.queryForMap("SELECT * FROM wa.privacy_erasure_ledger WHERE subject_id=? FOR UPDATE", subject);
            if (row.get("state").equals("PURGED")) return Result.status("ALREADY_ERASED");
            if (!row.get("state").equals("JOURNALED")) return Result.status("JOURNAL_PENDING");
            jdbc.update("UPDATE wa.privacy_erasure_ledger SET purge_started_at=? WHERE subject_id=? AND purge_started_at IS NULL",
                Timestamp.from(clock.now()), subject);
            Map<String, Integer> counts = new WaPrivacyCopies(jdbc).erase(subject, clock.now());
            jdbc.update("UPDATE wa.privacy_subject SET erased_at=? WHERE subject_id=?", Timestamp.from(clock.now()), subject);
            jdbc.update("UPDATE wa.privacy_erasure_ledger SET state='PURGED',purged_at=? WHERE subject_id=?", Timestamp.from(clock.now()), subject);
            return new Result("ERASED", counts, List.of("GENERAL_SEVEN_DAY_TECHNICAL_RETENTION", "EXPIRED_CREDENTIAL_PAYLOADS",
                "NINETY_DAY_OPERATIONAL_FACTS", "BACKUPS_AND_EXTERNAL_PROVIDERS"));
        });
    }

    private void requireOwnTransactionBoundary() {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
    }

    private <T> T authorized(Scope scope, Supplier<T> work) {
        return transaction.execute(tx -> {
            authority.accept(scope);
            T result = work.get();
            authority.accept(scope);
            return result;
        });
    }

    private boolean journal(UUID subject) {
        try {
            WaPrivacyJournal.Entry entry = ledger.entry(subject);
            WaPrivacyJournal.Ack acknowledgement = journal.append(entry);
            return journal.verify(entry, acknowledgement) && Boolean.TRUE.equals(authorized(
                new Scope(entry.environment(),entry.instanceId()), () -> ledger.acknowledge(entry, acknowledgement, clock.now())));
        } catch (RuntimeException unavailable) {
            // No external error body, subject/contact data or conversation is logged.
            return false;
        }
    }
}
