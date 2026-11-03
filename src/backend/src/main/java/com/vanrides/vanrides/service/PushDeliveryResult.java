package com.vanrides.vanrides.service;

public record PushDeliveryResult(
        boolean sent,
        String providerStatus
) {
    public static PushDeliveryResult sent(String providerStatus) {
        return new PushDeliveryResult(true, providerStatus);
    }

    public static PushDeliveryResult failed(String providerStatus) {
        return new PushDeliveryResult(false, providerStatus);
    }
}
