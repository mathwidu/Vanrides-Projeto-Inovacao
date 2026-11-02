package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminPassageiroSummaryResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final boolean menorDeIdade;
    private final AdminMotoristaSummaryResponse motorista;
    private final long responsavelCount;
    private final boolean emailVerificado;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminPassageiroSummaryResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean menorDeIdade,
                                          AdminMotoristaSummaryResponse motorista,
                                          long responsavelCount) {
        this(id, nome, email, telefone, menorDeIdade, motorista, responsavelCount, false);
    }

    public AdminPassageiroSummaryResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean menorDeIdade,
                                          AdminMotoristaSummaryResponse motorista,
                                          long responsavelCount,
                                          boolean emailVerificado) {
        this(id, nome, email, telefone, menorDeIdade, motorista, responsavelCount, emailVerificado, null, null);
    }

    public AdminPassageiroSummaryResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean menorDeIdade,
                                          AdminMotoristaSummaryResponse motorista,
                                          long responsavelCount,
                                          boolean emailVerificado,
                                          LocalDate dataNascimento,
                                          String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.menorDeIdade = menorDeIdade;
        this.motorista = motorista;
        this.responsavelCount = responsavelCount;
        this.emailVerificado = emailVerificado;
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

    public String getTelefone() {
        return telefone;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public long getResponsavelCount() {
        return responsavelCount;
    }

    public boolean isEmailVerificado() {
        return emailVerificado;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public String getFotoPerfilUrl() {
        return fotoPerfilUrl;
    }
}
