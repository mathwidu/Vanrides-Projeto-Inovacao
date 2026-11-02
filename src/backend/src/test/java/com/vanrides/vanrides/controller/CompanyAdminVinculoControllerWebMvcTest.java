package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminGuardianSummaryResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestDetailResponse;
import com.vanrides.vanrides.dto.AdminCompanyLinkRequestSummaryResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeDetailResponse;
import com.vanrides.vanrides.dto.AdminInviteCodeSummaryResponse;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroMotoristaLinkResponse;
import com.vanrides.vanrides.dto.AdminResponsavelPassageiroLinkResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminDriverInviteService;
import com.vanrides.vanrides.service.CompanyAdminDriverLinkRequestService;
import com.vanrides.vanrides.service.CompanyAdminVinculoService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminVinculoController.class)
class CompanyAdminVinculoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyAdminVinculoService companyAdminVinculoService;

    @MockitoBean
    private PassengerOnboardingService passengerOnboardingService;

    @MockitoBean
    private CompanyAdminDriverInviteService companyAdminDriverInviteService;

    @MockitoBean
    private CompanyAdminDriverLinkRequestService companyAdminDriverLinkRequestService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private PassageiroRepository passageiroRepository;

    @MockitoBean
    private MotoristaRepository motoristaRepository;

    @MockitoBean
    private GuardianRepository guardianRepository;

    @MockitoBean
    private CompanyAdminRepository companyAdminRepository;

    @Test
    void shouldListPassengerMotoristaLinks() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.vinc@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.vinc@vanrides.dev", "Admin", null, 66L));
        when(companyAdminVinculoService.listarVinculosPassageiroMotorista(66L)).thenReturn(List.of(
                new AdminPassageiroMotoristaLinkResponse(21L, "Aluno Um", true,
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007"))
        ));

        mockMvc.perform(get("/company-admins/vinculos/passageiros-motoristas")
                        .servletPath("/company-admins/vinculos/passageiros-motoristas")
                        .header("Authorization", basicHeader("admin.vinc@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].passageiroId").value(21))
                .andExpect(jsonPath("$[0].motorista.id").value(7));
    }

    @Test
    void shouldRemovePassengerMotoristaLink() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.unlink@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.unlink@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/passageiros/21/motorista")
                        .servletPath("/company-admins/passageiros/21/motorista")
                        .header("Authorization", basicHeader("admin.unlink@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldListGuardianPassengerLinks() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.guardlink@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.guardlink@vanrides.dev", "Admin", null, 66L));
        when(companyAdminVinculoService.listarVinculosResponsavelPassageiro(66L)).thenReturn(List.of(
                new AdminResponsavelPassageiroLinkResponse(
                        new AdminGuardianSummaryResponse(71L, "Mae Portal", "mae@vanrides.dev", "51999990001"),
                        new AdminPassageiroLinkedSummaryResponse(21L, "Aluno Um", "aluno@vanrides.dev", true, null)
                )
        ));

        mockMvc.perform(get("/company-admins/vinculos/responsaveis-passageiros")
                        .servletPath("/company-admins/vinculos/responsaveis-passageiros")
                        .header("Authorization", basicHeader("admin.guardlink@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].responsavel.id").value(71))
                .andExpect(jsonPath("$[0].passageiro.id").value(21));
    }

    @Test
    void shouldCreateGuardianPassengerLink() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.createlink@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.createlink@vanrides.dev", "Admin", null, 66L));
        when(companyAdminVinculoService.criarVinculoResponsavelPassageiro(71L, 21L, 66L)).thenReturn(
                new AdminResponsavelPassageiroLinkResponse(
                        new AdminGuardianSummaryResponse(71L, "Mae Portal", "mae@vanrides.dev", "51999990001"),
                        new AdminPassageiroLinkedSummaryResponse(21L, "Aluno Um", "aluno@vanrides.dev", true, null)
                )
        );

        mockMvc.perform(post("/company-admins/responsaveis/71/passageiros")
                        .servletPath("/company-admins/responsaveis/71/passageiros")
                        .header("Authorization", basicHeader("admin.createlink@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "passageiroId":21
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.passageiro.id").value(21));
    }

    @Test
    void shouldRejectLinkCreateWhenPassageiroIdIsMissing() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.badlink@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.badlink@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/responsaveis/71/passageiros")
                        .servletPath("/company-admins/responsaveis/71/passageiros")
                        .header("Authorization", basicHeader("admin.badlink@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldRemoveGuardianPassengerLink() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.removelink@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.removelink@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/responsaveis/71/passageiros/21")
                        .servletPath("/company-admins/responsaveis/71/passageiros/21")
                        .header("Authorization", basicHeader("admin.removelink@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldForbidPassengerFromAdminLinksRoutes() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(get("/company-admins/vinculos/responsaveis-passageiros")
                        .servletPath("/company-admins/vinculos/responsaveis-passageiros")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminVinculoService, never()).listarVinculosResponsavelPassageiro(eq(66L));
    }

    @Test
    void shouldListPendingAccessRequests() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.requests@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.requests@vanrides.dev", "Admin", null, 66L));
        when(passengerOnboardingService.listPendingRequests(66L)).thenReturn(List.of(
                new AdminCompanyLinkRequestSummaryResponse(
                        8L,
                        21L,
                        "Aluno Um",
                        "aluno@vanrides.dev",
                        "Empresa Azul",
                        "SEARCH_REQUEST",
                        java.time.OffsetDateTime.parse("2026-04-17T13:00:00Z"),
                        true
                )
        ));

        mockMvc.perform(get("/company-admins/access-requests")
                        .servletPath("/company-admins/access-requests")
                        .header("Authorization", basicHeader("admin.requests@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(8))
                .andExpect(jsonPath("$[0].passageiroNome").value("Aluno Um"));
    }

    @Test
    void shouldApproveAccessRequest() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.approve@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.approve@vanrides.dev", "Admin", null, 66L));
        when(passengerOnboardingService.approveRequest(8L, 66L, 90L, 7L, "Aprovado pelo portal", List.of(10L, 11L))).thenReturn(
                new AdminCompanyLinkRequestDetailResponse(
                        8L,
                        21L,
                        "Aluno Um",
                        "aluno@vanrides.dev",
                        "51999990021",
                        "Empresa Azul",
                        "SEARCH_REQUEST",
                        "APPROVED",
                        java.time.OffsetDateTime.parse("2026-04-17T13:00:00Z"),
                        true,
                        new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007")
                )
        );

        mockMvc.perform(post("/company-admins/access-requests/8/approve")
                        .servletPath("/company-admins/access-requests/8/approve")
                        .header("Authorization", basicHeader("admin.approve@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motoristaId":7,
                                  "reason":"Aprovado pelo portal",
                                  "servicoIds":[10,11]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("APPROVED"))
                .andExpect(jsonPath("$.motoristaAtual.id").value(7));
    }

    @Test
    void shouldListInviteCodes() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.invites@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.invites@vanrides.dev", "Admin", null, 66L));
        when(passengerOnboardingService.listInviteCodes(66L)).thenReturn(List.of(
                new AdminInviteCodeSummaryResponse(
                        5L,
                        "INV-2026-ABCD",
                        "ACTIVE",
                        10,
                        2,
                        java.time.OffsetDateTime.parse("2026-04-30T23:59:59Z"),
                        java.time.OffsetDateTime.parse("2026-04-17T10:00:00Z")
                )
        ));

        mockMvc.perform(get("/company-admins/invite-codes")
                        .servletPath("/company-admins/invite-codes")
                        .header("Authorization", basicHeader("admin.invites@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].code").value("INV-2026-ABCD"))
                .andExpect(jsonPath("$[0].status").value("ACTIVE"));
    }

    @Test
    void shouldCreateInviteCode() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.createinvite@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.createinvite@vanrides.dev", "Admin", null, 66L));
        when(passengerOnboardingService.createInviteCode(eq(66L), eq(90L), any(com.vanrides.vanrides.dto.AdminInviteCodeCreateRequest.class))).thenReturn(
                new AdminInviteCodeDetailResponse(
                        5L,
                        "INV-2026-ABCD",
                        "ACTIVE",
                        10,
                        0,
                        java.time.OffsetDateTime.parse("2026-04-30T23:59:59Z"),
                        java.time.OffsetDateTime.parse("2026-04-17T10:00:00Z"),
                        null,
                        List.of()
                )
        );

        mockMvc.perform(post("/company-admins/invite-codes")
                        .servletPath("/company-admins/invite-codes")
                        .header("Authorization", basicHeader("admin.createinvite@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "expiresAt":"2026-04-30T23:59:59Z",
                                  "maxUses":10
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.code").value("INV-2026-ABCD"))
                .andExpect(jsonPath("$.status").value("ACTIVE"));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
