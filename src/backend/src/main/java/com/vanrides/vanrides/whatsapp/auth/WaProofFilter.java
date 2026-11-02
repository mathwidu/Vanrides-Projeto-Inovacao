package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.api.WaProblem;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import org.springframework.http.MediaType;
import org.springframework.web.filter.OncePerRequestFilter;

/** Reads at most32KiB after the global request-body admission filter. Controllers use BODY. */
public final class WaProofFilter extends OncePerRequestFilter {
    private final WaDpopVerifier verifier;
    private final WaProofLedger ledger;
    public WaProofFilter(WaDpopVerifier verifier, WaProofLedger ledger) { this.verifier = verifier; this.ledger = ledger; }
    @Override protected boolean shouldNotFilter(HttpServletRequest request) { return request.getAttribute(WaHttp.ROUTE) == null; }
    @Override protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        try {
            WaRoutes.Route route = (WaRoutes.Route) request.getAttribute(WaHttp.ROUTE);
            if ("POST".equals(request.getMethod())) {
                String contentType = request.getContentType();
                if (contentType == null || !MediaType.APPLICATION_JSON.isCompatibleWith(MediaType.parseMediaType(contentType))) throw WaProblem.invalid();
            }
            byte[] body = request.getInputStream().readNBytes(32769);
            if (body.length > 32768 || "GET".equals(request.getMethod()) && body.length != 0) throw WaProblem.invalid();
            request.setAttribute(WaHttp.BODY, body);
            if (route.mode() != WaRoutes.Mode.SERVICE) {
                String token = route.mode() == WaRoutes.Mode.DEVICE ? request.getHeader("Authorization").substring(5) : null;
                WaDpopVerifier.Proof proof = verifier.verify(WaHttp.header(request, "DPoP", false), request.getMethod(),
                    request.getRequestURI(), body, token, "POST".equals(request.getMethod()));
                WaPrincipal principal = (WaPrincipal) request.getAttribute(WaHttp.PRINCIPAL);
                if (principal != null && !WaCrypto.same(principal.jkt(), proof.jkt())) throw WaProblem.unauthorized();
                if (!ledger.consume(proof)) throw new WaProblem(WaProblem.Code.PROOF_NONCE_REQUIRED, ledger.issue(proof.jkt()));
                request.setAttribute(WaHttp.PROOF, proof);
                response.setHeader("DPoP-Nonce", ledger.issue(proof.jkt()));
            }
            chain.doFilter(request, response);
        } catch (WaProblem e) { WaHttp.writeError(request, response, e); }
        catch (com.vanrides.vanrides.exception.RequestBodyCapacityException e) {
            WaHttp.writeError(request, response, new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE));
        }
        catch (com.vanrides.vanrides.exception.RequestBodyTooLargeException e) { WaHttp.writeError(request, response, WaProblem.invalid()); }
        catch (IllegalArgumentException e) { WaHttp.writeError(request, response, WaProblem.invalid()); }
        catch (org.springframework.dao.DataAccessException e) { WaHttp.writeError(request, response, new WaProblem(WaProblem.Code.DEPENDENCY_UNAVAILABLE)); }
    }
}
