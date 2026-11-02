package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.NotificationEvent;

public class NoopPushDeliveryGateway implements PushDeliveryGateway {

    @Override
    public String providerId() {
        return "none";
    }

    @Override
    public boolean isConfigured() {
        return false;
    }

    @Override
    public PushDeliveryResult send(DeviceToken deviceToken, NotificationEvent event) {
        return PushDeliveryResult.failed("provider_not_configured");
    }
}
