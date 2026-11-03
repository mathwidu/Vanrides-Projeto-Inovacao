package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PresencaService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
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
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PresencaController.class)
class PresencaRbacWebMvcTest {

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
    private PresencaService presencaService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @Test
    void shouldListOnlyOwnPresencasForPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.presenca@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.presenca@vanrides.dev", null, null, 66L));
        when(presencaService.listarPresencasPorPassageiro(21L, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas")
                        .servletPath("/presencas")
                        .header("Authorization", basicHeader("passageiro.presenca@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorPassageiro(eq(21L), eq(66L));
        verify(presencaService, never()).listarTodasPresencas(eq(66L));
    }

    @Test
    void shouldRejectCompanyWideListForDriverRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver.presenca@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(22L, "DRIVER", "driver.presenca@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas")
                        .servletPath("/presencas")
                        .header("Authorization", basicHeader("driver.presenca@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(presencaService, never()).listarTodasPresencas(eq(66L));
    }

    @Test
    void shouldAllowPassengerToReadOwnPresencasById() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.self@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro.self@vanrides.dev", null, null, 66L));
        when(presencaService.listarPresencasPorPassageiro(23L, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas/passageiro/23")
                        .servletPath("/presencas/passageiro/23")
                        .header("Authorization", basicHeader("passageiro.self@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorPassageiro(eq(23L), eq(66L));
    }

    @Test
    void shouldHideAnotherPassengerPresencasFromPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(23L, "PASSENGER", "passageiro.outro@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas/passageiro/99")
                        .servletPath("/presencas/passageiro/99")
                        .header("Authorization", basicHeader("passageiro.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());

        verify(presencaService, never()).listarPresencasPorPassageiro(eq(99L), eq(66L));
    }

    @Test
    void shouldAllowPassengerToCreateOwnPresenca() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("passageiro.cria@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(24L, "PASSENGER", "passageiro.cria@vanrides.dev", null, null, 66L));
        when(presencaService.jaExistePresenca(24L, hoje, 66L)).thenReturn(false);
        when(presencaService.salvarPresenca(any(Presenca.class), eq(66L))).thenReturn(new Presenca());

        mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("passageiro.cria@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":24}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isOk());

        verify(presencaService).jaExistePresenca(eq(24L), eq(hoje), eq(66L));
        verify(presencaService).salvarPresenca(any(Presenca.class), eq(66L));
    }

    @Test
    void shouldHideAnotherPassengerPresencaCreationFromPassengerRole() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("passageiro.cria.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(24L, "PASSENGER", "passageiro.cria.outro@vanrides.dev", null, null, 66L));

                mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("passageiro.cria.outro@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":99}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(presencaService, never()).jaExistePresenca(eq(99L), eq(hoje), eq(66L));
    }

    @Test
    void shouldAllowDriverToReadOwnDailyPresencas() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("driver.self@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(30L, "DRIVER", "driver.self@vanrides.dev", null, null, 66L));
        when(presencaService.listarPresencasPorMotoristaEData(30L, hoje, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas/motorista/30")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/motorista/30")
                        .header("Authorization", basicHeader("driver.self@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorMotoristaEData(eq(30L), eq(hoje), eq(66L));
    }

    @Test
    void shouldHideAnotherMotoristaPresencasFromDriverRole() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("driver.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(30L, "DRIVER", "driver.outro@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas/motorista/31")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/motorista/31")
                .header("Authorization", basicHeader("driver.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Motorista não encontrado na empresa atual."));

        verify(presencaService, never()).listarPresencasPorMotoristaEData(eq(31L), eq(hoje), eq(66L));
    }

    @Test
    void shouldAllowCompanyAdminToReadPresencasByDate() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("admin.presenca.data@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(32L, "COMPANY_ADMIN", "admin.presenca.data@vanrides.dev", null, null, 66L));
        when(presencaService.listarPresencasPorData(hoje, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas/data")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/data")
                        .header("Authorization", basicHeader("admin.presenca.data@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorData(eq(hoje), eq(66L));
    }

    @Test
    void shouldForbidPresencasByDateForPassengerRole() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("passageiro.presenca.data@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(33L, "PASSENGER", "passageiro.presenca.data@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas/data")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/data")
                        .header("Authorization", basicHeader("passageiro.presenca.data@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(presencaService, never()).listarPresencasPorData(eq(hoje), eq(66L));
    }

    @Test
    void shouldForbidPresencasByDateForDriverRole() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("driver.presenca.data@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(34L, "DRIVER", "driver.presenca.data@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas/data")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/data")
                        .header("Authorization", basicHeader("driver.presenca.data@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(presencaService, never()).listarPresencasPorData(eq(hoje), eq(66L));
    }

    @Test
    void shouldForbidPresencasByDateForGuardianRole() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("guardian.presenca.data@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca.data@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas/data")
                        .queryParam("data", hoje.toString())
                        .servletPath("/presencas/data")
                        .header("Authorization", basicHeader("guardian.presenca.data@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(presencaService, never()).listarPresencasPorData(eq(hoje), eq(66L));
    }

    @Test
    void shouldAllowGuardianToListLinkedPassengerPresencas() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.presenca@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca@vanrides.dev", null, null, 66L));
        when(guardianAccessService.listLinkedPassageiroIds(35L, 66L)).thenReturn(List.of(90L, 91L));
        when(presencaService.listarPresencasPorPassageiros(List.of(90L, 91L), 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas")
                        .servletPath("/presencas")
                        .header("Authorization", basicHeader("guardian.presenca@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorPassageiros(eq(List.of(90L, 91L)), eq(66L));
    }

    @Test
    void shouldAllowGuardianToReadLinkedPassengerPresencasById() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.presenca.id@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca.id@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(35L, 90L, 66L)).thenReturn(true);
        when(presencaService.listarPresencasPorPassageiro(90L, 66L)).thenReturn(List.of());

        mockMvc.perform(get("/presencas/passageiro/90")
                        .servletPath("/presencas/passageiro/90")
                        .header("Authorization", basicHeader("guardian.presenca.id@vanrides.dev", "Secret123")))
                .andExpect(status().isOk());

        verify(presencaService).listarPresencasPorPassageiro(eq(90L), eq(66L));
    }

    @Test
    void shouldHideUnlinkedPassengerPresencasFromGuardianRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.presenca.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca.outro@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(35L, 99L, 66L)).thenReturn(false);

        mockMvc.perform(get("/presencas/passageiro/99")
                        .servletPath("/presencas/passageiro/99")
                .header("Authorization", basicHeader("guardian.presenca.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(presencaService, never()).listarPresencasPorPassageiro(eq(99L), eq(66L));
    }

    @Test
    void shouldAllowGuardianToCreateLinkedPassengerPresenca() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("guardian.presenca.cria@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca.cria@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(35L, 90L, 66L)).thenReturn(true);
        when(presencaService.jaExistePresenca(90L, hoje, 66L)).thenReturn(false);
        when(presencaService.salvarPresenca(any(Presenca.class), eq(66L))).thenReturn(new Presenca());

        mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("guardian.presenca.cria@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":90}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isOk());
    }

    @Test
    void shouldHideGuardianPresencaCreationForUnlinkedPassenger() throws Exception {
        LocalDate hoje = LocalDate.now();
        when(authorizationService.authenticate(basicHeader("guardian.presenca.cria.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.presenca.cria.outro@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(35L, 99L, 66L)).thenReturn(false);

        mockMvc.perform(post("/presencas")
                        .servletPath("/presencas")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("guardian.presenca.cria.outro@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "dataPresenca":"%s",
                                  "confirmado":true,
                                  "passageiro":{"id":99}
                                }
                                """.formatted(hoje)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(presencaService, never()).jaExistePresenca(eq(99L), eq(hoje), eq(66L));
    }

    @Test
    void shouldAllowPassengerToDeleteOwnPresenca() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.delete@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(40L, "PASSENGER", "passageiro.delete@vanrides.dev", null, null, 66L));
        Presenca presenca = presencaForPassenger(500L, 40L);
        when(presencaService.buscarPresencaPorId(500L, 66L)).thenReturn(Optional.of(presenca));

        mockMvc.perform(delete("/presencas/500")
                        .servletPath("/presencas/500")
                        .header("Authorization", basicHeader("passageiro.delete@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());

        verify(presencaService).deletarPresenca(500L);
    }

    @Test
    void shouldHideAnotherPassengerPresencaDeletionFromPassengerRole() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.delete.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(40L, "PASSENGER", "passageiro.delete.outro@vanrides.dev", null, null, 66L));
        Presenca presenca = presencaForPassenger(501L, 41L);
        when(presencaService.buscarPresencaPorId(501L, 66L)).thenReturn(Optional.of(presenca));

        mockMvc.perform(delete("/presencas/501")
                        .servletPath("/presencas/501")
                .header("Authorization", basicHeader("passageiro.delete.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(presencaService, never()).deletarPresenca(501L);
    }

    @Test
    void shouldAllowGuardianToDeleteLinkedPassengerPresenca() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.delete@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.delete@vanrides.dev", null, null, 66L));
        Presenca presenca = presencaForPassenger(600L, 90L);
        when(presencaService.buscarPresencaPorId(600L, 66L)).thenReturn(Optional.of(presenca));
        when(guardianAccessService.hasLinkedPassageiro(35L, 90L, 66L)).thenReturn(true);

        mockMvc.perform(delete("/presencas/600")
                        .servletPath("/presencas/600")
                        .header("Authorization", basicHeader("guardian.delete@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());

        verify(presencaService).deletarPresenca(600L);
    }

    @Test
    void shouldHideGuardianDeletionForUnlinkedPassengerPresenca() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian.delete.outro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(35L, "GUARDIAN", "guardian.delete.outro@vanrides.dev", null, null, 66L));
        Presenca presenca = presencaForPassenger(601L, 91L);
        when(presencaService.buscarPresencaPorId(601L, 66L)).thenReturn(Optional.of(presenca));
        when(guardianAccessService.hasLinkedPassageiro(35L, 91L, 66L)).thenReturn(false);

        mockMvc.perform(delete("/presencas/601")
                        .servletPath("/presencas/601")
                        .header("Authorization", basicHeader("guardian.delete.outro@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.message").value("Passageiro não encontrado na empresa atual."));

        verify(presencaService, never()).deletarPresenca(601L);
    }

    private Presenca presencaForPassenger(Long presencaId, Long passageiroId) {
        Passageiro passageiro = new Passageiro(); passageiro.setId(passageiroId);
        Presenca presenca = new Presenca();
        presenca.setId(presencaId);
        presenca.setPassageiro(passageiro);
        return presenca;
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
