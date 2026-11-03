package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminCompanyLinkRequestDetailResponse(
        Long id,
        Long passageiroId,
        String passageiroNome,
        String passageiroEmail,
        String passageiroTelefone,
        String companyName,
        String source,
        String status,
        OffsetDateTime requestedAt,
        boolean emailVerified,
        AdminMotoristaSummaryResponse motoristaAtual
) {
}
