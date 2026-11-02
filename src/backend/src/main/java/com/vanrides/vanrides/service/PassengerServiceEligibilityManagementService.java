package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

@Service
public class PassengerServiceEligibilityManagementService {

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    public void assignServiceEligibilities(Passageiro passageiro,
                                           Company company,
                                           Long companyId,
                                           List<Long> servicoIds) {
        if (servicoIds == null) {
            return;
        }
        List<Long> orderedServiceIds = normalizeServicoIds(servicoIds);
        Map<Long, ServicoRecorrente> servicos = resolveServicos(companyId, orderedServiceIds);
        for (Long servicoId : orderedServiceIds) {
            Optional<ServicoPassageiro> existing = servicoPassageiroRepository
                    .findByServicoRecorrenteIdAndPassageiroIdAndCompanyId(servicoId, passageiro.getId(), companyId);
            if (existing.isPresent() && existing.get().isAtivo()) {
                continue;
            }
            ServicoPassageiro servicoPassageiro = existing.orElseGet(ServicoPassageiro::new);
            servicoPassageiro.setCompany(company);
            servicoPassageiro.setServicoRecorrente(servicos.get(servicoId));
            servicoPassageiro.setPassageiro(passageiro);
            servicoPassageiro.setAtivo(true);
            servicoPassageiro.setDataFim(null);
            servicoPassageiro.setOrdemPadrao(nextOrdemPadrao(servicoId, companyId));
            servicoPassageiroRepository.save(servicoPassageiro);
        }
    }

    public void replaceServiceEligibilities(Passageiro passageiro, Long companyId, List<Long> servicoIds) {
        List<Long> orderedServiceIds = normalizeServicoIds(servicoIds);
        Map<Long, ServicoRecorrente> servicos = resolveServicos(companyId, orderedServiceIds);
        Set<Long> selectedIds = new LinkedHashSet<>(orderedServiceIds);
        Map<Long, ServicoPassageiro> existingByServicoId = new LinkedHashMap<>();
        for (ServicoPassageiro existing : servicoPassageiroRepository
                .findAllWithServicoByPassageiroIdAndCompanyId(passageiro.getId(), companyId)) {
            existingByServicoId.put(existing.getServicoRecorrente().getId(), existing);
        }

        LocalDate today = LocalDate.now();
        for (ServicoPassageiro existing : existingByServicoId.values()) {
            Long servicoId = existing.getServicoRecorrente().getId();
            if (selectedIds.contains(servicoId)) {
                existing.setAtivo(true);
                existing.setDataFim(null);
                if (existing.getDataInicio() == null) {
                    existing.setDataInicio(today);
                }
                if (existing.getOrdemPadrao() == null) {
                    existing.setOrdemPadrao(nextOrdemPadrao(servicoId, companyId));
                }
            } else if (existing.isAtivo()) {
                existing.setAtivo(false);
                existing.setDataFim(today);
            }
        }

        for (Long servicoId : orderedServiceIds) {
            if (existingByServicoId.containsKey(servicoId)) {
                continue;
            }
            ServicoPassageiro servicoPassageiro = new ServicoPassageiro();
            servicoPassageiro.setCompany(passageiro.getCompany());
            servicoPassageiro.setServicoRecorrente(servicos.get(servicoId));
            servicoPassageiro.setPassageiro(passageiro);
            servicoPassageiro.setAtivo(true);
            servicoPassageiro.setDataInicio(today);
            servicoPassageiro.setOrdemPadrao(nextOrdemPadrao(servicoId, companyId));
            servicoPassageiroRepository.save(servicoPassageiro);
        }
    }

    public List<Long> validateServiceIds(Long companyId, List<Long> servicoIds) {
        List<Long> orderedServiceIds = normalizeServicoIds(servicoIds);
        resolveServicos(companyId, orderedServiceIds);
        return orderedServiceIds;
    }

    private Map<Long, ServicoRecorrente> resolveServicos(Long companyId, List<Long> servicoIds) {
        if (servicoIds.isEmpty()) {
            return Map.of();
        }
        List<ServicoRecorrente> servicos = servicoRecorrenteRepository.findAllAtivosByIdInAndCompanyId(servicoIds, companyId);
        Map<Long, ServicoRecorrente> byId = new LinkedHashMap<>();
        for (ServicoRecorrente servico : servicos) {
            byId.put(servico.getId(), servico);
        }
        if (byId.size() != servicoIds.size()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Serviço recorrente não encontrado na empresa atual.");
        }
        return byId;
    }

    private List<Long> normalizeServicoIds(List<Long> servicoIds) {
        List<Long> orderedIds = servicoIds.stream().filter(id -> id != null).toList();
        if (orderedIds.size() != servicoIds.size() || new LinkedHashSet<>(orderedIds).size() != orderedIds.size()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "A lista de serviços deve conter ids válidos e sem duplicidade.");
        }
        return orderedIds;
    }

    private Integer nextOrdemPadrao(Long servicoId, Long companyId) {
        return Math.toIntExact(servicoPassageiroRepository.countByServicoRecorrenteIdAndCompanyIdAndAtivoTrue(servicoId, companyId) + 1);
    }
}
