package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import com.vanrides.vanrides.model.DriverCompanyMembership;
import com.vanrides.vanrides.model.DriverCompanyMembershipStatus;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.repository.DriverCompanyLinkRequestRepository;
import com.vanrides.vanrides.repository.DriverCompanyMembershipRepository;
import org.springframework.stereotype.Component;

@Component
public class DriverOnboardingStatusResolver {

    private static final String STATUS_NO_COMPANY = "NO_COMPANY";
    private static final String STATUS_PENDING_APPROVAL = "PENDING_APPROVAL";
    private static final String STATUS_COMPANY_LINKED = "COMPANY_LINKED";

    private final DriverCompanyLinkRequestRepository requestRepository;
    private final DriverCompanyMembershipRepository membershipRepository;
    private final DriverOnboardingResponseMapper responseMapper;

    public DriverOnboardingStatusResolver(DriverCompanyLinkRequestRepository requestRepository,
                                          DriverCompanyMembershipRepository membershipRepository,
                                          DriverOnboardingResponseMapper responseMapper) {
        this.requestRepository = requestRepository;
        this.membershipRepository = membershipRepository;
        this.responseMapper = responseMapper;
    }

    public DriverOnboardingStatusResponse resolve(Motorista motorista) {
        if (motorista == null) {
            return noCompany();
        }

        DriverCompanyMembership activeMembership = membershipRepository
                .findFirstByMotoristaIdAndStatusOrderByCreatedAtDesc(motorista.getId(), DriverCompanyMembershipStatus.ACTIVE)
                .orElse(null);

        if (motorista.getCompany() != null) {
            return new DriverOnboardingStatusResponse(
                    STATUS_COMPANY_LINKED,
                    motorista.getCompany().getId(),
                    motorista.getCompany().getNome(),
                    activeMembership != null ? activeMembership.getId() : null,
                    null
            );
        }

        DriverCompanyLinkRequest pendingRequest = requestRepository
                .findFirstByMotoristaIdAndStatusOrderByRequestedAtDesc(motorista.getId(), DriverCompanyLinkRequestStatus.PENDING)
                .orElse(null);
        if (pendingRequest != null) {
            return new DriverOnboardingStatusResponse(
                    STATUS_PENDING_APPROVAL,
                    null,
                    null,
                    null,
                    responseMapper.toPendingRequest(pendingRequest)
            );
        }

        return noCompany();
    }

    private DriverOnboardingStatusResponse noCompany() {
        return new DriverOnboardingStatusResponse(STATUS_NO_COMPANY, null, null, null, null);
    }
}
