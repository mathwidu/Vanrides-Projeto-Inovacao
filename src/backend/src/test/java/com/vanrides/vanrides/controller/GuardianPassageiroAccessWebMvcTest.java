package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassageiroService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
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
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassageiroController.class)
class GuardianPassageiroAccessWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private AuthService authService;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private AuthorizationService authorizationService;

    @MockBean
    private PassageiroService passageiroService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @Test
    void shouldListOnlyLinkedPassengersForGuardian() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.linked@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(51L, "GUARDIAN", "guardian.linked@vanrides.dev", null, null, 66L));
        when(guardianAccessService.listLinkedPassageiroIds(51L, 66L)).thenReturn(List.of(101L, 102L));
        when(passageiroService.listarPassageirosPorIds(List.of(101L, 102L), 66L))
                .thenReturn(List.of(passageiro(101L, "Passageiro Um"), passageiro(102L, "Passageiro Dois")));

        mockMvc.perform(get("/passageiros")
                        .servletPath("/passageiros")
                        .header("Authorization", basicHeader("guardian.linked@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].id").value(101))
                .andExpect(jsonPath("$[1].id").value(102));

        verify(guardianAccessService).listLinkedPassageiroIds(eq(51L), eq(66L));
        verify(passageiroService).listarPassageirosPorIds(eq(List.of(101L, 102L)), eq(66L));
    }

    @Test
    void shouldAllowGuardianToReadLinkedPassengerById() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.read@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(52L, "GUARDIAN", "guardian.read@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(52L, 101L, 66L)).thenReturn(true);
        when(passageiroService.buscarPassageiroPorId(101L, 66L))
                .thenReturn(Optional.of(passageiro(101L, "Passageiro Vinculado")));

        mockMvc.perform(get("/passageiros/101")
                        .servletPath("/passageiros/101")
                        .header("Authorization", basicHeader("guardian.read@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(101));
    }

    @Test
    void shouldHideUnlinkedPassengerFromGuardian() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.read.other@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(52L, "GUARDIAN", "guardian.read.other@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(52L, 999L, 66L)).thenReturn(false);

        mockMvc.perform(get("/passageiros/999")
                        .servletPath("/passageiros/999")
                .header("Authorization", basicHeader("guardian.read.other@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(passageiroService, never()).buscarPassageiroPorId(eq(999L), eq(66L));
    }

    @Test
    void shouldRejectGuardianPassengerProfileUpdateAsUnavailable() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.update@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(53L, "GUARDIAN", "guardian.update@vanrides.dev", null, null, 66L));

        mockMvc.perform(put("/passageiros/101")
                        .servletPath("/passageiros/101")
                        .header("Authorization", basicHeader("guardian.update@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro Atualizado",
                                  "email":"atualizado@vanrides.dev",
                                  "telefone":"51999990001",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Responsável pode consultar passageiros vinculados e decidir presenças, mas alterações cadastrais são feitas pela empresa."));

        verify(guardianAccessService, never()).hasLinkedPassageiro(any(), any(), any());
        verify(passageiroService, never()).buscarPassageiroPorId(eq(101L), eq(66L));
        verify(passageiroService, never()).atualizarPassageiroProtegido(
                eq(101L),
                eq(66L),
                any(Passageiro.class)
        );
    }

    @Test
    void shouldRejectUnlinkedPassengerUpdateFromGuardianAsUnavailable() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.update.other@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(53L, "GUARDIAN", "guardian.update.other@vanrides.dev", null, null, 66L));

        mockMvc.perform(put("/passageiros/999")
                        .servletPath("/passageiros/999")
                        .header("Authorization", basicHeader("guardian.update.other@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "nome":"Passageiro Atualizado",
                                  "email":"atualizado@vanrides.dev",
                                  "telefone":"51999990001",
                                  "endereco":"Rua A",
                                  "bairro":"Centro",
                                  "cidade":"Porto Alegre",
                                  "estado":"RS",
                                  "cep":"90000-000"
                                }
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_UNAVAILABLE"));

        verify(guardianAccessService, never()).hasLinkedPassageiro(any(), any(), any());
        verify(passageiroService, never()).buscarPassageiroPorId(eq(999L), eq(66L));
        verify(passageiroService, never()).atualizarPassageiroProtegido(
                eq(999L),
                eq(66L),
                any(Passageiro.class)
        );
    }

    @Test
    void shouldRejectGuardianPassengerProfileDeletionAsUnavailable() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.delete@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(54L, "GUARDIAN", "guardian.delete@vanrides.dev", null, null, 66L));

        mockMvc.perform(delete("/passageiros/101")
                        .servletPath("/passageiros/101")
                        .header("Authorization", basicHeader("guardian.delete@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_UNAVAILABLE"))
                .andExpect(jsonPath("$.message").value("Responsável pode consultar passageiros vinculados e decidir presenças, mas alterações cadastrais são feitas pela empresa."));

        verify(guardianAccessService, never()).hasLinkedPassageiro(any(), any(), any());
        verify(passageiroService, never()).buscarPassageiroPorId(eq(101L), eq(66L));
        verify(passageiroService, never()).deletarPassageiro(101L, 66L);
    }

    @Test
    void shouldRejectUnlinkedPassengerDeletionFromGuardianAsUnavailable() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.delete.other@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(54L, "GUARDIAN", "guardian.delete.other@vanrides.dev", null, null, 66L));

        mockMvc.perform(delete("/passageiros/999")
                        .servletPath("/passageiros/999")
                .header("Authorization", basicHeader("guardian.delete.other@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("ACTION_UNAVAILABLE"));

        verify(guardianAccessService, never()).hasLinkedPassageiro(any(), any(), any());
        verify(passageiroService, never()).buscarPassageiroPorId(eq(999L), eq(66L));
        verify(passageiroService, never()).deletarPassageiro(999L, 66L);
    }

    private Passageiro passageiro(Long id, String nome) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEmail(nome.toLowerCase().replace(" ", ".") + "@vanrides.dev");
        passageiro.setTelefone("51999990000");
        passageiro.setEndereco("Rua Teste");
        passageiro.setBairro("Centro");
        passageiro.setCidade("Porto Alegre");
        passageiro.setEstado("RS");
        passageiro.setCep("90000-000");
        return passageiro;
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
