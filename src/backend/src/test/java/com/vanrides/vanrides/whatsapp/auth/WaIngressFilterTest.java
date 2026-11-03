package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.whatsapp.api.WaHttp;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import jakarta.servlet.DispatcherType;
import jakarta.servlet.http.MappingMatch;
import java.util.UUID;
import java.util.List;
import java.util.concurrent.atomic.AtomicBoolean;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletMapping;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class WaIngressFilterTest {
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WaCredentials credentials = new WaCredentials(jdbc, settings(), CLOCK);
    private final WaIngressFilter filter = filter(settings());
    private WaIngressFilter filter(WaSettings settings) {
        return new WaIngressFilter(settings, credentials, new WaRateLimiter(), new TrustedProxyClientAddressResolver("", 1024, 10));
    }
    @SuppressWarnings("unchecked")
    private void returns(WaPrincipal principal) {
        doReturn(List.of(principal)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }
    private MockHttpServletRequest request(String method, String uri) {
        var request = new MockHttpServletRequest(method, uri); request.setRemoteAddr("127.0.0.1");
        request.setContentType("application/json"); return request;
    }
    private MockHttpServletResponse rejected(MockHttpServletRequest request, int expected) throws Exception {
        var response = new MockHttpServletResponse(); var passed = new AtomicBoolean();
        filter.doFilter(request, response, (a, b) -> passed.set(true));
        assertThat(passed).isFalse(); assertThat(response.getStatus()).isEqualTo(expected);
        assertThat(response.getContentAsString()).contains("correlationId").doesNotContain("Bearer", "Exception");
        return response;
    }
    @Test void legacyRoutesAreUntouchedAndUnknownWhatsappDescendantsDeny() throws Exception {
        var passed = new AtomicBoolean(); filter.doFilter(request("GET", "/login"), new MockHttpServletResponse(), (a,b) -> passed.set(true));
        assertThat(passed).isTrue();
        rejected(request("GET", "/driver-app/v1/admin"), 404);
        rejected(request("DELETE", "/driver-app/v1/status"), 404);
        rejected(request("GET", "/integrations/whatsapp/v2/new"), 404);
        verifyNoInteractions(jdbc);
    }
    @Test void originAndNestedForwardAreRejectedBeforeAuthentication() throws Exception {
        var request = request("POST", "/driver-app/v1/pairings/begin"); request.addHeader("Origin", "https://example.invalid");
        rejected(request, 403);
        var nested = request("POST", "/driver-app/v1/pairings/begin"); var response = new MockHttpServletResponse();
        filter.doFilter(nested, response, (a,b) -> {
            nested.setDispatcherType(DispatcherType.FORWARD);
            filter.doFilter(nested, response, (c,d) -> { throw new AssertionError("forward reached controller"); });
        });
        assertThat(response.getStatus()).isEqualTo(403);
    }
    @Test void resolvesContextAndServletPrefixesWithoutOpeningEncodedOrMatrixPaths() throws Exception {
        var request = request("POST", "/root/api/driver-app/v1/pairings/begin");
        request.setContextPath("/root"); request.setServletPath("/api"); request.setPathInfo("/driver-app/v1/pairings/begin");
        request.setHttpServletMapping(new MockHttpServletMapping("driver-app/v1/pairings/begin", "/api/*", "dispatcher", MappingMatch.PATH));
        var passed = new AtomicBoolean(); filter.doFilter(request, new MockHttpServletResponse(), (a,b) -> passed.set(true));
        assertThat(passed).isTrue(); assertThat(request.getAttribute(WaHttp.PATH)).isEqualTo("/driver-app/v1/pairings/begin");
        for (String uri : new String[]{"/driver-app/v1/status;any", "/driver-app/%76/status", "/driver-app//v1/status"})
            rejected(request("GET", uri), 400);
    }
    @Test void separatesTechnicalRolesAndRejectsDuplicatedCredentials() throws Exception {
        var request = request("GET", "/integrations/whatsapp/v1/context"); request.addHeader("Authorization", "Bearer " + key(5));
        returns(new WaPrincipal(WaPrincipal.Kind.CHANNEL, UUID.randomUUID(), null, null, "instance", null, 0, false));
        rejected(request, 403);
        request.addHeader("Authorization", "Bearer " + key(6)); rejected(request, 400);
    }
    @Test void revokedDeviceEscapeHatchIsOnlyForOwnRevokeRoute() throws Exception {
        var principal = new WaPrincipal(WaPrincipal.Kind.DEVICE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(), null, key(7), 1, true);
        returns(principal);
        var revoke = request("POST", "/driver-app/v1/session/revoke"); revoke.addHeader("Authorization", "DPoP " + key(8));
        var passed = new AtomicBoolean(); filter.doFilter(revoke, new MockHttpServletResponse(), (a,b) -> passed.set(true));
        assertThat(passed).isTrue();
        var status = request("GET", "/driver-app/v1/status"); status.addHeader("Authorization", "DPoP " + key(8)); rejected(status, 401);
    }
    @Test void futureRoutesAndOversizedOrFormBodiesNeverReachHandlers() throws Exception {
        var credential = new WaPrincipal(WaPrincipal.Kind.SYSTEM_JOB, UUID.randomUUID(), null, null, "instance", null, 0, false);
        returns(credential);
        var tick = request("POST", "/integrations/whatsapp/v1/jobs/tick");
        tick.addHeader("Authorization", "Bearer " + key(5));
        var reached = new AtomicBoolean();
        filter.doFilter(tick, new MockHttpServletResponse(), (a,b) -> reached.set(true));
        assertThat(reached).isTrue();
        returns(new WaPrincipal(WaPrincipal.Kind.DEVICE, UUID.randomUUID(), UUID.randomUUID(), UUID.randomUUID(),
                null, key(7), 1, false));
        var future = request("POST", "/driver-app/v1/runs/" + UUID.randomUUID() + "/finish");
        future.addHeader("Authorization", "DPoP " + key(8));
        rejected(future, 503);
        var large = request("POST", "/driver-app/v1/pairings/begin"); large.setContent(new byte[32769]); rejected(large, 400);
        var form = request("POST", "/driver-app/v1/pairings/begin"); form.setContentType("application/x-www-form-urlencoded"); rejected(form, 400);
    }
    @Test void disabledConfigurationCannotOpenPairing() throws Exception {
        var request = request("POST", "/driver-app/v1/pairings/begin"); var response = new MockHttpServletResponse();
        filter(new WaSettings(environment().withProperty("vanrides.wa.enabled", "false"))).doFilter(request, response,
            (a,b) -> { throw new AssertionError("disabled pairing reached handler"); });
        assertThat(response.getStatus()).isEqualTo(403);
    }
}
