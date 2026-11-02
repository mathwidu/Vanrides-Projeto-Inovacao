package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record SupportTicketSummaryResponse(
        Long id,
        String status,
        String category,
        String subject,
        String createdByRole,
        Long createdByUserId,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt
) {
}
