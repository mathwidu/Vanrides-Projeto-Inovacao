package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.PublicPassengerRegistrationRequest;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.model.Passageiro;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.server.ResponseStatusException;

import static org.mockito.ArgumentMatchers.any;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassageiroController.class)
class PassageiroControllerWebMvcTest extends PassageiroControllerWebMvcTestSupport {

    @Test
    void shouldReturnBadRequestWhenSenhaIsMissingOnCreatePassageiro() throws Exception {
        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro Sem Senha",
                                  "email":"passageiro.sem.senha@vanrides.dev",
                                  "telefone":"51999990003",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Senha é obrigatória."));
    }

    @Test
    void shouldReturnBadRequestWhenAddressFieldsAreMissingOnCreatePassageiro() throws Exception {
        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro Sem Endereco Completo",
                                  "email":"passageiro.sem.endereco@vanrides.dev",
                                  "telefone":"51999990004",
                                  "senha":"Secret123",
                                  "endereco":"Rua A",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Endereço completo com bairro é obrigatório."));
    }

    @Test
    void shouldReturnInvalidEmailContractOnCreatePassageiro() throws Exception {
        when(passageiroService.registrarPassageiroPublico(any(PublicPassengerRegistrationRequest.class)))
                .thenThrow(invalidEmail());

        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(validPassengerPayload("invalido")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldCreatePassageiroWhenPayloadIsValid() throws Exception {
        Passageiro salvo = savedPassengerWithPrivateFields();

        when(passageiroService.registrarPassageiroPublico(any(PublicPassengerRegistrationRequest.class)))
                .thenReturn(salvo);

        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro QA",
                                  "email":"passageiro.qa@vanrides.dev",
                                  "telefone":"51999990005",
                                  "senha":"Secret123",
                                  "endereco":"Rua QA",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-111",
                                  "id":999,
                                  "emailVerified":true,
                                  "motorista":{"id":7},
                                  "company":{"id":66}
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.nome").value("Passageiro QA"))
                .andExpect(jsonPath("$.email").value("passageiro.qa@vanrides.dev"))
                .andExpect(jsonPath("$.telefone").value("51999990005"))
                .andExpect(jsonPath("$.emailVerified").value(false))
                .andExpect(jsonPath("$.emailVerificationLastSentAt").value("2026-07-02T17:05:00-03:00"))
                .andExpect(jsonPath("$.endereco").doesNotExist())
                .andExpect(jsonPath("$.bairro").doesNotExist())
                .andExpect(jsonPath("$.cidade").doesNotExist())
                .andExpect(jsonPath("$.estado").doesNotExist())
                .andExpect(jsonPath("$.cep").doesNotExist())
                .andExpect(jsonPath("$.latitude").doesNotExist())
                .andExpect(jsonPath("$.longitude").doesNotExist())
                .andExpect(jsonPath("$.dataNascimento").doesNotExist())
                .andExpect(jsonPath("$.fotoPerfilUrl").doesNotExist())
                .andExpect(jsonPath("$.motorista").doesNotExist())
                .andExpect(jsonPath("$.company").doesNotExist())
                .andExpect(jsonPath("$.senha").doesNotExist());

        ArgumentCaptor<PublicPassengerRegistrationRequest> requestCaptor =
                ArgumentCaptor.forClass(PublicPassengerRegistrationRequest.class);
        verify(passageiroService).registrarPassageiroPublico(requestCaptor.capture());
        assertThat(requestCaptor.getValue().email()).isEqualTo("passageiro.qa@vanrides.dev");
        assertThat(requestCaptor.getValue().cep()).isEqualTo("90000-111");
        assertThat(PublicPassengerRegistrationRequest.class.getRecordComponents())
                .extracting(component -> component.getName())
                .doesNotContain("id", "emailVerified", "motorista", "company");
    }

    @Test
    void shouldReturnConflictWithoutOverwritingAnExistingPendingEmail() throws Exception {
        when(passageiroService.registrarPassageiroPublico(any(PublicPassengerRegistrationRequest.class)))
                .thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/passageiros")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro Duplicado",
                                  "email":"pendente@vanrides.dev",
                                  "telefone":"51999990005",
                                  "senha":"AttackerPassword123",
                                  "endereco":"Rua QA",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-111"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnUpdatedPassengerPayloadOnProtectedPut() throws Exception {
        Passageiro saved = new Passageiro();
        saved.setId(15L);
        saved.setNome("Passageiro Atualizado");
        saved.setEmail("atualizado@vanrides.dev");
        saved.setTelefone("51999990005");
        when(authorizationService.authenticate(any(String.class))).thenReturn(
                new com.vanrides.vanrides.dto.AuthenticatedUser(
                        90L, "COMPANY_ADMIN", "admin@vanrides.dev", "Admin", null, 66L
                ));
        when(passageiroService.atualizarPassageiroProtegido(
                org.mockito.ArgumentMatchers.eq(15L),
                org.mockito.ArgumentMatchers.eq(66L),
                any(Passageiro.class)
        )).thenReturn(java.util.Optional.of(saved));

        mockMvc.perform(put("/passageiros/15")
                        .servletPath("/passageiros/15")
                        .header("Authorization", basicHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Passageiro Atualizado","email":"atualizado@vanrides.dev","telefone":"51999990005"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(15))
                .andExpect(jsonPath("$.email").value("atualizado@vanrides.dev"));
    }

    @Test
    void shouldReturnInvalidEmailContractOnProtectedPut() throws Exception {
        stubAdminAuthorization();
        when(passageiroService.atualizarPassageiroProtegido(
                org.mockito.ArgumentMatchers.eq(15L),
                org.mockito.ArgumentMatchers.eq(66L),
                any(Passageiro.class)
        )).thenThrow(invalidEmail());

        mockMvc.perform(put("/passageiros/15")
                        .servletPath("/passageiros/15")
                        .header("Authorization", basicHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Passageiro","email":"invalido","telefone":"51999990005"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"));
    }

    @Test
    void shouldReturnGenericConflictOnProtectedPut() throws Exception {
        stubAdminAuthorization();
        when(passageiroService.atualizarPassageiroProtegido(
                org.mockito.ArgumentMatchers.eq(15L),
                org.mockito.ArgumentMatchers.eq(66L),
                any(Passageiro.class)
        )).thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(put("/passageiros/15")
                        .servletPath("/passageiros/15")
                        .header("Authorization", basicHeader())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"nome":"Passageiro","email":"ocupado@vanrides.dev","telefone":"51999990005"}
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldDelegateTenantScopedDeleteExactlyOnceAndReturnNoContent() throws Exception {
        stubAdminAuthorization();

        mockMvc.perform(delete("/passageiros/15")
                        .servletPath("/passageiros/15")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isNoContent())
                .andExpect(content().string(""));

        verify(passageiroService, times(1)).deletarPassageiro(15L, 66L);
        verify(passageiroService, never()).buscarPassageiroPorId(15L, 66L);
        verifyNoMoreInteractions(passageiroService);
    }

    @Test
    void shouldReturnIndistinguishableNotFoundWhenTenantScopedDeleteMatchesNoRow() throws Exception {
        stubAdminAuthorization();
        ResponseStatusException notFound = passengerNotFound();
        doThrow(notFound).when(passageiroService).deletarPassageiro(15L, 66L);
        doThrow(passengerNotFound()).when(passageiroService).deletarPassageiro(999L, 66L);

        MvcResult missing = mockMvc.perform(delete("/passageiros/15")
                        .servletPath("/passageiros/15")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."))
                .andReturn();

        MvcResult outsideTenant = mockMvc.perform(delete("/passageiros/999")
                        .servletPath("/passageiros/999")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value("NOT_FOUND"))
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."))
                .andReturn();

        assertThat(outsideTenant.getResponse().getContentAsByteArray())
                .isEqualTo(missing.getResponse().getContentAsByteArray());
        verify(passageiroService).deletarPassageiro(15L, 66L);
        verify(passageiroService).deletarPassageiro(999L, 66L);
        verify(passageiroService, never()).buscarPassageiroPorId(any(), any());
        verifyNoMoreInteractions(passageiroService);
    }

}
