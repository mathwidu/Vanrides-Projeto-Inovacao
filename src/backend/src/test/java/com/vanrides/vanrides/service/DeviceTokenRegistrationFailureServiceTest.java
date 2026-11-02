package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.repository.DeviceTokenRepository;
import org.hibernate.exception.ConstraintViolationException;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenRegistrationFailureServiceTest {

    @Mock private DeviceTokenRepository deviceTokenRepository;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks
    private DeviceTokenService service;

    @Test
    void shouldFailClosedBeforeLoadingDeviceWhenExactOwnerCannotBeLocked() {
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "stale@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "stale@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason())
                    .isEqualTo("Não foi possível registrar o dispositivo. Autentique-se novamente.")
                    .doesNotContain("stale@vanrides.dev", "PASSENGER", "21");
        });

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void shouldMapOnlyTheDeviceOwnerGuardConstraintToGenericConflict() {
        DataIntegrityViolationException guarded = integrityViolation(
                "ck_device_token_login_identity_owner"
        );
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.push@vanrides.dev"));
        when(deviceTokenRepository.insertRegistrationIfAbsent(
                eq(sha256("token-123")),
                eq("token-123"),
                eq("IOS"),
                eq(21L),
                eq("PASSENGER"),
                eq("passageiro.push@vanrides.dev"),
                isNull(),
                any(Instant.class)
        )).thenThrow(guarded);
        when(loginIdentityService.hasConstraint(
                guarded, "ck_device_token_login_identity_owner"
        )).thenReturn(true);

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT);
            assertThat(exception.getReason())
                    .isEqualTo("Não foi possível registrar o dispositivo. Autentique-se novamente.");
        });
    }

    @Test
    void shouldMapTokenRowLockFailureToGenericRetryableResponse() {
        CannotAcquireLockException lockFailure = new CannotAcquireLockException("token lock");
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.push@vanrides.dev"));
        when(deviceTokenRepository.findByTokenHashForUpdate(sha256("token-123")))
                .thenThrow(lockFailure);

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(exception.getReason())
                    .isEqualTo("Não foi possível registrar o dispositivo agora. Tente novamente.")
                    .doesNotContain("token lock");
        });
    }

    @Test
    void shouldReturnRetryableResponseWhenEnsuredTokenCannotBeLoaded() {
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.push@vanrides.dev"));
        when(deviceTokenRepository.findByTokenHashForUpdate(sha256("token-123")))
                .thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(exception.getReason())
                    .isEqualTo("Não foi possível registrar o dispositivo agora. Tente novamente.");
        });
    }

    @Test
    void shouldMapRegistryLockFailureToGenericRetryableResponse() {
        CannotAcquireLockException lockFailure = new CannotAcquireLockException("deadlock");
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenThrow(lockFailure);

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
            assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.SERVICE_UNAVAILABLE);
            assertThat(exception.getReason())
                    .isEqualTo("Não foi possível registrar o dispositivo agora. Tente novamente.")
                    .doesNotContain("deadlock");
        });

        verifyNoInteractions(deviceTokenRepository);
    }

    @Test
    void shouldPreserveUnknownIntegrityFailure() {
        DataIntegrityViolationException unknown = integrityViolation(
                "ck_device_token_unrelated_integrity"
        );
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.push@vanrides.dev"));
        when(deviceTokenRepository.insertRegistrationIfAbsent(
                eq(sha256("token-123")),
                eq("token-123"),
                eq("IOS"),
                eq(21L),
                eq("PASSENGER"),
                eq("passageiro.push@vanrides.dev"),
                isNull(),
                any(Instant.class)
        )).thenThrow(unknown);

        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        )).isSameAs(unknown);
    }

    private static String sha256(String rawToken) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 não disponível", e);
        }
    }

    private DataIntegrityViolationException integrityViolation(String constraintName) {
        ConstraintViolationException hibernate = new ConstraintViolationException(
                "constraint failure",
                new SQLException("write rejected", "23514"),
                "insert",
                constraintName
        );
        return new DataIntegrityViolationException("write failed", hibernate);
    }
}
