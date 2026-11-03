package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminViagemManifestStatus;
import com.vanrides.vanrides.model.Company;
import com.vanrides.vanrides.model.RotaBase;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminViagemManifestStatusResolverTest {

    @Mock
    private ViagemManifestSyncService viagemManifestSyncService;

    @InjectMocks
    private AdminViagemManifestStatusResolver resolver;

    @Test
    void shouldResolvePendingManifestStatus() {
        Viagem viagem = recurringTrip();

        when(viagemManifestSyncService.previewSincronizacaoManifesto(66L, 30L))
                .thenReturn(new ViagemManifestSyncService.ManifestSyncPreview(
                        30L,
                        15L,
                        "Linha Manha",
                        LocalDate.of(2026, 3, 12),
                        1,
                        2,
                        1,
                        0,
                        1,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        var status = resolver.resolve(viagem, 1);

        assertThat(status.getStatus()).isEqualTo(AdminViagemManifestStatus.PENDING);
        assertThat(status.getMudancas()).isEqualTo(1);
        assertThat(status.getAdicionados()).isEqualTo(1);
        assertThat(status.getRemovidos()).isZero();
        assertThat(status.getMensagem()).contains("Sincronize");
    }

    @Test
    void shouldResolveUpToDateManifestStatus() {
        Viagem viagem = recurringTrip();

        when(viagemManifestSyncService.previewSincronizacaoManifesto(66L, 30L))
                .thenReturn(new ViagemManifestSyncService.ManifestSyncPreview(
                        30L,
                        15L,
                        "Linha Manha",
                        LocalDate.of(2026, 3, 12),
                        2,
                        2,
                        0,
                        0,
                        0,
                        List.of(),
                        List.of(),
                        List.of()
                ));

        var status = resolver.resolve(viagem, 2);

        assertThat(status.getStatus()).isEqualTo(AdminViagemManifestStatus.UP_TO_DATE);
        assertThat(status.getMudancas()).isZero();
        assertThat(status.getMensagem()).contains("atualizado");
    }

    @Test
    void shouldBlockManifestStatusWhenTripAlreadyStarted() {
        Viagem viagem = recurringTrip();
        viagem.setStatus(ViagemStatus.IN_PROGRESS);

        var status = resolver.resolve(viagem, 2);

        assertThat(status.getStatus()).isEqualTo(AdminViagemManifestStatus.BLOCKED);
        assertThat(status.getMensagem()).contains("fase planejada");
    }

    private Viagem recurringTrip() {
        Company company = new Company("VanRides", "dev");
        company.setId(66L);

        RotaBase rotaBase = new RotaBase();
        rotaBase.setId(10L);
        rotaBase.setCompany(company);

        ServicoRecorrente servico = new ServicoRecorrente();
        servico.setId(15L);
        servico.setCompany(company);
        servico.setRotaBaseLegada(rotaBase);

        Viagem viagem = new Viagem();
        viagem.setId(30L);
        viagem.setCompany(company);
        viagem.setRotaBase(rotaBase);
        viagem.setServicoRecorrente(servico);
        viagem.setStatus(ViagemStatus.PLANNED);
        viagem.setDataOperacao(LocalDate.of(2026, 3, 12));
        return viagem;
    }
}
