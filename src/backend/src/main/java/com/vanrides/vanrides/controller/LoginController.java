package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.ApiErrorResponse;
import com.vanrides.vanrides.dto.LoginRequest;
import com.vanrides.vanrides.dto.LoginResponse;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/login")
public class LoginController {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private RefreshTokenService refreshTokenService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private PassengerOnboardingService passengerOnboardingService;

    @Autowired
    private DriverOnboardingService driverOnboardingService;

    @PostMapping
    public ResponseEntity<?> login(@RequestBody LoginRequest loginRequest) {
        if (loginRequest == null
                || loginRequest.getEmail() == null
                || loginRequest.getEmail().isBlank()
                || loginRequest.getSenha() == null
                || loginRequest.getSenha().isBlank()) {
            return ResponseEntity.badRequest().body("Email e senha são obrigatórios.");
        }

        String senha = loginRequest.getSenha();

        AuthenticatedUser authenticatedUser = authService.authenticate(loginRequest.getEmail(), senha);
        if (authenticatedUser == null) {
            return ResponseEntity.status(401).body("Credenciais inválidas.");
        }

        try {
            emailVerificationService.assertVerifiedAccess(authenticatedUser);
        } catch (EmailVerificationException e) {
            return ResponseEntity.status(e.getHttpStatus())
                    .body(new ApiErrorResponse(e.getCode(), e.getMessage(), e.getEmail()));
        }

        String tipoUsuario;
        if (authenticatedUser.isCompanyAdmin()) {
            tipoUsuario = "Admin";
        } else if (authenticatedUser.isPassenger()) {
            tipoUsuario = "Passageiro";
        } else if (authenticatedUser.isGuardian()) {
            tipoUsuario = "Responsavel";
        } else {
            tipoUsuario = "Motorista";
        }
        String mensagem = "Bem-vindo, " + tipoUsuario + " " + authenticatedUser.getNome() + "!";

        LoginResponse response = new LoginResponse(
                tipoUsuario,
                mensagem,
                authenticatedUser.getUserId(),
                authenticatedUser.getMotoristaId()
        );
        if (authenticatedUser.isPassenger()) {
            response.setCompanyLinkStatus(
                    passengerOnboardingService.getStatus(authenticatedUser.getUserId()).companyLinkStatus()
            );
        } else if (authenticatedUser.isDriver()) {
            response.setCompanyLinkStatus(
                    driverOnboardingService.getStatus(authenticatedUser.getUserId()).companyLinkStatus()
            );
        } else {
            response.setCompanyLinkStatus("COMPANY_LINKED");
        }
        response.setTokenType("Bearer");
        response.setAccessToken(
                jwtService.generateAccessToken(
                        authenticatedUser.getUserId(),
                        authenticatedUser.getRole(),
                        authenticatedUser.getEmail(),
                        authenticatedUser.getCompanyId()
                )
        );
        response.setRefreshToken(refreshTokenService.issueRefreshToken(authenticatedUser));
        response.setExpiresInSeconds(jwtService.getAccessTokenTtlSeconds());

        return ResponseEntity.ok(response);
    }
}
