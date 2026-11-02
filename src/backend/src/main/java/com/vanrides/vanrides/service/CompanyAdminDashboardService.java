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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalDate;

@Service
public class CompanyAdminDashboardService {

    @Autowired
    private MotoristaRepository motoristaRepository;

    @Autowired
    private RotaBaseRepository rotaBaseRepository;

    @Autowired
    private PresencaRepository presencaRepository;

    @Autowired
    private PresencaServicoRepository presencaServicoRepository;

    @Autowired
    private ServicoRecorrenteRepository servicoRecorrenteRepository;

    @Autowired
    private ViagemRepository viagemRepository;

    public AdminDashboardSummaryResponse getSummary(Long companyId, LocalDate data) {
        long motoristasAtivos = motoristaRepository.countByCompanyId(companyId);
        long rotasAtivas = rotaBaseRepository.countByCompanyIdAndAtivaTrue(companyId);
        long passageirosComPresencaHoje = presencaRepository.countByPassageiro_Company_IdAndDataPresenca(companyId, data);
        long servicosAtivos = servicoRecorrenteRepository.countByCompanyIdAndAtivoTrue(companyId);
        long servicosAutomaticos = servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndGeracaoAutomaticaAtivaTrue(companyId);
        long servicosSemMotorista = servicoRecorrenteRepository.countByCompanyIdAndAtivoTrueAndMotoristaPadraoIsNull(companyId);
        long presencasServicoConfirmadas = presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(
                companyId,
                data,
                PresencaServicoStatus.CONFIRMED
        );
        long presencasServicoAusentes = presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(
                companyId,
                data,
                PresencaServicoStatus.ABSENT
        );
        long presencasServicoCanceladas = presencaServicoRepository.countByCompanyIdAndDataOperacaoAndStatus(
                companyId,
                data,
                PresencaServicoStatus.CANCELED_BY_COMPANY
        );
        long emAndamento = viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(companyId, data, ViagemStatus.IN_PROGRESS);
        long concluidas = viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(companyId, data, ViagemStatus.COMPLETED);
        long planejadas = viagemRepository.countByCompanyIdAndDataOperacaoAndStatus(companyId, data, ViagemStatus.PLANNED);
        long total = emAndamento + concluidas + planejadas;

        return new AdminDashboardSummaryResponse(
                motoristasAtivos,
                rotasAtivas,
                passageirosComPresencaHoje,
                new AdminDashboardSummaryResponse.ServicosRecorrentesSummary(
                        servicosAtivos,
                        servicosAutomaticos,
                        servicosSemMotorista
                ),
                new AdminDashboardSummaryResponse.PresencasServicoSummary(
                        presencasServicoConfirmadas,
                        presencasServicoAusentes,
                        presencasServicoCanceladas
                ),
                new AdminDashboardSummaryResponse.ViagensSummary(total, emAndamento, concluidas, planejadas)
        );
    }
}
