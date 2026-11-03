package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthorizationServiceTest {

    @Mock
    private AuthService authService;

    @Mock
    private JwtService jwtService;

    @Mock
    private EmailVerificationService emailVerificationService;

    @Mock
    private LoginUserDirectoryService loginUserDirectoryService;

    @Mock
    private Claims claims;

    private AuthorizationService authorizationService;

    @BeforeEach
    void setUp() {
        authorizationService = new AuthorizationService();
        ReflectionTestUtils.setField(authorizationService, "authService", authService);
        ReflectionTestUtils.setField(authorizationService, "jwtService", jwtService);
        ReflectionTestUtils.setField(authorizationService, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(authorizationService, "loginUserDirectoryService", loginUserDirectoryService);
    }

    @Test
    void shouldRejectBasicWhenBasicAuthIsDisabled() {
        ReflectionTestUtils.setField(authorizationService, "basicAuthEnabled", false);

        assertThatThrownBy(() -> authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Driver!123")))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Basic Auth desabilitado neste ambiente");

        verifyNoInteractions(authService);
    }

    @Test
    void shouldAllowBearerWhenBasicAuthIsDisabled() {
        ReflectionTestUtils.setField(authorizationService, "basicAuthEnabled", false);
        when(jwtService.isTokenValid("valid-token")).thenReturn(true);
        when(jwtService.parseAndValidate("valid-token")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("DRIVER");
        when(claims.getSubject()).thenReturn("21");
        when(claims.get("email", String.class)).thenReturn("driver@vanrides.dev");
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 21L))
                .thenReturn(snapshot(21L, UserRole.DRIVER, "driver@vanrides.dev", 77L));

        AuthenticatedUser user = authorizationService.authenticate("Bearer valid-token");

        assertThat(user.getRoleEnum()).isEqualTo(UserRole.DRIVER);
        assertThat(user.getUserId()).isEqualTo(21L);
        assertThat(user.getEmail()).isEqualTo("driver@vanrides.dev");
        assertThat(user.getCompanyId()).isEqualTo(77L);
        verifyNoInteractions(authService);
    }

    @Test
    void shouldAllowBasicWhenBasicAuthIsEnabled() {
        ReflectionTestUtils.setField(authorizationService, "basicAuthEnabled", true);
        AuthenticatedUser authenticated = new AuthenticatedUser(
                44L,
                UserRole.DRIVER,
                "driver@vanrides.dev",
                "Motorista",
                null,
                88L
        );
        when(authService.authenticate("driver@vanrides.dev", "Driver!123")).thenReturn(authenticated);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 44L))
                .thenReturn(snapshot(44L, UserRole.DRIVER, "driver@vanrides.dev", 88L));

        AuthenticatedUser user = authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Driver!123"));

        assertThat(user.getRoleEnum()).isEqualTo(UserRole.DRIVER);
        assertThat(user.getUserId()).isEqualTo(44L);
        assertThat(user.getEmail()).isEqualTo("driver@vanrides.dev");
        assertThat(user.getCompanyId()).isEqualTo(88L);
    }

    @Test
    void shouldRejectStaleBearerEmailEvenWhenUnverifiedAccessIsAllowed() {
        when(jwtService.isTokenValid("stale-token")).thenReturn(true);
        when(jwtService.parseAndValidate("stale-token")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("DRIVER");
        when(claims.getSubject()).thenReturn("21");
        when(claims.get("email", String.class)).thenReturn("old.driver@vanrides.dev");
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 21L))
                .thenReturn(snapshot(21L, UserRole.DRIVER, "new.driver@vanrides.dev", 77L));

        assertThatThrownBy(() -> authorizationService.authenticate("Bearer stale-token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token inválido ou expirado")
                .hasMessageNotContaining("old.driver@vanrides.dev")
                .hasMessageNotContaining("new.driver@vanrides.dev");

        verify(loginUserDirectoryService).requireByRoleAndId(UserRole.DRIVER, 21L);
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldHideMissingBearerOwnerBehindTheSameInvalidTokenMessage() {
        when(jwtService.isTokenValid("removed-owner-token")).thenReturn(true);
        when(jwtService.parseAndValidate("removed-owner-token")).thenReturn(claims);
        when(claims.get("role", String.class)).thenReturn("DRIVER");
        when(claims.getSubject()).thenReturn("21");
        when(claims.get("email", String.class)).thenReturn("driver@vanrides.dev");
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 21L))
                .thenThrow(new IllegalArgumentException("Usuário autenticável não encontrado."));

        assertThatThrownBy(() -> authorizationService.authenticate("Bearer removed-owner-token", true))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Token inválido ou expirado")
                .hasMessageNotContaining("Usuário")
                .hasMessageNotContaining("driver@vanrides.dev");

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRejectBasicAuthenticationIfIdentityChangesAfterPasswordCheck() {
        AuthenticatedUser authenticated = new AuthenticatedUser(
                44L,
                UserRole.DRIVER,
                "old.driver@vanrides.dev",
                "Motorista",
                null,
                88L
        );
        when(authService.authenticate("old.driver@vanrides.dev", "Driver!123"))
                .thenReturn(authenticated);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 44L))
                .thenReturn(snapshot(44L, UserRole.DRIVER, "new.driver@vanrides.dev", 88L));

        assertThatThrownBy(() -> authorizationService.authenticate(
                basicHeader("old.driver@vanrides.dev", "Driver!123"),
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciais inválidas")
                .hasMessageNotContaining("old.driver@vanrides.dev")
                .hasMessageNotContaining("new.driver@vanrides.dev");

        verify(loginUserDirectoryService).requireByRoleAndId(UserRole.DRIVER, 44L);
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldHideMissingBasicOwnerBehindTheSameInvalidCredentialsMessage() {
        AuthenticatedUser authenticated = new AuthenticatedUser(
                44L,
                UserRole.DRIVER,
                "driver@vanrides.dev",
                "Motorista",
                null,
                88L
        );
        when(authService.authenticate("driver@vanrides.dev", "Driver!123"))
                .thenReturn(authenticated);
        when(loginUserDirectoryService.requireByRoleAndId(UserRole.DRIVER, 44L))
                .thenThrow(new IllegalArgumentException("Usuário autenticável não encontrado."));

        assertThatThrownBy(() -> authorizationService.authenticate(
                basicHeader("driver@vanrides.dev", "Driver!123"),
                true
        ))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("Credenciais inválidas")
                .hasMessageNotContaining("Usuário")
                .hasMessageNotContaining("driver@vanrides.dev");

        verifyNoInteractions(emailVerificationService);
    }

    private static LoginUserDirectoryService.LoginUserSnapshot snapshot(
            Long userId,
            UserRole role,
            String email,
            Long companyId
    ) {
        return new LoginUserDirectoryService.LoginUserSnapshot(
                userId,
                role,
                email,
                companyId,
                true,
                true,
                OffsetDateTime.parse("2026-01-01T10:00:00Z")
        );
    }

    private static String basicHeader(String email, String password) {
        String raw = email + ":" + password;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
