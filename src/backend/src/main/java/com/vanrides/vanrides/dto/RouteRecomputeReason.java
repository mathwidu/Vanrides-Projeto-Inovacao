package com.vanrides.vanrides.dto;

public enum RouteRecomputeReason {
    NONE,
    TRIP_STARTED,
    ROUTE_REORDERED,
    DRIVER_CHANGED,
    DESTINATION_CHANGED,
    MAJOR_DEVIATION,
    MANUAL_REFRESH,
    PROVIDER_FALLBACK
}
