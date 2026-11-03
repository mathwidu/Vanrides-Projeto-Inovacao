package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.RouteRecomputeReason;
import com.vanrides.vanrides.dto.RouteSnapshotStatus;
import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.lenient;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemRotaGeograficaServiceTest {

    @Mock
    private LocalizacaoMotoristaService localizacaoMotoristaService;

    @Mock
    private GoogleDirectionsRoutingService googleDirectionsRoutingService;

    @Mock
    private MapsPlatformTripRouteFacade mapsPlatformTripRouteFacade;

    @InjectMocks
    private ViagemRotaGeograficaService viagemRotaGeograficaService;

    @BeforeEach
    void setUp() {
        lenient().when(mapsPlatformTripRouteFacade.planRoute(any(), any(), anyList()))
                .thenReturn(Optional.empty());
    }

    @Test
    void shouldBuildStaticGeometryWithDriverPositionAndPassengerQueue() {
        Viagem viagem = viagem();
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 30));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);
        when(googleDirectionsRoutingService.compute(anyList())).thenReturn(Optional.empty());

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);

        assertThat(response.getProvider()).isEqualTo(ViagemRotaGeograficaService.PROVIDER_STATIC_GEOMETRY);
        assertThat(response.getPolyline()).isNotBlank();
        assertThat(response.getWaypoints()).hasSize(3);
        assertThat(response.getWaypoints().get(0).getTipo()).isEqualTo("DRIVER");
        assertThat(response.getFilaTotalParadas()).isEqualTo(2);
        assertThat(response.getPosicaoPassageiroNaFila()).isEqualTo(2);
        assertThat(response.getParadasAntesDoPassageiro()).isEqualTo(1);
        assertThat(response.getLegs()).hasSize(2);
        assertThat(response.getNavegacao().getProvider()).isEqualTo("GOOGLE_MAPS_HANDOFF");
        assertThat(response.getNavegacao().getQuantidadeWaypoints()).isEqualTo(1);
        assertThat(response.getNavegacao().getUrl()).contains("google.com/maps/dir");
    }

    @Test
    void shouldReturnEmptyGeometryWhenTripHasNoGeocodedStops() {
        Viagem viagem = viagem();
        ViagemParada paradaSemCoordenada = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", null, null), ViagemParadaStatus.PENDING);

        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(null);

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(paradaSemCoordenada), 21L);

        assertThat(response.getProvider()).isEqualTo(ViagemRotaGeograficaService.PROVIDER_STATIC_GEOMETRY);
        assertThat(response.getPolyline()).isNull();
        assertThat(response.getWaypoints()).isEmpty();
        assertThat(response.getLegs()).isEmpty();
        assertThat(response.getNavegacao()).isNull();
    }

    @Test
    void shouldPreferGoogleDirectionsRouteWhenProviderReturnsStreetGeometry() {
        Viagem viagem = viagem();
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 30));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);
        when(googleDirectionsRoutingService.compute(anyList())).thenReturn(Optional.of(
                new GoogleDirectionsRoutingService.ComputedRoute(
                        GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS,
                        "real-polyline",
                        List.of(
                                new GoogleDirectionsRoutingService.ComputedLeg(1, "Motorista", "Aluno Um", 1400, 360),
                                new GoogleDirectionsRoutingService.ComputedLeg(2, "Aluno Um", "Aluno Dois", 900, 240)
                        )
                )
        ));

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);

        assertThat(response.getProvider()).isEqualTo(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        assertThat(response.getPolyline()).isEqualTo("real-polyline");
        assertThat(response.getLegs()).hasSize(2);
        assertThat(response.getLegs().get(0).getDistanciaMetros()).isEqualTo(1400);
        assertThat(response.getLegs().get(0).getDuracaoSegundos()).isEqualTo(360);
        assertThat(response.getNavegacao().getProvider()).isEqualTo("GOOGLE_MAPS_HANDOFF");
    }

    @Test
    void shouldPreferValidatedMapsPlatformRouteBeforeGoogleDirections() {
        Viagem viagem = viagem();
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 30));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);
        when(mapsPlatformTripRouteFacade.planRoute(any(), any(), anyList())).thenReturn(Optional.of(
                new MapsPlatformPlannedRoute(
                        "MAPS_PLATFORM_VALHALLA",
                        "maps-polyline",
                        List.of(new ViagemRotaLegResponse(1, "Motorista", "Aluno Um", 2100, 390)),
                        null
                )
        ));

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);

        assertThat(response.getProvider()).isEqualTo("MAPS_PLATFORM_VALHALLA");
        assertThat(response.getPolyline()).isEqualTo("maps-polyline");
        assertThat(response.getLegs()).hasSize(1);
        assertThat(response.getLegs().get(0).getDistanciaMetros()).isEqualTo(2100);
        assertThat(response.getNavegacao().getProvider()).isEqualTo("GOOGLE_MAPS_HANDOFF");
        verify(googleDirectionsRoutingService, never()).compute(anyList());
    }

    @Test
    void shouldReuseOperationalRouteSnapshotWhenTripAlreadyStarted() {
        Viagem viagem = viagem();
        viagem.setStatus(ViagemStatus.IN_PROGRESS);
        viagem.setRotaGeograficaProviderSnapshot(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        viagem.setRotaGeograficaPolylineSnapshot("persisted-polyline");
        viagem.setRotaGeograficaNavegacaoUrlSnapshot("https://www.google.com/maps/dir/?persisted=true");
        viagem.setRotaGeograficaCalculadaEm(LocalDateTime.of(2026, 3, 19, 7, 35));
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7010);
        localizacao.setLongitude(-51.1110);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 40));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);

        assertThat(response.isSnapshotOperacional()).isTrue();
        assertThat(response.getCalculadaEm()).isEqualTo(LocalDateTime.of(2026, 3, 19, 7, 35));
        assertThat(response.getProvider()).isEqualTo(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        assertThat(response.getPolyline()).isEqualTo("persisted-polyline");
        assertThat(response.getNavegacao().getUrl()).isEqualTo("https://www.google.com/maps/dir/?persisted=true");
        assertThat(response.getWaypoints()).hasSize(3);
        assertThat(response.getPosicaoPassageiroNaFila()).isEqualTo(2);
        assertThat(response.getSnapshotKey()).isEqualTo("company:1:viagem:5:rota:1");
        assertThat(response.getRouteInputHash()).isNotBlank();
        assertThat(response.getRouteSnapshotStatus()).isEqualTo(RouteSnapshotStatus.MATERIALIZED);
        assertThat(response.getRouteRecomputeReason()).isEqualTo(RouteRecomputeReason.TRIP_STARTED);
        assertThat(response.getRouteRecomputeAllowedAfter()).isEqualTo(LocalDateTime.of(2026, 3, 19, 7, 37));
        verify(googleDirectionsRoutingService, never()).compute(anyList());
    }

    @Test
    void shouldKeepRouteInputHashStableAcrossGpsUpdates() {
        Viagem viagem = viagem();
        viagem.setStatus(ViagemStatus.IN_PROGRESS);
        viagem.setRotaGeograficaProviderSnapshot(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        viagem.setRotaGeograficaPolylineSnapshot("persisted-polyline");
        viagem.setRotaGeograficaNavegacaoUrlSnapshot("https://www.google.com/maps/dir/?persisted=true");
        viagem.setRotaGeograficaCalculadaEm(LocalDateTime.of(2026, 3, 19, 7, 35));
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170), ViagemParadaStatus.PENDING);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista primeiraLocalizacao = new LocalizacaoMotorista();
        primeiraLocalizacao.setLatitude(-29.7010);
        primeiraLocalizacao.setLongitude(-51.1110);
        primeiraLocalizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 40));
        LocalizacaoMotorista segundaLocalizacao = new LocalizacaoMotorista();
        segundaLocalizacao.setLatitude(-29.7020);
        segundaLocalizacao.setLongitude(-51.1120);
        segundaLocalizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 41));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L))
                .thenReturn(primeiraLocalizacao, segundaLocalizacao);

        var first = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);
        var second = viagemRotaGeograficaService.buildForTrip(viagem, List.of(primeira, segunda), 22L);

        assertThat(first.getRouteInputHash()).isEqualTo(second.getRouteInputHash());
        assertThat(first.getSnapshotKey()).isEqualTo(second.getSnapshotKey());
        verify(googleDirectionsRoutingService, never()).compute(anyList());
    }

    @Test
    void shouldExposeRemainingNavigationWithoutCompletedStops() {
        Viagem viagem = viagem();
        viagem.setStatus(ViagemStatus.IN_PROGRESS);
        viagem.setDestinoFinalNomeSnapshot("Campus");
        viagem.setDestinoFinalLatitudeSnapshot(-29.7100);
        viagem.setDestinoFinalLongitudeSnapshot(-51.1300);
        viagem.setRotaGeograficaProviderSnapshot(GoogleDirectionsRoutingService.PROVIDER_GOOGLE_DIRECTIONS);
        viagem.setRotaGeograficaPolylineSnapshot("persisted-polyline");
        viagem.setRotaGeograficaNavegacaoUrlSnapshot("https://www.google.com/maps/dir/?original=true");
        viagem.setRotaGeograficaCalculadaEm(LocalDateTime.of(2026, 3, 19, 7, 35));
        ViagemParada atendida = parada(300L, viagem, 1, passageiro(21L, "Aluno Atendido", -29.7030, -51.1170), ViagemParadaStatus.COMPLETED);
        ViagemParada pendente = parada(301L, viagem, 2, passageiro(22L, "Aluno Pendente", -29.7060, -51.1230), ViagemParadaStatus.PENDING);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7010);
        localizacao.setLongitude(-51.1110);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 45));
        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);

        var response = viagemRotaGeograficaService.buildForTrip(viagem, List.of(atendida, pendente), 22L);

        assertThat(response.getNavegacao().getUrl()).isEqualTo("https://www.google.com/maps/dir/?original=true");
        assertThat(response.getNavegacaoRestante()).isNotNull();
        assertThat(response.getNavegacaoRestante().getQuantidadeWaypoints()).isEqualTo(1);
        assertThat(response.getNavegacaoRestante().getUrl()).contains("-29.706000,-51.123000");
        assertThat(response.getNavegacaoRestante().getUrl()).doesNotContain("-29.703000,-51.117000");
        verify(googleDirectionsRoutingService, never()).compute(anyList());
    }

    private Viagem viagem() {
        Company company = new Company("Default", "default");
        company.setId(1L);

        Motorista motorista = new Motorista();
        motorista.setId(4L);
        motorista.setNome("Motorista QA");

        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(3L);
        rotaBase.setNome("Rota QA");
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);

        Viagem viagem = new Viagem();
        viagem.setId(5L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 19));
        viagem.setStatus(ViagemStatus.PLANNED);
        return viagem;
    }

    private ViagemParada parada(Long id,
                                Viagem viagem,
                                int ordem,
                                Passageiro passageiro,
                                ViagemParadaStatus status) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        parada.setStatus(status);
        return parada;
    }

    private Passageiro passageiro(Long id, String nome, Double latitude, Double longitude) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setLatitude(latitude);
        passageiro.setLongitude(longitude);
        return passageiro;
    }
}
