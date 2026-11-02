package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ViagemLifecyclePolicyTest {

    @Test
    void shouldAllowStartingPlannedTrip() {
        assertThatCode(() -> ViagemLifecyclePolicy.validateTripStatusTransition(
                ViagemStatus.PLANNED,
                ViagemStatus.IN_PROGRESS
        )).doesNotThrowAnyException();
    }

    @Test
    void shouldRejectOperationalTransitionAfterTripIsCompleted() {
        assertThatThrownBy(() -> ViagemLifecyclePolicy.validateTripStatusTransition(
                ViagemStatus.COMPLETED,
                ViagemStatus.IN_PROGRESS
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("encerrada");
    }

    @Test
    void shouldRejectStopTransitionWhenTripIsClosed() {
        assertThatThrownBy(() -> ViagemLifecyclePolicy.validateStopStatusTransition(
                ViagemStatus.CANCELED,
                ViagemParadaStatus.PENDING,
                ViagemParadaStatus.COMPLETED
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("encerrada");
    }

    @Test
    void shouldRejectChangingStopThatAlreadyLeftPendingState() {
        assertThatThrownBy(() -> ViagemLifecyclePolicy.validateStopStatusTransition(
                ViagemStatus.IN_PROGRESS,
                ViagemParadaStatus.COMPLETED,
                ViagemParadaStatus.SKIPPED
        ))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("pendente");
    }

    @Test
    void shouldCompleteTripWhenEveryStopLeftPendingState() {
        ViagemStatus status = ViagemLifecyclePolicy.statusAfterStopUpdate(
                ViagemStatus.IN_PROGRESS,
                List.of(ViagemParadaStatus.COMPLETED, ViagemParadaStatus.SKIPPED)
        );

        assertThat(status).isEqualTo(ViagemStatus.COMPLETED);
    }

    @Test
    void shouldPromotePlannedTripWhenThereArePendingStops() {
        ViagemStatus status = ViagemLifecyclePolicy.statusAfterStopUpdate(
                ViagemStatus.PLANNED,
                List.of(ViagemParadaStatus.COMPLETED, ViagemParadaStatus.PENDING)
        );

        assertThat(status).isEqualTo(ViagemStatus.IN_PROGRESS);
    }

    @Test
    void shouldKeepActiveTripWhenThereArePendingStops() {
        ViagemStatus status = ViagemLifecyclePolicy.statusAfterStopUpdate(
                ViagemStatus.IN_PROGRESS,
                List.of(ViagemParadaStatus.COMPLETED, ViagemParadaStatus.PENDING)
        );

        assertThat(status).isEqualTo(ViagemStatus.IN_PROGRESS);
    }
}
