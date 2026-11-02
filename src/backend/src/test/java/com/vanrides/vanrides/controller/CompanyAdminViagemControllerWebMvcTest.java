package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminViagemBulkCreateResponse;
import com.vanrides.vanrides.dto.AdminViagemBulkIgnoredResponse;
import com.vanrides.vanrides.dto.AdminViagemDestinoFinalResponse;
import com.vanrides.vanrides.dto.AdminViagemDetailResponse;
import com.vanrides.vanrides.dto.AdminViagemParadaResponse;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyAdminRepository;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.CompanyAdminViagemService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminViagemController.class)
class CompanyAdminViagemControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private CompanyAdminViagemService companyAdminViagemService;

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
    void shouldListTripsForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.listarViagens(66L, null))
                .thenReturn(List.of(new AdminViagemSummaryResponse(30L, 10L, null, "Rota Escolar Manha",
                        "Linha Manha", TipoOperacaoServico.IDA, java.time.LocalDate.of(2026, 3, 12),
                        null, ViagemStatus.PLANNED, null, 2)));

        mockMvc.perform(get("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30))
                .andExpect(jsonPath("$[0].rotaNome").value("Rota Escolar Manha"))
                .andExpect(jsonPath("$[0].status").value("PLANNED"));
    }

    @Test
    void shouldCreateTripForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.criarViagem(eq(66L), any()))
                .thenReturn(new AdminViagemSummaryResponse(31L, 10L, "Rota Escolar Tarde",
                        java.time.LocalDate.of(2026, 3, 13), null, ViagemStatus.PLANNED, null, 3));

        mockMvc.perform(post("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rotaBaseId":10,
                                  "dataOperacao":"2026-03-13"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.rotaBaseId").value(10));
    }

    @Test
    void shouldCreateTripFromRecurringServiceForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.criarViagem(eq(66L), any()))
                .thenReturn(new AdminViagemSummaryResponse(32L, null, 15L, null, "Linha Manhã",
                        TipoOperacaoServico.IDA, LocalDate.of(2026, 3, 13), null, ViagemStatus.PLANNED, null, 4));

        mockMvc.perform(post("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "servicoId":15,
                                  "motoristaId":7,
                                  "dataOperacao":"2026-03-13"
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.servicoId").value(15))
                .andExpect(jsonPath("$.servicoNome").value("Linha Manhã"))
                .andExpect(jsonPath("$.tipoOperacao").value("IDA"));
    }

    @Test
    void shouldGenerateAutomaticTripsForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.gerarViagensAutomaticas(66L, LocalDate.of(2026, 3, 13)))
                .thenReturn(new AdminViagemBulkCreateResponse(
                        LocalDate.of(2026, 3, 13),
                        2,
                        1,
                        4,
                        5,
                        3,
                        List.of(new AdminViagemSummaryResponse(32L, null, 15L, null, "Linha Manhã",
                                TipoOperacaoServico.IDA, LocalDate.of(2026, 3, 13), null, ViagemStatus.PLANNED, null, 4)),
                        List.of(new AdminViagemBulkIgnoredResponse(18L, "Linha Lotada", "CAPACIDADE_EXCEDIDA",
                                "Manifesto operacional acima da capacidade planejada.")),
                        List.of(new AdminViagemBulkIgnoredResponse(19L, "Linha Rodando", "VIAGEM_EM_ANDAMENTO",
                                "A viagem já começou; revise alterações manualmente antes de alterar o manifesto."))
                ));

        mockMvc.perform(post("/company-admins/viagens/gerar-automaticas")
                        .servletPath("/company-admins/viagens/gerar-automaticas")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"dataOperacao\":\"2026-03-13\"}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.dataOperacao").value("2026-03-13"))
                .andExpect(jsonPath("$.criadas").value(2))
                .andExpect(jsonPath("$.existentes").value(1))
                .andExpect(jsonPath("$.atualizadas").value(4))
                .andExpect(jsonPath("$.bloqueadas").value(5))
                .andExpect(jsonPath("$.ignoradas").value(3))
                .andExpect(jsonPath("$.servicosIgnorados[0].servicoId").value(18))
                .andExpect(jsonPath("$.servicosIgnorados[0].motivo").value("CAPACIDADE_EXCEDIDA"))
                .andExpect(jsonPath("$.servicosBloqueados[0].servicoId").value(19))
                .andExpect(jsonPath("$.servicosBloqueados[0].motivo").value("VIAGEM_EM_ANDAMENTO"))
                .andExpect(jsonPath("$.viagens[0].servicoId").value(15));
    }

    @Test
    void shouldFetchTripDetailForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.buscarDetalhe(66L, 32L))
                .thenReturn(new AdminViagemDetailResponse(
                        32L,
                        10L,
                        15L,
                        "Rota Escolar Manhã",
                        "Linha Manhã",
                        TipoOperacaoServico.IDA,
                        LocalDate.of(2026, 3, 13),
                        null,
                        ViagemStatus.PLANNED,
                        2,
                        12,
                        null,
                        motorista(7L),
                        1,
                        new AdminViagemDestinoFinalResponse("Escola Central", "Rua A, 100", -30.01, -51.01),
                        parada(51L, 21L, PresencaServicoStatus.CONFIRMED, true),
                        List.of(parada(51L, 21L, PresencaServicoStatus.CONFIRMED, true)),
                        new ViagemRotaGeograficaResponse(
                                "GOOGLE_DIRECTIONS_V1",
                                "encoded-polyline",
                                new ViagemNavegacaoResponse("GOOGLE_MAPS_HANDOFF", "https://maps.google.com/?q=escola", "Origem", "Escola Central", 1),
                                List.of(new ViagemRotaWaypointResponse("STOP", 51L, 21L, "Passageiro 21", -30.02, -51.02, 1, true, false)),
                                List.of(new ViagemRotaLegResponse(0, "Origem", "Passageiro 21", 1200, 480)),
                                1,
                                null,
                                null
                        ),
                        "SERVICO_RECORRENTE",
                        true
                ));

        mockMvc.perform(get("/company-admins/viagens/32")
                        .servletPath("/company-admins/viagens/32")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.servicoId").value(15))
                .andExpect(jsonPath("$.fontePlanejamento").value("SERVICO_RECORRENTE"))
                .andExpect(jsonPath("$.snapshotOperacional").value(true))
                .andExpect(jsonPath("$.destinoFinal.nome").value("Escola Central"))
                .andExpect(jsonPath("$.paradas[0].passageiro.nome").value("Passageiro 21"))
                .andExpect(jsonPath("$.paradas[0].presencaId").value(101L))
                .andExpect(jsonPath("$.paradas[0].presencaStatus").value("CONFIRMED"))
                .andExpect(jsonPath("$.rotaGeografica.provider").value("GOOGLE_DIRECTIONS_V1"))
                .andExpect(jsonPath("$.rotaGeografica.navegacao.quantidadeWaypoints").value(1))
                .andExpect(jsonPath("$.rotaGeografica.navegacaoRestante.quantidadeWaypoints").value(1))
                .andExpect(jsonPath("$.rotaGeografica.waypoints[0].nome").value("Passageiro 21"));
    }

    @Test
    void shouldUpdateOperationalDriverForCompanyAdminTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.trocarMotorista(66L, 32L, 8L))
                .thenReturn(new AdminViagemDetailResponse(
                        32L,
                        null,
                        15L,
                        null,
                        "Linha Manhã",
                        TipoOperacaoServico.IDA,
                        LocalDate.of(2026, 3, 13),
                        null,
                        ViagemStatus.PLANNED,
                        3,
                        12,
                        null,
                        motorista(8L),
                        0,
                        new AdminViagemDestinoFinalResponse(null, null, null, null),
                        null,
                        List.of(),
                        "SERVICO_RECORRENTE",
                        true
                ));

        mockMvc.perform(put("/company-admins/viagens/32/motorista")
                        .servletPath("/company-admins/viagens/32/motorista")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "motoristaId":8
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.motorista.id").value(8))
                .andExpect(jsonPath("$.rotaVersao").value(3));
    }

    @Test
    void shouldListTripsForCompanyAdminWithDateFilter() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.listarViagens(66L, LocalDate.of(2026, 3, 12)))
                .thenReturn(List.of(new AdminViagemSummaryResponse(30L, 10L, "Rota Escolar Manha",
                        LocalDate.of(2026, 3, 12), null, ViagemStatus.PLANNED, null, 2)));

        mockMvc.perform(get("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].dataOperacao").value("2026-03-12"));
    }

    @Test
    void shouldRejectTripCreateWhenRequestIsInvalid() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));

        mockMvc.perform(post("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "rotaBaseId":10
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("BAD_REQUEST"))
                .andExpect(jsonPath("$.message").value("Data da operação é obrigatória."));
    }

    @Test
    void shouldExposeHumanMessageWhenManualTripAlreadyExists() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.viagens@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.criarViagem(eq(66L), any()))
                .thenThrow(new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Já existe viagem criada para este serviço e data."
                ));

        mockMvc.perform(post("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("admin.viagens@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "servicoId":15,
                                  "motoristaId":7,
                                  "dataOperacao":"2026-03-13"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("CONFLICT"))
                .andExpect(jsonPath("$.message").value("Já existe viagem criada para este serviço e data."));
    }

    @Test
    void shouldForbidDriverFromAdminTrips() throws Exception {
        when(authorizationService.authenticate(basicHeader("motorista@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "motorista@vanrides.dev", "Motorista", null, 66L));

        mockMvc.perform(get("/company-admins/viagens")
                        .servletPath("/company-admins/viagens")
                        .header("Authorization", basicHeader("motorista@vanrides.dev", "Secret123")))
                .andExpect(status().isForbidden());

        verify(companyAdminViagemService, never()).listarViagens(eq(66L), eq(null));
    }

    private AdminMotoristaSummaryResponse motorista(Long id) {
        return new AdminMotoristaSummaryResponse(
                id,
                "Motorista " + id,
                "motorista" + id + "@vanrides.dev",
                "51999990000"
        );
    }

    private AdminViagemParadaResponse parada(Long id,
                                             Long passageiroId,
                                             PresencaServicoStatus presencaStatus,
                                             boolean proximaParada) {
        return new AdminViagemParadaResponse(
                id,
                1,
                ViagemParadaStatus.PENDING,
                12,
                presencaStatus == PresencaServicoStatus.CONFIRMED,
                presencaStatus == PresencaServicoStatus.UNKNOWN ? null : 80L + passageiroId,
                presencaStatus,
                proximaParada,
                new ViagemPassageiroResumoResponse(
                        passageiroId,
                        "Passageiro " + passageiroId,
                        false
                )
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
