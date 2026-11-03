package com.vanrides.vanrides.dto;

public record PassengerOnboardingStatusResponse(
        String companyLinkStatus,
        Long companyId,
        String companyName,
        Long motoristaId,
        PendingCompanyLinkRequestResponse pendingRequest
) {
}
