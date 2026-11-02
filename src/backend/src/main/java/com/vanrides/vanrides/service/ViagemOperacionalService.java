package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.ViagemRotaAtualizadaEvent;
import com.vanrides.vanrides.dto.ViagemMotoristaResponse;
import com.vanrides.vanrides.dto.ViagemParadaResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroAtualResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

@Service
public class ViagemOperacionalService {

    @Autowired
    private ViagemRepository viagemRepository;
    @Autowired
    private ViagemParadaRepository viagemParadaRepository;
    @Autowired
    private PresencaRepository presencaRepository;
    @Autowired
    private PresencaServicoRepository presencaServicoRepository;
    @Autowired
    private ViagemEtaCalculator viagemEtaCalculator;
    @Autowired
    private SimpMessagingTemplate messagingTemplate;
    @Autowired
    private ViagemProvisioningService viagemProvisioningService;
    @Autowired
    private ViagemRotaGeograficaService viagemRotaGeograficaService;
    @Autowired
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Autowired
    private ViagemTrackingStateService viagemTrackingStateService;

    @Transactional
    public Optional<ViagemMotoristaResponse> buscarViagemDoMotorista(Long motoristaId,
                                                                     LocalDate dataOperacao,
                                                                     Long companyId) {
        return viagemProvisioningService.buscarOuCriarViagemAutomaticaDoMotorista(companyId, motoristaId, dataOperacao)
                .map(viagem -> toMotoristaResponse(viagem, companyId));
    }

    @Transactional
    public List<ViagemMotoristaResponse> buscarAgendaDoMotorista(Long motoristaId,
                                                                 LocalDate dataOperacao,
                                                                 Long companyId) {
        return viagemProvisioningService.buscarOuCriarViagensAutomaticasDoMotorista(companyId, motoristaId, dataOperacao)
                .stream()
                .map(viagem -> toMotoristaResponse(viagem, companyId))
                .toList();
    }

    @Transactional
    public Optional<ViagemPassageiroAtualResponse> buscarViagemAtualDoPassageiro(Long passageiroId,
                                                                                  LocalDate dataOperacao,
                                                                                  Long companyId) {
        return viagemProvisioningService.buscarOuCriarViagemAutomaticaDoPassageiro(companyId, passageiroId, dataOperacao)
                .map(viagem -> toPassageiroResponse(viagem, passageiroId, companyId));
    }

    @Transactional
    public List<ViagemPassageiroAtualResponse> buscarAgendaDoPassageiro(Long passageiroId,
                                                                        LocalDate dataOperacao,
                                                                        Long companyId) {
        return viagemProvisioningService.buscarOuCriarViagensAutomaticasDoPassageiro(companyId, passageiroId, dataOperacao)
                .stream()
                .map(viagem -> toPassageiroResponse(viagem, passageiroId, companyId))
                .toList();
    }

    @Transactional
    public ViagemMotoristaResponse atualizarStatusDaViagem(Long viagemId,
                                                           Long companyId,
                                                           ViagemStatus status) {
        Viagem viagem = viagemRepository.findByIdAndCompanyId(viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viagem não encontrada na empresa atual."));
        ViagemStatus currentStatus = viagem.getStatus();
        ViagemLifecyclePolicy.validateTripStatusTransition(currentStatus, status);
        viagem.setStatus(status);
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagemId);
        viagemEtaCalculator.recalculate(viagem, paradas);
        if (currentStatus == ViagemStatus.PLANNED && status == ViagemStatus.IN_PROGRESS) {
            viagemRotaGeograficaService.calcularEAplicarSnapshotOperacional(viagem, paradas);
        }
        viagemParadaRepository.saveAll(paradas);
        Viagem saved = viagemRepository.save(viagem);
        viagemRealtimePublisherService.publishTripSnapshot(saved, paradas);
        return toMotoristaResponse(saved, companyId);
    }

