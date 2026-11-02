package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemStatus;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CompanyTripManifestMaintenanceService {

    @Autowired
    private ViagemManifestSyncService viagemManifestSyncService;

    public ExistingTripMaintenance maintain(Long companyId, ServicoRecorrente servico, Viagem existing) {
        if (existing.getStatus() != ViagemStatus.PLANNED) {
            return ExistingTripMaintenance.bloqueada(existing, blockedService(servico, existing));
        }

        try {
            ViagemManifestSyncService.ManifestSyncPreview preview =
                    viagemManifestSyncService.previewSincronizacaoManifesto(companyId, existing.getId());
            if (preview.semMudancas()) {
                return ExistingTripMaintenance.existente(existing);
            }
            return ExistingTripMaintenance.atualizada(
                    viagemManifestSyncService.sincronizarManifesto(companyId, existing.getId())
            );
        } catch (ResponseStatusException exception) {
            return ExistingTripMaintenance.bloqueada(
                    existing,
                    blockedService(servico, existing, exception.getReason())
            );
        }
    }

    public enum MaintenanceResult {
        EXISTENTE,
        ATUALIZADA,
        BLOQUEADA
    }

    public enum MotivoServicoBloqueado {
        VIAGEM_EM_ANDAMENTO,
        VIAGEM_CONCLUIDA,
        VIAGEM_CANCELADA,
        MANIFESTO_INVALIDO
    }

    public record ServicoBloqueado(
            Long servicoId,
            String servicoNome,
            Long viagemId,
            MotivoServicoBloqueado motivo,
            String mensagem
    ) {
    }

    public record ExistingTripMaintenance(
            Viagem viagem,
            MaintenanceResult result,
            ServicoBloqueado bloqueio
    ) {
        static ExistingTripMaintenance existente(Viagem viagem) {
            return new ExistingTripMaintenance(viagem, MaintenanceResult.EXISTENTE, null);
        }

        static ExistingTripMaintenance atualizada(Viagem viagem) {
            return new ExistingTripMaintenance(viagem, MaintenanceResult.ATUALIZADA, null);
        }

        static ExistingTripMaintenance bloqueada(Viagem viagem, ServicoBloqueado bloqueio) {
            return new ExistingTripMaintenance(viagem, MaintenanceResult.BLOQUEADA, bloqueio);
        }
    }

    private ServicoBloqueado blockedService(ServicoRecorrente servico, Viagem viagem) {
        return blockedService(servico, viagem, null);
    }

    private ServicoBloqueado blockedService(ServicoRecorrente servico, Viagem viagem, String reason) {
        MotivoServicoBloqueado motivo = blockedReason(viagem);
        return new ServicoBloqueado(
                servico.getId(),
                servico.getNome(),
                viagem.getId(),
                motivo,
                blockedMessage(motivo, reason)
        );
    }

    private MotivoServicoBloqueado blockedReason(Viagem viagem) {
        if (viagem.getStatus() == ViagemStatus.IN_PROGRESS) {
            return MotivoServicoBloqueado.VIAGEM_EM_ANDAMENTO;
        }
        if (viagem.getStatus() == ViagemStatus.COMPLETED) {
            return MotivoServicoBloqueado.VIAGEM_CONCLUIDA;
        }
        if (viagem.getStatus() == ViagemStatus.CANCELED) {
            return MotivoServicoBloqueado.VIAGEM_CANCELADA;
        }
        return MotivoServicoBloqueado.MANIFESTO_INVALIDO;
    }

    private String blockedMessage(MotivoServicoBloqueado motivo, String reason) {
        if (motivo == MotivoServicoBloqueado.MANIFESTO_INVALIDO && reason != null && !reason.isBlank()) {
            return reason;
        }
        return switch (motivo) {
            case VIAGEM_EM_ANDAMENTO -> "A viagem já começou; revise alterações manualmente antes de alterar o manifesto.";
            case VIAGEM_CONCLUIDA -> "A viagem já foi concluída e não pode ter o manifesto alterado.";
            case VIAGEM_CANCELADA -> "A viagem foi cancelada e não recebe sincronização automática.";
            case MANIFESTO_INVALIDO -> "Não foi possível sincronizar o manifesto desta viagem.";
        };
    }
}
