package com.vanrides.vanrides.service.maps;

public record MapsRouteOptions(
        boolean offlineRequired,
        boolean avoidUnsafeAreas,
        boolean schoolTransportMode
) {
}
