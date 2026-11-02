package com.vanrides.vanrides.dto;

import java.time.LocalTime;
import java.util.List;

public class AdminRotaBaseCreateRequest {

    private String nome;
    private Long motoristaId;
    private List<Long> passageiroIds;
    private Boolean geracaoAutomaticaAtiva;
    private List<String> diasOperacao;
    private LocalTime horarioPartida;
    private String destinoFinalNome;
    private String destinoFinalEndereco;
    private Double destinoFinalLatitude;
    private Double destinoFinalLongitude;

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public void setMotoristaId(Long motoristaId) {
        this.motoristaId = motoristaId;
    }

    public List<Long> getPassageiroIds() {
        return passageiroIds;
    }

    public void setPassageiroIds(List<Long> passageiroIds) {
        this.passageiroIds = passageiroIds;
    }

    public Boolean getGeracaoAutomaticaAtiva() {
        return geracaoAutomaticaAtiva;
    }

    public void setGeracaoAutomaticaAtiva(Boolean geracaoAutomaticaAtiva) {
        this.geracaoAutomaticaAtiva = geracaoAutomaticaAtiva;
    }

    public List<String> getDiasOperacao() {
        return diasOperacao;
    }

    public void setDiasOperacao(List<String> diasOperacao) {
        this.diasOperacao = diasOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public void setHorarioPartida(LocalTime horarioPartida) {
        this.horarioPartida = horarioPartida;
    }

    public String getDestinoFinalNome() {
        return destinoFinalNome;
    }

    public void setDestinoFinalNome(String destinoFinalNome) {
        this.destinoFinalNome = destinoFinalNome;
    }

    public String getDestinoFinalEndereco() {
        return destinoFinalEndereco;
    }

    public void setDestinoFinalEndereco(String destinoFinalEndereco) {
        this.destinoFinalEndereco = destinoFinalEndereco;
    }

    public Double getDestinoFinalLatitude() {
        return destinoFinalLatitude;
    }

    public void setDestinoFinalLatitude(Double destinoFinalLatitude) {
        this.destinoFinalLatitude = destinoFinalLatitude;
    }

    public Double getDestinoFinalLongitude() {
        return destinoFinalLongitude;
    }

    public void setDestinoFinalLongitude(Double destinoFinalLongitude) {
        this.destinoFinalLongitude = destinoFinalLongitude;
    }
}
