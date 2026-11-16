package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The automatic job only processes explicit closures. A batch is at most 25 logical subjects. */
final class WaPrivacyProfileJobs {
    record Position(UUID credentialId,String environment,String instanceId,Instant asOf,boolean dryRun,boolean syntheticOnly,
                    Instant afterEraseAfter,UUID afterSubjectId) { }
    record Result(String status,int examined,int erased,int deferred,int wouldChange,boolean complete,Position next,List<String> reasons) { }
    private final WaPrivacyService service;
    private final WaPrivacySettings settings;
    private final WaClock clock;
    WaPrivacyProfileJobs(WaPrivacyService service,WaPrivacySettings settings,WaClock clock){this.service=service;this.settings=settings;this.clock=clock;}
    Result tick(WaPrincipal actor,WaPrivacyService.Scope scope,int limit,Position cursor){
        Instant asOf=cursor==null?clock.now().truncatedTo(ChronoUnit.MICROS):cursor.asOf();
        if(cursor!=null && (!actor.credentialId().equals(cursor.credentialId()) || !scope.environment().equals(cursor.environment())
            || !scope.instanceId().equals(cursor.instanceId()) || settings.dryRun()!=cursor.dryRun() || settings.syntheticOnly()!=cursor.syntheticOnly()
            || asOf==null || asOf.isAfter(clock.now()) || (cursor.afterEraseAfter()==null)!=(cursor.afterSubjectId()==null)
            || cursor.afterEraseAfter()!=null && cursor.afterEraseAfter().isAfter(asOf)))throw WaProblem.invalid();
        Instant after=cursor==null?null:cursor.afterEraseAfter();UUID afterId=cursor==null?null:cursor.afterSubjectId();
        var due=service.candidates(scope,Math.min(25,limit),asOf,after,afterId);int erased=0,deferred=0;var reasons=new ArrayList<String>();
        for(var subject:due){
            if(!settings.dryRun()){
                var result=service.erase(scope,subject.subjectId());
                if(result.status().equals("ERASED"))erased++;
                else if(!result.status().equals("ALREADY_ERASED")){deferred++;reasons.add(result.status());}
            }
            after=subject.eraseAfter();afterId=subject.subjectId();
        }
        boolean complete=service.candidatesComplete(scope,asOf);
        boolean more=!due.isEmpty() && !complete && !service.candidates(scope,1,asOf,after,afterId).isEmpty();
        Position next=more?new Position(actor.credentialId(),scope.environment(),scope.instanceId(),asOf,settings.dryRun(),settings.syntheticOnly(),after,afterId):null;
        return new Result(settings.dryRun()?"DRY_RUN":deferred==0?"APPLIED":"DEFERRED",due.size(),erased,deferred,
            settings.dryRun()?due.size():0,complete,next,List.copyOf(reasons));
    }
}
