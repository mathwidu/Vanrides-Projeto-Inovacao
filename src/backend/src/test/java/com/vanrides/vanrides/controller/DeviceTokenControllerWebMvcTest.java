package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.DeviceTokenInvalidateRequest;
import com.vanrides.vanrides.dto.DeviceTokenRegistrationRequest;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.DeviceTokenService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.util.Base64;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DeviceTokenController.class)
class DeviceTokenControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DeviceTokenService deviceTokenService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void shouldRegisterDeviceTokenForAuthenticatedUser() throws Exception {
        String authorization = basicHeader("passageiro.push@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(
                        21L,
                        UserRole.PASSENGER,
                        "passageiro.push@vanrides.dev",
                        "Passageiro",
                        null,
                        null
                ));

        mockMvc.perform(post("/devices/fcm-token")
                        .servletPath("/devices/fcm-token")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-123","platform":"ios"}
                                """))
                .andExpect(status().isNoContent());

        verify(deviceTokenService).register(
                eq(UserRole.PASSENGER),
                eq(21L),
                eq("passageiro.push@vanrides.dev"),
                eq(null),
                any(DeviceTokenRegistrationRequest.class)
        );
    }

    @Test
    void shouldInvalidateDeviceTokenForAuthenticatedUser() throws Exception {
        String authorization = basicHeader("driver.push@vanrides.dev", "Secret123");
        when(authorizationService.authenticate(authorization))
                .thenReturn(new AuthenticatedUser(
                        44L,
                        UserRole.DRIVER,
                        "driver.push@vanrides.dev",
                        "Motorista",
                        null,
                        66L
                ));

        mockMvc.perform(delete("/devices/fcm-token")
                        .servletPath("/devices/fcm-token")
                        .header("Authorization", authorization)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-123"}
                                """))
                .andExpect(status().isNoContent());

        verify(deviceTokenService).invalidate(
                eq(UserRole.DRIVER),
                eq(44L),
                any(DeviceTokenInvalidateRequest.class)
        );
    }

    @Test
    void shouldRejectRegistrationWithoutAuthorizationHeader() throws Exception {
        when(authorizationService.authenticate(null))
                .thenThrow(new IllegalArgumentException("Cabeçalho Authorization ausente"));

        mockMvc.perform(post("/devices/fcm-token")
                        .servletPath("/devices/fcm-token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"token":"token-123","platform":"ios"}
                                """))
                .andExpect(status().isUnauthorized());

        verify(deviceTokenService, never()).register(any(), any(), any(), any(), any());
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
