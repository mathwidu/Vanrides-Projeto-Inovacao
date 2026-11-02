package com.vanrides.vanrides.dto;

public record SupportTicketCreateRequest(
        String category,
        String subject,
        String message
) {
}
