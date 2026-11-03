package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminGuardianService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(GuardianAccountController.class)
class GuardianAccountControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminGuardianService companyAdminGuardianService;

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
    void shouldAllowGuardianToChangeOwnPassword() throws Exception {
        when(authorizationService.authenticate(basicHeader("responsavel@vanrides.dev", "Resp!123")))
                .thenReturn(new AuthenticatedUser(71L, "GUARDIAN", "responsavel@vanrides.dev", "Responsavel", null, 66L));

        mockMvc.perform(put("/responsaveis/minha-senha")
                        .servletPath("/responsaveis/minha-senha")
                        .header("Authorization", basicHeader("responsavel@vanrides.dev", "Resp!123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":"Resp!123",
                                  "novaSenha":"Nova!123"
                                }
                                """))
                .andExpect(status().isNoContent());

        verify(companyAdminGuardianService).atualizarMinhaSenha(eq(71L), eq(66L), any());
    }

    @Test
    void shouldForbidAdminFromGuardianPasswordRoute() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin@vanrides.dev", "Admin!123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(put("/responsaveis/minha-senha")
                        .servletPath("/responsaveis/minha-senha")
                        .header("Authorization", basicHeader("admin@vanrides.dev", "Admin!123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":"Admin!123",
                                  "novaSenha":"Nova!123"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(companyAdminGuardianService, never()).atualizarMinhaSenha(eq(90L), eq(66L), any());
    }

    @Test
    void shouldRejectInvalidPasswordChangePayload() throws Exception {
        when(authorizationService.authenticate(basicHeader("responsavel@vanrides.dev", "Resp!123")))
                .thenReturn(new AuthenticatedUser(71L, "GUARDIAN", "responsavel@vanrides.dev", "Responsavel", null, 66L));

        mockMvc.perform(put("/responsaveis/minha-senha")
                        .servletPath("/responsaveis/minha-senha")
                        .header("Authorization", basicHeader("responsavel@vanrides.dev", "Resp!123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "senhaAtual":""
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
