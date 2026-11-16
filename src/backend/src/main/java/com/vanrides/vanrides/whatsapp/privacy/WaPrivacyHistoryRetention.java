package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Private, opt-in port for safe 90-day leaves. Historical graphs remain explicit pending work. */
public final class WaPrivacyHistoryRetention {
    public enum Family {
        QUESTION("01", "attendance_question"), ATTENDANCE("02", "attendance"),
        ROSTER("03", "occurrence_roster_override"), SUPPORT("04", "support_case"),
        AUDIT("05", "audit_event"), RUN_GRAPH("06", "operation_run"), OCCURRENCE_GRAPH("07", "routine_occurrence");
        final String key, table;
        Family(String key, String table) { this.key=key; this.table=table; }
        static Family from(String key) {
            for (Family family : values()) if (family.key.equals(key)) return family;
            throw new IllegalStateException("PRIVACY_HISTORY_UNKNOWN_FAMILY");
        }
    }
    public record Position(UUID credentialId, String environment, String instanceId, Instant asOf,
                           Family afterFamily, Instant afterAt, UUID afterId, boolean dryRun, boolean syntheticOnly) { }
    public record Deferral(Family family, UUID id, String reason) { }
    public record Result(String status, int examined, int erased, int deleted, int deferred, int wouldDelete,
                         boolean complete, Position next, List<Deferral> pending) { }
    private record Grant(WaPrivacyService.Scope scope, Instant expires) { }
    private record Unit(Family family, UUID id, Instant at, String reason) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final boolean dryRun;

