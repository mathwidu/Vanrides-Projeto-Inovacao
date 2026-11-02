package com.vanrides.vanrides.dto;

public record PendingEmailChangeRequest(
        String email,
        String senha,
        String newEmail
) {
}
