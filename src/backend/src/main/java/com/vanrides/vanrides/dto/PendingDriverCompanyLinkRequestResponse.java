package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record PendingDriverCompanyLinkRequestResponse(
        Long id,
        Long companyId,
        String companyName,
        String status,
        OffsetDateTime requestedAt
) {
}
