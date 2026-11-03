package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminServicoPassageiroResponse {

    private final Long id;
    private final AdminPassageiroLinkedSummaryResponse passageiro;
    private final Integer ordemPadrao;
    private final boolean ativo;
    private final LocalDate dataInicio;
    private final LocalDate dataFim;

    public AdminServicoPassageiroResponse(Long id,
                                          AdminPassageiroLinkedSummaryResponse passageiro,
                                          Integer ordemPadrao,
                                          boolean ativo,
                                          LocalDate dataInicio,
                                          LocalDate dataFim) {
        this.id = id;
        this.passageiro = passageiro;
        this.ordemPadrao = ordemPadrao;
        this.ativo = ativo;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public Long getId() {
        return id;
    }

    public AdminPassageiroLinkedSummaryResponse getPassageiro() {
        return passageiro;
    }

    public Integer getOrdemPadrao() {
        return ordemPadrao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }
}
