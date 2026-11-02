package com.vanrides.vanrides.auth;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.Arrays;
import java.util.EnumSet;
import java.util.Set;

public final class AuthorizationGuard {

    private static final String FORBIDDEN_MESSAGE = "Papel sem permissão para esta operação";

    private AuthorizationGuard() {
    }

    public static UserRole getRequiredRole(HttpServletRequest request) {
        Object attribute = request.getAttribute("userRole");
        if (!(attribute instanceof String rawRole) || rawRole.isBlank()) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de papel ausente");
        }

        try {
            return UserRole.fromValue(rawRole);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
        }
    }

    public static Long getRequiredUserId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de usuário ausente");
    }

    public static void requireAnyRole(HttpServletRequest request, UserRole... allowedRoles) {
        UserRole currentRole = getRequiredRole(request);
        Set<UserRole> allowed = EnumSet.copyOf(Arrays.asList(allowedRoles));
        if (!allowed.contains(currentRole)) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, FORBIDDEN_MESSAGE);
        }
    }
}
