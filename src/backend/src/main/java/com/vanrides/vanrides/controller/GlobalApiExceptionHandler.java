package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.ApiErrorResponse;
import com.vanrides.vanrides.exception.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.server.ResponseStatusException;

@RestControllerAdvice
public class GlobalApiExceptionHandler {

    @ExceptionHandler(ResponseStatusException.class)
    public ResponseEntity<ApiErrorResponse> handleResponseStatusException(ResponseStatusException exception) {
        HttpStatus status = HttpStatus.resolve(exception.getStatusCode().value());
        String code = exception instanceof ApiStatusException apiStatusException
                ? apiStatusException.getApiCode()
                : status != null ? status.name() : String.valueOf(exception.getStatusCode().value());
        String message = exception.getReason();
        if (message == null || message.isBlank()) {
            message = status != null ? status.getReasonPhrase() : "Erro na requisição.";
        }

        return ResponseEntity
                .status(exception.getStatusCode())
                .headers(exception.getHeaders())
                .body(new ApiErrorResponse(code, message, null));
    }
}
