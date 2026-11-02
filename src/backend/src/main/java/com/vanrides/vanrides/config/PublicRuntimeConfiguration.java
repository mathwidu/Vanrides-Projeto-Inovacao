package com.vanrides.vanrides.config;

import org.springframework.core.env.Environment;

import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;

final class PublicRuntimeConfiguration {
    private final List<String> activeProfiles;
    private final String jwtSecret;
    private final boolean basicAuthEnabled;
    private final String emailProvider;
    private final String emailFrom;
    private final String resendApiKey;
    private final String emailVerificationPublicBaseUrl;
    private final String qaAutoVerifyEmailDomains;
    private final String routingProvider;
    private final String googleApiKey;
    private final boolean mapsPlatformEnabled;
    private final String mapsPlatformBaseUrl;
    private final boolean rateLimitEnabled;
    private final boolean http2Enabled;

    private PublicRuntimeConfiguration(Builder builder) {
        this.activeProfiles = List.copyOf(builder.activeProfiles);
        this.jwtSecret = builder.jwtSecret;
        this.basicAuthEnabled = builder.basicAuthEnabled;
        this.emailProvider = builder.emailProvider;
        this.emailFrom = builder.emailFrom;
        this.resendApiKey = builder.resendApiKey;
        this.emailVerificationPublicBaseUrl = builder.emailVerificationPublicBaseUrl;
        this.qaAutoVerifyEmailDomains = builder.qaAutoVerifyEmailDomains;
        this.routingProvider = builder.routingProvider;
        this.googleApiKey = builder.googleApiKey;
        this.mapsPlatformEnabled = builder.mapsPlatformEnabled;
        this.mapsPlatformBaseUrl = builder.mapsPlatformBaseUrl;
        this.rateLimitEnabled = builder.rateLimitEnabled;
        this.http2Enabled = builder.http2Enabled;
    }

    static PublicRuntimeConfiguration from(Environment environment) {
        LinkedHashSet<String> profiles = new LinkedHashSet<>();
        profiles.addAll(Arrays.asList(environment.getActiveProfiles()));
        profiles.addAll(Arrays.asList(environment.getDefaultProfiles()));

        return builder()
                .activeProfiles(profiles.stream().filter(profile -> !isBlank(profile)).toList())
                .jwtSecret(environment.getProperty("vanrides.auth.jwt-secret", ""))
                .basicAuthEnabled(environment.getProperty("vanrides.auth.basic-enabled", Boolean.class, true))
                .emailProvider(environment.getProperty("vanrides.email.provider", "log"))
                .emailFrom(environment.getProperty("vanrides.email.from", ""))
                .resendApiKey(environment.getProperty("vanrides.email.resend.api-key", ""))
                .emailVerificationPublicBaseUrl(environment.getProperty("vanrides.email.verification.public-base-url", ""))
                .qaAutoVerifyEmailDomains(environment.getProperty("vanrides.qa.auto-verify-email-domains", ""))
                .routingProvider(environment.getProperty("vanrides.routing.provider", "static"))
                .googleApiKey(environment.getProperty("google.api.key", ""))
                .mapsPlatformEnabled(environment.getProperty("vanrides.maps-platform.enabled", Boolean.class, false))
                .mapsPlatformBaseUrl(environment.getProperty("vanrides.maps-platform.base-url", ""))
                .rateLimitEnabled(environment.getProperty("vanrides.rate-limit.enabled", Boolean.class, true))
                .http2Enabled(environment.getProperty("server.http2.enabled", Boolean.class, false))
                .build();
    }

    static Builder builder() {
        return new Builder();
    }

    boolean isPublicEnvironment() {
        return activeProfiles.stream()
                .map(profile -> normalize(profile, ""))
                .anyMatch(PublicRuntimeConfigurationGuard.PUBLIC_PROFILES::contains);
    }

    boolean isProdLikeEnvironment() {
        return activeProfiles.stream()
                .map(profile -> normalize(profile, ""))
                .anyMatch(PublicRuntimeConfigurationGuard.PROD_LIKE_PROFILES::contains);
    }