    @Transactional
    public ViagemMotoristaResponse iniciarCorrida(Long viagemId, Long companyId) {
        return atualizarStatusDaViagem(viagemId, companyId, ViagemStatus.IN_PROGRESS);
    }

    @Transactional
    public ViagemMotoristaResponse encerrarCorrida(Long viagemId, Long companyId) {
        return atualizarStatusDaViagem(viagemId, companyId, ViagemStatus.COMPLETED);
    }

    @Transactional
    public ViagemMotoristaResponse atualizarStatusDaParada(Long viagemId,
                                                           Long paradaId,
                                                           Long companyId,
                                                           ViagemParadaStatus status) {
        Viagem viagem = viagemRepository.findByIdAndCompanyId(viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viagem não encontrada na empresa atual."));

        ViagemParada parada = viagemParadaRepository.findByIdAndViagemIdAndCompanyId(paradaId, viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Parada não encontrada na viagem atual."));

        ViagemLifecyclePolicy.validateStopStatusTransition(viagem.getStatus(), parada.getStatus(), status);
        parada.setStatus(status);
        viagemParadaRepository.save(parada);

        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagemId);
        ViagemStatus previousStatus = viagem.getStatus();
        ViagemStatus nextStatus = ViagemLifecyclePolicy.statusAfterStopUpdate(
                previousStatus,
                paradas.stream().map(ViagemParada::getStatus).toList()
        );
        boolean startedDuringStopUpdate = previousStatus == ViagemStatus.PLANNED
                && nextStatus == ViagemStatus.IN_PROGRESS;
        viagem.setStatus(nextStatus);

        viagemEtaCalculator.recalculate(viagem, paradas);
        if (startedDuringStopUpdate) {
            viagemRotaGeograficaService.calcularEAplicarSnapshotOperacional(viagem, paradas);
        }
        viagemParadaRepository.saveAll(paradas);
        Viagem saved = viagemRepository.save(viagem);
        viagemRealtimePublisherService.publishTripSnapshot(saved, paradas);
        return toMotoristaResponse(saved, companyId);
    }

    @Transactional
    public ViagemMotoristaResponse concluirParada(Long viagemId, Long paradaId, Long companyId) {
        return atualizarStatusDaParada(viagemId, paradaId, companyId, ViagemParadaStatus.COMPLETED);
    }

    @Transactional
    public ViagemMotoristaResponse pularParada(Long viagemId, Long paradaId, Long companyId) {
        return atualizarStatusDaParada(viagemId, paradaId, companyId, ViagemParadaStatus.SKIPPED);
    }

    @Transactional
    public ViagemMotoristaResponse marcarNoShowParada(Long viagemId, Long paradaId, Long companyId) {
        return atualizarStatusDaParada(viagemId, paradaId, companyId, ViagemParadaStatus.NO_SHOW);
    }

    @Transactional
    public ViagemMotoristaResponse atualizarRotaDaViagem(Long viagemId,
                                                         Long companyId,
                                                         List<Long> paradaIdsOrdenados) {
        Viagem viagem = viagemRepository.findByIdAndCompanyId(viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viagem não encontrada na empresa atual."));
        if (viagem.getStatus() != ViagemStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rota só pode ser editada antes de iniciar a corrida.");
        }

        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagemId);
        validateOrderedStopIds(paradas, paradaIdsOrdenados);

        Map<Long, ViagemParada> stopById = paradas.stream()
                .collect(Collectors.toMap(ViagemParada::getId, stop -> stop));

        List<ViagemParada> reordered = new java.util.ArrayList<>();
        int ordem = 1;
        for (Long paradaId : paradaIdsOrdenados) {
            ViagemParada stop = stopById.get(paradaId);
            stop.setOrdem(ordem++);
            reordered.add(stop);
        }

        viagem.setRotaVersao((viagem.getRotaVersao() == null ? 1 : viagem.getRotaVersao()) + 1);
        viagemEtaCalculator.recalculate(viagem, reordered);
        if (hasOperationalRouteSnapshot(viagem)) {
            viagemRotaGeograficaService.calcularEAplicarSnapshotOperacional(viagem, reordered);
        }
        viagemParadaRepository.saveAll(reordered);
        Viagem saved = viagemRepository.save(viagem);
        publishRouteUpdated(saved);
        viagemRealtimePublisherService.publishTripSnapshot(saved, reordered);
        return toMotoristaResponse(saved, companyId);
    }

    @Transactional(readOnly = true)
    public boolean viagemPertenceAoMotorista(Long viagemId, Long motoristaId, Long companyId) {
        return viagemRepository.existsByIdAndMotoristaIdAndCompanyId(viagemId, motoristaId, companyId);
    }

    private ViagemMotoristaResponse toMotoristaResponse(Viagem viagem, Long companyId) {
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        Map<Long, Boolean> confirmacoes = loadConfirmacoes(viagem, paradas, companyId);
        ViagemParada proximaParada = paradas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.PENDING)
                .findFirst()
                .orElse(null);

        List<ViagemParadaResponse> paradasResponse = paradas.stream()
                .map(parada -> toParadaResponse(parada, confirmacoes.getOrDefault(parada.getPassageiro().getId(), false), proximaParada))
                .toList();
        ViagemRotaGeograficaResponse rotaGeografica = viagemRotaGeograficaService.buildForTrip(viagem, paradas, null);

        return new ViagemMotoristaResponse(
                viagem.getId(),
                viagem.getRotaBaseIdOrNull(),
                viagem.getServicoRecorrenteIdOrNull(),
                rotaNomeParaContrato(viagem),
                servicoNomeParaContrato(viagem),
                viagem.getTipoOperacaoSnapshot(),
                viagem.getDataOperacao(),
                viagem.getHorarioPartida(),
                viagem.getStatus(),
                viagem.getRotaVersao(),
                proximaParada == null ? 0 : safeEta(proximaParada),
                viagem.getEtaAtualizadoEm(),
                toMotoristaSummary(viagem),
                proximaParada == null ? null : toParadaResponse(
                        proximaParada,
                        confirmacoes.getOrDefault(proximaParada.getPassageiro().getId(), false),
                        proximaParada
                ),
                paradasResponse,
                rotaGeografica,
                viagemTrackingStateService.resolve(viagem, paradas, rotaGeografica, null)
        );
    }

