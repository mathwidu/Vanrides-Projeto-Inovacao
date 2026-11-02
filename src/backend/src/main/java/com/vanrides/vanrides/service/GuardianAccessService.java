package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.GuardianPassageiro;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.GuardianPassageiroRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

@Service
public class GuardianAccessService {

    private static final Set<ViagemStatus> READABLE_TRIP_STATUSES = Set.of(
            ViagemStatus.PLANNED,
            ViagemStatus.IN_PROGRESS,
            ViagemStatus.COMPLETED
    );

    @Autowired
    private GuardianPassageiroRepository guardianPassageiroRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    @Autowired
    private OperationalDateProvider operationalDateProvider;

    public boolean hasLinkedPassageiro(Long guardianId, Long passageiroId, Long companyId) {
        return guardianPassageiroRepository.existsByGuardianIdAndPassageiroIdAndCompanyId(
                guardianId,
                passageiroId,
                companyId
        );
    }

    public boolean hasLinkedMotorista(Long guardianId, Long motoristaId, Long companyId) {
        return viagemRepository.existsByGuardianLinkedPassengerAndMotoristaOnDate(
                guardianId,
                motoristaId,
                companyId,
                operationalDateProvider.today(),
                READABLE_TRIP_STATUSES
        );
    }

    public List<Long> listLinkedPassageiroIds(Long guardianId, Long companyId) {
        // Fetching the linked passenger eagerly avoids LazyInitializationException
        // when the guardian list is resolved outside an open Hibernate session.
        return guardianPassageiroRepository.findAllWithPassageiroByGuardianIdAndCompanyId(guardianId, companyId)
                .stream()
                .map(GuardianPassageiro::getPassageiro)
                .map(passageiro -> passageiro.getId())
                .toList();
    }
}
