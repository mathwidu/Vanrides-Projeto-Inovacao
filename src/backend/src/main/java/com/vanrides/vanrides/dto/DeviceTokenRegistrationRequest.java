package com.vanrides.vanrides.dto;

public record DeviceTokenRegistrationRequest(
        String token,
        String platform
) {
}
