package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminViagemDestinoFinalResponse;
import com.vanrides.vanrides.dto.AdminViagemDetailResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestPassengerResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestPreviewResponse;
import com.vanrides.vanrides.dto.AdminViagemParadaResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
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

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.Base64;
import java.util.List;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(CompanyAdminViagemController.class)
class CompanyAdminViagemManifestControllerWebMvcTest {

    @Autowired
    private org.springframework.test.web.servlet.MockMvc mockMvc;

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
    void shouldPreviewTripManifestSyncForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader()))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.previewSincronizacaoManifesto(66L, 32L))
                .thenReturn(new AdminViagemManifestPreviewResponse(
                        32L,
                        15L,
                        "Linha Manhã",
                        LocalDate.of(2026, 3, 13),
                        2,
                        3,
                        2,
                        1,
                        1,
                        3,
                        false,
                        List.of(new AdminViagemManifestPassengerResponse(23L, "Novo Passageiro", false, null, 2)),
                        List.of(new AdminViagemManifestPassengerResponse(21L, "Sai da rota", false, 1, null)),
                        List.of(new AdminViagemManifestPassengerResponse(22L, "Permanece", false, 2, 1))
                ));

        mockMvc.perform(get("/company-admins/viagens/32/manifesto-preview")
                        .servletPath("/company-admins/viagens/32/manifesto-preview")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.viagemId").value(32))
                .andExpect(jsonPath("$.servicoId").value(15))
                .andExpect(jsonPath("$.paradasAtuais").value(2))
                .andExpect(jsonPath("$.paradasAposSincronizacao").value(3))
                .andExpect(jsonPath("$.adicionados").value(2))
                .andExpect(jsonPath("$.removidos").value(1))
                .andExpect(jsonPath("$.mudancas").value(3))
                .andExpect(jsonPath("$.semMudancas").value(false))
                .andExpect(jsonPath("$.passageirosAdicionados[0].passageiroId").value(23))
                .andExpect(jsonPath("$.passageirosAdicionados[0].ordemNova").value(2))
                .andExpect(jsonPath("$.passageirosRemovidos[0].ordemAtual").value(1));
    }

    @Test
    void shouldSyncTripManifestForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader()))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.sincronizarManifesto(66L, 32L))
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
                        4,
                        12,
                        null,
                        motorista(8L),
                        2,
                        new AdminViagemDestinoFinalResponse(null, null, null, null),
                        parada(51L, 21L, PresencaServicoStatus.UNKNOWN, true),
                        List.of(
                                parada(51L, 21L, PresencaServicoStatus.UNKNOWN, true),
                                parada(52L, 22L, PresencaServicoStatus.UNKNOWN, false)
                        ),
                        "SERVICO_RECORRENTE",
                        true
                ));

        mockMvc.perform(post("/company-admins/viagens/32/sincronizar-manifesto")
                        .servletPath("/company-admins/viagens/32/sincronizar-manifesto")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.rotaVersao").value(4))
                .andExpect(jsonPath("$.quantidadeParadas").value(2))
                .andExpect(jsonPath("$.paradas[1].passageiro.id").value(22));
    }

    @Test
    void shouldCancelPlannedTripForCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader()))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.viagens@vanrides.dev", "Admin", null, 66L));
        when(companyAdminViagemService.cancelarViagem(66L, 32L))
                .thenReturn(new AdminViagemDetailResponse(
                        32L,
                        null,
                        15L,
                        null,
                        "Linha Manhã",
                        TipoOperacaoServico.IDA,
                        LocalDate.of(2026, 3, 13),
                        null,
                        ViagemStatus.CANCELED,
                        3,
                        0,
                        null,
                        motorista(8L),
                        0,
                        new AdminViagemDestinoFinalResponse(null, null, null, null),
                        null,
                        List.of(),
                        "SERVICO_RECORRENTE",
                        true
                ));

        mockMvc.perform(post("/company-admins/viagens/32/cancelar")
                        .servletPath("/company-admins/viagens/32/cancelar")
                        .header("Authorization", basicHeader()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.status").value("CANCELED"));
    }

    private AdminMotoristaSummaryResponse motorista(Long id) {
        return new AdminMotoristaSummaryResponse(id, "Motorista " + id, "motorista" + id + "@vanrides.dev", "51999990000");
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
                new ViagemPassageiroResumoResponse(passageiroId, "Passageiro " + passageiroId, false)
        );
    }

    private String basicHeader() {
        String raw = "admin.viagens@vanrides.dev:Secret123";
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
