package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalTime;

public class AdminPassageiroServicoResponse {

    private final Long servicoId;
    private final String nome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalTime horarioPartida;
    private final AdminMotoristaSummaryResponse motoristaPadrao;
    private final boolean habilitado;
    private final Long elegibilidadeId;
    private final Integer ordemPadrao;
    private final LocalDate dataInicio;
    private final LocalDate dataFim;

    public AdminPassageiroServicoResponse(Long servicoId,
                                          String nome,
                                          TipoOperacaoServico tipoOperacao,
                                          LocalTime horarioPartida,
                                          AdminMotoristaSummaryResponse motoristaPadrao,
                                          boolean habilitado,
                                          Long elegibilidadeId,
                                          Integer ordemPadrao,
                                          LocalDate dataInicio,
                                          LocalDate dataFim) {
        this.servicoId = servicoId;
        this.nome = nome;
        this.tipoOperacao = tipoOperacao;
        this.horarioPartida = horarioPartida;
        this.motoristaPadrao = motoristaPadrao;
        this.habilitado = habilitado;
        this.elegibilidadeId = elegibilidadeId;
        this.ordemPadrao = ordemPadrao;
        this.dataInicio = dataInicio;
        this.dataFim = dataFim;
    }

    public Long getServicoId() {
        return servicoId;
    }

    public String getNome() {
        return nome;
    }

    public TipoOperacaoServico getTipoOperacao() {
        return tipoOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public AdminMotoristaSummaryResponse getMotoristaPadrao() {
        return motoristaPadrao;
    }

    public boolean isHabilitado() {
        return habilitado;
    }

    public Long getElegibilidadeId() {
        return elegibilidadeId;
    }

    public Integer getOrdemPadrao() {
        return ordemPadrao;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }
}
