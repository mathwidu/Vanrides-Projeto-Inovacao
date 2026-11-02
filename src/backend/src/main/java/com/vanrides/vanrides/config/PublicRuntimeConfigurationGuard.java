package com.vanrides.vanrides.config;

import org.springframework.beans.BeansException;
import org.springframework.beans.factory.config.BeanFactoryPostProcessor;
import org.springframework.beans.factory.config.ConfigurableListableBeanFactory;
import org.springframework.core.env.Environment;
import org.springframework.context.EnvironmentAware;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Component
public class PublicRuntimeConfigurationGuard implements BeanFactoryPostProcessor, EnvironmentAware {

    private static final String EMAIL_CONFIRMATION_PATH =
            "/auth/email-verifications/confirm";

    static final Set<String> PUBLIC_PROFILES = Set.of(
            "dev",
            "prod",
            "production",
            "staging",
            "stage",
            "homolog",
            "homologacao"
    );

    static final Set<String> PROD_LIKE_PROFILES = Set.of(
            "prod",
            "production",
            "staging",
            "stage",
            "homolog",
            "homologacao"
    );

    private static final Set<String> WEAK_JWT_SECRETS = Set.of(
            "change-me",
            "changeme",
            "secret",
            "jwt-secret",
            "vanrides",
            "password",
            "default"
    );

    private Environment environment;

    @Override
    public void setEnvironment(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void postProcessBeanFactory(ConfigurableListableBeanFactory beanFactory) throws BeansException {
        validate(PublicRuntimeConfiguration.from(environment));
    }

    static void validate(PublicRuntimeConfiguration configuration) {
        if (configuration.http2Enabled()) {
            throw new IllegalStateException(
                    "Configuracao publica invalida: SERVER_HTTP2_ENABLED deve permanecer false "
                            + "enquanto o fechamento de request body nao lido suportar somente HTTP/1.1."
            );
        }

        if (!configuration.isPublicEnvironment()) {
            return;
        }

        List<String> failures = new ArrayList<>();
        if (!isStrongJwtSecret(configuration.jwtSecret())) {
            failures.add("VANRIDES_JWT_SECRET deve estar definido com pelo menos 32 caracteres em DEV/prod-like.");
        }

        if (!configuration.rateLimitEnabled()) {
            failures.add("VANRIDES_RATE_LIMIT_ENABLED=true e obrigatorio em ambiente publicado.");
        }

        if (configuration.isProdLikeEnvironment() && configuration.basicAuthEnabled()) {
            failures.add("VANRIDES_AUTH_BASIC_ENABLED=false e obrigatorio em PROD/prod-like.");
        }

        if (configuration.isProdLikeEnvironment() && !isBlank(configuration.qaAutoVerifyEmailDomains())) {
            failures.add("VANRIDES_QA_AUTO_VERIFY_EMAIL_DOMAINS deve ficar vazio em PROD/prod-like.");
        }

        boolean emailBaseUrlSafe = isSafeEmailVerificationBaseUrl(
                configuration.emailVerificationPublicBaseUrl()
        );
        if (!isBlank(configuration.emailVerificationPublicBaseUrl()) && !emailBaseUrlSafe) {
            failures.add("VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL deve ser uma URL HTTPS publica, sem credenciais, query ou fragmento, e usar o path oficial de confirmacao.");
        }

        if (isResendProvider(configuration.emailProvider())) {
            if (isBlank(configuration.emailFrom())) {
                failures.add("VANRIDES_EMAIL_FROM e obrigatorio quando VANRIDES_EMAIL_PROVIDER=resend.");
            }
            if (isBlank(configuration.resendApiKey())) {
                failures.add("VANRIDES_EMAIL_RESEND_API_KEY e obrigatorio quando VANRIDES_EMAIL_PROVIDER=resend.");
            }
            if (isBlank(configuration.emailVerificationPublicBaseUrl())) {
                failures.add("VANRIDES_EMAIL_VERIFICATION_PUBLIC_BASE_URL deve ser uma URL HTTPS publica quando VANRIDES_EMAIL_PROVIDER=resend.");
            }
        }

        if (configuration.isProdLikeEnvironment() && isLogEmailProvider(configuration.emailProvider())) {
            failures.add("VANRIDES_EMAIL_PROVIDER=resend e recomendado como obrigatorio em PROD/prod-like.");
        }

        if (isGoogleDirectionsProvider(configuration.routingProvider()) && isBlank(configuration.googleApiKey())) {
            failures.add("GOOGLE_API_KEY e obrigatorio quando VANRIDES_ROUTING_PROVIDER=google-directions em ambiente publicado.");
        }

        if (configuration.mapsPlatformEnabled() && !isInternalServiceHttpUrl(configuration.mapsPlatformBaseUrl())) {
            failures.add("VANRIDES_MAPS_PLATFORM_BASE_URL deve apontar para o servico Maps Platform quando VANRIDES_MAPS_PLATFORM_ENABLED=true em ambiente publicado.");
        }

        if (!failures.isEmpty()) {
            throw new IllegalStateException("Configuracao publica invalida: " + String.join(" ", failures));
        }
    }

    private static boolean isStrongJwtSecret(String jwtSecret) {
        if (isBlank(jwtSecret)) {
            return false;
        }
        String normalizedSecret = jwtSecret.trim().toLowerCase(Locale.ROOT);
        return jwtSecret.trim().length() >= 32 && !WEAK_JWT_SECRETS.contains(normalizedSecret);
    }

    private static boolean isResendProvider(String provider) {
        return "resend".equals(normalize(provider, "log"));
    }

    private static boolean isLogEmailProvider(String provider) {
        return "log".equals(normalize(provider, "log"));
    }

    private static boolean isGoogleDirectionsProvider(String provider) {
        return "google-directions".equals(normalize(provider, "static"));
    }

    private static boolean isSafeEmailVerificationBaseUrl(String value) {
        if (isBlank(value)) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());
            String scheme = normalize(uri.getScheme(), "");
            String host = normalize(uri.getHost(), "");
            return "https".equals(scheme)
                    && !host.isBlank()
                    && !isLoopbackOrWildcardHost(host)
                    && uri.getUserInfo() == null
                    && uri.getRawQuery() == null
                    && uri.getRawFragment() == null
                    && EMAIL_CONFIRMATION_PATH.equals(uri.getRawPath());
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    private static boolean isLoopbackOrWildcardHost(String host) {
        String normalized = normalize(host, "");
        return "localhost".equals(normalized)
                || "localhost.".equals(normalized)
                || normalized.endsWith(".localhost")
                || normalized.endsWith(".localhost.")
                || normalized.startsWith("127.")
                || "0.0.0.0".equals(normalized)
                || "::1".equals(normalized)
                || "[::1]".equals(normalized);
    }

    private static boolean isInternalServiceHttpUrl(String value) {
        if (isBlank(value)) {
            return false;
        }

        try {
            URI uri = URI.create(value.trim());
            String scheme = normalize(uri.getScheme(), "");
            String host = normalize(uri.getHost(), "");
            return Set.of("http", "https").contains(scheme)
                    && !host.isBlank()
                    && !"localhost".equals(host)
                    && !"127.0.0.1".equals(host)
                    && !"0.0.0.0".equals(host);
        } catch (IllegalArgumentException e) {
            return false;
        }
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
}
