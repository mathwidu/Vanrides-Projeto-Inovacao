package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminPassageiroLinkedSummaryResponse;
import com.vanrides.vanrides.dto.AdminServicoPassageiroResponse;
import com.vanrides.vanrides.dto.AdminServicoPassageirosUpdateRequest;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteRequest;
import com.vanrides.vanrides.dto.AdminServicoRecorrenteResponse;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CompanyAdminServicoRecorrenteService {

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    @Transactional(readOnly = true)
    public List<AdminServicoRecorrenteResponse> listarServicos(Long companyId) {
        return servicoRecorrenteRepository.findAllAtivosWithMotoristaByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public AdminServicoRecorrenteResponse detalharServico(Long companyId, Long servicoId) {
        return toResponse(findServico(companyId, servicoId));
    }

    @Transactional
    public AdminServicoRecorrenteResponse criarServico(Long companyId, AdminServicoRecorrenteRequest request) {
        Company company = companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada."));

        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setCompany(company);
        applyRequest(companyId, servico, request);
        servico.setAtivo(true);

        ServicoRecorrente saved = servicoRecorrenteRepository.save(servico);
        List<Long> passageiroIds = request != null && request.getPassageiroIds() != null
                ? substituirPassageirosInternal(companyId, saved, request.getPassageiroIds())
                : List.of();

        return toResponse(saved, passageiroIds);
    }

    @Transactional
    public AdminServicoRecorrenteResponse editarServico(Long companyId,
                                                        Long servicoId,
                                                        AdminServicoRecorrenteRequest request) {
        ServicoRecorrente servico = findServico(companyId, servicoId);
        applyRequest(companyId, servico, request);
        ServicoRecorrente saved = servicoRecorrenteRepository.save(servico);

        List<Long> passageiroIds = request != null && request.getPassageiroIds() != null
                ? substituirPassageirosInternal(companyId, saved, request.getPassageiroIds())
                : servicoPassageiroRepository.findPassageiroIdsByServicoIdAndCompanyIdOrderByOrdem(servicoId, companyId);

        return toResponse(saved, passageiroIds);
    }

    @Transactional
    public void inativarServico(Long companyId, Long servicoId) {
        ServicoRecorrente servico = findServico(companyId, servicoId);
        if (viagemRepository.existsByServicoRecorrenteIdAndStatusAndCompanyId(
                servicoId,
                ViagemStatus.IN_PROGRESS,
                companyId
        )) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível inativar o serviço: existe viagem em andamento.");
        }
        servico.setAtivo(false);
        servicoRecorrenteRepository.save(servico);
    }

    @Transactional(readOnly = true)
    public List<AdminServicoPassageiroResponse> listarPassageiros(Long companyId, Long servicoId) {
        findServico(companyId, servicoId);
        return servicoPassageiroRepository
                .findAllAtivosWithPassageiroByServicoIdAndCompanyIdOrderByOrdem(servicoId, companyId)
                .stream()
                .map(this::toPassageiroResponse)
                .toList();
    }

    @Transactional
    public List<AdminServicoPassageiroResponse> substituirPassageiros(Long companyId,
                                                                      Long servicoId,
                                                                      AdminServicoPassageirosUpdateRequest request) {
        if (request == null || request.getPassageiroIds() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de passageiros é obrigatória.");
        }

        ServicoRecorrente servico = findServico(companyId, servicoId);
        substituirPassageirosInternal(companyId, servico, request.getPassageiroIds());
        return listarPassageiros(companyId, servicoId);
    }

    private void applyRequest(Long companyId,
                              ServicoRecorrente servico,
                              AdminServicoRecorrenteRequest request) {
        if (request == null || request.getNome() == null || request.getNome().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Nome do serviço é obrigatório.");
        }
        if (request.getCapacidadePlanejada() != null && request.getCapacidadePlanejada() <= 0) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Capacidade planejada deve ser maior que zero.");
        }

        servico.setNome(request.getNome().trim());
        servico.setMotoristaPadrao(resolveMotoristaPadrao(companyId, request.getMotoristaPadraoId()));
        servico.setTipoOperacao(parseTipoOperacao(request.getTipoOperacao()));
        servico.setPoliticaPresenca(parsePoliticaPresenca(request.getPoliticaPresenca()));
        servico.setGeracaoAutomaticaAtiva(request.getGeracaoAutomaticaAtiva() == null || request.getGeracaoAutomaticaAtiva());
        servico.setDiasOperacaoFromSet(normalizeOperationDays(request.getDiasOperacao()));
        servico.setHorarioPartida(request.getHorarioPartida());
        servico.setDestinoFinalNome(request.getDestinoFinalNome());
        servico.setDestinoFinalEndereco(request.getDestinoFinalEndereco());
        servico.setDestinoFinalLatitude(request.getDestinoFinalLatitude());
        servico.setDestinoFinalLongitude(request.getDestinoFinalLongitude());
        servico.setCapacidadePlanejada(request.getCapacidadePlanejada());
    }

    private List<Long> substituirPassageirosInternal(Long companyId,
                                                     ServicoRecorrente servico,
                                                     List<Long> passageiroIds) {
        List<Long> orderedIds = normalizePassageiroIds(passageiroIds);
        Map<Long, Passageiro> passageiros = resolvePassageiros(companyId, orderedIds);
        Set<Long> selectedIds = new LinkedHashSet<>(orderedIds);
        Map<Long, Integer> ordemByPassageiroId = new LinkedHashMap<>();
        for (int index = 0; index < orderedIds.size(); index++) {
            ordemByPassageiroId.put(orderedIds.get(index), index + 1);
        }

        Map<Long, ServicoPassageiro> existingByPassageiroId = new LinkedHashMap<>();
        for (ServicoPassageiro existing : servicoPassageiroRepository
                .findAllByServicoRecorrenteIdAndCompanyId(servico.getId(), companyId)) {
            existingByPassageiroId.put(existing.getPassageiro().getId(), existing);
        }

        LocalDate today = LocalDate.now();
        for (ServicoPassageiro existing : existingByPassageiroId.values()) {
            Long passageiroId = existing.getPassageiro().getId();
            if (selectedIds.contains(passageiroId)) {
                existing.setAtivo(true);
                existing.setDataFim(null);
                if (existing.getDataInicio() == null) {
                    existing.setDataInicio(today);
                }
                existing.setOrdemPadrao(ordemByPassageiroId.get(passageiroId));
            } else if (existing.isAtivo()) {
                existing.setAtivo(false);
                existing.setDataFim(today);
            }
        }

        for (Long passageiroId : orderedIds) {
            if (existingByPassageiroId.containsKey(passageiroId)) {
                continue;
            }
            ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
            servicoPassageiro.setCompany(servico.getCompany());
            servicoPassageiro.setServicoRecorrente(servico);
            servicoPassageiro.setPassageiro(passageiros.get(passageiroId));
            servicoPassageiro.setOrdemPadrao(ordemByPassageiroId.get(passageiroId));
            servicoPassageiro.setAtivo(true);
            servicoPassageiro.setDataInicio(today);
            servicoPassageiroRepository.save(servicoPassageiro);
        }
        return orderedIds;
    }

    private ServicoRecorrente findServico(Long companyId, Long servicoId) {
        return servicoRecorrenteRepository.findByIdAndCompanyId(servicoId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual."));
    }

    private Motorista resolveMotoristaPadrao(Long companyId, Long motoristaId) {
        if (motoristaId == null) {
            return null;
        }
        return motoristaRepository.findByIdAndCompanyId(motoristaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista padrão não encontrado na empresa atual."));
    }

    private Map<Long, Passageiro> resolvePassageiros(Long companyId, List<Long> orderedIds) {
        if (orderedIds.isEmpty()) {
            return Map.of();
        }

        List<Passageiro> passageiros = passageiroRepository.findAllByIdInAndCompanyId(orderedIds, companyId);
        Map<Long, Passageiro> byId = new LinkedHashMap<>();
        for (Passageiro passageiro : passageiros) {
            byId.put(passageiro.getId(), passageiro);
        }
        if (byId.size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Um ou mais passageiros não existem na empresa atual.");
        }
        return byId;
    }

    private List<Long> normalizePassageiroIds(List<Long> passageiroIds) {
        if (passageiroIds == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Lista de passageiros é obrigatória.");
        }

        List<Long> orderedIds = passageiroIds.stream()
                .filter(id -> id != null)
                .toList();

        if (orderedIds.size() != passageiroIds.size() || new LinkedHashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista de passageiros deve conter ids válidos e sem duplicidade.");
        }
        return orderedIds;
    }

    private Set<DayOfWeek> normalizeOperationDays(List<String> diasOperacao) {
        if (diasOperacao == null || diasOperacao.isEmpty()) {
            LinkedHashSet<DayOfWeek> weekdays = new LinkedHashSet<>();
            weekdays.add(DayOfWeek.MONDAY);
            weekdays.add(DayOfWeek.TUESDAY);
            weekdays.add(DayOfWeek.WEDNESDAY);
            weekdays.add(DayOfWeek.THURSDAY);
            weekdays.add(DayOfWeek.FRIDAY);
            return weekdays;
        }

        LinkedHashSet<DayOfWeek> normalized = new LinkedHashSet<>();
        for (String value : diasOperacao) {
            if (value == null || value.isBlank()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dias de operação devem ser informados sem valores vazios.");
            }
            try {
                normalized.add(DayOfWeek.valueOf(value.trim().toUpperCase()));
            } catch (IllegalArgumentException exception) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Dia de operação inválido: " + value);
            }
        }
        return normalized;
    }

    private TipoOperacaoServico parseTipoOperacao(String value) {
        if (value == null || value.isBlank()) {
            return TipoOperacaoServico.IDA;
        }
        try {
            return TipoOperacaoServico.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Tipo de operação inválido: " + value);
        }
    }

    private PoliticaPresencaServico parsePoliticaPresenca(String value) {
        if (value == null || value.isBlank()) {
            return PoliticaPresencaServico.INCLUIR_POR_PADRAO;
        }
        try {
            return PoliticaPresencaServico.valueOf(value.trim().toUpperCase());
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Política de presença inválida: " + value);
        }
    }

    private AdminServicoRecorrenteResponse toResponse(ServicoRecorrente servico) {
        return toResponse(servico, servicoPassageiroRepository
                .findPassageiroIdsByServicoIdAndCompanyIdOrderByOrdem(servico.getId(), servico.getCompany().getId()));
    }

    private AdminServicoRecorrenteResponse toResponse(ServicoRecorrente servico, List<Long> passageiroIds) {
        return new AdminServicoRecorrenteResponse(
                servico.getId(),
                servico.getRotaBaseLegada() == null ? null : servico.getRotaBaseLegada().getId(),
                servico.getNome(),
                servico.getTipoOperacao(),
                toMotoristaSummary(servico.getMotoristaPadrao()),
                passageiroIds,
                servico.isAtivo(),
                servico.isGeracaoAutomaticaAtiva(),
                servico.getPoliticaPresenca(),
                servico.getDiasOperacaoAsSet().stream()
                        .sorted()
                        .map(DayOfWeek::name)
                        .toList(),
                servico.getHorarioPartida(),
                servico.getDestinoFinalNome(),
                servico.getDestinoFinalEndereco(),
                servico.getDestinoFinalLatitude(),
                servico.getDestinoFinalLongitude(),
                servico.getCapacidadePlanejada()
        );
    }

    private AdminServicoPassageiroResponse toPassageiroResponse(ServicoPassageiro servicoPassageiro) {
        return new AdminServicoPassageiroResponse(
                servicoPassageiro.getId(),
                toPassageiroSummary(servicoPassageiro.getPassageiro()),
                servicoPassageiro.getOrdemPadrao(),
                servicoPassageiro.isAtivo(),
                servicoPassageiro.getDataInicio(),
                servicoPassageiro.getDataFim()
        );
    }

    private AdminPassageiroLinkedSummaryResponse toPassageiroSummary(Passageiro passageiro) {
        return new AdminPassageiroLinkedSummaryResponse(
                passageiro.getId(),
                passageiro.getNome(),
                passageiro.getEmail(),
                passageiro.isMenorDeIdade(),
                toMotoristaSummary(passageiro.getMotorista()),
                passageiro.getDataNascimento(), passageiro.getFotoPerfilUrl()
        );
    }

    private AdminMotoristaSummaryResponse toMotoristaSummary(Motorista motorista) {
        if (motorista == null) {
            return null;
        }
        return new AdminMotoristaSummaryResponse(
                motorista.getId(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.isEmailVerified(), motorista.getDataNascimento(), motorista.getFotoPerfilUrl());
    }
}
