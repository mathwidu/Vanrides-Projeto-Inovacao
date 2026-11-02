package com.vanrides.vanrides.service.maps;

import java.util.List;

public record MapsRoutePlanRequest(
        String region,
        String vehicleType,
        List<MapsRouteStop> stops,
        MapsRouteOptions options
) {
}
