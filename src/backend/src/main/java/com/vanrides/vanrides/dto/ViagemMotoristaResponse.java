package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

public class ViagemMotoristaResponse {

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
    private final ViagemParadaResponse proximaParada;
    private final List<ViagemParadaResponse> paradas;
    private final ViagemRotaGeograficaResponse rotaGeografica;
    private final TrackingStateResponse trackingState;
    private final NavigationRoutePlanResponse navigationRoutePlan;

    public ViagemMotoristaResponse(Long id,
                                   Long rotaBaseId,
                                   String rotaNome,
                                   LocalDate dataOperacao,
                                   LocalTime horarioPartida,
                                   ViagemStatus status,
                                   Integer rotaVersao,
                                   Integer etaMinutosAteProximaParada,
                                   LocalDateTime etaAtualizadoEm,
                                   AdminMotoristaSummaryResponse motorista,
                                   ViagemParadaResponse proximaParada,
                                   List<ViagemParadaResponse> paradas,
                                   ViagemRotaGeograficaResponse rotaGeografica) {
        this(id, rotaBaseId, null, rotaNome, null, null, dataOperacao, horarioPartida, status, rotaVersao,
                etaMinutosAteProximaParada, etaAtualizadoEm, motorista, proximaParada, paradas, rotaGeografica,
                null);
    }

    public ViagemMotoristaResponse(Long id,
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
                                   ViagemParadaResponse proximaParada,
                                   List<ViagemParadaResponse> paradas,
                                   ViagemRotaGeograficaResponse rotaGeografica) {
        this(id, rotaBaseId, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao, horarioPartida,
                status, rotaVersao, etaMinutosAteProximaParada, etaAtualizadoEm, motorista, proximaParada,
                paradas, rotaGeografica, null);
    }

    public ViagemMotoristaResponse(Long id,
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
                                   ViagemParadaResponse proximaParada,
                                   List<ViagemParadaResponse> paradas,
                                   ViagemRotaGeograficaResponse rotaGeografica,
                                   TrackingStateResponse trackingState) {
        this(id, rotaBaseId, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao, horarioPartida,
                status, rotaVersao, etaMinutosAteProximaParada, etaAtualizadoEm, motorista, proximaParada,
                paradas, rotaGeografica, trackingState,
                rotaGeografica == null ? null : rotaGeografica.getNavigationRoutePlan());
    }

    public ViagemMotoristaResponse(Long id,
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
                                   ViagemParadaResponse proximaParada,
                                   List<ViagemParadaResponse> paradas,
                                   ViagemRotaGeograficaResponse rotaGeografica,
                                   TrackingStateResponse trackingState,
                                   NavigationRoutePlanResponse navigationRoutePlan) {
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
        this.proximaParada = proximaParada;
        this.paradas = paradas;
        this.rotaGeografica = rotaGeografica;
        this.trackingState = trackingState == null ? TrackingStateResponse.unavailable() : trackingState;
        this.navigationRoutePlan = navigationRoutePlan;
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

    public ViagemParadaResponse getProximaParada() {
        return proximaParada;
    }

    public List<ViagemParadaResponse> getParadas() {
        return paradas;
    }

    public ViagemRotaGeograficaResponse getRotaGeografica() {
        return rotaGeografica;
    }

    public TrackingStateResponse getTrackingState() {
        return trackingState;
    }

    public NavigationRoutePlanResponse getNavigationRoutePlan() {
        return navigationRoutePlan;
    }
}
