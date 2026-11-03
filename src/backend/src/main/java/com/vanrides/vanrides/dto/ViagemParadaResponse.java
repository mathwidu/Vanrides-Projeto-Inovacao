package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemParadaStatus;

public class ViagemParadaResponse {

    private final Long id;
    private final int ordem;
    private final ViagemParadaStatus status;
    private final Integer etaMinutos;
    private final boolean confirmadoPresenca;
    private final boolean proximaParada;
    private final ViagemPassageiroResumoResponse passageiro;

    public ViagemParadaResponse(Long id,
                                int ordem,
                                ViagemParadaStatus status,
                                Integer etaMinutos,
                                boolean confirmadoPresenca,
                                boolean proximaParada,
                                ViagemPassageiroResumoResponse passageiro) {
        this.id = id;
        this.ordem = ordem;
        this.status = status;
        this.etaMinutos = etaMinutos;
        this.confirmadoPresenca = confirmadoPresenca;
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

    public boolean isProximaParada() {
        return proximaParada;
    }

    public ViagemPassageiroResumoResponse getPassageiro() {
        return passageiro;
    }
}
