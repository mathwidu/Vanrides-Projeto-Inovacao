package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.time.OffsetDateTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "servico_recorrente", indexes = {
        @Index(name = "idx_servico_recorrente_company_id", columnList = "company_id"),
        @Index(name = "idx_servico_recorrente_motorista_id", columnList = "motorista_padrao_id")
}, uniqueConstraints = {
        @UniqueConstraint(name = "uk_servico_recorrente_rota_base", columnNames = {"rota_base_id"})
})
public class ServicoRecorrente {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_base_id")
    private RotaBase rotaBaseLegada;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_padrao_id")
    private Motorista motoristaPadrao;

    @Column(nullable = false)
    private String nome;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao", nullable = false)
    private TipoOperacaoServico tipoOperacao = TipoOperacaoServico.IDA;

    @Column(name = "horario_partida")
    private LocalTime horarioPartida;

    @Column(name = "dias_operacao", nullable = false)
    private String diasOperacao = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @Column(name = "destino_final_nome")
    private String destinoFinalNome;

    @Column(name = "destino_final_endereco")
    private String destinoFinalEndereco;

    @Column(name = "destino_final_latitude")
    private Double destinoFinalLatitude;

    @Column(name = "destino_final_longitude")
    private Double destinoFinalLongitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "politica_presenca", nullable = false)
    private PoliticaPresencaServico politicaPresenca = PoliticaPresencaServico.INCLUIR_POR_PADRAO;

    @Column(name = "capacidade_planejada")
    private Integer capacidadePlanejada;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "geracao_automatica_ativa", nullable = false)
    private boolean geracaoAutomaticaAtiva = true;

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

    public Set<DayOfWeek> getDiasOperacaoAsSet() {
        if (diasOperacao == null || diasOperacao.isBlank()) {
            return Set.of();
        }
        return Arrays.stream(diasOperacao.split(","))
                .map(String::trim)
                .filter(value -> !value.isBlank())
                .map(String::toUpperCase)
                .map(DayOfWeek::valueOf)
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    public void setDiasOperacaoFromSet(Set<DayOfWeek> dias) {
        this.diasOperacao = dias.stream()
                .sorted()
                .map(DayOfWeek::name)
                .collect(Collectors.joining(","));
    }

    public boolean operaNaData(LocalDate dataOperacao) {
        return dataOperacao != null && getDiasOperacaoAsSet().contains(dataOperacao.getDayOfWeek());
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

    public RotaBase getRotaBaseLegada() {
        return rotaBaseLegada;
    }

    public void setRotaBaseLegada(RotaBase rotaBaseLegada) {
        this.rotaBaseLegada = rotaBaseLegada;
    }

    public Motorista getMotoristaPadrao() {
        return motoristaPadrao;
    }

    public void setMotoristaPadrao(Motorista motoristaPadrao) {
        this.motoristaPadrao = motoristaPadrao;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public TipoOperacaoServico getTipoOperacao() {
        return tipoOperacao;
    }

    public void setTipoOperacao(TipoOperacaoServico tipoOperacao) {
        this.tipoOperacao = tipoOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public void setHorarioPartida(LocalTime horarioPartida) {
        this.horarioPartida = horarioPartida;
    }

    public String getDiasOperacao() {
        return diasOperacao;
    }

    public void setDiasOperacao(String diasOperacao) {
        this.diasOperacao = diasOperacao;
    }

    public String getDestinoFinalNome() {
        return destinoFinalNome;
    }

    public void setDestinoFinalNome(String destinoFinalNome) {
        this.destinoFinalNome = destinoFinalNome;
    }

    public String getDestinoFinalEndereco() {
        return destinoFinalEndereco;
    }

    public void setDestinoFinalEndereco(String destinoFinalEndereco) {
        this.destinoFinalEndereco = destinoFinalEndereco;
    }

    public Double getDestinoFinalLatitude() {
        return destinoFinalLatitude;
    }

    public void setDestinoFinalLatitude(Double destinoFinalLatitude) {
        this.destinoFinalLatitude = destinoFinalLatitude;
    }

    public Double getDestinoFinalLongitude() {
        return destinoFinalLongitude;
    }

    public void setDestinoFinalLongitude(Double destinoFinalLongitude) {
        this.destinoFinalLongitude = destinoFinalLongitude;
    }

    public PoliticaPresencaServico getPoliticaPresenca() {
        return politicaPresenca;
    }

    public void setPoliticaPresenca(PoliticaPresencaServico politicaPresenca) {
        this.politicaPresenca = politicaPresenca;
    }

    public Integer getCapacidadePlanejada() {
        return capacidadePlanejada;
    }

    public void setCapacidadePlanejada(Integer capacidadePlanejada) {
        this.capacidadePlanejada = capacidadePlanejada;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isGeracaoAutomaticaAtiva() {
        return geracaoAutomaticaAtiva;
    }

    public void setGeracaoAutomaticaAtiva(boolean geracaoAutomaticaAtiva) {
        this.geracaoAutomaticaAtiva = geracaoAutomaticaAtiva;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
