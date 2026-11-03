package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.NotificationEvent;

public interface PushDeliveryGateway {

    /** Stable lowercase identifier matched against vanrides.push.provider. */
    String providerId();

    boolean isConfigured();

    PushDeliveryResult send(DeviceToken deviceToken, NotificationEvent event);
}
