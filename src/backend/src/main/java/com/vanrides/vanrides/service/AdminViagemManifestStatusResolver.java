package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminViagemManifestStatusResponse;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
class AdminViagemManifestStatusResolver {

    @Autowired
    private ViagemManifestSyncService viagemManifestSyncService;

    AdminViagemManifestStatusResponse resolve(Viagem viagem, int quantidadeParadas) {
        if (viagem.getServicoRecorrente() == null || viagem.getId() == null) {
            return AdminViagemManifestStatusResponse.notApplicable();
        }
        if (viagem.getStatus() != ViagemStatus.PLANNED) {
            return AdminViagemManifestStatusResponse.blocked(
                    "A viagem já saiu da fase planejada; revise mudanças manualmente."
            );
        }

        try {
            ViagemManifestSyncService.ManifestSyncPreview preview =
                    viagemManifestSyncService.previewSincronizacaoManifesto(viagem.getCompany().getId(), viagem.getId());
            if (preview.semMudancas()) {
                return AdminViagemManifestStatusResponse.upToDate(quantidadeParadas);
            }
            return AdminViagemManifestStatusResponse.pending(preview.adicionados(), preview.removidos());
        } catch (ResponseStatusException exception) {
            return AdminViagemManifestStatusResponse.blocked(exception.getReason());
        }
    }
}
