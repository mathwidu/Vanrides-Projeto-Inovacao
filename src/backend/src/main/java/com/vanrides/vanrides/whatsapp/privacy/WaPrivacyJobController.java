package com.vanrides.vanrides.whatsapp.privacy;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.api.*;
import com.vanrides.vanrides.whatsapp.auth.*;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class WaPrivacyJobController {
    private final WaPrivacyJobs jobs;
    public WaPrivacyJobController(WaPrivacyJobs jobs){this.jobs=jobs;}
    @PostMapping("/integrations/whatsapp/v1/privacy/tick") public ObjectNode tick(HttpServletRequest request){
        if(!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route)
            || route.mode()!=WaRoutes.Mode.SERVICE || route.kind()!=WaPrincipal.Kind.SYSTEM_JOB || !route.available()
            || !(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal actor) || actor.kind()!=WaPrincipal.Kind.SYSTEM_JOB)throw WaProblem.denied();
        if(request.getQueryString()!=null || !(request.getAttribute(WaHttp.BODY) instanceof byte[] body))throw WaProblem.invalid();
        return jobs.tick(actor,WaJson.object(body));
    }
}
