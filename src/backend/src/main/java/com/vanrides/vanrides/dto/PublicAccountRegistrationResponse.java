package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record PublicAccountRegistrationResponse(
        Long id,
        String nome,
        String email,
        String telefone,
        boolean emailVerified,
        OffsetDateTime emailVerificationLastSentAt
) {
}
