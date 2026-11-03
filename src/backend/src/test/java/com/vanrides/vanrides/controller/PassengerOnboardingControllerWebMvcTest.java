package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.dto.PendingCompanyLinkRequestResponse;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassengerOnboardingController.class)
class PassengerOnboardingControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PassengerOnboardingService passengerOnboardingService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @Test
    void shouldReturnPassengerOnboardingStatus() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, null));
        when(passengerOnboardingService.getStatus(21L)).thenReturn(
                new PassengerOnboardingStatusResponse(
                        "PENDING_APPROVAL",
                        null,
                        null,
                        null,
                        new PendingCompanyLinkRequestResponse(7L, 55L, "Empresa Azul", "PENDING", OffsetDateTime.parse("2026-04-17T10:15:30Z"))
                )
        );

        mockMvc.perform(get("/passageiro-onboarding/status")
                        .servletPath("/passageiro-onboarding/status")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyLinkStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.pendingRequest.companyName").value("Empresa Azul"));
    }

    @Test
    void shouldSearchCompaniesForPassengerOnboarding() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, null));
        when(passengerOnboardingService.searchCompanies("azul")).thenReturn(List.of(
                new com.vanrides.vanrides.dto.PassengerCompanySearchResponse(55L, "Empresa Azul", "empresa-azul")
        ));

        mockMvc.perform(get("/passageiro-onboarding/companies")
                        .param("query", "azul")
                        .servletPath("/passageiro-onboarding/companies")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(55))
                .andExpect(jsonPath("$[0].slug").value("empresa-azul"));
    }

    @Test
    void shouldCreateCompanyAccessRequest() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, null));
        when(passengerOnboardingService.createRequest(21L, 55L)).thenReturn(
                new PassengerOnboardingStatusResponse(
                        "PENDING_APPROVAL",
                        null,
                        null,
                        null,
                        new PendingCompanyLinkRequestResponse(7L, 55L, "Empresa Azul", "PENDING", OffsetDateTime.parse("2026-04-17T10:15:30Z"))
                )
        );

        mockMvc.perform(post("/passageiro-onboarding/requests")
                        .servletPath("/passageiro-onboarding/requests")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"companyId":55}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.companyLinkStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.pendingRequest.companyId").value(55));
    }

    @Test
    void shouldRedeemInviteCodeAndReturnLinkedStatus() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, null));
        when(passengerOnboardingService.redeemInviteCode(21L, "INV-2026-ABCD")).thenReturn(
                new PassengerOnboardingStatusResponse("COMPANY_LINKED", 55L, "Empresa Azul", null, null)
        );

        mockMvc.perform(post("/passageiro-onboarding/invite-codes/redeem")
                        .servletPath("/passageiro-onboarding/invite-codes/redeem")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"code":"INV-2026-ABCD"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.companyLinkStatus").value("COMPANY_LINKED"))
                .andExpect(jsonPath("$.companyId").value(55));
    }

    @Test
    void shouldUpgradePassengerSessionAfterCompanyLink() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, null));

        Passageiro passageiro = new Passageiro();
        passageiro.setId(21L);
        passageiro.setNome("Passageiro");
        passageiro.setEmail("passageiro@vanrides.dev");
        com.vanrides.vanrides.model.Company company = new com.vanrides.vanrides.model.Company("Empresa Azul", "empresa-azul");
        company.setId(55L);
        passageiro.setCompany(company);

        when(passengerOnboardingService.requireOperationalPassenger(21L)).thenReturn(passageiro);
        when(jwtService.generateAccessToken(21L, "PASSENGER", "passageiro@vanrides.dev", 55L)).thenReturn("linked-access-token");
        when(refreshTokenService.issueRefreshToken(any(AuthenticatedUser.class))).thenReturn("linked-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        mockMvc.perform(post("/passageiro-onboarding/session-upgrade")
                        .servletPath("/passageiro-onboarding/session-upgrade")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("linked-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("linked-refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"));
    }

    @Test
    void shouldForbidAdminFromPassengerOnboardingRoute() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin@vanrides.dev", "Admin", null, 55L));

        mockMvc.perform(get("/passageiro-onboarding/status")
                        .servletPath("/passageiro-onboarding/status")
                        .header("Authorization", basicHeader("admin@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(passengerOnboardingService, never()).getStatus(90L);
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
