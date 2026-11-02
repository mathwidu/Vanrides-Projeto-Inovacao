package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.regex.Pattern;

@Service
public class AuthService {

    private static final String DUMMY_PASSWORD_HASH =
            "$2a$12$VKn0RYNCrMrtZ0eZ1DdENu36GktpUq5X0yil/hQYZdFhuCLIVzJ6u";
    private static final Pattern BCRYPT_HASH_PATTERN =
            Pattern.compile("^\\$2[aby]\\$12\\$[./A-Za-z0-9]{53}$");

    private final LoginUserDirectoryService loginUserDirectoryService;
    private final PasswordVerifier passwordVerifier;

    @Autowired
    public AuthService(LoginUserDirectoryService loginUserDirectoryService) {
        this(loginUserDirectoryService, AuthService::verifyWithBcrypt);
    }

    AuthService(
            LoginUserDirectoryService loginUserDirectoryService,
            PasswordVerifier passwordVerifier) {
        this.loginUserDirectoryService = Objects.requireNonNull(loginUserDirectoryService);
        this.passwordVerifier = Objects.requireNonNull(passwordVerifier);
    }

    public AuthenticatedUser authenticate(String email, String senha) {
        if (email == null || email.isBlank() || senha == null || senha.isBlank()) {
            return null;
        }

        LoginUserDirectoryService.LoginCredentialSnapshot credentials =
                loginUserDirectoryService.findCredentialsByEmail(email);
        boolean usableCredentials = hasUsableCredentials(credentials);
        String passwordHash = usableCredentials
                ? credentials.passwordHash()
                : DUMMY_PASSWORD_HASH;

        boolean passwordMatches;
        try {
            passwordMatches = passwordVerifier.verify(
                    senha.toCharArray(),
                    passwordHash.toCharArray()
            );
        } catch (RuntimeException invalidPasswordHash) {
            return null;
        }

        if (!usableCredentials || !passwordMatches) {
            return null;
        }

        return new AuthenticatedUser(
                credentials.userId(),
                credentials.role(),
                credentials.email(),
                credentials.name(),
                credentials.motoristaId(),
                credentials.companyId()
        );
    }

    private boolean hasUsableCredentials(LoginUserDirectoryService.LoginCredentialSnapshot credentials) {
        return credentials != null
                && credentials.accessConfigured()
                && credentials.passwordHash() != null
                && BCRYPT_HASH_PATTERN.matcher(credentials.passwordHash()).matches();
    }

    private static boolean verifyWithBcrypt(char[] password, char[] passwordHash) {
        return BCrypt.verifyer().verify(password, passwordHash).verified;
    }

    @FunctionalInterface
    interface PasswordVerifier {
        boolean verify(char[] password, char[] passwordHash);
    }
}
