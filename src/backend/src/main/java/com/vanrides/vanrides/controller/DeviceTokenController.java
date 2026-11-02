package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DeviceTokenInvalidateRequest;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.service.DeviceTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/devices")
public class DeviceTokenController {

    private final DeviceTokenService deviceTokenService;

    public DeviceTokenController(DeviceTokenService deviceTokenService) {
        this.deviceTokenService = deviceTokenService;
    }

    @PostMapping("/fcm-token")
    public ResponseEntity<Void> registerToken(@RequestBody(required = false) DeviceTokenRegistrationRequest body,
                                              HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        deviceTokenService.register(
                role,
                AuthorizationGuard.getRequiredUserId(request),
                getOptionalStringAttribute(request, "userEmail"),
                getOptionalLongAttribute(request, "userCompanyId"),
                body
        );
        return ResponseEntity.noContent().build();
    }

    @DeleteMapping("/fcm-token")
    public ResponseEntity<Void> invalidateToken(@RequestBody(required = false) DeviceTokenInvalidateRequest body,
                                                HttpServletRequest request) {
        deviceTokenService.invalidate(
                AuthorizationGuard.getRequiredRole(request),
                AuthorizationGuard.getRequiredUserId(request),
                body
        );
        return ResponseEntity.noContent().build();
    }

    private String getOptionalStringAttribute(HttpServletRequest request, String name) {
        Object attribute = request.getAttribute(name);
        return attribute instanceof String value && !value.isBlank() ? value : null;
    }

    private Long getOptionalLongAttribute(HttpServletRequest request, String name) {
        Object attribute = request.getAttribute(name);
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        return null;
    }
}
