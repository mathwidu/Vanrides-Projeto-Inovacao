package com.vanrides.vanrides.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.NotificationDispatchRequest;
import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.DeviceTokenPlatform;
import com.vanrides.vanrides.model.NotificationDispatchStatus;
import com.vanrides.vanrides.model.NotificationEvent;
import com.vanrides.vanrides.model.NotificationEventType;
import com.vanrides.vanrides.repository.DeviceTokenRepository;
import com.vanrides.vanrides.repository.NotificationEventRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDispatchServiceTest {

    @Mock
    private NotificationEventRepository notificationEventRepository;

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private PushDeliveryGateway pushDeliveryGateway;

    private NotificationDispatchService service;

    @BeforeEach
    void setUp() {
        service = new NotificationDispatchService(
                notificationEventRepository,
                deviceTokenRepository,
                pushDeliveryGateway,
                new ObjectMapper()
        );
    }

    @Test
    void shouldExposeNamedOperationalEventTypes() {
        assertThat(NotificationEventType.values()).contains(
                NotificationEventType.TRIP_STATUS_CHANGED,
                NotificationEventType.STOP_STATUS_CHANGED,
                NotificationEventType.PRESENCE_STATUS_CHANGED,
                NotificationEventType.SUPPORT_TICKET_MESSAGE_CREATED
        );
    }

    @Test
    void shouldPersistSkippedEventWhenProviderIsNotConfigured() {
        when(pushDeliveryGateway.isConfigured()).thenReturn(false);
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationEvent event = service.dispatch(tripStatusRequest());

        assertThat(event.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED_PROVIDER_NOT_CONFIGURED);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getProviderStatus()).isEqualTo("provider_not_configured");
        assertThat(event.getEventType()).isEqualTo(NotificationEventType.TRIP_STATUS_CHANGED);
        assertThat(event.getRecipientRole()).isEqualTo(UserRole.PASSENGER);
        assertThat(event.getDataJson()).contains("\"viagemId\":\"100\"");
        verifyNoInteractions(deviceTokenRepository);
        verify(pushDeliveryGateway, never()).send(any(), any());
    }

    @Test
    void shouldSkipWhenRecipientHasNoActiveDeviceToken() {
        when(pushDeliveryGateway.isConfigured()).thenReturn(true);
        when(deviceTokenRepository.findAllByUserIdAndUserRoleAndActiveTrue(21L, "PASSENGER"))
                .thenReturn(List.of());
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationEvent event = service.dispatch(tripStatusRequest());

        assertThat(event.getStatus()).isEqualTo(NotificationDispatchStatus.SKIPPED_NO_DEVICES);
        assertThat(event.getAttempts()).isZero();
        assertThat(event.getProviderStatus()).isEqualTo("no_active_devices");
        verify(pushDeliveryGateway, never()).send(any(), any());
    }

    @Test
    void shouldSendToAllActiveDeviceTokensWhenProviderIsConfigured() {
        DeviceToken ios = deviceToken("ios-token", DeviceTokenPlatform.IOS);
        DeviceToken android = deviceToken("android-token", DeviceTokenPlatform.ANDROID);
        when(pushDeliveryGateway.isConfigured()).thenReturn(true);
        when(deviceTokenRepository.findAllByUserIdAndUserRoleAndActiveTrue(21L, "PASSENGER"))
                .thenReturn(List.of(ios, android));
        when(pushDeliveryGateway.send(any(DeviceToken.class), any(NotificationEvent.class)))
                .thenReturn(PushDeliveryResult.sent("accepted"));
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationEvent event = service.dispatch(tripStatusRequest());

        assertThat(event.getStatus()).isEqualTo(NotificationDispatchStatus.SENT);
        assertThat(event.getAttempts()).isEqualTo(2);
        assertThat(event.getProviderStatus()).isEqualTo("accepted");
        assertThat(event.getDispatchedAt()).isNotNull();

        ArgumentCaptor<DeviceToken> tokenCaptor = ArgumentCaptor.forClass(DeviceToken.class);
        verify(pushDeliveryGateway, times(2)).send(tokenCaptor.capture(), any(NotificationEvent.class));
        assertThat(tokenCaptor.getAllValues()).containsExactly(ios, android);
    }

    @Test
    void shouldMarkEventAsFailedWhenConfiguredProviderRejectsEveryDevice() {
        DeviceToken ios = deviceToken("ios-token", DeviceTokenPlatform.IOS);
        when(pushDeliveryGateway.isConfigured()).thenReturn(true);
        when(deviceTokenRepository.findAllByUserIdAndUserRoleAndActiveTrue(21L, "PASSENGER"))
                .thenReturn(List.of(ios));
        when(pushDeliveryGateway.send(any(DeviceToken.class), any(NotificationEvent.class)))
                .thenReturn(PushDeliveryResult.failed("provider_error"));
        when(notificationEventRepository.save(any(NotificationEvent.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        NotificationEvent event = service.dispatch(tripStatusRequest());

        assertThat(event.getStatus()).isEqualTo(NotificationDispatchStatus.FAILED);
        assertThat(event.getAttempts()).isEqualTo(1);
        assertThat(event.getProviderStatus()).isEqualTo("provider_error");
    }

    private NotificationDispatchRequest tripStatusRequest() {
        return new NotificationDispatchRequest(
                NotificationEventType.TRIP_STATUS_CHANGED,
                UserRole.PASSENGER,
                21L,
                66L,
                "Viagem atualizada",
                "Sua van iniciou a viagem.",
                Map.of("viagemId", "100", "status", "IN_PROGRESS")
        );
    }

    private DeviceToken deviceToken(String token, DeviceTokenPlatform platform) {
        DeviceToken deviceToken = new DeviceToken();
        deviceToken.setTokenValue(token);
        deviceToken.setTokenHash(token + "-hash");
        deviceToken.setPlatform(platform);
        deviceToken.setUserId(21L);
        deviceToken.setUserRole("PASSENGER");
        deviceToken.setActive(true);
        return deviceToken;
    }
}
