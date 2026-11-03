package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminPassageiroLinkedSummaryResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final boolean menorDeIdade;
    private final AdminMotoristaSummaryResponse motorista;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminPassageiroLinkedSummaryResponse(Long id,
                                                String nome,
                                                String email,
                                                boolean menorDeIdade,
                                                AdminMotoristaSummaryResponse motorista) {
        this(id, nome, email, menorDeIdade, motorista, null, null);
    }

    public AdminPassageiroLinkedSummaryResponse(Long id,
                                                String nome,
                                                String email,
                                                boolean menorDeIdade,
                                                AdminMotoristaSummaryResponse motorista,
                                                LocalDate dataNascimento,
                                                String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.menorDeIdade = menorDeIdade;
        this.motorista = motorista;
        this.dataNascimento = dataNascimento;
        this.fotoPerfilUrl = fotoPerfilUrl;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public String getEmail() {
        return email;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public String getFotoPerfilUrl() {
        return fotoPerfilUrl;
    }
}
