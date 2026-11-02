package com.vanrides.vanrides.dto;

import java.time.LocalDateTime;

public class LocalizacaoMotoristaResponse {

    private final Double latitude;
    private final Double longitude;
    private final LocalDateTime updatedAt;
    private final LocationFreshness freshness;

    public LocalizacaoMotoristaResponse(Double latitude,
                                        Double longitude,
                                        LocalDateTime updatedAt,
                                        LocationFreshness freshness) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.updatedAt = updatedAt;
        this.freshness = freshness == null ? LocationFreshness.UNAVAILABLE : freshness;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocationFreshness getFreshness() {
        return freshness;
    }
}
