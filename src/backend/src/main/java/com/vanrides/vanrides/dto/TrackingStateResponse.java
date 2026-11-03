package com.vanrides.vanrides.dto;

import java.time.LocalDateTime;

public class TrackingStateResponse {

    private final RouteSnapshotStatus routeSnapshotStatus;
    private final LocationFreshness locationFreshness;
    private final EtaConfidence etaConfidence;
    private final Integer stopsRemaining;
    private final Integer stopsCompleted;
    private final Integer stopsBeforePassenger;
    private final Long nextStopId;
    private final Integer nextStopOrder;
    private final LocalDateTime driverLocationUpdatedAt;
    private final RouteRecomputeReason routeRecomputeReason;
    private final LocalDateTime routeRecomputedAt;

    public TrackingStateResponse(RouteSnapshotStatus routeSnapshotStatus,
                                 LocationFreshness locationFreshness,
                                 EtaConfidence etaConfidence,
                                 Integer stopsRemaining,
                                 Integer stopsCompleted,
                                 Integer stopsBeforePassenger,
                                 Long nextStopId,
                                 Integer nextStopOrder,
                                 LocalDateTime driverLocationUpdatedAt,
                                 RouteRecomputeReason routeRecomputeReason,
                                 LocalDateTime routeRecomputedAt) {
        this.routeSnapshotStatus = routeSnapshotStatus == null ? RouteSnapshotStatus.UNAVAILABLE : routeSnapshotStatus;
        this.locationFreshness = locationFreshness == null ? LocationFreshness.UNAVAILABLE : locationFreshness;
        this.etaConfidence = etaConfidence == null ? EtaConfidence.UNAVAILABLE : etaConfidence;
        this.stopsRemaining = stopsRemaining == null ? 0 : stopsRemaining;
        this.stopsCompleted = stopsCompleted == null ? 0 : stopsCompleted;
        this.stopsBeforePassenger = stopsBeforePassenger;
        this.nextStopId = nextStopId;
        this.nextStopOrder = nextStopOrder;
        this.driverLocationUpdatedAt = driverLocationUpdatedAt;
        this.routeRecomputeReason = routeRecomputeReason == null ? RouteRecomputeReason.NONE : routeRecomputeReason;
        this.routeRecomputedAt = routeRecomputedAt;
    }

    public static TrackingStateResponse unavailable() {
        return new TrackingStateResponse(
                RouteSnapshotStatus.UNAVAILABLE,
                LocationFreshness.UNAVAILABLE,
                EtaConfidence.UNAVAILABLE,
                0,
                0,
                null,
                null,
                null,
                null,
                RouteRecomputeReason.NONE,
                null
        );
    }

    public Long getNextStopId() {
        return nextStopId;
    }

    public Integer getNextStopOrder() {
        return nextStopOrder;
    }

    public LocationFreshness getLocationFreshness() {
        return locationFreshness;
    }

    public EtaConfidence getEtaConfidence() {
        return etaConfidence;
    }

    public Integer getStopsRemaining() {
        return stopsRemaining;
    }

    public Integer getStopsCompleted() {
        return stopsCompleted;
    }

    public Integer getStopsBeforePassenger() {
        return stopsBeforePassenger;
    }

    public LocalDateTime getDriverLocationUpdatedAt() {
        return driverLocationUpdatedAt;
    }

    public RouteSnapshotStatus getRouteSnapshotStatus() {
        return routeSnapshotStatus;
    }

    public RouteRecomputeReason getRouteRecomputeReason() {
        return routeRecomputeReason;
    }

    public LocalDateTime getRouteRecomputedAt() {
        return routeRecomputedAt;
    }
}
