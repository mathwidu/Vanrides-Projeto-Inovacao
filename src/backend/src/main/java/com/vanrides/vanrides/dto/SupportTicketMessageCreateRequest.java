package com.vanrides.vanrides.dto;

public record SupportTicketMessageCreateRequest(
        String message,
        String status
) {
}
