package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.RefreshToken;
import com.vanrides.vanrides.repository.RefreshTokenRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;
import java.util.UUID;

@Service
public class RefreshTokenService {

    @Value("${vanrides.auth.refresh-token-ttl-days:30}")
    private long refreshTokenTtlDays;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private LoginIdentityService loginIdentityService;

    public String issueRefreshToken(AuthenticatedUser user) {
        String rawToken = generateRawToken();

        RefreshToken refreshToken = new RefreshToken();
        refreshToken.setTokenHash(hashToken(rawToken));
        refreshToken.setUserId(user.getUserId());
        refreshToken.setUserRole(user.getRole());
        refreshToken.setUserEmail(user.getEmail());
        refreshToken.setCompanyId(user.getCompanyId());
        refreshToken.setExpiresAt(Instant.now().plus(Duration.ofDays(refreshTokenTtlDays)));
        refreshToken.setRevoked(false);

        refreshTokenRepository.save(refreshToken);
        return rawToken;
    }

    @Transactional
    public ValidatedRefreshToken validate(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            return null;
        }

        Optional<RefreshToken> found = refreshTokenRepository.findByTokenHashAndRevokedFalse(hashToken(rawToken));
        if (found.isEmpty()) {
            return null;
        }

        RefreshToken entity = found.get();
        if (entity.getExpiresAt().isBefore(Instant.now())) {
            return revokeAndReject(entity);
        }

        UserRole role;
        try {
            role = UserRole.fromValue(entity.getUserRole());
        } catch (IllegalArgumentException exception) {
            return revokeAndReject(entity);
        }

        if (!loginIdentityService.isOwnedBy(entity.getUserEmail(), role, entity.getUserId())) {
            return revokeAndReject(entity);
        }

        return new ValidatedRefreshToken(entity);
    }

    public String rotate(ValidatedRefreshToken validatedRefreshToken) {
        RefreshToken entity = validatedRefreshToken.entity();
        entity.setRevoked(true);
        refreshTokenRepository.save(entity);

        AuthenticatedUser user = new AuthenticatedUser(
                entity.getUserId(),
                entity.getUserRole(),
                entity.getUserEmail(),
                "",
                null,
                entity.getCompanyId()
        );
        return issueRefreshToken(user);
    }

    @Transactional
    public void revokeAllForUser(Long userId, String userRole) {
        refreshTokenRepository.revokeAllByUserIdAndUserRole(userId, userRole);
    }

    private String generateRawToken() {
        return UUID.randomUUID().toString() + "." + UUID.randomUUID();
    }

    private String hashToken(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private ValidatedRefreshToken revokeAndReject(RefreshToken entity) {
        entity.setRevoked(true);
        refreshTokenRepository.save(entity);
        return null;
    }

    public record ValidatedRefreshToken(RefreshToken entity) {}
}
