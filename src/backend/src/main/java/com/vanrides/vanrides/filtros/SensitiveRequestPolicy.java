package com.vanrides.vanrides.filtros;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

@Component
public final class SensitiveRequestPolicy {

    private static final Pattern DRIVER_INVITE = Pattern.compile(
            "^/driver-onboarding/invites/[^/]+/(preview|redeem)$"
    );

    public Optional<BoundedRateLimitStore.Route> routeFor(HttpServletRequest request) {
        if (request == null) {
            return Optional.empty();
        }

        String method = normalizeMethod(request.getMethod());
        String path = normalizedPath(request);
        if ("POST".equals(method)) {
            return postRoute(path);
        }
        if ("GET".equals(method)) {
            return driverInviteRoute(path, "preview")
                    .map(ignored -> BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW);
        }
        return Optional.empty();
    }

    String normalizedPath(HttpServletRequest request) {
        return ApplicationRequestPath.resolve(request);
    }

    private Optional<BoundedRateLimitStore.Route> postRoute(String path) {
        return switch (path) {
            case "/login" -> Optional.of(BoundedRateLimitStore.Route.LOGIN);
            case "/auth/refresh" -> Optional.of(BoundedRateLimitStore.Route.REFRESH);
            case "/auth/email-verifications/confirm" ->
                    Optional.of(BoundedRateLimitStore.Route.EMAIL_CONFIRM);
            case "/auth/email-verifications/resend" ->
                    Optional.of(BoundedRateLimitStore.Route.EMAIL_RESEND);
            case "/auth/email-verifications/change-email" ->
                    Optional.of(BoundedRateLimitStore.Route.CHANGE_EMAIL);
            case "/company-admins/bootstrap" -> Optional.of(BoundedRateLimitStore.Route.BOOTSTRAP);
            case "/passageiros" -> Optional.of(BoundedRateLimitStore.Route.PASSENGER_REGISTRATION);
            case "/motoristas" -> Optional.of(BoundedRateLimitStore.Route.DRIVER_REGISTRATION);
            case "/passageiro-onboarding/invite-codes/redeem" ->
                    Optional.of(BoundedRateLimitStore.Route.PASSENGER_INVITE_REDEEM);
            default -> driverInviteRoute(path, "redeem")
                    .map(ignored -> BoundedRateLimitStore.Route.DRIVER_INVITE_REDEEM);
        };
    }

    private Optional<String> driverInviteRoute(String path, String expectedAction) {
        if (!isDriverInviteRoute(path, expectedAction)) {
            return Optional.empty();
        }
        return Optional.of(expectedAction);
    }

    static boolean isDriverInviteRoute(String path, String expectedAction) {
        if (path == null || expectedAction == null) {
            return false;
        }
        java.util.regex.Matcher matcher = DRIVER_INVITE.matcher(path);
        return matcher.matches() && expectedAction.equals(matcher.group(1));
    }

    private String normalizeMethod(String method) {
        if (method == null) {
            return "";
        }
        String normalized = method.trim().toUpperCase(Locale.ROOT);
        return "HEAD".equals(normalized) ? "GET" : normalized;
    }
}
