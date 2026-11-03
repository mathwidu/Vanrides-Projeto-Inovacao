package com.vanrides.vanrides.service.maps;

import java.util.List;

public record MapsRoutePlanResponse(
        String routeId,
        String provider,
        String region,
        String vehicleType,
        String coverageStatus,
        String offlinePackStatus,
        double qualityScore,
        String fallbackReason,
        int estimatedDurationSeconds,
        int estimatedDistanceMeters,
        List<List<Double>> polyline,
        List<MapsRouteLeg> legs,
        List<MapsNavigationInstruction> instructions
) {
}
