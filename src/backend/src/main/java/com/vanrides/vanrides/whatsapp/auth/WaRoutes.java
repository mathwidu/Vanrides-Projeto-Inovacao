package com.vanrides.vanrides.whatsapp.auth;

import java.util.List;
import java.util.regex.Pattern;

/** A new controller never becomes public merely by matching a namespace. */
public final class WaRoutes {
    public enum Mode { SERVICE, DEVICE, PROOF }
    public record Route(String method, Pattern path, Mode mode, WaPrincipal.Kind kind,
                        boolean available, boolean allowWhileDisabled) { }
    private static final String UUID = "[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}";
    private static final String WA = "/integrations/whatsapp/v1";
    private static final String APP = "/driver-app/v1";
    private static final List<Route> ROUTES = List.of(
        service("GET", "/context", WaPrincipal.Kind.CONVERSATION_WORKER, true, true),
        service("POST", "/commands", WaPrincipal.Kind.CONVERSATION_WORKER, true, true),
        service("POST", "/events", WaPrincipal.Kind.CHANNEL, true, true),
        service("POST", "/inbox/claim", WaPrincipal.Kind.CONVERSATION_WORKER, true, false),
        service("POST", "/inbox/" + UUID + "/result", WaPrincipal.Kind.CONVERSATION_WORKER, true, true),
        service("POST", "/jobs/tick", WaPrincipal.Kind.SYSTEM_JOB, true, true),
        service("POST", "/outbox/claim", WaPrincipal.Kind.DELIVERY_WORKER, true, false),
        service("POST", "/outbox/" + UUID + "/authorize-submit", WaPrincipal.Kind.DELIVERY_WORKER, true, false),
        service("POST", "/outbox/" + UUID + "/result", WaPrincipal.Kind.DELIVERY_WORKER, true, true),
        app("POST", "/pairings/begin", Mode.PROOF, true, false),
        app("GET", "/pairings/" + UUID + "/status", Mode.PROOF, true, true),
        app("POST", "/pairings/redeem", Mode.PROOF, true, false),
        app("POST", "/session/refresh", Mode.PROOF, true, true),
        app("POST", "/session/revoke", Mode.DEVICE, true, true),
        app("GET", "/status", Mode.DEVICE, true, true),
        app("GET", "/tracking-status", Mode.DEVICE, true, true),
        app("POST", "/start-intents/" + UUID + "/activate", Mode.DEVICE, true, false),
        app("POST", "/runs/" + UUID + "/resume", Mode.DEVICE, true, false),
        app("POST", "/runs/" + UUID + "/finish", Mode.DEVICE, false, true),
        app("POST", "/tracking-sessions/" + UUID + "/samples", Mode.DEVICE, true, false),
        app("POST", "/tracking-sessions/" + UUID + "/state", Mode.DEVICE, true, true)
    );
    private WaRoutes() { }
    private static Route service(String method, String path, WaPrincipal.Kind kind, boolean available, boolean disabled) {
        return new Route(method, Pattern.compile(WA + path), Mode.SERVICE, kind, available, disabled);
    }
    private static Route app(String method, String path, Mode mode, boolean available, boolean disabled) {
        return new Route(method, Pattern.compile(APP + path), mode, WaPrincipal.Kind.DEVICE, available, disabled);
    }
    public static boolean scoped(String path) {
        return path != null && (path.equals("/driver-app") || path.startsWith("/driver-app/")
            || path.equals("/integrations/whatsapp") || path.startsWith("/integrations/whatsapp/"));
    }
    public static Route resolve(String method, String path) {
        return ROUTES.stream().filter(route -> route.method().equals(method) && route.path().matcher(path).matches())
            .findFirst().orElse(null);
    }
}
