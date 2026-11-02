package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.TrackingStateResponse;
import com.vanrides.vanrides.dto.LocationFreshness;
import com.vanrides.vanrides.dto.ViagemOperationalAlertResponse;
import com.vanrides.vanrides.dto.ViagemRealtimeSnapshotEvent;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.LocalizacaoMotorista;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;

@Service
public class ViagemRealtimePublisherService {

    private final SimpMessagingTemplate messagingTemplate;
    private final ViagemProvisioningService viagemProvisioningService;
    private final ViagemParadaRepository viagemParadaRepository;
    private final LocalizacaoMotoristaService localizacaoMotoristaService;
    private final ViagemTrackingStateResolver viagemTrackingStateResolver;
    private final OperationalDateProvider operationalDateProvider;

    @Autowired
    public ViagemRealtimePublisherService(SimpMessagingTemplate messagingTemplate,
                                          ViagemProvisioningService viagemProvisioningService,
                                          ViagemParadaRepository viagemParadaRepository,
                                          LocalizacaoMotoristaService localizacaoMotoristaService,
                                          ViagemTrackingStateResolver viagemTrackingStateResolver,
                                          OperationalDateProvider operationalDateProvider) {
        this.messagingTemplate = messagingTemplate;
        this.viagemProvisioningService = viagemProvisioningService;
        this.viagemParadaRepository = viagemParadaRepository;
        this.localizacaoMotoristaService = localizacaoMotoristaService;
        this.viagemTrackingStateResolver = viagemTrackingStateResolver;
        this.operationalDateProvider = operationalDateProvider;
    }

    ViagemRealtimePublisherService(SimpMessagingTemplate messagingTemplate,
                                   ViagemProvisioningService viagemProvisioningService,
                                   ViagemParadaRepository viagemParadaRepository,
                                   LocalizacaoMotoristaService localizacaoMotoristaService,
                                   Clock clock) {
        this(
                messagingTemplate,
                viagemProvisioningService,
                viagemParadaRepository,
                localizacaoMotoristaService,
                new ViagemTrackingStateResolver(clock),
                new OperationalDateProvider(clock)
        );
    }

    ViagemRealtimePublisherService(SimpMessagingTemplate messagingTemplate,
                                   ViagemProvisioningService viagemProvisioningService,
                                   ViagemParadaRepository viagemParadaRepository,
                                   LocalizacaoMotoristaService localizacaoMotoristaService,
                                   ViagemTrackingStateResolver viagemTrackingStateResolver,
                                   Clock clock) {
        this(
                messagingTemplate,
                viagemProvisioningService,
                viagemParadaRepository,
                localizacaoMotoristaService,
                viagemTrackingStateResolver,
                new OperationalDateProvider(clock)
        );
    }

    public void publishTripSnapshot(Viagem viagem) {
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        LocalizacaoMotorista localizacao = localizacaoMotoristaService.buscarUltimaPorMotorista(
                viagem.getMotorista().getId(),
                viagem.getCompany().getId()
        );
        publishTripSnapshot(viagem, paradas, localizacao);
    }

    public void publishTripSnapshot(Viagem viagem, List<ViagemParada> paradas) {
        LocalizacaoMotorista localizacao = localizacaoMotoristaService.buscarUltimaPorMotorista(
                viagem.getMotorista().getId(),
                viagem.getCompany().getId()
        );
        publishTripSnapshot(viagem, paradas, localizacao);
    }

    public void publishTripSnapshot(Viagem viagem,
                                    List<ViagemParada> paradas,
                                    LocalizacaoMotorista localizacaoMotorista) {
        List<ViagemParada> safeParadas = paradas == null ? List.of() : paradas;
        ViagemParada proximaParada = safeParadas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.PENDING)
                .findFirst()
                .orElse(null);

