package com.vanrides.vanrides.dto;

public class NavigationRoutePlanLegResponse {

    private final String fromStopId;
    private final String toStopId;
    private final Integer distanceMeters;
    private final Integer durationSeconds;

    public NavigationRoutePlanLegResponse(String fromStopId,
                                          String toStopId,
                                          Integer distanceMeters,
                                          Integer durationSeconds) {
        this.fromStopId = fromStopId;
        this.toStopId = toStopId;
        this.distanceMeters = distanceMeters;
        this.durationSeconds = durationSeconds;
    }

    public String getFromStopId() {
        return fromStopId;
    }

    public String getToStopId() {
        return toStopId;
    }

    public Integer getDistanceMeters() {
        return distanceMeters;
    }

    public Integer getDurationSeconds() {
        return durationSeconds;
    }
}
