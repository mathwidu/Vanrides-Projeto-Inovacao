package com.vanrides.vanrides.dto;

import java.time.LocalDate;
import java.util.List;

public class AdminResponsavelDetailResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final boolean acessoConfigurado;
    private final List<AdminPassageiroLinkedSummaryResponse> passageiros;
    private final boolean emailVerificado;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminResponsavelDetailResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean acessoConfigurado,
                                          List<AdminPassageiroLinkedSummaryResponse> passageiros) {
        this(id, nome, email, telefone, acessoConfigurado, passageiros, false);
    }

    public AdminResponsavelDetailResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean acessoConfigurado,
                                          List<AdminPassageiroLinkedSummaryResponse> passageiros,
                                          boolean emailVerificado) {
        this(id, nome, email, telefone, acessoConfigurado, passageiros, emailVerificado, null, null);
    }

    public AdminResponsavelDetailResponse(Long id,
                                          String nome,
                                          String email,
                                          String telefone,
                                          boolean acessoConfigurado,
                                          List<AdminPassageiroLinkedSummaryResponse> passageiros,
                                          boolean emailVerificado,
                                          LocalDate dataNascimento,
                                          String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.acessoConfigurado = acessoConfigurado;
        this.passageiros = passageiros;
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

    public boolean isAcessoConfigurado() {
        return acessoConfigurado;
    }

    public List<AdminPassageiroLinkedSummaryResponse> getPassageiros() {
        return passageiros;
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
