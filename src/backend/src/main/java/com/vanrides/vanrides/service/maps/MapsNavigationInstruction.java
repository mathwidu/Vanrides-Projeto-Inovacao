package com.vanrides.vanrides.service.maps;

public record MapsNavigationInstruction(
        int index,
        String text,
        String voiceText,
        int distanceMeters,
        String maneuver
) {
}
