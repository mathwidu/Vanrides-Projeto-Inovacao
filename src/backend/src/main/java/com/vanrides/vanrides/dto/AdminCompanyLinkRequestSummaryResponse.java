package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminCompanyLinkRequestSummaryResponse(
        Long id,
        Long passageiroId,
        String passageiroNome,
        String passageiroEmail,
        String companyName,
        String source,
        OffsetDateTime requestedAt,
        boolean emailVerified
) {
}
