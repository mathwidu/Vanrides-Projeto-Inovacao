package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.exception.ApiStatusException;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class GuardianPassengerMutationPolicyTest {

    @Test
    void shouldAllowCompanyAdminToMutatePassengerProfile() {
        assertThatCode(() -> GuardianPassengerMutationPolicy.requireCanMutatePassengerProfile(UserRole.COMPANY_ADMIN))
                .doesNotThrowAnyException();
    }

    @Test
    void shouldRejectGuardianPassengerProfileMutationAsUnavailable() {
        assertThatThrownBy(() -> GuardianPassengerMutationPolicy.requireCanMutatePassengerProfile(UserRole.GUARDIAN))
                .isInstanceOf(ApiStatusException.class)
                .satisfies(exception -> {
                    ApiStatusException apiException = (ApiStatusException) exception;
                    assertThat(apiException.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(apiException.getApiCode()).isEqualTo("ACTION_UNAVAILABLE");
                    assertThat(apiException.getReason()).isEqualTo(GuardianPassengerMutationPolicy.PASSENGER_PROFILE_MUTATION_UNAVAILABLE_MESSAGE);
                });
    }

    @Test
    void shouldRejectOtherRolesAsForbidden() {
        assertThatThrownBy(() -> GuardianPassengerMutationPolicy.requireCanMutatePassengerProfile(UserRole.PASSENGER))
                .isInstanceOf(ResponseStatusException.class)
                .satisfies(exception -> {
                    ResponseStatusException statusException = (ResponseStatusException) exception;
                    assertThat(statusException.getStatusCode()).isEqualTo(HttpStatus.FORBIDDEN);
                    assertThat(statusException.getReason()).isEqualTo("Papel sem permissão para esta operação");
                });
    }
}
