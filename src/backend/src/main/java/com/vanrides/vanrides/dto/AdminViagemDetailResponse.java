package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.TipoOperacaoServico;
import com.vanrides.vanrides.model.ViagemStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class AdminViagemDetailResponse {

    private final Long id;
    private final Long rotaBaseId;
    private final Long servicoId;
    private final String rotaNome;
    private final String servicoNome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalDate dataOperacao;
    private final LocalTime horarioPartida;
    private final ViagemStatus status;
    private final Integer rotaVersao;
    private final Integer etaMinutosAteProximaParada;
    private final LocalDateTime etaAtualizadoEm;
    private final AdminMotoristaSummaryResponse motorista;
    private final int quantidadeParadas;
    private final AdminViagemDestinoFinalResponse destinoFinal;
    private final AdminViagemParadaResponse proximaParada;
    private final List<AdminViagemParadaResponse> paradas;
    private final ViagemRotaGeograficaResponse rotaGeografica;
    private final String fontePlanejamento;
    private final boolean snapshotOperacional;
    private final TrackingStateResponse trackingState;

    public AdminViagemDetailResponse(Long id,
                                     Long rotaBaseId,
                                     Long servicoId,
                                     String rotaNome,
                                     String servicoNome,
                                     TipoOperacaoServico tipoOperacao,
                                     LocalDate dataOperacao,
                                     LocalTime horarioPartida,
                                     ViagemStatus status,
                                     Integer rotaVersao,
                                     Integer etaMinutosAteProximaParada,
                                     LocalDateTime etaAtualizadoEm,
                                     AdminMotoristaSummaryResponse motorista,
                                     int quantidadeParadas,
                                     AdminViagemDestinoFinalResponse destinoFinal,
                                     AdminViagemParadaResponse proximaParada,
                                     List<AdminViagemParadaResponse> paradas,
                                     String fontePlanejamento,
                                     boolean snapshotOperacional) {
        this(id, rotaBaseId, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao,
                horarioPartida, status, rotaVersao, etaMinutosAteProximaParada, etaAtualizadoEm,
                motorista, quantidadeParadas, destinoFinal, proximaParada, paradas, null,
                fontePlanejamento, snapshotOperacional, null);
    }

    public AdminViagemDetailResponse(Long id,
                                     Long rotaBaseId,
                                     Long servicoId,
                                     String rotaNome,
                                     String servicoNome,
                                     TipoOperacaoServico tipoOperacao,
                                     LocalDate dataOperacao,
                                     LocalTime horarioPartida,
                                     ViagemStatus status,
                                     Integer rotaVersao,
                                     Integer etaMinutosAteProximaParada,
                                     LocalDateTime etaAtualizadoEm,
                                     AdminMotoristaSummaryResponse motorista,
                                     int quantidadeParadas,
                                     AdminViagemDestinoFinalResponse destinoFinal,
                                     AdminViagemParadaResponse proximaParada,
                                     List<AdminViagemParadaResponse> paradas,
                                     ViagemRotaGeograficaResponse rotaGeografica,
                                     String fontePlanejamento,
                                     boolean snapshotOperacional) {
        this(id, rotaBaseId, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao,
                horarioPartida, status, rotaVersao, etaMinutosAteProximaParada, etaAtualizadoEm,
                motorista, quantidadeParadas, destinoFinal, proximaParada, paradas, rotaGeografica,
                fontePlanejamento, snapshotOperacional, null);
    }

    public AdminViagemDetailResponse(Long id,
                                     Long rotaBaseId,
                                     Long servicoId,
                                     String rotaNome,
                                     String servicoNome,
                                     TipoOperacaoServico tipoOperacao,
                                     LocalDate dataOperacao,
                                     LocalTime horarioPartida,
                                     ViagemStatus status,
                                     Integer rotaVersao,
                                     Integer etaMinutosAteProximaParada,
                                     LocalDateTime etaAtualizadoEm,
                                     AdminMotoristaSummaryResponse motorista,
                                     int quantidadeParadas,
                                     AdminViagemDestinoFinalResponse destinoFinal,
                                     AdminViagemParadaResponse proximaParada,
                                     List<AdminViagemParadaResponse> paradas,
                                     ViagemRotaGeograficaResponse rotaGeografica,
                                     String fontePlanejamento,
                                     boolean snapshotOperacional,
                                     TrackingStateResponse trackingState) {
        this.id = id;
        this.rotaBaseId = rotaBaseId;
        this.servicoId = servicoId;
        this.rotaNome = rotaNome;
        this.servicoNome = servicoNome;
        this.tipoOperacao = tipoOperacao;
        this.dataOperacao = dataOperacao;
        this.horarioPartida = horarioPartida;
        this.status = status;
        this.rotaVersao = rotaVersao;
        this.etaMinutosAteProximaParada = etaMinutosAteProximaParada;
        this.etaAtualizadoEm = etaAtualizadoEm;
        this.motorista = motorista;
        this.quantidadeParadas = quantidadeParadas;
        this.destinoFinal = destinoFinal;
        this.proximaParada = proximaParada;
        this.paradas = paradas;
        this.rotaGeografica = rotaGeografica;
        this.fontePlanejamento = fontePlanejamento;
        this.snapshotOperacional = snapshotOperacional;
        this.trackingState = trackingState == null ? TrackingStateResponse.unavailable() : trackingState;
    }

    public Long getId() {
        return id;
    }

    public Long getRotaBaseId() {
        return rotaBaseId;
    }

    public Long getServicoId() {
        return servicoId;
    }

    public String getRotaNome() {
        return rotaNome;
    }

    public String getServicoNome() {
        return servicoNome;
    }

    public TipoOperacaoServico getTipoOperacao() {
        return tipoOperacao;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public LocalTime getHorarioPartida() {
        return horarioPartida;
    }

    public ViagemStatus getStatus() {
        return status;
    }

    public Integer getRotaVersao() {
        return rotaVersao;
    }

    public Integer getEtaMinutosAteProximaParada() {
        return etaMinutosAteProximaParada;
    }

    public LocalDateTime getEtaAtualizadoEm() {
        return etaAtualizadoEm;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public int getQuantidadeParadas() {
        return quantidadeParadas;
    }

    public AdminViagemDestinoFinalResponse getDestinoFinal() {
        return destinoFinal;
    }

    public AdminViagemParadaResponse getProximaParada() {
        return proximaParada;
    }

    public List<AdminViagemParadaResponse> getParadas() {
        return paradas;
    }

    public ViagemRotaGeograficaResponse getRotaGeografica() {
        return rotaGeografica;
    }

    public String getFontePlanejamento() {
        return fontePlanejamento;
    }

    public boolean isSnapshotOperacional() {
        return snapshotOperacional;
    }

    public TrackingStateResponse getTrackingState() {
        return trackingState;
    }
}
