package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminMotoristaSummaryResponse;
import com.vanrides.vanrides.dto.AdminViagemManifestStatusResponse;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;
import com.vanrides.vanrides.model.Viagem;

final class AdminViagemSummaryMapper {

    private AdminViagemSummaryMapper() {
    }

    static AdminViagemSummaryResponse toSummary(Viagem viagem, int quantidadeParadas) {
        return toSummary(viagem, quantidadeParadas, AdminViagemManifestStatusResponse.notApplicable());
    }

    static AdminViagemSummaryResponse toSummary(
            Viagem viagem,
            int quantidadeParadas,
            AdminViagemManifestStatusResponse manifesto
    ) {
        return new AdminViagemSummaryResponse(
                viagem.getId(),
                viagem.getRotaBaseIdOrNull(),
                viagem.getServicoRecorrenteIdOrNull(),
                viagem.getRotaBase() == null ? null : viagem.getRotaBase().getNome(),
                viagem.getNomeOperacional(),
                viagem.getTipoOperacaoSnapshot(),
                viagem.getDataOperacao(),
                viagem.getHorarioPartida(),
                viagem.getStatus(),
                new AdminMotoristaSummaryResponse(
                        viagem.getMotorista().getId(),
                        viagem.getMotorista().getNome(),
                        viagem.getMotorista().getEmail(),
                        viagem.getMotorista().getTelefone(),
                        viagem.getMotorista().isEmailVerified(),
                        viagem.getMotorista().getDataNascimento(),
                        viagem.getMotorista().getFotoPerfilUrl()
                ),
                quantidadeParadas,
                manifesto
        );
    }
}
