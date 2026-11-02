package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.config.CorsConfig;
import com.vanrides.vanrides.service.EmailVerificationLandingPageRenderer;
import com.vanrides.vanrides.service.EmailVerificationResendQueue;
import com.vanrides.vanrides.service.EmailVerificationService;
import com.vanrides.vanrides.service.JwtService;
import com.vanrides.vanrides.service.PendingEmailChangeService;
import com.vanrides.vanrides.service.RefreshTokenService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.head;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.options;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.setup.MockMvcBuilders.standaloneSetup;

class EmailVerificationLandingWebMvcTest {

    private static final String PATH = "/auth/email-verifications/confirm";
    private static final String SECRET = "legacy-secret-never-render";

    private EmailVerificationService emailVerificationService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        emailVerificationService = mock(EmailVerificationService.class);
        EmailVerificationLandingPageRenderer renderer =
                new EmailVerificationLandingPageRenderer();
        AuthController controller = new AuthController();
        ReflectionTestUtils.setField(controller, "refreshTokenService", mock(RefreshTokenService.class));
        ReflectionTestUtils.setField(controller, "jwtService", mock(JwtService.class));
        ReflectionTestUtils.setField(controller, "emailVerificationService", emailVerificationService);
        ReflectionTestUtils.setField(controller, "emailVerificationLandingPageRenderer", renderer);
        ReflectionTestUtils.setField(controller, "emailVerificationResendQueue", mock(EmailVerificationResendQueue.class));
        ReflectionTestUtils.setField(controller, "pendingEmailChangeService", mock(PendingEmailChangeService.class));

        mockMvc = standaloneSetup(controller)
                .addFilters(new CorsConfig().corsFilter().getFilter())
                .build();
    }

    @Test
    void shouldServeAProtectedNonMutatingLandingForCleanAndLegacyGet() throws Exception {
        MvcResult clean = mockMvc.perform(get(PATH)
                        .header("Purpose", "prefetch")
                        .header("Sec-Purpose", "prefetch;prerender"))
                .andExpect(status().isOk())
                .andExpect(content().contentType("text/html;charset=UTF-8"))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Pragma", "no-cache"))
                .andExpect(header().string("Expires", "0"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().string("X-Content-Type-Options", "nosniff"))
                .andExpect(header().string("X-Frame-Options", "DENY"))
                .andExpect(header().string(
                        "X-Robots-Tag",
                        "noindex, nofollow, noarchive, nosnippet"
                ))
                .andExpect(header().exists("Content-Security-Policy"))
                .andExpect(header().exists("Permissions-Policy"))
                .andReturn();

        MvcResult legacy = mockMvc.perform(get(PATH).queryParam("token", SECRET))
                .andExpect(status().isOk())
                .andReturn();

        assertThat(legacy.getResponse().getContentAsByteArray())
                .isEqualTo(clean.getResponse().getContentAsByteArray());
        for (String headerName : List.of(
                "Content-Type",
                "Cache-Control",
                "Pragma",
                "Expires",
                "Referrer-Policy",
                "Content-Security-Policy",
                "X-Content-Type-Options",
                "X-Frame-Options",
                "X-Robots-Tag",
                "Permissions-Policy"
        )) {
            assertThat(legacy.getResponse().getHeader(headerName))
                    .isEqualTo(clean.getResponse().getHeader(headerName));
        }
        assertThat(legacy.getResponse().getContentAsString()).doesNotContain(SECRET);
        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldServeHeadWithoutBodyOrMutation() throws Exception {
        mockMvc.perform(head(PATH).queryParam("token", SECRET))
                .andExpect(status().isOk())
                .andExpect(content().string(""))
                .andExpect(header().string("Cache-Control", "no-store, max-age=0"))
                .andExpect(header().string("Referrer-Policy", "no-referrer"))
                .andExpect(header().exists("Content-Security-Policy"));

        verifyNoInteractions(emailVerificationService);
    }

    @Test
    void shouldKeepOptionsNonMutating() throws Exception {
        mockMvc.perform(options(PATH)
                        .header("Origin", "https://app.vanrides.test")
                        .header("Access-Control-Request-Method", "POST")
                        .header("Access-Control-Request-Headers", "Content-Type"))
                .andExpect(status().isOk())
                .andExpect(header().string("Access-Control-Allow-Origin", "https://app.vanrides.test"));

        verifyNoInteractions(emailVerificationService);
    }
}
