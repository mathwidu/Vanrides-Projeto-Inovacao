package com.vanrides.vanrides.config;

import com.vanrides.vanrides.service.NoopPushDeliveryGateway;
import com.vanrides.vanrides.service.PushDeliveryGateway;
import org.springframework.beans.factory.SmartInitializingSingleton;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.env.Environment;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.Locale;

@Configuration(proxyBeanMethods = false)
public class PushDeliveryConfiguration {

    static final String PROVIDER_PROPERTY = "vanrides.push.provider";
    static final String NO_PROVIDER = "none";

    @Bean
    @ConditionalOnProperty(
            name = PROVIDER_PROPERTY,
            havingValue = NO_PROVIDER,
            matchIfMissing = true
    )
    NoopPushDeliveryGateway noopPushDeliveryGateway() {
        return new NoopPushDeliveryGateway();
    }

    @Bean
    SmartInitializingSingleton pushDeliveryGatewayInvariant(
            List<PushDeliveryGateway> gateways,
            Environment environment
    ) {
        String selectedProvider = environment.getProperty(PROVIDER_PROPERTY, NO_PROVIDER)
                .trim()
                .toLowerCase(Locale.ROOT);
        return () -> validateGatewaySelection(selectedProvider, gateways);
    }

    private void validateGatewaySelection(String selectedProvider, List<PushDeliveryGateway> gateways) {
        if (gateways.size() != 1) {
            throw new IllegalStateException(
                    "Exactly one PushDeliveryGateway must be registered; found " + gateways.size() + "."
            );
        }

        PushDeliveryGateway gateway = gateways.get(0);
        if (!selectedProvider.equals(gateway.providerId())) {
            throw new IllegalStateException(
                    "The selected push provider must match the registered PushDeliveryGateway."
            );
        }
    }
}
