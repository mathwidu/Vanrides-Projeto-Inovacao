package com.vanrides.vanrides.filtros;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.time.Duration;
import java.util.concurrent.atomic.AtomicLong;

import static org.assertj.core.api.Assertions.assertThat;

class BasicRateLimitFilterTest {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    @Test
    void shouldThrottleSensitiveRouteAtNPlusOneWithRemainingRetryAfter() throws Exception {
        Fixture fixture = fixture(true, 2, 10, "");

        assertThat(execute(fixture.filter, request("POST", "/login", "10.0.0.10")).chained).isTrue();
        assertThat(execute(fixture.filter, request("POST", "/login", "10.0.0.10")).chained).isTrue();

        Result blocked = execute(fixture.filter, request("POST", "/login", "10.0.0.10"));

        assertThat(blocked.chained).isFalse();
        assertThat(blocked.response.getStatus()).isEqualTo(429);
        assertThat(blocked.response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(errorCode(blocked.response)).isEqualTo("RATE_LIMITED");
        assertThat(blocked.response.isCommitted()).isFalse();
    }

    @Test
    void shouldIgnoreSpoofedForwardedHeaderFromDirectPeer() throws Exception {
        Fixture fixture = fixture(true, 2, 10, "");

        MockHttpServletRequest first = request("POST", "/login", "10.0.0.10");
        first.addHeader("X-Forwarded-For", "198.51.100.1");
        MockHttpServletRequest second = request("POST", "/login", "10.0.0.10");
        second.addHeader("X-Forwarded-For", "198.51.100.2");
        MockHttpServletRequest third = request("POST", "/login", "10.0.0.10");
        third.addHeader("X-Forwarded-For", "not-an-address");

        assertThat(execute(fixture.filter, first).chained).isTrue();
        assertThat(execute(fixture.filter, second).chained).isTrue();
        assertThat(execute(fixture.filter, third).response.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldUseForwardedClientOnlyWhenImmediatePeerIsTrusted() throws Exception {
        Fixture fixture = fixture(true, 1, 10, "127.0.0.1/32");

        MockHttpServletRequest clientA = request("POST", "/login", "127.0.0.1");
        clientA.addHeader("X-Forwarded-For", "198.51.100.10");
        MockHttpServletRequest clientB = request("POST", "/login", "127.0.0.1");
        clientB.addHeader("X-Forwarded-For", "198.51.100.11");
        MockHttpServletRequest clientAAgain = request("POST", "/login", "127.0.0.1");
        clientAAgain.addHeader("X-Forwarded-For", "198.51.100.10");

        assertThat(execute(fixture.filter, clientA).chained).isTrue();
        assertThat(execute(fixture.filter, clientB).chained).isTrue();
        assertThat(execute(fixture.filter, clientAAgain).response.getStatus()).isEqualTo(429);
    }

    @Test
    void shouldRejectMalformedForwardedChainFromTrustedPeerWithoutCallingApplication() throws Exception {
        Fixture fixture = fixture(true, 10, 10, "127.0.0.1/32");
        MockHttpServletRequest request = request("POST", "/login", "127.0.0.1");
        request.addHeader("X-Forwarded-For", "198.51.100.10,,127.0.0.1");

        Result result = execute(fixture.filter, request);

        assertThat(result.chained).isFalse();
        assertThat(result.response.getStatus()).isEqualTo(400);
        assertThat(errorCode(result.response)).isEqualTo("INVALID_FORWARDED_ADDRESS");
        assertThat(result.response.isCommitted()).isFalse();
        assertThat(fixture.registry.get(RateLimitMetrics.FORWARDED_INVALID_METRIC)
                .tag("reason", "empty_forwarded_hop").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldPartitionByRouteAndNormalizeDynamicInviteTokens() throws Exception {
        Fixture fixture = fixture(true, 1, 10, "");

        assertThat(execute(fixture.filter,
                request("POST", "/driver-onboarding/invites/token-a/redeem", "10.0.0.10")).chained)
                .isTrue();
        assertThat(execute(fixture.filter,
                request("POST", "/driver-onboarding/invites/token-b/redeem", "10.0.0.10"))
                .response.getStatus()).isEqualTo(429);
        assertThat(execute(fixture.filter, request("POST", "/auth/refresh", "10.0.0.10")).chained)
                .isTrue();
    }

    @Test
    void shouldFailClosedForNewIdentityWhenRouteCapacityIsFull() throws Exception {
        Fixture fixture = fixture(true, 10, 1, "");

        assertThat(execute(fixture.filter, request("POST", "/login", "10.0.0.10")).chained).isTrue();
        Result capacityRejected = execute(fixture.filter, request("POST", "/login", "10.0.0.11"));

        assertThat(capacityRejected.chained).isFalse();
        assertThat(capacityRejected.response.getStatus()).isEqualTo(429);
        assertThat(capacityRejected.response.getHeader("Retry-After")).isEqualTo("60");
        assertThat(fixture.registry.get(RateLimitMetrics.CAPACITY_REJECTIONS_METRIC)
                .tag("route", "login").counter().count()).isEqualTo(1);
    }

    @Test
    void shouldSkipPreflightDisabledAndNonSensitiveRequests() throws Exception {
        Fixture enabled = fixture(true, 1, 10, "");
        MockHttpServletRequest preflight = request("OPTIONS", "/login", "10.0.0.10");
        preflight.addHeader("Origin", "https://app.example");
        preflight.addHeader("Access-Control-Request-Method", "POST");

        assertThat(execute(enabled.filter, preflight).chained).isTrue();
        assertThat(execute(enabled.filter, request("GET", "/motoristas", "10.0.0.10")).chained)
                .isTrue();
        assertThat(execute(enabled.filter, request("GET", "/motoristas", "10.0.0.10")).chained)
                .isTrue();

        Fixture disabled = fixture(false, 1, 10, "");
        assertThat(execute(disabled.filter, request("POST", "/login", "10.0.0.10")).chained)
                .isTrue();
        assertThat(execute(disabled.filter, request("POST", "/login", "10.0.0.10")).chained)
                .isTrue();
    }

    private Fixture fixture(boolean enabled, int limit, int capacity, String trustedCidrs) {
        AtomicLong ticker = new AtomicLong();
        SimpleMeterRegistry registry = new SimpleMeterRegistry();
        BasicRateLimitFilter filter = new BasicRateLimitFilter(
                OBJECT_MAPPER,
                enabled,
                new SensitiveRequestPolicy(),
                new TrustedProxyClientAddressResolver(trustedCidrs, 1024, 10, new byte[32]),
                new BoundedRateLimitStore(limit, Duration.ofSeconds(60), capacity, ticker::get),
                new RateLimitMetrics(registry, capacity)
        );
        return new Fixture(filter, registry);
    }

    private Result execute(BasicRateLimitFilter filter, MockHttpServletRequest request) throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        filter.doFilterInternal(request, response, chain);
        return new Result(response, chain.getRequest() != null);
    }

    private MockHttpServletRequest request(String method, String path, String remoteAddress) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRemoteAddr(remoteAddress);
        return request;
    }

    private String errorCode(MockHttpServletResponse response) throws Exception {
        JsonNode body = OBJECT_MAPPER.readTree(response.getContentAsByteArray());
        return body.path("code").asText();
    }

    private record Fixture(BasicRateLimitFilter filter, SimpleMeterRegistry registry) {
    }

    private record Result(MockHttpServletResponse response, boolean chained) {
    }
}
