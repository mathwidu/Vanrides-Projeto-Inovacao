package com.vanrides.vanrides.whatsapp.config;

import java.net.URI;
import java.time.Instant;
import java.util.Base64;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

/** No secret-bearing toString; no mutable global defaults or cross-environment fallback. */
@Component
public final class WaSettings {
    private final boolean enabled;
    private final boolean teamApiOnly;
    private final boolean mvpOnline;
    private final boolean newStartsEnabled;
    private final boolean cohortOnly;
    private final boolean approachEnabled;
    private final Set<UUID> approachReviewedRoutines;
    private final String environment;
    private final String baseUrl;
    private final String startLinkBaseUrl;
    private final byte[] hashKey;
    private final byte[] responseKey;
    private final String operatorCredential;
    private final Instant operatorExpiresAt;

    public WaSettings(Environment env) {
        enabled = flag(env, "enabled", false);
        teamApiOnly = com.vanrides.vanrides.config.TeamApiMode.enabled(env);
        mvpOnline = com.vanrides.vanrides.config.MvpOnlineMode.enabled(env);
        newStartsEnabled = flag(env, "new-starts-enabled", true);
        cohortOnly = flag(env, "test-cohort-only", true);
        approachEnabled = flag(env, "approach-enabled", false);
        try {
            String reviewed = env.getProperty("vanrides.wa.approach-reviewed-routine-ids", "");
            approachReviewedRoutines = reviewed.isBlank() ? Set.of() : java.util.Arrays.stream(reviewed.split(",", -1))
                .map(String::trim).map(UUID::fromString).collect(Collectors.toUnmodifiableSet());
        } catch (RuntimeException invalid) { throw new IllegalStateException("WA_CONFIGURATION_INVALID"); }
        environment = env.getProperty("vanrides.wa.environment", "disabled");
        baseUrl = env.getProperty("vanrides.wa.public-base-url", "");
        startLinkBaseUrl = env.getProperty("vanrides.wa.start-link-base-url", "");
        if (!startLinkBaseUrl.isEmpty()) {
            try {
                URI link = URI.create(startLinkBaseUrl);
                if (!"https".equals(link.getScheme()) || link.getHost() == null || link.getUserInfo() != null
                        || link.getQuery() != null || link.getFragment() != null || !link.getPath().isEmpty()
                        || link.getPort() == 0 || link.getPort() > 65535) invalid();
            } catch (RuntimeException e) { throw new IllegalStateException("WA_CONFIGURATION_INVALID"); }
        }
        String h = env.getProperty("vanrides.wa.hash-key", "");
        String r = env.getProperty("vanrides.wa.response-key", "");
        operatorCredential = env.getProperty("vanrides.wa.operator-credential", "");
        String expires = env.getProperty("vanrides.wa.operator-expires-at", "");
        boolean configured = enabled || !h.isEmpty() || !r.isEmpty() || !operatorCredential.isEmpty();
        if (!configured) {
            hashKey = new byte[0]; responseKey = new byte[0]; operatorExpiresAt = Instant.EPOCH;
            return;
        }
        try {
            if (!environment.matches("[a-z][a-z0-9_-]{0,31}") || environment.equals("disabled")) invalid();
            URI uri = URI.create(baseUrl);
            boolean local = Set.of("127.0.0.1", "localhost", "[::1]").contains(uri.getHost());
            if (uri.getHost() == null || uri.getUserInfo() != null || uri.getQuery() != null
                    || uri.getFragment() != null || !uri.getPath().isEmpty()
                    || !("https".equals(uri.getScheme()) || "http".equals(uri.getScheme()) && local)) invalid();
            hashKey = key(h); responseKey = key(r);
            if (h.equals(r)) invalid();
            key(operatorCredential);
            operatorExpiresAt = Instant.parse(expires);
        } catch (RuntimeException e) { throw new IllegalStateException("WA_CONFIGURATION_INVALID"); }
    }
    private static void invalid() { throw new IllegalArgumentException("WA_CONFIGURATION_INVALID"); }
    private static boolean flag(Environment env, String key, boolean fallback) {
        String value = env.getProperty("vanrides.wa." + key, Boolean.toString(fallback));
        if (!Set.of("true", "false").contains(value)) { invalid(); }
        return Boolean.parseBoolean(value);
    }
    private static byte[] key(String value) {
        if (!value.matches("[A-Za-z0-9_-]{43}")) { invalid(); }
        byte[] bytes = Base64.getUrlDecoder().decode(value);
        if (bytes.length != 32 || !Base64.getUrlEncoder().withoutPadding().encodeToString(bytes).equals(value)) invalid();
        return bytes;
    }
    public boolean enabled() { return enabled; }
    public boolean teamApiOnly() { return teamApiOnly; }
    public boolean mvpOnline() { return mvpOnline; }
    public boolean publishedMvp() { return teamApiOnly || mvpOnline; }
    public boolean newStartsEnabled() { return enabled && newStartsEnabled; }
    public boolean cohortOnly() { return cohortOnly; }
    public boolean approachEnabled() { return enabled && approachEnabled; }
    public boolean approachReviewed(UUID routine) { return approachReviewedRoutines.contains(routine); }
    public boolean configured() { return hashKey.length == 32 && responseKey.length == 32; }
    public String environment() { return environment; }
    public String baseUrl() { return baseUrl; }
    public String startLinkBaseUrl() { return startLinkBaseUrl; }
    public byte[] hashKey() { return hashKey.clone(); }
    public byte[] responseKey() { return responseKey.clone(); }
    public String operatorCredential() { return operatorCredential; }
    public Instant operatorExpiresAt() { return operatorExpiresAt; }
}
