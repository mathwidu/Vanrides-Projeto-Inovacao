package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Private job port. The credential owns scope; callers must bind Position into their opaque job cursor. */
public final class WaPrivacyTerminalMaintenance {
    public record Position(UUID credentialId, String environment, String instanceId, Instant asOf,
                           String after, boolean dryRun, boolean syntheticOnly) { }
    public record Result(String status, int examined, int erased, int deleted, int deferred,
                         boolean complete, Position next) { }
    private record Credential(WaPrivacyService.Scope scope, Instant expires) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final boolean dryRun;

    public WaPrivacyTerminalMaintenance(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                                       WaPrivacyPolicy policy) { this(jdbc, manager, clock, policy, true); }
    public WaPrivacyTerminalMaintenance(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                                       WaPrivacyPolicy policy, boolean dryRun) {
        this.jdbc = jdbc; this.transaction = new TransactionTemplate(manager); this.clock = clock;
        this.policy = policy; this.dryRun = dryRun;
    }
    public Result tick(WaPrincipal actor, int limit, Position cursor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        if (actor == null || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId()==null || actor.revoked()) throw WaProblem.denied();
        if (limit<1 || limit>100) throw WaProblem.invalid();
        if (!policy.enabled()) return new Result("DISABLED",0,0,0,0,false,null);
        return transaction.execute(tx -> {
            Credential grant = credential(actor);
            Instant asOf = cursor == null ? clock.now() : cursor.asOf();
            String after = cursor == null ? "" : cursor.after();
            if (cursor != null && (!actor.credentialId().equals(cursor.credentialId())
                || !grant.scope().environment().equals(cursor.environment()) || !grant.scope().instanceId().equals(cursor.instanceId())
                || cursor.dryRun()!=dryRun || cursor.syntheticOnly()!=policy.syntheticOnly()
                || asOf==null || asOf.isAfter(clock.now()) || !asOf.plusSeconds(300).isAfter(clock.now())
                || after==null || !after.matches("[IO]:[0-9]{20}:[0-9a-f-]{36}|"))) throw WaProblem.invalid();
            var page = new WaPrivacyTerminalContent(jdbc).sweep(grant.scope(),asOf,clock.now(),policy.syntheticOnly(),after,limit,dryRun);
            if (!grant.expires().isAfter(clock.now())) throw WaProblem.unauthorized();
            Position next = page.complete() || dryRun && page.endOfPrefix() ? null : new Position(actor.credentialId(),grant.scope().environment(),
                grant.scope().instanceId(),asOf,page.endOfPrefix() && !dryRun ? "" : page.after(),dryRun,policy.syntheticOnly());
            return new Result(dryRun ? "DRY_RUN" : page.deferred()>0 ? "DEFERRED" : "APPLIED",
                page.examined(),page.erased(),0,page.deferred(),page.complete(),next);
        });
    }
    private Credential credential(WaPrincipal actor) {
        var rows = jdbc.query("""
            SELECT environment,allowed_instance_id,expires_at FROM wa.service_credential
            WHERE id=? AND principal='SYSTEM_JOB' AND allowed_instance_id IS NOT NULL
              AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """, (rs,row) -> new Credential(new WaPrivacyService.Scope(rs.getString(1),rs.getString(2)),rs.getTimestamp(3).toInstant()),
            actor.credentialId(),Timestamp.from(clock.now()));
        if (rows.size()!=1 || !rows.get(0).scope().instanceId().equals(actor.instanceId())) throw WaProblem.unauthorized();
        return rows.get(0);
    }
}
