package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.PassageiroServicoDisponivelResponse;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.PresencaServicoService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(PassageiroServicoController.class)
class PassageiroServicoControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private PresencaServicoService presencaServicoService;

    @MockBean
    private GuardianAccessService guardianAccessService;

    @MockBean
    private AuthorizationService authorizationService;

    @Test
    void shouldListAvailableServicesForOwnPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("passageiro.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.servicos@vanrides.dev", null, null, 66L));
        when(presencaServicoService.listarServicosDisponiveis(66L, 21L, data))
                .thenReturn(List.of(servicoDisponivel(data)));

        mockMvc.perform(get("/passageiros/21/servicos-disponiveis")
                        .servletPath("/passageiros/21/servicos-disponiveis")
                        .queryParam("data", data.toString())
                        .header("Authorization", basicHeader("passageiro.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].servicoId").value(10))
                .andExpect(jsonPath("$[0].presencaStatus").value("UNKNOWN"));
    }

    @Test
    void shouldListAvailableServicesForGuardianLinkedPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("guardian.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "GUARDIAN", "guardian.servicos@vanrides.dev", null, null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(90L, 21L, 66L)).thenReturn(true);
        when(presencaServicoService.listarServicosDisponiveis(66L, 21L, data))
                .thenReturn(List.of(servicoDisponivel(data)));

        mockMvc.perform(get("/passageiros/21/servicos-disponiveis")
                        .servletPath("/passageiros/21/servicos-disponiveis")
                        .queryParam("data", data.toString())
                        .header("Authorization", basicHeader("guardian.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].nome").value("Ida manha"));
    }

    @Test
    void shouldHideAnotherPassengerAvailableServicesFromPassenger() throws Exception {
        LocalDate data = LocalDate.of(2026, 5, 25);
        when(authorizationService.authenticate(basicHeader("passageiro.outro.servicos@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro.outro.servicos@vanrides.dev", null, null, 66L));

        mockMvc.perform(get("/passageiros/22/servicos-disponiveis")
                        .servletPath("/passageiros/22/servicos-disponiveis")
                        .queryParam("data", data.toString())
                        .header("Authorization", basicHeader("passageiro.outro.servicos@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());

        verify(presencaServicoService, never()).listarServicosDisponiveis(66L, 22L, data);
    }

    private PassageiroServicoDisponivelResponse servicoDisponivel(LocalDate data) {
        return new PassageiroServicoDisponivelResponse(
                10L,
                "Ida manha",
                TipoOperacaoServico.IDA,
                data,
                LocalTime.of(7, 15),
                "Escola Central",
                "Rua Escola, 100",
                PoliticaPresencaServico.INCLUIR_POR_PADRAO,
                null,
                null,
                PresencaServicoStatus.UNKNOWN
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
