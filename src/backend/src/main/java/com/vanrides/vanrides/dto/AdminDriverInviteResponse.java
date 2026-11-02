package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminDriverInviteResponse(
        Long id,
        Long companyId,
        String companyName,
        String token,
        String shareUrl,
        String status,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime acceptedAt,
        Long acceptedByMotoristaId,
        String acceptedByMotoristaName
) {
}
