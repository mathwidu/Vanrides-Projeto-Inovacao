package com.vanrides.vanrides.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

@Entity
@Table(name = "viagem")
public class Viagem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @JsonIgnore
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "rota_base_id")
    private RotaBase rotaBase;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "servico_recorrente_id")
    private ServicoRecorrente servicoRecorrente;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "motorista_id", nullable = false)
    private Motorista motorista;

    @Column(name = "data_operacao", nullable = false)
    private LocalDate dataOperacao;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ViagemStatus status = ViagemStatus.PLANNED;

    @Column(name = "rota_versao", nullable = false)
    private Integer rotaVersao = 1;

    @Column(name = "eta_base_minutos")
    private Integer etaBaseMinutos;

    @Column(name = "eta_atualizado_em")
    private LocalDateTime etaAtualizadoEm;

    @Column(name = "horario_partida")
    private LocalTime horarioPartida;

    @Column(name = "servico_nome_snapshot")
    private String servicoNomeSnapshot;

    @Enumerated(EnumType.STRING)
    @Column(name = "tipo_operacao_snapshot")
    private TipoOperacaoServico tipoOperacaoSnapshot;

    @Column(name = "destino_final_nome_snapshot")
    private String destinoFinalNomeSnapshot;

    @Column(name = "destino_final_endereco_snapshot")
    private String destinoFinalEnderecoSnapshot;

    @Column(name = "destino_final_latitude_snapshot")
    private Double destinoFinalLatitudeSnapshot;

    @Column(name = "destino_final_longitude_snapshot")
    private Double destinoFinalLongitudeSnapshot;

    @Column(name = "rota_geografica_provider_snapshot")
    private String rotaGeograficaProviderSnapshot;

    @Column(name = "rota_geografica_polyline_snapshot", columnDefinition = "TEXT")
    private String rotaGeograficaPolylineSnapshot;

    @Column(name = "rota_geografica_navegacao_url_snapshot", columnDefinition = "TEXT")
    private String rotaGeograficaNavegacaoUrlSnapshot;

    @Column(name = "rota_geografica_calculada_em")
    private LocalDateTime rotaGeograficaCalculadaEm;

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

    public RotaBase getRotaBase() {
        return rotaBase;
    }

    public void setRotaBase(RotaBase rotaBase) {
        this.rotaBase = rotaBase;
    }

    public ServicoRecorrente getServicoRecorrente() {
        return servicoRecorrente;
    }

    public void setServicoRecorrente(ServicoRecorrente servicoRecorrente) {
        this.servicoRecorrente = servicoRecorrente;
    }

    public Motorista getMotorista() {
        return motorista;
    }

    public void setMotorista(Motorista motorista) {
        this.motorista = motorista;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public void setDataOperacao(LocalDate dataOperacao) {
        this.dataOperacao = dataOperacao;
    }

    public ViagemStatus getStatus() {
        return status;
    }

    public void setStatus(ViagemStatus status) {
        this.status = status;
    }

    public Integer getRotaVersao() {
        return rotaVersao;
    }

    public void setRotaVersao(Integer rotaVersao) {
        this.rotaVersao = rotaVersao;
    }

    public Integer getEtaBaseMinutos() {
        return etaBaseMinutos;
    }

    public void setEtaBaseMinutos(Integer etaBaseMinutos) {
        this.etaBaseMinutos = etaBaseMinutos;
    }

    public LocalDateTime getEtaAtualizadoEm() {
        return etaAtualizadoEm;
    }

    public void setEtaAtualizadoEm(LocalDateTime etaAtualizadoEm) {
        this.etaAtualizadoEm = etaAtualizadoEm;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public void setHorarioPartida(LocalTime horarioPartida) {
        this.horarioPartida = horarioPartida;
    }

    public String getServicoNomeSnapshot() {
        return servicoNomeSnapshot;
    }

    public void setServicoNomeSnapshot(String servicoNomeSnapshot) {
        this.servicoNomeSnapshot = servicoNomeSnapshot;
    }

    public TipoOperacaoServico getTipoOperacaoSnapshot() {
        return tipoOperacaoSnapshot;
    }

    public void setTipoOperacaoSnapshot(TipoOperacaoServico tipoOperacaoSnapshot) {
        this.tipoOperacaoSnapshot = tipoOperacaoSnapshot;
    }

    public String getDestinoFinalNomeSnapshot() {
        return destinoFinalNomeSnapshot;
    }

    public void setDestinoFinalNomeSnapshot(String destinoFinalNomeSnapshot) {
        this.destinoFinalNomeSnapshot = destinoFinalNomeSnapshot;
    }

    public String getDestinoFinalEnderecoSnapshot() {
        return destinoFinalEnderecoSnapshot;
    }

    public void setDestinoFinalEnderecoSnapshot(String destinoFinalEnderecoSnapshot) {
        this.destinoFinalEnderecoSnapshot = destinoFinalEnderecoSnapshot;
    }

    public Double getDestinoFinalLatitudeSnapshot() {
        return destinoFinalLatitudeSnapshot;
    }

    public void setDestinoFinalLatitudeSnapshot(Double destinoFinalLatitudeSnapshot) {
        this.destinoFinalLatitudeSnapshot = destinoFinalLatitudeSnapshot;
    }

    public Double getDestinoFinalLongitudeSnapshot() {
        return destinoFinalLongitudeSnapshot;
    }

    public void setDestinoFinalLongitudeSnapshot(Double destinoFinalLongitudeSnapshot) {
        this.destinoFinalLongitudeSnapshot = destinoFinalLongitudeSnapshot;
    }

    public String getRotaGeograficaProviderSnapshot() {
        return rotaGeograficaProviderSnapshot;
    }

    public void setRotaGeograficaProviderSnapshot(String rotaGeograficaProviderSnapshot) {
        this.rotaGeograficaProviderSnapshot = rotaGeograficaProviderSnapshot;
    }

    public String getRotaGeograficaPolylineSnapshot() {
        return rotaGeograficaPolylineSnapshot;
    }

    public void setRotaGeograficaPolylineSnapshot(String rotaGeograficaPolylineSnapshot) {
        this.rotaGeograficaPolylineSnapshot = rotaGeograficaPolylineSnapshot;
    }

    public String getRotaGeograficaNavegacaoUrlSnapshot() {
        return rotaGeograficaNavegacaoUrlSnapshot;
    }

    public void setRotaGeograficaNavegacaoUrlSnapshot(String rotaGeograficaNavegacaoUrlSnapshot) {
        this.rotaGeograficaNavegacaoUrlSnapshot = rotaGeograficaNavegacaoUrlSnapshot;
    }

    public LocalDateTime getRotaGeograficaCalculadaEm() {
        return rotaGeograficaCalculadaEm;
    }

    public void setRotaGeograficaCalculadaEm(LocalDateTime rotaGeograficaCalculadaEm) {
        this.rotaGeograficaCalculadaEm = rotaGeograficaCalculadaEm;
    }

    public Long getRotaBaseIdOrNull() {
        return rotaBase == null ? null : rotaBase.getId();
    }

    public Long getServicoRecorrenteIdOrNull() {
        return servicoRecorrente == null ? null : servicoRecorrente.getId();
    }

    public String getNomeOperacional() {
        if (servicoNomeSnapshot != null && !servicoNomeSnapshot.isBlank()) {
            return servicoNomeSnapshot;
        }
        if (servicoRecorrente != null) {
            return servicoRecorrente.getNome();
        }
        return rotaBase == null ? null : rotaBase.getNome();
    }
}
