package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.DeviceTokenInvalidateRequest;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.model.DeviceToken;
import com.vanrides.vanrides.model.DeviceTokenPlatform;
import com.vanrides.vanrides.repository.DeviceTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class DeviceTokenServiceTest {

    @Mock
    private DeviceTokenRepository deviceTokenRepository;

    @Mock
    private LoginIdentityService loginIdentityService;

    @InjectMocks
    private DeviceTokenService service;

    @Test
    void shouldRegisterNewDeviceTokenForAuthenticatedUser() {
        DeviceToken inserted = new DeviceToken();
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.push@vanrides.dev"));
        when(deviceTokenRepository.findByTokenHashForUpdate(sha256("token-123")))
                .thenReturn(Optional.of(inserted));
        when(deviceTokenRepository.saveAndFlush(any(DeviceToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        );

        ArgumentCaptor<DeviceToken> captor = ArgumentCaptor.forClass(DeviceToken.class);
        InOrder order = inOrder(loginIdentityService, deviceTokenRepository);
        order.verify(loginIdentityService).lockExactOwnerForDeviceRegistration(
                "passageiro.push@vanrides.dev", UserRole.PASSENGER, 21L
        );
        order.verify(deviceTokenRepository).insertRegistrationIfAbsent(
                eq(sha256("token-123")),
                eq("token-123"),
                eq("IOS"),
                eq(21L),
                eq("PASSENGER"),
                eq("passageiro.push@vanrides.dev"),
                isNull(),
                any(Instant.class)
        );
        order.verify(deviceTokenRepository).findByTokenHashForUpdate(sha256("token-123"));
        order.verify(deviceTokenRepository).saveAndFlush(captor.capture());

        DeviceToken saved = captor.getValue();
        assertThat(saved.getTokenValue()).isEqualTo("token-123");
        assertThat(saved.getTokenHash()).isEqualTo(sha256("token-123"));
        assertThat(saved.getPlatform()).isEqualTo(DeviceTokenPlatform.IOS);
        assertThat(saved.getUserId()).isEqualTo(21L);
        assertThat(saved.getUserRole()).isEqualTo("PASSENGER");
        assertThat(saved.getUserEmail()).isEqualTo("passageiro.push@vanrides.dev");
        assertThat(saved.getCompanyId()).isNull();
        assertThat(saved.isActive()).isTrue();
        assertThat(saved.getLastRegisteredAt()).isNotNull();
        assertThat(saved.getInvalidatedAt()).isNull();
    }

    @Test
    void shouldUpdateExistingTokenIdempotentlyForSameUser() {
        DeviceToken existing = existingToken("token-123", UserRole.PASSENGER, 21L);
        existing.setInvalidatedAt(Instant.parse("2026-01-01T10:00:00Z"));
        existing.setActive(false);

        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "passageiro.novo@vanrides.dev", UserRole.PASSENGER, 21L
        )).thenReturn(Optional.of("passageiro.novo@vanrides.dev"));
        when(deviceTokenRepository.findByTokenHashForUpdate(sha256("token-123")))
                .thenReturn(Optional.of(existing));
        when(deviceTokenRepository.saveAndFlush(any(DeviceToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.novo@vanrides.dev",
                66L,
                new DeviceTokenRegistrationRequest(" token-123 ", "android")
        );

        assertThat(existing.getPlatform()).isEqualTo(DeviceTokenPlatform.ANDROID);
        assertThat(existing.getUserId()).isEqualTo(21L);
        assertThat(existing.getUserRole()).isEqualTo("PASSENGER");
        assertThat(existing.getUserEmail()).isEqualTo("passageiro.novo@vanrides.dev");
        assertThat(existing.getCompanyId()).isEqualTo(66L);
        assertThat(existing.isActive()).isTrue();
        assertThat(existing.getInvalidatedAt()).isNull();
        verify(deviceTokenRepository).saveAndFlush(existing);
    }

    @Test
    void shouldReassignDuplicateTokenToCurrentAuthenticatedUser() {
        DeviceToken existing = existingToken("token-123", UserRole.DRIVER, 44L);
        when(loginIdentityService.lockExactOwnerForDeviceRegistration(
                "responsavel.push@vanrides.dev", UserRole.GUARDIAN, 71L
        )).thenReturn(Optional.of("responsavel.push@vanrides.dev"));
        when(deviceTokenRepository.findByTokenHashForUpdate(sha256("token-123")))
                .thenReturn(Optional.of(existing));
        when(deviceTokenRepository.saveAndFlush(any(DeviceToken.class)))
                .thenAnswer(invocation -> invocation.getArgument(0));

        service.register(
                UserRole.GUARDIAN,
                71L,
                "responsavel.push@vanrides.dev",
                66L,
                new DeviceTokenRegistrationRequest("token-123", "ios")
        );

        assertThat(existing.getUserId()).isEqualTo(71L);
        assertThat(existing.getUserRole()).isEqualTo("GUARDIAN");
        assertThat(existing.getUserEmail()).isEqualTo("responsavel.push@vanrides.dev");
        assertThat(existing.getCompanyId()).isEqualTo(66L);
        assertThat(existing.isActive()).isTrue();
        verify(deviceTokenRepository).saveAndFlush(existing);
    }

    @Test
    void shouldRejectBlankToken() {
        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest(" ", "ios")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(deviceTokenRepository, loginIdentityService);
    }

    @Test
    void shouldRejectUnknownPlatform() {
        assertThatThrownBy(() -> service.register(
                UserRole.PASSENGER,
                21L,
                "passageiro.push@vanrides.dev",
                null,
                new DeviceTokenRegistrationRequest("token-123", "blackberry")
        ))
                .isInstanceOf(ResponseStatusException.class)
                .extracting("statusCode")
                .isEqualTo(HttpStatus.BAD_REQUEST);

        verifyNoInteractions(deviceTokenRepository, loginIdentityService);
    }

    @Test
    void shouldInvalidateOwnedToken() {
        DeviceToken existing = existingToken("token-123", UserRole.DRIVER, 44L);
        when(deviceTokenRepository.findByTokenHashAndUserIdAndUserRole(
                sha256("token-123"),
                44L,
                "DRIVER"
        )).thenReturn(Optional.of(existing));

        service.invalidate(UserRole.DRIVER, 44L, new DeviceTokenInvalidateRequest("token-123"));

        assertThat(existing.isActive()).isFalse();
        assertThat(existing.getInvalidatedAt()).isNotNull();
        verify(deviceTokenRepository).save(existing);
    }

    @Test
    void shouldIgnoreInvalidateForTokenThatIsNotOwnedByUser() {
        when(deviceTokenRepository.findByTokenHashAndUserIdAndUserRole(
                sha256("token-123"),
                44L,
                "DRIVER"
        )).thenReturn(Optional.empty());

        service.invalidate(UserRole.DRIVER, 44L, new DeviceTokenInvalidateRequest("token-123"));

        verify(deviceTokenRepository, never()).save(any(DeviceToken.class));
    }

    private DeviceToken existingToken(String token, UserRole role, Long userId) {
        DeviceToken entity = new DeviceToken();
        entity.setTokenValue(token);
        entity.setTokenHash(sha256(token));
        entity.setPlatform(DeviceTokenPlatform.IOS);
        entity.setUserId(userId);
        entity.setUserRole(role.claimValue());
        entity.setUserEmail("old@vanrides.dev");
        entity.setCompanyId(66L);
        entity.setActive(true);
        entity.setLastRegisteredAt(Instant.parse("2026-01-01T10:00:00Z"));
        return entity;
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

}
