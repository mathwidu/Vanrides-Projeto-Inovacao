package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ViagemManifestSyncService {

    @Autowired
    private ViagemRepository viagemRepository;

    @Autowired
    private ViagemParadaRepository viagemParadaRepository;

    @Autowired
    private ViagemEtaCalculator viagemEtaCalculator;

    @Autowired
    private ServicoTripPolicy servicoTripPolicy;

    @Autowired
    private ServicoOperacionalStopsResolver servicoOperacionalStopsResolver;

    @Autowired
    private ViagemSnapshotApplier viagemSnapshotApplier;

    @Transactional(readOnly = true)
    public ManifestSyncPreview previewSincronizacaoManifesto(Long companyId, Long viagemId) {
        return buildManifestSyncPlan(companyId, viagemId).toPreview();
    }

    @Transactional
    public Viagem sincronizarManifesto(Long companyId, Long viagemId) {
        ManifestSyncPlan plan = buildManifestSyncPlan(companyId, viagemId);
        Viagem viagem = plan.viagem();
        ServicoRecorrente servico = plan.servico();

        if (!plan.paradasRemovidas().isEmpty()) {
            viagemParadaRepository.deleteAll(plan.paradasRemovidas());
        }

        List<ViagemParada> paradasSincronizadas = new ArrayList<>();
        int ordem = 1;
        for (ServicoPassageiro servicoPassageiro : plan.paradasSelecionadas()) {
            Passageiro passageiro = servicoPassageiro.getPassageiro();
            ViagemParada parada = plan.paradasAtuaisPorPassageiro().get(passageiro.getId());
            if (parada == null) {
                parada = new ViagemParada();
                parada.setViagem(viagem);
                parada.setPassageiro(passageiro);
            }
            parada.setOrdem(ordem++);
            paradasSincronizadas.add(parada);
        }

        viagem.setHorarioPartida(servico.getHorarioPartida());
        viagemSnapshotApplier.apply(viagem, servico.getRotaBaseLegada(), servico);
        viagem.setRotaVersao((viagem.getRotaVersao() == null ? 1 : viagem.getRotaVersao()) + 1);
        viagemEtaCalculator.recalculate(viagem, paradasSincronizadas);
        viagemParadaRepository.saveAll(paradasSincronizadas);
        return viagemRepository.save(viagem);
    }

    private ManifestSyncPlan buildManifestSyncPlan(Long companyId, Long viagemId) {
        Viagem viagem = viagemRepository.findByIdAndCompanyId(viagemId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Viagem não encontrada na empresa atual."));
        if (viagem.getStatus() != ViagemStatus.PLANNED) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "O manifesto só pode ser atualizado antes da viagem começar.");
        }

        ServicoRecorrente servico = viagem.getServicoRecorrente();
        if (servico == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Somente viagens geradas por serviço recorrente podem atualizar o manifesto.");
        }

        servicoTripPolicy.validateDisponivelParaData(servico, viagem.getDataOperacao());
        List<ServicoPassageiro> paradasSelecionadas = servicoOperacionalStopsResolver.select(
                companyId,
                servico,
                viagem.getDataOperacao()
        );
        if (paradasSelecionadas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O serviço não possui passageiros operacionais para atualizar o manifesto.");
        }
        servicoTripPolicy.validatePlannedCapacity(servico, paradasSelecionadas.size());

        List<ViagemParada> paradasAtuais = viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId());
        Map<Long, ViagemParada> paradasAtuaisPorPassageiro = paradasAtuais.stream()
                .filter(parada -> parada.getPassageiro() != null && parada.getPassageiro().getId() != null)
                .collect(Collectors.toMap(parada -> parada.getPassageiro().getId(), parada -> parada, (left, right) -> left));
        Set<Long> passageirosSelecionados = paradasSelecionadas.stream()
                .map(parada -> parada.getPassageiro().getId())
                .collect(Collectors.toSet());
        return new ManifestSyncPlan(
                viagem,
                servico,
                paradasSelecionadas,
                paradasAtuais,
                paradasAtuaisPorPassageiro,
                paradasAtuais.stream()
                        .filter(parada -> parada.getPassageiro() == null || !passageirosSelecionados.contains(parada.getPassageiro().getId()))
                        .toList(),
                paradasSelecionadas.stream()
                        .filter(parada -> !paradasAtuaisPorPassageiro.containsKey(parada.getPassageiro().getId()))
                        .toList(),
                paradasSelecionadas.stream()
                        .filter(parada -> paradasAtuaisPorPassageiro.containsKey(parada.getPassageiro().getId()))
                        .toList()
        );
    }

    public record ManifestSyncPreview(
            Long viagemId,
            Long servicoId,
            String servicoNome,
            LocalDate dataOperacao,
            int paradasAtuais,
            int paradasAposSincronizacao,
            int adicionados,
            int removidos,
            int mantidos,
            List<ManifestSyncPassenger> passageirosAdicionados,
            List<ManifestSyncPassenger> passageirosRemovidos,
            List<ManifestSyncPassenger> passageirosMantidos
    ) {
        public int mudancas() {
            return adicionados + removidos;
        }

        public boolean semMudancas() {
            return mudancas() == 0;
        }
    }

    public record ManifestSyncPassenger(
            Long passageiroId,
            String nome,
            boolean menorDeIdade,
            Integer ordemAtual,
            Integer ordemNova
    ) {
    }

    private record ManifestSyncPlan(
            Viagem viagem,
            ServicoRecorrente servico,
            List<ServicoPassageiro> paradasSelecionadas,
            List<ViagemParada> paradasAtuais,
            Map<Long, ViagemParada> paradasAtuaisPorPassageiro,
            List<ViagemParada> paradasRemovidas,
            List<ServicoPassageiro> paradasAdicionadas,
            List<ServicoPassageiro> paradasMantidas
    ) {
        ManifestSyncPreview toPreview() {
            return new ManifestSyncPreview(
                    viagem.getId(),
                    servico.getId(),
                    servico.getNome(),
                    viagem.getDataOperacao(),
                    paradasAtuais.size(),
                    paradasSelecionadas.size(),
                    paradasAdicionadas.size(),
                    paradasRemovidas.size(),
                    paradasMantidas.size(),
                    mapSelectedPassengers(paradasAdicionadas),
                    mapRemovedPassengers(paradasRemovidas),
                    mapSelectedPassengers(paradasMantidas)
            );
        }

        private List<ManifestSyncPassenger> mapSelectedPassengers(List<ServicoPassageiro> paradas) {
            return paradas.stream()
                    .map(parada -> {
                        Passageiro passageiro = parada.getPassageiro();
                        ViagemParada paradaAtual = paradasAtuaisPorPassageiro.get(passageiro.getId());
                        return new ManifestSyncPassenger(
                                passageiro.getId(),
                                passageiro.getNome(),
                                passageiro.isMenorDeIdade(),
                                paradaAtual == null ? null : paradaAtual.getOrdem(),
                                findNewOrder(passageiro.getId())
                        );
                    })
                    .toList();
        }

        private List<ManifestSyncPassenger> mapRemovedPassengers(List<ViagemParada> paradas) {
            return paradas.stream()
                    .filter(parada -> parada.getPassageiro() != null)
                    .map(parada -> new ManifestSyncPassenger(
                            parada.getPassageiro().getId(),
                            parada.getPassageiro().getNome(),
                            parada.getPassageiro().isMenorDeIdade(),
                            parada.getOrdem(),
                            null
                    ))
                    .toList();
        }

        private Integer findNewOrder(Long passageiroId) {
            for (int index = 0; index < paradasSelecionadas.size(); index++) {
                if (paradasSelecionadas.get(index).getPassageiro().getId().equals(passageiroId)) {
                    return index + 1;
                }
            }
            return null;
        }
    }
}
