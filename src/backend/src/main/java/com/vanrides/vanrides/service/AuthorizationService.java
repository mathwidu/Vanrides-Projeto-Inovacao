package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

@Service
public class AuthorizationService {

    @Autowired
    private AuthService authService;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private EmailVerificationService emailVerificationService;

    @Autowired
    private LoginUserDirectoryService loginUserDirectoryService;

    @Value("${vanrides.auth.basic-enabled:true}")
    private boolean basicAuthEnabled = true;

    public AuthenticatedUser authenticate(String authorizationHeader) {
        return authenticate(authorizationHeader, false);
    }

    public AuthenticatedUser authenticate(String authorizationHeader, boolean allowUnverifiedAccess) {
        if (authorizationHeader == null || authorizationHeader.isBlank()) {
            throw new IllegalArgumentException("Cabeçalho Authorization ausente");
        }

        if (authorizationHeader.startsWith("Bearer ")) {
            return authenticateBearerToken(authorizationHeader.substring("Bearer ".length()).trim(), allowUnverifiedAccess);
        }

        if (authorizationHeader.startsWith("Basic ")) {
            if (!basicAuthEnabled) {
                throw new IllegalArgumentException("Basic Auth desabilitado neste ambiente");
            }
            return authenticateBasicCredentials(authorizationHeader.substring("Basic ".length()).trim(), allowUnverifiedAccess);
        }

        throw new IllegalArgumentException("Authorization deve usar Bearer ou Basic");
    }

    private AuthenticatedUser authenticateBearerToken(String token, boolean allowUnverifiedAccess) {
        if (!jwtService.isTokenValid(token)) {
            throw new IllegalArgumentException("Token inválido ou expirado");
        }

        Claims claims = jwtService.parseAndValidate(token);
        String role = claims.get("role", String.class);
        String subject = claims.getSubject();
        String email = claims.get("email", String.class);
        if (role == null || subject == null) {
            throw new IllegalArgumentException("Token sem claims obrigatórias");
        }

        long userId;
        try {
            userId = Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Token com subject inválido", e);
        }

        UserRole resolvedRole = UserRole.fromValue(role);
        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                requireCurrentSnapshot(resolvedRole, userId, "Token inválido ou expirado");
        requireExactCurrentEmail(email, snapshot, "Token inválido ou expirado");

        AuthenticatedUser currentUser = new AuthenticatedUser(
                snapshot.userId(),
                snapshot.role().claimValue(),
                snapshot.email(),
                null,
                null,
                snapshot.companyId()
        );
        if (!allowUnverifiedAccess) {
            emailVerificationService.assertVerifiedAccess(currentUser);
        }
        return currentUser;
    }

    private AuthenticatedUser authenticateBasicCredentials(String base64Credentials, boolean allowUnverifiedAccess) {
        byte[] credDecoded;
        try {
            credDecoded = Base64.getDecoder().decode(base64Credentials);
        } catch (IllegalArgumentException e) {
            throw new IllegalArgumentException("Credenciais Basic inválidas", e);
        }

        String credentials = new String(credDecoded, StandardCharsets.UTF_8);
        String[] values = credentials.split(":", 2);

        if (values.length != 2) {
            throw new IllegalArgumentException("Formato de autenticação inválido");
        }

        AuthenticatedUser user = authService.authenticate(values[0], values[1]);
        if (user == null) {
            throw new IllegalArgumentException("Credenciais inválidas");
        }

        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                requireCurrentSnapshot(
                        user.getRoleEnum(),
                        user.getUserId(),
                        "Credenciais inválidas"
                );
        requireExactCurrentEmail(user.getEmail(), snapshot, "Credenciais inválidas");

        AuthenticatedUser currentUser = new AuthenticatedUser(
                snapshot.userId(),
                snapshot.role().claimValue(),
                snapshot.email(),
                user.getNome(),
                user.getMotoristaId(),
                snapshot.companyId()
        );
        if (!allowUnverifiedAccess) {
            emailVerificationService.assertVerifiedAccess(currentUser);
        }
        return currentUser;
    }

    private void requireExactCurrentEmail(
            String credentialEmail,
            LoginUserDirectoryService.LoginUserSnapshot snapshot,
            String rejectionMessage) {
        if (credentialEmail == null || !credentialEmail.equals(snapshot.email())) {
            throw new IllegalArgumentException(rejectionMessage);
        }
    }

    private LoginUserDirectoryService.LoginUserSnapshot requireCurrentSnapshot(
            UserRole role,
            Long userId,
            String rejectionMessage) {
        try {
            return loginUserDirectoryService.requireByRoleAndId(role, userId);
        } catch (IllegalArgumentException missingOrInconsistentIdentity) {
            throw new IllegalArgumentException(rejectionMessage);
        }
    }
}