        int paradasPendentes = (int) safeParadas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.PENDING)
                .count();
        int paradasConcluidas = (int) safeParadas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.COMPLETED)
                .count();
        TrackingStateResponse trackingState = viagemTrackingStateResolver.resolve(
                viagem,
                safeParadas,
                buildRealtimeRouteSnapshot(viagem, safeParadas),
                localizacaoMotorista,
                null
        );
        List<ViagemOperationalAlertResponse> avisosOperacionais = buildOperationalAlerts(
                viagem,
                safeParadas,
                proximaParada,
                trackingState
        );

        messagingTemplate.convertAndSend(
                "/topic/viagens/" + viagem.getCompany().getId() + "/" + viagem.getId(),
                new ViagemRealtimeSnapshotEvent(
                        "VIAGEM_ATUALIZADA",
                        viagem.getId(),
                        viagem.getMotorista().getId(),
                        viagem.getDataOperacao(),
                        viagem.getStatus(),
                        viagem.getRotaVersao(),
                        safeParadas.size(),
                        paradasPendentes,
                        paradasConcluidas,
                        proximaParada == null ? null : proximaParada.getId(),
                        proximaParada == null ? null : proximaParada.getOrdem(),
                        proximaParada == null ? null : proximaParada.getPassageiro().getId(),
                        proximaParada == null ? null : proximaParada.getPassageiro().getNome(),
                        proximaParada == null ? null : proximaParada.getStatus(),
                        proximaParada == null ? null : proximaParada.getEtaMinutos(),
                        viagem.getEtaAtualizadoEm(),
                        localizacaoMotorista == null ? null : localizacaoMotorista.getLatitude(),
                        localizacaoMotorista == null ? null : localizacaoMotorista.getLongitude(),
                        localizacaoMotorista == null ? null : localizacaoMotorista.getDataHora(),
                        trackingState,
                        avisosOperacionais
                )
        );
    }

    private List<ViagemOperationalAlertResponse> buildOperationalAlerts(Viagem viagem,
                                                                        List<ViagemParada> paradas,
                                                                        ViagemParada proximaParada,
                                                                        TrackingStateResponse trackingState) {
        if (viagem == null || viagem.getStatus() != ViagemStatus.IN_PROGRESS) {
            return List.of();
        }

        List<ViagemOperationalAlertResponse> alerts = new ArrayList<>();
        if (proximaParada != null) {
            alerts.add(new ViagemOperationalAlertResponse(
                    "NEXT_PASSENGER",
                    "INFO",
                    "Você é o próximo",
                    nextPassengerMessage(proximaParada),
                    proximaParada.getPassageiro() == null ? null : proximaParada.getPassageiro().getId(),
                    proximaParada.getId(),
                    proximaParada.getOrdem()
            ));

            if (isDriverApproaching(proximaParada, trackingState)) {
                alerts.add(new ViagemOperationalAlertResponse(
                        "DRIVER_APPROACHING",
                        "URGENT",
                        "Van chegando",
                        "Você é o próximo. Fique pronto para embarcar.",
                        proximaParada.getPassageiro() == null ? null : proximaParada.getPassageiro().getId(),
                        proximaParada.getId(),
                        proximaParada.getOrdem()
                ));
            }
        }

        paradas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.COMPLETED)
                .max(Comparator.comparing(ViagemParada::getOrdem, Comparator.nullsLast(Integer::compareTo)))
                .ifPresent(parada -> alerts.add(new ViagemOperationalAlertResponse(
                        "STOP_COMPLETED",
                        "SUCCESS",
                        "Parada concluída",
                        completedStopMessage(parada),
                        parada.getPassageiro() == null ? null : parada.getPassageiro().getId(),
                        parada.getId(),
                        parada.getOrdem()
                )));

        return alerts;
    }

    private boolean isDriverApproaching(ViagemParada proximaParada, TrackingStateResponse trackingState) {
        Integer eta = proximaParada.getEtaMinutos();
        if (eta == null || eta > 3) {
            return false;
        }
        LocationFreshness freshness = trackingState == null ? LocationFreshness.UNAVAILABLE : trackingState.getLocationFreshness();
        return freshness == LocationFreshness.LIVE || freshness == LocationFreshness.DELAYED;
    }

    private String nextPassengerMessage(ViagemParada parada) {
        String nome = parada.getPassageiro() == null ? "passageiro" : parada.getPassageiro().getNome();
        return nome + " é a próxima parada da viagem.";
    }

    private String completedStopMessage(ViagemParada parada) {
        Integer ordem = parada.getOrdem();
        if (ordem == null) {
            return "Uma parada da viagem foi concluída.";
        }
        return "Parada " + ordem + " concluída.";
    }

    private ViagemRotaGeograficaResponse buildRealtimeRouteSnapshot(Viagem viagem, List<ViagemParada> paradas) {
        if (!hasOperationalRouteSnapshot(viagem)) {
            return null;
        }

        return new ViagemRotaGeograficaResponse(
                viagem.getRotaGeograficaProviderSnapshot(),
                viagem.getRotaGeograficaPolylineSnapshot(),
                null,
                List.of(),
                List.of(),
                paradas.size(),
                null,
                null,
                viagem.getRotaGeograficaCalculadaEm(),
                true
        );
    }

    private boolean hasOperationalRouteSnapshot(Viagem viagem) {
        return viagem.getRotaGeograficaCalculadaEm() != null
                && viagem.getRotaGeograficaProviderSnapshot() != null
                && !viagem.getRotaGeograficaProviderSnapshot().isBlank();
    }

    public void publishTodayTripSnapshotForMotorista(Long motoristaId, Long companyId) {
        Optional<Viagem> viagem = viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(
                companyId,
                motoristaId,
                operationalDateProvider.today()
        );
        viagem.ifPresent(this::publishTripSnapshot);
    }
}
