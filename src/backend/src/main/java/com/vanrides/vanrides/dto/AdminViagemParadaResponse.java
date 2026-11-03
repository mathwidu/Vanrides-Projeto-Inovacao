package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.ViagemParadaStatus;

public class AdminViagemParadaResponse {

    private final Long id;
    private final int ordem;
    private final ViagemParadaStatus status;
    private final Integer etaMinutos;
    private final boolean confirmadoPresenca;
    private final Long presencaId;
    private final PresencaServicoStatus presencaStatus;
    private final boolean proximaParada;
    private final ViagemPassageiroResumoResponse passageiro;

    public AdminViagemParadaResponse(Long id,
                                     int ordem,
                                     ViagemParadaStatus status,
                                     Integer etaMinutos,
                                     boolean confirmadoPresenca,
                                     Long presencaId,
                                     PresencaServicoStatus presencaStatus,
                                     boolean proximaParada,
                                     ViagemPassageiroResumoResponse passageiro) {
        this.id = id;
        this.ordem = ordem;
        this.status = status;
        this.etaMinutos = etaMinutos;
        this.confirmadoPresenca = confirmadoPresenca;
        this.presencaId = presencaId;
        this.presencaStatus = presencaStatus;
        this.proximaParada = proximaParada;
        this.passageiro = passageiro;
    }

    public Long getId() {
        return id;
    }

    public int getOrdem() {
        return ordem;
    }

    public ViagemParadaStatus getStatus() {
        return status;
    }

    public Integer getEtaMinutos() {
        return etaMinutos;
    }

    public boolean isConfirmadoPresenca() {
        return confirmadoPresenca;
    }

    public Long getPresencaId() {
        return presencaId;
    }

    public PresencaServicoStatus getPresencaStatus() {
        return presencaStatus;
    }

    public boolean isProximaParada() {
        return proximaParada;
    }

    public ViagemPassageiroResumoResponse getPassageiro() {
        return passageiro;
    }
}
