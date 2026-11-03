package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.Presenca;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.RotaBaseParada;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.RotaBaseParadaRepository;
import com.vanrides.vanrides.repository.RotaBaseRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import com.vanrides.vanrides.repository.ViagemParadaRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

@Service
public class ViagemProvisioningService {

    @Autowired
    private ViagemRepository viagemRepository;

    @Autowired
    private ViagemParadaRepository viagemParadaRepository;

    @Autowired
    private RotaBaseRepository rotaBaseRepository;

    @Autowired
    private RotaBaseParadaRepository rotaBaseParadaRepository;

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Autowired
    private PresencaServicoRepository presencaServicoRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private PresencaRepository presencaRepository;

    @Autowired
    private ViagemEtaCalculator viagemEtaCalculator;

    @Autowired
    private ServicoTripPolicy servicoTripPolicy;

    @Autowired
    private ViagemSnapshotApplier viagemSnapshotApplier;

    @Autowired
    private CompanyTripManifestMaintenanceService companyTripManifestMaintenanceService;

    @Transactional
    public Viagem criarViagemManual(Long companyId, Long rotaBaseId, LocalDate dataOperacao) {
        return criarViagemManual(companyId, rotaBaseId, null, null, dataOperacao);
    }

    @Transactional
    public Viagem criarViagemManual(Long companyId,
                                    Long rotaBaseId,
                                    Long servicoId,
                                    Long motoristaId,
                                    LocalDate dataOperacao) {
        if (servicoId != null) {
            return criarViagemManualPorServico(companyId, servicoId, motoristaId, dataOperacao);
        }
        return criarViagemManualPorRotaLegada(companyId, rotaBaseId, dataOperacao);
    }

