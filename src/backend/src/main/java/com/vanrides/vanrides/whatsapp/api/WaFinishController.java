package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import com.vanrides.vanrides.whatsapp.tracking.WaFinishService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaFinishController {
    private final WaFinishService finishes;
    public WaFinishController(WaFinishService finishes) { this.finishes=finishes; }
    @PostMapping("/driver-app/v1/finish-intents/{id}/preview")
    public ObjectNode preview(HttpServletRequest request,@PathVariable String id) {
        return finishes.preview(actor(request),WaJson.uuid(id),body(request));
    }
    @PostMapping("/driver-app/v1/runs/{id}/finish")
    public ObjectNode finish(HttpServletRequest request,@PathVariable String id) {
        return finishes.confirm(actor(request),WaJson.uuid(id),body(request),WaHttp.idempotency(request));
    }
    @PostMapping("/driver-app/v1/finish-intents/{id}/receipt")
    public ObjectNode receipt(HttpServletRequest request,@PathVariable String id) {
        route(request,WaRoutes.Mode.PROOF);
        return finishes.receipt((WaDpopVerifier.Proof)request.getAttribute(WaHttp.PROOF),WaJson.uuid(id),body(request));
    }
    private static WaPrincipal actor(HttpServletRequest request) {
        route(request,WaRoutes.Mode.DEVICE);
        if(!(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal actor) || actor.kind()!=WaPrincipal.Kind.DEVICE) throw WaProblem.unauthorized();
        return actor;
    }
    private static void route(HttpServletRequest request,WaRoutes.Mode mode) {
        if(request.getQueryString()!=null) throw WaProblem.invalid();
        if(!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route) || route.mode()!=mode || !route.available()) throw WaProblem.denied();
        if(!(request.getAttribute(WaHttp.PROOF) instanceof WaDpopVerifier.Proof)) throw WaProblem.unauthorized();
    }
    private static ObjectNode body(HttpServletRequest request) {
        if(!(request.getAttribute(WaHttp.BODY) instanceof byte[] body)) throw WaProblem.invalid(); return WaJson.object(body);
    }
}
