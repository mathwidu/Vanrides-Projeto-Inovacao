package com.vanrides.vanrides.service;

import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.env.Environment;
import org.springframework.stereotype.Service;

import java.util.Arrays;
import java.util.Locale;

@Service
public class ConfiguredEmailDeliveryGateway implements EmailDeliveryGateway {

    private static final Logger log = LoggerFactory.getLogger(ConfiguredEmailDeliveryGateway.class);

    @Autowired
    private LoggingEmailDeliveryGateway loggingEmailDeliveryGateway;

    @Autowired
    private ResendEmailDeliveryGateway resendEmailDeliveryGateway;

    @Autowired
    private Environment environment;

    @Value("${vanrides.email.provider:log}")
    private String provider;

    @PostConstruct
    void validateConfiguration() {
        if(!java.util.Set.of("log","resend","disabled").contains(normalizedProvider()))
            throw new IllegalStateException("EMAIL_PROVIDER_INVALID");
        if(normalizedProvider().equals("disabled")) {
            if(!com.vanrides.vanrides.config.TeamApiMode.enabled(environment)) throw new IllegalStateException("EMAIL_DISABLED_OUTSIDE_TEAM_API");
            com.vanrides.vanrides.config.TeamApiMode.validate(environment);
        }
        if (usesResend() && !resendEmailDeliveryGateway.isConfigured()) {
            if (isLocalProfileActive()) {
                log.warn("Resend solicitado, mas sem credenciais; ambiente local vai usar logging como fallback.");
                return;
            }
            throw new IllegalStateException("Provider de e-mail configurado como Resend, mas a API key/remetente não foram configurados.");
        }
    }

    @Override
    public void sendEmail(String toEmail, String subject, String htmlBody, String textBody) {
        if(normalizedProvider().equals("disabled")) throw new IllegalStateException("EMAIL_DELIVERY_DISABLED");
        if(!java.util.Set.of("log","resend").contains(normalizedProvider())) throw new IllegalStateException("EMAIL_PROVIDER_INVALID");
        if (usesResend()) {
            if (resendEmailDeliveryGateway.isConfigured()) {
                resendEmailDeliveryGateway.sendEmail(toEmail, subject, htmlBody, textBody);
                return;
            }
            if (isLocalProfileActive()) {
                loggingEmailDeliveryGateway.sendEmail(toEmail, subject, htmlBody, textBody);
                return;
            }
            throw new IllegalStateException("Provider Resend ativo sem configuração válida.");
        }

        loggingEmailDeliveryGateway.sendEmail(toEmail, subject, htmlBody, textBody);
    }

    private boolean usesResend() {
        return normalizedProvider().equals("resend");
    }

    private String normalizedProvider() {
        if (provider == null || provider.isBlank()) {
            return "log";
        }
        return provider.trim().toLowerCase(Locale.ROOT);
    }

    private boolean isLocalProfileActive() {
        return Arrays.stream(environment.getActiveProfiles()).anyMatch("local"::equalsIgnoreCase);
    }
}
