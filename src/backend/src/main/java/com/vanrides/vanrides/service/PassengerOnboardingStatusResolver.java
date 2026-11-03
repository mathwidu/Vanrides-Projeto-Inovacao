package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.repository.CompanyLinkRequestRepository;
import org.springframework.stereotype.Component;

@Component
class PassengerOnboardingStatusResolver {

    private static final String STATUS_NO_COMPANY = "NO_COMPANY";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_COMPANY_LINKED = "COMPANY_LINKED";

    private final CompanyLinkRequestRepository companyLinkRequestRepository;
    private final PassengerOnboardingResponseMapper responseMapper;

    PassengerOnboardingStatusResolver(CompanyLinkRequestRepository companyLinkRequestRepository,
                                      PassengerOnboardingResponseMapper responseMapper) {
        this.companyLinkRequestRepository = companyLinkRequestRepository;
        this.responseMapper = responseMapper;
    }

    PassengerOnboardingStatusResponse resolve(Passageiro passageiro) {
        if (passageiro == null) {
            return noCompany();
        }

        if (passageiro.getCompany() != null) {
            return new PassengerOnboardingStatusResponse(
                    STATUS_COMPANY_LINKED,
                    passageiro.getCompany().getId(),
                    passageiro.getCompany().getNome(),
                    passageiro.getMotorista() != null ? passageiro.getMotorista().getId() : null,
                    null
            );
        }

        CompanyLinkRequest pendingRequest = companyLinkRequestRepository
                .findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(passageiro.getId(), CompanyLinkRequestStatus.PENDING)
                .orElse(null);
        if (pendingRequest != null) {
            return new PassengerOnboardingStatusResponse(
                    STATUS_PENDING_APPROVAL,
                    null,
                    null,
                    null,
                    responseMapper.toPendingRequest(pendingRequest)
            );
        }

        return noCompany();
    }

    private PassengerOnboardingStatusResponse noCompany() {
        return new PassengerOnboardingStatusResponse(STATUS_NO_COMPANY, null, null, null, null);
    }
}
