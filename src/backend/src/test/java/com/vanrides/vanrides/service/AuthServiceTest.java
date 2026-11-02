package com.vanrides.vanrides.service;

import at.favre.lib.crypto.bcrypt.BCrypt;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    private static final String CORRECT_PASSWORD_HASH = BCrypt.withDefaults()
            .hashToString(12, "Correct!123".toCharArray());

    @Mock
    private LoginUserDirectoryService loginUserDirectoryService;

    private AuthService authService;

    @BeforeEach
    void setUp() {
        authService = new AuthService(loginUserDirectoryService);
    }

    @Test
    void shouldAuthenticateOnlyTheCompanyAdminSelectedByRegistry() {
        when(loginUserDirectoryService.findCredentialsByEmail("admin@vanrides.dev"))
                .thenReturn(credentials("admin@vanrides.dev", UserRole.COMPANY_ADMIN, 1L,
                        "Admin Portal", null, 11L, true));

        AuthenticatedUser user = authService.authenticate("admin@vanrides.dev", "Correct!123");

        assertThat(user).isNotNull();
        assertThat(user.getRoleEnum()).isEqualTo(UserRole.COMPANY_ADMIN);
        assertThat(user.getEmail()).isEqualTo("admin@vanrides.dev");
        assertThat(user.getCompanyId()).isEqualTo(11L);
        verify(loginUserDirectoryService).findCredentialsByEmail("admin@vanrides.dev");
    }

    @Test
    void shouldAuthenticateGuardianWithConfiguredAccess() {
        when(loginUserDirectoryService.findCredentialsByEmail("guardian@vanrides.dev"))
                .thenReturn(credentials("guardian@vanrides.dev", UserRole.GUARDIAN, 2L,
                        "Guardian", null, 21L, true));

        AuthenticatedUser user = authService.authenticate("guardian@vanrides.dev", "Correct!123");

        assertThat(user).isNotNull();
        assertThat(user.getRoleEnum()).isEqualTo(UserRole.GUARDIAN);
        assertThat(user.getCompanyId()).isEqualTo(21L);
    }

    @Test
    void shouldPreservePassengerDriverRelationship() {
        when(loginUserDirectoryService.findCredentialsByEmail("passenger@vanrides.dev"))
                .thenReturn(credentials("passenger@vanrides.dev", UserRole.PASSENGER, 3L,
                        "Passenger", 99L, 31L, true));

        AuthenticatedUser user = authService.authenticate("passenger@vanrides.dev", "Correct!123");

        assertThat(user).isNotNull();
        assertThat(user.getMotoristaId()).isEqualTo(99L);
    }

    @ParameterizedTest
    @ValueSource(strings = {"$2a$", "$2b$", "$2y$"})
    void shouldAuthenticateAllSupportedCost12BcryptVersions(String version) {
        String passwordHash = CORRECT_PASSWORD_HASH.replace("$2a$", version);
        when(loginUserDirectoryService.findCredentialsByEmail("variant@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        10L,
                        UserRole.PASSENGER,
                        "variant@vanrides.dev",
                        passwordHash,
                        "Variant",
                        null,
                        61L,
                        true
                ));

        AuthenticatedUser user = authService.authenticate(
                "variant@vanrides.dev",
                "Correct!123"
        );

        assertThat(user).isNotNull();
        assertThat(user.getUserId()).isEqualTo(10L);
    }

    @Test
    void shouldRejectWrongPasswordWithoutTryingAnotherRole() {
        when(loginUserDirectoryService.findCredentialsByEmail("owner@vanrides.dev"))
                .thenReturn(credentials("owner@vanrides.dev", UserRole.DRIVER, 4L,
                        "Driver", null, 41L, true));

        assertThat(authService.authenticate("owner@vanrides.dev", "Wrong!123")).isNull();

        verify(loginUserDirectoryService).findCredentialsByEmail("owner@vanrides.dev");
    }

    @Test
    void shouldRejectGuardianWithoutConfiguredAccess() {
        when(loginUserDirectoryService.findCredentialsByEmail("pending@vanrides.dev"))
                .thenReturn(credentials("pending@vanrides.dev", UserRole.GUARDIAN, 5L,
                        "Pending", null, 51L, false));

        assertThat(authService.authenticate("pending@vanrides.dev", "Correct!123")).isNull();
    }

    @Test
    void shouldRejectMissingOrIncompleteCredentials() {
        when(loginUserDirectoryService.findCredentialsByEmail("missing@vanrides.dev")).thenReturn(null);
        when(loginUserDirectoryService.findCredentialsByEmail("nohash@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        6L,
                        UserRole.DRIVER,
                        "nohash@vanrides.dev",
                        null,
                        "No Hash",
                        null,
                        null,
                        true
                ));

        assertThat(authService.authenticate("missing@vanrides.dev", "Correct!123")).isNull();
        assertThat(authService.authenticate("nohash@vanrides.dev", "Correct!123")).isNull();
        assertThat(authService.authenticate("nohash@vanrides.dev", null)).isNull();
    }

    @Test
    void shouldPerformExactlyOneDummyVerificationForUnknownAccount() {
        RecordingPasswordVerifier verifier = new RecordingPasswordVerifier(false);
        AuthService opaqueAuthService = new AuthService(loginUserDirectoryService, verifier);
        when(loginUserDirectoryService.findCredentialsByEmail("unknown@vanrides.dev")).thenReturn(null);

        assertThat(opaqueAuthService.authenticate("unknown@vanrides.dev", "Wrong!123")).isNull();

        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash).startsWith("$2a$12$").hasSize(60);
    }

    @Test
    void shouldPerformExactlyOneDummyVerificationForUnavailableOrMalformedCredentials() {
        RecordingPasswordVerifier verifier = new RecordingPasswordVerifier(true);
        AuthService opaqueAuthService = new AuthService(loginUserDirectoryService, verifier);
        when(loginUserDirectoryService.findCredentialsByEmail("pending@vanrides.dev"))
                .thenReturn(credentials("pending@vanrides.dev", UserRole.GUARDIAN, 5L,
                        "Pending", null, 51L, false));
        when(loginUserDirectoryService.findCredentialsByEmail("malformed@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        6L,
                        UserRole.DRIVER,
                        "malformed@vanrides.dev",
                        "not-a-bcrypt-hash",
                        "Malformed",
                        null,
                        52L,
                        true
                ));
        when(loginUserDirectoryService.findCredentialsByEmail("nohash@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        8L,
                        UserRole.DRIVER,
                        "nohash@vanrides.dev",
                        null,
                        "No Hash",
                        null,
                        54L,
                        true
                ));

        assertThat(opaqueAuthService.authenticate("pending@vanrides.dev", "Wrong!123")).isNull();
        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash).startsWith("$2a$12$");

        verifier.reset();

        assertThat(opaqueAuthService.authenticate("nohash@vanrides.dev", "Wrong!123")).isNull();
        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash).startsWith("$2a$12$");

        verifier.reset();

        assertThat(opaqueAuthService.authenticate("malformed@vanrides.dev", "Wrong!123")).isNull();
        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash).startsWith("$2a$12$");
    }

    @Test
    void shouldUseBoundedDummyCostForAnUnexpectedStoredBcryptCost() {
        RecordingPasswordVerifier verifier = new RecordingPasswordVerifier(true);
        AuthService opaqueAuthService = new AuthService(loginUserDirectoryService, verifier);
        String unboundedHash = "$2a$31$" + "A".repeat(53);
        when(loginUserDirectoryService.findCredentialsByEmail("cost31@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        9L,
                        UserRole.DRIVER,
                        "cost31@vanrides.dev",
                        unboundedHash,
                        "Unsafe Cost",
                        null,
                        55L,
                        true
                ));

        assertThat(opaqueAuthService.authenticate("cost31@vanrides.dev", "Wrong!123"))
                .isNull();

        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash)
                .startsWith("$2a$12$")
                .isNotEqualTo(unboundedHash);
    }

    @Test
    void shouldPerformExactlyOneRealVerificationForUsableCredentials() {
        RecordingPasswordVerifier verifier = new RecordingPasswordVerifier(true);
        AuthService opaqueAuthService = new AuthService(loginUserDirectoryService, verifier);
        when(loginUserDirectoryService.findCredentialsByEmail("owner@vanrides.dev"))
                .thenReturn(new LoginUserDirectoryService.LoginCredentialSnapshot(
                        7L,
                        UserRole.DRIVER,
                        "owner@vanrides.dev",
                        CORRECT_PASSWORD_HASH,
                        "Owner",
                        null,
                        53L,
                        true
                ));

        assertThat(opaqueAuthService.authenticate("owner@vanrides.dev", "Correct!123")).isNotNull();

        assertThat(verifier.invocationCount).isEqualTo(1);
        assertThat(verifier.lastPasswordHash).isEqualTo(CORRECT_PASSWORD_HASH);
    }

    @Test
    void shouldSkipLookupAndVerificationWhenAttemptIsBlank() {
        RecordingPasswordVerifier verifier = new RecordingPasswordVerifier(false);
        AuthService opaqueAuthService = new AuthService(loginUserDirectoryService, verifier);

        assertThat(opaqueAuthService.authenticate(" ", "Correct!123")).isNull();
        assertThat(opaqueAuthService.authenticate("owner@vanrides.dev", " ")).isNull();

        assertThat(verifier.invocationCount).isZero();
        verifyNoInteractions(loginUserDirectoryService);
    }

    private LoginUserDirectoryService.LoginCredentialSnapshot credentials(
            String email,
            UserRole role,
            Long userId,
            String name,
            Long motoristaId,
            Long companyId,
            boolean accessConfigured) {
        return new LoginUserDirectoryService.LoginCredentialSnapshot(
                userId,
                role,
                email,
                CORRECT_PASSWORD_HASH,
                name,
                motoristaId,
                companyId,
                accessConfigured
        );
    }

    private static class RecordingPasswordVerifier implements AuthService.PasswordVerifier {
        private final boolean result;
        private int invocationCount;
        private String lastPasswordHash;

        private RecordingPasswordVerifier(boolean result) {
            this.result = result;
        }

        @Override
        public boolean verify(char[] password, char[] passwordHash) {
            invocationCount++;
            lastPasswordHash = new String(passwordHash);
            return result;
        }

        private void reset() {
            invocationCount = 0;
            lastPasswordHash = null;
        }
    }
}
