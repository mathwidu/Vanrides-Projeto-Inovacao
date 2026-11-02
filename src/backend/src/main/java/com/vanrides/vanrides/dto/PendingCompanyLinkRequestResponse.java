package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record PendingCompanyLinkRequestResponse(
        Long id,
        Long companyId,
        String companyName,
        String status,
        OffsetDateTime requestedAt
) {
}
