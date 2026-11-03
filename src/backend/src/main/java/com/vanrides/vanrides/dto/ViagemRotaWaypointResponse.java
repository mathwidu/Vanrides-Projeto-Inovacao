package com.vanrides.vanrides.dto;

public class ViagemRotaWaypointResponse {

    private final String tipo;
    private final Long paradaId;
    private final Long passageiroId;
    private final String nome;
    private final Double latitude;
    private final Double longitude;
    private final Integer ordem;
    private final boolean proximaParada;
    private final boolean paradaDoPassageiro;

    public ViagemRotaWaypointResponse(String tipo,
                                      Long paradaId,
                                      Long passageiroId,
                                      String nome,
                                      Double latitude,
                                      Double longitude,
                                      Integer ordem,
                                      boolean proximaParada,
                                      boolean paradaDoPassageiro) {
        this.tipo = tipo;
        this.paradaId = paradaId;
        this.passageiroId = passageiroId;
        this.nome = nome;
        this.latitude = latitude;
        this.longitude = longitude;
        this.ordem = ordem;
        this.proximaParada = proximaParada;
        this.paradaDoPassageiro = paradaDoPassageiro;
    }

    public String getTipo() {
        return tipo;
    }

    public Long getParadaId() {
        return paradaId;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public String getNome() {
        return nome;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public boolean isProximaParada() {
        return proximaParada;
    }

    public boolean isParadaDoPassageiro() {
        return paradaDoPassageiro;
    }
}
