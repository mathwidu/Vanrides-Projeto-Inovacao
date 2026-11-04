package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.web.filter.GenericFilterBean;

/** Runs before global CORS; does not read a body ahead of the existing admission guard. */
public final class WaIngressFilter extends GenericFilterBean {
    private final WaSettings settings;
    private final WaCredentials credentials;
    private final WaRateLimiter limiter;
    private final TrustedProxyClientAddressResolver addresses;
    public WaIngressFilter(WaSettings settings, WaCredentials credentials, WaRateLimiter limiter,
                           TrustedProxyClientAddressResolver addresses) {
        this.settings = settings; this.credentials = credentials; this.limiter = limiter; this.addresses = addresses;
    }
    static String path(HttpServletRequest request) {
        var mapping = request.getHttpServletMapping();
        var match = mapping == null ? null : mapping.getMappingMatch();
        String path;
        if (match == jakarta.servlet.http.MappingMatch.PATH) {
            path = request.getPathInfo();
            if (path == null || path.isBlank()) path = "/";
        } else if (match != null) {
            path = request.getServletPath();
        } else {
            path = request.getPathInfo();
            if (path == null || path.isBlank()) path = request.getServletPath();
        }
        if (path == null || path.isEmpty()) {
            path = request.getRequestURI(); String context = request.getContextPath();
            if (context != null && !context.isEmpty() && path.startsWith(context + "/")) path = path.substring(context.length());
        }
        return path;
    }
    @Override public void doFilter(jakarta.servlet.ServletRequest incoming, jakarta.servlet.ServletResponse outgoing, FilterChain chain)
            throws ServletException, IOException {
        if (!(incoming instanceof HttpServletRequest request) || !(outgoing instanceof HttpServletResponse response)) {
            chain.doFilter(incoming, outgoing); return;
        }
        if (settings.publishedMvp() && !WaTeamApiBoundary.admit(request,response,settings.mvpOnline())) return;
        if (!WaRoutes.scoped(path(request))) { chain.doFilter(request, response); return; }
        try {
            String path = path(request);
            request.setAttribute(WaHttp.PATH, path);
            response.setHeader("Cache-Control", "no-store");
            response.setHeader("X-Correlation-Id", WaHttp.correlation(request));
            if (request.getDispatcherType() != DispatcherType.REQUEST || request.getHeader("Origin") != null) throw WaProblem.denied();
            if (request.getRequestURI().matches(".*[%;\\\\].*") || request.getRequestURI().contains("//")
                    || request.getRequestURI().contains("..")) throw WaProblem.invalid();
            WaRoutes.Route route = WaRoutes.resolve(request.getMethod(), path);
            limiter.acquire(addresses.resolve(request).digest(), route != null && route.mode() == WaRoutes.Mode.PROOF);
            if (route == null) throw WaProblem.missing();
            if (!settings.configured() || !settings.enabled() && !route.allowWhileDisabled()) throw WaProblem.denied();
            request.setAttribute(WaHttp.ROUTE, route);
            if (route.mode() != WaRoutes.Mode.PROOF) {
                String header = WaHttp.header(request, "Authorization", false);
                String prefix = route.mode() == WaRoutes.Mode.SERVICE ? "Bearer " : "DPoP ";
                if (header == null || !header.startsWith(prefix)) throw WaProblem.unauthorized();
                String token = header.substring(prefix.length());
                WaPrincipal principal = route.mode() == WaRoutes.Mode.SERVICE ? credentials.service(token)
                    : credentials.device(token, path.equals("/driver-app/v1/session/revoke")
                        || path.equals("/driver-app/v1/tracking-status") || path.matches("/driver-app/v1/tracking-sessions/[^/]+/state"));
                if (principal.kind() != route.kind()) throw WaProblem.denied();
                request.setAttribute(WaHttp.PRINCIPAL, principal);
            } else if (request.getHeader("Authorization") != null) throw WaProblem.unauthorized();
            if (!route.available()) throw new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE);
            if (request.getContentLengthLong() > 32768) throw WaProblem.invalid();
            if ("POST".equals(request.getMethod()) || request.getContentType() != null) {
                String type = request.getContentType();
                try {
                    if (type == null) throw WaProblem.invalid();
                    var media = org.springframework.http.MediaType.parseMediaType(type);
                    if (media.isWildcardType() || media.isWildcardSubtype()
                            || !org.springframework.http.MediaType.APPLICATION_JSON.isCompatibleWith(media)) throw WaProblem.invalid();
                } catch (IllegalArgumentException e) { throw WaProblem.invalid(); }
            }
            chain.doFilter(request, response);
        } catch (WaProblem e) { WaHttp.writeError(request, response, e); }
        catch (TrustedProxyClientAddressResolver.ResolutionException e) { WaHttp.writeError(request, response, WaProblem.invalid()); }
        catch (org.springframework.dao.DataAccessException e) { WaHttp.writeError(request, response, new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE)); }
    }
}
