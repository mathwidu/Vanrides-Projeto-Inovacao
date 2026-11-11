package com.vanrides.vanrides.whatsapp.auth;

import com.vanrides.vanrides.config.TeamApiTestEnvironment;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.whatsapp.config.WaSettings;
import com.vanrides.vanrides.whatsapp.team.WaTeamSettings;
import jakarta.servlet.DispatcherType;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static com.vanrides.vanrides.whatsapp.auth.WaAuthTestSupport.key;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class WaTeamWebBoundaryTest {
    private static final String ORIGIN = "https://api.example.invalid";
    private static final String POLICY = "X-Vanrides-Admission-Policy";
    private final WaTeamSettings team = mock(WaTeamSettings.class);
    private final JdbcTemplate jdbc = mock(JdbcTemplate.class);
    private final WaSettings settings = new WaSettings(TeamApiTestEnvironment.validOnline());
    private final WaIngressFilter filter = new WaIngressFilter(settings, new WaCredentials(jdbc, settings, WaAuthTestSupport.CLOCK),
        new WaRateLimiter(), new TrustedProxyClientAddressResolver("", 1024, 10), team);

    @Test
    void teamRoutesAreExplicitAndRejectCrossOriginForwardAndTechnicalCredentials() throws Exception {
        check(request("GET", "/equipe"), 404, false);
        when(team.enabled()).thenReturn(true);
        when(team.origin()).thenReturn(ORIGIN);
        var html = check(request("GET", "/equipe/"), 200, true);
        assertThat(html.getHeader("Content-Security-Policy")).contains("frame-ancestors 'none'");
        assertThat(html.getHeader("Referrer-Policy")).isEqualTo("no-referrer");
        check(request("GET", "/equipe/secret"), 404, false);
        check(request("GET", "/equipe/api/participants"), 404, false);
        check(request("OPTIONS", "/equipe/api/participants"), 404, false);
        var nested = request("GET", "/equipe");
        nested.setDispatcherType(DispatcherType.FORWARD);
        check(nested, 404, false);
        var mutation = request("POST", "/equipe/api/participants");
        check(mutation, 400, false);
        mutation.addHeader("Origin", "https://foreign.invalid");
        check(mutation, 403, false);
        mutation.removeHeader("Origin");
        mutation.addHeader("Origin", ORIGIN);
        check(mutation, 200, true);
        mutation.addHeader("Authorization", "Bearer " + key(5));
        check(mutation, 403, false);
        verifyNoInteractions(jdbc);
    }

    @Test
    void testDriverRoutesAreExactPostOnlyAndRequireTheTeamOrigin() throws Exception {
        when(team.enabled()).thenReturn(true);
        when(team.origin()).thenReturn(ORIGIN);
        for (String path : List.of("/equipe/api/test-driver", "/equipe/api/test-driver/status")) {
            check(request("GET", path), 404, false);
            check(request("PUT", path), 404, false);
            check(request("POST", path + "/"), 404, false);
            check(request("POST", path + "/extra"), 404, false);
            var mutation = request("POST", path);
            check(mutation, 400, false);
            mutation.addHeader("Origin", "https://foreign.invalid");
            check(mutation, 403, false);
            mutation.removeHeader("Origin");
            mutation.addHeader("Origin", ORIGIN);
            check(mutation, 200, true);
            mutation.addHeader("Authorization", "Bearer " + key(5));
            check(mutation, 403, false);
        }
        verifyNoInteractions(jdbc);
    }

    @Test
    void dynamicIngressHandshakeRequiresStrictModeOnTheAuthenticatedChannelInstance() throws Exception {
        var request = request("POST", "/integrations/whatsapp/v1/team-events");
        request.addHeader("Authorization", "Bearer " + key(5));
        returns(new WaPrincipal(WaPrincipal.Kind.CHANNEL, UUID.randomUUID(), null, null, "test-instance", null, 0, false));
        assertThat(check(request, 400, false).getHeader(POLICY)).isNull();
        request.addHeader(POLICY, "TEAM_V1");
        assertThat(check(request, 503, false).getHeader(POLICY)).isNull();
        when(team.strictIngress("test-instance")).thenReturn(true);
        assertThat(check(request, 200, true).getHeader(POLICY)).isEqualTo("TEAM_V1");
        var legacy = request("POST", "/integrations/whatsapp/v1/events");
        legacy.addHeader("Authorization", "Bearer " + key(5));
        assertThat(check(legacy, 200, true).getHeader(POLICY)).isEqualTo("TEAM_V1");
        request.removeHeader(POLICY);
        request.addHeader(POLICY, "UNKNOWN");
        check(request, 400, false);
    }

    @SuppressWarnings("unchecked")
    private void returns(WaPrincipal principal) {
        doReturn(List.of(principal)).when(jdbc).query(anyString(), any(RowMapper.class), any(Object[].class));
    }

    private static MockHttpServletRequest request(String method, String path) {
        var request = new MockHttpServletRequest(method, path);
        request.setRemoteAddr("127.0.0.1");
        if (method.equals("POST")) request.addHeader("Content-Type", "application/json");
        return request;
    }

    private MockHttpServletResponse check(MockHttpServletRequest request, int status, boolean reached) throws Exception {
        var response = new MockHttpServletResponse();
        var called = new java.util.concurrent.atomic.AtomicBoolean();
        filter.doFilter(request, response, (a, b) -> called.set(true));
        assertThat(called.get()).isEqualTo(reached);
        assertThat(response.getStatus()).isEqualTo(status);
        return response;
    }
}
