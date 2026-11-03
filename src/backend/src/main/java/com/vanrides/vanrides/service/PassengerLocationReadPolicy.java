package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

import java.util.Objects;
import java.util.Set;

@Component
public class PassengerLocationReadPolicy {

    private static final String NOT_FOUND_MESSAGE = "Motorista não encontrado na empresa atual.";
    private static final Set<ViagemStatus> READABLE_TRIP_STATUSES = Set.of(
            ViagemStatus.PLANNED,
            ViagemStatus.IN_PROGRESS,
            ViagemStatus.COMPLETED
    );

    private final ViagemRepository viagemRepository;
    private final OperationalDateProvider operationalDateProvider;

    @Autowired
    public PassengerLocationReadPolicy(
            ViagemRepository viagemRepository,
            OperationalDateProvider operationalDateProvider) {
        this.viagemRepository = Objects.requireNonNull(viagemRepository, "viagemRepository");
        this.operationalDateProvider = Objects.requireNonNull(
                operationalDateProvider,
                "operationalDateProvider"
        );
    }

    public void requireCanReadMotorista(Long passageiroId, Long motoristaId, Long companyId) {
        if (!isPositive(passageiroId) || !isPositive(motoristaId) || !isPositive(companyId)) {
            throw notFound();
        }

        if (viagemRepository.existsByPassengerAndMotoristaOnDate(
                passageiroId,
                motoristaId,
                companyId,
                operationalDateProvider.today(),
                READABLE_TRIP_STATUSES
        )) {
            return;
        }

        throw notFound();
    }

    private boolean isPositive(Long value) {
        return value != null && value > 0;
    }

    private ResponseStatusException notFound() {
        return new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
    }
}
