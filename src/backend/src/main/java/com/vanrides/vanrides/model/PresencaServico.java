package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "presenca_servico", indexes = {
        @Index(name = "idx_presenca_servico_company_data", columnList = "company_id,data_operacao"),
        @Index(name = "idx_presenca_servico_passageiro_data", columnList = "passageiro_id,data_operacao"),
        @Index(name = "idx_presenca_servico_servico_data", columnList = "servico_recorrente_id,data_operacao")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_presenca_servico", columnNames = {"servico_recorrente_id", "passageiro_id", "data_operacao"})
})
public class PresencaServico {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_recorrente_id", nullable = false)
    private ServicoRecorrente servicoRecorrente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "presenca_id")
    private Presenca presencaLegada;

    @Column(name = "data_operacao", nullable = false)
    private LocalDate dataOperacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresencaServicoStatus status = PresencaServicoStatus.UNKNOWN;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PresencaServicoOrigem origem = PresencaServicoOrigem.SYSTEM;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

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

    public boolean isConfirmado() {
        return status == PresencaServicoStatus.CONFIRMED;
    }

    public boolean isAusente() {
        return status == PresencaServicoStatus.ABSENT;
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public ServicoRecorrente getServicoRecorrente() {
        return servicoRecorrente;
    }

    public void setServicoRecorrente(ServicoRecorrente servicoRecorrente) {
        this.servicoRecorrente = servicoRecorrente;
    }

    public Passageiro getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Passageiro passageiro) {
        this.passageiro = passageiro;
    }

    public Presenca getPresencaLegada() {
        return presencaLegada;
    }

    public void setPresencaLegada(Presenca presencaLegada) {
        this.presencaLegada = presencaLegada;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(LocalDate dataOperacao) {
        this.dataOperacao = dataOperacao;
    }

    public PresencaServicoStatus getStatus() {
        return status;
    }

    public void setStatus(PresencaServicoStatus status) {
        this.status = status;
    }

    public PresencaServicoOrigem getOrigem() {
        return origem;
    }

    public void setOrigem(PresencaServicoOrigem origem) {
        this.origem = origem;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
