package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.sql.Timestamp;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Physical-row budget, independent completeness and no runtime registration. */
public final class WaPrivacyHistoryProjection {
    public enum Family {
        PIN("00"), STOP("01"), APPROACH_PROGRESS("02"), FINISH_PROGRESS("03"), RUN("04"), OCCURRENCE("05");
        final String key; Family(String key){this.key=key;}
        static Family from(String key){for(var f:values())if(f.key.equals(key))return f;throw new IllegalStateException("HISTORY_FAMILY");}
    }
    public record Position(UUID credentialId,String environment,String instanceId,Instant asOf,Family afterFamily,
                           UUID afterOccurrence,UUID afterParent,UUID afterId,boolean dryRun,boolean syntheticOnly) { }
    public record Deferral(Family family,UUID id,String reason) { }
    public record Result(String status,int examined,int erased,int deleted,int deferred,int wouldChange,
                         boolean complete,Position next,List<Deferral> pending) { }
    private record Unit(Family family,UUID occurrence,UUID parent,UUID id,String reason) { }
    private record Grant(WaPrivacyService.Scope scope,Instant expires) { }
    private static final UUID ZERO=new UUID(0,0);
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final Consumer<WaPrivacyService.Scope> authority;
    private final boolean dryRun;
    public WaPrivacyHistoryProjection(JdbcTemplate jdbc,PlatformTransactionManager manager,WaClock clock,
                                     WaPrivacyPolicy policy,Consumer<WaPrivacyService.Scope> authority) {
        this(jdbc,manager,clock,policy,authority,true);
    }
    public WaPrivacyHistoryProjection(JdbcTemplate jdbc,PlatformTransactionManager manager,WaClock clock,
                                     WaPrivacyPolicy policy,Consumer<WaPrivacyService.Scope> authority,boolean dryRun) {
        this.jdbc=jdbc;transaction=new TransactionTemplate(manager);this.clock=clock;this.policy=policy;
        this.authority=java.util.Objects.requireNonNull(authority);this.dryRun=dryRun;
    }
    public Result tick(WaPrincipal actor,int limit,Position cursor) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        if(actor==null || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId()==null || actor.revoked())throw WaProblem.denied();
        if(limit<1 || limit>100)throw WaProblem.invalid();
        if(!policy.enabled())return new Result("DISABLED",0,0,0,0,0,false,null,List.of());
        return transaction.execute(tx->sweep(actor,limit,cursor));
    }
    private Result sweep(WaPrincipal actor,int limit,Position cursor) {
        Instant database=jdbc.queryForObject("SELECT transaction_timestamp()",Timestamp.class).toInstant();
        Instant now=(clock.now().isBefore(database)?clock.now():database).truncatedTo(ChronoUnit.MICROS);
        Grant grant=grant(actor,now);authority.accept(grant.scope());validate(actor,grant,cursor,now);
        Instant asOf=cursor==null?now:cursor.asOf();
        var work=jdbc.query(WaPrivacyHistoryProjectionQueries.WORK+"""
            SELECT family,occurrence_id,parent_id,id,reason FROM work
            WHERE (family,occurrence_id,parent_id,id)>(?::text,?::uuid,?::uuid,?::uuid)
            ORDER BY family,occurrence_id,parent_id,id LIMIT ?
            """,(rs,row)->new Unit(Family.from(rs.getString(1)),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getString(5)),
            append(parameters(grant.scope(),asOf),cursor==null||cursor.afterFamily()==null?"":cursor.afterFamily().key,
                cursor==null||cursor.afterOccurrence()==null?ZERO:cursor.afterOccurrence(),cursor==null||cursor.afterParent()==null?ZERO:cursor.afterParent(),
                cursor==null||cursor.afterId()==null?ZERO:cursor.afterId(),limit));
        Position prefix=cursor==null?position(actor,grant.scope(),asOf,null):cursor;
        int examined=0,erased=0,deleted=0,would=0;var pending=new ArrayList<Deferral>();boolean blocked=false;
        for(var unit:work){
            examined++;
            if(!fence(unit)) {pending.add(new Deferral(unit.family(),unit.id(),"COPY_WRITER_BUSY"));blocked=true;break;}
            var current=jdbc.query(WaPrivacyHistoryProjectionQueries.WORK+
                "SELECT reason FROM work WHERE family=? AND occurrence_id=? AND parent_id=? AND id=?",(rs,row)->rs.getString(1),
                append(parameters(grant.scope(),asOf),unit.family().key,unit.occurrence(),unit.parent(),unit.id()));
            if(current.isEmpty()){prefix=position(actor,grant.scope(),asOf,unit);continue;}
            if(current.get(0)!=null){
                pending.add(new Deferral(unit.family(),unit.id(),current.get(0)));
                prefix=position(actor,grant.scope(),asOf,unit);continue;
            }
            if(dryRun)would++;
            else {int changed=apply(unit,asOf);if(unit.family()==Family.APPROACH_PROGRESS||unit.family()==Family.FINISH_PROGRESS)deleted+=changed;else if(unit.family()!=Family.PIN)erased+=changed;}
            prefix=position(actor,grant.scope(),asOf,unit);
        }
        boolean complete=!Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyHistoryProjectionQueries.WORK+
            "SELECT EXISTS(SELECT 1 FROM work)",Boolean.class,parameters(grant.scope(),asOf)));
        authority.accept(grant.scope());
        Instant end=jdbc.queryForObject("SELECT clock_timestamp()",Timestamp.class).toInstant();
        if(!grant.expires().isAfter(clock.now().isBefore(end)?clock.now():end))throw WaProblem.unauthorized();
        Position next=complete || !blocked&&work.size()<limit?null:prefix;
        return new Result(dryRun?"DRY_RUN":pending.isEmpty()?"APPLIED":"DEFERRED",examined,erased,deleted,pending.size(),would,complete,next,List.copyOf(pending));
    }
    private boolean fence(Unit unit) {
        String owners="SELECT driver_id id FROM wa.routine_occurrence WHERE id=? UNION SELECT passenger_id FROM wa.run_stop WHERE run_id IN (SELECT id FROM wa.operation_run WHERE occurrence_id=?)";
        int count=jdbc.queryForObject("SELECT count(*) FROM wa.profile WHERE identity_id IN ("+owners+")",Integer.class,unit.occurrence(),unit.occurrence());
        if(jdbc.queryForList("SELECT identity_id FROM wa.profile WHERE identity_id IN ("+owners+") ORDER BY identity_id FOR SHARE SKIP LOCKED",unit.occurrence(),unit.occurrence()).size()!=count)return false;
        if(jdbc.queryForList("SELECT id FROM wa.routine_occurrence WHERE id=? FOR UPDATE SKIP LOCKED",unit.occurrence()).isEmpty())return false;
        var run=jdbc.queryForList("SELECT id FROM wa.operation_run WHERE occurrence_id=?",unit.occurrence());
        if(!run.isEmpty()&&jdbc.queryForList("SELECT id FROM wa.operation_run WHERE occurrence_id=? FOR UPDATE SKIP LOCKED",unit.occurrence()).isEmpty())return false;
        return switch(unit.family()){
            case PIN,OCCURRENCE,RUN->true;
            case STOP->!jdbc.queryForList("SELECT id FROM wa.run_stop WHERE id=? FOR UPDATE SKIP LOCKED",unit.id()).isEmpty();
            case APPROACH_PROGRESS->!jdbc.queryForList("SELECT run_id FROM wa.approach_progress WHERE run_id=? AND physical_stop_key=? FOR UPDATE SKIP LOCKED",unit.parent(),unit.id()).isEmpty();
            case FINISH_PROGRESS->!jdbc.queryForList("SELECT run_id FROM wa.run_finish_progress WHERE run_id=? FOR UPDATE SKIP LOCKED",unit.id()).isEmpty();
        };
    }
    private int apply(Unit u,Instant asOf) {
        return switch(u.family()){
            case PIN->jdbc.update("""
                UPDATE wa.routine_occurrence o SET history_environment=s.environment,history_instance_id=s.instance_id,
                  history_origin_kind=s.kind,history_origin_id=s.origin_id,history_origin_bound_at=transaction_timestamp()
                FROM (SELECT * FROM wa.privacy_history_sources(?) ORDER BY kind,origin_id LIMIT 1) s WHERE o.id=?
                """,u.occurrence(),u.occurrence());
            case STOP->jdbc.update("""
                UPDATE wa.run_stop SET latitude_snapshot=NULL,longitude_snapshot=NULL,reference_snapshot=NULL,
                  attendance_snapshot=NULL,state=NULL,canceled_at=NULL,history_erased_at=? WHERE id=?
                """,Timestamp.from(asOf),u.id());
            case APPROACH_PROGRESS->jdbc.update("DELETE FROM wa.approach_progress WHERE run_id=? AND physical_stop_key=?",u.parent(),u.id());
            case FINISH_PROGRESS->jdbc.update("DELETE FROM wa.run_finish_progress WHERE run_id=?",u.id());
            case RUN->jdbc.update("UPDATE wa.operation_run SET snapshot_json=wa.privacy_history_run_snapshot(snapshot_json,snapshot_at),completion_reason=NULL,history_erased_at=? WHERE id=?",Timestamp.from(asOf),u.id());
            case OCCURRENCE->jdbc.update("UPDATE wa.routine_occurrence SET schedule_snapshot=CASE WHEN schedule_snapshot IS NULL THEN NULL ELSE '{}'::jsonb END,history_erased_at=? WHERE id=?",Timestamp.from(asOf),u.id());
        };
    }
    private Grant grant(WaPrincipal actor,Instant now){
        var grants=jdbc.query("SELECT environment,allowed_instance_id,expires_at FROM wa.service_credential WHERE id=? AND principal='SYSTEM_JOB' AND revoked_at IS NULL AND expires_at>? AND allowed_instance_id IS NOT NULL FOR SHARE",
            (rs,row)->new Grant(new WaPrivacyService.Scope(rs.getString(1),rs.getString(2)),rs.getTimestamp(3).toInstant()),actor.credentialId(),Timestamp.from(now));
        if(grants.size()!=1||!grants.get(0).scope().instanceId().equals(actor.instanceId()))throw WaProblem.unauthorized();return grants.get(0);
    }
    private void validate(WaPrincipal actor,Grant grant,Position p,Instant now){
        if(p==null)return;boolean empty=p.afterFamily()==null&&p.afterOccurrence()==null&&p.afterParent()==null&&p.afterId()==null;
        boolean full=p.afterFamily()!=null&&p.afterOccurrence()!=null&&p.afterParent()!=null&&p.afterId()!=null;
        if(!actor.credentialId().equals(p.credentialId())||!grant.scope().environment().equals(p.environment())||!grant.scope().instanceId().equals(p.instanceId())
            ||p.dryRun()!=dryRun||p.syntheticOnly()!=policy.syntheticOnly()||p.asOf()==null||p.asOf().isAfter(now)||!(empty||full))throw WaProblem.invalid();
    }
    private Position position(WaPrincipal actor,WaPrivacyService.Scope scope,Instant asOf,Unit u){return new Position(actor.credentialId(),scope.environment(),scope.instanceId(),asOf,u==null?null:u.family(),u==null?null:u.occurrence(),u==null?null:u.parent(),u==null?null:u.id(),dryRun,policy.syntheticOnly());}
    private Object[] parameters(WaPrivacyService.Scope scope,Instant asOf){return new Object[]{scope.environment(),scope.instanceId(),policy.syntheticOnly(),Timestamp.from(asOf)};}
    private static Object[] append(Object[] a,Object... b){Object[] r=java.util.Arrays.copyOf(a,a.length+b.length);System.arraycopy(b,0,r,a.length,b.length);return r;}
}
