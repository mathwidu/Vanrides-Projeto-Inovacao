package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.OffsetDateTime;

@Entity
@Table(name = "servico_passageiro", indexes = {
        @Index(name = "idx_servico_passageiro_company_id", columnList = "company_id"),
        @Index(name = "idx_servico_passageiro_servico_id", columnList = "servico_recorrente_id"),
        @Index(name = "idx_servico_passageiro_passageiro_id", columnList = "passageiro_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_servico_passageiro", columnNames = {"servico_recorrente_id", "passageiro_id"})
})
public class ServicoPassageiro {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_recorrente_id", nullable = false)
    private ServicoRecorrente servicoRecorrente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @Column(name = "ordem_padrao")
    private Integer ordemPadrao;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "data_inicio")
    private LocalDate dataInicio;

    @Column(name = "data_fim")
    private LocalDate dataFim;

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

    public boolean ativoNaData(LocalDate dataOperacao) {
        if (!ativo || dataOperacao == null) {
            return false;
        }
        boolean startsBeforeOrOnDate = dataInicio == null || !dataInicio.isAfter(dataOperacao);
        boolean endsAfterOrOnDate = dataFim == null || !dataFim.isBefore(dataOperacao);
        return startsBeforeOrOnDate && endsAfterOrOnDate;
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

    public Integer getOrdemPadrao() {
        return ordemPadrao;
    }

    public void setOrdemPadrao(Integer ordemPadrao) {
        this.ordemPadrao = ordemPadrao;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public LocalDate getDataInicio() {
        return dataInicio;
    }

    public void setDataInicio(LocalDate dataInicio) {
        this.dataInicio = dataInicio;
    }

    public LocalDate getDataFim() {
        return dataFim;
    }

    public void setDataFim(LocalDate dataFim) {
        this.dataFim = dataFim;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
