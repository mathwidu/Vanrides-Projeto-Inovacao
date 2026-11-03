package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.model.RefreshToken;
import com.vanrides.vanrides.exception.ApiStatusException;
import com.vanrides.vanrides.exception.LoginIdentityConflictException;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationLandingPageRenderer;
import com.vanrides.vanrides.service.EmailVerificationResendQueue;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.LoginUserDirectoryService;
import com.vanrides.vanrides.service.PendingEmailChangeService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.http.HttpStatus;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Instant;

import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(AuthController.class)
@Import(EmailVerificationLandingPageRenderer.class)
class AuthControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private RefreshTokenService refreshTokenService;

    @MockBean
    private JwtService jwtService;

    @MockBean
    private EmailVerificationService emailVerificationService;

    @MockBean
    private EmailVerificationResendQueue emailVerificationResendQueue;

    @MockBean
    private PendingEmailChangeService pendingEmailChangeService;

    @MockBean
    private AuthorizationService authorizationService;

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockBean
    private PassageiroRepository passageiroRepository;

    @MockBean
    private MotoristaRepository motoristaRepository;

    @Test
    void shouldReturnBadRequestWhenRefreshTokenIsMissing() throws Exception {
        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("refreshToken é obrigatório."));
    }

    @Test
    void shouldReturnUnauthorizedWhenRefreshTokenIsInvalid() throws Exception {
        when(refreshTokenService.validate("invalid-token")).thenReturn(null);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"invalid-token"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Refresh token inválido ou expirado."));
    }

    @Test
    void shouldRotateAndReturnNewTokensWhenRefreshTokenIsValid() throws Exception {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(7L);
        entity.setUserRole("MOTORISTA");
        entity.setUserEmail("driver@vanrides.com");
        entity.setCompanyId(55L);
        entity.setExpiresAt(Instant.now().plusSeconds(3600));
        RefreshTokenService.ValidatedRefreshToken validated = new RefreshTokenService.ValidatedRefreshToken(entity);

        when(refreshTokenService.validate("valid-token")).thenReturn(validated);
        when(jwtService.generateAccessToken(7L, "DRIVER", "driver@vanrides.com", 55L)).thenReturn("new-access");
        when(refreshTokenService.rotate(validated)).thenReturn("new-refresh");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"valid-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void shouldReturnForbiddenWhenRefreshTokenUserIsNotVerified() throws Exception {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(7L);
        entity.setUserRole(UserRole.DRIVER.claimValue());
        entity.setUserEmail("driver@vanrides.com");
        entity.setCompanyId(55L);
        entity.setExpiresAt(Instant.now().plusSeconds(3600));
        RefreshTokenService.ValidatedRefreshToken validated = new RefreshTokenService.ValidatedRefreshToken(entity);

        when(refreshTokenService.validate("valid-token")).thenReturn(validated);
        doThrow(new com.vanrides.vanrides.service.EmailVerificationException(
                "EMAIL_NOT_VERIFIED",
                "O e-mail informado ainda não foi verificado.",
                "driver@vanrides.com",
                403
        )).when(emailVerificationService).assertVerifiedAccess(org.mockito.ArgumentMatchers.any(AuthenticatedUser.class));

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"valid-token"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value("driver@vanrides.com"));
    }

    @Test
    void shouldRotateAndReturnNewTokensWhenRefreshTokenUserHasNoCompanyYet() throws Exception {
        RefreshToken entity = new RefreshToken();
        entity.setUserId(12L);
        entity.setUserRole(UserRole.PASSENGER.claimValue());
        entity.setUserEmail("sem.empresa@vanrides.com");
        entity.setCompanyId(null);
        entity.setExpiresAt(Instant.now().plusSeconds(3600));
        RefreshTokenService.ValidatedRefreshToken validated = new RefreshTokenService.ValidatedRefreshToken(entity);

        when(refreshTokenService.validate("valid-token")).thenReturn(validated);
        when(jwtService.generateAccessToken(12L, "PASSENGER", "sem.empresa@vanrides.com", null))
                .thenReturn("new-access-no-company");
        when(refreshTokenService.rotate(validated)).thenReturn("new-refresh-no-company");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        mockMvc.perform(post("/auth/refresh")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"refreshToken":"valid-token"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.accessToken").value("new-access-no-company"))
                .andExpect(jsonPath("$.refreshToken").value("new-refresh-no-company"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));
    }

    @Test
    void shouldConfirmEmailVerificationToken() throws Exception {
        when(emailVerificationService.confirm("token-ok")).thenReturn(
                new LoginUserDirectoryService.LoginUserSnapshot(
                        11L,
                        UserRole.PASSENGER,
                        "user@vanrides.com",
                        7L,
                        true,
                        true,
                        null
                )
        );

        mockMvc.perform(post("/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-ok"}
                                """))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.message").value("E-mail verificado com sucesso."))
                .andExpect(jsonPath("$.email").value("user@vanrides.com"));
    }

    @Test
    void shouldReturnGenericInvalidConfirmationContract() throws Exception {
        when(emailVerificationService.confirm("invalid-token")).thenThrow(
                new EmailVerificationException(
                        "INVALID_EMAIL_VERIFICATION_TOKEN",
                        "Token de verificação inválido, expirado ou já utilizado.",
                        null,
                        400
                ));

        mockMvc.perform(post("/auth/email-verifications/confirm")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"invalid-token"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL_VERIFICATION_TOKEN"))
                .andExpect(jsonPath("$.message")
                        .value("Token de verificação inválido, expirado ou já utilizado."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }

    @Test
    void shouldReturnAcceptedWhenResendingVerification() throws Exception {
        mockMvc.perform(post("/auth/email-verifications/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@vanrides.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("user@vanrides.com"));

        verify(emailVerificationResendQueue).enqueue("user@vanrides.com");
    }

    @Test
    void shouldReturnTheSameAcceptedJsonForEmptyResend() throws Exception {
        mockMvc.perform(post("/auth/email-verifications/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message")
                        .value("Se existir uma conta pendente para este e-mail, uma nova confirmação será enviada."))
                .andExpect(jsonPath("$.email").doesNotExist());

        verify(emailVerificationResendQueue).enqueue(null);
    }

    @Test
    void shouldReturnTheSameAcceptedJsonForInvalidResend() throws Exception {
        mockMvc.perform(post("/auth/email-verifications/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"invalido"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("invalido"));

        verify(emailVerificationResendQueue).enqueue("invalido");
    }

    @Test
    void shouldReturnTheSameAcceptedJsonForUnknownResend() throws Exception {
        mockMvc.perform(post("/auth/email-verifications/resend")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"unknown@vanrides.com"}
                                """))
                .andExpect(status().isAccepted())
                .andExpect(jsonPath("$.message").exists())
                .andExpect(jsonPath("$.email").value("unknown@vanrides.com"));

        verify(emailVerificationResendQueue).enqueue("unknown@vanrides.com");
    }

    @Test
    void shouldChangePendingEmail() throws Exception {
        when(pendingEmailChangeService.changePendingEmail("admin@vanrides.com", "Secret123", "novo@vanrides.com"))
                .thenReturn("novo@vanrides.com");

        mockMvc.perform(post("/auth/email-verifications/change-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@vanrides.com",
                                  "senha":"Secret123",
                                  "newEmail":"novo@vanrides.com"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.email").value("novo@vanrides.com"))
                .andExpect(jsonPath("$.message").exists());
    }

    @Test
    void shouldReturnInvalidEmailContractWhenChangingPendingEmail() throws Exception {
        when(pendingEmailChangeService.changePendingEmail(
                "admin@vanrides.com",
                "Secret123",
                "invalido"
        )).thenThrow(new ApiStatusException(
                HttpStatus.BAD_REQUEST,
                "INVALID_EMAIL",
                "Informe um e-mail válido."
        ));

        mockMvc.perform(post("/auth/email-verifications/change-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@vanrides.com",
                                  "senha":"Secret123",
                                  "newEmail":"invalido"
                                }
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value("INVALID_EMAIL"))
                .andExpect(jsonPath("$.message").value("Informe um e-mail válido."));
    }

    @Test
    void shouldReturnGenericConflictWhenChangingPendingEmail() throws Exception {
        when(pendingEmailChangeService.changePendingEmail(
                "admin@vanrides.com",
                "Secret123",
                "occupied@vanrides.com"
        )).thenThrow(new LoginIdentityConflictException());

        mockMvc.perform(post("/auth/email-verifications/change-email")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "email":"admin@vanrides.com",
                                  "senha":"Secret123",
                                  "newEmail":"occupied@vanrides.com"
                                }
                                """))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value("EMAIL_ALREADY_IN_USE"))
                .andExpect(jsonPath("$.message").value("O e-mail informado já está em uso."))
                .andExpect(jsonPath("$.email").doesNotExist());
    }
}
