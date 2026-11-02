package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

@JsonIgnoreProperties(ignoreUnknown = true)
public record DriverLocationPublishRequest(
        Double latitude,
        Double longitude
) {
}
