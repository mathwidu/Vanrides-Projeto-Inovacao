package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminViagemManifestStatus;
import com.vanrides.vanrides.dto.AdminViagemManifestStatusResponse;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminViagemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminViagemController.class)
class CompanyAdminViagemManifestSummaryControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminViagemService companyAdminViagemService;
    @MockBean
    private AuthorizationService authorizationService;
    @MockBean
    private PassageiroRepository passageiroRepository;
    @MockBean
    private MotoristaRepository motoristaRepository;
    @MockBean
    private GuardianRepository guardianRepository;
    @MockBean
    private CompanyAdminRepository companyAdminRepository;

    @Test
    void shouldExposeManifestStatusInTripSummary() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.listarViagens(66L, null))
                .thenReturn(List.of(new AdminViagemSummaryResponse(30L, 10L, null, "Rota Escolar Manha",
                        "Linha Manha", TipoOperacaoServico.IDA, LocalDate.of(2026, 3, 12),
                        null, ViagemStatus.PLANNED, null, 2, pendingManifest())));

        mockMvc.perform(get("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].manifesto.status").value("PENDING"))
                .andExpect(jsonPath("$[0].manifesto.mudancas").value(2))
                .andExpect(jsonPath("$[0].manifesto.adicionados").value(1))
                .andExpect(jsonPath("$[0].manifesto.removidos").value(1));
    }

    private AdminViagemManifestStatusResponse pendingManifest() {
        return new AdminViagemManifestStatusResponse(
                AdminViagemManifestStatus.PENDING,
                2,
                1,
                1,
                "Sincronize o manifesto antes da saída."
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
