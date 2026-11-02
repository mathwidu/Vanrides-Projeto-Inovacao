package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record DriverInvitePreviewResponse(
        Long companyId,
        String companyName,
        String role,
        String status,
        OffsetDateTime expiresAt
) {
}
