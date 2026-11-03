package com.vanrides.vanrides.config;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class PublicRuntimeConfigurationGuardTest {

    @Test
    void shouldAllowLocalWithDeveloperDefaults() {
        PublicRuntimeConfiguration configuration = configurationBuilder()
                .activeProfiles(List.of("local"))
                .jwtSecret("")
                .basicAuthEnabled(true)
                .emailProvider("log")
                .routingProvider("static")
                .build();

        assertThatCode(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectHttp2UntilUnreadBodyClosureSupportsStreams() {
        PublicRuntimeConfiguration configuration = configurationBuilder()
                .activeProfiles(List.of("local"))
                .http2Enabled(true)
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("SERVER_HTTP2_ENABLED")
                .hasMessageContaining("HTTP/1.1");
    }

    @Test
    void shouldRejectDevWithoutStrongJwtSecret() {
        PublicRuntimeConfiguration configuration = configurationBuilder()
                .activeProfiles(List.of("dev"))
                .jwtSecret("change-me")
                .basicAuthEnabled(true)
                .emailProvider("log")
                .routingProvider("static")
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_JWT_SECRET")
                .hasMessageNotContaining("change-me");
    }

    @Test
    void shouldRejectPublishedEnvironmentWithRateLimitDisabled() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .rateLimitEnabled(false)
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_RATE_LIMIT_ENABLED=true");
    }

    @Test
    void shouldRejectProdWithBasicAuthEnabled() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("prod"))
                .basicAuthEnabled(true)
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_AUTH_BASIC_ENABLED=false");
    }

    @Test
    void shouldRejectProdWithQaAutoVerifyDomains() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("prod"))
                .qaAutoVerifyEmailDomains("vanrides.dev")
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS")
                .hasMessageContaining("PROD");
    }

    @Test
    void shouldRejectResendProviderWithoutRequiredSettings() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .emailProvider("resend")
                .emailFrom("")
                .resendApiKey("")
                .emailVerificationPublicBaseUrl("http://localhost:8080/auth/email-verifications/confirm")
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_EMAIL_FROM")
                .hasMessageContaining("VANRIDES_EMAIL_RESEND_API_KEY")
                .hasMessageContaining("VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL")
                .hasMessageNotContaining("localhost:8080/auth/email-verifications/confirm");
    }

    @Test
    void shouldRejectGoogleDirectionsWithoutApiKeyInPublishedEnvironment() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .routingProvider("google-directions")
                .googleApiKey("")
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("GOOGLE_API_KEY");
    }

    @Test
    void shouldRejectMapsPlatformEnabledWithLoopbackUrlInPublishedEnvironment() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .mapsPlatformEnabled(true)
                .mapsPlatformBaseUrl("http://127.0.0.1:18080")
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_MAPS_PLATFORM_BASE_URL")
                .hasMessageNotContaining("127.0.0.1:18080");
    }

    @Test
    void shouldAllowMapsPlatformEnabledWithInternalServiceUrlInPublishedEnvironment() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .mapsPlatformEnabled(true)
                .mapsPlatformBaseUrl("http://vanrides-maps-platform:18080")
                .build();

        assertThatCode(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldAllowDevWithPublishedSettingsAndQaAutoVerify() {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .activeProfiles(List.of("dev"))
                .basicAuthEnabled(true)
                .qaAutoVerifyEmailDomains("vanrides.dev")
                .emailProvider("resend")
                .routingProvider("google-directions")
                .googleApiKey("valid-google-api-key")
                .build();

        assertThatCode(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .doesNotThrowAnyException();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "https://user:password@api.vanrides.test/auth/email-verifications/confirm",
            "https://api.vanrides.test/auth/email-verifications/confirm?source=email",
            "https://api.vanrides.test/auth/email-verifications/confirm#token=secret",
            "https://api.vanrides.test/auth/email-verifications/confirm/",
            "https://api.vanrides.test/auth/email-verifications/wrong",
            "https://login.localhost/auth/email-verifications/confirm",
            "https://localhost./auth/email-verifications/confirm",
            "https://127.0.0.2/auth/email-verifications/confirm",
            "https://[::1]/auth/email-verifications/confirm"
    })
    void shouldRejectUnsafePublishedEmailVerificationBaseUrl(String baseUrl) {
        PublicRuntimeConfiguration configuration = validPublishedConfigurationBuilder()
                .emailVerificationPublicBaseUrl(baseUrl)
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL")
                .hasMessageNotContaining(baseUrl);
    }

    @Test
    void shouldReportAllPublishedConfigurationProblemsTogether() {
        PublicRuntimeConfiguration configuration = configurationBuilder()
                .activeProfiles(List.of("prod"))
                .jwtSecret("")
                .basicAuthEnabled(true)
                .emailProvider("resend")
                .emailFrom("")
                .resendApiKey("")
                .emailVerificationPublicBaseUrl("http://localhost:8080/auth/email-verifications/confirm")
                .qaAutoVerifyEmailDomains("vanrides.dev")
                .routingProvider("google-directions")
                .googleApiKey("")
                .rateLimitEnabled(false)
                .build();

        assertThatThrownBy(() -> PublicRuntimeConfigurationGuard.validate(configuration))
                .isInstanceOf(IllegalStateException.class)
                .satisfies(error -> {
                    assertThat(error.getMessage()).contains("VANRIDES_JWT_SECRET");
                    assertThat(error.getMessage()).contains("VANRIDES_RATE_LIMIT_ENABLED=true");
                    assertThat(error.getMessage()).contains("VANRIDES_AUTH_BASIC_ENABLED=false");
                    assertThat(error.getMessage()).contains("VANRIDES_EMAIL_FROM");
                    assertThat(error.getMessage()).contains("VANRIDES_EMAIL_RESEND_API_KEY");
                    assertThat(error.getMessage()).contains("VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL");
                    assertThat(error.getMessage()).contains("VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS");
                    assertThat(error.getMessage()).contains("GOOGLE_API_KEY");
                });
    }

    private static PublicRuntimeConfiguration.Builder validPublishedConfigurationBuilder() {
        return configurationBuilder()
                .activeProfiles(List.of("prod"))
                .jwtSecret("0123456789abcdef0123456789abcdef")
                .basicAuthEnabled(false)
                .emailProvider("resend")
                .emailFrom("VanRides <noreply@notifications.matheusduarte.dev.br>")
                .resendApiKey("re_0123456789abcdef")
                .emailVerificationPublicBaseUrl("https://api.vanrides.matheusduarte.dev.br/auth/email-verifications/confirm")
                .qaAutoVerifyEmailDomains("")
                .routingProvider("static")
                .googleApiKey("");
    }

    private static PublicRuntimeConfiguration.Builder configurationBuilder() {
        return PublicRuntimeConfiguration.builder();
    }
}
