package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminInviteCodeSummaryResponse(
        Long id,
        String code,
        String status,
        Integer maxUses,
        int useCount,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt
) {
}
