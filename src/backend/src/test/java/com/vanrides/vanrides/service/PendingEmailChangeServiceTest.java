package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.service.LoginUserDirectoryService.LoginUserSnapshot;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PendingEmailChangeServiceTest {

    @Mock private AuthService authService;
    @Mock private CompanyAdminService companyAdminService;
    @Mock private PassageiroService passageiroService;
    @Mock private MotoristaService motoristaService;
    @Mock private CompanyAdminGuardianService companyAdminGuardianService;
    @Mock private EmailVerificationService emailVerificationService;
    @Mock private LoginUserDirectoryService loginUserDirectoryService;
    @Mock private LoginIdentityService loginIdentityService;

    private PendingEmailChangeService service;

    @BeforeEach
    void setUp() {
        service = new PendingEmailChangeService();
        ReflectionTestUtils.setField(service, "authService", authService);
        ReflectionTestUtils.setField(service, "companyAdminService", companyAdminService);
        ReflectionTestUtils.setField(service, "passageiroService", passageiroService);
        ReflectionTestUtils.setField(service, "motoristaService", motoristaService);
        ReflectionTestUtils.setField(service, "companyAdminGuardianService", companyAdminGuardianService);
        ReflectionTestUtils.setField(service, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(service, "loginUserDirectoryService", loginUserDirectoryService);
        ReflectionTestUtils.setField(service, "loginIdentityService", loginIdentityService);
    }

    @ParameterizedTest
    @EnumSource(UserRole.class)
    void shouldDispatchAllRolesAndCreateVerificationOnlyAfterWriter(UserRole role) {
        AuthenticatedUser user = authenticated(role, 9L);
        when(authService.authenticate(" Current@Example.com ", "Secret123")).thenReturn(user);
        when(loginUserDirectoryService.requireByRoleAndId(role, 9L)).thenReturn(snapshot(role, 9L, false));
        when(loginIdentityService.requireAvailableFor(" NEW@Example.com ", role, 9L))
                .thenReturn("new@example.com");

        assertThat(service.changePendingEmail(
                " Current@Example.com ",
                "Secret123",
                " NEW@Example.com "))
                .isEqualTo("new@example.com");

        verifyWriter(role, 9L, "new@example.com");
        verify(emailVerificationService).markPendingAndRevokeSessions(role, 9L);
        verify(emailVerificationService).sendVerificationIfAllowed(role, 9L, "new@example.com");
    }

    @Test
    void shouldRejectAReservedDestinationBeforeWriterOrRevocation() {
        AuthenticatedUser user = authenticated(UserRole.PASSENGER, 7L);
        when(authService.authenticate("current@example.com", "Secret123")).thenReturn(user);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.PASSENGER, 7L))
                .thenReturn(snapshot(UserRole.PASSENGER, 7L, false));
        when(loginIdentityService.requireAvailableFor(
                "taken@example.com",
                UserRole.PASSENGER,
                7L)).thenThrow(new LoginIdentityConflictException());

        assertThatThrownBy(() -> service.changePendingEmail(
                "current@example.com",
                "Secret123",
                "taken@example.com"))
                .isInstanceOf(LoginIdentityConflictException.class);

        verifyNoWriterInteractions();
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldNotRevokeOrSendWhenTheWriterLosesTheRace() {
        AuthenticatedUser user = authenticated(UserRole.DRIVER, 8L);
        when(authService.authenticate("current@example.com", "Secret123")).thenReturn(user);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 8L))
                .thenReturn(snapshot(UserRole.DRIVER, 8L, false));
        when(loginIdentityService.requireAvailableFor(
                "race@example.com",
                UserRole.DRIVER,
                8L)).thenReturn("race@example.com");
        doThrow(new LoginIdentityConflictException())
                .when(motoristaService).updatePendingDriverEmail(8L, "race@example.com");

        assertThatThrownBy(() -> service.changePendingEmail(
                "current@example.com",
                "Secret123",
                "race@example.com"))
                .isInstanceOf(LoginIdentityConflictException.class);

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectVerifiedAccountsBeforeDestinationLookup() {
        AuthenticatedUser user = authenticated(UserRole.GUARDIAN, 10L);
        when(authService.authenticate("verified@example.com", "Secret123")).thenReturn(user);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.GUARDIAN, 10L))
                .thenReturn(snapshot(UserRole.GUARDIAN, 10L, true));

        assertThatThrownBy(() -> service.changePendingEmail(
                "verified@example.com",
                "Secret123",
                "new@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception ->
                        assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));

        verifyNoInteractions(loginIdentityService, emailVerificationService);
        verifyNoWriterInteractions();
    }

    @Test
    void shouldKeepInvalidCredentialsIndistinguishable() {
        when(authService.authenticate("missing@example.com", "Wrong123")).thenReturn(null);

        assertThatThrownBy(() -> service.changePendingEmail(
                "missing@example.com",
                "Wrong123",
                "new@example.com"))
                .isInstanceOfSatisfying(ResponseStatusException.class, exception -> {
                    assertThat(exception.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
                    assertThat(exception.getReason()).isEqualTo("Credenciais inválidas.");
                });

        verifyNoInteractions(loginIdentityService, emailVerificationService);
    }

    private AuthenticatedUser authenticated(UserRole role, Long id) {
        return new AuthenticatedUser(id, role, "current@example.com", "User", null, 4L);
    }

    private LoginUserSnapshot snapshot(UserRole role, Long id, boolean verified) {
        return new LoginUserSnapshot(
                id,
                role,
                "current@example.com",
                4L,
                verified,
                true,
                OffsetDateTime.parse("2026-07-17T10:00:00-03:00")
        );
    }

    private void verifyWriter(UserRole role, Long id, String email) {
        switch (role) {
            case COMPANY_ADMIN -> verify(companyAdminService).updatePendingAdminEmail(id, email);
            case PASSENGER -> verify(passageiroService).updatePendingPassengerEmail(id, email);
            case DRIVER -> verify(motoristaService).updatePendingDriverEmail(id, email);
            case GUARDIAN -> verify(companyAdminGuardianService).updatePendingGuardianEmail(id, email);
        }
    }

    private void verifyNoWriterInteractions() {
        verifyNoInteractions(
                companyAdminService,
                passageiroService,
                motoristaService,
                companyAdminGuardianService
        );
    }
}
