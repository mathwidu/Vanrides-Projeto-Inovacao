package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.CompanyAdminResponse;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminController.class)
class CompanyAdminControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminService companyAdminService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @MockBean
    private CompanyAdminRepository companyAdminRepository;

    @Test
    void shouldBootstrapCompanyAdmin() throws Exception {
        when(companyAdminService.bootstrapFirstAdmin(any())).thenReturn(
                new CompanyAdminResponse(55L, 7L, "default", "Admin Portal", "admin@vanrides.dev", "51999999999"));

        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Admin Portal","email":"admin@vanrides.dev","telefone":"51999999999","senha":"Admin!123","companyName":"VanRides Default"}
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(55))
                .andExpect(jsonPath("$.companyId").value(7))
                .andExpect(jsonPath("$.companySlug").value("default"))
                .andExpect(jsonPath("$.email").value("admin@vanrides.dev"));
    }

    @Test
    void shouldRejectBootstrapWhenRequiredFieldsAreMissing() throws Exception {
        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"","email":"admin@vanrides.dev","telefone":"","senha":"12345678"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error").value("Nome, email, telefone e senha são obrigatórios."));
    }

    @Test
    void shouldRejectLegacyCompanySlugSelectorWithoutCompanyName() throws Exception {
        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Admin Portal","email":"admin@vanrides.dev","telefone":"51999999999","senha":"Admin!123","companySlug":"legacy-company"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.error")
                        .value("Nome da empresa é obrigatório para o primeiro cadastro."));
    }

    @Test
    void shouldReturnGenericConflictWhenCompanyAlreadyHasPendingAdmin() throws Exception {
        when(companyAdminService.bootstrapFirstAdmin(any())).thenThrow(
                new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Esta empresa já possui um administrador."
                ));

        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Retry","email":"retry@vanrides.dev","telefone":"51999999998","senha":"Admin!123","companyName":"Empresa Existente"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.error")
                        .value("Esta empresa já possui um administrador."));
    }

    @Test
    void shouldUseTheGenericGlobalEmailConflictContract() throws Exception {
        when(companyAdminService.bootstrapFirstAdmin(any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Admin","email":"taken@vanrides.dev","telefone":"51999999998","senha":"Admin!123","companyName":"Nova Empresa"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }

    @Test
    void shouldUseInvalidEmailContractOnBootstrap() throws Exception {
        when(companyAdminService.bootstrapFirstAdmin(any())).thenThrow(
                new ApiStatusException(
                        HttpStatus.BAD_REQUEST,
                        "INVALID_EMAIL",
                        "Informe um e-mail válido."
                ));

        mockMvc.perform(post("/company-admins/bootstrap")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Admin","email":"invalido","telefone":"51999999998","senha":"Admin!123","companyName":"Nova Empresa"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist())
                .andExpect(jsonPath("$.error").doesNotExist());
    }
}
