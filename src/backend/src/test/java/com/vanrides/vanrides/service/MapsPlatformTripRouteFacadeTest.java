package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.ViagemRotaLegResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.service.maps.MapsNavigationInstruction;
import com.vanrides.vanrides.service.maps.MapsPlatformClientUnavailableException;
import com.vanrides.vanrides.service.maps.MapsPlatformRouteClient;
import com.vanrides.vanrides.service.maps.MapsRouteLeg;
import com.vanrides.vanrides.service.maps.MapsRouteOptions;
import com.vanrides.vanrides.service.maps.MapsRoutePlanRequest;
import com.vanrides.vanrides.service.maps.MapsRoutePlanResponse;
import com.vanrides.vanrides.service.maps.MapsRouteStop;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MapsPlatformTripRouteFacadeTest {

    @Mock
    private MapsPlatformRouteClient mapsPlatformRouteClient;

    @InjectMocks
    private MapsPlatformTripRouteFacade facade;

    @Test
    void shouldNotCallMapsPlatformWhenClientIsDisabled() {
        when(mapsPlatformRouteClient.isEnabled()).thenReturn(false);

        Optional<MapsPlatformPlannedRoute> result = facade.planRoute(
                viagem(),
                localizacaoMotorista(),
                paradasGeocodificadas(viagem())
        );

        assertThat(result).isEmpty();
        verify(mapsPlatformRouteClient, never()).planRoute(any());
    }

    @Test
    void shouldBuildTripRequestAndKeepFallbackForFixtureResponse() {
        Viagem viagem = viagem();
        when(mapsPlatformRouteClient.isEnabled()).thenReturn(true);
        when(mapsPlatformRouteClient.planRoute(any())).thenReturn(fallbackResponse());

        Optional<MapsPlatformPlannedRoute> result = facade.planRoute(
                viagem,
                localizacaoMotorista(),
                paradasGeocodificadas(viagem)
        );

        assertThat(result).isEmpty();
        ArgumentCaptor<MapsRoutePlanRequest> requestCaptor = ArgumentCaptor.forClass(MapsRoutePlanRequest.class);
        verify(mapsPlatformRouteClient).planRoute(requestCaptor.capture());
        MapsRoutePlanRequest request = requestCaptor.getValue();

        assertThat(request.region()).isEqualTo("BR-RS");
        assertThat(request.vehicleType()).isEqualTo("van_school_safe");
        assertThat(request.options()).isEqualTo(new MapsRouteOptions(false, true, true));
        assertThat(request.stops()).extracting(MapsRouteStop::id)
                .containsExactly("driver", "stop-300", "stop-301", "destination");
        assertThat(request.stops()).extracting(MapsRouteStop::label)
                .containsExactly("Motorista QA", "Aluno Um", "Aluno Dois", "Campus");
        assertThat(request.stops().get(0).lat()).isEqualTo(-29.7000);
        assertThat(request.stops().get(3).lon()).isEqualTo(-51.1300);
    }

    @Test
    void shouldConvertValidatedReadyResponseToPlannedRoute() {
        Viagem viagem = viagem();
        when(mapsPlatformRouteClient.isEnabled()).thenReturn(true);
        when(mapsPlatformRouteClient.planRoute(any())).thenReturn(validatedResponse());

        Optional<MapsPlatformPlannedRoute> result = facade.planRoute(
                viagem,
                localizacaoMotorista(),
                paradasGeocodificadas(viagem)
        );

        assertThat(result).isPresent();
        assertThat(result.get().provider()).isEqualTo("MAPS_PLATFORM_VALHALLA");
        assertThat(result.get().polyline()).isNotBlank();
        assertThat(result.get().legs()).hasSize(2);
        assertThat(result.get().legs().get(0).getOrigemLabel()).isEqualTo("Motorista QA");
        assertThat(result.get().legs().get(0).getDestinoLabel()).isEqualTo("Aluno Um");
        assertThat(result.get().legs().get(0).getDistanciaMetros()).isEqualTo(2100);
        assertThat(result.get().navigationRoutePlan().getRouteId()).isEqualTo("route-br-rs-001");
        assertThat(result.get().navigationRoutePlan().getCoverageStatus()).isEqualTo("validada");
        assertThat(result.get().navigationRoutePlan().getInstructions())
                .extracting("voiceText")
                .containsExactly("Siga");
    }

    @Test
    void shouldKeepFallbackWhenMapsPlatformFails() {
        when(mapsPlatformRouteClient.isEnabled()).thenReturn(true);
        when(mapsPlatformRouteClient.planRoute(any()))
                .thenThrow(new MapsPlatformClientUnavailableException("Maps Platform request failed"));

        Optional<MapsPlatformPlannedRoute> result = facade.planRoute(
                viagem(),
                localizacaoMotorista(),
                paradasGeocodificadas(viagem())
        );

        assertThat(result).isEmpty();
    }

    private Viagem viagem() {
        Company company = new Company("Default", "default");
        company.setId(1L);

        Motorista motorista = new Motorista();
        motorista.setId(4L);
        motorista.setNome("Motorista QA");

        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(3L);
        rotaBase.setCompany(company);
        rotaBase.setMotorista(motorista);

        Viagem viagem = new Viagem();
        viagem.setId(5L);
        viagem.setCompany(company);
        viagem.setMotorista(motorista);
        viagem.setRotaBase(rotaBase);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 19));
        viagem.setDestinoFinalNomeSnapshot("Campus");
        viagem.setDestinoFinalLatitudeSnapshot(-29.7100);
        viagem.setDestinoFinalLongitudeSnapshot(-51.1300);
        return viagem;
    }

    private LocalizacaoMotorista localizacaoMotorista() {
        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 7, 30));
        return localizacao;
    }

    private List<ViagemParada> paradasGeocodificadas(Viagem viagem) {
        return List.of(
                parada(300L, viagem, 1, passageiro(21L, "Aluno Um", -29.7030, -51.1170)),
                parada(301L, viagem, 2, passageiro(22L, "Aluno Dois", -29.7060, -51.1230))
        );
    }

    private ViagemParada parada(Long id, Viagem viagem, int ordem, Passageiro passageiro) {
        ViagemParada parada = new ViagemParada();
        parada.setId(id);
        parada.setViagem(viagem);
        parada.setOrdem(ordem);
        parada.setPassageiro(passageiro);
        parada.setStatus(ViagemParadaStatus.PENDING);
        return parada;
    }

    private Passageiro passageiro(Long id, String nome, Double latitude, Double longitude) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        passageiro.setEstado("RS");
        passageiro.setLatitude(latitude);
        passageiro.setLongitude(longitude);
        return passageiro;
    }

    private MapsRoutePlanResponse fallbackResponse() {
        return response("fixture", "fallback", "missing");
    }

    private MapsRoutePlanResponse validatedResponse() {
        return response("valhalla", "validada", "ready");
    }

    private MapsRoutePlanResponse response(String provider, String coverageStatus, String offlinePackStatus) {
        return new MapsRoutePlanResponse(
                "route-br-rs-001",
                provider,
                "BR-RS",
                "van_school_safe",
                coverageStatus,
                offlinePackStatus,
                0.95,
                null,
                780,
                4200,
                List.of(
                        List.of(-29.7000, -51.1100),
                        List.of(-29.7030, -51.1170),
                        List.of(-29.7060, -51.1230)
                ),
                List.of(
                        new MapsRouteLeg("driver", "stop-300", 2100, 390),
                        new MapsRouteLeg("stop-300", "stop-301", 2100, 390)
                ),
                List.of(
                        new MapsNavigationInstruction(0, "Siga", "Siga", 2100, "depart")
                )
        );
    }
}
