package com.vanrides.vanrides.whatsapp.config;

import com.vanrides.vanrides.whatsapp.auth.*;
import jakarta.servlet.DispatcherType;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication(type = ConditionalOnWebApplication.Type.SERVLET)
public class WaWebConfiguration {
    @Bean
    FilterRegistrationBean<WaIngressFilter> waIngress(WaSettings settings, WaCredentials credentials, WaRateLimiter limiter,
            com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver addresses) {
        var bean = new FilterRegistrationBean<>(new WaIngressFilter(settings, credentials, limiter, addresses));
        bean.setName("waIngress"); bean.setOrder(Ordered.HIGHEST_PRECEDENCE);
        bean.setDispatcherTypes(DispatcherType.REQUEST, DispatcherType.FORWARD);
        return bean;
    }
    @Bean
    FilterRegistrationBean<WaProofFilter> waProof(WaDpopVerifier verifier, WaProofLedger ledger) {
        var bean = new FilterRegistrationBean<>(new WaProofFilter(verifier, ledger));
        bean.setName("waProof"); bean.setOrder(Ordered.HIGHEST_PRECEDENCE + 5);
        return bean;
    }
}
