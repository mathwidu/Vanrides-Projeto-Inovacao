package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingService;
import com.vanrides.vanrides.whatsapp.tracking.WaTrackingStopReceipt;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaTrackingController {
    private final WaTrackingService tracking;
    private final WaTrackingStopReceipt stopReceipts;
    public WaTrackingController(WaTrackingService tracking,WaTrackingStopReceipt stopReceipts) {
        this.tracking=tracking; this.stopReceipts=stopReceipts;
    }
    @GetMapping("/driver-app/v1/tracking-status") public ObjectNode status(HttpServletRequest request) {
        return tracking.status(actor(request));
    }
    @PostMapping("/driver-app/v1/start-intents/{id}/activate") public ObjectNode activate(HttpServletRequest request,@PathVariable String id) {
        return tracking.activate(actor(request),WaJson.uuid(id),body(request),WaHttp.idempotency(request));
    }
    @PostMapping("/driver-app/v1/runs/{id}/resume") public ObjectNode resume(HttpServletRequest request,@PathVariable String id) {
        return tracking.resume(actor(request),WaJson.uuid(id),body(request),WaHttp.idempotency(request));
    }
    @PostMapping("/driver-app/v1/tracking-sessions/{id}/samples") public ObjectNode samples(HttpServletRequest request,@PathVariable String id) {
        return tracking.samples(actor(request),WaJson.uuid(id),body(request));
    }
    @PostMapping("/driver-app/v1/tracking-sessions/{id}/state") public ObjectNode state(HttpServletRequest request,@PathVariable String id) {
        return tracking.state(actor(request),WaJson.uuid(id),body(request),WaHttp.idempotency(request));
    }
    @PostMapping("/driver-app/v1/tracking-sessions/{id}/stop-receipt") public ObjectNode stopReceipt(HttpServletRequest request,@PathVariable String id) {
        if(request.getQueryString()!=null) throw WaProblem.invalid();
        if(!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route) || route.mode()!=WaRoutes.Mode.PROOF || !route.available()) throw WaProblem.denied();
        if(!(request.getAttribute(WaHttp.PROOF) instanceof WaDpopVerifier.Proof proof)) throw WaProblem.unauthorized();
        return stopReceipts.read(proof,WaJson.uuid(id),body(request));
    }
    private WaPrincipal actor(HttpServletRequest request) {
        if(request.getQueryString()!=null) throw WaProblem.invalid();
        if(!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route) || route.mode()!=WaRoutes.Mode.DEVICE || !route.available()) throw WaProblem.denied();
        if(!(request.getAttribute(WaHttp.PROOF) instanceof WaDpopVerifier.Proof)) throw WaProblem.unauthorized();
        if(!(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal actor) || actor.kind()!=WaPrincipal.Kind.DEVICE) throw WaProblem.unauthorized();
        return actor;
    }
    private ObjectNode body(HttpServletRequest request) {
        if(!(request.getAttribute(WaHttp.BODY) instanceof byte[] body)) throw WaProblem.invalid(); return WaJson.object(body);
    }
}
