package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.RefreshToken;
import com.vanrides.vanrides.repository.RefreshTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class RefreshTokenServiceTest {

    @Mock
    private RefreshTokenRepository refreshTokenRepository;

    @Mock
    private LoginIdentityService loginIdentityService;

    @InjectMocks
    private RefreshTokenService refreshTokenService;

    @Test
    void shouldValidateRefreshTokenOnlyForCurrentGlobalOwner() {
        RefreshToken token = token(UserRole.PASSENGER.claimValue(), 12L, "passenger@example.com");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(loginIdentityService.isOwnedBy(
                "passenger@example.com",
                UserRole.PASSENGER,
                12L
        )).thenReturn(true);

        RefreshTokenService.ValidatedRefreshToken validated = refreshTokenService.validate("valid-token");

        assertThat(validated).isNotNull();
        assertThat(validated.entity()).isSameAs(token);
        assertThat(token.isRevoked()).isFalse();
        verify(refreshTokenRepository, never()).save(token);
    }

    @Test
    void shouldRevokeAndRejectRefreshTokenWhenEmailWasReusedByAnotherOwner() {
        RefreshToken token = token(UserRole.DRIVER.claimValue(), 23L, "reused@example.com");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));
        when(loginIdentityService.isOwnedBy("reused@example.com", UserRole.DRIVER, 23L))
                .thenReturn(false);

        assertThat(refreshTokenService.validate("stale-token")).isNull();

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
    }

    @Test
    void shouldRevokeAndRejectRefreshTokenWithUnknownRole() {
        RefreshToken token = token("UNKNOWN_ROLE", 34L, "unknown@example.com");
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        assertThat(refreshTokenService.validate("unknown-role-token")).isNull();

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
        verifyNoInteractions(loginIdentityService);
    }

    @Test
    void shouldRevokeExpiredTokenWithoutResolvingIdentity() {
        RefreshToken token = token(UserRole.COMPANY_ADMIN.claimValue(), 45L, "admin@example.com");
        token.setExpiresAt(Instant.now().minusSeconds(1));
        when(refreshTokenRepository.findByTokenHashAndRevokedFalse(anyString()))
                .thenReturn(Optional.of(token));

        assertThat(refreshTokenService.validate("expired-token")).isNull();

        assertThat(token.isRevoked()).isTrue();
        verify(refreshTokenRepository).save(token);
        verifyNoInteractions(loginIdentityService);
    }

    @Test
    void shouldIgnoreBlankTokenWithoutQueryingPersistenceOrIdentity() {
        assertThat(refreshTokenService.validate("  ")).isNull();

        verifyNoInteractions(refreshTokenRepository, loginIdentityService);
    }

    private RefreshToken token(String role, Long userId, String email) {
        RefreshToken token = new RefreshToken();
        token.setUserRole(role);
        token.setUserId(userId);
        token.setUserEmail(email);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        token.setRevoked(false);
        return token;
    }
}
