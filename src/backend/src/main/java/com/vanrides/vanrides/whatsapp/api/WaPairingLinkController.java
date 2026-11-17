package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingLinks;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaPairingLinkController {
    private final WaPairingLinks links;
    public WaPairingLinkController(WaPairingLinks links) { this.links=links; }
    @PostMapping("/driver-app/v1/pairing-links/{id}/preview")
    public ObjectNode preview(HttpServletRequest request,@PathVariable String id) {
        return links.preview(proof(request),WaJson.uuid(id),body(request));
    }
    @PostMapping("/driver-app/v1/pairing-links/{id}/claim")
    public ObjectNode claim(HttpServletRequest request,@PathVariable String id) {
        return links.claim(proof(request),WaJson.uuid(id),body(request),WaHttp.idempotency(request));
    }
    private static WaDpopVerifier.Proof proof(HttpServletRequest request) {
        if(request.getQueryString()!=null) throw WaProblem.invalid();
        if(!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route)
                || route.mode()!=WaRoutes.Mode.PROOF || !route.available()) throw WaProblem.denied();
        if(!(request.getAttribute(WaHttp.PROOF) instanceof WaDpopVerifier.Proof proof)) throw WaProblem.unauthorized();
        return proof;
    }
    private static ObjectNode body(HttpServletRequest request) {
        if(!(request.getAttribute(WaHttp.BODY) instanceof byte[] body)) throw WaProblem.invalid();
        return WaJson.object(body);
    }
}
