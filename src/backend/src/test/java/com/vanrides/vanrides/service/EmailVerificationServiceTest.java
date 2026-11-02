package com.vanrides.vanrides.service;

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.EmailVerificationToken;
import com.vanrides.vanrides.repository.EmailVerificationTokenRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.ArgumentCaptor;
import org.slf4j.LoggerFactory;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class EmailVerificationServiceTest {

    @Mock private EmailVerificationTokenRepository emailVerificationTokenRepository;
    @Mock private LoginUserDirectoryService loginUserDirectoryService;
    @Mock private EmailDeliveryGateway emailDeliveryGateway;
    @Mock private RefreshTokenService refreshTokenService;
    @Mock private LoginIdentityService loginIdentityService;

    @InjectMocks private EmailVerificationService emailVerificationService;

    @Test
    void shouldKeepSignupPendingWhenEmailDeliveryFails() {
        Long userId = 88L;
        String canonicalEmail = "passageiro@example.com";
        EmailVerificationToken previousToken = token(
                UserRole.PASSENGER.claimValue(),
                userId,
                canonicalEmail
        );
        stubLockedOwner(snapshot(
                userId,
                UserRole.PASSENGER,
                canonicalEmail,
                false,
                true
        ));
        when(emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.PASSENGER.claimValue(),
                userId
        )).thenReturn(List.of(previousToken));
        doThrow(new IllegalStateException("Falha para " + canonicalEmail))
                .when(emailDeliveryGateway)
                .sendEmail(anyString(), anyString(), anyString(), anyString());
        ListAppender<ILoggingEvent> logs = attachLogCapture();

        try {
            assertThatCode(() -> emailVerificationService.sendVerificationIfAllowed(
                    UserRole.PASSENGER,
                    userId,
                    canonicalEmail
            )).doesNotThrowAnyException();

            ArgumentCaptor<EmailVerificationToken> savedTokens =
                    ArgumentCaptor.forClass(EmailVerificationToken.class);
            verify(emailVerificationTokenRepository, times(2))
                    .saveAndFlush(savedTokens.capture());
            List<EmailVerificationToken> capturedTokens = savedTokens.getAllValues();
            assertThat(capturedTokens.get(capturedTokens.size() - 1).getConsumedAt())
                    .isNotNull();
            assertThat(previousToken.getConsumedAt()).isNull();
            verify(emailDeliveryGateway).sendEmail(anyString(), anyString(), anyString(), anyString());
            verify(loginUserDirectoryService, never()).recordVerificationEmailSent(any(), any(), any());
            assertThat(logs.list)
                    .extracting(ILoggingEvent::getFormattedMessage)
                    .noneMatch(message -> message.contains(canonicalEmail));
        } finally {
            detachLogCapture(logs);
        }
    }

    @Test
    void shouldRecordVerificationEmailSentAfterSuccessfulDelivery() {
        Long userId = 99L;
        EmailVerificationToken previousToken = token(
                UserRole.DRIVER.claimValue(),
                userId,
                "motorista@example.com"
        );
        stubLockedOwner(snapshot(
                userId,
                UserRole.DRIVER,
                "motorista@example.com",
                false,
                true
        ));
        when(emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.DRIVER.claimValue(),
                userId
        )).thenReturn(List.of(previousToken));

        emailVerificationService.sendVerificationIfAllowed(
                UserRole.DRIVER,
                userId,
                "motorista@example.com"
        );

        assertThat(previousToken.getConsumedAt()).isNotNull();
        org.mockito.InOrder lockOrder = inOrder(
                loginUserDirectoryService,
                loginIdentityService,
                emailVerificationTokenRepository
        );
        lockOrder.verify(loginUserDirectoryService)
                .lockAccountByRoleAndId(UserRole.DRIVER, userId);
        lockOrder.verify(loginIdentityService)
                .lockExactOwnerForEmailVerification(
                        "motorista@example.com",
                        UserRole.DRIVER,
                        userId
                );
        lockOrder.verify(emailVerificationTokenRepository)
                .findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                        UserRole.DRIVER.claimValue(),
                        userId
                );
        org.mockito.InOrder deliveryOrder = inOrder(
                emailVerificationTokenRepository,
                emailDeliveryGateway,
                loginUserDirectoryService
        );
        deliveryOrder.verify(emailVerificationTokenRepository).saveAndFlush(any());
        deliveryOrder.verify(emailDeliveryGateway)
                .sendEmail(anyString(), anyString(), anyString(), anyString());
        deliveryOrder.verify(emailVerificationTokenRepository).flush();
        deliveryOrder.verify(loginUserDirectoryService)
                .recordVerificationEmailSent(any(), any(), any());
    }

    @Test
    void shouldEscapeCanonicalEmailBeforeInterpolatingItIntoHtml() {
        Long userId = 100L;
        String canonicalEmail = "user<>&\"'@example.com";
        stubLockedOwner(snapshot(
                userId,
                UserRole.DRIVER,
                canonicalEmail,
                false,
                true
        ));
        when(emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.DRIVER.claimValue(),
                userId
        )).thenReturn(List.of());
        ArgumentCaptor<String> htmlBody = ArgumentCaptor.forClass(String.class);

        emailVerificationService.sendVerificationIfAllowed(
                UserRole.DRIVER,
                userId,
                canonicalEmail
        );

        verify(emailDeliveryGateway).sendEmail(
                org.mockito.ArgumentMatchers.eq(canonicalEmail),
                anyString(),
                htmlBody.capture(),
                anyString()
        );
        assertThat(htmlBody.getValue())
                .doesNotContain(canonicalEmail)
                .contains("user&lt;&gt;&amp;&quot;&#39;@example.com");
    }

    @Test
    void shouldNotCreateOrSendVerificationWhenEmailDoesNotBelongToCurrentOwner() {
        Long userId = 41L;
        when(loginUserDirectoryService.lockAccountByRoleAndId(UserRole.GUARDIAN, userId))
                .thenReturn(Optional.of(snapshot(
                        userId,
                        UserRole.GUARDIAN,
                        "current@example.com",
                        false,
                        true
                )));

        assertThatCode(() -> emailVerificationService.sendVerificationIfAllowed(
                UserRole.GUARDIAN,
                userId,
                "stale@example.com"
        )).doesNotThrowAnyException();

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailVerificationTokenRepository, never()).saveAndFlush(any());
        verify(emailDeliveryGateway, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
        verify(loginUserDirectoryService, never()).recordVerificationEmailSent(any(), any(), any());
    }

    @Test
    void shouldKeepResendOpaqueForInvalidOrUnknownEmail() {
        when(loginUserDirectoryService.findByEmail("invalid email")).thenReturn(null);

        assertThatCode(() -> emailVerificationService.resendForEmail("invalid email"))
                .doesNotThrowAnyException();

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailDeliveryGateway, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldKeepResendOpaqueWhenOwnerDisappearsBeforeSending() {
        Long userId = 65L;
        LoginUserDirectoryService.LoginUserSnapshot snapshot =
                snapshot(userId, UserRole.PASSENGER, "removed@example.com", false, true);
        when(loginUserDirectoryService.findByEmail("removed@example.com")).thenReturn(snapshot);
        when(loginUserDirectoryService.lockAccountByRoleAndId(UserRole.PASSENGER, userId))
                .thenReturn(Optional.empty());

        assertThatCode(() -> emailVerificationService.resendForEmail("removed@example.com"))
                .doesNotThrowAnyException();

        verify(emailVerificationTokenRepository, never()).save(any());
        verify(emailDeliveryGateway, never()).sendEmail(anyString(), anyString(), anyString(), anyString());
    }

    @Test
    void shouldRejectVerifiedAccessWhenAuthenticatedEmailNoLongerOwnsIdentity() {
        Long userId = 74L;
        AuthenticatedUser user = new AuthenticatedUser(
                userId,
                UserRole.COMPANY_ADMIN,
                "stale@example.com",
                "Admin",
                null,
                8L
        );
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.COMPANY_ADMIN, userId))
                .thenReturn(snapshot(userId, UserRole.COMPANY_ADMIN, "current@example.com", true, true));
        when(loginIdentityService.isOwnedBy("stale@example.com", UserRole.COMPANY_ADMIN, userId))
                .thenReturn(false);

        assertThatThrownBy(() -> emailVerificationService.assertVerifiedAccess(user))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Usuário autenticável não encontrado.");
    }

    @Test
    void shouldRevokeVerificationAndRefreshArtifactsForHardDelete() {
        Long userId = 85L;
        EmailVerificationToken token = token(UserRole.GUARDIAN.claimValue(), userId, "guardian@example.com");
        stubLockedOwner(snapshot(
                userId,
                UserRole.GUARDIAN,
                "guardian@example.com",
                false,
                true
        ));
        when(emailVerificationTokenRepository.findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                UserRole.GUARDIAN.claimValue(),
                userId
        )).thenReturn(List.of(token));

        emailVerificationService.revokeAccountArtifacts(UserRole.GUARDIAN, userId);

        assertThat(token.getConsumedAt()).isNotNull();
        verify(emailVerificationTokenRepository).flush();
        verify(refreshTokenService).revokeAllForUser(userId, UserRole.GUARDIAN.claimValue());
        verify(loginUserDirectoryService, never()).markPending(any(), any());

        org.mockito.InOrder artifactOrder = inOrder(
                loginUserDirectoryService,
                loginIdentityService,
                emailVerificationTokenRepository,
                refreshTokenService
        );
        artifactOrder.verify(loginUserDirectoryService)
                .lockAccountByRoleAndId(UserRole.GUARDIAN, userId);
        artifactOrder.verify(loginIdentityService)
                .lockExactOwnerForEmailVerification(
                        "guardian@example.com",
                        UserRole.GUARDIAN,
                        userId
                );
        artifactOrder.verify(emailVerificationTokenRepository)
                .findAllByUserRoleAndUserIdAndConsumedAtIsNull(
                        UserRole.GUARDIAN.claimValue(),
                        userId
                );
        artifactOrder.verify(emailVerificationTokenRepository).flush();
        artifactOrder.verify(refreshTokenService)
                .revokeAllForUser(userId, UserRole.GUARDIAN.claimValue());
    }

    private LoginUserDirectoryService.LoginUserSnapshot snapshot(
            Long userId,
            UserRole role,
            String email,
            boolean verified,
            boolean accessConfigured) {
        return new LoginUserDirectoryService.LoginUserSnapshot(
                userId, role, email, null, verified, accessConfigured, null);
    }

    private void stubLockedOwner(LoginUserDirectoryService.LoginUserSnapshot snapshot) {
        when(loginUserDirectoryService.lockAccountByRoleAndId(
                snapshot.role(),
                snapshot.userId()
        )).thenReturn(Optional.of(snapshot));
        when(loginIdentityService.lockExactOwnerForEmailVerification(
                snapshot.email(),
                snapshot.role(),
                snapshot.userId()
        )).thenReturn(Optional.of(snapshot.email()));
    }

    private EmailVerificationToken token(String role, Long userId, String email) {
        EmailVerificationToken token = new EmailVerificationToken();
        token.setUserRole(role);
        token.setUserId(userId);
        token.setEmailSnapshot(email);
        token.setExpiresAt(Instant.now().plusSeconds(3600));
        return token;
    }

    private ListAppender<ILoggingEvent> attachLogCapture() {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailVerificationService.class);
        ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        return appender;
    }

    private void detachLogCapture(ListAppender<ILoggingEvent> appender) {
        Logger logger = (Logger) LoggerFactory.getLogger(EmailVerificationService.class);
        logger.detachAppender(appender);
        appender.stop();
    }
}
