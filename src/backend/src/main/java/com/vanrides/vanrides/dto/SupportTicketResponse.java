package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;
import java.util.List;

public record SupportTicketResponse(
        Long id,
        String status,
        String category,
        String subject,
        String createdByRole,
        Long createdByUserId,
        String createdByName,
        OffsetDateTime createdAt,
        OffsetDateTime updatedAt,
        OffsetDateTime resolvedAt,
        List<SupportTicketMessageResponse> messages
) {
}