    private ViagemPassageiroAtualResponse toPassageiroResponse(Viagem viagem, Long passageiroId, Long companyId) {
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        Map<Long, Boolean> confirmacoes = loadConfirmacoes(viagem, paradas, companyId);
        ViagemParada proximaParada = paradas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.PENDING)
                .findFirst()
                .orElse(null);

        ViagemParada paradaDoPassageiro = paradas.stream()
                .filter(parada -> parada.getPassageiro().getId().equals(passageiroId))
                .findFirst()
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na viagem atual."));
        ViagemRotaGeograficaResponse rotaGeografica = viagemRotaGeograficaService.buildForTrip(viagem, paradas, passageiroId);

        return new ViagemPassageiroAtualResponse(
                viagem.getId(),
                viagem.getServicoRecorrenteIdOrNull(),
                rotaNomeParaContrato(viagem),
                servicoNomeParaContrato(viagem),
                viagem.getTipoOperacaoSnapshot(),
                viagem.getDataOperacao(),
                viagem.getHorarioPartida(),
                viagem.getStatus(),
                viagem.getRotaVersao(),
                safeEta(paradaDoPassageiro),
                viagem.getEtaAtualizadoEm(),
                toMotoristaSummary(viagem),
                toParadaResponse(
                        paradaDoPassageiro,
                        confirmacoes.getOrDefault(passageiroId, false),
                        proximaParada
                ),
                rotaGeografica,
                viagemTrackingStateService.resolve(viagem, paradas, rotaGeografica, passageiroId)
        );
    }

    private String rotaNomeParaContrato(Viagem viagem) {
        return viagem.getRotaBase() == null ? null : viagem.getRotaBase().getNome();
    }

    private String servicoNomeParaContrato(Viagem viagem) {
        return viagem.getServicoRecorrente() == null ? null : viagem.getNomeOperacional();
    }

    private Integer safeEta(ViagemParada parada) {
        return parada.getEtaMinutos() != null ? parada.getEtaMinutos() : 0;
    }

    private Map<Long, Boolean> loadConfirmacoes(Viagem viagem,
                                                List<ViagemParada> paradas,
                                                Long companyId) {
        List<Long> passageiroIds = paradas.stream()
                .map(parada -> parada.getPassageiro().getId())
                .toList();

        Map<Long, Boolean> confirmacoes = new HashMap<>();
        if (viagem.getServicoRecorrente() != null) {
            for (ViagemParada parada : paradas) {
                confirmacoes.put(parada.getPassageiro().getId(), true);
            }
            for (PresencaServico presenca : presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                    passageiroIds,
                    companyId,
                    viagem.getServicoRecorrente().getId(),
                    viagem.getDataOperacao()
            )) {
                boolean confirmado = presenca.getStatus() == PresencaServicoStatus.CONFIRMED
                        || presenca.getStatus() == PresencaServicoStatus.UNKNOWN;
                confirmacoes.put(presenca.getPassageiro().getId(), confirmado);
            }
            return confirmacoes;
        }

        for (Presenca presenca : presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(passageiroIds, companyId, viagem.getDataOperacao())) {
            confirmacoes.put(presenca.getPassageiro().getId(), presenca.isConfirmado());
        }
        return confirmacoes;
    }

    private ViagemParadaResponse toParadaResponse(ViagemParada parada,
                                                  boolean confirmadoPresenca,
                                                  ViagemParada proximaParada) {
        return new ViagemParadaResponse(
                parada.getId(),
                parada.getOrdem(),
                parada.getStatus(),
                parada.getEtaMinutos(),
                confirmadoPresenca,
                proximaParada != null && proximaParada.getId().equals(parada.getId()),
                new ViagemPassageiroResumoResponse(
                        parada.getPassageiro().getId(),
                        parada.getPassageiro().getNome(),
                        parada.getPassageiro().isMenorDeIdade()
                )
        );
    }

    private void validateOrderedStopIds(List<ViagemParada> paradas, List<Long> paradaIdsOrdenados) {
        List<Long> existingIds = paradas.stream().map(ViagemParada::getId).toList();
        if (paradaIdsOrdenados.size() != existingIds.size()
                || paradaIdsOrdenados.stream().distinct().count() != paradaIdsOrdenados.size()
                || !existingIds.containsAll(paradaIdsOrdenados)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A rota precisa informar exatamente as paradas atuais da viagem, sem duplicidade."
            );
        }
    }

    private void publishRouteUpdated(Viagem viagem) {
        messagingTemplate.convertAndSend(
                "/topic/viagens/" + viagem.getCompany().getId(),
                new ViagemRotaAtualizadaEvent(
                        "ROTA_ATUALIZADA",
                        viagem.getId(),
                        viagem.getMotorista().getId(),
                        viagem.getDataOperacao(),
                        viagem.getRotaVersao(),
                        viagem.getEtaAtualizadoEm()
                )
        );
    }

    private boolean hasOperationalRouteSnapshot(Viagem viagem) {
        return viagem.getRotaGeograficaCalculadaEm() != null
                && viagem.getRotaGeograficaProviderSnapshot() != null
                && !viagem.getRotaGeograficaProviderSnapshot().isBlank();
    }

    private AdminMotoristaSummaryResponse toMotoristaSummary(Viagem viagem) {
        return new AdminMotoristaSummaryResponse(
                viagem.getMotorista().getId(),
                viagem.getMotorista().getNome(),
                viagem.getMotorista().getEmail(),
                viagem.getMotorista().getTelefone(), viagem.getMotorista().isEmailVerified(),
                viagem.getMotorista().getDataNascimento(), viagem.getMotorista().getFotoPerfilUrl());
    }
}
