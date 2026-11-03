package com.vanrides.vanrides.dto;

import java.time.LocalDate;

public class AdminGuardianSummaryResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminGuardianSummaryResponse(Long id, String nome, String email, String telefone) {
        this(id, nome, email, telefone, null, null);
    }

    public AdminGuardianSummaryResponse(Long id,
                                        String nome,
                                        String email,
                                        String telefone,
                                        LocalDate dataNascimento,
                                        String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
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

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public String getFotoPerfilUrl() {
        return fotoPerfilUrl;
    }
}
