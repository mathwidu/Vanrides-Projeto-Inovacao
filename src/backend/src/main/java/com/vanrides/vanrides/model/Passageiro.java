package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "passageiro")
public class Passageiro implements EmailVerifiableAccount {

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
    private String senha;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verified", nullable = false)
    private boolean emailVerified = false;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verified_at")
    private OffsetDateTime emailVerifiedAt;

    @JsonProperty(access = JsonProperty.Access.READ_ONLY)
    @Column(name = "email_verification_last_sent_at")
    private OffsetDateTime emailVerificationLastSentAt;

    @Column(nullable = false)
    private String endereco;

    @Column(nullable = false)
    private String bairro; // ✅ Novo campo

    @Column(nullable = false)
    private String cidade;

    @Column(nullable = false)
    private String estado;

    @Column(nullable = false)
    private String cep;

    @Column
    private Double latitude;

    @Column
    private Double longitude;

    @Column(name = "menor_de_idade", nullable = false)
    private boolean menorDeIdade = false;

    @Column(name = "data_nascimento")
    private LocalDate dataNascimento;

    @Column(name = "foto_perfil_url", columnDefinition = "TEXT")
    private String fotoPerfilUrl;

    @ManyToOne
    @JoinColumn(name = "motorista_id")
    private Motorista motorista;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id")
    private Company company;

    // 🔥 Construtor vazio obrigatório para o JPA
    public Passageiro() {}

    // 🔥 Construtor completo (sem latitude/longitude)
    public Passageiro(String nome, String email, String telefone, String senha,
                      String endereco, String bairro, String cidade, String estado, String cep) {
        this.nome = nome;
        this.email = email;
        this.telefone = telefone;
        this.senha = senha;
        this.endereco = endereco;
        this.bairro = bairro;
        this.cidade = cidade;
        this.estado = estado;
        this.cep = cep;
    }

    // 🔥 Getters e Setters
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

    public String getEndereco() {
        return endereco;
    }

    public void setEndereco(String endereco) {
        this.endereco = endereco;
    }

    public String getBairro() {
        return bairro;
    }

    public void setBairro(String bairro) {
        this.bairro = bairro;
    }

    public String getCidade() {
        return cidade;
    }

    public void setCidade(String cidade) {
        this.cidade = cidade;
    }

    public String getEstado() {
        return estado;
    }

    public void setEstado(String estado) {
        this.estado = estado;
    }

    public String getCep() {
        return cep;
    }

    public void setCep(String cep) {
        this.cep = cep;
    }

    public Double getLatitude() {
        return latitude;
    }

    public void setLatitude(Double latitude) {
        this.latitude = latitude;
    }

    public Double getLongitude() {
        return longitude;
    }

    public void setLongitude(Double longitude) {
        this.longitude = longitude;
    }

    public Motorista getMotorista() {
        return motorista;
    }

    public void setMotorista(Motorista motorista) {
        this.motorista = motorista;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }

    public void setMenorDeIdade(boolean menorDeIdade) {
        this.menorDeIdade = menorDeIdade;
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

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }
}
