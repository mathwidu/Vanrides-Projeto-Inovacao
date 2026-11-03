package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.time.LocalDate;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountMeResponse {

    private final Long id;
    private final String tipoUsuario;
    private final String papel;
    private final String nome;
    private final String email;
    private final String telefone;
    private final LocalDate dataNascimento;
    private final String fotoPerfilUrl;
    private final Boolean menorDeIdade;
    private final boolean emailVerified;
    private final EmpresaResponse empresa;
    private final Long passageiroId;
    private final Long motoristaId;
    private final Long responsavelId;
    private final Long companyAdminId;

    public AccountMeResponse(Long id,
                             String tipoUsuario,
                             String papel,
                             String nome,
                             String email,
                             String telefone,
                             boolean emailVerified,
                             EmpresaResponse empresa,
                             Long passageiroId,
                             Long motoristaId,
                             Long responsavelId,
                             Long companyAdminId) {
        this(id, tipoUsuario, papel, nome, email, telefone, null, null, null,
                emailVerified, empresa, passageiroId, motoristaId, responsavelId, companyAdminId);
    }

    public AccountMeResponse(Long id,
                             String tipoUsuario,
                             String papel,
                             String nome,
                             String email,
                             String telefone,
                             LocalDate dataNascimento,
                             String fotoPerfilUrl,
                             boolean emailVerified,
                             EmpresaResponse empresa,
                             Long passageiroId,
                             Long motoristaId,
                             Long responsavelId,
                             Long companyAdminId) {
        this(id, tipoUsuario, papel, nome, email, telefone, dataNascimento, fotoPerfilUrl, null,
                emailVerified, empresa, passageiroId, motoristaId, responsavelId, companyAdminId);
    }

    public AccountMeResponse(Long id,
                             String tipoUsuario,
                             String papel,
                             String nome,
                             String email,
                             String telefone,
                             boolean emailVerified,
                             EmpresaResponse empresa,
                             Long passageiroId,
                             Long motoristaId,
                             Long responsavelId,
                             Long companyAdminId,
                             LocalDate dataNascimento,
                             String fotoPerfilUrl,
                             Boolean menorDeIdade) {
        this(id, tipoUsuario, papel, nome, email, telefone, dataNascimento, fotoPerfilUrl, menorDeIdade,
                emailVerified, empresa, passageiroId, motoristaId, responsavelId, companyAdminId);
    }

    public AccountMeResponse(Long id,
                             String tipoUsuario,
                             String papel,
                             String nome,
                             String email,
                             String telefone,
                             LocalDate dataNascimento,
                             String fotoPerfilUrl,
                             Boolean menorDeIdade,
                             boolean emailVerified,
                             EmpresaResponse empresa,
                             Long passageiroId,
                             Long motoristaId,
                             Long responsavelId,
                             Long companyAdminId) {
        this.id = id;
        this.tipoUsuario = tipoUsuario;
        this.papel = papel;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.dataNascimento = dataNascimento;
        this.fotoPerfilUrl = fotoPerfilUrl;
        this.menorDeIdade = menorDeIdade;
        this.emailVerified = emailVerified;
        this.empresa = empresa;
        this.passageiroId = passageiroId;
        this.motoristaId = motoristaId;
        this.responsavelId = responsavelId;
        this.companyAdminId = companyAdminId;
    }

    public Long getId() {
        return id;
    }

    public String getTipoUsuario() {
        return tipoUsuario;
    }

    public String getPapel() {
        return papel;
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

    public Boolean getMenorDeIdade() {
        return menorDeIdade;
    }

    public boolean isEmailVerified() {
        return emailVerified;
    }

    public EmpresaResponse getEmpresa() {
        return empresa;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public Long getResponsavelId() {
        return responsavelId;
    }

    public Long getCompanyAdminId() {
        return companyAdminId;
    }

    public static class EmpresaResponse {
        private final Long id;
        private final String nome;
        private final String slug;

        public EmpresaResponse(Long id, String nome, String slug) {
            this.id = id;
            this.nome = nome;
            this.slug = slug;
        }

        public Long getId() {
            return id;
        }

        public String getNome() {
            return nome;
        }

        public String getSlug() {
            return slug;
        }
    }
}
