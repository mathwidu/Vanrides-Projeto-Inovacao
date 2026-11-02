package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.PresencaServicoStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalTime;

public class PassageiroServicoDisponivelResponse {

    private final Long servicoId;
    private final String nome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalDate dataOperacao;
    private final LocalTime horarioPartida;
    private final String destinoFinalNome;
    private final String destinoFinalEndereco;
    private final PoliticaPresencaServico politicaPresenca;
    private final AdminMotoristaSummaryResponse motoristaPadrao;
    private final Long presencaId;
    private final PresencaServicoStatus presencaStatus;

    public PassageiroServicoDisponivelResponse(Long servicoId,
                                               String nome,
                                               TipoOperacaoServico tipoOperacao,
                                               LocalDate dataOperacao,
                                               LocalTime horarioPartida,
                                               String destinoFinalNome,
                                               String destinoFinalEndereco,
                                               PoliticaPresencaServico politicaPresenca,
                                               AdminMotoristaSummaryResponse motoristaPadrao,
                                               Long presencaId,
                                               PresencaServicoStatus presencaStatus) {
        this.servicoId = servicoId;
        this.nome = nome;
        this.tipoOperacao = tipoOperacao;
        this.dataOperacao = dataOperacao;
        this.horarioPartida = horarioPartida;
        this.destinoFinalNome = destinoFinalNome;
        this.destinoFinalEndereco = destinoFinalEndereco;
        this.politicaPresenca = politicaPresenca;
        this.motoristaPadrao = motoristaPadrao;
        this.presencaId = presencaId;
        this.presencaStatus = presencaStatus;
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

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public String getDestinoFinalNome() {
        return destinoFinalNome;
    }

    public String getDestinoFinalEndereco() {
        return destinoFinalEndereco;
    }

    public PoliticaPresencaServico getPoliticaPresenca() {
        return politicaPresenca;
    }

    public AdminMotoristaSummaryResponse getMotoristaPadrao() {
        return motoristaPadrao;
    }

    public Long getPresencaId() {
        return presencaId;
    }

    public PresencaServicoStatus getPresencaStatus() {
        return presencaStatus;
    }
}
