package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DeviceTokenInvalidateRequest;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.DeviceTokenPlatform;
import com.vanrides.vanrides.repository.DeviceTokenRepository;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;

@Service
public class DeviceTokenService {

    private static final int MAX_TOKEN_LENGTH = 1024;
    private static final String DEVICE_OWNER_CONSTRAINT =
            "ck_device_token_login_identity_owner";
    private static final String REGISTRATION_CONFLICT_MESSAGE =
            "Não foi possível registrar o dispositivo. Autentique-se novamente.";
    private static final String REGISTRATION_RETRY_MESSAGE =
            "Não foi possível registrar o dispositivo agora. Tente novamente.";

    private final DeviceTokenRepository deviceTokenRepository;
    private final LoginIdentityService loginIdentityService;

    public DeviceTokenService(
            DeviceTokenRepository deviceTokenRepository,
            LoginIdentityService loginIdentityService) {
        this.deviceTokenRepository = deviceTokenRepository;
        this.loginIdentityService = loginIdentityService;
    }

    @Transactional
    public void register(UserRole role,
                         Long userId,
                         String userEmail,
                         Long companyId,
                         DeviceTokenRegistrationRequest request) {
        String token = requireToken(request == null ? null : request.token());
        DeviceTokenPlatform platform = requirePlatform(request == null ? null : request.platform());
        String tokenHash = hashToken(token);

        try {
            String normalizedEmail = loginIdentityService
                    .lockExactOwnerForDeviceRegistration(userEmail, role, userId)
                    .orElseThrow(this::registrationConflict);

            Instant registeredAt = Instant.now();
            deviceTokenRepository.insertRegistrationIfAbsent(
                    tokenHash,
                    token,
                    platform.name(),
                    userId,
                    role.claimValue(),
                    normalizedEmail,
                    companyId,
                    registeredAt
            );
            DeviceToken entity = deviceTokenRepository.findByTokenHashForUpdate(tokenHash)
                    .orElseThrow(this::registrationRetry);
            entity.setTokenHash(tokenHash);
            entity.setTokenValue(token);
            entity.setPlatform(platform);
            entity.setUserId(userId);
            entity.setUserRole(role.claimValue());
            entity.setUserEmail(normalizedEmail);
            entity.setCompanyId(companyId);
            entity.setActive(true);
            entity.setInvalidatedAt(null);
            entity.setLastRegisteredAt(registeredAt);

            deviceTokenRepository.saveAndFlush(entity);
        } catch (DataIntegrityViolationException exception) {
            if (loginIdentityService.hasConstraint(exception, DEVICE_OWNER_CONSTRAINT)) {
                throw registrationConflict();
            }
            throw exception;
        } catch (PessimisticLockingFailureException exception) {
            throw new ResponseStatusException(
                    HttpStatus.SERVICE_UNAVAILABLE,
                    REGISTRATION_RETRY_MESSAGE,
                    exception
            );
        }
    }

    @Transactional
    public void invalidate(UserRole role, Long userId, DeviceTokenInvalidateRequest request) {
        String token = requireToken(request == null ? null : request.token());
        String tokenHash = hashToken(token);

        deviceTokenRepository
                .findByTokenHashAndUserIdAndUserRole(tokenHash, userId, role.claimValue())
                .ifPresent(entity -> {
                    entity.setActive(false);
                    entity.setInvalidatedAt(Instant.now());
                    deviceTokenRepository.save(entity);
                });
    }

    private String requireToken(String rawToken) {
        if (rawToken == null || rawToken.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token é obrigatório.");
        }
        String token = rawToken.trim();
        if (token.length() > MAX_TOKEN_LENGTH) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "token excede o tamanho máximo.");
        }
        return token;
    }

    private DeviceTokenPlatform requirePlatform(String rawPlatform) {
        try {
            return DeviceTokenPlatform.fromValue(rawPlatform);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, e.getMessage());
        }
    }

    private ResponseStatusException registrationConflict() {
        return new ResponseStatusException(
                HttpStatus.CONFLICT,
                REGISTRATION_CONFLICT_MESSAGE
        );
    }

    private ResponseStatusException registrationRetry() {
        return new ResponseStatusException(
                HttpStatus.SERVICE_UNAVAILABLE,
                REGISTRATION_RETRY_MESSAGE
        );
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
}
