package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminViagemCreateRequest {

    private Long rotaBaseId;
    private Long servicoId;
    private Long motoristaId;
    private LocalDate dataOperacao;

    public Long getRotaBaseId() {
        return rotaBaseId;
    }

    public void setRotaBaseId(Long rotaBaseId) {
        this.rotaBaseId = rotaBaseId;
    }

    public Long getServicoId() {
        return servicoId;
    }

    public void setServicoId(Long servicoId) {
        this.servicoId = servicoId;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public void setMotoristaId(Long motoristaId) {
        this.motoristaId = motoristaId;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(LocalDate dataOperacao) {
        this.dataOperacao = dataOperacao;
    }
}