    private Viagem criarViagemManualPorRotaLegada(Long companyId, Long rotaBaseId, LocalDate dataOperacao) {
        RotaBase rotaBase = rotaBaseRepository.findByIdAndCompanyId(rotaBaseId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota base não encontrada na empresa atual."));

        if (viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(rotaBase.getId(), dataOperacao, companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe viagem criada para esta rota e data.");
        }

        Optional<ServicoRecorrente> servicoMigrado = servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(rotaBase.getId(), companyId);
        if (servicoMigrado.isPresent()
                && viagemRepository.existsByServicoRecorrenteIdAndDataOperacaoAndCompanyId(servicoMigrado.get().getId(), dataOperacao, companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe viagem criada para este serviço e data.");
        }

        List<RotaBaseParada> paradasBase = rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(rotaBase.getId());
        if (paradasBase.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "A rota base precisa ter paradas antes de gerar a viagem.");
        }

        return persistTripSnapshot(companyId, rotaBase, servicoMigrado.orElse(null), dataOperacao, paradasBase);
    }

    private Viagem criarViagemManualPorServico(Long companyId,
                                               Long servicoId,
                                               Long motoristaId,
                                               LocalDate dataOperacao) {
        ServicoRecorrente servico = servicoRecorrenteRepository.findByIdAndCompanyId(servicoId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual."));
        servicoTripPolicy.validateDisponivelParaData(servico, dataOperacao);

        if (findExistingForServico(companyId, servico, dataOperacao).isPresent()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Já existe viagem criada para este serviço e data.");
        }

        Motorista motorista = resolveMotoristaOperacional(companyId, servico, motoristaId);
        List<ServicoPassageiro> paradasSelecionadas = selectOperationalServiceStops(companyId, servico, dataOperacao);
        if (paradasSelecionadas.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "O serviço precisa ter passageiros operacionais para gerar a viagem.");
        }
        servicoTripPolicy.validatePlannedCapacity(servico, paradasSelecionadas.size());

        return persistTripSnapshot(companyId, servico, motorista, dataOperacao, paradasSelecionadas);
    }

    @Transactional
    public Optional<Viagem> buscarOuCriarViagemAutomaticaDoMotorista(Long companyId,
                                                                     Long motoristaId,
                                                                     LocalDate dataOperacao) {
        List<Viagem> viagens = buscarOuCriarViagensAutomaticasDoMotorista(companyId, motoristaId, dataOperacao);
        if (viagens.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selecionarViagemMaisRelevante(viagens));
    }

    @Transactional
    public List<Viagem> buscarOuCriarViagensAutomaticasDoMotorista(Long companyId,
                                                                   Long motoristaId,
                                                                   LocalDate dataOperacao) {
        Map<Long, Viagem> viagens = new java.util.LinkedHashMap<>();
        for (Viagem viagem : safeList(viagemRepository.findByMotoristaIdAndDataOperacaoAndCompanyId(motoristaId, dataOperacao, companyId))) {
            viagens.put(viagem.getId(), viagem);
        }

        safeList(servicoRecorrenteRepository.findAllAutomaticosByMotoristaPadraoIdAndCompanyId(motoristaId, companyId))
                .stream()
                .filter(servico -> servico.operaNaData(dataOperacao))
                .map(servico -> createAutomaticTripIfNeeded(companyId, servico, dataOperacao))
                .filter(viagem -> isTripOperatedByMotorista(viagem, motoristaId))
                .forEach(viagem -> viagens.put(viagem.getId(), viagem));

        safeList(rotaBaseRepository.findAllAutomaticasByMotoristaIdAndCompanyId(motoristaId, companyId))
                .stream()
                .filter(rotaBase -> rotaBase.operaNaData(dataOperacao))
                .filter(rotaBase -> !hasServicoRecorrente(companyId, rotaBase))
                .map(rotaBase -> createAutomaticTripIfNeeded(companyId, rotaBase, dataOperacao))
                .filter(viagem -> isTripOperatedByMotorista(viagem, motoristaId))
                .forEach(viagem -> viagens.put(viagem.getId(), viagem));

        return ordenarViagensDoDia(viagens.values().stream().toList());
    }

    private boolean isTripOperatedByMotorista(Viagem viagem, Long motoristaId) {
        if (viagem == null || viagem.getId() == null || viagem.getMotorista() == null) {
            return false;
        }
        return viagem.getMotorista().getId().equals(motoristaId);
    }

    @Transactional
    public Optional<Viagem> buscarOuCriarViagemAutomaticaDoPassageiro(Long companyId,
                                                                      Long passageiroId,
                                                                      LocalDate dataOperacao) {
        List<Viagem> viagens = buscarOuCriarViagensAutomaticasDoPassageiro(companyId, passageiroId, dataOperacao);
        if (viagens.isEmpty()) {
            return Optional.empty();
        }
        return Optional.of(selecionarViagemMaisRelevante(viagens));
    }

    @Transactional
    public List<Viagem> buscarOuCriarViagensAutomaticasDoPassageiro(Long companyId,
                                                                    Long passageiroId,
                                                                    LocalDate dataOperacao) {
        Map<Long, Viagem> viagens = new java.util.LinkedHashMap<>();
        for (Viagem viagem : safeList(viagemRepository.findByPassageiroIdAndDataOperacaoAndCompanyId(passageiroId, dataOperacao, companyId))) {
            viagens.put(viagem.getId(), viagem);
        }

        for (ServicoRecorrente servico : safeList(servicoRecorrenteRepository.findAllAutomaticosByPassageiroIdAndCompanyId(passageiroId, companyId))) {
            if (!servico.operaNaData(dataOperacao)) {
                continue;
            }
            if (!isPassengerOperationalForServico(companyId, servico, dataOperacao, passageiroId)) {
                continue;
            }

            boolean existedBefore = findExistingForServico(companyId, servico, dataOperacao).isPresent();
            Viagem viagem = createAutomaticTripIfNeeded(companyId, servico, dataOperacao);
            if (viagem != null && (!existedBefore || isTripVisibleToPassageiro(viagem, passageiroId))) {
                viagens.put(viagem.getId(), viagem);
            }
        }

        for (RotaBase rotaBase : safeList(rotaBaseRepository.findAllAutomaticasByPassageiroIdAndCompanyId(passageiroId, companyId))) {
            if (!rotaBase.operaNaData(dataOperacao) || hasServicoRecorrente(companyId, rotaBase)) {
                continue;
            }
            if (!isPassengerOperationalForRotaBase(companyId, rotaBase, dataOperacao, passageiroId)) {
                continue;
            }

            boolean existedBefore = viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(rotaBase.getId(), dataOperacao, companyId);
            Viagem viagem = createAutomaticTripIfNeeded(companyId, rotaBase, dataOperacao);
            if (viagem != null && (!existedBefore || isTripVisibleToPassageiro(viagem, passageiroId))) {
                viagens.put(viagem.getId(), viagem);
            }
        }

        return ordenarViagensDoDia(viagens.values().stream().toList());
    }

    private boolean isTripVisibleToPassageiro(Viagem viagem, Long passageiroId) {
        if (viagem == null || viagem.getId() == null) {
            return false;
        }
        return viagemParadaRepository.findAllWithPassageiroByViagemIdOrderByOrdemAsc(viagem.getId())
                .stream()
                .anyMatch(parada -> parada.getPassageiro().getId().equals(passageiroId));
    }

    private boolean isPassengerOperationalForServico(Long companyId,
                                                     ServicoRecorrente servico,
                                                     LocalDate dataOperacao,
                                                     Long passageiroId) {
        return selectOperationalServiceStops(companyId, servico, dataOperacao)
                .stream()
                .anyMatch(stop -> stop.getPassageiro().getId().equals(passageiroId));
    }

    private boolean isPassengerOperationalForRotaBase(Long companyId,
                                                     RotaBase rotaBase,
                                                     LocalDate dataOperacao,
                                                     Long passageiroId) {
        return selectOperationalStops(companyId, rotaBase, dataOperacao)
                .stream()
                .anyMatch(stop -> stop.getPassageiro().getId().equals(passageiroId));
    }

    private List<Viagem> ordenarViagensDoDia(List<Viagem> viagens) {
        return viagens.stream()
                .sorted(java.util.Comparator
                        .comparing(Viagem::getHorarioPartida, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder()))
                        .thenComparing(Viagem::getId, java.util.Comparator.nullsLast(java.util.Comparator.naturalOrder())))
                .toList();
    }

    private <T> List<T> safeList(List<T> values) {
        return values == null ? List.of() : values;
    }

    @Transactional
    public ViagensProvisionadasResult gerarViagensAutomaticasDaEmpresa(Long companyId,
                                                                       LocalDate dataOperacao) {
        List<ServicoRecorrente> servicos = servicoRecorrenteRepository.findAllAutomaticosByCompanyId(companyId);
        List<Viagem> viagens = new java.util.ArrayList<>();
        List<ServicoIgnorado> servicosIgnorados = new java.util.ArrayList<>();
        List<CompanyTripManifestMaintenanceService.ServicoBloqueado> servicosBloqueados = new java.util.ArrayList<>();
        int criadas = 0, existentes = 0, atualizadas = 0, bloqueadas = 0, ignoradas = 0;

        for (ServicoRecorrente servico : servicos) {
            if (!servico.operaNaData(dataOperacao)) {
                servicosIgnorados.add(ignoredService(servico, MotivoServicoIgnorado.FORA_DA_DATA));
                ignoradas++;
                continue;
            }

            Optional<Viagem> existing = findExistingForServico(companyId, servico, dataOperacao);
            if (existing.isPresent()) {
                CompanyTripManifestMaintenanceService.ExistingTripMaintenance maintenance =
                        companyTripManifestMaintenanceService.maintain(companyId, servico, existing.get());
                viagens.add(maintenance.viagem());
                switch (maintenance.result()) {
                    case EXISTENTE -> existentes++;
                    case ATUALIZADA -> atualizadas++;
                    case BLOQUEADA -> {
                        bloqueadas++;
                        servicosBloqueados.add(maintenance.bloqueio());
                    }
                }
                continue;
            }

            AutomaticTripAttempt attempt = createAutomaticTripAttempt(companyId, servico, dataOperacao);
            if (attempt.viagem() == null) {
                servicosIgnorados.add(ignoredService(servico, attempt.motivo()));
                ignoradas++;
                continue;
            }
            viagens.add(attempt.viagem());
            criadas++;
        }

        return new ViagensProvisionadasResult(dataOperacao, viagens, criadas, existentes, atualizadas, bloqueadas,
                ignoradas, servicosIgnorados, servicosBloqueados);
    }

    public record ViagensProvisionadasResult(
            LocalDate dataOperacao,
            List<Viagem> viagens,
            int criadas,
            int existentes,
            int atualizadas,
            int bloqueadas,
            int ignoradas,
            List<ServicoIgnorado> servicosIgnorados,
            List<CompanyTripManifestMaintenanceService.ServicoBloqueado> servicosBloqueados
    ) {
        public ViagensProvisionadasResult(LocalDate dataOperacao,
                                          List<Viagem> viagens,
                                          int criadas,
                                          int existentes,
                                          int ignoradas) {
            this(dataOperacao, viagens, criadas, existentes, 0, 0, ignoradas, List.of(), List.of());
        }

        public ViagensProvisionadasResult(LocalDate dataOperacao,
                                          List<Viagem> viagens,
                                          int criadas,
                                          int existentes,
                                          int ignoradas,
                                          List<ServicoIgnorado> servicosIgnorados) {
            this(dataOperacao, viagens, criadas, existentes, 0, 0, ignoradas, servicosIgnorados, List.of());
        }

        public ViagensProvisionadasResult {
            viagens = viagens == null ? List.of() : viagens;
            servicosIgnorados = servicosIgnorados == null ? List.of() : servicosIgnorados;
            servicosBloqueados = servicosBloqueados == null ? List.of() : servicosBloqueados;
        }
    }

    public enum MotivoServicoIgnorado {
        FORA_DA_DATA,
        SEM_MOTORISTA,
        SEM_PASSAGEIROS_OPERACIONAIS,
        CAPACIDADE_EXCEDIDA
    }

    public record ServicoIgnorado(
            Long servicoId,
            String servicoNome,
            MotivoServicoIgnorado motivo,
            String mensagem
    ) {
    }

    private record AutomaticTripAttempt(
            Viagem viagem,
            MotivoServicoIgnorado motivo
    ) {
    }

    private Viagem selecionarViagemMaisRelevante(List<Viagem> viagens) {
        if (viagens.size() == 1) {
            return viagens.get(0);
        }

        LocalTime agora = LocalTime.now();

        return viagens.stream()
                .filter(v -> v.getStatus() == ViagemStatus.IN_PROGRESS)
                .findFirst()
                .orElseGet(() -> viagens.stream()
                        .filter(v -> v.getStatus() == ViagemStatus.PLANNED)
                        .filter(v -> v.getHorarioPartida() == null || !v.getHorarioPartida().isBefore(agora))
                        .findFirst()
                        .orElse(viagens.get(viagens.size() - 1)));
    }

    private Viagem createAutomaticTripIfNeeded(Long companyId, ServicoRecorrente servico, LocalDate dataOperacao) {
        return createAutomaticTripAttempt(companyId, servico, dataOperacao).viagem();
    }

    private AutomaticTripAttempt createAutomaticTripAttempt(Long companyId, ServicoRecorrente servico, LocalDate dataOperacao) {
        Optional<Viagem> existing = findExistingForServico(companyId, servico, dataOperacao);
        if (existing.isPresent()) {
            return new AutomaticTripAttempt(existing.get(), null);
        }

        Motorista motorista = servico.getMotoristaPadrao();
        if (motorista == null) {
            return new AutomaticTripAttempt(null, MotivoServicoIgnorado.SEM_MOTORISTA);
        }

        List<ServicoPassageiro> selectedStops = selectOperationalServiceStops(companyId, servico, dataOperacao);
        if (selectedStops.isEmpty()) {
            return new AutomaticTripAttempt(null, MotivoServicoIgnorado.SEM_PASSAGEIROS_OPERACIONAIS);
        }
        if (servicoTripPolicy.exceedsPlannedCapacity(servico, selectedStops.size())) {
            return new AutomaticTripAttempt(null, MotivoServicoIgnorado.CAPACIDADE_EXCEDIDA);
        }

        try {
            return new AutomaticTripAttempt(persistTripSnapshot(companyId, servico, motorista, dataOperacao, selectedStops), null);
        } catch (DataIntegrityViolationException exception) {
            return new AutomaticTripAttempt(
                    findExistingForServico(companyId, servico, dataOperacao)
                            .orElseThrow(() -> exception),
                    null
            );
        }
    }

    private ServicoIgnorado ignoredService(ServicoRecorrente servico, MotivoServicoIgnorado motivo) {
        return new ServicoIgnorado(servico.getId(), servico.getNome(), motivo, ignoredMessage(motivo));
    }

    private String ignoredMessage(MotivoServicoIgnorado motivo) {
        return switch (motivo) {
            case FORA_DA_DATA -> "Serviço não opera na data selecionada.";
            case SEM_MOTORISTA -> "Serviço não tem motorista padrão para montar a viagem automática.";
            case SEM_PASSAGEIROS_OPERACIONAIS -> "Nenhum passageiro ficou no manifesto operacional desta data.";
            case CAPACIDADE_EXCEDIDA -> "Manifesto operacional acima da capacidade planejada.";
        };
    }

    private Viagem createAutomaticTripIfNeeded(Long companyId, RotaBase rotaBase, LocalDate dataOperacao) {
        if (viagemRepository.existsByRotaBaseIdAndDataOperacaoAndCompanyId(rotaBase.getId(), dataOperacao, companyId)) {
            return viagemRepository.findByRotaBaseIdAndDataOperacaoAndCompanyId(rotaBase.getId(), dataOperacao, companyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.CONFLICT, "Viagem do dia já existe para a rota atual."));
        }

        List<RotaBaseParada> selectedStops = selectOperationalStops(companyId, rotaBase, dataOperacao);
        if (selectedStops.isEmpty()) {
            return null;
        }

        try {
            return persistTripSnapshot(companyId, rotaBase, null, dataOperacao, selectedStops);
        } catch (DataIntegrityViolationException exception) {
            return viagemRepository.findByRotaBaseIdAndDataOperacaoAndCompanyId(rotaBase.getId(), dataOperacao, companyId)
                    .orElseThrow(() -> exception);
        }
    }

