package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "viagem_parada")
public class ViagemParada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "viagem_id", nullable = false)
    private Viagem viagem;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @Column(nullable = false)
    private Integer ordem;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViagemParadaStatus status = ViagemParadaStatus.PENDING;

    @Column(name = "eta_minutos")
    private Integer etaMinutos;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Viagem getViagem() {
        return viagem;
    }

    public void setViagem(Viagem viagem) {
        this.viagem = viagem;
    }

    public Passageiro getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Passageiro passageiro) {
        this.passageiro = passageiro;
    }

    public Integer getOrdem() {
        return ordem;
    }

    public void setOrdem(Integer ordem) {
        this.ordem = ordem;
    }

    public ViagemParadaStatus getStatus() {
        return status;
    }

    public void setStatus(ViagemParadaStatus status) {
        this.status = status;
    }

    public Integer getEtaMinutos() {
        return etaMinutos;
    }

    public void setEtaMinutos(Integer etaMinutos) {
        this.etaMinutos = etaMinutos;
    }
}
