package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.Motorista;
import com.vanrides.vanrides.model.ServicoRecorrente;
import com.vanrides.vanrides.model.Viagem;
import com.vanrides.vanrides.model.ViagemStatus;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyTripManifestMaintenanceServiceTest {

    @Mock
    private ViagemManifestSyncService viagemManifestSyncService;

    @InjectMocks
    private CompanyTripManifestMaintenanceService maintenanceService;

    @Test
    void shouldKeepExistingPlannedTripWhenManifestHasNoChanges() {
        ServicoRecorrente servico = servico(15L, "Linha Manhã");
        Viagem viagem = viagem(39L, servico, ViagemStatus.PLANNED);
        when(viagemManifestSyncService.previewSincronizacaoManifesto(66L, 39L))
                .thenReturn(manifestPreview(39L, 15L, "Linha Manhã", 2, 2, 0, 0, 2));

        var result = maintenanceService.maintain(66L, servico, viagem);

        assertThat(result.result()).isEqualTo(CompanyTripManifestMaintenanceService.MaintenanceResult.EXISTENTE);
        assertThat(result.viagem()).isSameAs(viagem);
        assertThat(result.bloqueio()).isNull();
        verify(viagemManifestSyncService, never()).sincronizarManifesto(66L, 39L);
    }

    @Test
    void shouldSyncExistingPlannedTripWhenManifestChanged() {
        ServicoRecorrente servico = servico(15L, "Linha Manhã");
        Viagem viagem = viagem(39L, servico, ViagemStatus.PLANNED);
        Viagem atualizada = viagem(39L, servico, ViagemStatus.PLANNED);
        when(viagemManifestSyncService.previewSincronizacaoManifesto(66L, 39L))
                .thenReturn(manifestPreview(39L, 15L, "Linha Manhã", 1, 2, 1, 0, 1));
        when(viagemManifestSyncService.sincronizarManifesto(66L, 39L)).thenReturn(atualizada);

        var result = maintenanceService.maintain(66L, servico, viagem);

        assertThat(result.result()).isEqualTo(CompanyTripManifestMaintenanceService.MaintenanceResult.ATUALIZADA);
        assertThat(result.viagem()).isSameAs(atualizada);
        assertThat(result.bloqueio()).isNull();
        verify(viagemManifestSyncService).sincronizarManifesto(66L, 39L);
    }

    @Test
    void shouldBlockExistingTripWhenItAlreadyStarted() {
        ServicoRecorrente servico = servico(15L, "Linha em andamento");
        Viagem viagem = viagem(39L, servico, ViagemStatus.IN_PROGRESS);

        var result = maintenanceService.maintain(66L, servico, viagem);

        assertThat(result.result()).isEqualTo(CompanyTripManifestMaintenanceService.MaintenanceResult.BLOQUEADA);
        assertThat(result.viagem()).isSameAs(viagem);
        assertThat(result.bloqueio().servicoId()).isEqualTo(15L);
        assertThat(result.bloqueio().servicoNome()).isEqualTo("Linha em andamento");
        assertThat(result.bloqueio().viagemId()).isEqualTo(39L);
        assertThat(result.bloqueio().motivo())
                .isEqualTo(CompanyTripManifestMaintenanceService.MotivoServicoBloqueado.VIAGEM_EM_ANDAMENTO);
        assertThat(result.bloqueio().mensagem()).contains("já começou");
        verify(viagemManifestSyncService, never()).previewSincronizacaoManifesto(66L, 39L);
        verify(viagemManifestSyncService, never()).sincronizarManifesto(66L, 39L);
    }

    private ViagemManifestSyncService.ManifestSyncPreview manifestPreview(Long viagemId,
                                                                          Long servicoId,
                                                                          String servicoNome,
                                                                          int paradasAtuais,
                                                                          int paradasAposSincronizacao,
                                                                          int adicionados,
                                                                          int removidos,
                                                                          int mantidos) {
        return new ViagemManifestSyncService.ManifestSyncPreview(
                viagemId,
                servicoId,
                servicoNome,
                LocalDate.of(2026, 3, 19),
                paradasAtuais,
                paradasAposSincronizacao,
                adicionados,
                removidos,
                mantidos,
                List.of(),
                List.of(),
                List.of()
        );
    }

    private Viagem viagem(Long id, ServicoRecorrente servico, ViagemStatus status) {
        Viagem viagem = new Viagem();
        viagem.setId(id);
        viagem.setCompany(company(66L));
        viagem.setServicoRecorrente(servico);
        viagem.setMotorista(motorista(7L));
        viagem.setDataOperacao(LocalDate.of(2026, 3, 19));
        viagem.setStatus(status);
        return viagem;
    }

    private ServicoRecorrente servico(Long id, String nome) {
        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(id);
        servico.setCompany(company(66L));
        servico.setNome(nome);
        servico.setMotoristaPadrao(motorista(7L));
        return servico;
    }

    private Company company(Long id) {
        Company company = new Company();
        company.setId(id);
        company.setNome("VanRides");
        return company;
    }

    private Motorista motorista(Long id) {
        Motorista motorista = new Motorista();
        motorista.setId(id);
        motorista.setNome("Motorista " + id);
        return motorista;
    }
}
