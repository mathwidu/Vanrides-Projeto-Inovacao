package com.vanrides.vanrides.auth;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserRoleTest {

    @Test
    void shouldNormalizeLegacyDriverRole() {
        assertThat(UserRole.fromValue("MOTORISTA")).isEqualTo(UserRole.DRIVER);
    }

    @Test
    void shouldNormalizeLegacyPassengerRole() {
        assertThat(UserRole.fromValue("PASSAGEIRO")).isEqualTo(UserRole.PASSENGER);
    }

    @Test
    void shouldKeepCanonicalRolesStable() {
        assertThat(UserRole.fromValue("COMPANY_ADMIN")).isEqualTo(UserRole.COMPANY_ADMIN);
        assertThat(UserRole.fromValue("DRIVER")).isEqualTo(UserRole.DRIVER);
        assertThat(UserRole.fromValue("PASSENGER")).isEqualTo(UserRole.PASSENGER);
        assertThat(UserRole.fromValue("GUARDIAN")).isEqualTo(UserRole.GUARDIAN);
    }
}
