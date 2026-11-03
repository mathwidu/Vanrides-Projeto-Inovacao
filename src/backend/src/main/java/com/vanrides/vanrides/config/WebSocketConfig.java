package com.vanrides.vanrides.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompTenantChannelInterceptor stompTenantChannelInterceptor;

    public WebSocketConfig(StompTenantChannelInterceptor stompTenantChannelInterceptor) {
        this.stompTenantChannelInterceptor = stompTenantChannelInterceptor;
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        config.enableSimpleBroker("/topic"); // Canal de saída (broadcast para clientes)
        config.setApplicationDestinationPrefixes("/app"); // Prefixo para mensagens enviadas ao servidor
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompTenantChannelInterceptor);
    }

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws-location")
                .setAllowedOriginPatterns("*") // ✅ Correção essencial para Spring Boot 3+
                .withSockJS(); // Você pode remover se quiser trabalhar sem fallback
    }
}
