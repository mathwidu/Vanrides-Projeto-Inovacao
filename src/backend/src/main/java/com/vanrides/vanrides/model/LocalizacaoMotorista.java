package com.vanrides.vanrides.model;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
public class LocalizacaoMotorista {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Double latitude;
    private Double longitude;

    private LocalDateTime dataHora;

    @ManyToOne
    @JoinColumn(name = "motorista_id")
    private Motorista motorista;

    public LocalizacaoMotorista() {}

    public LocalizacaoMotorista(Double latitude, Double longitude, LocalDateTime dataHora, Motorista motorista) {
        this.latitude = latitude;
        this.longitude = longitude;
        this.dataHora = dataHora;
        this.motorista = motorista;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public LocalDateTime getDataHora() {
        return dataHora;
    }

    public void setDataHora(LocalDateTime dataHora) {
        this.dataHora = dataHora;
    }

    public Motorista getMotorista() {
        return motorista;
    }

    public void setMotorista(Motorista motorista) {
        this.motorista = motorista;
    }
}
