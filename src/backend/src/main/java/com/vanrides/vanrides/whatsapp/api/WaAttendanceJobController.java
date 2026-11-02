package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.attendance.WaAttendanceJobs;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public final class WaAttendanceJobController {
    private final WaAttendanceJobs jobs;

    public WaAttendanceJobController(WaAttendanceJobs jobs) {
        this.jobs = jobs;
    }

    @PostMapping("/integrations/whatsapp/v1/jobs/tick")
    public ObjectNode tick(HttpServletRequest request) {
        if (!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route)
                || route.mode() != WaRoutes.Mode.SERVICE || route.kind() != WaPrincipal.Kind.SYSTEM_JOB || !route.available()
                || !(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal principal)
                || principal.kind() != WaPrincipal.Kind.SYSTEM_JOB) {
            throw WaProblem.denied();
        }
        if (request.getQueryString() != null || !(request.getAttribute(WaHttp.BODY) instanceof byte[] body)) {
            throw WaProblem.invalid();
        }
        return jobs.tick(principal, WaJson.object(body));
    }
}
