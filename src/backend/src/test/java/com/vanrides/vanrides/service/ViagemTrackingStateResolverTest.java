package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.EtaConfidence;
import com.vanrides.vanrides.dto.LocationFreshness;
import com.vanrides.vanrides.dto.RouteRecomputeReason;
import com.vanrides.vanrides.dto.RouteSnapshotStatus;
import com.vanrides.vanrides.dto.ViagemNavegacaoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ViagemTrackingStateResolverTest {

    private static final ZoneId ZONE = ZoneId.of("America/Sao_Paulo");
    private static final LocalDateTime NOW = LocalDateTime.of(2026, 3, 12, 7, 40);

    private final ViagemTrackingStateResolver resolver = new ViagemTrackingStateResolver(
            Clock.fixed(Instant.parse("2026-03-12T10:40:00Z"), ZONE)
    );

    @Test
    void shouldResolveLiveMaterializedRouteWithHighEtaConfidence() {
        Viagem viagem = viagem(ViagemStatus.IN_PROGRESS);
        ViagemParada completed = parada(300L, viagem, 1, 21L, ViagemParadaStatus.COMPLETED, 0);
        ViagemParada next = parada(301L, viagem, 2, 22L, ViagemParadaStatus.PENDING, 8);
        ViagemParada passenger = parada(302L, viagem, 3, 23L, ViagemParadaStatus.PENDING, 14);

        var state = resolver.resolve(
                viagem,
                List.of(completed, next, passenger),
                materializedRoute(),
                localizacao(NOW.minusSeconds(20)),
                23L
        );

        assertThat(state.getRouteSnapshotStatus()).isEqualTo(RouteSnapshotStatus.MATERIALIZED);
        assertThat(state.getLocationFreshness()).isEqualTo(LocationFreshness.LIVE);
        assertThat(state.getEtaConfidence()).isEqualTo(EtaConfidence.HIGH);
        assertThat(state.getStopsRemaining()).isEqualTo(2);
        assertThat(state.getStopsCompleted()).isEqualTo(1);
        assertThat(state.getStopsBeforePassenger()).isEqualTo(1);
        assertThat(state.getNextStopId()).isEqualTo(301L);
        assertThat(state.getNextStopOrder()).isEqualTo(2);
        assertThat(state.getDriverLocationUpdatedAt()).isEqualTo(NOW.minusSeconds(20));
        assertThat(state.getRouteRecomputeReason()).isEqualTo(RouteRecomputeReason.TRIP_STARTED);
        assertThat(state.getRouteRecomputedAt()).isEqualTo(NOW.minusMinutes(5));
    }

    @Test
    void shouldResolveLocationFreshnessThresholds() {
        assertThat(resolver.resolveLocationFreshness(localizacao(NOW.minusSeconds(30))))
                .isEqualTo(LocationFreshness.LIVE);
        assertThat(resolver.resolveLocationFreshness(localizacao(NOW.minusSeconds(90))))
                .isEqualTo(LocationFreshness.DELAYED);
        assertThat(resolver.resolveLocationFreshness(localizacao(NOW.minusMinutes(5))))
                .isEqualTo(LocationFreshness.STALE);
        assertThat(resolver.resolveLocationFreshness(localizacao(NOW.minusMinutes(6))))
                .isEqualTo(LocationFreshness.UNAVAILABLE);
    }

    @Test
    void shouldPauseTrackingBeforeTripStarts() {
        var state = resolver.resolve(
                viagem(ViagemStatus.PLANNED),
                List.of(),
                null,
                localizacao(NOW.minusSeconds(10)),
                null
        );

        assertThat(state.getLocationFreshness()).isEqualTo(LocationFreshness.PAUSED);
        assertThat(state.getEtaConfidence()).isEqualTo(EtaConfidence.UNAVAILABLE);
    }

    @Test
    void shouldExposeUnavailableWhenInProgressTripHasNoDriverLocation() {
        Viagem viagem = viagem(ViagemStatus.IN_PROGRESS);
        ViagemParada parada = parada(300L, viagem, 1, 21L, ViagemParadaStatus.PENDING, null);

        var state = resolver.resolve(viagem, List.of(parada), null, null, 21L);

        assertThat(state.getRouteSnapshotStatus()).isEqualTo(RouteSnapshotStatus.UNAVAILABLE);
        assertThat(state.getLocationFreshness()).isEqualTo(LocationFreshness.UNAVAILABLE);
        assertThat(state.getEtaConfidence()).isEqualTo(EtaConfidence.UNAVAILABLE);
        assertThat(state.getStopsRemaining()).isEqualTo(1);
        assertThat(state.getStopsCompleted()).isZero();
        assertThat(state.getStopsBeforePassenger()).isZero();
        assertThat(state.getNextStopId()).isEqualTo(300L);
        assertThat(state.getNextStopOrder()).isEqualTo(1);
    }

    private Viagem viagem(ViagemStatus status) {
        Company company = new Company("VanRides", "vanrides");
        company.setId(66L);

        Motorista motorista = new Motorista();
        motorista.setId(7L);
        motorista.setCompany(company);

        Viagem viagem = new Viagem();
        viagem.setId(30L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setStatus(status);
        return viagem;
    }

    private ViagemParada parada(Long id,
                                Viagem viagem,
                                int ordem,
                                Long passageiroId,
                                ViagemParadaStatus status,
                                Integer etaMinutos) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(passageiroId);

        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        parada.setStatus(status);
        parada.setEtaMinutos(etaMinutos);
        return parada;
    }

    private LocalizacaoMotorista localizacao(LocalDateTime updatedAt) {
        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-30.0346);
        localizacao.setLongitude(-51.2177);
        localizacao.setDataHora(updatedAt);
        return localizacao;
    }

    private ViagemRotaGeograficaResponse materializedRoute() {
        return new ViagemRotaGeograficaResponse(
                "GOOGLE_DIRECTIONS_V1",
                "encoded-polyline",
                new ViagemNavegacaoResponse(
                        "GOOGLE_MAPS_HANDOFF",
                        "https://maps.google.com",
                        "Motorista",
                        "Destino",
                        2
                ),
                List.of(),
                List.of(),
                3,
                3,
                1,
                NOW.minusMinutes(5),
                true
        );
    }
}
