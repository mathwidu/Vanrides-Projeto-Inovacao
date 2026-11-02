package com.vanrides.vanrides.config;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthorizationService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;

import java.security.Principal;
import java.util.List;
import java.util.Map;

@Component
public class StompTenantChannelInterceptor implements ChannelInterceptor {

    public static final String AUTHENTICATED_USER_ATTR = "authenticatedUser";

    private final AuthorizationService authorizationService;

    @Autowired
    public StompTenantChannelInterceptor(AuthorizationService authorizationService) {
        this.authorizationService = authorizationService;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = MessageHeaderAccessor.getAccessor(message, StompHeaderAccessor.class);
        if (accessor == null || accessor.getCommand() == null) {
            return message;
        }

        StompCommand command = accessor.getCommand();
        if (StompCommand.CONNECT.equals(command)) {
            handleConnect(accessor);
        } else if (StompCommand.SUBSCRIBE.equals(command)) {
            handleSubscribe(accessor);
        } else if (StompCommand.SEND.equals(command)) {
            handleSend(accessor);
        }

        return message;
    }

    private void handleConnect(StompHeaderAccessor accessor) {
        String authorizationHeader = firstNativeHeader(accessor, "Authorization");
        if (authorizationHeader == null) {
            authorizationHeader = firstNativeHeader(accessor, "authorization");
        }

        try {
            AuthenticatedUser user = authorizationService.authenticate(authorizationHeader);
            Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
            if (sessionAttributes != null) {
                sessionAttributes.put(AUTHENTICATED_USER_ATTR, user);
            }
            accessor.setUser(new StompUserPrincipal(user.getEmail()));
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Falha ao autenticar WebSocket: " + e.getMessage(), e);
        }
    }

    private void handleSubscribe(StompHeaderAccessor accessor) {
        AuthenticatedUser user = authenticatedUser(accessor);
        String destination = accessor.getDestination();
        if (destination == null) {
            throw new IllegalArgumentException("Destino STOMP ausente");
        }

        if (destination.equals("/topic/localizacao")) {
            throw new IllegalArgumentException("Tópico global de localização desativado");
        }

        if (destination.startsWith("/topic/localizacao/")) {
            Long destinationCompanyId = parseCompanyId(destination.substring("/topic/localizacao/".length()));
            if (!user.getCompanyId().equals(destinationCompanyId)) {
                throw new IllegalArgumentException("Inscrição fora do tenant autenticado");
            }
            return;
        }

        if (destination.equals("/topic/viagens")) {
            throw new IllegalArgumentException("Tópico global de viagens desativado");
        }

        if (destination.startsWith("/topic/viagens/")) {
            Long destinationCompanyId = parseCompanyIdSegment(destination.substring("/topic/viagens/".length()));
            if (!user.getCompanyId().equals(destinationCompanyId)) {
                throw new IllegalArgumentException("Inscrição fora do tenant autenticado");
            }
        }
    }

    private void handleSend(StompHeaderAccessor accessor) {
        authenticatedUser(accessor);
        String destination = accessor.getDestination();
        if (destination != null && destination.equals("/app/enviarLocalizacao")) {
            return;
        }
    }

    private AuthenticatedUser authenticatedUser(StompHeaderAccessor accessor) {
        Map<String, Object> sessionAttributes = accessor.getSessionAttributes();
        if (sessionAttributes == null) {
            throw new IllegalArgumentException("Sessão WebSocket sem autenticação");
        }

        Object value = sessionAttributes.get(AUTHENTICATED_USER_ATTR);
        if (value instanceof AuthenticatedUser user) {
            return user;
        }

        throw new IllegalArgumentException("Usuário WebSocket não autenticado");
    }

    private Long parseCompanyId(String rawValue) {
        try {
            return Long.parseLong(rawValue);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Destino de localização com companyId inválido", e);
        }
    }

    private Long parseCompanyIdSegment(String rawValue) {
        String companySegment = rawValue;
        int separatorIndex = rawValue.indexOf('/');
        if (separatorIndex >= 0) {
            companySegment = rawValue.substring(0, separatorIndex);
        }
        return parseCompanyId(companySegment);
    }

    private String firstNativeHeader(StompHeaderAccessor accessor, String headerName) {
        List<String> values = accessor.getNativeHeader(headerName);
        if (values == null || values.isEmpty()) {
            return null;
        }
        return values.get(0);
    }

    static final class StompUserPrincipal implements Principal {
        private final String name;

        private StompUserPrincipal(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
    }
}
