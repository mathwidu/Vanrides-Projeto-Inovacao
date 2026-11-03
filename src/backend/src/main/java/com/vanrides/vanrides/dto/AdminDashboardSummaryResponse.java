package com.vanrides.vanrides.dto;

public class AdminDashboardSummaryResponse {

    private final long motoristasAtivos;
    private final long rotasAtivas;
    private final long passageirosComPresencaHoje;
    private final ServicosRecorrentesSummary servicosRecorrentes;
    private final PresencasServicoSummary presencasServicoHoje;
    private final ViagensSummary viagensHoje;

    public AdminDashboardSummaryResponse(long motoristasAtivos,
                                         long rotasAtivas,
                                         long passageirosComPresencaHoje,
                                         ViagensSummary viagensHoje) {
        this(
                motoristasAtivos,
                rotasAtivas,
                passageirosComPresencaHoje,
                new ServicosRecorrentesSummary(0, 0, 0),
                new PresencasServicoSummary(0, 0, 0),
                viagensHoje
        );
    }

    public AdminDashboardSummaryResponse(long motoristasAtivos,
                                         long rotasAtivas,
                                         long passageirosComPresencaHoje,
                                         ServicosRecorrentesSummary servicosRecorrentes,
                                         PresencasServicoSummary presencasServicoHoje,
                                         ViagensSummary viagensHoje) {
        this.motoristasAtivos = motoristasAtivos;
        this.rotasAtivas = rotasAtivas;
        this.passageirosComPresencaHoje = passageirosComPresencaHoje;
        this.servicosRecorrentes = servicosRecorrentes;
        this.presencasServicoHoje = presencasServicoHoje;
        this.viagensHoje = viagensHoje;
    }

    public long getMotoristasAtivos() {
        return motoristasAtivos;
    }

    public long getRotasAtivas() {
        return rotasAtivas;
    }

    public long getPassageirosComPresencaHoje() {
        return passageirosComPresencaHoje;
    }

    public ServicosRecorrentesSummary getServicosRecorrentes() {
        return servicosRecorrentes;
    }

    public PresencasServicoSummary getPresencasServicoHoje() {
        return presencasServicoHoje;
    }

    public ViagensSummary getViagensHoje() {
        return viagensHoje;
    }

    public record ServicosRecorrentesSummary(long ativos, long automaticos, long semMotorista) {
    }

    public record PresencasServicoSummary(long confirmadas, long ausentes, long canceladas, long totalDecisoes) {

        public PresencasServicoSummary(long confirmadas, long ausentes, long canceladas) {
            this(confirmadas, ausentes, canceladas, confirmadas + ausentes + canceladas);
        }
    }

    public record ViagensSummary(long total, long emAndamento, long concluidas, long planejadas) {
    }
}