    private Optional<Viagem> findExistingForServico(Long companyId, ServicoRecorrente servico, LocalDate dataOperacao) {
        Optional<Viagem> byService = viagemRepository.findByServicoRecorrenteIdAndDataOperacaoAndCompanyId(servico.getId(), dataOperacao, companyId);
        if (byService.isPresent()) {
            return byService;
        }
        if (servico.getRotaBaseLegada() == null) {
            return Optional.empty();
        }
        return viagemRepository.findByRotaBaseIdAndDataOperacaoAndCompanyId(servico.getRotaBaseLegada().getId(), dataOperacao, companyId);
    }

    private boolean hasServicoRecorrente(Long companyId, RotaBase rotaBase) {
        return servicoRecorrenteRepository.findByRotaBaseLegadaIdAndCompanyId(rotaBase.getId(), companyId).isPresent();
    }

    private Motorista resolveMotoristaOperacional(Long companyId, ServicoRecorrente servico, Long motoristaId) {
        if (motoristaId != null) {
            return motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));
        }
        if (servico.getMotoristaPadrao() != null) {
            return servico.getMotoristaPadrao();
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Informe um motorista para gerar a viagem deste serviço.");
    }

    private Viagem persistTripSnapshot(Long companyId,
                                       RotaBase rotaBase,
                                       ServicoRecorrente servicoRecorrente,
                                       LocalDate dataOperacao,
                                       List<RotaBaseParada> paradasSelecionadas) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Viagem viagem = new Viagem();
        viagem.setCompany(company);
        viagem.setRotaBase(rotaBase);
        viagem.setServicoRecorrente(servicoRecorrente);
        viagem.setMotorista(rotaBase.getMotorista());
        viagem.setDataOperacao(dataOperacao);
        viagem.setHorarioPartida(servicoRecorrente == null ? rotaBase.getHorarioPartida() : servicoRecorrente.getHorarioPartida());
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(1);
        viagemSnapshotApplier.apply(viagem, rotaBase, servicoRecorrente);

        Viagem saved = viagemRepository.save(viagem);
        List<ViagemParada> paradasDaViagem = new java.util.ArrayList<>();
        int ordem = 1;
        for (RotaBaseParada paradaBase : paradasSelecionadas) {
            paradasDaViagem.add(saveTripStop(saved, paradaBase.getPassageiro(), ordem++));
        }

        viagemEtaCalculator.recalculate(saved, paradasDaViagem);
        viagemParadaRepository.saveAll(paradasDaViagem);
        return viagemRepository.save(saved);
    }

    private Viagem persistTripSnapshot(Long companyId,
                                       ServicoRecorrente servico,
                                       Motorista motorista,
                                       LocalDate dataOperacao,
                                       List<ServicoPassageiro> paradasSelecionadas) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        Viagem viagem = new Viagem();
        viagem.setCompany(company);
        viagem.setRotaBase(servico.getRotaBaseLegada());
        viagem.setServicoRecorrente(servico);
        viagem.setMotorista(motorista);
        viagem.setDataOperacao(dataOperacao);
        viagem.setHorarioPartida(servico.getHorarioPartida());
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setRotaVersao(1);
        viagemSnapshotApplier.apply(viagem, servico.getRotaBaseLegada(), servico);

        Viagem saved = viagemRepository.save(viagem);
        List<ViagemParada> paradasDaViagem = new java.util.ArrayList<>();
        int ordem = 1;
        for (ServicoPassageiro servicoPassageiro : paradasSelecionadas) {
            paradasDaViagem.add(saveTripStop(saved, servicoPassageiro.getPassageiro(), ordem++));
        }

        viagemEtaCalculator.recalculate(saved, paradasDaViagem);
        viagemParadaRepository.saveAll(paradasDaViagem);
        return viagemRepository.save(saved);
    }

    private ViagemParada saveTripStop(Viagem viagem, Passageiro passageiro, int ordem) {
        ViagemParada parada = new ViagemParada();
        parada.setViagem(viagem);
        parada.setPassageiro(passageiro);
        parada.setOrdem(ordem);
        return viagemParadaRepository.save(parada);
    }

    private List<ServicoPassageiro> selectOperationalServiceStops(Long companyId,
                                                                  ServicoRecorrente servico,
                                                                  LocalDate dataOperacao) {
        List<ServicoPassageiro> serviceStops = servicoPassageiroRepository
                .findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(servico.getId(), companyId)
                .stream()
                .filter(item -> item.ativoNaData(dataOperacao))
                .toList();
        if (serviceStops.isEmpty()) {
            return List.of();
        }

        List<Long> passengerIds = serviceStops.stream()
                .map(stop -> stop.getPassageiro().getId())
                .toList();
        Map<Long, PresencaServicoStatus> decisionsByPassenger = presencaServicoRepository
                .findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(passengerIds, companyId, servico.getId(), dataOperacao)
                .stream()
                .collect(Collectors.toMap(item -> item.getPassageiro().getId(), PresencaServico::getStatus));

        if (servico.getPoliticaPresenca() == PoliticaPresencaServico.EXIGE_CONFIRMACAO) {
            return serviceStops.stream()
                    .filter(stop -> decisionsByPassenger.get(stop.getPassageiro().getId()) == PresencaServicoStatus.CONFIRMED)
                    .toList();
        }

        return serviceStops.stream()
                .filter(stop -> {
                    PresencaServicoStatus status = decisionsByPassenger.get(stop.getPassageiro().getId());
                    return status != PresencaServicoStatus.ABSENT && status != PresencaServicoStatus.CANCELED_BY_COMPANY;
                })
                .toList();
    }

    private List<RotaBaseParada> selectOperationalStops(Long companyId,
                                                        RotaBase rotaBase,
                                                        LocalDate dataOperacao) {
        List<RotaBaseParada> baseStops = rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(rotaBase.getId());
        if (baseStops.isEmpty()) {
            return List.of();
        }

        List<Long> passengerIds = baseStops.stream()
                .map(stop -> stop.getPassageiro().getId())
                .toList();
        List<Presenca> presencas = presencaRepository.findByPassageiroIdInAndCompanyIdAndDataPresenca(passengerIds, companyId, dataOperacao);

        if (presencas.isEmpty()) {
            return baseStops;
        }

        Set<Long> confirmedPassengerIds = presencas.stream()
                .filter(Presenca::isConfirmado)
                .map(presenca -> presenca.getPassageiro().getId())
                .collect(Collectors.toSet());

        if (confirmedPassengerIds.isEmpty()) {
            return List.of();
        }

        return baseStops.stream()
                .filter(stop -> confirmedPassengerIds.contains(stop.getPassageiro().getId()))
                .toList();
    }
}