    public WaPrivacyHistoryRetention(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                                     WaPrivacyPolicy policy) { this(jdbc,manager,clock,policy,true); }
    public WaPrivacyHistoryRetention(JdbcTemplate jdbc, PlatformTransactionManager manager, WaClock clock,
                                     WaPrivacyPolicy policy, boolean dryRun) {
        this.jdbc=jdbc; this.transaction=new TransactionTemplate(manager); this.clock=clock;
        this.policy=policy; this.dryRun=dryRun;
    }
    /** Position must be carried inside the caller's authenticated opaque HISTORY_90D cursor. */
    public Result tick(WaPrincipal actor, int limit, Position cursor) {
        if (TransactionSynchronizationManager.isActualTransactionActive()
            || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        if (actor==null || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId()==null || actor.revoked())
            throw WaProblem.denied();
        if (limit<1 || limit>100) throw WaProblem.invalid();
        if (!policy.enabled()) return new Result("DISABLED",0,0,0,0,0,false,null,List.of());
        return transaction.execute(tx -> sweep(actor,limit,cursor));
    }
    private Result sweep(WaPrincipal actor, int limit, Position cursor) {
        Grant grant=grant(actor);
        Instant now=clock.now(), asOf=cursor==null ? now : cursor.asOf();
        validate(actor,grant,cursor,now);
        List<Unit> work=jdbc.query(WaPrivacyHistoryQueries.CANDIDATES + """
            SELECT family,id,fact_at,reason FROM eligible
            WHERE (family,fact_at,id)>(?::text,?::timestamptz,?::uuid) ORDER BY family,fact_at,id LIMIT ?
            """,(rs,row)->new Unit(Family.from(rs.getString(1)),rs.getObject(2,UUID.class),
                rs.getTimestamp(3).toInstant(),rs.getString(4)),append(parameters(grant.scope(),asOf),
                cursor==null || cursor.afterFamily()==null ? "" : cursor.afterFamily().key,
                Timestamp.from(cursor==null || cursor.afterAt()==null ? Instant.EPOCH : cursor.afterAt()),
                cursor==null || cursor.afterId()==null ? new UUID(0,0) : cursor.afterId(),limit));
        int examined=0,deleted=0,wouldDelete=0;
        var pending=new ArrayList<Deferral>();
        Position prefix=position(actor,grant.scope(),asOf,cursor==null ? null : cursor.afterFamily(),
            cursor==null ? null : cursor.afterAt(),cursor==null ? null : cursor.afterId());
        boolean blocked=false;
        for (Unit unit : work) {
            examined++;
            var locked=jdbc.queryForList("SELECT id FROM wa."+unit.family().table+" WHERE id=? FOR UPDATE SKIP LOCKED",unit.id());
            if (locked.isEmpty()) {
                if (!exists(grant.scope(),asOf,unit)) { prefix=position(actor,grant.scope(),asOf,unit.family(),unit.at(),unit.id()); continue; }
                pending.add(new Deferral(unit.family(),unit.id(),"ROW_BUSY")); blocked=true; break;
            }
            var current=jdbc.query(WaPrivacyHistoryQueries.CANDIDATES
                + "SELECT reason FROM eligible WHERE family=? AND id=?",(rs,row)->rs.getString(1),
                append(parameters(grant.scope(),asOf),unit.family().key,unit.id()));
            if (!current.isEmpty() && current.get(0)!=null) {
                pending.add(new Deferral(unit.family(),unit.id(),current.get(0)));
                prefix=position(actor,grant.scope(),asOf,unit.family(),unit.at(),unit.id()); continue;
            }
            if (!current.isEmpty()) {
                if (dryRun) wouldDelete++;
                else deleted+=jdbc.update("DELETE FROM wa."+unit.family().table+" WHERE id=?",unit.id());
            }
            prefix=position(actor,grant.scope(),asOf,unit.family(),unit.at(),unit.id());
        }
        boolean complete=!Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyHistoryQueries.CANDIDATES
            + "SELECT EXISTS(SELECT 1 FROM eligible)",Boolean.class,parameters(grant.scope(),asOf)));
        if (!grant.expires().isAfter(clock.now())) throw WaProblem.unauthorized();
        Position next=complete || dryRun && !blocked && work.size()<limit ? null : prefix;
        if (!complete && !dryRun && !blocked && work.size()<limit) next=position(actor,grant.scope(),asOf,null,null,null);
        return new Result(dryRun ? "DRY_RUN" : pending.isEmpty() ? "APPLIED" : "DEFERRED",examined,0,deleted,
            pending.size(),wouldDelete,complete,next,List.copyOf(pending));
    }
    private Grant grant(WaPrincipal actor) {
        var rows=jdbc.query("""
            SELECT environment,allowed_instance_id,expires_at FROM wa.service_credential
            WHERE id=? AND principal='SYSTEM_JOB' AND allowed_instance_id IS NOT NULL
              AND revoked_at IS NULL AND expires_at>? FOR SHARE
            """,(rs,row)->new Grant(new WaPrivacyService.Scope(rs.getString(1),rs.getString(2)),rs.getTimestamp(3).toInstant()),
            actor.credentialId(),Timestamp.from(clock.now()));
        if (rows.size()!=1 || !rows.get(0).scope().instanceId().equals(actor.instanceId())) throw WaProblem.unauthorized();
        return rows.get(0);
    }
    private void validate(WaPrincipal actor, Grant grant, Position cursor, Instant now) {
        if (cursor==null) return;
        boolean empty=cursor.afterFamily()==null && cursor.afterAt()==null && cursor.afterId()==null;
        boolean full=cursor.afterFamily()!=null && cursor.afterAt()!=null && cursor.afterId()!=null;
        if (!actor.credentialId().equals(cursor.credentialId()) || !grant.scope().environment().equals(cursor.environment())
            || !grant.scope().instanceId().equals(cursor.instanceId()) || cursor.dryRun()!=dryRun
            || cursor.syntheticOnly()!=policy.syntheticOnly() || cursor.asOf()==null || cursor.asOf().isAfter(now)
            || !cursor.asOf().plusSeconds(300).isAfter(now) || !(empty || full)) throw WaProblem.invalid();
    }
    private Position position(WaPrincipal actor, WaPrivacyService.Scope scope, Instant asOf, Family family, Instant at, UUID id) {
        return new Position(actor.credentialId(),scope.environment(),scope.instanceId(),asOf,family,at,id,dryRun,policy.syntheticOnly());
    }
    private boolean exists(WaPrivacyService.Scope scope, Instant asOf, Unit unit) {
        return Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyHistoryQueries.CANDIDATES
            + "SELECT EXISTS(SELECT 1 FROM eligible WHERE family=? AND id=?)",Boolean.class,
            append(parameters(scope,asOf),unit.family().key,unit.id())));
    }
    private Object[] parameters(WaPrivacyService.Scope scope, Instant asOf) {
        return new Object[]{scope.environment(),scope.instanceId(),policy.syntheticOnly(),Timestamp.from(asOf.minusSeconds(90L*86400))};
    }
    private static Object[] append(Object[] first, Object... rest) {
        Object[] all=java.util.Arrays.copyOf(first,first.length+rest.length);
        System.arraycopy(rest,0,all,first.length,rest.length); return all;
    }
}
