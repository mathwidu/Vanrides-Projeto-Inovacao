package com.vanrides.vanrides.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;
import org.springframework.web.filter.CorsFilter;

import java.util.Arrays;
import java.util.List;

@Configuration
public class CorsConfig {

    @Value("${vanrides.cors.allowed-origin-patterns:*}")
    private String allowedOriginPatterns;

    @Value("${vanrides.cors.allowed-methods:*}")
    private String allowedMethods;

    @Value("${vanrides.cors.allowed-headers:*}")
    private String allowedHeaders;

    @Bean
    public FilterRegistrationBean<CorsFilter> corsFilter() {
        CorsConfiguration configuration = new CorsConfiguration();
        configuration.setAllowedOriginPatterns(splitCsv(allowedOriginPatterns));
        configuration.setAllowedMethods(splitCsv(allowedMethods));
        configuration.setAllowedHeaders(splitCsv(allowedHeaders));
        configuration.setMaxAge(3600L);

        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);

        FilterRegistrationBean<CorsFilter> registration = new FilterRegistrationBean<>(new CorsFilter(source));
        registration.setName("vanridesCorsFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 1);
        return registration;
    }

    private List<String> splitCsv(String value) {
        if (value == null || value.isBlank()) {
            return List.of("*");
        }
        List<String> values = Arrays.stream(value.split(","))
                .map(String::trim)
                .filter(item -> !item.isEmpty())
                .toList();
        return values.isEmpty() ? List.of("*") : values;
    }
}
