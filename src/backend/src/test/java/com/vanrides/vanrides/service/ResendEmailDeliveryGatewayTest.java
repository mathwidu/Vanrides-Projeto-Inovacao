package com.vanrides.vanrides.service;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ResendEmailDeliveryGatewayTest {

    @Test
    void shouldSanitizeInvisibleBoundaryCharactersFromApiKey() {
        String sanitized = ResendEmailDeliveryGateway.sanitizeApiKeyForHeader(" \nre_test_key\u2028\r");

        assertThat(sanitized).isEqualTo("re_test_key");
    }

    @Test
    void shouldRejectUnsafeCharactersInsideApiKey() {
        assertThatThrownBy(() -> ResendEmailDeliveryGateway.sanitizeApiKeyForHeader("re_test\u2028_key"))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("caracteres invalidos");
    }
}
