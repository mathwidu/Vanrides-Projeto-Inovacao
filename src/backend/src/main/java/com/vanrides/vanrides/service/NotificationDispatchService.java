package com.vanrides.vanrides.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.dto.NotificationDispatchRequest;
import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.NotificationDispatchStatus;
import com.vanrides.vanrides.model.NotificationEvent;
import com.vanrides.vanrides.repository.DeviceTokenRepository;
import com.vanrides.vanrides.repository.NotificationEventRepository;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class NotificationDispatchService {

    private final NotificationEventRepository notificationEventRepository;
    private final DeviceTokenRepository deviceTokenRepository;
    private final PushDeliveryGateway pushDeliveryGateway;
    private final ObjectMapper objectMapper;

    public NotificationDispatchService(NotificationEventRepository notificationEventRepository,
                                       DeviceTokenRepository deviceTokenRepository,
                                       PushDeliveryGateway pushDeliveryGateway,
                                       ObjectMapper objectMapper) {
        this.notificationEventRepository = notificationEventRepository;
        this.deviceTokenRepository = deviceTokenRepository;
        this.pushDeliveryGateway = pushDeliveryGateway;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public NotificationEvent dispatch(NotificationDispatchRequest request) {
        NotificationEvent event = buildEvent(request);

        if (!pushDeliveryGateway.isConfigured()) {
            event.setStatus(NotificationDispatchStatus.SKIPPED_PROVIDER_NOT_CONFIGURED);
            event.setProviderStatus("provider_not_configured");
            return notificationEventRepository.save(event);
        }

        List<DeviceToken> devices = deviceTokenRepository.findAllByUserIdAndUserRoleAndActiveTrue(
                request.recipientUserId(),
                request.recipientRole().claimValue()
        );
        if (devices.isEmpty()) {
            event.setStatus(NotificationDispatchStatus.SKIPPED_NO_DEVICES);
            event.setProviderStatus("no_active_devices");
            return notificationEventRepository.save(event);
        }

        int attempts = 0;
        boolean sent = false;
        String providerStatus = null;
        for (DeviceToken device : devices) {
            attempts++;
            PushDeliveryResult result = pushDeliveryGateway.send(device, event);
            if (result != null) {
                providerStatus = result.providerStatus();
                sent = sent || result.sent();
            }
        }

        event.setAttempts(attempts);
        event.setProviderStatus(providerStatus);
        event.setStatus(sent ? NotificationDispatchStatus.SENT : NotificationDispatchStatus.FAILED);
        event.setDispatchedAt(Instant.now());
        return notificationEventRepository.save(event);
    }

    private NotificationEvent buildEvent(NotificationDispatchRequest request) {
        if (request == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "notification request is required.");
        }
        if (request.eventType() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "eventType is required.");
        }
        if (request.recipientRole() == null || request.recipientUserId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "recipient is required.");
        }

        NotificationEvent event = new NotificationEvent();
        event.setEventType(request.eventType());
        event.setRecipientRole(request.recipientRole());
        event.setRecipientUserId(request.recipientUserId());
        event.setCompanyId(request.companyId());
        event.setTitle(requiredText(request.title(), "title", 160));
        event.setBody(requiredText(request.body(), "body", 500));
        event.setDataJson(toJson(request.data()));
        event.setStatus(NotificationDispatchStatus.PENDING);
        return event;
    }

    private String requiredText(String value, String field, int maxLength) {
        if (value == null || value.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " is required.");
        }
        String trimmed = value.trim();
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, field + " exceeds the maximum length.");
        }
        return trimmed;
    }

    private String toJson(Map<String, String> data) {
        try {
            return objectMapper.writeValueAsString(data == null ? Map.of() : data);
        } catch (JsonProcessingException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "data is invalid.", e);
        }
    }
}
