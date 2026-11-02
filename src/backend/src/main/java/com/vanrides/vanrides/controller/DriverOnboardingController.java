package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.DriverCompanyRequestCreateRequest;
import com.vanrides.vanrides.dto.DriverInvitePreviewResponse;
import com.vanrides.vanrides.dto.DriverOnboardingStatusResponse;
import com.vanrides.vanrides.dto.TokenResponse;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/driver-onboarding")
public class DriverOnboardingController {

    private final DriverOnboardingService driverOnboardingService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public DriverOnboardingController(DriverOnboardingService driverOnboardingService,
                                      JwtService jwtService,
                                      RefreshTokenService refreshTokenService) {
        this.driverOnboardingService = driverOnboardingService;
        this.jwtService = jwtService;
        this.refreshTokenService = refreshTokenService;
    }

    @GetMapping("/invites/{token}/preview")
    public ResponseEntity<DriverInvitePreviewResponse> previewInvite(@PathVariable String token) {
        return ResponseEntity.ok(driverOnboardingService.previewInvite(token));
    }

    @PostMapping("/invites/{token}/redeem")
    public ResponseEntity<DriverOnboardingStatusResponse> redeemInvite(@PathVariable String token,
                                                                       HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        return ResponseEntity.ok(driverOnboardingService.redeemInvite(AuthorizationGuard.getRequiredUserId(request), token));
    }

    @GetMapping("/status")
    public ResponseEntity<DriverOnboardingStatusResponse> status(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        return ResponseEntity.ok(driverOnboardingService.getStatus(AuthorizationGuard.getRequiredUserId(request)));
    }

    @GetMapping("/companies")
    public ResponseEntity<?> searchCompanies(@RequestParam(value = "query", required = false) String query,
                                             HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        return ResponseEntity.ok(driverOnboardingService.searchCompanies(query));
    }

    @PostMapping("/requests")
    public ResponseEntity<DriverOnboardingStatusResponse> createRequest(@RequestBody DriverCompanyRequestCreateRequest body,
                                                                        HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        if (body == null || body.companyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId é obrigatório.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(driverOnboardingService.createRequest(AuthorizationGuard.getRequiredUserId(request), body.companyId()));
    }

    @PostMapping("/session-upgrade")
    public ResponseEntity<TokenResponse> upgradeSession(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.DRIVER);
        Long motoristaId = AuthorizationGuard.getRequiredUserId(request);
        Motorista motorista = driverOnboardingService.requireOperationalDriver(motoristaId);

        AuthenticatedUser user = new AuthenticatedUser(
                motorista.getId(),
                UserRole.DRIVER,
                motorista.getEmail(),
                motorista.getNome(),
                null,
                motorista.getCompany() != null ? motorista.getCompany().getId() : null
        );

        refreshTokenService.revokeAllForUser(motoristaId, UserRole.DRIVER.claimValue());

        return ResponseEntity.ok(new TokenResponse(
                jwtService.generateAccessToken(
                        user.getUserId(),
                        user.getRole(),
                        user.getEmail(),
                        user.getCompanyId()
                ),
                refreshTokenService.issueRefreshToken(user),
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        ));
    }
}
