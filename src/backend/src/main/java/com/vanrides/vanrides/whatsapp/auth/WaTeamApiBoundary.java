package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.config.TeamApiMode;
import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;

/** Runs inside the existing first filter, before CORS, body admission and every legacy handler. */
final class WaTeamApiBoundary {
    private WaTeamApiBoundary() { }
    static boolean admit(HttpServletRequest request,HttpServletResponse response) throws IOException {
        response.setHeader("X-VanRides-Environment",TeamApiMode.ENVIRONMENT);
        response.setHeader("Cache-Control","no-store");
        String path=WaIngressFilter.path(request),raw=request.getRequestURI();
        boolean canonical=raw!=null && raw.equals(request.getContextPath()+path) && !raw.matches(".*[%;\\\\].*")
            && !raw.contains("//") && !raw.contains("..");
        boolean health=request.getMethod().equals("GET") && path.equals("/actuator/health") && request.getQueryString()==null;
        WaRoutes.Route route=WaRoutes.resolve(request.getMethod(),path);
        boolean worker=route!=null && route.kind()==WaPrincipal.Kind.CONVERSATION_WORKER && route.mode()==WaRoutes.Mode.SERVICE
            && (path.equals("/integrations/whatsapp/v1/context") || path.equals("/integrations/whatsapp/v1/commands")
                || path.equals("/integrations/whatsapp/v1/inbox/claim") || path.matches("/integrations/whatsapp/v1/inbox/[0-9a-f-]{36}/result"));
        if(canonical && request.getDispatcherType()==DispatcherType.REQUEST && request.getHeader("Origin")==null && (health || worker)) return true;
        WaHttp.writeError(request,response,WaProblem.missing());return false;
    }
}
