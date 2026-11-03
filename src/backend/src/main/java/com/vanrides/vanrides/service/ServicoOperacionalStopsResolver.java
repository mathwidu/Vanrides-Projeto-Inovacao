package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.PresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.ServicoPassageiro;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.ServicoPassageiroRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
public class ServicoOperacionalStopsResolver {

    @Autowired
    private ServicoPassageiroRepository servicoPassageiroRepository;

    @Autowired
    private PresencaServicoRepository presencaServicoRepository;

    public List<ServicoPassageiro> select(Long companyId, ServicoRecorrente servico, LocalDate dataOperacao) {
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
                .findByPassageiroIdInAndCompanyIdAndServicoRecorrenteIdAndDataOperacao(
                        passengerIds,
                        companyId,
                        servico.getId(),
                        dataOperacao
                )
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
}
