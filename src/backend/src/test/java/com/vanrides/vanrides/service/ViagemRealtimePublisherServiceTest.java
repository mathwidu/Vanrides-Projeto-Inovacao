package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.EtaConfidence;
import com.vanrides.vanrides.dto.LocationFreshness;
import com.vanrides.vanrides.dto.RouteSnapshotStatus;
import com.vanrides.vanrides.dto.ViagemRealtimeSnapshotEvent;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.simp.SimpMessagingTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ViagemRealtimePublisherServiceTest {

    @Mock
    private SimpMessagingTemplate messagingTemplate;

    @Mock
    private ViagemProvisioningService viagemProvisioningService;

    @Mock
    private ViagemParadaRepository viagemParadaRepository;

    @Mock
    private LocalizacaoMotoristaService localizacaoMotoristaService;

    @Test
    void shouldUseSaoPauloOperationalDateWhenUtcIsAlreadyNextDay() {
        Clock fixedClock = Clock.fixed(
                Instant.parse("2026-07-17T02:30:00Z"),
                ZoneId.of("America/Sao_Paulo")
        );
        ViagemRealtimePublisherService service = new ViagemRealtimePublisherService(
                messagingTemplate,
                viagemProvisioningService,
                viagemParadaRepository,
                localizacaoMotoristaService,
                fixedClock
        );

        service.publishTodayTripSnapshotForMotorista(4L, 66L);

        verify(viagemProvisioningService).buscarOuCriarViagemAutomaticaDoMotorista(
                66L,
                4L,
                LocalDate.of(2026, 7, 16)
        );
    }

    @Test
    void shouldPublishTripScopedSnapshotWithProgressAndLocation() {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-19T16:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        ViagemRealtimePublisherService service = new ViagemRealtimePublisherService(
                messagingTemplate,
                viagemProvisioningService,
                viagemParadaRepository,
                localizacaoMotoristaService,
                fixedClock
        );

        Viagem viagem = viagem();
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um"), ViagemParadaStatus.PENDING);
        primeira.setEtaMinutos(12);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois"), ViagemParadaStatus.COMPLETED);

        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(LocalDateTime.of(2026, 3, 19, 13, 0));

        when(localizacaoMotoristaService.buscarUltimaPorMotorista(4L, 1L)).thenReturn(localizacao);
        when(viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(1L, 4L, LocalDate.of(2026, 3, 19)))
                .thenReturn(Optional.of(viagem));
        when(viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(5L))
                .thenReturn(List.of(primeira, segunda));

        service.publishTodayTripSnapshotForMotorista(4L, 1L);

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/viagens/1/5"), captor.capture());
        assertThat(captor.getValue()).isInstanceOf(com.vanrides.vanrides.dto.ViagemRealtimeSnapshotEvent.class);

        com.vanrides.vanrides.dto.ViagemRealtimeSnapshotEvent event =
                (com.vanrides.vanrides.dto.ViagemRealtimeSnapshotEvent) captor.getValue();
        assertThat(event.getTipo()).isEqualTo("VIAGEM_ATUALIZADA");
        assertThat(event.getViagemId()).isEqualTo(5L);
        assertThat(event.getParadasPendentes()).isEqualTo(1);
        assertThat(event.getParadasConcluidas()).isEqualTo(1);
        assertThat(event.getProximoPassageiroNome()).isEqualTo("Aluno Um");
        assertThat(event.getEtaMinutosAteProximaParada()).isEqualTo(12);
        assertThat(event.getLatitudeMotorista()).isEqualTo(-29.7000);
        assertThat(event.getTrackingState().getRouteSnapshotStatus()).isEqualTo(RouteSnapshotStatus.MATERIALIZED);
        assertThat(event.getTrackingState().getLocationFreshness()).isEqualTo(LocationFreshness.LIVE);
        assertThat(event.getTrackingState().getEtaConfidence()).isEqualTo(EtaConfidence.HIGH);
        assertThat(event.getTrackingState().getStopsRemaining()).isEqualTo(1);
        assertThat(event.getTrackingState().getStopsCompleted()).isEqualTo(1);
        assertThat(event.getTrackingState().getNextStopId()).isEqualTo(300L);
        assertThat(event.getTrackingState().getDriverLocationUpdatedAt())
                .isEqualTo(LocalDateTime.of(2026, 3, 19, 13, 0));
        assertThat(event.getTrackingState().getRouteRecomputedAt())
                .isEqualTo(LocalDateTime.of(2026, 3, 19, 12, 45));
        assertThat(event.getAvisosOperacionais())
                .extracting("tipo")
                .containsExactly("NEXT_PASSENGER", "STOP_COMPLETED");
        assertThat(event.getAvisosOperacionais().get(0).getPassageiroId()).isEqualTo(21L);
        assertThat(event.getAvisosOperacionais().get(1).getParadaId()).isEqualTo(301L);
    }

    @Test
    void shouldPublishDriverApproachingAlertWhenNextStopEtaIsWithinThreshold() {
        Viagem viagem = viagem();
        ViagemParada proxima = parada(300L, viagem, 1, passageiro(21L, "Aluno Um"), ViagemParadaStatus.PENDING);
        proxima.setEtaMinutos(2);

        ViagemRealtimeSnapshotEvent event = publishDirectSnapshot(
                viagem,
                LocalDateTime.of(2026, 3, 19, 13, 0),
                List.of(proxima)
        );

        assertThat(event.getAvisosOperacionais())
                .extracting("tipo")
                .contains("DRIVER_APPROACHING");
    }

    @Test
    void shouldNotPublishOperationalAlertsWhenTripIsNotInProgress() {
        Viagem viagem = viagem();
        viagem.setStatus(ViagemStatus.COMPLETED);

        ViagemRealtimeSnapshotEvent event = publishDirectSnapshot(
                viagem,
                LocalDateTime.of(2026, 3, 19, 13, 0)
        );

        assertThat(event.getAvisosOperacionais()).isEmpty();
    }

    @Test
    void shouldPublishDelayedTrackingStateWhenLocationIsOlderThanLiveWindow() {
        ViagemRealtimeSnapshotEvent event = publishDirectSnapshot(LocalDateTime.of(2026, 3, 19, 12, 59));

        assertThat(event.getTrackingState().getLocationFreshness()).isEqualTo(LocationFreshness.DELAYED);
        assertThat(event.getTrackingState().getEtaConfidence()).isEqualTo(EtaConfidence.MEDIUM);
    }

    @Test
    void shouldPublishStaleTrackingStateWhenLocationIsOlderThanDelayedWindow() {
        ViagemRealtimeSnapshotEvent event = publishDirectSnapshot(LocalDateTime.of(2026, 3, 19, 12, 57));

        assertThat(event.getTrackingState().getLocationFreshness()).isEqualTo(LocationFreshness.STALE);
        assertThat(event.getTrackingState().getEtaConfidence()).isEqualTo(EtaConfidence.LOW);
    }

    @Test
    void shouldNotComputeRouteWhenRealtimeTripHasNoOperationalSnapshot() {
        Viagem viagem = viagem();
        viagem.setRotaGeograficaProviderSnapshot(null);
        viagem.setRotaGeograficaPolylineSnapshot(null);
        viagem.setRotaGeograficaNavegacaoUrlSnapshot(null);
        viagem.setRotaGeograficaCalculadaEm(null);

        ViagemRealtimeSnapshotEvent event = publishDirectSnapshot(
                viagem,
                LocalDateTime.of(2026, 3, 19, 13, 0)
        );

        assertThat(event.getTrackingState().getRouteSnapshotStatus()).isEqualTo(RouteSnapshotStatus.UNAVAILABLE);
        assertThat(event.getTrackingState().getEtaConfidence()).isEqualTo(EtaConfidence.MEDIUM);
    }

    private ViagemRealtimeSnapshotEvent publishDirectSnapshot(LocalDateTime locationTime) {
        return publishDirectSnapshot(viagem(), locationTime);
    }

    private ViagemRealtimeSnapshotEvent publishDirectSnapshot(Viagem viagem, LocalDateTime locationTime) {
        return publishDirectSnapshot(viagem, locationTime, paradas(viagem));
    }

    private ViagemRealtimeSnapshotEvent publishDirectSnapshot(Viagem viagem,
                                                             LocalDateTime locationTime,
                                                             List<ViagemParada> paradas) {
        Clock fixedClock = Clock.fixed(Instant.parse("2026-03-19T16:00:00Z"), ZoneId.of("America/Sao_Paulo"));
        ViagemRealtimePublisherService service = new ViagemRealtimePublisherService(
                messagingTemplate,
                viagemProvisioningService,
                viagemParadaRepository,
                localizacaoMotoristaService,
                fixedClock
        );

        service.publishTripSnapshot(viagem, paradas, localizacao(locationTime));

        ArgumentCaptor<Object> captor = ArgumentCaptor.forClass(Object.class);
        verify(messagingTemplate).convertAndSend(org.mockito.ArgumentMatchers.eq("/topic/viagens/1/5"), captor.capture());
        return (ViagemRealtimeSnapshotEvent) captor.getValue();
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
        viagem.setStatus(ViagemStatus.IN_PROGRESS);
        viagem.setRotaVersao(1);
        viagem.setEtaAtualizadoEm(LocalDateTime.of(2026, 3, 19, 12, 50));
        viagem.setRotaGeograficaProviderSnapshot("GOOGLE_DIRECTIONS");
        viagem.setRotaGeograficaPolylineSnapshot("encoded-polyline");
        viagem.setRotaGeograficaNavegacaoUrlSnapshot("https://maps.example/route");
        viagem.setRotaGeograficaCalculadaEm(LocalDateTime.of(2026, 3, 19, 12, 45));
        return viagem;
    }

    private List<ViagemParada> paradas(Viagem viagem) {
        ViagemParada primeira = parada(300L, viagem, 1, passageiro(21L, "Aluno Um"), ViagemParadaStatus.PENDING);
        primeira.setEtaMinutos(12);
        ViagemParada segunda = parada(301L, viagem, 2, passageiro(22L, "Aluno Dois"), ViagemParadaStatus.COMPLETED);
        return List.of(primeira, segunda);
    }

    private LocalizacaoMotorista localizacao(LocalDateTime locationTime) {
        LocalizacaoMotorista localizacao = new LocalizacaoMotorista();
        localizacao.setLatitude(-29.7000);
        localizacao.setLongitude(-51.1100);
        localizacao.setDataHora(locationTime);
        return localizacao;
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

    private Passageiro passageiro(Long id, String nome) {
        Passageiro passageiro = new Passageiro();
        passageiro.setId(id);
        passageiro.setNome(nome);
        return passageiro;
    }
}
