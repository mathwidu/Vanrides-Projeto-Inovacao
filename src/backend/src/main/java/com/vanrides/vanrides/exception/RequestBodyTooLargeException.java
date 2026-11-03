package com.vanrides.vanrides.exception;

import org.springframework.http.HttpStatus;

public final class RequestBodyTooLargeException extends ApiStatusException {

    public static final String CODE = "PAYLOAD_TOO_LARGE";
    public static final String MESSAGE = "Corpo da requisição excede o limite permitido.";

    public RequestBodyTooLargeException() {
        super(HttpStatus.PAYLOAD_TOO_LARGE, CODE, MESSAGE);
    }
}
