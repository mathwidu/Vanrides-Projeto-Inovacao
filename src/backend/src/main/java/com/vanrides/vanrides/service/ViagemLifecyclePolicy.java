package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

final class ViagemLifecyclePolicy {

    private ViagemLifecyclePolicy() {
    }

    static void validateTripStatusTransition(ViagemStatus currentStatus, ViagemStatus targetStatus) {
        if (targetStatus == null || currentStatus == targetStatus) {
            return;
        }

        if (isClosed(currentStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A viagem já foi encerrada e não aceita novas transições operacionais."
            );
        }

        if (targetStatus == ViagemStatus.IN_PROGRESS && currentStatus != ViagemStatus.PLANNED) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A corrida só pode ser iniciada a partir de uma viagem planejada."
            );
        }

        if (targetStatus == ViagemStatus.COMPLETED
                && currentStatus != ViagemStatus.PLANNED
                && currentStatus != ViagemStatus.IN_PROGRESS) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A corrida só pode ser encerrada a partir de uma viagem ativa."
            );
        }
    }

    static void validateStopStatusTransition(ViagemStatus tripStatus,
                                             ViagemParadaStatus stopStatus,
                                             ViagemParadaStatus targetStatus) {
        if (isClosed(tripStatus)) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A corrida já foi encerrada e não permite alterar paradas."
            );
        }
        if (stopStatus != ViagemParadaStatus.PENDING) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A parada atual já saiu do estado pendente."
            );
        }
        if (targetStatus != ViagemParadaStatus.COMPLETED
                && targetStatus != ViagemParadaStatus.SKIPPED
                && targetStatus != ViagemParadaStatus.NO_SHOW) {
            throw new ResponseStatusException(
                    HttpStatus.BAD_REQUEST,
                    "A operação da corrida só aceita concluir, pular ou marcar no-show na parada atual."
            );
        }
    }

    static ViagemStatus statusAfterStopUpdate(ViagemStatus currentTripStatus,
                                              List<ViagemParadaStatus> stopStatuses) {
        if (stopStatuses.stream().allMatch(status -> status != ViagemParadaStatus.PENDING)) {
            return ViagemStatus.COMPLETED;
        }
        if (currentTripStatus == ViagemStatus.PLANNED) {
            return ViagemStatus.IN_PROGRESS;
        }
        return currentTripStatus;
    }

    private static boolean isClosed(ViagemStatus status) {
        return status == ViagemStatus.CANCELED || status == ViagemStatus.COMPLETED;
    }
}
