package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(LoginController.class)
class LoginControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private AuthService authService;

    @MockitoBean
    private JwtService jwtService;

    @MockitoBean
    private RefreshTokenService refreshTokenService;

    @MockitoBean
    private EmailVerificationService emailVerificationService;

    @MockitoBean
    private PassengerOnboardingService passengerOnboardingService;

    @MockitoBean
    private DriverOnboardingService driverOnboardingService;

    @MockitoBean
    private AuthorizationService authorizationService;

    // Necessário porque o FilterAuthBasic é carregado no contexto WebMvc.
    @MockitoBean
    private PassageiroRepository passageiroRepository;

    @MockitoBean
    private MotoristaRepository motoristaRepository;

    @Test
    void shouldReturnTokensWhenCredentialsAreValid() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                10L,
                UserRole.PASSENGER,
                "user@vanrides.com",
                "Ana",
                99L,
                77L
        );
        when(authService.authenticate(" User@VanRides.com ", "secret123")).thenReturn(user);
        when(jwtService.generateAccessToken(10L, "PASSENGER", "user@vanrides.com", 77L)).thenReturn("access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(passengerOnboardingService.getStatus(10L)).thenReturn(
                new com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse("COMPANY_LINKED", 77L, "Empresa Teste", 99L, null)
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" User@VanRides.com ","senha":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Passageiro"))
                .andExpect(jsonPath("$.id").value(10))
                .andExpect(jsonPath("$.motoristaId").value(99))
                .andExpect(jsonPath("$.companyLinkStatus").value("COMPANY_LINKED"))
                .andExpect(jsonPath("$.accessToken").value("access-token"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token"))
                .andExpect(jsonPath("$.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.expiresInSeconds").value(900));

        verify(authService).authenticate(" User@VanRides.com ", "secret123");
        verify(jwtService).generateAccessToken(10L, "PASSENGER", "user@vanrides.com", 77L);
    }

    @Test
    void shouldReturnAdminTokensWhenAdminCredentialsAreValid() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                77L,
                UserRole.COMPANY_ADMIN,
                "admin@vanrides.com",
                "Admin Portal",
                null,
                7L
        );
        when(authService.authenticate("admin@vanrides.com", "Admin!123")).thenReturn(user);
        when(jwtService.generateAccessToken(77L, "COMPANY_ADMIN", "admin@vanrides.com", 7L)).thenReturn("admin-access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("admin-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"admin@vanrides.com","senha":"Admin!123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Admin"))
                .andExpect(jsonPath("$.id").value(77))
                .andExpect(jsonPath("$.accessToken").value("admin-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("admin-refresh-token"));
    }

    @Test
    void shouldReturnPassengerWithoutCompanyOnboardingStateWhenCredentialsAreValid() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                12L,
                UserRole.PASSENGER,
                "sem.empresa@vanrides.com",
                "Bruna",
                null,
                null
        );
        when(authService.authenticate("sem.empresa@vanrides.com", "secret123")).thenReturn(user);
        when(jwtService.generateAccessToken(12L, "PASSENGER", "sem.empresa@vanrides.com", null)).thenReturn("access-token-no-company");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("refresh-token-no-company");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(passengerOnboardingService.getStatus(12L)).thenReturn(
                new com.vanrides.vanrides.dto.PassengerOnboardingStatusResponse("NO_COMPANY", null, null, null, null)
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"sem.empresa@vanrides.com","senha":"secret123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Passageiro"))
                .andExpect(jsonPath("$.id").value(12))
                .andExpect(jsonPath("$.motoristaId").doesNotExist())
                .andExpect(jsonPath("$.companyLinkStatus").value("NO_COMPANY"))
                .andExpect(jsonPath("$.accessToken").value("access-token-no-company"))
                .andExpect(jsonPath("$.refreshToken").value("refresh-token-no-company"));
    }

    @Test
    void shouldReturnGuardianTokensWhenGuardianCredentialsAreValid() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                88L,
                UserRole.GUARDIAN,
                "responsavel@vanrides.com",
                "Responsavel Mobile",
                null,
                7L
        );
        when(authService.authenticate("responsavel@vanrides.com", "Resp!123")).thenReturn(user);
        when(jwtService.generateAccessToken(88L, "GUARDIAN", "responsavel@vanrides.com", 7L)).thenReturn("guardian-access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("guardian-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"responsavel@vanrides.com","senha":"Resp!123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Responsavel"))
                .andExpect(jsonPath("$.id").value(88))
                .andExpect(jsonPath("$.accessToken").value("guardian-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("guardian-refresh-token"));
    }

    @Test
    void shouldReturnDriverWithoutCompanyStateWhenDriverHasNoCompanyYet() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                31L,
                UserRole.DRIVER,
                "motorista.solto@vanrides.com",
                "Motorista Solto",
                null,
                null
        );
        when(authService.authenticate("motorista.solto@vanrides.com", "Driver!123")).thenReturn(user);
        when(jwtService.generateAccessToken(31L, "DRIVER", "motorista.solto@vanrides.com", null)).thenReturn("driver-access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("driver-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(driverOnboardingService.getStatus(31L)).thenReturn(
                new com.vanrides.vanrides.dto.DriverOnboardingStatusResponse("NO_COMPANY", null, null, null, null)
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"motorista.solto@vanrides.com","senha":"Driver!123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Motorista"))
                .andExpect(jsonPath("$.id").value(31))
                .andExpect(jsonPath("$.companyLinkStatus").value("NO_COMPANY"))
                .andExpect(jsonPath("$.accessToken").value("driver-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("driver-refresh-token"));
    }

    @Test
    void shouldReturnDriverPendingApprovalStateWhenDriverHasOpenRequest() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                32L,
                UserRole.DRIVER,
                "motorista.pendente@vanrides.com",
                "Motorista Pendente",
                null,
                null
        );
        when(authService.authenticate("motorista.pendente@vanrides.com", "Driver!123")).thenReturn(user);
        when(jwtService.generateAccessToken(32L, "DRIVER", "motorista.pendente@vanrides.com", null)).thenReturn("driver-pending-access-token");
        when(refreshTokenService.issueRefreshToken(user)).thenReturn("driver-pending-refresh-token");
        when(jwtService.getAccessTokenTtlSeconds()).thenReturn(900L);
        when(driverOnboardingService.getStatus(32L)).thenReturn(
                new com.vanrides.vanrides.dto.DriverOnboardingStatusResponse("PENDING_APPROVAL", null, null, null, null)
        );

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"motorista.pendente@vanrides.com","senha":"Driver!123"}
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.tipoUsuario").value("Motorista"))
                .andExpect(jsonPath("$.id").value(32))
                .andExpect(jsonPath("$.companyLinkStatus").value("PENDING_APPROVAL"))
                .andExpect(jsonPath("$.accessToken").value("driver-pending-access-token"))
                .andExpect(jsonPath("$.refreshToken").value("driver-pending-refresh-token"));
    }

    @Test
    void shouldReturnUnauthorizedWhenCredentialsAreInvalid() throws Exception {
        when(authService.authenticate(anyString(), anyString())).thenReturn(null);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@vanrides.com","senha":"wrong"}
                                """))
                .andExpect(status().isUnauthorized())
                .andExpect(content().string("Credenciais inválidas."));
    }

    @Test
    void shouldReturnForbiddenWhenEmailIsNotVerified() throws Exception {
        AuthenticatedUser user = new AuthenticatedUser(
                10L,
                UserRole.PASSENGER,
                "user@vanrides.com",
                "Ana",
                99L,
                77L
        );
        when(authService.authenticate("user@vanrides.com", "secret123")).thenReturn(user);
        doThrow(new com.vanrides.vanrides.service.EmailVerificationException(
                "EMAIL_NOT_VERIFIED",
                "O e-mail informado ainda não foi verificado.",
                "user@vanrides.com",
                403
        )).when(emailVerificationService).assertVerifiedAccess(user);

        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@vanrides.com","senha":"secret123"}
                                """))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value("EMAIL_NOT_VERIFIED"))
                .andExpect(jsonPath("$.email").value("user@vanrides.com"));
    }

    @Test
    void shouldReturnBadRequestWhenPasswordFieldIsMissing() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":"user@vanrides.com","password":"secret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email e senha são obrigatórios."));
    }

    @Test
    void shouldReturnBadRequestWhenEmailIsBlank() throws Exception {
        mockMvc.perform(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"email":" ","senha":"secret123"}
                                """))
                .andExpect(status().isBadRequest())
                .andExpect(content().string("Email e senha são obrigatórios."));
    }
}
