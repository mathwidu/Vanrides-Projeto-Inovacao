package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminInviteCodeUsageResponse(
        Long id,
        Long passageiroId,
        String passageiroNome,
        String passageiroEmail,
        OffsetDateTime usedAt,
        String accountStatus
) {
}
