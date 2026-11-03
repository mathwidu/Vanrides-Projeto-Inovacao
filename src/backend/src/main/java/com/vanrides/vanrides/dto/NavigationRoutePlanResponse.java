package com.vanrides.vanrides.dto;

import java.util.List;

public class NavigationRoutePlanResponse {

    private final String routeId;
    private final String provider;
    private final String region;
    private final String vehicleType;
    private final String coverageStatus;
    private final String offlinePackStatus;
    private final Double qualityScore;
    private final String fallbackReason;
    private final Integer estimatedDurationSeconds;
    private final Integer estimatedDistanceMeters;
    private final List<List<Double>> polyline;
    private final List<NavigationRoutePlanLegResponse> legs;
    private final List<NavigationRoutePlanInstructionResponse> instructions;

    public NavigationRoutePlanResponse(String routeId,
                                       String provider,
                                       String region,
                                       String vehicleType,
                                       String coverageStatus,
                                       String offlinePackStatus,
                                       Double qualityScore,
                                       String fallbackReason,
                                       Integer estimatedDurationSeconds,
                                       Integer estimatedDistanceMeters,
                                       List<List<Double>> polyline,
                                       List<NavigationRoutePlanLegResponse> legs,
                                       List<NavigationRoutePlanInstructionResponse> instructions) {
        this.routeId = routeId;
        this.provider = provider;
        this.region = region;
        this.vehicleType = vehicleType;
        this.coverageStatus = coverageStatus;
        this.offlinePackStatus = offlinePackStatus;
        this.qualityScore = qualityScore;
        this.fallbackReason = fallbackReason;
        this.estimatedDurationSeconds = estimatedDurationSeconds;
        this.estimatedDistanceMeters = estimatedDistanceMeters;
        this.polyline = polyline == null ? List.of() : List.copyOf(polyline);
        this.legs = legs == null ? List.of() : List.copyOf(legs);
        this.instructions = instructions == null ? List.of() : List.copyOf(instructions);
    }

    public String getRouteId() {
        return routeId;
    }

    public String getProvider() {
        return provider;
    }

    public String getRegion() {
        return region;
    }

    public String getVehicleType() {
        return vehicleType;
    }

    public String getCoverageStatus() {
        return coverageStatus;
    }

    public String getOfflinePackStatus() {
        return offlinePackStatus;
    }

    public Double getQualityScore() {
        return qualityScore;
    }

    public String getFallbackReason() {
        return fallbackReason;
    }

    public Integer getEstimatedDurationSeconds() {
        return estimatedDurationSeconds;
    }

    public Integer getEstimatedDistanceMeters() {
        return estimatedDistanceMeters;
    }

    public List<List<Double>> getPolyline() {
        return polyline;
    }

    public List<NavigationRoutePlanLegResponse> getLegs() {
        return legs;
    }

    public List<NavigationRoutePlanInstructionResponse> getInstructions() {
        return instructions;
    }
}
