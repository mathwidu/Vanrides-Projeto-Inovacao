package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.NotificationEventType;

import java.util.Map;

public record NotificationDispatchRequest(
        NotificationEventType eventType,
        UserRole recipientRole,
        Long recipientUserId,
        Long companyId,
        String title,
        String body,
        Map<String, String> data
) {
}
