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

/** Disabled integration port. Each unit mutates at most one physical row; busy prefixes never advance. */
public final class WaPrivacyTechnicalRetention {
    public enum Family { SAMPLE, BATCH, CONTEXT, REPLAY, CIPHER, PAIRING, DEVICE }
    public record Position(UUID credentialId,String environment,String instanceId,Instant asOf,String after,
                           boolean dryRun,boolean syntheticOnly) { }
    public record Deferral(Family family,UUID id,String reason) { }
    public record Result(String status,int examined,int erased,int deleted,int deferred,int wouldChange,
                         boolean complete,Position next,List<Deferral> pending) { }
    private record Grant(String environment,String instance,Instant expires) { }
    private record Unit(Family family,UUID id,UUID parent,UUID run,UUID owner,String reason,String key) { }
    private final JdbcTemplate jdbc;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaPrivacyPolicy policy;
    private final boolean dryRun;
    public WaPrivacyTechnicalRetention(JdbcTemplate jdbc,PlatformTransactionManager manager,WaClock clock,WaPrivacyPolicy policy) {
        this(jdbc,manager,clock,policy,true);
    }
    public WaPrivacyTechnicalRetention(JdbcTemplate jdbc,PlatformTransactionManager manager,WaClock clock,WaPrivacyPolicy policy,boolean dryRun) {
        this.jdbc=jdbc;transaction=new TransactionTemplate(manager);this.clock=clock;this.policy=policy;this.dryRun=dryRun;
    }
    /** Position belongs inside the runtime's authenticated TECHNICAL_7D envelope. */
    public Result tick(WaPrincipal actor,int limit,Position cursor) {
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        if(actor==null || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId()==null || actor.revoked())throw WaProblem.denied();
        if(limit<1 || limit>100)throw WaProblem.invalid();
        if(!policy.enabled())return new Result("DISABLED",0,0,0,0,0,false,null,List.of());
        return transaction.execute(tx->{
            Grant grant=grant(actor);validate(actor,grant,cursor);
            Instant asOf=cursor==null?clock.now():cursor.asOf();String after=cursor==null?"":cursor.after();
            List<Unit> units=jdbc.query(WaPrivacyTechnicalQueries.CANDIDATES+"SELECT family,id,parent,run_id,owner,reason,work_key FROM eligible WHERE work_key>? ORDER BY work_key LIMIT ?",
                (rs,n)->new Unit(Family.valueOf(rs.getString(1)),rs.getObject(2,UUID.class),rs.getObject(3,UUID.class),rs.getObject(4,UUID.class),rs.getObject(5,UUID.class),rs.getString(6),rs.getString(7)),append(params(grant,asOf),after,limit));
            int examined=0,erased=0,deleted=0,would=0;var pending=new ArrayList<Deferral>();String prefix=after;boolean blocked=false;
            for(Unit unit:units){
                examined++;
                if(dryRun){
                    if(unit.reason()!=null){
                        pending.add(new Deferral(unit.family(),unit.id(),unit.reason()));
                        if(semantic(unit.reason())){prefix=unit.key();continue;}
                        blocked=true;break;
                    }
                    would++;prefix=unit.key();continue;
                }
                if(!lock(unit)){pending.add(new Deferral(unit.family(),unit.id(),"ROW_BUSY"));blocked=true;break;}
                List<String> reasons=jdbc.query(WaPrivacyTechnicalQueries.CANDIDATES+"SELECT reason FROM eligible WHERE work_key=?",
                    (rs,n)->rs.getString(1),append(params(grant,asOf),unit.key()));
                if(reasons.isEmpty()){prefix=unit.key();continue;}
                if(reasons.get(0)!=null){
                    String reason=reasons.get(0);pending.add(new Deferral(unit.family(),unit.id(),reason));
                    if(semantic(reason)){prefix=unit.key();continue;}
                    blocked=true;break;
                }
                int changed=change(unit);
                if(unit.family()==Family.SAMPLE || unit.family()==Family.BATCH || unit.family()==Family.PAIRING)deleted+=changed;else erased+=changed;
                prefix=unit.key();
            }
            boolean complete=!Boolean.TRUE.equals(jdbc.queryForObject(WaPrivacyTechnicalQueries.CANDIDATES+"SELECT EXISTS(SELECT 1 FROM eligible)",Boolean.class,params(grant,asOf)));
            if(!grant.expires().isAfter(clock.now()))throw WaProblem.unauthorized();
            Position next=complete || !blocked && units.size()<limit?null:position(actor,grant,asOf,prefix);
            return new Result(dryRun?"DRY_RUN":pending.isEmpty()?"APPLIED":"DEFERRED",examined,erased,deleted,pending.size(),would,complete,next,List.copyOf(pending));
        });
    }
    private static boolean semantic(String reason){
        return switch(reason){case "MULTI_INSTANCE","AMBIGUOUS_OWNER","ACTIVE_REFERENCE","CHILDREN_PENDING","REPLAY_PENDING"->true;default->false;};
    }
    private boolean lock(Unit unit){
        if(unit.run()!=null && !row("operation_run","id",unit.run()))return false;
        if(unit.parent()!=null && !row("tracking_session","id",unit.parent()))return false;
        // Ingress owns identity before inserting; resume owns profile before identity. Never wait on either.
        if(!row("identity","id",unit.owner()) || !row("profile","identity_id",unit.owner()))return false;
        return switch(unit.family()){
            case SAMPLE->row("tracking_sample_receipt","id",unit.id());
            case BATCH->!jdbc.queryForList("SELECT batch_id FROM wa.tracking_batch_receipt WHERE session_id=? AND batch_id=? FOR UPDATE SKIP LOCKED",unit.parent(),unit.id()).isEmpty();
            case CONTEXT->row("conversation_state","identity_id",unit.id());
            case REPLAY->row("operation_replay","id",unit.id());
            case CIPHER->row("message_outbox","id",unit.id());
            case PAIRING->row("pairing_challenge","id",unit.id());
            case DEVICE->row("driver_device","id",unit.id());
        };
    }
    private boolean row(String table,String key,UUID id){return !jdbc.queryForList("SELECT "+key+" FROM wa."+table+" WHERE "+key+"=? FOR UPDATE SKIP LOCKED",id).isEmpty();}
    private int change(Unit unit){
        return switch(unit.family()){
            case SAMPLE->jdbc.update("DELETE FROM wa.tracking_sample_receipt WHERE id=?",unit.id());
            case BATCH->jdbc.update("DELETE FROM wa.tracking_batch_receipt WHERE session_id=? AND batch_id=?",unit.parent(),unit.id());
            case CONTEXT->jdbc.update("UPDATE wa.conversation_state c SET context_json="+WaPrivacyTechnicalQueries.CONTEXT+",version=version+1,updated_at=? WHERE identity_id=?",Timestamp.from(clock.now()),unit.id());
            case REPLAY->jdbc.update("UPDATE wa.operation_replay SET response_ciphertext=decode('00','hex') WHERE id=?",unit.id());
            case CIPHER->jdbc.update("UPDATE wa.message_outbox SET private_payload_ciphertext=NULL,start_payload_ciphertext=NULL,version=version+1 WHERE id=?",unit.id());
            case PAIRING->jdbc.update("DELETE FROM wa.pairing_challenge WHERE id=?",unit.id());
            case DEVICE->jdbc.update("UPDATE wa.driver_device SET public_jwk='{}' WHERE id=?",unit.id());
        };
    }
    private Grant grant(WaPrincipal actor){
        var rows=jdbc.query("SELECT environment,allowed_instance_id,expires_at FROM wa.service_credential WHERE id=? AND principal='SYSTEM_JOB' AND allowed_instance_id IS NOT NULL AND revoked_at IS NULL AND expires_at>? FOR SHARE",
            (rs,n)->new Grant(rs.getString(1),rs.getString(2),rs.getTimestamp(3).toInstant()),actor.credentialId(),Timestamp.from(clock.now()));
        if(rows.size()!=1 || !rows.get(0).instance().equals(actor.instanceId()))throw WaProblem.unauthorized();return rows.get(0);
    }
    private void validate(WaPrincipal actor,Grant grant,Position cursor){
        if(cursor==null)return;
        if(!actor.credentialId().equals(cursor.credentialId()) || !grant.environment().equals(cursor.environment()) || !grant.instance().equals(cursor.instanceId())
            || cursor.dryRun()!=dryRun || cursor.syntheticOnly()!=policy.syntheticOnly() || cursor.asOf()==null || cursor.asOf().isAfter(clock.now())
            || cursor.after()==null || !cursor.after().matches("[0-9]{2}:[0-9]{20}:[0-9a-f-]{36}:[0-9a-f-]{36}|"))throw WaProblem.invalid();
    }
    private Position position(WaPrincipal actor,Grant grant,Instant asOf,String after){return new Position(actor.credentialId(),grant.environment(),grant.instance(),asOf,after,dryRun,policy.syntheticOnly());}
    private Object[] params(Grant grant,Instant asOf){return new Object[]{grant.environment(),grant.instance(),policy.syntheticOnly(),Timestamp.from(asOf),Timestamp.from(clock.now())};}
    private static Object[] append(Object[] first,Object...rest){Object[] all=java.util.Arrays.copyOf(first,first.length+rest.length);System.arraycopy(rest,0,all,first.length,rest.length);return all;}
}
