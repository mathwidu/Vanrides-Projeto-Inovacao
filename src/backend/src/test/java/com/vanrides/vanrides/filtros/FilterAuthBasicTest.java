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
import org.springframework.test.util.ReflectionTestUtils;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FilterAuthBasicTest {

    @Mock
    private AuthorizationService authorizationService;

    private FilterAuthBasic filter;

    @BeforeEach
    void setUp() {
        filter = new FilterAuthBasic();
        ReflectionTestUtils.setField(filter, "authorizationService", authorizationService);
    }

    @Test
    void shouldAllowPreflightWithoutAuth() throws Exception {
        MockHttpServletRequest request = protectedRequest("OPTIONS");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowBootstrapAdminRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/company-admins/bootstrap");
        request.setServletPath("/company-admins/bootstrap");
        request.setRequestURI("/company-admins/bootstrap");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldAllowUploadedAvatarsWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest(
                "GET",
                "/uploads/avatars/passenger-21-test.png"
        );
        request.setServletPath("/uploads/avatars/passenger-21-test.png");
        request.setRequestURI("/uploads/avatars/passenger-21-test.png");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
    }

    @Test
    void shouldProtectAdminPassengersRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/company-admins/passageiros");
        request.setServletPath("/company-admins/passageiros");
        request.setRequestURI("/company-admins/passageiros");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldProtectAdminGuardiansRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/company-admins/responsaveis");
        request.setServletPath("/company-admins/responsaveis");
        request.setRequestURI("/company-admins/responsaveis");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldProtectAdminLinksRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/company-admins/vinculos/responsaveis-passageiros");
        request.setServletPath("/company-admins/vinculos/responsaveis-passageiros");
        request.setRequestURI("/company-admins/vinculos/responsaveis-passageiros");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldProtectGuardianPasswordRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/responsaveis/minha-senha");
        request.setServletPath("/responsaveis/minha-senha");
        request.setRequestURI("/responsaveis/minha-senha");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldProtectTripsRouteWithoutAuth() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/viagens/motorista/7/hoje");
        request.setServletPath("/viagens/motorista/7/hoje");
        request.setRequestURI("/viagens/motorista/7/hoje");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldRejectProtectedRouteWhenAuthorizationHeaderIsMissing() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Cabeçalho Authorization ausente", response.getErrorMessage());
    }

    @Test
    void shouldAllowBearerWhenTokenIsValid() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        request.addHeader("Authorization", "Bearer valid-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authorizationService.authenticate("Bearer valid-token")).thenReturn(
                new AuthenticatedUser(21L, "MOTORISTA", "driver@vanrides.com", null, null, 77L)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("DRIVER", request.getAttribute("userRole"));
        assertEquals(21L, request.getAttribute("userId"));
        assertEquals("driver@vanrides.com", request.getAttribute("userEmail"));
        assertEquals(77L, request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldAllowPassengerOnboardingRouteWithoutCompanyContext() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/passageiro-onboarding/status");
        request.setServletPath("/passageiro-onboarding/status");
        request.setRequestURI("/passageiro-onboarding/status");
        request.addHeader("Authorization", "Bearer onboarding-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authorizationService.authenticate("Bearer onboarding-token")).thenReturn(
                new AuthenticatedUser(31L, "PASSENGER", "onboarding@vanrides.com", "Onboarding", null, null)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("PASSENGER", request.getAttribute("userRole"));
        assertEquals(31L, request.getAttribute("userId"));
        assertNull(request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldRejectBearerWhenTokenIsInvalid() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        request.addHeader("Authorization", "Bearer bad-token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();

        when(authorizationService.authenticate("Bearer bad-token"))
                .thenThrow(new IllegalArgumentException("Token inválido ou expirado"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Token inválido ou expirado", response.getErrorMessage());
    }

    @Test
    void shouldAllowBasicWhenCredentialsMatchMotorista() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        request.addHeader("Authorization", "Basic mock-basic");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate("Basic mock-basic")).thenReturn(
                new AuthenticatedUser(44L, "MOTORISTA", "driver@vanrides.com", "Motorista", null, 88L)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("DRIVER", request.getAttribute("userRole"));
        assertEquals(44L, request.getAttribute("userId"));
        assertEquals("driver@vanrides.com", request.getAttribute("userEmail"));
        assertEquals(88L, request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldAllowBasicWhenCredentialsMatchCompanyAdmin() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        request.addHeader("Authorization", "Basic admin-basic");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate("Basic admin-basic")).thenReturn(
                new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin@vanrides.com", "Admin Portal", null, 77L)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("COMPANY_ADMIN", request.getAttribute("userRole"));
        assertEquals(90L, request.getAttribute("userId"));
        assertEquals(77L, request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldAllowBasicWhenCredentialsMatchGuardian() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("PUT", "/responsaveis/minha-senha");
        request.setServletPath("/responsaveis/minha-senha");
        request.setRequestURI("/responsaveis/minha-senha");
        request.addHeader("Authorization", "Basic guardian-basic");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate("Basic guardian-basic")).thenReturn(
                new AuthenticatedUser(71L, "GUARDIAN", "responsavel@vanrides.com", "Responsavel", null, 88L)
        );

        filter.doFilterInternal(request, response, chain);

        assertNotNull(chain.getRequest());
        assertEquals(200, response.getStatus());
        assertEquals("GUARDIAN", request.getAttribute("userRole"));
        assertEquals(71L, request.getAttribute("userId"));
        assertEquals(88L, request.getAttribute("userCompanyId"));
    }

    @Test
    void shouldRejectWhenAuthorizationSchemeIsUnsupported() throws Exception {
        MockHttpServletRequest request = protectedRequest("GET");
        request.addHeader("Authorization", "Token abc");
        MockHttpServletResponse response = new MockHttpServletResponse();
        MockFilterChain chain = new MockFilterChain();
        when(authorizationService.authenticate("Token abc"))
                .thenThrow(new IllegalArgumentException("Authorization deve usar Bearer ou Basic"));

        filter.doFilterInternal(request, response, chain);

        assertNull(chain.getRequest());
        assertEquals(401, response.getStatus());
        assertEquals("Authorization deve usar Bearer ou Basic", response.getErrorMessage());
    }

    private MockHttpServletRequest protectedRequest(String method) {
        MockHttpServletRequest request = new MockHttpServletRequest(method, "/presencas");
        request.setServletPath("/presencas");
        request.setRequestURI("/presencas");
        return request;
    }
}
