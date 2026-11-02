package com.vanrides.vanrides.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.dto.ApiErrorResponse;
import jakarta.servlet.http.HttpServletResponse;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.Objects;

public final class ApiErrorResponseWriter {

    private ApiErrorResponseWriter() {
    }

    public static void write(
            ObjectMapper objectMapper,
            HttpServletResponse response,
            int status,
            String code,
            String message
    ) throws IOException {
        Objects.requireNonNull(objectMapper, "objectMapper");
        Objects.requireNonNull(response, "response");

        byte[] payload = objectMapper.writeValueAsBytes(
                new ApiErrorResponse(code, message, null)
        );
        response.setStatus(status);
        response.setContentType("application/json");
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentLength(payload.length);
        response.getOutputStream().write(payload);
    }
}
