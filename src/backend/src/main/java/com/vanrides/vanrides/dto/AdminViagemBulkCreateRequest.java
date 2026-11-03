package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminViagemBulkCreateRequest {

    private LocalDate dataOperacao;

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(LocalDate dataOperacao) {
        this.dataOperacao = dataOperacao;
    }
}
