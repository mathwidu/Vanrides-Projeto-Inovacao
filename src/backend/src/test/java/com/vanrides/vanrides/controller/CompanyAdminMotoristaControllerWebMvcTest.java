package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminMotoristaUpdateRequest;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminMotoristaService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;

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

@WebMvcTest(CompanyAdminMotoristaController.class)
class CompanyAdminMotoristaControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminMotoristaService companyAdminMotoristaService;

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
    void shouldListCompanyDriversForAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.listarMotoristas(66L))
                .thenReturn(List.of(new AdminMotoristaSummaryResponse(7L, "Motorista Um", "mot1@vanrides.dev", "51999990007")));

        mockMvc.perform(get("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(7))
                .andExpect(jsonPath("$[0].nome").value("Motorista Um"));
    }

    @Test
    void shouldCreateDriverWithinAdminCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.criarMotorista(eq(66L), any()))
                .thenReturn(new AdminMotoristaSummaryResponse(8L, "Motorista Novo", "mot.novo@vanrides.dev", "51999990008"));

        mockMvc.perform(post("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista Novo",
                                  "email":"mot.novo@vanrides.dev",
                                  "telefone":"51999990008",
                                  "senha":"Motorista@123"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.email").value("mot.novo@vanrides.dev"));
    }

    @Test
    void shouldReturnGenericConflictInsteadOfAdoptingExistingAppDriver() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.criarMotorista(eq(66L), any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista App",
                                  "email":"mot.app@vanrides.dev",
                                  "telefone":"51999990008"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenCreatingDriver() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.criarMotorista(eq(66L), any()))
                .thenThrow(invalidEmail());

        mockMvc.perform(post("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Motorista","email":"invalido","telefone":"51999990008","senha":"Motorista@123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldRejectCreateWhenPayloadIsInvalid() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista Sem Senha"
                                }
                                """))
                .andExpect(status().isBadRequest());
    }

    @Test
    void shouldUpdateDriverWithinAdminCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.editarMotorista(eq(66L), eq(8L), any(AdminMotoristaUpdateRequest.class)))
                .thenReturn(new AdminMotoristaSummaryResponse(8L, "Motorista Atualizado", "mot.atualizado@vanrides.dev", "51999990008"));

        mockMvc.perform(put("/company-admins/motoristas/8")
                        .servletPath("/company-admins/motoristas/8")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista Atualizado",
                                  "email":"mot.atualizado@vanrides.dev",
                                  "telefone":"51999990008"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(8))
                .andExpect(jsonPath("$.nome").value("Motorista Atualizado"))
                .andExpect(jsonPath("$.email").value("mot.atualizado@vanrides.dev"));
    }

    @Test
    void shouldReturnConflictWhenUpdatingDriverWithDuplicateEmail() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.editarMotorista(eq(66L), eq(8L), any(AdminMotoristaUpdateRequest.class)))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(put("/company-admins/motoristas/8")
                        .servletPath("/company-admins/motoristas/8")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista Atualizado",
                                  "email":"duplicado@vanrides.dev",
                                  "telefone":"51999990008"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenUpdatingDriver() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.editarMotorista(eq(66L), eq(8L), any(AdminMotoristaUpdateRequest.class)))
                .thenThrow(invalidEmail());

        mockMvc.perform(put("/company-admins/motoristas/8")
                        .servletPath("/company-admins/motoristas/8")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Motorista Atualizado","email":"invalido","telefone":"51999990008"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnNotFoundWhenUpdatingDriverFromAnotherCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        when(companyAdminMotoristaService.editarMotorista(eq(66L), eq(99L), any(AdminMotoristaUpdateRequest.class)))
                .thenThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        mockMvc.perform(put("/company-admins/motoristas/99")
                        .servletPath("/company-admins/motoristas/99")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Motorista Externo",
                                  "email":"externo@vanrides.dev",
                                  "telefone":"51999990099"
                                }
                                """))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldDeleteDriverWithinAdminCompanyWhenNoTripIsInProgress() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(delete("/company-admins/motoristas/8")
                        .servletPath("/company-admins/motoristas/8")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldReturnConflictWhenDeletingDriverWithTripInProgress() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível remover: há uma viagem em andamento com este motorista."))
                .when(companyAdminMotoristaService)
                .deletarMotorista(66L, 8L);

        mockMvc.perform(delete("/company-admins/motoristas/8")
                        .servletPath("/company-admins/motoristas/8")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .andExpect(status().isConflict());
    }

    @Test
    void shouldReturnNotFoundWhenDeletingDriverFromAnotherCompany() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.motoristas@vanrides.dev", "Admin", null, 66L));
        org.mockito.Mockito.doThrow(new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."))
                .when(companyAdminMotoristaService)
                .deletarMotorista(66L, 99L);

        mockMvc.perform(delete("/company-admins/motoristas/99")
                        .servletPath("/company-admins/motoristas/99")
                        .header("Authorization", basicHeader("admin.motoristas@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());
    }

    @Test
    void shouldForbidPassengerFromAdminDriversRoute() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(get("/company-admins/motoristas")
                        .servletPath("/company-admins/motoristas")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminMotoristaService, never()).listarMotoristas(eq(66L));
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }

    private ApiStatusException invalidEmail() {
        return new ApiStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL",
                "Informe um e-mail válido."
        );
    }
}
