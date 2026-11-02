package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.Set;
import java.util.stream.Collectors;

@Entity
@Table(name = "rota_base")
public class RotaBase {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_id", nullable = false)
    private Motorista motorista;

    @Column(nullable = false)
    private String nome;

    @Column(nullable = false)
    private boolean ativo = true;

    @Column(name = "ativa", nullable = false)
    private boolean ativa = true;

    @Column(name = "geracao_automatica_ativa", nullable = false)
    private boolean geracaoAutomaticaAtiva = true;

    @Column(name = "dias_operacao", nullable = false)
    private String diasOperacao = "MONDAY,TUESDAY,WEDNESDAY,THURSDAY,FRIDAY";

    @Column(name = "horario_partida")
    private LocalTime horarioPartida;

    @Column(name = "destino_final_nome")
    private String destinoFinalNome;

    @Column(name = "destino_final_endereco")
    private String destinoFinalEndereco;

    @Column(name = "destino_final_latitude")
    private Double destinoFinalLatitude;

    @Column(name = "destino_final_longitude")
    private Double destinoFinalLongitude;

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

    public Motorista getMotorista() {
        return motorista;
    }

    public void setMotorista(Motorista motorista) {
        this.motorista = motorista;
    }

    public String getNome() {
        return nome;
    }

    public void setNome(String nome) {
        this.nome = nome;
    }

    public boolean isAtivo() {
        return ativo;
    }

    public void setAtivo(boolean ativo) {
        this.ativo = ativo;
    }

    public boolean isAtiva() {
        return ativa;
    }

    public void setAtiva(boolean ativa) {
        this.ativa = ativa;
    }

    public boolean isGeracaoAutomaticaAtiva() {
        return geracaoAutomaticaAtiva;
    }

    public void setGeracaoAutomaticaAtiva(boolean geracaoAutomaticaAtiva) {
        this.geracaoAutomaticaAtiva = geracaoAutomaticaAtiva;
    }

    public String getDiasOperacao() {
        return diasOperacao;
    }

    public void setDiasOperacao(String diasOperacao) {
        this.diasOperacao = diasOperacao;
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
        return getDiasOperacaoAsSet().contains(dataOperacao.getDayOfWeek());
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public void setHorarioPartida(LocalTime horarioPartida) {
        this.horarioPartida = horarioPartida;
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
}
