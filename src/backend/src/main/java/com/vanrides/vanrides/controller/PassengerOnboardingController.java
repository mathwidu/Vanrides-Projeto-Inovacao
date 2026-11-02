package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.PassengerCompanyRequestCreateRequest;
import com.vanrides.vanrides.dto.PassengerInviteCodeRedeemRequest;
import com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse;
import com.vanrides.vanrides.dto.TokenResponse;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.RefreshTokenService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

@RestController
@RequestMapping("/passageiro-onboarding")
public class PassengerOnboardingController {

    @Autowired
    private PassengerOnboardingService passengerOnboardingService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @GetMapping("/status")
    public ResponseEntity<PassengerOnboardingStatusResponse> status(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER);
        return ResponseEntity.ok(passengerOnboardingService.getStatus(AuthorizationGuard.getRequiredUserId(request)));
    }

    @GetMapping("/companies")
    public ResponseEntity<?> searchCompanies(@RequestParam(value = "query", required = false) String query,
                                             HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER);
        return ResponseEntity.ok(passengerOnboardingService.searchCompanies(query));
    }

    @PostMapping("/requests")
    public ResponseEntity<PassengerOnboardingStatusResponse> createRequest(@RequestBody PassengerCompanyRequestCreateRequest body,
                                                                           HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER);
        if (body == null || body.companyId() == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "companyId é obrigatório.");
        }
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(passengerOnboardingService.createRequest(AuthorizationGuard.getRequiredUserId(request), body.companyId()));
    }

    @PostMapping("/invite-codes/redeem")
    public ResponseEntity<PassengerOnboardingStatusResponse> redeemInviteCode(@RequestBody PassengerInviteCodeRedeemRequest body,
                                                                               HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER);
        if (body == null || body.code() == null || body.code().isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "code é obrigatório.");
        }
        return ResponseEntity.ok(passengerOnboardingService.redeemInviteCode(AuthorizationGuard.getRequiredUserId(request), body.code()));
    }

    @PostMapping("/session-upgrade")
    public ResponseEntity<TokenResponse> upgradeSession(HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER);
        Long passageiroId = AuthorizationGuard.getRequiredUserId(request);
        Passageiro passageiro = passengerOnboardingService.requireOperationalPassenger(passageiroId);

        AuthenticatedUser user = new AuthenticatedUser(
                passageiro.getId(),
                UserRole.PASSENGER,
                passageiro.getEmail(),
                passageiro.getNome(),
                passageiro.getMotorista() != null ? passageiro.getMotorista().getId() : null,
                passageiro.getCompany() != null ? passageiro.getCompany().getId() : null
        );

        refreshTokenService.revokeAllForUser(passageiroId, UserRole.PASSENGER.claimValue());

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
