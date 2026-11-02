package com.vanrides.vanrides.service;

import com.vanrides.vanrides.dto.AdminDashboardSummaryResponse;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.repository.MotoristaRepository;
import com.vanrides.vanrides.repository.PresencaRepository;
import com.vanrides.vanrides.repository.PresencaServicoRepository;
import com.vanrides.vanrides.repository.RotaBaseRepository;
import com.vanrides.vanrides.repository.ServicoRecorrenteRepository;
import com.vanrides.vanrides.repository.ViagemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CompanyAdminDashboardServiceTest {

    @Mock
    private MotoristaRepository motoristaRepository;

    @Mock
    private RotaBaseRepository rotaBaseRepository;

    @Mock
    private PresencaRepository presencaRepository;

    @Mock
    private PresencaServicoRepository presencaServicoRepository;

    @Mock
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Mock
    private ViagemRepository viagemRepository;

    @InjectMocks
    private CompanyAdminDashboardService companyAdminDashboardService;

    @Test
    void shouldBuildDashboardSummaryForCompanyAndDate() {
        LocalDate data = LocalDate.of(2026, 4, 19);

        when(motoristaRepository.countByCompanyId(66L)).thenReturn(3L);
        when(rotaBaseRepository.countByCompanyIdAndAtivaTrue(66L)).thenReturn(2L);
        when(presencaRepository.countByPassageiro_Company_IdAndDataPresenca(66L, data)).thenReturn(7L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrue(66L)).thenReturn(5L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndGeracaoAutomaticaAtivaTrue(66L)).thenReturn(4L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndMotoristaPadraoIsNull(66L)).thenReturn(1L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.CONFIRMED)).thenReturn(8L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.ABSENT)).thenReturn(2L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.CANCELED_BY_COMPANY)).thenReturn(1L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.IN_PROGRESS)).thenReturn(1L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.COMPLETED)).thenReturn(2L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.PLANNED)).thenReturn(1L);

        AdminDashboardSummaryResponse response = companyAdminDashboardService.getSummary(66L, data);

        assertThat(response.getMotoristasAtivos()).isEqualTo(3L);
        assertThat(response.getRotasAtivas()).isEqualTo(2L);
        assertThat(response.getPassageirosComPresencaHoje()).isEqualTo(7L);
        assertThat(response.getServicosRecorrentes().ativos()).isEqualTo(5L);
        assertThat(response.getServicosRecorrentes().automaticos()).isEqualTo(4L);
        assertThat(response.getServicosRecorrentes().semMotorista()).isEqualTo(1L);
        assertThat(response.getPresencasServicoHoje().confirmadas()).isEqualTo(8L);
        assertThat(response.getPresencasServicoHoje().ausentes()).isEqualTo(2L);
        assertThat(response.getPresencasServicoHoje().canceladas()).isEqualTo(1L);
        assertThat(response.getPresencasServicoHoje().totalDecisoes()).isEqualTo(11L);
        assertThat(response.getViagensHoje().total()).isEqualTo(4L);
        assertThat(response.getViagensHoje().emAndamento()).isEqualTo(1L);
        assertThat(response.getViagensHoje().concluidas()).isEqualTo(2L);
        assertThat(response.getViagensHoje().planejadas()).isEqualTo(1L);
    }

    @Test
    void shouldReturnZerosWhenCompanyHasNoMetrics() {
        LocalDate data = LocalDate.of(2026, 4, 19);

        when(motoristaRepository.countByCompanyId(66L)).thenReturn(0L);
        when(rotaBaseRepository.countByCompanyIdAndAtivaTrue(66L)).thenReturn(0L);
        when(presencaRepository.countByPassageiro_Company_IdAndDataPresenca(66L, data)).thenReturn(0L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrue(66L)).thenReturn(0L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndGeracaoAutomaticaAtivaTrue(66L)).thenReturn(0L);
        when(servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndMotoristaPadraoIsNull(66L)).thenReturn(0L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.CONFIRMED)).thenReturn(0L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.ABSENT)).thenReturn(0L);
        when(presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, PresencaServicoStatus.CANCELED_BY_COMPANY)).thenReturn(0L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.IN_PROGRESS)).thenReturn(0L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.COMPLETED)).thenReturn(0L);
        when(viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(66L, data, ViagemStatus.PLANNED)).thenReturn(0L);

        AdminDashboardSummaryResponse response = companyAdminDashboardService.getSummary(66L, data);

        assertThat(response.getMotoristasAtivos()).isZero();
        assertThat(response.getRotasAtivas()).isZero();
        assertThat(response.getServicosRecorrentes()).isEqualTo(new AdminDashboardSummaryResponse.ServicosRecorrentesSummary(0, 0, 0));
        assertThat(response.getPresencasServicoHoje()).isEqualTo(new AdminDashboardSummaryResponse.PresencasServicoSummary(0, 0, 0));
        assertThat(response.getPassageirosComPresencaHoje()).isZero();
        assertThat(response.getViagensHoje()).isEqualTo(new AdminDashboardSummaryResponse.ViagensSummary(0, 0, 0, 0));
    }
}
