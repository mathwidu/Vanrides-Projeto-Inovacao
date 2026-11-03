package com.vanrides.vanrides.service.maps;

public record MapsRouteLeg(
        String fromStopId,
        String toStopId,
        int distanceMeters,
        int durationSeconds
) {
}
