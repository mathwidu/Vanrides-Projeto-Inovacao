package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemMotoristaResponse;
import com.vanrides.vanrides.dto.ViagemParadaResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroAtualResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.dto.ViagemRotaWaypointResponse;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.OperationalDateProvider;
import com.vanrides.vanrides.service.ViagemOperacionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

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

@WebMvcTest(ViagemController.class)
class ViagemControllerWebMvcTest {
    @Autowired
    private MockMvc mockMvc;
    @MockBean
    private ViagemOperacionalService viagemOperacionalService;
    @MockBean
    private GuardianAccessService guardianAccessService;
    @MockBean
    private AuthorizationService authorizationService;
    @MockitoBean
    private OperationalDateProvider operationalDateProvider;

    @Test
    void shouldAllowDriverToReadOwnTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.of(driverTripResponse()));

        mockMvc.perform(get("/viagens/motorista/7/hoje")
                        .servletPath("/viagens/motorista/7/hoje")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.proximaParada.passageiro.nome").value("Aluno Um"))
                .andExpect(jsonPath("$.rotaGeografica.provider").value("STATIC_GEOMETRY_V1"))
                .andExpect(jsonPath("$.rotaGeografica.navegacao.provider").value("GOOGLE_MAPS_HANDOFF"))
                .andExpect(jsonPath("$.rotaGeografica.navegacaoRestante.provider").value("GOOGLE_MAPS_HANDOFF"))
                .andExpect(jsonPath("$.rotaGeografica.routeSnapshotStatus").value("PLANNED"))
                .andExpect(jsonPath("$.rotaGeografica.routeRecomputeReason").value("NONE"));
    }

    @Test
    void shouldAllowDriverToReadDayAgenda() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.buscarAgendaDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(List.of(driverTripResponse(30L, "Linha Manha"), driverTripResponse(31L, "Linha Tarde")));

        mockMvc.perform(get("/viagens/motorista/7/dia")
                        .servletPath("/viagens/motorista/7/dia")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30))
                .andExpect(jsonPath("$[0].servicoNome").value("Linha Manha"))
                .andExpect(jsonPath("$[1].id").value(31))
                .andExpect(jsonPath("$[1].servicoNome").value("Linha Tarde"));
    }

    @Test
    void shouldReturnNoContentWhenPassengerHasNoTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));
        when(viagemOperacionalService.buscarViagemAtualDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.empty());

        mockMvc.perform(get("/viagens/passageiro/21/atual")
                        .servletPath("/viagens/passageiro/21/atual")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isNoContent());
    }

    @Test
    void shouldAllowGuardianToReadLinkedPassengerTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(71L, "GUARDIAN", "guardian@vanrides.dev", "Responsavel", null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(71L, 21L, 66L)).thenReturn(true);
        when(viagemOperacionalService.buscarViagemAtualDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.of(passengerTripResponse()));

        mockMvc.perform(get("/viagens/passageiro/21/atual")
                        .servletPath("/viagens/passageiro/21/atual")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("guardian@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.paradaDoPassageiro.passageiro.id").value(21))
                .andExpect(jsonPath("$.status").value("PLANNED"))
                .andExpect(jsonPath("$.rotaGeografica.filaTotalParadas").value(1))
                .andExpect(jsonPath("$.rotaGeografica.posicaoPassageiroNaFila").value(1));
    }

    @Test
    void shouldAllowGuardianToReadLinkedPassengerDayAgenda() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(71L, "GUARDIAN", "guardian@vanrides.dev", "Responsavel", null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(71L, 21L, 66L)).thenReturn(true);
        when(viagemOperacionalService.buscarAgendaDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(List.of(passengerTripResponse(30L, "Linha Manha"), passengerTripResponse(31L, "Linha Tarde")));

        mockMvc.perform(get("/viagens/passageiro/21/dia")
                        .servletPath("/viagens/passageiro/21/dia")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("guardian@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").value(30))
                .andExpect(jsonPath("$[0].servicoNome").value("Linha Manha"))
                .andExpect(jsonPath("$[1].id").value(31))
                .andExpect(jsonPath("$[1].servicoNome").value("Linha Tarde"));
    }

    @Test
    void shouldRejectGuardianWhenPassengerIsNotLinked() throws Exception {
        when(authorizationService.authenticate(basicHeader("guardian@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(71L, "GUARDIAN", "guardian@vanrides.dev", "Responsavel", null, 66L));
        when(guardianAccessService.hasLinkedPassageiro(71L, 22L, 66L)).thenReturn(false);

        mockMvc.perform(get("/viagens/passageiro/22/atual")
                        .servletPath("/viagens/passageiro/22/atual")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("guardian@vanrides.dev", "Secret123")))
                .andExpect(status().isNotFound());

        verify(viagemOperacionalService, never()).buscarViagemAtualDoPassageiro(eq(22L), any(), eq(66L));
    }

    @Test
    void shouldAllowDriverToUpdateTripStatusWhenOwnTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.atualizarStatusDaViagem(30L, 66L, ViagemStatus.IN_PROGRESS))
                .thenReturn(driverTripResponse());

        mockMvc.perform(put("/viagens/30/status")
                        .servletPath("/viagens/30/status")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    void shouldAllowDriverToStartOwnTripWithExplicitAction() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.iniciarCorrida(30L, 66L)).thenReturn(driverTripResponseWithRouteSnapshot());

        mockMvc.perform(post("/viagens/30/iniciar")
                        .servletPath("/viagens/30/iniciar")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30))
                .andExpect(jsonPath("$.rotaGeografica.snapshotOperacional").value(true))
                .andExpect(jsonPath("$.rotaGeografica.calculadaEm").value("2026-03-12T07:35:00"))
                .andExpect(jsonPath("$.rotaGeografica.routeSnapshotStatus").value("MATERIALIZED"))
                .andExpect(jsonPath("$.rotaGeografica.routeRecomputeReason").value("TRIP_STARTED"))
                .andExpect(jsonPath("$.rotaGeografica.routeRecomputeAllowedAfter").value("2026-03-12T07:37:00"));
    }

    @Test
    void shouldAllowDriverToConcludeOwnStopWithExplicitAction() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.concluirParada(30L, 300L, 66L)).thenReturn(driverTripResponse());

        mockMvc.perform(post("/viagens/30/paradas/300/concluir")
                        .servletPath("/viagens/30/paradas/300/concluir")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    void shouldAllowDriverToMarkOwnStopAsNoShowWithExplicitAction() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.marcarNoShowParada(30L, 300L, 66L)).thenReturn(driverTripResponse());

        mockMvc.perform(post("/viagens/30/paradas/300/no-show")
                        .servletPath("/viagens/30/paradas/300/no-show")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    void shouldAllowDriverToEndOwnTripWithExplicitAction() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.encerrarCorrida(30L, 66L)).thenReturn(driverTripResponse());

        mockMvc.perform(post("/viagens/30/encerrar")
                        .servletPath("/viagens/30/encerrar")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(30));
    }

    @Test
    void shouldForbidPassengerFromUpdatingTripStatus() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));

        mockMvc.perform(put("/viagens/30/status")
                        .servletPath("/viagens/30/status")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "status":"IN_PROGRESS"
                                }
                                """))
                .andExpect(status().isForbidden());

        verify(viagemOperacionalService, never()).atualizarStatusDaViagem(eq(30L), eq(66L), any());
    }

    @Test
    void shouldAllowDriverToReorderOwnTripStops() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.viagemPertenceAoMotorista(30L, 7L, 66L)).thenReturn(true);
        when(viagemOperacionalService.atualizarRotaDaViagem(eq(30L), eq(66L), eq(List.of(301L, 300L))))
                .thenReturn(driverTripResponse());

        mockMvc.perform(put("/viagens/30/rota")
                        .servletPath("/viagens/30/rota")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "paradaIds":[301,300]
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.rotaVersao").value(2));
    }

    private ViagemMotoristaResponse driverTripResponse() {
        return driverTripResponse(30L, "Rota Escolar Manha");
    }
    private ViagemMotoristaResponse driverTripResponse(Long id, String nomeOperacional) {
        return driverTripResponse(id, nomeOperacional, rotaGeograficaResponse());
    }
    private ViagemMotoristaResponse driverTripResponseWithRouteSnapshot() {
        return driverTripResponse(30L, "Rota Escolar Manha", rotaGeograficaSnapshotResponse());
    }
    private ViagemMotoristaResponse driverTripResponse(Long id,
                                                       String nomeOperacional,
                                                       ViagemRotaGeograficaResponse rotaGeografica) {
        ViagemParadaResponse parada = new ViagemParadaResponse(
                300L,
                1,
                ViagemParadaStatus.PENDING,
                12,
                true,
                true,
                new ViagemPassageiroResumoResponse(21L, "Aluno Um", true)
        );
        return new ViagemMotoristaResponse(
                id,
                10L,
                15L,
                nomeOperacional,
                nomeOperacional,
                null,
                LocalDate.of(2026, 3, 12),
                null,
                ViagemStatus.PLANNED,
                2,
                12,
                LocalDate.of(2026, 3, 12).atStartOfDay(),
                null,
                parada,
                List.of(parada),
                rotaGeografica
        );
    }

    private ViagemPassageiroAtualResponse passengerTripResponse() {
        return passengerTripResponse(30L, "Rota Escolar Manha");
    }
    private ViagemPassageiroAtualResponse passengerTripResponse(Long id, String nomeOperacional) {
        ViagemParadaResponse parada = new ViagemParadaResponse(
                300L,
                1,
                ViagemParadaStatus.PENDING,
                12,
                true,
                true,
                new ViagemPassageiroResumoResponse(21L, "Aluno Um", true)
        );
        return new ViagemPassageiroAtualResponse(
                id,
                15L,
                nomeOperacional,
                nomeOperacional,
                null,
                LocalDate.of(2026, 3, 12),
                null,
                ViagemStatus.PLANNED,
                2,
                12,
                LocalDate.of(2026, 3, 12).atStartOfDay(),
                null,
                parada,
                rotaGeograficaResponse()
        );
    }

    private ViagemRotaGeograficaResponse rotaGeograficaResponse() {
        return new ViagemRotaGeograficaResponse(
                "STATIC_GEOMETRY_V1",
                "abcd",
                new ViagemNavegacaoResponse(
                        "GOOGLE_MAPS_HANDOFF",
                        "https://www.google.com/maps/dir/?api=1",
                        "Motorista",
                        "Aluno Um",
                        0
                ),
                List.of(new ViagemRotaWaypointResponse(
                        "STOP",
                        300L,
                        21L,
                        "Aluno Um",
                        -29.0,
                        -51.0,
                        1,
                        true,
                        true
                )),
                List.of(new ViagemRotaLegResponse(
                        1,
                        "Motorista",
                        "Aluno Um",
                        1200,
                        300
                )),
                1,
                1,
                0
        );
    }

    private ViagemRotaGeograficaResponse rotaGeograficaSnapshotResponse() {
        return new ViagemRotaGeograficaResponse(
                "GOOGLE_DIRECTIONS_V1",
                "persisted-polyline",
                new ViagemNavegacaoResponse(
                        "GOOGLE_MAPS_HANDOFF",
                        "https://www.google.com/maps/dir/?persisted=true",
                        "Motorista",
                        "Aluno Um",
                        0
                ),
                List.of(new ViagemRotaWaypointResponse(
                        "STOP",
                        300L,
                        21L,
                        "Aluno Um",
                        -29.0,
                        -51.0,
                        1,
                        true,
                        true
                )),
                List.of(new ViagemRotaLegResponse(
                        1,
                        "Motorista",
                        "Aluno Um",
                        1200,
                        300
                )),
                1,
                1,
                0,
                LocalDateTime.of(2026, 3, 12, 7, 35),
                true
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
