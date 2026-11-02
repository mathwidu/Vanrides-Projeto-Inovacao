package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.PresencaServicoResponse;
import com.vanrides.vanrides.model.PresencaServicoOrigem;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.PresencaServicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
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

@WebMvcTest(PresencaServicoController.class)
class PresencaServicoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PresencaServicoService presencaServicoService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    void shouldListServicePresenceForOwnPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("passageiro.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.presenca.servico@vanrides.dev", null, null, 66L));
        when(presencaServicoService.listarPresencasPorPassageiro(66L, 21L, data, data))
                .thenReturn(List.of(response(PresencaServicoStatus.CONFIRMED, PresencaServicoOrigem.PASSENGER)));

        mockMvc.perform(get("/presencas-servico/passageiro/21")
                        .servletPath("/presencas-servico/passageiro/21")
                        .queryParam("dataInicio", data.toString())
                        .queryParam("dataFim", data.toString())
                        .header("Authorization", basicHeader("passageiro.presenca.servico@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("CONFIRMED"));
    }

    @Test
    void shouldCreateServicePresenceForOwnPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("passageiro.cria.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.cria.presenca.servico@vanrides.dev", null, null, 66L));
        when(presencaServicoService.criarPresenca(eq(66L), any(), eq(PresencaServicoOrigem.PASSENGER)))
                .thenReturn(response(PresencaServicoStatus.CONFIRMED, PresencaServicoOrigem.PASSENGER));

        mockMvc.perform(post("/presencas-servico")
                        .servletPath("/presencas-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("passageiro.cria.presenca.servico@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "servicoId":10,
                                  "passageiroId":21,
                                  "dataOperacao":"%s",
                                  "status":"CONFIRMED"
                                }
                                """.formatted(data)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origem").value("PASSENGER"));
    }

    @Test
    void shouldCreateServicePresenceForGuardianLinkedPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("guardian.cria.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "GUARDIAN", "guardian.cria.presenca.servico@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(90L, 21L, 66L)).thenReturn(true);
        when(presencaServicoService.criarPresenca(eq(66L), any(), eq(PresencaServicoOrigem.GUARDIAN)))
                .thenReturn(response(PresencaServicoStatus.ABSENT, PresencaServicoOrigem.GUARDIAN));

        mockMvc.perform(post("/presencas-servico")
                        .servletPath("/presencas-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("guardian.cria.presenca.servico@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "servicoId":10,
                                  "passageiroId":21,
                                  "dataOperacao":"%s",
                                  "status":"ABSENT"
                                }
                                """.formatted(data)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.origem").value("GUARDIAN"));
    }

    @Test
    void shouldHideAnotherPassengerServicePresenceCreationFromPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("passageiro.outro.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.outro.presenca.servico@vanrides.dev", null, null, 66L));

        mockMvc.perform(post("/presencas-servico")
                        .servletPath("/presencas-servico")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("passageiro.outro.presenca.servico@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "servicoId":10,
                                  "passageiroId":22,
                                  "dataOperacao":"%s",
                                  "status":"CONFIRMED"
                                }
                                """.formatted(data)))
                .andExpect(status().isNotFound());

        verify(presencaServicoService, never()).criarPresenca(eq(66L), any(), any());
    }

    @Test
    void shouldUpdateOwnServicePresence() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro.edita.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.edita.presenca.servico@vanrides.dev", null, null, 66L));
        when(presencaServicoService.buscarPassageiroIdDaPresenca(66L, 40L)).thenReturn(Optional.of(21L));
        when(presencaServicoService.atualizarStatus(66L, 40L, "ABSENT", PresencaServicoOrigem.PASSENGER))
                .thenReturn(response(PresencaServicoStatus.ABSENT, PresencaServicoOrigem.PASSENGER));

        mockMvc.perform(put("/presencas-servico/40")
                        .servletPath("/presencas-servico/40")
                        .contentType(MediaType.APPLICATION_JSON)
                        .header("Authorization", basicHeader("passageiro.edita.presenca.servico@vanrides.dev", "Secret123"))
                        .content("""
                                {
                                  "status":"ABSENT"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("ABSENT"));
    }

    @Test
    void shouldDeleteServicePresenceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.remove.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(99L, "COMPANY_ADMIN", "admin.remove.presenca.servico@vanrides.dev", null, null, 66L));
        when(presencaServicoService.buscarPassageiroIdDaPresenca(66L, 40L)).thenReturn(Optional.of(21L));

        mockMvc.perform(delete("/presencas-servico/40")
                        .servletPath("/presencas-servico/40")
                        .header("Authorization", basicHeader("admin.remove.presenca.servico@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());

        verify(presencaServicoService).deletarPresenca(66L, 40L);
    }

    @Test
    void shouldForbidDriverFromServicePresenceList() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("driver.presenca.servico@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(30L, "DRIVER", "driver.presenca.servico@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/presencas-servico/passageiro/21")
                        .servletPath("/presencas-servico/passageiro/21")
                        .queryParam("dataInicio", data.toString())
                        .queryParam("dataFim", data.toString())
                        .header("Authorization", basicHeader("driver.presenca.servico@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());
    }

    private PresencaServicoResponse response(PresencaServicoStatus status, PresencaServicoOrigem origem) {
        return new PresencaServicoResponse(
                40L,
                10L,
                "Ida manha",
                TipoOperacaoServico.IDA,
                LocalTime.of(7, 15),
                21L,
                "Ana",
                LocalDate.of(2026, 5, 25),
                status,
                origem
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
