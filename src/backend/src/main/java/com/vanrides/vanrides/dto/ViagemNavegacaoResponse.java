package com.vanrides.vanrides.dto;

public class ViagemNavegacaoResponse {

    private final String provider;
    private final String url;
    private final String origem;
    private final String destino;
    private final Integer quantidadeWaypoints;

    public ViagemNavegacaoResponse(String provider,
                                   String url,
                                   String origem,
                                   String destino,
                                   Integer quantidadeWaypoints) {
        this.provider = provider;
        this.url = url;
        this.origem = origem;
        this.destino = destino;
        this.quantidadeWaypoints = quantidadeWaypoints;
    }

    public String getProvider() {
        return provider;
    }

    public String getUrl() {
        return url;
    }

    public String getOrigem() {
        return origem;
    }

    public String getDestino() {
        return destino;
    }

    public Integer getQuantidadeWaypoints() {
        return quantidadeWaypoints;
    }
}
