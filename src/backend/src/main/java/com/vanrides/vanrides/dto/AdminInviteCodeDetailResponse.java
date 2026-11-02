package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record AdminInviteCodeDetailResponse(
        Long id,
        String code,
        String status,
        Integer maxUses,
        int useCount,
        OffsetDateTime expiresAt,
        OffsetDateTime createdAt,
        OffsetDateTime lastUsedAt,
        List<AdminInviteCodeUsageResponse> usages
) {
}
