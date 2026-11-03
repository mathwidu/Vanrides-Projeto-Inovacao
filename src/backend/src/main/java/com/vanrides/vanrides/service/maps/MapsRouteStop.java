package com.vanrides.vanrides.service.maps;

public record MapsRouteStop(
        String id,
        String label,
        double lat,
        double lon
) {
}
