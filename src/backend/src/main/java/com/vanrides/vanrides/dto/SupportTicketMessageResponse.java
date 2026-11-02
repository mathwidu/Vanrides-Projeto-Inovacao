package com.vanrides.vanrides.dto;

import java.time.OffsetDateTime;

public record SupportTicketMessageResponse(
        Long id,
        String authorRole,
        Long authorUserId,
        String authorName,
        String message,
        OffsetDateTime createdAt
) {
}
