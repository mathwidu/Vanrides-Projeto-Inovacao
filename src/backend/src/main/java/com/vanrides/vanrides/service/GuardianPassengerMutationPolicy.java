package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.exception.ApiStatusException;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

public final class GuardianPassengerMutationPolicy {

    public static final String PASSENGER_PROFILE_MUTATION_UNAVAILABLE_MESSAGE =
            "Responsável pode consultar passageiros vinculados e decidir presenças, mas alterações cadastrais são feitas pela empresa.";

    private GuardianPassengerMutationPolicy() {
    }

    public static void requireCanMutatePassengerProfile(UserRole role) {
        if (role.isCompanyAdmin()) {
            return;
        }

        if (role.isGuardian()) {
            throw new ApiStatusException(
                    HttpStatus.FORBIDDEN,
                    "ACTION_UNAVAILABLE",
                    PASSENGER_PROFILE_MUTATION_UNAVAILABLE_MESSAGE
            );
        }

        throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação");
    }
}
