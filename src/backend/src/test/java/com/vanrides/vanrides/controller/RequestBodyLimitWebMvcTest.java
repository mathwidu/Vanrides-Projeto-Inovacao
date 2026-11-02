package com.vanrides.vanrides.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.config.CorsConfig;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.filtros.FilterAuthBasic;
import com.vanrides.vanrides.filtros.RateLimitMetrics;
import com.vanrides.vanrides.filtros.RequestBodyLimitFilter;
import com.vanrides.vanrides.filtros.RequestCorrelationFilter;
import com.vanrides.vanrides.service.AuthService;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DriverOnboardingService;
import com.vanrides.vanrides.service.EmailVerificationResendQueue;
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
import org.springframework.test.web.servlet.RequestBuilder;
import org.springframework.test.web.servlet.request.MockHttpServletRequestBuilder;
import org.springframework.web.filter.CorsFilter;
import org.springframework.web.servlet.HandlerInterceptor;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class RequestBodyLimitWebMvcTest {

    private static final int MAX_BYTES = 128;
    private static final String ORIGIN = "https://app.vanrides.test";
    private static final String AUTHORIZATION = "Bearer test-only-token";

    private final ObjectMapper objectMapper = new ObjectMapper();
    private final AtomicInteger handlerCalls = new AtomicInteger();

    private AuthService authService;
    private AuthorizationService authorizationService;
    private PendingEmailChangeService pendingEmailChangeService;
    private DriverOnboardingService driverOnboardingService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        authService = mock(AuthService.class);
        authorizationService = mock(AuthorizationService.class);
        pendingEmailChangeService = mock(PendingEmailChangeService.class);
        driverOnboardingService = mock(DriverOnboardingService.class);

        FilterAuthBasic authenticationFilter = new FilterAuthBasic();
        ReflectionTestUtils.setField(authenticationFilter, "authorizationService", authorizationService);
        ReflectionTestUtils.setField(authenticationFilter, "objectMapper", objectMapper);

        RequestBodyLimitFilter bodyFilter = new RequestBodyLimitFilter(
                objectMapper,
                MAX_BYTES,
                metrics()
        );
        CorsFilter corsFilter = new CorsConfig().corsFilter().getFilter();

        mockMvc = standaloneSetup(loginController(), authController(), driverController())
                .setControllerAdvice(new RequestBodyLimitAdvice(), new GlobalApiExceptionHandler())
                .addInterceptors(handlerProbe())
                .addFilters(
                        new RequestCorrelationFilter(),
                        corsFilter,
                        bodyFilter,
                        authenticationFilter
                )
                .build();
    }

    @Test
    void shouldRejectKnownOversizedBodiesForAllSensitiveFamiliesBeforeApplicationCode() throws Exception {
        byte[] oversized = new byte[MAX_BYTES + 1];
        List<MockHttpServletRequestBuilder> requests = List.of(
                post("/login").contentType(MediaType.APPLICATION_JSON).content(oversized),
                post("/auth/email-verifications/change-email")
                        .contentType(MediaType.APPLICATION_JSON).content(oversized),
                get("/driver-onboarding/invites/token-a/preview")
                        .contentType(MediaType.APPLICATION_JSON).content(oversized),
                post("/driver-onboarding/invites/token-a/redeem")
                        .header("Authorization", AUTHORIZATION)
                        .contentType(MediaType.APPLICATION_JSON).content(oversized)
        );

        for (int index = 0; index < requests.size(); index++) {
            String requestId = "known-body-" + index;
            assertPayloadTooLarge(withHeaders(requests.get(index), requestId), requestId);
        }

        assertThat(handlerCalls).hasValue(0);
        verifyNoInteractions(
                authService,
                authorizationService,
                pendingEmailChangeService,
                driverOnboardingService
        );
    }

    @Test
    void shouldCountAnUnknownLengthStreamOnlyUntilLimitPlusOne() throws Exception {
        UnknownLengthRequestBuilder request = new UnknownLengthRequestBuilder(
                "/login",
                new byte[MAX_BYTES + 32],
                "unknown-stream"
        );

        assertPayloadTooLarge(request, "unknown-stream");

        assertThat(request.bytesRead()).isEqualTo(MAX_BYTES + 1);
        assertThat(handlerCalls).hasValue(0);
        verifyNoInteractions(
                authService,
                authorizationService,
                pendingEmailChangeService,
                driverOnboardingService
        );
    }

    @Test
    void shouldRejectFormUrlEncodedOutsideTheApiContractWithCorsAndRequestId() throws Exception {
        mockMvc.perform(withHeaders(post("/login")
                        .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                        .content("email=user%40vanrides.test&senha=Secret123"), "form-rejected"))
                .andExpect(status().isUnsupportedMediaType())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        "form-rejected"
                ))
                .andExpect(jsonPath("$.code").value("UNSUPPORTED_BODY_MEDIA_TYPE"));

        assertThat(handlerCalls).hasValue(0);
        verifyNoInteractions(
                authService,
                authorizationService,
                pendingEmailChangeService,
                driverOnboardingService
        );
    }

    @Test
    void shouldAllowAnExactSizeBodyToReachTheControllerAndService() throws Exception {
        byte[] exactBody = exactLoginBody();

        mockMvc.perform(withHeaders(post("/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(exactBody), "exact-body"))
                .andExpect(status().isUnauthorized())
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, "exact-body"))
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN));

        assertThat(exactBody).hasSize(MAX_BYTES);
        assertThat(handlerCalls).hasValue(1);
        verify(authService).authenticate("user@vanrides.test", "Secret123");
    }

    @Test
    void shouldReturnMissingRouteWithoutReadingUnknownBody() throws Exception {
        UnknownLengthRequestBuilder request = new UnknownLengthRequestBuilder(
                "/inexistente",
                new byte[MAX_BYTES + 32],
                "missing-route"
        );

        mockMvc.perform(request)
                .andExpect(status().isNotFound())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, "missing-route"));

        assertThat(request.bytesRead()).isZero();
        verifyNoInteractions(authorizationService, driverOnboardingService);
    }

    @Test
    void shouldAuthenticateProtectedRouteBeforeReadingUnknownBody() throws Exception {
        UnknownLengthRequestBuilder request = new UnknownLengthRequestBuilder(
                "/driver-onboarding/requests",
                new byte[MAX_BYTES + 32],
                "auth-before-body"
        );

        mockMvc.perform(request)
                .andExpect(status().isUnauthorized())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string(
                        RequestCorrelationFilter.REQUEST_ID_HEADER,
                        "auth-before-body"
                ));

        assertThat(request.bytesRead()).isZero();
        verify(authorizationService).authenticate(null);
        verifyNoInteractions(driverOnboardingService);
    }

    @Test
    void shouldRejectLazyUnknownBodyBeforeAuthenticatedControllerMutation() throws Exception {
        byte[] validJsonWithOversizedPadding = Arrays.copyOf(
                "{\"companyId\":11}".getBytes(StandardCharsets.UTF_8),
                MAX_BYTES + 32
        );
        Arrays.fill(
                validJsonWithOversizedPadding,
                "{\"companyId\":11}".length(),
                validJsonWithOversizedPadding.length,
                (byte) ' '
        );
        UnknownLengthRequestBuilder request = new UnknownLengthRequestBuilder(
                "/driver-onboarding/requests",
                validJsonWithOversizedPadding,
                "lazy-oversized",
                AUTHORIZATION
        );
        when(authorizationService.authenticate(AUTHORIZATION)).thenReturn(new AuthenticatedUser(
                7L,
                UserRole.DRIVER,
                "driver@vanrides.test",
                "Driver",
                null,
                11L
        ));

        assertPayloadTooLarge(request, "lazy-oversized");

        assertThat(request.bytesRead()).isEqualTo(MAX_BYTES + 1);
        verifyNoInteractions(driverOnboardingService);
    }

    private void assertPayloadTooLarge(RequestBuilder request, String requestId) throws Exception {
        mockMvc.perform(request)
                .andExpect(status().isPayloadTooLarge())
                .andExpect(header().string("Access-Control-Allow-Origin", ORIGIN))
                .andExpect(header().string(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId))
                .andExpect(jsonPath("$.code").value("PAYLOAD_TOO_LARGE"));
    }

    private MockHttpServletRequestBuilder withHeaders(
            MockHttpServletRequestBuilder request,
            String requestId
    ) {
        return request
                .header("Origin", ORIGIN)
                .header(RequestCorrelationFilter.REQUEST_ID_HEADER, requestId);
    }

    private byte[] exactLoginBody() {
        byte[] json = "{\"email\":\"user@vanrides.test\",\"senha\":\"Secret123\"}"
                .getBytes(StandardCharsets.UTF_8);
        byte[] exact = Arrays.copyOf(json, MAX_BYTES);
        Arrays.fill(exact, json.length, exact.length, (byte) ' ');
        return exact;
    }

    private LoginController loginController() {
        LoginController controller = new LoginController();
        ReflectionTestUtils.setField(controller, "authService", authService);
        ReflectionTestUtils.setField(controller, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(controller, "refreshTokenService", mock(RefreshTokenService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationService", mock(EmailVerificationService.class));
        ReflectionTestUtils.setField(controller, "passengerOnboardingService", mock(PassengerOnboardingService.class));
        ReflectionTestUtils.setField(controller, "driverOnboardingService", driverOnboardingService);
        return controller;
    }

    private AuthController authController() {
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "refreshTokenService", mock(RefreshTokenService.class));
        ReflectionTestUtils.setField(controller, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationService", mock(EmailVerificationService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationResendQueue", mock(EmailVerificationResendQueue.class));
        ReflectionTestUtils.setField(controller, "pendingEmailChangeService", pendingEmailChangeService);
        return controller;
    }

    private DriverOnboardingController driverController() {
        return new DriverOnboardingController(
                driverOnboardingService,
                mock(JwtService.class),
                mock(RefreshTokenService.class)
        );
    }

    private RateLimitMetrics metrics() {
        StaticListableBeanFactory beans = new StaticListableBeanFactory();
        beans.addBean("meterRegistry", new SimpleMeterRegistry());
        return new RateLimitMetrics(beans.getBeanProvider(MeterRegistry.class), 100);
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

}
