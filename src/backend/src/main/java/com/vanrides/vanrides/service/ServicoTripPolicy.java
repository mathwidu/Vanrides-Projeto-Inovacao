package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ServicoRecorrente;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.time.LocalDate;

@Component
public class ServicoTripPolicy {

    public void validateDisponivelParaData(ServicoRecorrente servico, LocalDate dataOperacao) {
        if (!servico.isAtivo()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço recorrente inativo não pode gerar viagem.");
        }
        if (!servico.operaNaData(dataOperacao)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Serviço recorrente não opera na data informada.");
        }
    }

    public void validatePlannedCapacity(ServicoRecorrente servico, int manifestSize) {
        if (exceedsPlannedCapacity(servico, manifestSize)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT, "Manifesto operacional excede a capacidade planejada do serviço.");
        }
    }

    public boolean exceedsPlannedCapacity(ServicoRecorrente servico, int manifestSize) {
        Integer capacidadePlanejada = servico.getCapacidadePlanejada();
        return capacidadePlanejada != null && capacidadePlanejada > 0 && manifestSize > capacidadePlanejada;
    }
}
