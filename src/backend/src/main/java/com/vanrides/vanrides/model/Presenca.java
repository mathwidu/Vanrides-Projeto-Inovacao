package com.vanrides.vanrides.model;

import jakarta.persistence.*;

import java.time.LocalDate;

@Entity
@Table(name = "presenca")
public class Presenca {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @Column(nullable = false)
    private LocalDate dataPresenca;

    @Column(nullable = false)
    private boolean confirmado;

    // Construtor vazio (obrigatório para o JPA)
    public Presenca() {}

    // Construtor completo
    public Presenca(Passageiro passageiro, LocalDate dataPresenca, boolean confirmado) {
        this.passageiro = passageiro;
        this.dataPresenca = dataPresenca;
        this.confirmado = confirmado;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Passageiro getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Passageiro passageiro) {
        this.passageiro = passageiro;
    }

    public LocalDate getDataPresenca() {
        return dataPresenca;
    }

    public void setDataPresenca(LocalDate dataPresenca) {
        this.dataPresenca = dataPresenca;
    }

    public boolean isConfirmado() {
        return confirmado;
    }

    public void setConfirmado(boolean confirmado) {
        this.confirmado = confirmado;
    }
}
