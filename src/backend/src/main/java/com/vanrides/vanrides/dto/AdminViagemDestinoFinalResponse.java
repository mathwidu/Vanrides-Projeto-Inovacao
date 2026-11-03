package com.vanrides.vanrides.dto;

public class AdminViagemDestinoFinalResponse {

    private final String nome;
    private final String endereco;
    private final Double latitude;
    private final Double longitude;

    public AdminViagemDestinoFinalResponse(String nome,
                                           String endereco,
                                           Double latitude,
                                           Double longitude) {
        this.nome = nome;
        this.endereco = endereco;
        this.latitude = latitude;
        this.longitude = longitude;
    }

    public String getNome() {
        return nome;
    }

    public String getEndereco() {
        return endereco;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }
}
