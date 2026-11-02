package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class PresencaServicoRequest {

    private Long servicoId;
    private Long passageiroId;
    private LocalDate dataOperacao;
    private String status;

    public Long getServicoId() {
        return servicoId;
    }

    public void setServicoId(Long servicoId) {
        this.servicoId = servicoId;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public void setPassageiroId(Long passageiroId) {
        this.passageiroId = passageiroId;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(LocalDate dataOperacao) {
        this.dataOperacao = dataOperacao;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }
}
