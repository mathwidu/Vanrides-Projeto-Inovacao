package com.vanrides.vanrides.dto;

import java.time.LocalDate;
import java.util.List;

public class AdminPassageiroDetailResponse {

    private final Long id;
    private final String nome;
    private final String email;
    private final String telefone;
    private final String endereco;
    private final String bairro;
    private final String cidade;
    private final String estado;
    private final String cep;
    private final Double latitude;
    private final Double longitude;
    private final boolean menorDeIdade;
    private final AdminMotoristaSummaryResponse motorista;
    private final List<AdminGuardianSummaryResponse> responsaveis;
    private final boolean emailVerificado;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;

    public AdminPassageiroDetailResponse(Long id,
                                         String nome,
                                         String email,
                                         String telefone,
                                         String endereco,
                                         String bairro,
                                         String cidade,
                                         String estado,
                                         String cep,
                                         Double latitude,
                                         Double longitude,
                                         boolean menorDeIdade,
                                         AdminMotoristaSummaryResponse motorista,
                                         List<AdminGuardianSummaryResponse> responsaveis) {
        this(
                id,
                nome,
                email,
                telefone,
                endereco,
                bairro,
                cidade,
                estado,
                cep,
                latitude,
                longitude,
                menorDeIdade,
                motorista,
                responsaveis,
                false
        );
    }

    public AdminPassageiroDetailResponse(Long id,
                                         String nome,
                                         String email,
                                         String telefone,
                                         String endereco,
                                         String bairro,
                                         String cidade,
                                         String estado,
                                         String cep,
                                         Double latitude,
                                         Double longitude,
                                         boolean menorDeIdade,
                                         AdminMotoristaSummaryResponse motorista,
                                         List<AdminGuardianSummaryResponse> responsaveis,
                                         boolean emailVerificado) {
        this(
                id,
                nome,
                email,
                telefone,
                endereco,
                bairro,
                cidade,
                estado,
                cep,
                latitude,
                longitude,
                menorDeIdade,
                motorista,
                responsaveis,
                emailVerificado,
                null,
                null
        );
    }

    public AdminPassageiroDetailResponse(Long id,
                                         String nome,
                                         String email,
                                         String telefone,
                                         String endereco,
                                         String bairro,
                                         String cidade,
                                         String estado,
                                         String cep,
                                         Double latitude,
                                         Double longitude,
                                         boolean menorDeIdade,
                                         AdminMotoristaSummaryResponse motorista,
                                         List<AdminGuardianSummaryResponse> responsaveis,
                                         boolean emailVerificado,
                                         LocalDate dataNascimento,
                                         String fotoPerfilUrl) {
        this.id = id;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.endereco = endereco;
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.cep = cep;
        this.latitude = latitude;
        this.longitude = longitude;
        this.menorDeIdade = menorDeIdade;
        this.motorista = motorista;
        this.responsaveis = responsaveis;
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

    public String getEndereco() {
        return endereco;
    }

    public String getBairro() {
        return bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public String getEstado() {
        return estado;
    }

    public String getCep() {
        return cep;
    }

    public Double getLatitude() {
        return latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public List<AdminGuardianSummaryResponse> getResponsaveis() {
        return responsaveis;
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
