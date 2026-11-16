package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.databind.json.JsonMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import com.vanrides.vanrides.whatsapp.config.*;
import com.vanrides.vanrides.whatsapp.transport.WaTransportAccess;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionSynchronizationManager;
import org.springframework.transaction.support.TransactionTemplate;

/** Fixed independent families; no outer transaction may enclose journal I/O or a port's own transaction. */
@Service
public class WaPrivacyJobs {
    public enum Family { TERMINAL_7D,TECHNICAL_7D,PROFILE_30D,HISTORY_PROJECTION_90D,HISTORY_90D }
    private record Page(String status,int examined,int erased,int deleted,int deferred,int wouldChange,
                        boolean complete,Object next,List<String> reasons) { }
    private static final JsonMapper JSON=JsonMapper.builder().addModule(new JavaTimeModule())
        .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS).disable(MapperFeature.ALLOW_COERCION_OF_SCALARS)
        .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES,DeserializationFeature.FAIL_ON_MISSING_CREATOR_PROPERTIES,
            DeserializationFeature.FAIL_ON_NULL_FOR_PRIMITIVES).build();
    private static final Set<String> REASONS=Set.of("ROW_BUSY","ACTIVE_REFERENCE","HUMAN_WORK_BUSY","MULTI_INSTANCE","CHILDREN_PENDING",
        "AMBIGUOUS_OWNER","REPLAY_PENDING","SUBMITTING","COPY_WRITER_BUSY","TERMINAL_CONTENT_PENDING","JOURNAL_PENDING","NOT_DUE",
        "NOT_CLOSED","OUT_OF_SCOPE","MISSING_TERMINAL_AT","HISTORY_ORIGIN_UNATTRIBUTABLE","CONFLICTING_PROVENANCE",
        "QUESTION_OUTBOX_PROVENANCE","QUESTION_REFERENCE","SUPPORT_ORIGIN_UNATTRIBUTABLE","AUDIT_COHORT_UNATTRIBUTABLE",
        "AUDIT_ORIGIN_UNATTRIBUTABLE","HISTORY_GRAPH_PENDING");
    private final JdbcTemplate jdbc;
    private final PlatformTransactionManager manager;
    private final TransactionTemplate transaction;
    private final WaClock clock;
    private final WaSettings settings;
    private final WaPrivacySettings privacy;
    private final WaPrivacyStartupGate gate;
    private final WaTransportAccess access;
    private final WaPrivacyCursor cursors;
    public WaPrivacyJobs(JdbcTemplate jdbc,PlatformTransactionManager manager,WaClock clock,WaSettings settings,
                         WaPrivacySettings privacy,WaPrivacyStartupGate gate,WaTransportAccess access){
        this.jdbc=jdbc;this.manager=manager;transaction=new TransactionTemplate(manager);this.clock=clock;this.settings=settings;
        this.privacy=privacy;this.gate=gate;this.access=access;cursors=new WaPrivacyCursor(new WaCrypto(settings),clock);
    }
    public ObjectNode tick(WaPrincipal actor,ObjectNode request){
        if(TransactionSynchronizationManager.isActualTransactionActive() || TransactionSynchronizationManager.hasResource(jdbc.getDataSource()))
            throw new IllegalStateException("PRIVACY_AMBIENT_TRANSACTION_NOT_ALLOWED");
        if(actor==null || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB || actor.credentialId()==null || actor.instanceId()==null || actor.revoked())throw WaProblem.denied();
        if(request.has("cursor"))WaJson.fields(request,"family","batchLimit","cursor");else WaJson.fields(request,"family","batchLimit");
        Family family;
        try{family=Family.valueOf(WaJson.text(request,"family",1,30));}catch(IllegalArgumentException invalid){throw WaProblem.invalid();}
        var value=request.get("batchLimit");
        if(value==null || !value.isIntegralNumber() || !value.canConvertToInt() || value.intValue()<1 || value.intValue()>100)throw WaProblem.invalid();
        int limit=value.intValue();
        var scope=new WaPrivacyService.Scope(settings.environment(),actor.instanceId());
        Consumer<WaPrivacyService.Scope> authority=authority(actor);
        transaction.executeWithoutResult(tx->authority.accept(scope));
        ObjectNode position=null;
        if(request.has("cursor")){
            var cursor=cursors.read(actor,WaJson.text(request,"cursor",40,2214));
            if(!family.name().equals(cursor.family()))throw WaProblem.invalid();position=cursor.position();
        }
        if(!privacy.enabled())return response(actor,family,new Page("DISABLED",0,0,0,0,0,false,null,List.of()));
        Page page=switch(family){
            case TERMINAL_7D->terminal(actor,limit,position);
            case TECHNICAL_7D->technical(actor,limit,position);
            case PROFILE_30D->profile(actor,scope,authority,limit,position);
            case HISTORY_PROJECTION_90D->projection(actor,authority,limit,position);
            case HISTORY_90D->history(actor,limit,position);
        };
        return response(actor,family,page);
    }
    private Consumer<WaPrivacyService.Scope> authority(WaPrincipal actor){
        return scope->{
            var grant=access.lock(actor,WaPrincipal.Kind.SYSTEM_JOB);
            if(!settings.environment().equals(scope.environment()) || !grant.instance().equals(scope.instanceId()))throw WaProblem.unauthorized();
            access.revalidate(grant);
        };
    }
    private Page terminal(WaPrincipal actor,int limit,ObjectNode cursor){
        var position=cursor==null?new WaPrivacyTerminalMaintenance.Position(actor.credentialId(),settings.environment(),actor.instanceId(),clock.now(),"",privacy.dryRun(),privacy.syntheticOnly())
            :decode(cursor,WaPrivacyTerminalMaintenance.Position.class);
        scope(position.environment());
        var result=new WaPrivacyTerminalMaintenance(jdbc,manager,clock,privacy.policy(),privacy.dryRun()).tick(actor,limit,position);
        // This existing port reports preview candidates as deferred; never claim they are executable mutations.
        return new Page(result.status(),result.examined(),result.erased(),result.deleted(),result.deferred(),0,result.complete(),result.next(),
            result.deferred()>0?List.of(privacy.dryRun()?"DRY_RUN":"TERMINAL_CONTENT_PENDING"):List.of());
    }
    private Page technical(WaPrincipal actor,int limit,ObjectNode cursor){
        var position=cursor==null?new WaPrivacyTechnicalRetention.Position(actor.credentialId(),settings.environment(),actor.instanceId(),clock.now(),"",privacy.dryRun(),privacy.syntheticOnly())
            :decode(cursor,WaPrivacyTechnicalRetention.Position.class);
        scope(position.environment());
        var result=new WaPrivacyTechnicalRetention(jdbc,manager,clock,privacy.policy(),privacy.dryRun()).tick(actor,limit,position);
        return new Page(result.status(),result.examined(),result.erased(),result.deleted(),result.deferred(),result.wouldChange(),result.complete(),result.next(),
            result.pending().stream().map(WaPrivacyTechnicalRetention.Deferral::reason).toList());
    }
    private Page history(WaPrincipal actor,int limit,ObjectNode cursor){
        var position=cursor==null?new WaPrivacyHistoryRetention.Position(actor.credentialId(),settings.environment(),actor.instanceId(),clock.now(),null,null,null,privacy.dryRun(),privacy.syntheticOnly())
            :decode(cursor,WaPrivacyHistoryRetention.Position.class);
        scope(position.environment());
        var result=new WaPrivacyHistoryRetention(jdbc,manager,clock,privacy.policy(),privacy.dryRun()).tick(actor,limit,position);
        return new Page(result.status(),result.examined(),result.erased(),result.deleted(),result.deferred(),result.wouldDelete(),result.complete(),result.next(),
            result.pending().stream().map(WaPrivacyHistoryRetention.Deferral::reason).toList());
    }
    private Page profile(WaPrincipal actor,WaPrivacyService.Scope scope,Consumer<WaPrivacyService.Scope> authority,int limit,ObjectNode cursor){
        var service=new WaPrivacyService(jdbc,manager,clock,privacy.policy(),gate.journal(),authority);
        var result=new WaPrivacyProfileJobs(service,privacy,clock).tick(actor,scope,limit,cursor==null?null:decode(cursor,WaPrivacyProfileJobs.Position.class));
        return new Page(result.status(),result.examined(),result.erased(),0,result.deferred(),result.wouldChange(),result.complete(),result.next(),result.reasons());
    }
    private Page projection(WaPrincipal actor,Consumer<WaPrivacyService.Scope> authority,int limit,ObjectNode cursor){
        var position=cursor==null?null:decode(cursor,WaPrivacyHistoryProjection.Position.class);
        if(position!=null)scope(position.environment());
        var result=new WaPrivacyHistoryProjection(jdbc,manager,clock,privacy.policy(),authority,privacy.dryRun()).tick(actor,limit,position);
        return new Page(result.status(),result.examined(),result.erased(),result.deleted(),result.deferred(),result.wouldChange(),result.complete(),result.next(),
            result.pending().stream().map(WaPrivacyHistoryProjection.Deferral::reason).toList());
    }
    private ObjectNode response(WaPrincipal actor,Family family,Page page){
        ObjectNode result=WaJson.object("{}").put("status",page.status()).put("family",family.name()).put("examined",page.examined())
            .put("erased",page.erased()).put("deleted",page.deleted()).put("deferred",page.deferred()).put("wouldChange",page.wouldChange())
            .put("complete",page.complete()).put("serverTime",clock.now().toString());
        if(page.next()==null)result.putNull("nextCursor");else result.put("nextCursor",cursors.issue(actor,family.name(),JSON.valueToTree(page.next())));
        var reasons=result.putArray("reasonCodes");page.reasons().stream().map(reason->REASONS.contains(reason)||reason.equals("DRY_RUN")?reason:"RETENTION_DEFERRED")
            .distinct().sorted().forEach(reasons::add);return result;
    }
    private void scope(String environment){if(!settings.environment().equals(environment))throw WaProblem.invalid();}
    private static <T> T decode(ObjectNode value,Class<T> type){try{return JSON.treeToValue(value,type);}catch(Exception invalid){throw WaProblem.invalid();}}
}
