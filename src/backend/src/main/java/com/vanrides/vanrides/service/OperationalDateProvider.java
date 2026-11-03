package com.vanrides.vanrides.service;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Objects;

@Component
public class OperationalDateProvider {

    private static final String CONFIGURATION_ERROR =
            "vanrides.operations.time-zone deve ser um fuso IANA válido e não vazio.";

    private final Clock clock;

    @Autowired
    public OperationalDateProvider(
            @Value("${vanrides.operations.time-zone:America/Sao_Paulo}") String configuredTimeZone) {
        this(Clock.system(resolveZone(configuredTimeZone)));
    }

    OperationalDateProvider(Clock clock) {
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public LocalDate today() {
        return LocalDate.now(clock);
    }

    private static ZoneId resolveZone(String configuredTimeZone) {
        if (configuredTimeZone == null || configuredTimeZone.isBlank()) {
            throw new IllegalArgumentException(CONFIGURATION_ERROR);
        }
        try {
            return ZoneId.of(configuredTimeZone.trim());
        } catch (DateTimeException exception) {
            throw new IllegalArgumentException(CONFIGURATION_ERROR, exception);
        }
    }
}
