package com.vanrides.vanrides.dto;

public class CompanyAdminResponse {

    private Long id;
    private Long companyId;
    private String companySlug;
    private String nome;
    private String email;
    private String telefone;
    private boolean emailVerificado;

    public CompanyAdminResponse() {
    }

    public CompanyAdminResponse(Long id, Long companyId, String companySlug, String nome, String email, String telefone) {
        this(id, companyId, companySlug, nome, email, telefone, false);
    }

    public CompanyAdminResponse(Long id, Long companyId, String companySlug, String nome, String email, String telefone, boolean emailVerificado) {
        this.id = id;
        this.companyId = companyId;
        this.companySlug = companySlug;
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.emailVerificado = emailVerificado;
    }

    public Long getId() {
        return id;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public String getCompanySlug() {
        return companySlug;
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
}
