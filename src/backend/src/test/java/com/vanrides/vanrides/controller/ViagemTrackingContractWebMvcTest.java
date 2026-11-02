package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.EtaConfidence;
import com.vanrides.vanrides.dto.LocationFreshness;
import com.vanrides.vanrides.dto.RouteRecomputeReason;
import com.vanrides.vanrides.dto.RouteSnapshotStatus;
import com.vanrides.vanrides.dto.TrackingStateResponse;
import com.vanrides.vanrides.dto.ViagemMotoristaResponse;
import com.vanrides.vanrides.dto.ViagemParadaResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroAtualResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.GuardianAccessService;
import com.vanrides.vanrides.service.OperationalDateProvider;
import com.vanrides.vanrides.service.ViagemOperacionalService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ViagemController.class)
class ViagemTrackingContractWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private ViagemOperacionalService viagemOperacionalService;

    @MockitoBean
    private GuardianAccessService guardianAccessService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @MockitoBean
    private OperationalDateProvider operationalDateProvider;

    @Test
    void shouldExposeTrackingStateForDriverTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));
        when(viagemOperacionalService.buscarViagemDoMotorista(7L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.of(driverTripResponse()));

        mockMvc.perform(get("/viagens/motorista/7/hoje")
                        .servletPath("/viagens/motorista/7/hoje")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingState.routeSnapshotStatus").value("MATERIALIZED"))
                .andExpect(jsonPath("$.trackingState.locationFreshness").value("LIVE"))
                .andExpect(jsonPath("$.trackingState.etaConfidence").value("HIGH"))
                .andExpect(jsonPath("$.trackingState.stopsRemaining").value(1))
                .andExpect(jsonPath("$.trackingState.stopsCompleted").value(0))
                .andExpect(jsonPath("$.trackingState.nextStopId").value(300))
                .andExpect(jsonPath("$.trackingState.nextStopOrder").value(1))
                .andExpect(jsonPath("$.trackingState.driverLocationUpdatedAt").value("2026-03-12T07:40:00"))
                .andExpect(jsonPath("$.trackingState.routeRecomputeReason").value("TRIP_STARTED"))
                .andExpect(jsonPath("$.trackingState.routeRecomputedAt").value("2026-03-12T07:35:00"));
    }

    @Test
    void shouldExposeTrackingStateForPassengerTrip() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));
        when(viagemOperacionalService.buscarViagemAtualDoPassageiro(21L, LocalDate.of(2026, 3, 12), 66L))
                .thenReturn(Optional.of(passengerTripResponse()));

        mockMvc.perform(get("/viagens/passageiro/21/atual")
                        .servletPath("/viagens/passageiro/21/atual")
                        .param("data", "2026-03-12")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.trackingState.locationFreshness").value("LIVE"))
                .andExpect(jsonPath("$.trackingState.stopsBeforePassenger").value(0));
    }

    private ViagemMotoristaResponse driverTripResponse() {
        ViagemParadaResponse parada = parada();
        return new ViagemMotoristaResponse(
                30L,
                10L,
                15L,
                "Rota Escolar Manha",
                "Rota Escolar Manha",
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
                null,
                premiumTrackingState()
        );
    }

    private ViagemPassageiroAtualResponse passengerTripResponse() {
        ViagemParadaResponse parada = parada();
        return new ViagemPassageiroAtualResponse(
                30L,
                15L,
                "Rota Escolar Manha",
                "Rota Escolar Manha",
                null,
                LocalDate.of(2026, 3, 12),
                null,
                ViagemStatus.PLANNED,
                2,
                12,
                LocalDate.of(2026, 3, 12).atStartOfDay(),
                null,
                parada,
                null,
                premiumTrackingState()
        );
    }

    private ViagemParadaResponse parada() {
        return new ViagemParadaResponse(
                300L,
                1,
                ViagemParadaStatus.PENDING,
                12,
                true,
                true,
                new ViagemPassageiroResumoResponse(21L, "Aluno Um", true)
        );
    }

    private TrackingStateResponse premiumTrackingState() {
        return new TrackingStateResponse(
                RouteSnapshotStatus.MATERIALIZED,
                LocationFreshness.LIVE,
                EtaConfidence.HIGH,
                1,
                0,
                0,
                300L,
                1,
                LocalDateTime.of(2026, 3, 12, 7, 40),
                RouteRecomputeReason.TRIP_STARTED,
                LocalDateTime.of(2026, 3, 12, 7, 35)
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
