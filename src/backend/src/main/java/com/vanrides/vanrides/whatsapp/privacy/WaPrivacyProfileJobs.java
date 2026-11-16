package com.vanrides.vanrides.whatsapp.privacy;

import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.config.WaClock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/** The automatic job only processes explicit closures. A batch is at most 25 logical subjects. */
final class WaPrivacyProfileJobs {
    record Position(UUID credentialId,String environment,String instanceId,Instant asOf,boolean dryRun,boolean syntheticOnly) { }
    record Result(String status,int examined,int erased,int deferred,int wouldChange,boolean complete,Position next,List<String> reasons) { }
    private final WaPrivacyService service;
    private final WaPrivacySettings settings;
    private final WaClock clock;
    WaPrivacyProfileJobs(WaPrivacyService service,WaPrivacySettings settings,WaClock clock){this.service=service;this.settings=settings;this.clock=clock;}
    Result tick(WaPrincipal actor,WaPrivacyService.Scope scope,int limit,Position cursor){
        Instant asOf=cursor==null?clock.now():cursor.asOf();
        if(cursor!=null && (!actor.credentialId().equals(cursor.credentialId()) || !scope.environment().equals(cursor.environment())
            || !scope.instanceId().equals(cursor.instanceId()) || settings.dryRun()!=cursor.dryRun() || settings.syntheticOnly()!=cursor.syntheticOnly()
            || asOf==null || asOf.isAfter(clock.now()) || !asOf.plusSeconds(300).isAfter(clock.now())))throw WaProblem.invalid();
        var due=service.candidates(scope,Math.min(25,limit));int erased=0,deferred=0;var reasons=new ArrayList<String>();
        if(!settings.dryRun())for(UUID subject:due){
            var result=service.erase(scope,subject);
            if(result.status().equals("ERASED"))erased++;
            else if(!result.status().equals("ALREADY_ERASED")){deferred++;reasons.add(result.status());}
        }
        boolean complete=service.candidates(scope,1).isEmpty();
        Position next=!settings.dryRun() && !complete && erased>0
            ?new Position(actor.credentialId(),scope.environment(),scope.instanceId(),asOf,settings.dryRun(),settings.syntheticOnly()):null;
        return new Result(settings.dryRun()?"DRY_RUN":deferred==0?"APPLIED":"DEFERRED",due.size(),erased,deferred,
            settings.dryRun()?due.size():0,complete,next,List.copyOf(reasons));
    }
}
