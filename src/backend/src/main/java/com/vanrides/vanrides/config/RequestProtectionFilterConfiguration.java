package com.vanrides.vanrides.config;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.filtros.BasicRateLimitFilter;
import com.vanrides.vanrides.filtros.BoundedRateLimitStore;
import com.vanrides.vanrides.filtros.RateLimitMetrics;
import com.vanrides.vanrides.filtros.RequestBodyLimitFilter;
import com.vanrides.vanrides.filtros.RequestBodyReadAdmissionController;
import com.vanrides.vanrides.filtros.SensitiveRequestPolicy;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.boot.web.embedded.tomcat.TomcatServletWebServerFactory;
import org.springframework.boot.web.server.WebServerFactoryCustomizer;

@Configuration(proxyBeanMethods = false)
public class RequestProtectionFilterConfiguration {

    @Bean
    BasicRateLimitFilter basicRateLimitFilter(
            ObjectMapper objectMapper,
            @Value("${vanrides.rate-limit.enabled:true}") boolean enabled,
            SensitiveRequestPolicy sensitiveRequestPolicy,
            TrustedProxyClientAddressResolver clientAddressResolver,
            BoundedRateLimitStore rateLimitStore,
            RateLimitMetrics metrics
    ) {
        return new BasicRateLimitFilter(
                objectMapper,
                enabled,
                sensitiveRequestPolicy,
                clientAddressResolver,
                rateLimitStore,
                metrics
        );
    }

    @Bean
    RequestBodyLimitFilter requestBodyLimitFilter(
            ObjectMapper objectMapper,
            @Value("${vanrides.request-body.max-bytes:1048576}") int maxBytes,
            RateLimitMetrics metrics,
            SensitiveRequestPolicy sensitiveRequestPolicy,
            RequestBodyReadAdmissionController admissionController
    ) {
        return new RequestBodyLimitFilter(
                objectMapper,
                maxBytes,
                metrics,
                sensitiveRequestPolicy,
                admissionController
        );
    }

    @Bean
    WebServerFactoryCustomizer<TomcatServletWebServerFactory> unreadRequestBodyValveCustomizer(
            RequestBodyReadAdmissionController admissionController
    ) {
        return factory -> factory.addContextValves(
                new UnreadRequestBodyValve(admissionController::recordUnreadConnectionClosed)
        );
    }
}
