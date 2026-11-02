package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import com.vanrides.vanrides.dto.ApiErrorResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.AuthorizationService;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.lang.NonNull;
import org.springframework.web.cors.CorsUtils;

import java.io.IOException;

@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 4)
public class FilterAuthBasic extends OncePerRequestFilter {

    @Autowired
    private AuthorizationService authorizationService;

    @Autowired
    private ObjectMapper objectMapper;

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request,
                                    @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain)
        throws ServletException, IOException {

        String path = ApplicationRequestPath.resolve(request);
        String method = request.getMethod();

        // 🔓 Permitir CORS preflight (OPTIONS) sem autenticação
        if (CorsUtils.isPreFlightRequest(request) || method.equals("OPTIONS")) {
            chain.doFilter(request, response);
            return;
        }

        // 🔓 Permitir POST sem autenticação para cadastro público inicial e bootstrap do portal
        if (((path.equals("/passageiros") || path.equals("/motoristas")) && method.equals("POST"))
                || (path.equals("/company-admins/bootstrap") && method.equals("POST"))) {
            chain.doFilter(request, response);
            return;
        }

        // 🔓 Permitir GET/HEAD sem autenticação para listar motoristas
        if (path.equals("/motoristas") && (method.equals("GET") || method.equals("HEAD"))) {
            chain.doFilter(request, response);
            return;
        }

        if (SensitiveRequestPolicy.isDriverInviteRoute(path, "preview")
                && (method.equals("GET") || method.equals("HEAD"))) {
            chain.doFilter(request, response);
            return;
        }

        // 🔒 Protege os demais endpoints
        if (path.startsWith("/presencas")
                || path.startsWith("/passageiros")
                || path.startsWith("/passageiro-onboarding")
                || path.startsWith("/driver-onboarding")
                || path.startsWith("/account")
                || path.startsWith("/devices")
                || path.startsWith("/responsaveis")
                || path.startsWith("/support")
                || path.startsWith("/viagens")
                || path.startsWith("/motoristas")
                || path.startsWith("/company-admins")
                || path.startsWith("/api/localizacao")) {
            String authHeader = request.getHeader("Authorization");
            try {
                boolean allowUnverifiedAccess = allowsUnverifiedDriverOnboarding(path, method);
                AuthenticatedUser user = allowUnverifiedAccess
                        ? authorizationService.authenticate(authHeader, true)
                        : authorizationService.authenticate(authHeader);
                if (user == null) {
                    throw new IllegalArgumentException("Credenciais inválidas");
                }
                request.setAttribute("userRole", user.getRole());
                request.setAttribute("userId", user.getUserId());
                request.setAttribute("userEmail", user.getEmail());
                if (user.getCompanyId() != null) {
                    request.setAttribute("userCompanyId", user.getCompanyId());
                }
                if (requiresCompanyContext(path)) {
                    request.setAttribute("userCompanyId", getCompanyIdOrReject(user.getCompanyId(), response));
                    if (response.isCommitted()) {
                        return;
                    }
                }
                chain.doFilter(request, response);
                return;
            } catch (EmailVerificationException e) {
                writeJsonError(response, e);
                return;
            } catch (IllegalArgumentException e) {
                response.sendError(401, e.getMessage());
                return;
            }
        } else {
            chain.doFilter(request, response);
        }
    }

    private Long getCompanyIdOrReject(Long companyId, HttpServletResponse response) throws IOException {
        if (companyId == null) {
            response.sendError(401, "Usuário sem empresa associada");
            return null;
        }
        return companyId;
    }

    private boolean requiresCompanyContext(String path) {
        return !path.startsWith("/passageiro-onboarding")
                && !path.startsWith("/driver-onboarding")
                && !path.startsWith("/account")
                && !path.startsWith("/devices");
    }

    private boolean allowsUnverifiedDriverOnboarding(String path, String method) {
        return SensitiveRequestPolicy.isDriverInviteRoute(path, "redeem")
                && method.equals("POST");
    }

    private void writeJsonError(HttpServletResponse response, EmailVerificationException exception) throws IOException {
        response.setStatus(exception.getHttpStatus());
        response.setContentType("application/json");
        response.setCharacterEncoding("UTF-8");
        objectMapper.writeValue(
                response.getWriter(),
                new ApiErrorResponse(exception.getCode(), exception.getMessage(), exception.getEmail())
        );
    }
}
