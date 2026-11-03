package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminResponsavelDetailResponse;
import com.vanrides.vanrides.dto.AdminResponsavelSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
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
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminGuardianController.class)
class CompanyAdminGuardianControllerWebMvcTest {

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
    void shouldListCompanyGuardiansForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.resp@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.resp@vanrides.dev", "Admin", null, 66L));
        when(companyAdminGuardianService.listarResponsaveis(66L)).thenReturn(List.of(
                new AdminResponsavelSummaryResponse(71L, "Mae Portal", "mae@vanrides.dev", "51999990001", 2, true)
        ));

        mockMvc.perform(get("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("admin.resp@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Mae Portal"))
                .andExpect(jsonPath("$[0].passageiroCount").value(2));
    }

    @Test
    void shouldForbidPassengerRoleFromAdminGuardiansRoute() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(get("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminGuardianService, never()).listarResponsaveis(eq(66L));
    }

    @Test
    void shouldCreateGuardianForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.create@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.create@vanrides.dev", "Admin", null, 66L));
        when(companyAdminGuardianService.criarResponsavel(eq(66L), any())).thenReturn(
                new AdminResponsavelDetailResponse(71L, "Mae Nova", "mae.nova@vanrides.dev", "51999990001", true, List.of())
        );

        mockMvc.perform(post("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("admin.create@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Mae Nova",
                                  "email":"mae.nova@vanrides.dev",
                                  "telefone":"51999990001",
                                  "senha":"Resp!123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(71))
                .andExpect(jsonPath("$.acessoConfigurado").value(true));
    }

    @Test
    void shouldReturnGuardianDetail() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.detail@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.detail@vanrides.dev", "Admin", null, 66L));
        when(companyAdminGuardianService.buscarDetalhe(71L, 66L)).thenReturn(Optional.of(
                new AdminResponsavelDetailResponse(
                        71L,
                        "Mae Detalhe",
                        "mae.detalhe@vanrides.dev",
                        "51999990001",
                        true,
                        List.of(new AdminPassageiroLinkedSummaryResponse(21L, "Aluno Um", "aluno@vanrides.dev", true, null))
                )
        ));

        mockMvc.perform(get("/company-admins/responsaveis/71")
                        .servletPath("/company-admins/responsaveis/71")
                        .header("Authorization", basicHeader("admin.detail@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.passageiros.length()").value(1))
                .andExpect(jsonPath("$.passageiros[0].nome").value("Aluno Um"));
    }

    @Test
    void shouldUpdateGuardian() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.update@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.update@vanrides.dev", "Admin", null, 66L));
        when(companyAdminGuardianService.atualizarResponsavel(eq(71L), eq(66L), any())).thenReturn(
                new AdminResponsavelDetailResponse(71L, "Mae Atualizada", "mae.atualizada@vanrides.dev", "51999990073", true, List.of())
        );

        mockMvc.perform(put("/company-admins/responsaveis/71")
                        .servletPath("/company-admins/responsaveis/71")
                        .header("Authorization", basicHeader("admin.update@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Mae Atualizada",
                                  "email":"mae.atualizada@vanrides.dev",
                                  "telefone":"51999990073",
                                  "senha":"Nova!123"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.nome").value("Mae Atualizada"))
                .andExpect(jsonPath("$.acessoConfigurado").value(true));
    }

    @Test
    void shouldDeleteGuardian() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.delete@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.delete@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/responsaveis/71")
                        .servletPath("/company-admins/responsaveis/71")
                        .header("Authorization", basicHeader("admin.delete@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldRejectCreateWhenPayloadIsInvalid() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.bad@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.bad@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("admin.bad@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"   "
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenCreatingGuardian() throws Exception {
        stubAdmin("admin.guardian.invalid@vanrides.dev");
        when(companyAdminGuardianService.criarResponsavel(eq(66L), any()))
                .thenThrow(invalidEmail());

        mockMvc.perform(post("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("admin.guardian.invalid@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writePayload("invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnGenericConflictInsteadOfAdoptingGuardian() throws Exception {
        stubAdmin("admin.guardian.conflict@vanrides.dev");
        when(companyAdminGuardianService.criarResponsavel(eq(66L), any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/company-admins/responsaveis")
                        .servletPath("/company-admins/responsaveis")
                        .header("Authorization", basicHeader("admin.guardian.conflict@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writePayload("ocupado@vanrides.dev")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenUpdatingGuardian() throws Exception {
        stubAdmin("admin.guardian.update.invalid@vanrides.dev");
        when(companyAdminGuardianService.atualizarResponsavel(eq(71L), eq(66L), any()))
                .thenThrow(invalidEmail());

        mockMvc.perform(put("/company-admins/responsaveis/71")
                        .servletPath("/company-admins/responsaveis/71")
                        .header("Authorization", basicHeader("admin.guardian.update.invalid@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writePayload("invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnGenericConflictWhenUpdatingGuardian() throws Exception {
        stubAdmin("admin.guardian.update.conflict@vanrides.dev");
        when(companyAdminGuardianService.atualizarResponsavel(eq(71L), eq(66L), any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(put("/company-admins/responsaveis/71")
                        .servletPath("/company-admins/responsaveis/71")
                        .header("Authorization", basicHeader("admin.guardian.update.conflict@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(writePayload("ocupado@vanrides.dev")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    private void stubAdmin(String email) {
        when(authorizationService.authenticate(basicHeader(email, "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", email, "Admin", null, 66L));
    }

    private ApiStatusException invalidEmail() {
        return new ApiStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL",
                "Informe um e-mail válido."
        );
    }

    private String writePayload(String email) {
        return """
                {
                  "nome":"Responsavel",
                  "email":"%s",
                  "telefone":"51999990001",
                  "senha":"Resp!123"
                }
                """.formatted(email);
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
