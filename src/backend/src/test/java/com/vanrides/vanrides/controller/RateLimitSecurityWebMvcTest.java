package com.vanrides.vanrides.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.config.CorsConfig;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.filtros.BasicRateLimitFilter;
import com.vanrides.vanrides.filtros.BoundedRateLimitStore;
import com.vanrides.vanrides.filtros.FilterAuthBasic;
import com.vanrides.vanrides.filtros.RateLimitMetrics;
import com.vanrides.vanrides.filtros.RequestCorrelationFilter;
import com.vanrides.vanrides.filtros.SensitiveRequestPolicy;
import com.vanrides.vanrides.filtros.TrustedProxyClientAddressResolver;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.EmailVerificationResendQueue;
import com.vanrides.vanrides.service.EmailVerificationLandingPageRenderer;
import com.vanrides.vanrides.service.EmailVerificationException;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PassengerOnboardingService;
import com.vanrides.vanrides.service.PendingEmailChangeService;
import com.vanrides.vanrides.service.RefreshTokenService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.support.StaticListableBeanFactory;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerInterceptor;

import java.util.concurrent.atomic.AtomicInteger;

import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.clearInvocations;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RateLimitSecurityWebMvcTest {

    private static final String ORIGIN = "https://app.vanrides.test";
    private static final String AUTHORIZATION = "Bearer test-only-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger handlerCalls = new AtomicInteger();

    private AuthService authService;
    private AuthorizationService authorizationService;
    private EmailVerificationService emailVerificationService;
    private PendingEmailChangeService pendingEmailChangeService;
    private DriverOnboardingService driverOnboardingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authorizationService = mock(AuthorizationService.class);
        emailVerificationService = mock(EmailVerificationService.class);
        pendingEmailChangeService = mock(PendingEmailChangeService.class);
        driverOnboardingService = mock(DriverOnboardingService.class);

        LoginController loginController = loginController();
        AuthController authController = authController();
        DriverOnboardingController driverController = new DriverOnboardingController(
                driverOnboardingService,
                mock(JwtService.class),
                mock(RefreshTokenService.class)
        );

        FilterAuthBasic authenticationFilter = new FilterAuthBasic();
        ReflectionTestUtils.setField(authenticationFilter, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(authenticationFilter, "objectMapper", objectMapper);
        when(authorizationService.authenticate(anyString(), eq(true))).thenReturn(driverUser());

        BasicRateLimitFilter rateLimitFilter = new BasicRateLimitFilter(
                objectMapper,
                true,
                new SensitiveRequestPolicy(),
                new TrustedProxyClientAddressResolver("", 1024, 10),
                new BoundedRateLimitStore(1, 60, 100),
                metrics(100)
        );
        CorsFilter corsFilter = new CorsConfig().corsFilter().getFilter();

        mockMvc = standaloneSetup(loginController, authController, driverController)
                .addInterceptors(handlerProbe())
                .addFilters(
                        new RequestCorrelationFilter(),
                        corsFilter,
                        rateLimitFilter,
                        authenticationFilter
                )
                .build();
    }

    @Test
    void shouldRateLimitLoginBeforeControllerAuthenticationAndService() throws Exception {
        MockHttpServletRequestBuilder warmup = login("rate-login-warmup");
        MockHttpServletRequestBuilder blocked = login("rate-login-blocked");

        warmAndAssertBlocked(warmup, blocked, "rate-login-blocked");
    }

    @Test
    void shouldRateLimitChangeEmailBeforeControllerAuthenticationAndService() throws Exception {
        MockHttpServletRequestBuilder warmup = changeEmail("rate-change-warmup");
        MockHttpServletRequestBuilder blocked = changeEmail("rate-change-blocked");

        warmAndAssertBlocked(warmup, blocked, "rate-change-blocked");
    }

    @Test
    void shouldSharePreviewBucketBetweenGetAndHeadBeforeControllerAndService() throws Exception {
        MockHttpServletRequestBuilder warmup = preview("token-a", "rate-preview-warmup");
        MockHttpServletRequestBuilder blocked = previewHead("token-b", "rate-preview-blocked");

        warmAndAssertBlocked(warmup, blocked, "rate-preview-blocked");
    }

    @Test
    void shouldAllowPublicPreviewHeadWhileQuotaIsAvailable() throws Exception {
        mockMvc.perform(previewHead("token-head", "rate-preview-head"))
                .andExpect(status().isOk())
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, "rate-preview-head"))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));
    }

    @Test
    void shouldNotSpendEmailConfirmationQuotaOnGetOrHead() throws Exception {
        mockMvc.perform(emailConfirm("token-a", "rate-confirm-get-one"))
                .andExpect(status().isOk());
        mockMvc.perform(emailConfirmHead("token-b", "rate-confirm-head"))
                .andExpect(status().isOk());
        mockMvc.perform(emailConfirm("token-c", "rate-confirm-get-two"))
                .andExpect(status().isOk());

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldRateLimitEmailConfirmationPostBeforeControllerAndService() throws Exception {
        when(emailVerificationService.confirm(anyString())).thenThrow(
                new EmailVerificationException(
                        "INVALID_EMAIL_VERIFICATION_TOKEN",
                        "Token de verificação inválido, expirado ou já utilizado.",
                        null,
                        400
                )
        );
        MockHttpServletRequestBuilder warmup = emailConfirmPost(
                "token-a",
                "rate-confirm-post-warmup"
        );
        MockHttpServletRequestBuilder blocked = emailConfirmPost(
                "token-b",
                "rate-confirm-post-blocked"
        );

        warmAndAssertBlocked(warmup, blocked, "rate-confirm-post-blocked");
    }

    @Test
    void shouldRateLimitRedeemAcrossDifferentTokensBeforeAuthenticationAndService() throws Exception {
        MockHttpServletRequestBuilder warmup = redeem("token-a", "rate-redeem-warmup");
        MockHttpServletRequestBuilder blocked = redeem("token-b", "rate-redeem-blocked");

        warmAndAssertBlocked(warmup, blocked, "rate-redeem-blocked");
    }

    private void warmAndAssertBlocked(
            MockHttpServletRequestBuilder warmup,
            MockHttpServletRequestBuilder blocked,
            String requestId
    ) throws Exception {
        mockMvc.perform(warmup).andReturn();
        handlerCalls.set(0);
        clearInvocations(
                authService,
                authorizationService,
                emailVerificationService,
                pendingEmailChangeService,
                driverOnboardingService
        );

        mockMvc.perform(blocked)
                .andExpect(status().isTooManyRequests())
                .andExpect(header().exists("Retry-After"))
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(jsonPath("$.code").value("RATE_LIMITED"));

        org.assertj.core.api.Assertions.assertThat(handlerCalls).hasValue(0);
        verifyNoInteractions(
                authService,
                authorizationService,
                emailVerificationService,
                pendingEmailChangeService,
                driverOnboardingService
        );
    }

    private MockHttpServletRequestBuilder login(String requestId) {
        return withHeaders(post("/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"user@vanrides.test","senha":"Secret123"}
                        """), requestId);
    }

    private MockHttpServletRequestBuilder changeEmail(String requestId) {
        return withHeaders(post("/auth/email-verifications/change-email")
                .contentType(MediaType.APPLICATION_JSON)
                .content("""
                        {"email":"old@vanrides.test","senha":"Secret123","newEmail":"new@vanrides.test"}
                        """), requestId);
    }

    private MockHttpServletRequestBuilder preview(String token, String requestId) {
        return withHeaders(get("/driver-onboarding/invites/{token}/preview", token), requestId);
    }

    private MockHttpServletRequestBuilder previewHead(String token, String requestId) {
        return withHeaders(head("/driver-onboarding/invites/{token}/preview", token), requestId);
    }

    private MockHttpServletRequestBuilder emailConfirm(String token, String requestId) {
        return withHeaders(get("/auth/email-verifications/confirm")
                .queryParam("token", token), requestId);
    }

    private MockHttpServletRequestBuilder emailConfirmHead(String token, String requestId) {
        return withHeaders(head("/auth/email-verifications/confirm")
                .queryParam("token", token), requestId);
    }

    private MockHttpServletRequestBuilder emailConfirmPost(String token, String requestId) {
        return withHeaders(post("/auth/email-verifications/confirm")
                .contentType(MediaType.APPLICATION_JSON)
                .content("{\"token\":\"" + token + "\"}"), requestId);
    }

    private MockHttpServletRequestBuilder redeem(String token, String requestId) {
        return withHeaders(post("/driver-onboarding/invites/{token}/redeem", token)
                .header("Authorization", AUTHORIZATION)
                .contentType(MediaType.APPLICATION_JSON)
                .content("{}"), requestId);
    }

    private MockHttpServletRequestBuilder withHeaders(
            MockHttpServletRequestBuilder request,
            String requestId
    ) {
        return request
                .header("Origin", ORIGIN)
                .header(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
    }

    private LoginController loginController() {
        LoginController controller = new LoginController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(controller, "refreshTokenService", mock(RefreshTokenService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(controller, "passengerOnboardingService", mock(PassengerOnboardingService.class));
        ReflectionTestUtils.setField(controller, "driverOnboardingService", driverOnboardingService);
        return controller;
    }

    private AuthController authController() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "refreshTokenService", mock(RefreshTokenService.class));
        ReflectionTestUtils.setField(controller, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(
                controller,
                "emailVerificationLandingPageRenderer",
                new EmailVerificationLandingPageRenderer()
        );
        ReflectionTestUtils.setField(controller, "emailVerificationResendQueue", mock(EmailVerificationResendQueue.class));
        ReflectionTestUtils.setField(controller, "pendingEmailChangeService", pendingEmailChangeService);
        return controller;
    }

    private RateLimitMetrics metrics(int capacity) {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", new SimpleMeterRegistry());
        return new RateLimitMetrics(beans.getBeanProvider(MeterRegistry.class), capacity);
    }

    private HandlerInterceptor handlerProbe() {
        return new HandlerInterceptor() {
            @Override
            public boolean preHandle(
                    HttpServletRequest request,
                    HttpServletResponse response,
                    Object handler
            ) {
                handlerCalls.incrementAndGet();
                return true;
            }
        };
    }

    private AuthenticatedUser driverUser() {
        return new AuthenticatedUser(
                7L,
                UserRole.DRIVER,
                "driver@vanrides.test",
                "Driver",
                null,
                11L
        );
    }
}
