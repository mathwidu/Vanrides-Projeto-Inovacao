package com.vanrides.vanrides.dto;

public class ApiErrorResponse {

    private final String code;
    private final String message;
    private final String email;

    public ApiErrorResponse(String code, String message, String email) {
        this.code = code;
        this.message = message;
        this.email = email;
    }

    public String getCode() {
        return code;
    }

    public String getMessage() {
        return message;
    }

    public String getEmail() {
        return email;
    }
}
