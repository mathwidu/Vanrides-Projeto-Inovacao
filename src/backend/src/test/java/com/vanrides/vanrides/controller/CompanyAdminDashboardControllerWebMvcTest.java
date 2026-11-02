package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminDashboardSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminDashboardService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminDashboardController.class)
class CompanyAdminDashboardControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private CompanyAdminDashboardService companyAdminDashboardService;

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
    void shouldReturnUnauthorizedWhenDashboardSummaryHasNoAuth() throws Exception {
        mockMvc.perform(get("/company-admins/dashboard/summary")
                        .servletPath("/company-admins/dashboard/summary"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void shouldReturnDashboardSummaryForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.dashboard@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.dashboard@vanrides.dev", "Admin", null, 66L));
        when(companyAdminDashboardService.getSummary(eq(66L), eq(LocalDate.now())))
                .thenReturn(new AdminDashboardSummaryResponse(
                        3,
                        2,
                        7,
                        new AdminDashboardSummaryResponse.ServicosRecorrentesSummary(5, 4, 1),
                        new AdminDashboardSummaryResponse.PresencasServicoSummary(8, 2, 1),
                        new AdminDashboardSummaryResponse.ViagensSummary(4, 1, 2, 1)
                ));

        mockMvc.perform(get("/company-admins/dashboard/summary")
                        .servletPath("/company-admins/dashboard/summary")
                        .header("Authorization", basicHeader("admin.dashboard@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motoristasAtivos").value(3))
                .andExpect(jsonPath("$.rotasAtivas").value(2))
                .andExpect(jsonPath("$.passageirosComPresencaHoje").value(7))
                .andExpect(jsonPath("$.servicosRecorrentes.ativos").value(5))
                .andExpect(jsonPath("$.servicosRecorrentes.automaticos").value(4))
                .andExpect(jsonPath("$.servicosRecorrentes.semMotorista").value(1))
                .andExpect(jsonPath("$.presencasServicoHoje.confirmadas").value(8))
                .andExpect(jsonPath("$.presencasServicoHoje.ausentes").value(2))
                .andExpect(jsonPath("$.presencasServicoHoje.canceladas").value(1))
                .andExpect(jsonPath("$.presencasServicoHoje.totalDecisoes").value(11))
                .andExpect(jsonPath("$.viagensHoje.total").value(4))
                .andExpect(jsonPath("$.viagensHoje.emAndamento").value(1))
                .andExpect(jsonPath("$.viagensHoje.concluidas").value(2))
                .andExpect(jsonPath("$.viagensHoje.planejadas").value(1));
    }

    @Test
    void shouldReturnDashboardSummaryForRequestedDate() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.dashboard@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.dashboard@vanrides.dev", "Admin", null, 66L));
        when(companyAdminDashboardService.getSummary(66L, LocalDate.of(2026, 4, 19)))
                .thenReturn(new AdminDashboardSummaryResponse(
                        0,
                        0,
                        0,
                        new AdminDashboardSummaryResponse.ViagensSummary(0, 0, 0, 0)
                ));

        mockMvc.perform(get("/company-admins/dashboard/summary")
                        .servletPath("/company-admins/dashboard/summary")
                        .param("data", "2026-04-19")
                        .header("Authorization", basicHeader("admin.dashboard@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.motoristasAtivos").value(0))
                .andExpect(jsonPath("$.viagensHoje.total").value(0));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
