package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.PoliticaPresencaServico;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalTime;
import java.util.List;

public class AdminServicoRecorrenteResponse {

    private final Long id;
    private final Long rotaBaseLegadaId;
    private final String nome;
    private final TipoOperacaoServico tipoOperacao;
    private final AdminMotoristaSummaryResponse motoristaPadrao;
    private final int quantidadePassageiros;
    private final List<Long> passageiroIds;
    private final boolean ativo;
    private final boolean geracaoAutomaticaAtiva;
    private final PoliticaPresencaServico politicaPresenca;
    private final List<String> diasOperacao;
    private final LocalTime horarioPartida;
    private final String destinoFinalNome;
    private final String destinoFinalEndereco;
    private final Double destinoFinalLatitude;
    private final Double destinoFinalLongitude;
    private final Integer capacidadePlanejada;

    public AdminServicoRecorrenteResponse(Long id,
                                          Long rotaBaseLegadaId,
                                          String nome,
                                          TipoOperacaoServico tipoOperacao,
                                          AdminMotoristaSummaryResponse motoristaPadrao,
                                          List<Long> passageiroIds,
                                          boolean ativo,
                                          boolean geracaoAutomaticaAtiva,
                                          PoliticaPresencaServico politicaPresenca,
                                          List<String> diasOperacao,
                                          LocalTime horarioPartida,
                                          String destinoFinalNome,
                                          String destinoFinalEndereco,
                                          Double destinoFinalLatitude,
                                          Double destinoFinalLongitude,
                                          Integer capacidadePlanejada) {
        this.id = id;
        this.rotaBaseLegadaId = rotaBaseLegadaId;
        this.nome = nome;
        this.tipoOperacao = tipoOperacao;
        this.motoristaPadrao = motoristaPadrao;
        this.quantidadePassageiros = passageiroIds.size();
        this.passageiroIds = passageiroIds;
        this.ativo = ativo;
        this.geracaoAutomaticaAtiva = geracaoAutomaticaAtiva;
        this.politicaPresenca = politicaPresenca;
        this.diasOperacao = diasOperacao;
        this.horarioPartida = horarioPartida;
        this.destinoFinalNome = destinoFinalNome;
        this.destinoFinalEndereco = destinoFinalEndereco;
        this.destinoFinalLatitude = destinoFinalLatitude;
        this.destinoFinalLongitude = destinoFinalLongitude;
        this.capacidadePlanejada = capacidadePlanejada;
    }

    public Long getId() {
        return id;
    }

    public Long getRotaBaseLegadaId() {
        return rotaBaseLegadaId;
    }

    public String getNome() {
        return nome;
    }

    public TipoOperacaoServico getTipoOperacao() {
        return tipoOperacao;
    }

    public AdminMotoristaSummaryResponse getMotoristaPadrao() {
        return motoristaPadrao;
    }

    public int getQuantidadePassageiros() {
        return quantidadePassageiros;
    }

    public List<Long> getPassageiroIds() {
        return passageiroIds;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public boolean isGeracaoAutomaticaAtiva() {
        return geracaoAutomaticaAtiva;
    }

    public PoliticaPresencaServico getPoliticaPresenca() {
        return politicaPresenca;
    }

    public List<String> getDiasOperacao() {
        return diasOperacao;
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

    public Double getDestinoFinalLatitude() {
        return destinoFinalLatitude;
    }

    public Double getDestinoFinalLongitude() {
        return destinoFinalLongitude;
    }

    public Integer getCapacidadePlanejada() {
        return capacidadePlanejada;
    }
}
