package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "guardian", indexes = {
        @Index(name = "idx_guardian_company_id", columnList = "company_id"),
        @Index(name = "idx_guardian_company_email", columnList = "company_id,email")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_guardian_company_email", columnNames = {"company_id", "email"})
})
public class Guardian implements EmailVerifiableAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private String email;

    @Column(nullable = false)
    private String telefone;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(name = "senha")
    private String senha;

    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @Column(name = "email_verification_last_sent_at")
    private OffsetDateTime emailVerificationLastSentAt;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "foto_perfil_url", columnDefinition = "TEXT")
    private String fotoPerfilUrl;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        createdAt = now;
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getTelefone() {
        return telefone;
    }

    public void setTelefone(String telefone) {
        this.telefone = telefone;
    }

    public String getSenha() {
        return senha;
    }

    public void setSenha(String senha) {
        this.senha = senha;
    }

    @Override
    public boolean isEmailVerified() {
        return emailVerified;
    }

    @Override
    public void setEmailVerified(boolean emailVerified) {
        this.emailVerified = emailVerified;
    }

    @Override
    public OffsetDateTime getEmailVerifiedAt() {
        return emailVerifiedAt;
    }

    @Override
    public void setEmailVerifiedAt(OffsetDateTime emailVerifiedAt) {
        this.emailVerifiedAt = emailVerifiedAt;
    }

    @Override
    public OffsetDateTime getEmailVerificationLastSentAt() {
        return emailVerificationLastSentAt;
    }

    @Override
    public void setEmailVerificationLastSentAt(OffsetDateTime emailVerificationLastSentAt) {
        this.emailVerificationLastSentAt = emailVerificationLastSentAt;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public LocalDate getDataNascimento() {
        return dataNascimento;
    }

    public void setDataNascimento(LocalDate dataNascimento) {
        this.dataNascimento = dataNascimento;
    }

    public String getFotoPerfilUrl() {
        return fotoPerfilUrl;
    }

    public void setFotoPerfilUrl(String fotoPerfilUrl) {
        this.fotoPerfilUrl = fotoPerfilUrl;
    }
}
