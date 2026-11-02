package com.vanrides.vanrides.filtros;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletMapping;

import jakarta.servlet.http.MappingMatch;

import java.util.Optional;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

class SensitiveRequestPolicyTest {

    private final SensitiveRequestPolicy policy = new SensitiveRequestPolicy();

    @ParameterizedTest
    @MethodSource("sensitiveRoutes")
    void shouldMapOnlyClosedSensitiveFamilies(
            String method,
            String path,
            BoundedRateLimitStore.Route expected
    ) {
        assertThat(policy.routeFor(request(method, path))).contains(expected);
    }

    @Test
    void shouldNormalizeDifferentDriverInviteTokensIntoOneFamily() {
        Optional<BoundedRateLimitStore.Route> first = policy.routeFor(request(
                "GET",
                "/driver-onboarding/invites/token-one/preview"
        ));
        Optional<BoundedRateLimitStore.Route> second = policy.routeFor(request(
                "GET",
                "/driver-onboarding/invites/token-two/preview"
        ));

        assertThat(first).contains(BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW);
        assertThat(second).isEqualTo(first);
    }

    @Test
    void shouldIgnoreQueryValuesAndRemoveMatrixParametersLikeSpringMvc() {
        MockHttpServletRequest request = request("POST", "/login;source=mobile");
        request.setRequestURI("/login;source=mobile?email=never-part-of-key");

        assertThat(policy.routeFor(request)).contains(BoundedRateLimitStore.Route.LOGIN);
        assertThat(policy.normalizedPath(request)).isEqualTo("/login");
    }

    @Test
    void shouldNotStripContextPathTwiceFromServletPath() {
        MockHttpServletRequest request = request("POST", "/auth/refresh");
        request.setContextPath("/auth");
        request.setRequestURI("/auth/auth/refresh");

        assertThat(policy.normalizedPath(request)).isEqualTo("/auth/refresh");
        assertThat(policy.routeFor(request)).contains(BoundedRateLimitStore.Route.REFRESH);
    }

    @Test
    void shouldUsePathInfoForPrefixMappedDispatcherServlet() {
        MockHttpServletRequest request = request("POST", "/api");
        request.setContextPath("/vanrides");
        request.setPathInfo("/login");
        request.setRequestURI("/vanrides/api/login");
        request.setHttpServletMapping(new MockHttpServletMapping(
                "login", "/api/*", "dispatcherServlet", MappingMatch.PATH));

        assertThat(policy.normalizedPath(request)).isEqualTo("/login");
        assertThat(policy.routeFor(request)).contains(BoundedRateLimitStore.Route.LOGIN);
    }

    @Test
    void shouldMapHeadPreviewInsidePrefixMappedDispatcherServlet() {
        MockHttpServletRequest request = request("HEAD", "/api");
        request.setPathInfo("/driver-onboarding/invites/token/preview");
        request.setRequestURI("/api/driver-onboarding/invites/token/preview");
        request.setHttpServletMapping(new MockHttpServletMapping(
                "driver-onboarding/invites/token/preview",
                "/api/*",
                "dispatcherServlet",
                MappingMatch.PATH
        ));

        assertThat(policy.routeFor(request))
                .contains(BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW);
    }

    @Test
    void shouldRemoveContextPathOnlyFromRequestUriFallback() {
        MockHttpServletRequest request = request("POST", "");
        request.setContextPath("/vanrides");
        request.setServletPath("");
        request.setRequestURI("/vanrides/login");

        assertThat(policy.normalizedPath(request)).isEqualTo("/login");
        assertThat(policy.routeFor(request)).contains(BoundedRateLimitStore.Route.LOGIN);
    }

    @ParameterizedTest
    @MethodSource("nonSensitiveLookalikes")
    void shouldNotRateLimitLookalikesOrWrongMethods(String method, String path) {
        assertThat(policy.routeFor(request(method, path))).isEmpty();
    }

    private static Stream<Arguments> sensitiveRoutes() {
        return Stream.of(
                Arguments.of("POST", "/login", BoundedRateLimitStore.Route.LOGIN),
                Arguments.of("POST", "/auth/refresh", BoundedRateLimitStore.Route.REFRESH),
                Arguments.of("POST", "/auth/email-verifications/confirm", BoundedRateLimitStore.Route.EMAIL_CONFIRM),
                Arguments.of("POST", "/auth/email-verifications/resend", BoundedRateLimitStore.Route.EMAIL_RESEND),
                Arguments.of("POST", "/auth/email-verifications/change-email", BoundedRateLimitStore.Route.CHANGE_EMAIL),
                Arguments.of("POST", "/company-admins/bootstrap", BoundedRateLimitStore.Route.BOOTSTRAP),
                Arguments.of("POST", "/passageiros", BoundedRateLimitStore.Route.PASSENGER_REGISTRATION),
                Arguments.of("POST", "/motoristas", BoundedRateLimitStore.Route.DRIVER_REGISTRATION),
                Arguments.of("GET", "/driver-onboarding/invites/abc/preview", BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW),
                Arguments.of("HEAD", "/driver-onboarding/invites/abc/preview", BoundedRateLimitStore.Route.DRIVER_INVITE_PREVIEW),
                Arguments.of("POST", "/driver-onboarding/invites/abc/redeem", BoundedRateLimitStore.Route.DRIVER_INVITE_REDEEM),
                Arguments.of("POST", "/passageiro-onboarding/invite-codes/redeem", BoundedRateLimitStore.Route.PASSENGER_INVITE_REDEEM)
        );
    }

    private static Stream<Arguments> nonSensitiveLookalikes() {
        return Stream.of(
                Arguments.of("GET", "/login"),
                Arguments.of("GET", "/auth/email-verifications/confirm"),
                Arguments.of("HEAD", "/auth/email-verifications/confirm"),
                Arguments.of("POST", "/login/"),
                Arguments.of("POST", "/driver-onboarding/invites//redeem"),
                Arguments.of("POST", "/driver-onboarding/invites/abc/preview"),
                Arguments.of("GET", "/driver-onboarding/invites/abc/redeem"),
                Arguments.of("POST", "/passageiro-onboarding/invite-codes/redeem-extra"),
                Arguments.of("POST", "/unrelated")
        );
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        return request;
    }
}
