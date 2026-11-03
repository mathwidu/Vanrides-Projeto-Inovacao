package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminRotaBaseCreateRequest;
import com.vanrides.vanrides.dto.AdminRotaBaseSummaryResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.RotaBase;
import com.vanrides.vanrides.model.RotaBaseParada;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.CompanyRepository;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.RotaBaseParadaRepository;
import com.vanrides.vanrides.repository.RotaBaseRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.DayOfWeek;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
public class CompanyAdminRotaService {

    @Autowired
    private RotaBaseRepository rotaBaseRepository;

    @Autowired
    private RotaBaseParadaRepository rotaBaseParadaRepository;

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private CompanyRepository companyRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    @Transactional
    public AdminRotaBaseSummaryResponse criarRotaBase(Long companyId, AdminRotaBaseCreateRequest request) {
        RotaBase rotaBase = new RotaBase();
        rotaBase.setCompany(companyRepository.findById(companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Empresa não encontrada.")));
        List<Long> orderedIds = applyRouteRequest(companyId, rotaBase, request);
        rotaBase.setAtivo(true);
        rotaBase.setAtiva(true);

        RotaBase saved = rotaBaseRepository.save(rotaBase);
        persistStops(saved, orderedIds, resolvePassageiros(companyId, orderedIds));

        return toSummary(saved, orderedIds);
    }

    @Transactional
    public AdminRotaBaseSummaryResponse editarRotaBase(Long companyId,
                                                       Long rotaId,
                                                       AdminRotaBaseCreateRequest request) {
        RotaBase rotaBase = rotaBaseRepository.findByIdAndCompanyId(rotaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota base não encontrada na empresa atual."));
        List<Long> orderedIds = applyRouteRequest(companyId, rotaBase, request);
        rotaBaseRepository.save(rotaBase);

        List<RotaBaseParada> existingStops = rotaBaseParadaRepository.findAllWithPassageiroByRotaBaseIdOrderByOrdemAsc(rotaBase.getId());
        if (!existingStops.isEmpty()) {
            rotaBaseParadaRepository.deleteAll(existingStops);
        }
        persistStops(rotaBase, orderedIds, resolvePassageiros(companyId, orderedIds));

        return toSummary(rotaBase, orderedIds);
    }

    @Transactional
    public void inativarRotaBase(Long companyId, Long rotaId) {
        RotaBase rotaBase = rotaBaseRepository.findByIdAndCompanyId(rotaId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Rota base não encontrada na empresa atual."));

        if (viagemRepository.existsByRotaBaseIdAndStatusAndCompanyId(rotaId, ViagemStatus.IN_PROGRESS, companyId)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Não é possível inativar a rota: existe viagem em andamento.");
        }

        rotaBase.setAtiva(false);
        rotaBase.setAtivo(false);
        rotaBaseRepository.save(rotaBase);
    }

    @Transactional(readOnly = true)
    public List<AdminRotaBaseSummaryResponse> listarRotasBase(Long companyId) {
        return rotaBaseRepository.findAllWithMotoristaByCompanyIdOrderByNomeAsc(companyId)
                .stream()
                .map(rotaBase -> toSummary(rotaBase,
                        rotaBaseParadaRepository.findPassageiroIdsByRotaBaseIdOrderByOrdemAsc(rotaBase.getId())))
                .toList();
    }

    private List<Long> applyRouteRequest(Long companyId,
                                         RotaBase rotaBase,
                                         AdminRotaBaseCreateRequest request) {
        Motorista motorista = motoristaRepository.findByIdAndCompanyId(request.getMotoristaId(), companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Motorista não encontrado na empresa atual."));

        List<Long> orderedIds = normalizePassageiroIds(request.getPassageiroIds());
        Map<Long, Passageiro> passageiros = resolvePassageiros(companyId, orderedIds);
        validateMotoristaOwnership(motorista.getId(), passageiros, orderedIds);

        rotaBase.setMotorista(motorista);
        rotaBase.setNome(request.getNome().trim());
        rotaBase.setGeracaoAutomaticaAtiva(request.getGeracaoAutomaticaAtiva() == null || request.getGeracaoAutomaticaAtiva());
        rotaBase.setDiasOperacaoFromSet(normalizeOperationDays(request.getDiasOperacao()));
        rotaBase.setHorarioPartida(request.getHorarioPartida());
        rotaBase.setDestinoFinalNome(request.getDestinoFinalNome());
        rotaBase.setDestinoFinalEndereco(request.getDestinoFinalEndereco());
        rotaBase.setDestinoFinalLatitude(request.getDestinoFinalLatitude());
        rotaBase.setDestinoFinalLongitude(request.getDestinoFinalLongitude());
        return orderedIds;
    }

    private List<Long> normalizePassageiroIds(List<Long> passageiroIds) {
        if (passageiroIds == null || passageiroIds.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A rota precisa de ao menos um passageiro.");
        }

        List<Long> orderedIds = passageiroIds.stream()
                .filter(id -> id != null)
                .toList();

        if (orderedIds.size() != passageiroIds.size() || new LinkedHashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista de passageiros deve conter ids válidos e sem duplicidade.");
        }
        return orderedIds;
    }

    private Map<Long, Passageiro> resolvePassageiros(Long companyId, List<Long> orderedIds) {
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

    private void validateMotoristaOwnership(Long motoristaId,
                                            Map<Long, Passageiro> passageiros,
                                            List<Long> orderedIds) {
        for (Long passageiroId : orderedIds) {
            Passageiro passageiro = passageiros.get(passageiroId);
            if (passageiro.getMotorista() == null || !motoristaId.equals(passageiro.getMotorista().getId())) {
                throw new ResponseStatusException(
                        HttpStatus.CONFLICT,
                        "Todos os passageiros da rota precisam estar vinculados ao motorista selecionado."
                );
            }
        }
    }

    private void persistStops(RotaBase rotaBase,
                              List<Long> orderedIds,
                              Map<Long, Passageiro> passageiros) {
        int ordem = 1;
        for (Long passageiroId : orderedIds) {
            RotaBaseParada parada = new RotaBaseParada();
            parada.setRotaBase(rotaBase);
            parada.setPassageiro(passageiros.get(passageiroId));
            parada.setOrdem(ordem++);
            rotaBaseParadaRepository.save(parada);
        }
    }

    private AdminRotaBaseSummaryResponse toSummary(RotaBase rotaBase, List<Long> passageiroIds) {
        return new AdminRotaBaseSummaryResponse(
                rotaBase.getId(),
                rotaBase.getNome(),
                toMotoristaSummary(rotaBase.getMotorista()),
                passageiroIds,
                rotaBase.isAtivo() && rotaBase.isAtiva(),
                rotaBase.isGeracaoAutomaticaAtiva(),
                rotaBase.getDiasOperacaoAsSet().stream()
                        .sorted()
                        .map(DayOfWeek::name)
                        .toList(),
                rotaBase.getHorarioPartida(),
                rotaBase.getDestinoFinalNome(),
                rotaBase.getDestinoFinalEndereco(),
                rotaBase.getDestinoFinalLatitude(),
                rotaBase.getDestinoFinalLongitude()
        );
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

    private AdminMotoristaSummaryResponse toMotoristaSummary(Motorista motorista) {
        return new AdminMotoristaSummaryResponse(
                motorista.getId(),
                motorista.getNome(),
                motorista.getEmail(),
                motorista.getTelefone(),
                motorista.isEmailVerified(),
                motorista.getDataNascimento(),
                motorista.getFotoPerfilUrl()
        );
    }
}
