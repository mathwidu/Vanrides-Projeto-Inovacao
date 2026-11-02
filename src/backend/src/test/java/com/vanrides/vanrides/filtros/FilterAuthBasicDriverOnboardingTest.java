package com.vanrides.vanrides.filtros;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpServletMapping;
import org.springframework.test.util.ReflectionTestUtils;

import jakarta.servlet.http.MappingMatch;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilterAuthBasicDriverOnboardingTest {

    @Mock
    private AuthorizationService authorizationService;

    private FilterAuthBasic filter;

    @BeforeEach
    void setUp() {
        filter = new FilterAuthBasic();
        ReflectionTestUtils.setField(filter, "authorizationService", authorizationService);
    }

    @Test
    void shouldAllowDriverOnboardingRouteWithoutCompanyContext() throws Exception {
        MockHttpServletRequest request = request("GET", "/driver-onboarding/status");
        request.addHeader("Authorization", "Bearer driver-onboarding-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authorizationService.authenticate("Bearer driver-onboarding-token")).thenReturn(
                new AuthenticatedUser(41L, "DRIVER", "driver@vanrides.com", "Driver", null, null)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("DRIVER", request.getAttribute("userRole"));
        assertEquals(41L, request.getAttribute("userId"));
        assertNull(request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldAllowPublicDriverInvitePreviewWithoutAuth() throws Exception {
        MockHttpServletRequest request = request("GET", "/driver-onboarding/invites/abc123/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowPublicDriverInviteHeadPreviewWithoutAuth() throws Exception {
        MockHttpServletRequest request = request("HEAD", "/driver-onboarding/invites/abc123/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowPublicHeadPreviewInsidePrefixMappedDispatcherServlet() throws Exception {
        MockHttpServletRequest request = prefixMappedRequest(
                "HEAD", "/driver-onboarding/invites/abc123/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldKeepProtectedRouteProtectedInsidePrefixMappedDispatcherServlet() throws Exception {
        MockHttpServletRequest request = prefixMappedRequest("GET", "/company-admins/dashboard");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        verify(authorizationService).authenticate(null);
    }

    @Test
    void shouldNotTreatNestedPreviewLookalikeAsPublic() throws Exception {
        MockHttpServletRequest request = request(
                "GET", "/driver-onboarding/invites/abc123/nested/preview");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        verify(authorizationService).authenticate(null);
    }

    @Test
    void shouldAuthenticateDriverInviteRedeemAllowingUnverifiedAccess() throws Exception {
        MockHttpServletRequest request = request("POST", "/driver-onboarding/invites/abc123/redeem");
        request.addHeader("Authorization", "Bearer unverified-driver-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authorizationService.authenticate("Bearer unverified-driver-token", true)).thenReturn(
                new AuthenticatedUser(41L, "DRIVER", "driver@vanrides.com", "Driver", null, null)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        verify(authorizationService).authenticate("Bearer unverified-driver-token", true);
    }

    private MockHttpServletRequest request(String method, String path) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, path);
        request.setServletPath(path);
        request.setRequestURI(path);
        return request;
    }

    private MockHttpServletRequest prefixMappedRequest(String method, String pathInfo) {
        MockHttpServletRequest request = request(method, "/api");
        request.setPathInfo(pathInfo);
        request.setRequestURI("/api" + pathInfo);
        request.setHttpServletMapping(new MockHttpServletMapping(
                pathInfo.substring(1), "/api/*", "dispatcherServlet", MappingMatch.PATH));
        return request;
    }
}
