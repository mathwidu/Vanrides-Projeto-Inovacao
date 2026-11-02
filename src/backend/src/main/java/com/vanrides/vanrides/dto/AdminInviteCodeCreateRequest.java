package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record AdminInviteCodeCreateRequest(
        OffsetDateTime expiresAt,
        Integer maxUses
) {
}
