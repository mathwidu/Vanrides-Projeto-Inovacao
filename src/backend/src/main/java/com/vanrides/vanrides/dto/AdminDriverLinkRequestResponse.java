package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminDriverLinkRequestResponse(
        Long id,
        Long motoristaId,
        String motoristaNome,
        String motoristaEmail,
        String motoristaTelefone,
        Long companyId,
        String companyName,
        String status,
        OffsetDateTime requestedAt,
        OffsetDateTime reviewedAt,
        boolean emailVerified,
        String reviewNotes
) {
}
