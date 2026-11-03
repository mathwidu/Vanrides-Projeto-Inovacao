package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaDpopVerifier;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import com.vanrides.vanrides.whatsapp.commands.WaCommandService;
import com.vanrides.vanrides.whatsapp.pairing.WaPairingService;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Set;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RestController;

/** The filter's trusted attributes are mandatory; no HTTP DTO can manufacture a principal. */
@RestController
public class WaController {
    private final WaCommandService commands;
    private final WaPairingService pairing;
    public WaController(WaCommandService commands, WaPairingService pairing) { this.commands = commands; this.pairing = pairing; }

    @GetMapping("/integrations/whatsapp/v1/context")
    public ObjectNode context(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.SERVICE);
        if (!request.getParameterMap().keySet().equals(Set.of("inboxEventId"))
                || request.getParameterValues("inboxEventId").length != 1) throw WaProblem.invalid();
        return commands.context(principal(request), WaJson.uuid(request.getParameter("inboxEventId")), lease(request));
    }
    @PostMapping("/integrations/whatsapp/v1/commands")
    public ObjectNode command(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.SERVICE); noQuery(request);
        return commands.execute(principal(request), lease(request), WaHttp.idempotency(request), body(request),
            WaHttp.correlation(request)).requireSuccess();
    }
    @PostMapping("/driver-app/v1/pairings/begin")
    public ObjectNode begin(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.PROOF); noQuery(request);
        return pairing.begin(proof(request), body(request), WaHttp.idempotency(request)).requireSuccess();
    }
    @GetMapping("/driver-app/v1/pairings/{id}/status")
    public ObjectNode status(HttpServletRequest request, @PathVariable String id) {
        guard(request, WaRoutes.Mode.PROOF); noQuery(request);
        return pairing.status(proof(request), WaJson.uuid(id));
    }
    @PostMapping("/driver-app/v1/pairings/redeem")
    public ObjectNode redeem(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.PROOF); noQuery(request);
        return pairing.redeem(proof(request), body(request), WaHttp.idempotency(request)).requireSuccess();
    }
    @PostMapping("/driver-app/v1/session/refresh")
    public ObjectNode refresh(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.PROOF); noQuery(request);
        return pairing.refresh(proof(request), body(request), WaHttp.idempotency(request)).requireSuccess();
    }
    @PostMapping("/driver-app/v1/session/revoke")
    public ObjectNode revoke(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.DEVICE); noQuery(request);
        return pairing.revoke(principal(request), body(request), WaHttp.idempotency(request)).requireSuccess();
    }
    @GetMapping("/driver-app/v1/status")
    public ObjectNode driverStatus(HttpServletRequest request) {
        guard(request, WaRoutes.Mode.DEVICE); noQuery(request);
        return pairing.driverStatus(principal(request));
    }
    private void guard(HttpServletRequest request, WaRoutes.Mode mode) {
        if (!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route) || route.mode() != mode
                || !route.available()) throw WaProblem.denied();
        if (mode != WaRoutes.Mode.PROOF) {
            WaPrincipal actor = principal(request);
            if (actor.kind() != route.kind()) throw WaProblem.denied();
        }
        if (mode != WaRoutes.Mode.SERVICE) proof(request);
    }
    private WaPrincipal principal(HttpServletRequest request) {
        if (!(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal value)) throw WaProblem.unauthorized();
        return value;
    }
    private WaDpopVerifier.Proof proof(HttpServletRequest request) {
        if (!(request.getAttribute(WaHttp.PROOF) instanceof WaDpopVerifier.Proof value)) throw WaProblem.unauthorized();
        return value;
    }
    private ObjectNode body(HttpServletRequest request) {
        if (!(request.getAttribute(WaHttp.BODY) instanceof byte[] value)) throw WaProblem.invalid();
        return WaJson.object(value);
    }
    private String lease(HttpServletRequest request) { return WaHttp.header(request, "X-Inbox-Lease", true); }
    private void noQuery(HttpServletRequest request) {
        if (request.getQueryString() != null) throw WaProblem.invalid();
    }
}
