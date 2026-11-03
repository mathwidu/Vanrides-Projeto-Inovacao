package com.vanrides.vanrides.dto;

import java.time.LocalTime;
import java.util.List;

public class AdminRotaBaseSummaryResponse {

    private final Long id;
    private final String nome;
    private final AdminMotoristaSummaryResponse motorista;
    private final int quantidadeParadas;
    private final List<Long> passageiroIds;
    private final boolean ativo;
    private final boolean geracaoAutomaticaAtiva;
    private final List<String> diasOperacao;
    private final LocalTime horarioPartida;
    private final String destinoFinalNome;
    private final String destinoFinalEndereco;
    private final Double destinoFinalLatitude;
    private final Double destinoFinalLongitude;

    public AdminRotaBaseSummaryResponse(Long id,
                                        String nome,
                                        AdminMotoristaSummaryResponse motorista,
                                        List<Long> passageiroIds,
                                        boolean ativo,
                                        boolean geracaoAutomaticaAtiva,
                                        List<String> diasOperacao,
                                        LocalTime horarioPartida,
                                        String destinoFinalNome,
                                        String destinoFinalEndereco,
                                        Double destinoFinalLatitude,
                                        Double destinoFinalLongitude) {
        this.id = id;
        this.nome = nome;
        this.motorista = motorista;
        this.quantidadeParadas = passageiroIds.size();
        this.passageiroIds = passageiroIds;
        this.ativo = ativo;
        this.geracaoAutomaticaAtiva = geracaoAutomaticaAtiva;
        this.diasOperacao = diasOperacao;
        this.horarioPartida = horarioPartida;
        this.destinoFinalNome = destinoFinalNome;
        this.destinoFinalEndereco = destinoFinalEndereco;
        this.destinoFinalLatitude = destinoFinalLatitude;
        this.destinoFinalLongitude = destinoFinalLongitude;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public int getQuantidadeParadas() {
        return quantidadeParadas;
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
}
