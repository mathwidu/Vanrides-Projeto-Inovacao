package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminViagemBulkCreateResponse;
import com.vanrides.vanrides.dto.AdminViagemCreateRequest;
import com.vanrides.vanrides.dto.AdminViagemDestinoFinalResponse;
import com.vanrides.vanrides.dto.AdminViagemDetailResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestPreviewResponse;
import com.vanrides.vanrides.dto.AdminViagemParadaResponse;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;
import com.vanrides.vanrides.dto.ViagemPassageiroResumoResponse;
import com.vanrides.vanrides.dto.ViagemRotaGeograficaResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Service
public class CompanyAdminViagemService {

    @Autowired
    private ViagemRepository viagemRepository;
    @Autowired
    private ViagemParadaRepository viagemParadaRepository;
    @Autowired
    private ViagemProvisioningService viagemProvisioningService;
    @Autowired
    private ViagemManifestSyncService viagemManifestSyncService;
    @Autowired
    private AdminViagemManifestStatusResolver manifestStatusResolver;
    @Autowired
    private MotoristaRepository motoristaRepository;
    @Autowired
    private PresencaRepository presencaRepository;
    @Autowired
    private PresencaServicoRepository presencaServicoRepository;
    @Autowired
    private ViagemEtaCalculator viagemEtaCalculator;

    @Autowired
    private ViagemRealtimePublisherService viagemRealtimePublisherService;

    @Autowired
    private ViagemRotaGeograficaService viagemRotaGeograficaService;

    @Autowired
    private ViagemTrackingStateService viagemTrackingStateService;

    @Transactional
    public AdminViagemSummaryResponse criarViagem(Long companyId, AdminViagemCreateRequest request) {
        Viagem viagem = viagemProvisioningService.criarViagemManual(
                companyId,
                request.getRotaBaseId(),
                request.getServicoId(),
                request.getMotoristaId(),
                request.getDataOperacao()
        );
        return toSummary(viagem);
    }

    @Transactional(readOnly = true)
    public List<AdminViagemSummaryResponse> listarViagens(Long companyId, LocalDate dataOperacao) {
        List<Viagem> viagens = dataOperacao == null
                ? viagemRepository.findAllByCompanyIdOrderByDataOperacaoDescIdDesc(companyId)
                : viagemRepository.findAllByCompanyIdAndDataOperacaoOrderByIdDesc(companyId, dataOperacao);

        return viagens
                .stream()
                .map(this::toSummary)
                .toList();
    }

    @Transactional
    public AdminViagemBulkCreateResponse gerarViagensAutomaticas(Long companyId, LocalDate dataOperacao) {
        ViagemProvisioningService.ViagensProvisionadasResult result =
                viagemProvisioningService.gerarViagensAutomaticasDaEmpresa(companyId, dataOperacao);
        List<AdminViagemSummaryResponse> viagens = result.viagens()
                .stream()
                .map(this::toSummary)
                .toList();
        return AdminViagemBulkCreateResponseMapper.toResponse(result, viagens);
    }

    @Transactional(readOnly = true)
    public AdminViagemDetailResponse buscarDetalhe(Long companyId, Long viagemId) {
        Viagem viagem = findViagem(companyId, viagemId);
        return toDetail(viagem, null);
    }

    @Transactional(readOnly = true)
    public AdminViagemManifestPreviewResponse previewSincronizacaoManifesto(Long companyId, Long viagemId) {
        return AdminViagemManifestPreviewMapper.toResponse(
                viagemManifestSyncService.previewSincronizacaoManifesto(companyId, viagemId)
        );
    }

    @Transactional
    public AdminViagemDetailResponse sincronizarManifesto(Long companyId, Long viagemId) {
        Viagem viagem = viagemManifestSyncService.sincronizarManifesto(companyId, viagemId);
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        viagemRealtimePublisherService.publishTripSnapshot(viagem, paradas);
        return toDetail(viagem, paradas);
    }

    @Transactional
    public AdminViagemDetailResponse trocarMotorista(Long companyId, Long viagemId, Long motoristaId) {
        Viagem viagem = findViagem(companyId, viagemId);
        if (viagem.getStatus() != ViagemStatus.PLANNED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A troca de motorista só é permitida antes da viagem começar."
            );
        }

