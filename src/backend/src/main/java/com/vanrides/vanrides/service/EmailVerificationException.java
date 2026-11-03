package com.vanrides.vanrides.service;

public class EmailVerificationException extends RuntimeException {

    private final String code;
    private final String email;
    private final int httpStatus;

    public EmailVerificationException(String code, String message, String email, int httpStatus) {
        super(message);
        this.code = code;
        this.email = email;
        this.httpStatus = httpStatus;
    }

    public String getCode() {
        return code;
    }

    public String getEmail() {
        return email;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
