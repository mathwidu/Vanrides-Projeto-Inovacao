package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminMotoristaSummaryResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final boolean emailVerificado;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminMotoristaSummaryResponse(Long id, String nome, String email, String telefone) {
        this(id, nome, email, telefone, false);
    }

    public AdminMotoristaSummaryResponse(Long id, String nome, String email, String telefone, boolean emailVerificado) {
        this(id, nome, email, telefone, emailVerificado, null, null);
    }

    public AdminMotoristaSummaryResponse(Long id,
                                         String nome,
                                         String email,
                                         String telefone,
                                         boolean emailVerificado,
                                         LocalDate dataNascimento,
                                         String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
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
