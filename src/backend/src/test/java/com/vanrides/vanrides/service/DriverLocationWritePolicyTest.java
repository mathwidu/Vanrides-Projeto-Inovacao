package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class DriverLocationWritePolicyTest {

    private final DriverLocationWritePolicy policy = new DriverLocationWritePolicy();

    @Test
    void shouldAllowDriverToWriteOwnLocation() {
        assertThatCode(() -> policy.requireOwnDriver(4L, 4L))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldHideDifferentDriverIdentity() {
        assertThatThrownBy(() -> policy.requireOwnDriver(4L, 5L))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND);
                    assertThat(exception.getReason()).isEqualTo("Motorista não encontrado na empresa atual.");
                });
    }

    @Test
    void shouldRejectMissingAuthenticatedDriverIdentity() {
        assertThatThrownBy(() -> policy.requireOwnDriver(null, null))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND)
                );
    }
}
