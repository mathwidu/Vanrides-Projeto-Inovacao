package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminPassageiroController.class)
class CompanyAdminPassageiroIdentityContractWebMvcTest
        extends CompanyAdminPassageiroControllerWebMvcTestSupport {

    @Test
    void shouldReturnInvalidEmailContractWhenCreatingAdminPassenger() throws Exception {
        stubAdmin("admin.create.invalid@vanrides.dev");
        when(companyAdminPassageiroService.criarPassageiro(eq(66L), any()))
                .thenThrow(invalidEmail());

        mockMvc.perform(post("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.create.invalid@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnGenericConflictInsteadOfAdoptingAdminPassenger() throws Exception {
        stubAdmin("admin.create.conflict@vanrides.dev");
        when(companyAdminPassageiroService.criarPassageiro(eq(66L), any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/company-admins/passageiros")
                        .servletPath("/company-admins/passageiros")
                        .header("Authorization", basicHeader("admin.create.conflict@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(createPayload("ocupado@vanrides.dev")))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenUpdatingAdminPassenger() throws Exception {
        stubAdmin("admin.update.invalid@vanrides.dev");
        when(companyAdminPassageiroService.atualizarPassageiro(eq(21L), eq(66L), any()))
                .thenThrow(invalidEmail());

        mockMvc.perform(put("/company-admins/passageiros/21")
                        .servletPath("/company-admins/passageiros/21")
                        .header("Authorization", basicHeader("admin.update.invalid@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnGenericConflictWhenUpdatingAdminPassenger() throws Exception {
        stubAdmin("admin.update.conflict@vanrides.dev");
        when(companyAdminPassageiroService.atualizarPassageiro(eq(21L), eq(66L), any()))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(put("/company-admins/passageiros/21")
                        .servletPath("/company-admins/passageiros/21")
                        .header("Authorization", basicHeader("admin.update.conflict@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updatePayload("ocupado@vanrides.dev")))
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

    private String createPayload(String email) {
        return """
                {
                  "nome":"Aluno Novo",
                  "email":"%s",
                  "telefone":"51999990025",
                  "endereco":"Rua Nova",
                  "bairro":"Centro",
                  "cidade":"Porto Alegre",
                  "estado":"RS",
                  "cep":"90000-000",
                  "senha":"Aluno@123",
                  "menorDeIdade":true
                }
                """.formatted(email);
    }

    private String updatePayload(String email) {
        return """
                {
                  "nome":"Aluno Atualizado",
                  "email":"%s",
                  "telefone":"51999990021",
                  "endereco":"Rua Atualizada",
                  "bairro":"Centro",
                  "cidade":"Porto Alegre",
                  "estado":"RS",
                  "cep":"90000-000",
                  "menorDeIdade":false
                }
                """.formatted(email);
    }
}
