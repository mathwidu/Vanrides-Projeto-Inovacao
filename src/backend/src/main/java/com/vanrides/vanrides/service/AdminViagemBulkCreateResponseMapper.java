package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminViagemBulkCreateResponse;
import com.vanrides.vanrides.dto.AdminViagemBulkIgnoredResponse;
import com.vanrides.vanrides.dto.AdminViagemSummaryResponse;

import java.util.List;

final class AdminViagemBulkCreateResponseMapper {

    private AdminViagemBulkCreateResponseMapper() {
    }

    static AdminViagemBulkCreateResponse toResponse(
            ViagemProvisioningService.ViagensProvisionadasResult result,
            List<AdminViagemSummaryResponse> viagens) {
        return new AdminViagemBulkCreateResponse(
                result.dataOperacao(),
                result.criadas(),
                result.existentes(),
                result.atualizadas(),
                result.bloqueadas(),
                result.ignoradas(),
                viagens,
                result.servicosIgnorados()
                        .stream()
                        .map(ignorada -> new AdminViagemBulkIgnoredResponse(
                                ignorada.servicoId(),
                                ignorada.servicoNome(),
                                ignorada.motivo().name(),
                                ignorada.mensagem()
                        ))
                        .toList(),
                result.servicosBloqueados()
                        .stream()
                        .map(bloqueado -> new AdminViagemBulkIgnoredResponse(
                                bloqueado.servicoId(),
                                bloqueado.servicoNome(),
                                bloqueado.motivo().name(),
                                bloqueado.mensagem()
                        ))
                        .toList()
        );
    }
}
