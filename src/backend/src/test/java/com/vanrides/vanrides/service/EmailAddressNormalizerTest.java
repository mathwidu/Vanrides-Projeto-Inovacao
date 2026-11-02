package com.vanrides.vanrides.service;

import com.vanrides.vanrides.exception.ApiStatusException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class EmailAddressNormalizerTest {

    private final EmailAddressNormalizer normalizer = new EmailAddressNormalizer();

    @Test
    void shouldNormalizeOnlyAsciiBoundarySpacesAndCase() {
        assertThat(normalizer.normalizeRequired("  User.Name+Tag@EXAMPLE.COM  "))
                .isEqualTo("user.name+tag@example.com");
    }

    @Test
    void shouldUseLocaleRootEvenWhenDefaultLocaleIsTurkish() {
        Locale previous = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));

            assertThat(normalizer.normalizeRequired("I@EXAMPLE.COM"))
                    .isEqualTo("i@example.com");
        } finally {
            Locale.setDefault(previous);
        }
    }

    @Test
    void shouldAcceptCanonicalMaximumLength() {
        String email = "a".repeat(242) + "@example.com";

        assertThat(email).hasSize(254);
        assertThat(normalizer.normalizeRequired(email)).isEqualTo(email);
    }

    @ParameterizedTest
    @NullSource
    @ValueSource(strings = {
            "",
            "a@",
            "@b",
            "a@@b",
            "a b@example.com",
            "\tuser@example.com",
            "user@example.com\n",
            "user@example.com\u007f",
            "\u00a0user@example.com\u00a0",
            "usér@example.com"
    })
    void shouldRejectValuesOutsideCanonicalContract(String rawEmail) {
        assertThat(normalizer.tryNormalize(rawEmail)).isEmpty();
        assertThatThrownBy(() -> normalizer.normalizeRequired(rawEmail))
                .isInstanceOfSatisfying(ApiStatusException.class, exception -> {
                    assertThat(exception.getStatusCode().value()).isEqualTo(400);
                    assertThat(exception.getApiCode()).isEqualTo("INVALID_EMAIL");
                    assertThat(exception.getReason()).isEqualTo("Informe um e-mail válido.");
                });
    }

    @Test
    void shouldRejectValuesLongerThan254AsciiBytes() {
        String email = "a".repeat(243) + "@example.com";

        assertThat(email).hasSize(255);
        assertThat(normalizer.tryNormalize(email)).isEmpty();
    }
}
