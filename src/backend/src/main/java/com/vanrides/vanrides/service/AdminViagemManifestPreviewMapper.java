package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminViagemManifestPassengerResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestPreviewResponse;

import java.util.List;

final class AdminViagemManifestPreviewMapper {

    private AdminViagemManifestPreviewMapper() {
    }

    static AdminViagemManifestPreviewResponse toResponse(ViagemManifestSyncService.ManifestSyncPreview preview) {
        return new AdminViagemManifestPreviewResponse(
                preview.viagemId(),
                preview.servicoId(),
                preview.servicoNome(),
                preview.dataOperacao(),
                preview.paradasAtuais(),
                preview.paradasAposSincronizacao(),
                preview.adicionados(),
                preview.removidos(),
                preview.mantidos(),
                preview.mudancas(),
                preview.semMudancas(),
                toPassengers(preview.passageirosAdicionados()),
                toPassengers(preview.passageirosRemovidos()),
                toPassengers(preview.passageirosMantidos())
        );
    }

    private static List<AdminViagemManifestPassengerResponse> toPassengers(
            List<ViagemManifestSyncService.ManifestSyncPassenger> passengers) {
        return passengers.stream()
                .map(passenger -> new AdminViagemManifestPassengerResponse(
                        passenger.passageiroId(),
                        passenger.nome(),
                        passenger.menorDeIdade(),
                        passenger.ordemAtual(),
                        passenger.ordemNova()
                ))
                .toList();
    }
}