    String jwtSecret() {
        return jwtSecret;
    }

    boolean basicAuthEnabled() {
        return basicAuthEnabled;
    }

    String emailProvider() {
        return emailProvider;
    }

    String emailFrom() {
        return emailFrom;
    }

    String resendApiKey() {
        return resendApiKey;
    }

    String emailVerificationPublicBaseUrl() {
        return emailVerificationPublicBaseUrl;
    }

    String qaAutoVerifyEmailDomains() {
        return qaAutoVerifyEmailDomains;
    }

    String routingProvider() {
        return routingProvider;
    }

    String googleApiKey() {
        return googleApiKey;
    }

    boolean mapsPlatformEnabled() {
        return mapsPlatformEnabled;
    }

    String mapsPlatformBaseUrl() {
        return mapsPlatformBaseUrl;
    }

    boolean rateLimitEnabled() {
        return rateLimitEnabled;
    }

    boolean http2Enabled() {
        return http2Enabled;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    private static String normalize(String value, String defaultValue) {
        if (value == null || value.trim().isEmpty()) {
            return defaultValue;
        }
        return value.trim().toLowerCase(Locale.ROOT);
    }

    static final class Builder {
        private List<String> activeProfiles = List.of("local");
        private String jwtSecret = "";
        private boolean basicAuthEnabled = true;
        private String emailProvider = "log";
        private String emailFrom = "";
        private String resendApiKey = "";
        private String emailVerificationPublicBaseUrl = "";
        private String qaAutoVerifyEmailDomains = "";
        private String routingProvider = "static";
        private String googleApiKey = "";
        private boolean mapsPlatformEnabled = false;
        private String mapsPlatformBaseUrl = "";
        private boolean rateLimitEnabled = true;
        private boolean http2Enabled = false;

        Builder activeProfiles(List<String> activeProfiles) {
            this.activeProfiles = activeProfiles == null ? List.of() : activeProfiles;
            return this;
        }

        Builder jwtSecret(String jwtSecret) {
            this.jwtSecret = jwtSecret;
            return this;
        }

        Builder basicAuthEnabled(boolean basicAuthEnabled) {
            this.basicAuthEnabled = basicAuthEnabled;
            return this;
        }

        Builder emailProvider(String emailProvider) {
            this.emailProvider = emailProvider;
            return this;
        }

        Builder emailFrom(String emailFrom) {
            this.emailFrom = emailFrom;
            return this;
        }

        Builder resendApiKey(String resendApiKey) {
            this.resendApiKey = resendApiKey;
            return this;
        }

        Builder emailVerificationPublicBaseUrl(String emailVerificationPublicBaseUrl) {
            this.emailVerificationPublicBaseUrl = emailVerificationPublicBaseUrl;
            return this;
        }

        Builder qaAutoVerifyEmailDomains(String qaAutoVerifyEmailDomains) {
            this.qaAutoVerifyEmailDomains = qaAutoVerifyEmailDomains;
            return this;
        }

        Builder routingProvider(String routingProvider) {
            this.routingProvider = routingProvider;
            return this;
        }

        Builder googleApiKey(String googleApiKey) {
            this.googleApiKey = googleApiKey;
            return this;
        }

        Builder mapsPlatformEnabled(boolean mapsPlatformEnabled) {
            this.mapsPlatformEnabled = mapsPlatformEnabled;
            return this;
        }

        Builder mapsPlatformBaseUrl(String mapsPlatformBaseUrl) {
            this.mapsPlatformBaseUrl = mapsPlatformBaseUrl;
            return this;
        }

        Builder rateLimitEnabled(boolean rateLimitEnabled) {
            this.rateLimitEnabled = rateLimitEnabled;
            return this;
        }

        Builder http2Enabled(boolean http2Enabled) {
            this.http2Enabled = http2Enabled;
            return this;
        }

        PublicRuntimeConfiguration build() {
            return new PublicRuntimeConfiguration(this);
        }
    }
}
