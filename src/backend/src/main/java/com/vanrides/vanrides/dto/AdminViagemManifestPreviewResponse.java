package com.vanrides.vanrides.dto;

import java.time.LocalDate;
import java.util.List;

public record AdminViagemManifestPreviewResponse(
        Long viagemId,
        Long servicoId,
        String servicoNome,
        LocalDate dataOperacao,
        int paradasAtuais,
        int paradasAposSincronizacao,
        int adicionados,
        int removidos,
        int mantidos,
        int mudancas,
        boolean semMudancas,
        List<AdminViagemManifestPassengerResponse> passageirosAdicionados,
        List<AdminViagemManifestPassengerResponse> passageirosRemovidos,
        List<AdminViagemManifestPassengerResponse> passageirosMantidos
) {
}
