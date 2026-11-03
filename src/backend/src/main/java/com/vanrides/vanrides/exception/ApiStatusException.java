package com.vanrides.vanrides.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public class ApiStatusException extends ResponseStatusException {

    private final String apiCode;

    public ApiStatusException(HttpStatus status, String apiCode, String reason) {
        super(status, reason);
        this.apiCode = apiCode;
    }

    public String getApiCode() {
        return apiCode;
    }
}
