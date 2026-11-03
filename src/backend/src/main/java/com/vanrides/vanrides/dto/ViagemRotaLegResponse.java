package com.vanrides.vanrides.dto;

public class ViagemRotaLegResponse {

    private final Integer indice;
    private final String origemLabel;
    private final String destinoLabel;
    private final Integer distanciaMetros;
    private final Integer duracaoSegundos;

    public ViagemRotaLegResponse(Integer indice,
                                 String origemLabel,
                                 String destinoLabel,
                                 Integer distanciaMetros,
                                 Integer duracaoSegundos) {
        this.indice = indice;
        this.origemLabel = origemLabel;
        this.destinoLabel = destinoLabel;
        this.distanciaMetros = distanciaMetros;
        this.duracaoSegundos = duracaoSegundos;
    }

    public Integer getIndice() {
        return indice;
    }

    public String getOrigemLabel() {
        return origemLabel;
    }

    public String getDestinoLabel() {
        return destinoLabel;
    }

    public Integer getDistanciaMetros() {
        return distanciaMetros;
    }

    public Integer getDuracaoSegundos() {
        return duracaoSegundos;
    }
}
