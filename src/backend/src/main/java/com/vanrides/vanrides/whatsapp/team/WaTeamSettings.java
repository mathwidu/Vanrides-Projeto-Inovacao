package com.vanrides.vanrides.whatsapp.team;

import com.vanrides.vanrides.config.MvpOnlineMode;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.util.UUID;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocketFactory;
import javax.net.ssl.TrustManagerFactory;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Component;

@Component
public class WaTeamSettings {
    private static final String PREFIX = "VANRIDES_WA_TEAM_WEB_";
    private final boolean enabled;
    private final boolean strictIngress;
    private String origin;
    private UUID cohort;
    private String instance;
    private String clientId;
    private String clientSecret;
    private String installationId;
    private URI evolutionOrigin;
    private String evolutionKey;
    private SSLSocketFactory evolutionTls;

    public WaTeamSettings(Environment env) {
        enabled = flag(env, "ENABLED");
        strictIngress = flag(env, "STRICT_INGRESS");
        if (strictIngress && !enabled) throw invalid("STRICT_INGRESS");
        if (!enabled) return;
        if (!MvpOnlineMode.enabled(env)) throw invalid("ENABLED");
        MvpOnlineMode.validate(env);
        origin = "https://" + URI.create(env.getRequiredProperty("vanrides.wa.public-base-url")).getHost();
        cohort = cohort(env);
        instance = required(env, "INSTANCE", "[A-Za-z0-9_-]{1,80}");
        clientId = required(env, "GITHUB_CLIENT_ID", "[A-Za-z0-9_]{8,80}");
        installationId = required(env, "GITHUB_INSTALLATION_ID", "[1-9][0-9]{0,17}");
        clientSecret = secret(env, "GITHUB_CLIENT_SECRET_FILE");
        evolutionOrigin = evolutionOrigin(env);
        evolutionKey = secret(env, "EVOLUTION_KEY_FILE");
        evolutionTls = tls(env);
    }

    private static UUID cohort(Environment env) {
        try {
            String value = required(env, "COHORT_ID", "[0-9a-f-]{36}");
            UUID id = UUID.fromString(value);
            if (id.toString().equals(value) && !id.equals(new UUID(0, 0))) return id;
        } catch (IllegalArgumentException ignored) { }
        throw invalid("COHORT_ID");
    }

    private static URI evolutionOrigin(Environment env) {
        try {
            URI uri = URI.create(required(env, "EVOLUTION_ORIGIN", "[^\\s]{1,255}"));
            if ("https".equals(uri.getScheme()) && uri.getHost() != null && uri.getRawUserInfo() == null
                    && uri.getRawQuery() == null && uri.getRawFragment() == null && uri.getRawPath().isEmpty()
                    && (uri.getPort() == -1 || uri.getPort() > 0 && uri.getPort() <= 65535)) return uri;
        } catch (IllegalArgumentException ignored) { }
        throw invalid("EVOLUTION_ORIGIN");
    }

    private static boolean flag(Environment env, String name) {
        return switch (env.getProperty(PREFIX + name, "false")) {
            case "true" -> true;
            case "false" -> false;
            default -> throw invalid(name);
        };
    }

    private static String required(Environment env, String name, String pattern) {
        String value = env.getProperty(PREFIX + name, "");
        if (!value.matches(pattern)) throw invalid(name);
        return value;
    }

    private static byte[] file(Environment env, String name, int maximum) {
        try {
            Path path = Path.of(env.getProperty(PREFIX + name, ""));
            if (path.isAbsolute() && Files.isRegularFile(path)) {
                try (InputStream input = Files.newInputStream(path)) {
                    byte[] bytes = input.readNBytes(maximum + 1);
                    if (bytes.length > 0 && bytes.length <= maximum) return bytes;
                }
            }
        } catch (Exception ignored) { }
        throw invalid(name);
    }

    private static String secret(Environment env, String name) {
        String value = new String(file(env, name, 512), StandardCharsets.UTF_8).stripTrailing();
        if (!value.matches("[!-~]{16,256}")) throw invalid(name);
        return value;
    }

    private static SSLSocketFactory tls(Environment env) {
        String name = "EVOLUTION_CA_FILE";
        byte[] bytes = file(env, name, 16384);
        try {
            var input = new java.io.ByteArrayInputStream(bytes);
            var chain = CertificateFactory.getInstance("X.509").generateCertificates(input);
            if (chain.size() != 1) throw invalid(name);
            var ca = (X509Certificate) chain.iterator().next();
            ca.checkValidity();
            if (ca.getBasicConstraints() < 0) throw invalid(name);
            KeyStore trust = KeyStore.getInstance(KeyStore.getDefaultType());
            trust.load(null, null);
            trust.setCertificateEntry("evolution", ca);
            TrustManagerFactory managers = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            managers.init(trust);
            SSLContext context = SSLContext.getInstance("TLS");
            context.init(null, managers.getTrustManagers(), null);
            return context.getSocketFactory();
        } catch (Exception invalid) { throw invalid(name); }
    }

    private static IllegalStateException invalid(String name) {
        return new IllegalStateException("WA_TEAM_WEB_CONFIGURATION_INVALID: " + PREFIX + name);
    }

    public boolean enabled() { return enabled; }
    public boolean strictIngress(String channel) { return strictIngress && instance.equals(channel); }
    public UUID cohort() { return cohort; }
    public String origin() { return origin; }
    String instance() { return instance; }
    String callback() { return origin + "/equipe/callback"; }
    String clientId() { return clientId; }
    String clientSecret() { return clientSecret; }
    String installationId() { return installationId; }
    URI evolutionOrigin() { return evolutionOrigin; }
    String evolutionKey() { return evolutionKey; }
    SSLSocketFactory evolutionTls() { return evolutionTls; }
}
