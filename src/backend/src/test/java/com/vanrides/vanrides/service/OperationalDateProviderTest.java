package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.core.env.MapPropertySource;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class OperationalDateProviderTest {

    @Test
    void shouldKeepSaoPauloOperationalDateWhenUtcIsAlreadyNextDay() {
        Clock clock = Clock.fixed(
                Instant.parse("2026-07-17T02:30:00Z"),
                ZoneId.of("America/Sao_Paulo")
        );

        assertThat(new OperationalDateProvider(clock).today())
                .isEqualTo(LocalDate.of(2026, 7, 16));
    }

    @Test
    void shouldRejectBlankOrInvalidConfiguredTimeZone() {
        assertThatThrownBy(() -> new OperationalDateProvider(" "))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vanrides.operations.time-zone deve ser um fuso IANA válido e não vazio.");
        assertThatThrownBy(() -> new OperationalDateProvider("Mars/Olympus_Mons"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("vanrides.operations.time-zone deve ser um fuso IANA válido e não vazio.");
    }

    @ParameterizedTest
    @ValueSource(strings = {" ", "Mars/Olympus_Mons"})
    void shouldFailSpringContextStartupForInvalidConfiguredTimeZone(String configuredTimeZone) {
        try (AnnotationConfigApplicationContext context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "operational-date-test",
                    Map.of("vanrides.operations.time-zone", configuredTimeZone)
            ));
            context.register(OperationalDateProvider.class);

            assertThatThrownBy(context::refresh)
                    .isInstanceOf(org.springframework.beans.factory.BeanCreationException.class)
                    .hasStackTraceContaining(
                            "java.lang.IllegalArgumentException: "
                                    + "vanrides.operations.time-zone deve ser um fuso IANA válido e não vazio."
                    );
        }
    }
}
