package com.vanrides.vanrides.dto;

public class EmailVerificationResponse {

    private final String message;
    private final String email;

    public EmailVerificationResponse(String message, String email) {
        this.message = message;
        this.email = email;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }
}
