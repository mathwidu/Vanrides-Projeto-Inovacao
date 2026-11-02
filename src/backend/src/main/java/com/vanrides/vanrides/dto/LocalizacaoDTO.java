package com.vanrides.vanrides.dto;

public class LocalizacaoDTO {

    private Double latitude;
    private Double longitude;
    private Long motoristaId; // 🔥 Campo corrigido

    public LocalizacaoDTO() {}

    public LocalizacaoDTO(Double latitude, Double longitude, Long motoristaId) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.motoristaId = motoristaId;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Long getMotoristaId() { // 🔥 Getter corrigido
        return motoristaId;
    }

    public void setMotoristaId(Long motoristaId) { // 🔥 Setter corrigido
        this.motoristaId = motoristaId;
    }
}
