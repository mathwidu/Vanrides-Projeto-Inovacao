package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

@Entity
@Table(name = "rota_base_parada")
public class RotaBaseParada {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_base_id", nullable = false)
    private RotaBase rotaBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @Column(nullable = false)
    private Integer ordem;

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public RotaBase getRotaBase() {
        return rotaBase;
    }

    public void setRotaBase(RotaBase rotaBase) {
        this.rotaBase = rotaBase;
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
}
