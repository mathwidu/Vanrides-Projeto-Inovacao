package com.vanrides.vanrides.service;

import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public final class DriverLocationWritePolicy {

    private static final String NOT_FOUND_MESSAGE = "Motorista não encontrado na empresa atual.";

    public void requireOwnDriver(Long authenticatedDriverId, Long targetDriverId) {
        if (authenticatedDriverId == null || !authenticatedDriverId.equals(targetDriverId)) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, NOT_FOUND_MESSAGE);
        }
    }
}
