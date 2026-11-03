package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Component
public class ViagemEtaCalculator {

    private static final int PLANNED_BASE_MINUTES = 12;
    private static final int IN_PROGRESS_BASE_MINUTES = 4;
    private static final int STEP_MINUTES = 6;

    public void recalculate(Viagem viagem, List<ViagemParada> orderedStops) {
        int currentEta = initialEtaForTrip(viagem.getStatus());

        for (ViagemParada stop : orderedStops) {
            if (stop.getStatus() == ViagemParadaStatus.PENDING) {
                stop.setEtaMinutos(currentEta);
                currentEta += STEP_MINUTES;
            } else {
                stop.setEtaMinutos(0);
            }
        }

        viagem.setEtaBaseMinutos(firstPendingEta(orderedStops));
        viagem.setEtaAtualizadoEm(LocalDateTime.now());
    }

    private int initialEtaForTrip(ViagemStatus status) {
        return switch (status) {
            case PLANNED -> PLANNED_BASE_MINUTES;
            case IN_PROGRESS -> IN_PROGRESS_BASE_MINUTES;
            case COMPLETED, CANCELED -> 0;
        };
    }

    private Integer firstPendingEta(List<ViagemParada> orderedStops) {
        return orderedStops.stream()
                .filter(stop -> stop.getStatus() == ViagemParadaStatus.PENDING)
                .map(ViagemParada::getEtaMinutos)
                .findFirst()
                .orElse(0);
    }
}
