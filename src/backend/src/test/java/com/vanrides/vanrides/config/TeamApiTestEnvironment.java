package com.vanrides.vanrides.config;

import org.springframework.mock.env.MockEnvironment;

/** Synthetic public configuration, never a source of runtime credentials. */
public final class TeamApiTestEnvironment {
    private TeamApiTestEnvironment() { }
    public static MockEnvironment validOnline() {
        return valid().withProperty("vanrides.wa.team-api-only","false").withProperty("vanrides.wa.mvp-online","true")
            .withProperty("vanrides.wa.environment",MvpOnlineMode.ENVIRONMENT).withProperty("vanrides.wa.new-starts-enabled","true")
            .withProperty("vanrides.wa.start-link-base-url","https://vanrides-inovacao-api.matheusduarte.dev.br");
    }
    public static MockEnvironment valid() {
        var env=new MockEnvironment().withProperty("vanrides.wa.team-api-only","true")
            .withProperty("vanrides.email.provider","disabled").withProperty("vanrides.wa.environment",TeamApiMode.ENVIRONMENT)
            .withProperty("vanrides.wa.enabled","true").withProperty("vanrides.wa.new-starts-enabled","false")
            .withProperty("vanrides.wa.approach-enabled","false").withProperty("vanrides.wa.test-cohort-only","true")
            .withProperty("vanrides.wa.public-base-url","https://vanrides-inovacao-api.matheusduarte.dev.br")
            .withProperty("vanrides.auth.jwt-secret","synthetic-team-api-test-secret-at-least-32-characters")
            .withProperty("vanrides.auth.basic-enabled","false").withProperty("vanrides.email.verification.public-base-url","")
            .withProperty("vanrides.wa.hash-key",key(1)).withProperty("vanrides.wa.response-key",key(2))
            .withProperty("vanrides.wa.operator-credential",key(3)).withProperty("vanrides.wa.operator-expires-at","2035-01-01T00:00:00Z");
        env.setActiveProfiles("homolog");return env;
    }
    private static String key(int value) { byte[] bytes=new byte[32];java.util.Arrays.fill(bytes,(byte)value);return java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes); }
}
