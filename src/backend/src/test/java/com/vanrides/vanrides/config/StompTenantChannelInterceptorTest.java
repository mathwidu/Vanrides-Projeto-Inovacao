package com.vanrides.vanrides.config;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.AuthorizationService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.messaging.Message;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.MessageBuilder;

import java.util.HashMap;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class StompTenantChannelInterceptorTest {

    @Mock
    private AuthorizationService authorizationService;

    private StompTenantChannelInterceptor interceptor;

    @BeforeEach
    void setUp() {
        interceptor = new StompTenantChannelInterceptor(authorizationService);
    }

    @Test
    void shouldAuthenticateConnectAndStoreUserInSession() {
        AuthenticatedUser user = new AuthenticatedUser(10L, "MOTORISTA", "driver@vanrides.dev", null, null, 88L);
        when(authorizationService.authenticate("Bearer token")).thenReturn(user);

        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.CONNECT);
        accessor.addNativeHeader("Authorization", "Bearer token");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.setLeaveMutable(true);

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        interceptor.preSend(message, null);

        assertThat(accessor.getSessionAttributes())
                .containsEntry(StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR, user);
    }

    @Test
    void shouldAllowSubscribeInsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/localizacao/88");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "MOTORISTA", "driver@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectSubscribeOutsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/localizacao/99");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "MOTORISTA", "driver@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
        assertThat(ex.getMessage()).contains("fora do tenant");
    }

    @Test
    void shouldRejectLegacyGlobalLocationTopic() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/localizacao");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "PASSAGEIRO", "passageiro@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
        assertThat(ex.getMessage()).contains("global");
    }

    @Test
    void shouldAllowTripTopicInsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/viagens/88");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "PASSAGEIRO", "passageiro@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldAllowTripScopedTopicInsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/viagens/88/45");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "PASSAGEIRO", "passageiro@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());
        Message<?> result = interceptor.preSend(message, null);

        assertThat(result).isNotNull();
    }

    @Test
    void shouldRejectTripTopicOutsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/viagens/99");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "PASSAGEIRO", "passageiro@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
        assertThat(ex.getMessage()).contains("fora do tenant");
    }

    @Test
    void shouldRejectTripScopedTopicOutsideAuthenticatedTenant() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SUBSCRIBE);
        accessor.setDestination("/topic/viagens/99/45");
        accessor.setSessionAttributes(new HashMap<>());
        accessor.getSessionAttributes().put(
                StompTenantChannelInterceptor.AUTHENTICATED_USER_ATTR,
                new AuthenticatedUser(10L, "PASSAGEIRO", "passageiro@vanrides.dev", null, null, 88L)
        );

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
        assertThat(ex.getMessage()).contains("fora do tenant");
    }

    @Test
    void shouldRejectLocationSendWhenSessionIsNotAuthenticated() {
        StompHeaderAccessor accessor = StompHeaderAccessor.create(StompCommand.SEND);
        accessor.setDestination("/app/enviarLocalizacao");
        accessor.setSessionAttributes(new HashMap<>());

        Message<byte[]> message = MessageBuilder.createMessage(new byte[0], accessor.getMessageHeaders());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> interceptor.preSend(message, null));
        assertThat(ex.getMessage()).contains("não autenticado");
    }
}
