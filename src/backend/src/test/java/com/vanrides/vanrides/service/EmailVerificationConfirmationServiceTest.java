package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.EmailVerificationToken;
import com.vanrides.vanrides.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationConfirmationServiceTest {

    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private LoginUserDirectoryService loginUserDirectoryService;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks
    private EmailVerificationService emailVerificationService;

    @Test
    void shouldConfirmOnlyWhenTokenRoleIdAndEmailStillOwnTheGlobalIdentity() {
        Long userId = 52L;
        EmailVerificationToken token = token(UserRole.PASSENGER.claimValue(), userId, "owner@example.com");
        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                snapshot(userId, UserRole.PASSENGER, "owner@example.com", false, true);
        when(emailVerificationTokenRepository.findConfirmationCandidateByTokenHash(hashToken("valid-token")))
                .thenReturn(Optional.of(candidate(token)));
        when(loginUserDirectoryService.lockAccountByRoleAndId(UserRole.PASSENGER, userId))
                .thenReturn(Optional.of(snapshot));
        when(loginIdentityService.lockExactOwnerForEmailVerification(
                "owner@example.com", UserRole.PASSENGER, userId))
                .thenReturn(Optional.of("owner@example.com"));
        when(emailVerificationTokenRepository.findUnconsumedByIdForUpdate(token.getId()))
                .thenReturn(Optional.of(token));
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.PASSENGER, userId))
                .thenReturn(snapshot);
        when(emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.PASSENGER.claimValue(),
                userId
        )).thenReturn(List.of(token));

        LoginUserDirectoryService.LoginUserSnapshot confirmed =
                emailVerificationService.confirm("valid-token");

        assertThat(confirmed).isSameAs(snapshot);
        verify(loginUserDirectoryService).markVerified(any(), any(), any());
        verify(emailVerificationTokenRepository).flush();

        InOrder lockOrder = inOrder(
                loginUserDirectoryService,
                loginIdentityService,
                emailVerificationTokenRepository
        );
        lockOrder.verify(loginUserDirectoryService)
                .lockAccountByRoleAndId(UserRole.PASSENGER, userId);
        lockOrder.verify(loginIdentityService)
                .lockExactOwnerForEmailVerification(
                        "owner@example.com", UserRole.PASSENGER, userId);
        lockOrder.verify(emailVerificationTokenRepository)
                .findUnconsumedByIdForUpdate(token.getId());
        lockOrder.verify(loginUserDirectoryService).markVerified(any(), any(), any());
    }

    @Test
    void shouldConsumeTokenAndFailClosedWhenIdentityWasDeletedOrReused() {
        Long userId = 63L;
        EmailVerificationToken token = token(UserRole.DRIVER.claimValue(), userId, "reused@example.com");
        when(emailVerificationTokenRepository.findConfirmationCandidateByTokenHash(hashToken("stale-token")))
                .thenReturn(Optional.of(candidate(token)));
        when(loginUserDirectoryService.lockAccountByRoleAndId(UserRole.DRIVER, userId))
                .thenReturn(Optional.of(snapshot(
                        userId, UserRole.DRIVER, "reused@example.com", false, true)));
        when(loginIdentityService.lockExactOwnerForEmailVerification(
                "reused@example.com", UserRole.DRIVER, userId))
                .thenReturn(Optional.empty());
        when(emailVerificationTokenRepository.findUnconsumedByIdForUpdate(token.getId()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.confirm("stale-token"))
                .isInstanceOfSatisfying(EmailVerificationException.class, exception -> {
                    assertThat(exception.getCode())
                            .isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN");
                    assertThat(exception.getEmail()).isNull();
                });

        assertThat(token.getConsumedAt()).isNotNull();
        verify(emailVerificationTokenRepository).save(token);
        verify(loginUserDirectoryService, never()).markVerified(any(), any(), any());
    }

    @Test
    void shouldFailClosedWhenStoredAccountEmailIsNotExactlyCanonical() {
        Long userId = 64L;
        EmailVerificationToken token = token(UserRole.PASSENGER.claimValue(), userId, "owner@example.com");
        when(emailVerificationTokenRepository.findConfirmationCandidateByTokenHash(hashToken("case-drift-token")))
                .thenReturn(Optional.of(candidate(token)));
        when(loginUserDirectoryService.lockAccountByRoleAndId(UserRole.PASSENGER, userId))
                .thenReturn(Optional.of(snapshot(
                        userId,
                        UserRole.PASSENGER,
                        "Owner@example.com",
                        false,
                        true
                )));
        when(loginIdentityService.lockExactOwnerForEmailVerification(
                "owner@example.com", UserRole.PASSENGER, userId))
                .thenReturn(Optional.of("owner@example.com"));
        when(emailVerificationTokenRepository.findUnconsumedByIdForUpdate(token.getId()))
                .thenReturn(Optional.of(token));

        assertThatThrownBy(() -> emailVerificationService.confirm("case-drift-token"))
                .isInstanceOfSatisfying(EmailVerificationException.class, exception ->
                        assertThat(exception.getCode()).isEqualTo("INVALID_EMAIL_VERIFICATION_TOKEN"));

        assertThat(token.getConsumedAt()).isNotNull();
        verify(emailVerificationTokenRepository).save(token);
        verify(loginUserDirectoryService, never()).markVerified(any(), any(), any());
    }

    private LoginUserDirectoryService.LoginUserSnapshot snapshot(
            Long userId,
            UserRole role,
            String email,
            boolean verified,
            boolean accessConfigured) {
        return new LoginUserDirectoryService.LoginUserSnapshot(
                userId,
                role,
                email,
                null,
                verified,
                accessConfigured,
                null
        );
    }

    private EmailVerificationToken token(String role, Long userId, String email) {
        EmailVerificationToken token = new EmailVerificationToken();
        ReflectionTestUtils.setField(token, "id", userId + 1000L);
        token.setUserRole(role);
        token.setUserId(userId);
        token.setEmailSnapshot(email);
        token.setTokenHash("stored-hash-" + userId);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private EmailVerificationTokenRepository.ConfirmationCandidate candidate(
            EmailVerificationToken token) {
        return new Candidate(
                token.getId(),
                token.getUserRole(),
                token.getUserId(),
                token.getEmailSnapshot(),
                token.getTokenHash()
        );
    }

    private String hashToken(String rawToken) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(rawToken.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException(exception);
        }
    }

    private record Candidate(
            Long id,
            String userRole,
            Long userId,
            String emailSnapshot,
            String tokenHash)
            implements EmailVerificationTokenRepository.ConfirmationCandidate {

        @Override
        public Long getId() {
            return id;
        }

        @Override
        public String getUserRole() {
            return userRole;
        }

        @Override
        public Long getUserId() {
            return userId;
        }

        @Override
        public String getEmailSnapshot() {
            return emailSnapshot;
        }

        @Override
        public String getTokenHash() {
            return tokenHash;
        }
    }
}
