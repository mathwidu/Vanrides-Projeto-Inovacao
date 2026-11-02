package com.vanrides.vanrides.service;

import io.jsonwebtoken.Claims;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;

class JwtServiceTest {

    @Test
    void shouldEmbedCompanyIdClaimWhenGeneratingAccessToken() {
        JwtService jwtService = new JwtService();
        ReflectionTestUtils.setField(jwtService, "configuredSecret", "0123456789abcdef0123456789abcdef");
        ReflectionTestUtils.setField(jwtService, "accessTokenTtlMinutes", 15L);
        jwtService.init();

        String token = jwtService.generateAccessToken(42L, "DRIVER", "driver@vanrides.dev", 88L);
        Claims claims = jwtService.parseAndValidate(token);

        assertThat(claims.getSubject()).isEqualTo("42");
        assertThat(claims.get("role", String.class)).isEqualTo("DRIVER");
        assertThat(claims.get("email", String.class)).isEqualTo("driver@vanrides.dev");
        assertThat(claims.get("companyId", Number.class).longValue()).isEqualTo(88L);
    }
}
