package com.vanrides.vanrides.dto;

public record DriverOnboardingStatusResponse(
        String companyLinkStatus,
        Long companyId,
        String companyName,
        Long activeMembershipId,
        PendingDriverCompanyLinkRequestResponse pendingRequest
) {
}
