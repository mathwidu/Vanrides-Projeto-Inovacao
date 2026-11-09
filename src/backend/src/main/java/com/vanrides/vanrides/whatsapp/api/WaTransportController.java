package com.vanrides.vanrides.whatsapp.api;

import com.fasterxml.jackson.databind.node.ObjectNode;
import com.vanrides.vanrides.whatsapp.auth.WaPrincipal;
import com.vanrides.vanrides.whatsapp.auth.WaRoutes;
import com.vanrides.vanrides.whatsapp.transport.WaEventService;
import com.vanrides.vanrides.whatsapp.transport.WaInboxService;
import com.vanrides.vanrides.whatsapp.transport.WaOutboxService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
public class WaTransportController {
    private final WaEventService events;
    private final WaInboxService inbox;
    private final WaOutboxService outbox;
    public WaTransportController(WaEventService events, WaInboxService inbox, WaOutboxService outbox) {
        this.events = events; this.inbox = inbox; this.outbox = outbox;
    }
    @PostMapping({"/integrations/whatsapp/v1/events", "/integrations/whatsapp/v1/team-events"})
    @ResponseStatus(HttpStatus.ACCEPTED)
    public ObjectNode event(HttpServletRequest request) {
        return events.accept(actor(request, WaPrincipal.Kind.CHANNEL), body(request));
    }
    @PostMapping("/integrations/whatsapp/v1/inbox/claim")
    public ObjectNode inboxClaim(HttpServletRequest request) {
        return inbox.claim(actor(request, WaPrincipal.Kind.CONVERSATION_WORKER), body(request));
    }
    @PostMapping("/integrations/whatsapp/v1/inbox/{id}/result")
    public ObjectNode inboxResult(HttpServletRequest request, @PathVariable String id) {
        return inbox.result(actor(request, WaPrincipal.Kind.CONVERSATION_WORKER), WaJson.uuid(id),
            WaHttp.header(request, "X-Inbox-Lease", true), body(request));
    }
    @PostMapping("/integrations/whatsapp/v1/outbox/claim")
    public ObjectNode outboxClaim(HttpServletRequest request) {
        return outbox.claim(actor(request, WaPrincipal.Kind.DELIVERY_WORKER), body(request));
    }
    @PostMapping("/integrations/whatsapp/v1/outbox/{id}/authorize-submit")
    public ObjectNode outboxAuthorize(HttpServletRequest request, @PathVariable String id) {
        return outbox.authorize(actor(request, WaPrincipal.Kind.DELIVERY_WORKER), WaJson.uuid(id), body(request)).requireSuccess();
    }
    @PostMapping("/integrations/whatsapp/v1/outbox/{id}/result")
    public ObjectNode outboxResult(HttpServletRequest request, @PathVariable String id) {
        return outbox.result(actor(request, WaPrincipal.Kind.DELIVERY_WORKER), WaJson.uuid(id), body(request));
    }
    private WaPrincipal actor(HttpServletRequest request, WaPrincipal.Kind kind) {
        if (request.getQueryString() != null) throw WaProblem.invalid();
        if (!(request.getAttribute(WaHttp.ROUTE) instanceof WaRoutes.Route route)
                || route.mode() != WaRoutes.Mode.SERVICE || !route.available() || route.kind() != kind) throw WaProblem.denied();
        if (!(request.getAttribute(WaHttp.PRINCIPAL) instanceof WaPrincipal value) || value.kind() != kind) throw WaProblem.unauthorized();
        return value;
    }
    private ObjectNode body(HttpServletRequest request) {
        if (!(request.getAttribute(WaHttp.BODY) instanceof byte[] value)) throw WaProblem.invalid();
        return WaJson.object(value);
    }
}
