package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminResponsavelSummaryResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final long passageiroCount;
    private final boolean acessoConfigurado;
    private final boolean emailVerificado;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminResponsavelSummaryResponse(Long id,
                                           String nome,
                                           String email,
                                           String telefone,
                                           long passageiroCount,
                                           boolean acessoConfigurado) {
        this(id, nome, email, telefone, passageiroCount, acessoConfigurado, false);
    }

    public AdminResponsavelSummaryResponse(Long id,
                                           String nome,
                                           String email,
                                           String telefone,
                                           long passageiroCount,
                                           boolean acessoConfigurado,
                                           boolean emailVerificado) {
        this(id, nome, email, telefone, passageiroCount, acessoConfigurado, emailVerificado, null, null);
    }

    public AdminResponsavelSummaryResponse(Long id,
                                           String nome,
                                           String email,
                                           String telefone,
                                           long passageiroCount,
                                           boolean acessoConfigurado,
                                           boolean emailVerificado,
                                           LocalDate dataNascimento,
                                           String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.passageiroCount = passageiroCount;
        this.acessoConfigurado = acessoConfigurado;
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

    public long getPassageiroCount() {
        return passageiroCount;
    }

    public boolean isAcessoConfigurado() {
        return acessoConfigurado;
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