        Motorista motorista = motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        viagem.setMotorista(motorista);
        viagem.setRotaVersao((viagem.getRotaVersao() == null ? 1 : viagem.getRotaVersao()) + 1);

        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        viagemEtaCalculator.recalculate(viagem, paradas);
        viagemParadaRepository.saveAll(paradas);
        Viagem saved = viagemRepository.save(viagem);
        viagemRealtimePublisherService.publishTripSnapshot(saved, paradas);

        return toDetail(saved, paradas);
    }

    @Transactional
    public AdminViagemDetailResponse cancelarViagem(Long companyId, Long viagemId) {
        Viagem viagem = findViagem(companyId, viagemId);
        if (viagem.getStatus() != ViagemStatus.PLANNED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A empresa só pode cancelar uma viagem planejada antes da corrida começar."
            );
        }

        viagem.setStatus(ViagemStatus.CANCELED);
        List<ViagemParada> paradas = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        Viagem saved = viagemRepository.save(viagem);
        viagemRealtimePublisherService.publishTripSnapshot(saved, paradas);
        return toDetail(saved, paradas);
    }

    private Viagem findViagem(Long companyId, Long viagemId) {
        return viagemRepository.findByIdAndCompanyId(viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viagem não encontrada na empresa atual."));
    }

    private AdminViagemSummaryResponse toSummary(Viagem viagem) {
        int quantidadeParadas = (int) viagemParadaRepository.countByViagemId(viagem.getId());
        return AdminViagemSummaryMapper.toSummary(
                viagem,
                quantidadeParadas,
                manifestStatusResolver.resolve(viagem, quantidadeParadas)
        );
    }

    private AdminViagemDetailResponse toDetail(Viagem viagem, List<ViagemParada> loadedParadas) {
        List<ViagemParada> paradas = loadedParadas == null
                ? viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId())
                : loadedParadas;
        Map<Long, ManifestPresenceDecision> presencas = loadPresencaStatuses(viagem, paradas);

        ViagemParada proximaParada = paradas.stream()
                .filter(parada -> parada.getStatus() == ViagemParadaStatus.PENDING)
                .findFirst()
                .orElse(null);

        List<AdminViagemParadaResponse> paradasResponse = paradas.stream()
                .map(parada -> toParadaResponse(
                        viagem,
                        parada,
                        presencas.getOrDefault(
                                parada.getPassageiro().getId(),
                                new ManifestPresenceDecision(null, PresencaServicoStatus.UNKNOWN)
                        ),
                        proximaParada
                ))
                .toList();

        AdminViagemParadaResponse proximaParadaResponse = proximaParada == null
                ? null
                : paradasResponse.stream()
                        .filter(parada -> parada.getId().equals(proximaParada.getId()))
                        .findFirst()
                        .orElse(null);
        ViagemRotaGeograficaResponse rotaGeografica = viagemRotaGeograficaService.buildForTrip(viagem, paradas, null);

        return new AdminViagemDetailResponse(
                viagem.getId(),
                viagem.getRotaBaseIdOrNull(),
                viagem.getServicoRecorrenteIdOrNull(),
                viagem.getRotaBase() == null ? null : viagem.getRotaBase().getNome(),
                viagem.getNomeOperacional(),
                viagem.getTipoOperacaoSnapshot(),
                viagem.getDataOperacao(),
                viagem.getHorarioPartida(),
                viagem.getStatus(),
                viagem.getRotaVersao(),
                proximaParada == null ? 0 : safeEta(proximaParada),
                viagem.getEtaAtualizadoEm(),
                toMotoristaSummary(viagem),
                paradas.size(),
                toDestinoFinal(viagem),
                proximaParadaResponse,
                paradasResponse,
                rotaGeografica,
                viagem.getServicoRecorrente() == null ? "ROTA_BASE_LEGADA" : "SERVICO_RECORRENTE",
                hasOperationalSnapshot(viagem),
                viagemTrackingStateService.resolve(viagem, paradas, rotaGeografica, null)
        );
    }

    private Map<Long, ManifestPresenceDecision> loadPresencaStatuses(Viagem viagem, List<ViagemParada> paradas) {
        List<Long> passageiroIds = paradas.stream()
                .map(parada -> parada.getPassageiro().getId())
                .toList();
        if (passageiroIds.isEmpty()) {
            return Map.of();
        }

        Map<Long, ManifestPresenceDecision> statuses = new HashMap<>();
        for (Long passageiroId : passageiroIds) {
            statuses.put(passageiroId, new ManifestPresenceDecision(null, PresencaServicoStatus.UNKNOWN));
        }

        if (viagem.getServicoRecorrente() != null) {
            statuses.putAll(presencaServicoRepository.findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                            passageiroIds,
                            viagem.getCompany().getId(),
                            viagem.getServicoRecorrente().getId(),
                            viagem.getDataOperacao()
                    )
                    .stream()
                    .collect(Collectors.toMap(
                            item -> item.getPassageiro().getId(),
                            item -> new ManifestPresenceDecision(item.getId(), item.getStatus()),
                            (left, right) -> right
                    )));
            return statuses;
        }

        statuses.putAll(presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(
                        passageiroIds,
                        viagem.getCompany().getId(),
                        viagem.getDataOperacao()
                )
                .stream()
                .collect(Collectors.toMap(
                        item -> item.getPassageiro().getId(),
                        item -> new ManifestPresenceDecision(null, toLegacyPresencaStatus(item)),
                        (left, right) -> right
                )));
        return statuses;
    }

    private PresencaServicoStatus toLegacyPresencaStatus(Presenca presenca) {
        return presenca.isConfirmado() ? PresencaServicoStatus.CONFIRMED : PresencaServicoStatus.ABSENT;
    }

    private AdminViagemParadaResponse toParadaResponse(Viagem viagem,
                                                       ViagemParada parada,
                                                       ManifestPresenceDecision presenceDecision,
                                                       ViagemParada proximaParada) {
        PresencaServicoStatus presencaStatus = presenceDecision.status();
        return new AdminViagemParadaResponse(
                parada.getId(),
                parada.getOrdem(),
                parada.getStatus(),
                parada.getEtaMinutos(),
                isConfirmadoPresenca(viagem, presencaStatus),
                presenceDecision.presencaId(),
                presencaStatus,
                proximaParada != null && proximaParada.getId().equals(parada.getId()),
                new ViagemPassageiroResumoResponse(
                        parada.getPassageiro().getId(),
                        parada.getPassageiro().getNome(),
                        parada.getPassageiro().isMenorDeIdade()
                )
        );
    }

    private record ManifestPresenceDecision(Long presencaId, PresencaServicoStatus status) {
    }

    private boolean isConfirmadoPresenca(Viagem viagem, PresencaServicoStatus status) {
        if (status == PresencaServicoStatus.CONFIRMED) {
            return true;
        }
        return status == PresencaServicoStatus.UNKNOWN
                && viagem.getServicoRecorrente() != null
                && viagem.getServicoRecorrente().getPoliticaPresenca() == PoliticaPresencaServico.INCLUIR_POR_PADRAO;
    }

    private AdminViagemDestinoFinalResponse toDestinoFinal(Viagem viagem) {
        return new AdminViagemDestinoFinalResponse(
                viagem.getDestinoFinalNomeSnapshot(),
                viagem.getDestinoFinalEnderecoSnapshot(),
                viagem.getDestinoFinalLatitudeSnapshot(),
                viagem.getDestinoFinalLongitudeSnapshot()
        );
    }

    private boolean hasOperationalSnapshot(Viagem viagem) {
        return viagem.getServicoNomeSnapshot() != null
                || viagem.getTipoOperacaoSnapshot() != null
                || viagem.getDestinoFinalNomeSnapshot() != null
                || viagem.getDestinoFinalEnderecoSnapshot() != null
                || viagem.getDestinoFinalLatitudeSnapshot() != null
                || viagem.getDestinoFinalLongitudeSnapshot() != null;
    }

    private Integer safeEta(ViagemParada parada) {
        return parada.getEtaMinutos() == null ? 0 : parada.getEtaMinutos();
    }

    private AdminMotoristaSummaryResponse toMotoristaSummary(Viagem viagem) {
        return new AdminMotoristaSummaryResponse(
                viagem.getMotorista().getId(),
                viagem.getMotorista().getNome(),
                viagem.getMotorista().getEmail(),
                viagem.getMotorista().getTelefone(),
                viagem.getMotorista().isEmailVerified(),
                viagem.getMotorista().getDataNascimento(),
                viagem.getMotorista().getFotoPerfilUrl()
        );
    }
}
