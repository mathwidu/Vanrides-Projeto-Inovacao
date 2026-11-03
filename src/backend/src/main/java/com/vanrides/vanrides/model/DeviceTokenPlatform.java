package com.vanrides.vanrides.model;

import java.util.Locale;

public enum DeviceTokenPlatform {
    IOS,
    ANDROID,
    WEB,
    UNKNOWN;

    public static DeviceTokenPlatform fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("platform é obrigatório.");
        }

        return switch (rawValue.trim().replace('-', '_').toUpperCase(Locale.ROOT)) {
            case "IOS", "I_OS" -> IOS;
            case "ANDROID" -> ANDROID;
            case "WEB" -> WEB;
            case "UNKNOWN" -> UNKNOWN;
            default -> throw new IllegalArgumentException("platform inválido.");
        };
    }
}
