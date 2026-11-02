package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.PresencaServicoOrigem;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalTime;

public class PresencaServicoResponse {

    private final Long id;
    private final Long servicoId;
    private final String servicoNome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalTime horarioPartida;
    private final Long passageiroId;
    private final String passageiroNome;
    private final LocalDate dataOperacao;
    private final PresencaServicoStatus status;
    private final PresencaServicoOrigem origem;

    public PresencaServicoResponse(Long id,
                                   Long servicoId,
                                   String servicoNome,
                                   TipoOperacaoServico tipoOperacao,
                                   LocalTime horarioPartida,
                                   Long passageiroId,
                                   String passageiroNome,
                                   LocalDate dataOperacao,
                                   PresencaServicoStatus status,
                                   PresencaServicoOrigem origem) {
        this.id = id;
        this.servicoId = servicoId;
        this.servicoNome = servicoNome;
        this.tipoOperacao = tipoOperacao;
        this.horarioPartida = horarioPartida;
        this.passageiroId = passageiroId;
        this.passageiroNome = passageiroNome;
        this.dataOperacao = dataOperacao;
        this.status = status;
        this.origem = origem;
    }

    public Long getId() {
        return id;
    }

    public Long getServicoId() {
        return servicoId;
    }

    public String getServicoNome() {
        return servicoNome;
    }

    public TipoOperacaoServico getTipoOperacao() {
        return tipoOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public String getPassageiroNome() {
        return passageiroNome;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public PresencaServicoStatus getStatus() {
        return status;
    }

    public PresencaServicoOrigem getOrigem() {
        return origem;
    }
}
