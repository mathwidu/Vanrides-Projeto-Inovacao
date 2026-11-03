package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.ApiErrorResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.EmailVerificationConfirmRequest;
import com.vanrides.vanrides.dto.EmailVerificationResendRequest;
import com.vanrides.vanrides.dto.EmailVerificationResponse;
import com.vanrides.vanrides.dto.PendingEmailChangeRequest;
import com.vanrides.vanrides.dto.PendingEmailChangeResponse;
import com.vanrides.vanrides.dto.RefreshTokenRequest;
import com.vanrides.vanrides.dto.TokenResponse;
import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationLandingPageRenderer;
import com.vanrides.vanrides.service.EmailVerificationResendQueue;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.LoginUserDirectoryService;
import com.vanrides.vanrides.service.PendingEmailChangeService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestMethod;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/auth")
public class AuthController {

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private EmailVerificationLandingPageRenderer emailVerificationLandingPageRenderer;

    @Autowired
    private EmailVerificationResendQueue emailVerificationResendQueue;

    @Autowired
    private PendingEmailChangeService pendingEmailChangeService;

    @PostMapping("/refresh")
    public ResponseEntity<?> refresh(@RequestBody RefreshTokenRequest request) {
        if (request == null || request.getRefreshToken() == null || request.getRefreshToken().isBlank()) {
            return ResponseEntity.badRequest().body("refreshToken é obrigatório.");
        }

        RefreshTokenService.ValidatedRefreshToken validated = refreshTokenService.validate(request.getRefreshToken());
        if (validated == null) {
            return ResponseEntity.status(401).body("Refresh token inválido ou expirado.");
        }

        AuthenticatedUser user = new AuthenticatedUser(
                validated.entity().getUserId(),
                validated.entity().getUserRole(),
                validated.entity().getUserEmail(),
                "",
                null,
                validated.entity().getCompanyId()
        );
        try {
            emailVerificationService.assertVerifiedAccess(user);
        } catch (EmailVerificationException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorResponse(e.getCode(), e.getMessage(), e.getEmail()));
        }

        String newAccessToken = jwtService.generateAccessToken(
                validated.entity().getUserId(),
                UserRole.fromValue(validated.entity().getUserRole()).claimValue(),
                validated.entity().getUserEmail(),
                validated.entity().getCompanyId()
        );
        String newRefreshToken = refreshTokenService.rotate(validated);

        TokenResponse response = new TokenResponse(
                newAccessToken,
                newRefreshToken,
                "Bearer",
                jwtService.getAccessTokenTtlSeconds()
        );

        return ResponseEntity.ok(response);
    }

    @PostMapping("/email-verifications/confirm")
    public ResponseEntity<?> confirmEmail(@RequestBody EmailVerificationConfirmRequest request) {
        if (request == null || request.getToken() == null || request.getToken().isBlank()) {
            return ResponseEntity.badRequest().body("token é obrigatório.");
        }

        try {
            LoginUserDirectoryService.LoginUserSnapshot snapshot = emailVerificationService.confirm(request.getToken());
            return ResponseEntity.ok(new EmailVerificationResponse("E-mail verificado com sucesso.", snapshot.email()));
        } catch (EmailVerificationException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorResponse(e.getCode(), e.getMessage(), e.getEmail()));
        }
    }

    @GetMapping(value = "/email-verifications/confirm", produces = MediaType.TEXT_HTML_VALUE)
    public ResponseEntity<String> emailVerificationLanding() {
        return emailVerificationLandingResponse(emailVerificationLandingPageRenderer.render());
    }

    @RequestMapping(
            value = "/email-verifications/confirm",
            method = RequestMethod.HEAD,
            produces = MediaType.TEXT_HTML_VALUE
    )
    public ResponseEntity<String> emailVerificationLandingHead() {
        return emailVerificationLandingResponse(null);
    }

    private ResponseEntity<String> emailVerificationLandingResponse(String body) {
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType("text/html;charset=UTF-8"))
                .header(HttpHeaders.CACHE_CONTROL, "no-store, max-age=0")
                .header(HttpHeaders.PRAGMA, "no-cache")
                .header(HttpHeaders.EXPIRES, "0")
                .header("Referrer-Policy", "no-referrer")
                .header("Content-Security-Policy", emailVerificationLandingPageRenderer.contentSecurityPolicy())
                .header("X-Content-Type-Options", "nosniff")
                .header("X-Frame-Options", "DENY")
                .header("X-Robots-Tag", "noindex, nofollow, noarchive, nosnippet")
                .header("Permissions-Policy", "camera=(), microphone=(), geolocation=(), payment=()")
                .body(body);
    }

    @PostMapping("/email-verifications/resend")
    public ResponseEntity<EmailVerificationResponse> resendVerification(@RequestBody EmailVerificationResendRequest request) {
        emailVerificationResendQueue.enqueue(request != null ? request.getEmail() : null);

        return ResponseEntity.accepted()
                .body(new EmailVerificationResponse(
                        "Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada.",
                        request != null ? request.getEmail() : null
                ));
    }

    @PostMapping("/email-verifications/change-email")
    public ResponseEntity<PendingEmailChangeResponse> changePendingEmail(@RequestBody PendingEmailChangeRequest request) {
        if (request == null
                || request.email() == null || request.email().isBlank()
                || request.senha() == null || request.senha().isBlank()
                || request.newEmail() == null || request.newEmail().isBlank()) {
            return ResponseEntity.badRequest()
                    .body(new PendingEmailChangeResponse("Email atual, senha e novo e-mail são obrigatórios.", null));
        }

        String updatedEmail = pendingEmailChangeService.changePendingEmail(request.email(), request.senha(), request.newEmail());
        return ResponseEntity.ok(new PendingEmailChangeResponse(
                "E-mail pendente atualizado com sucesso. Uma nova confirmação foi enviada.",
                updatedEmail
        ));
    }
}
