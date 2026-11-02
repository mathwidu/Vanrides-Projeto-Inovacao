package com.vanrides.vanrides.dto;

import java.time.LocalDate;
import java.time.LocalDateTime;

public class ViagemRotaAtualizadaEvent {

    private final String tipo;
    private final Long viagemId;
    private final Long motoristaId;
    private final LocalDate dataOperacao;
    private final int rotaVersao;
    private final LocalDateTime atualizadoEm;

    public ViagemRotaAtualizadaEvent(String tipo,
                                     Long viagemId,
                                     Long motoristaId,
                                     LocalDate dataOperacao,
                                     int rotaVersao,
                                     LocalDateTime atualizadoEm) {
        this.tipo = tipo;
        this.viagemId = viagemId;
        this.motoristaId = motoristaId;
        this.dataOperacao = dataOperacao;
        this.rotaVersao = rotaVersao;
        this.atualizadoEm = atualizadoEm;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getViagemId() {
        return viagemId;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public int getRotaVersao() {
        return rotaVersao;
    }

    public LocalDateTime getAtualizadoEm() {
        return atualizadoEm;
    }
}
