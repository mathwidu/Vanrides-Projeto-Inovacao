package com.vanrides.vanrides.filtros;

import com.vanrides.vanrides.config.CorsConfig;
import org.junit.jupiter.api.Test;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.AnnotationUtils;
import org.springframework.core.annotation.Order;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.filter.CorsFilter;

import static org.assertj.core.api.Assertions.assertThat;

class FilterOrderTest {

    @Test
    void shouldKeepSecurityControlsInExplicitOrder() {
        assertThat(orderOf(RequestCorrelationFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE);
        assertThat(corsRegistration().getOrder()).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 1);
        assertThat(orderOf(BasicRateLimitFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 2);
        assertThat(orderOf(RequestBodyLimitFilter.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 3);
        assertThat(orderOf(FilterAuthBasic.class)).isEqualTo(Ordered.HIGHEST_PRECEDENCE + 4);
    }

    @Test
    void shouldUseCorsFilterBeforeEarlySecurityErrors() {
        FilterRegistrationBean<CorsFilter> registration = corsRegistration();

        assertThat(registration.getFilter()).isInstanceOf(CorsFilter.class);
        assertThat(registration.getUrlPatterns()).isEmpty();
    }

    private int orderOf(Class<?> filterType) {
        Order order = AnnotationUtils.findAnnotation(filterType, Order.class);
        assertThat(order).as("@Order de %s", filterType.getSimpleName()).isNotNull();
        return order.value();
    }

    private FilterRegistrationBean<CorsFilter> corsRegistration() {
        CorsConfig config = new CorsConfig();
        ReflectionTestUtils.setField(config, "allowedOriginPatterns", "https://app.example");
        ReflectionTestUtils.setField(config, "allowedMethods", "GET,POST,OPTIONS");
        ReflectionTestUtils.setField(config, "allowedHeaders", "Authorization,Content-Type");
        return config.corsFilter();
    }
}
