package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "motorista")
public class Motorista implements EmailVerifiableAccount {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false, unique = true)
    private String email;

    @Column(nullable = false)
    private String telefone;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)
    @Column(nullable = false)
    private String senha; // ✅ Novo campo senha adicionado

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verification_last_sent_at")
    private OffsetDateTime emailVerificationLastSentAt;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "foto_perfil_url", columnDefinition = "TEXT")
    private String fotoPerfilUrl;

    // Construtor vazio obrigatório para o JPA
    public Motorista() {}

    // Construtor preenchido
    public Motorista(String nome, String email, String telefone, String senha) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.senha = senha;
    }

    // Getters e Setters
    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
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

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
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
