package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.ViagemParada;
import com.vanrides.vanrides.model.ViagemParadaStatus;

import java.util.ArrayList;
import java.util.List;

final class ViagemRotaStopPointResolver {

    private ViagemRotaStopPointResolver() {
    }

    static List<ViagemRotaStopPoint> resolve(List<ViagemParada> paradas, Long passengerId) {
        ViagemParada proximaParada = paradas.stream()
                .filter(item -> item.getStatus() == ViagemParadaStatus.PENDING)
                .findFirst()
                .orElse(null);

        List<ViagemRotaStopPoint> stopPoints = new ArrayList<>();
        for (ViagemParada parada : paradas) {
            Passageiro passageiro = parada.getPassageiro();
            if (passageiro.getLatitude() == null || passageiro.getLongitude() == null) {
                continue;
            }
            stopPoints.add(new ViagemRotaStopPoint(
                    parada.getId(),
                    passageiro.getId(),
                    passageiro.getNome(),
                    passageiro.getLatitude(),
                    passageiro.getLongitude(),
                    parada.getOrdem(),
                    parada.getStatus(),
                    proximaParada != null && proximaParada.getId().equals(parada.getId()),
                    passengerId != null && passengerId.equals(passageiro.getId())
            ));
        }
        return stopPoints.stream()
                .sorted((left, right) -> Integer.compare(left.ordem(), right.ordem()))
                .toList();
    }
}

record ViagemRotaStopPoint(Long paradaId,
                           Long passageiroId,
                           String nome,
                           Double latitude,
                           Double longitude,
                           Integer ordem,
                           ViagemParadaStatus status,
                           boolean proximaParada,
                           boolean paradaDoPassageiro) {
}
