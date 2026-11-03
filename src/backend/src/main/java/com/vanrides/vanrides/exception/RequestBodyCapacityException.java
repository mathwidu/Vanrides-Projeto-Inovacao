package com.vanrides.vanrides.exception;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;

public final class RequestBodyCapacityException extends ApiStatusException {

    public static final String CODE = "REQUEST_BODY_CAPACITY";
    public static final String MESSAGE = "Capacidade temporária de leitura esgotada. Tente novamente.";

    private final HttpHeaders headers;

    public RequestBodyCapacityException() {
        super(HttpStatus.SERVICE_UNAVAILABLE, CODE, MESSAGE);
        HttpHeaders responseHeaders = new HttpHeaders();
        responseHeaders.set(HttpHeaders.RETRY_AFTER, "1");
        this.headers = HttpHeaders.readOnlyHttpHeaders(responseHeaders);
    }

    @Override
    public HttpHeaders getHeaders() {
        return headers;
    }
}
