package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.team.WaTeamSettings;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.util.Set;
import org.springframework.http.MediaType;

/** Exact browser allowlist. Technical WhatsApp and legacy routes still use their own boundary. */
final class WaTeamWebBoundary {
    private static final Set<String> ASSETS = Set.of("/equipe", "/equipe/", "/equipe/index.html",
        "/equipe/app.js", "/equipe/style.css");
    private static final Set<String> GET = Set.of("/equipe/login", "/equipe/callback", "/equipe/api/session");
    private static final Set<String> POST = Set.of("/equipe/api/logout", "/equipe/api/participants/status", "/equipe/api/participants");
    private WaTeamWebBoundary() { }

    static boolean scoped(String path) { return path.equals("/equipe") || path.startsWith("/equipe/"); }

    static boolean admit(WaTeamSettings settings, String path, HttpServletRequest request, HttpServletResponse response) {
        boolean post = "POST".equals(request.getMethod()) && POST.contains(path);
        boolean get = "GET".equals(request.getMethod()) && (GET.contains(path) || ASSETS.contains(path));
        String raw = request.getRequestURI();
        if (settings == null || !settings.enabled() || !(post || get)
                || !raw.equals(request.getContextPath() + path) || request.getDispatcherType() != DispatcherType.REQUEST)
            throw WaProblem.missing();
        if (!path.equals("/equipe/callback") && request.getQueryString() != null
                && !(ASSETS.contains(path) && request.getQueryString().matches("auth=(invalid|denied|unavailable)")))
            throw WaProblem.invalid();
        String origin = WaHttp.header(request, "Origin", post);
        if (origin != null && !settings.origin().equals(origin) || WaHttp.header(request, "Authorization", false) != null)
            throw WaProblem.denied();
        if (post) {
            try {
                String type = WaHttp.header(request, "Content-Type", true);
                MediaType actual = MediaType.parseMediaType(type);
                if (!"application".equals(actual.getType()) || !"json".equals(actual.getSubtype())
                        || request.getContentLengthLong() > 2048 || request.getHeader("Content-Encoding") != null)
                    throw WaProblem.invalid();
            } catch (IllegalArgumentException invalid) { throw WaProblem.invalid(); }
        }
        response.setHeader("X-VanRides-Environment", com.vanrides.vanrides.config.MvpOnlineMode.ENVIRONMENT);
        response.setHeader("Cache-Control", "no-store");
        response.setHeader("Referrer-Policy", "no-referrer");
        response.setHeader("X-Content-Type-Options", "nosniff");
        response.setHeader("Content-Security-Policy", "default-src 'none'; script-src 'self'; style-src 'self'; "
            + "img-src 'self'; connect-src 'self'; base-uri 'none'; form-action 'self'; frame-ancestors 'none'");
        response.setHeader("X-Correlation-Id", WaHttp.correlation(request));
        return !ASSETS.contains(path);
    }
}
