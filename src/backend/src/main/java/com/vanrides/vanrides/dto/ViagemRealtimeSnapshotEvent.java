package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemParadaStatus;
import com.vanrides.vanrides.model.ViagemStatus;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;

public class ViagemRealtimeSnapshotEvent {

    private final String tipo;
    private final Long viagemId;
    private final Long motoristaId;
    private final LocalDate dataOperacao;
    private final ViagemStatus status;
    private final Integer rotaVersao;
    private final Integer totalParadas;
    private final Integer paradasPendentes;
    private final Integer paradasConcluidas;
    private final Long proximaParadaId;
    private final Integer proximaParadaOrdem;
    private final Long proximoPassageiroId;
    private final String proximoPassageiroNome;
    private final ViagemParadaStatus proximaParadaStatus;
    private final Integer etaMinutosAteProximaParada;
    private final LocalDateTime etaAtualizadoEm;
    private final Double latitudeMotorista;
    private final Double longitudeMotorista;
    private final LocalDateTime localizacaoAtualizadaEm;
    private final TrackingStateResponse trackingState;
    private final List<ViagemOperationalAlertResponse> avisosOperacionais;

    public ViagemRealtimeSnapshotEvent(String tipo,
                                       Long viagemId,
                                       Long motoristaId,
                                       LocalDate dataOperacao,
                                       ViagemStatus status,
                                       Integer rotaVersao,
                                       Integer totalParadas,
                                       Integer paradasPendentes,
                                       Integer paradasConcluidas,
                                       Long proximaParadaId,
                                       Integer proximaParadaOrdem,
                                       Long proximoPassageiroId,
                                       String proximoPassageiroNome,
                                       ViagemParadaStatus proximaParadaStatus,
                                       Integer etaMinutosAteProximaParada,
                                       LocalDateTime etaAtualizadoEm,
                                       Double latitudeMotorista,
                                       Double longitudeMotorista,
                                       LocalDateTime localizacaoAtualizadaEm) {
        this(
                tipo,
                viagemId,
                motoristaId,
                dataOperacao,
                status,
                rotaVersao,
                totalParadas,
                paradasPendentes,
                paradasConcluidas,
                proximaParadaId,
                proximaParadaOrdem,
                proximoPassageiroId,
                proximoPassageiroNome,
                proximaParadaStatus,
                etaMinutosAteProximaParada,
                etaAtualizadoEm,
                latitudeMotorista,
                longitudeMotorista,
                localizacaoAtualizadaEm,
                TrackingStateResponse.unavailable(),
                List.of()
        );
    }

    public ViagemRealtimeSnapshotEvent(String tipo,
                                       Long viagemId,
                                       Long motoristaId,
                                       LocalDate dataOperacao,
                                       ViagemStatus status,
                                       Integer rotaVersao,
                                       Integer totalParadas,
                                       Integer paradasPendentes,
                                       Integer paradasConcluidas,
                                       Long proximaParadaId,
                                       Integer proximaParadaOrdem,
                                       Long proximoPassageiroId,
                                       String proximoPassageiroNome,
                                       ViagemParadaStatus proximaParadaStatus,
                                       Integer etaMinutosAteProximaParada,
                                       LocalDateTime etaAtualizadoEm,
                                       Double latitudeMotorista,
                                       Double longitudeMotorista,
                                       LocalDateTime localizacaoAtualizadaEm,
                                       TrackingStateResponse trackingState) {
        this(
                tipo,
                viagemId,
                motoristaId,
                dataOperacao,
                status,
                rotaVersao,
                totalParadas,
                paradasPendentes,
                paradasConcluidas,
                proximaParadaId,
                proximaParadaOrdem,
                proximoPassageiroId,
                proximoPassageiroNome,
                proximaParadaStatus,
                etaMinutosAteProximaParada,
                etaAtualizadoEm,
                latitudeMotorista,
                longitudeMotorista,
                localizacaoAtualizadaEm,
                trackingState,
                List.of()
        );
    }

    public ViagemRealtimeSnapshotEvent(String tipo,
                                       Long viagemId,
                                       Long motoristaId,
                                       LocalDate dataOperacao,
                                       ViagemStatus status,
                                       Integer rotaVersao,
                                       Integer totalParadas,
                                       Integer paradasPendentes,
                                       Integer paradasConcluidas,
                                       Long proximaParadaId,
                                       Integer proximaParadaOrdem,
                                       Long proximoPassageiroId,
                                       String proximoPassageiroNome,
                                       ViagemParadaStatus proximaParadaStatus,
                                       Integer etaMinutosAteProximaParada,
                                       LocalDateTime etaAtualizadoEm,
                                       Double latitudeMotorista,
                                       Double longitudeMotorista,
                                       LocalDateTime localizacaoAtualizadaEm,
                                       TrackingStateResponse trackingState,
                                       List<ViagemOperationalAlertResponse> avisosOperacionais) {
        this.tipo = tipo;
        this.viagemId = viagemId;
        this.motoristaId = motoristaId;
        this.dataOperacao = dataOperacao;
        this.status = status;
        this.rotaVersao = rotaVersao;
        this.totalParadas = totalParadas;
        this.paradasPendentes = paradasPendentes;
        this.paradasConcluidas = paradasConcluidas;
        this.proximaParadaId = proximaParadaId;
        this.proximaParadaOrdem = proximaParadaOrdem;
        this.proximoPassageiroId = proximoPassageiroId;
        this.proximoPassageiroNome = proximoPassageiroNome;
        this.proximaParadaStatus = proximaParadaStatus;
        this.etaMinutosAteProximaParada = etaMinutosAteProximaParada;
        this.etaAtualizadoEm = etaAtualizadoEm;
        this.latitudeMotorista = latitudeMotorista;
        this.longitudeMotorista = longitudeMotorista;
        this.localizacaoAtualizadaEm = localizacaoAtualizadaEm;
        this.trackingState = trackingState == null ? TrackingStateResponse.unavailable() : trackingState;
        this.avisosOperacionais = avisosOperacionais == null ? List.of() : List.copyOf(avisosOperacionais);
    }

    public String getTipo() {
        return tipo;
    }

    public Long getViagemId() {
        return viagemId;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public LocalDate getDataOperacao() {
        return dataOperacao;
    }

    public ViagemStatus getStatus() {
        return status;
    }

    public Integer getRotaVersao() {
        return rotaVersao;
    }

    public Integer getTotalParadas() {
        return totalParadas;
    }

    public Integer getParadasPendentes() {
        return paradasPendentes;
    }

    public Integer getParadasConcluidas() {
        return paradasConcluidas;
    }

    public Long getProximaParadaId() {
        return proximaParadaId;
    }

    public Integer getProximaParadaOrdem() {
        return proximaParadaOrdem;
    }

    public Long getProximoPassageiroId() {
        return proximoPassageiroId;
    }

    public String getProximoPassageiroNome() {
        return proximoPassageiroNome;
    }

    public ViagemParadaStatus getProximaParadaStatus() {
        return proximaParadaStatus;
    }

    public Integer getEtaMinutosAteProximaParada() {
        return etaMinutosAteProximaParada;
    }

    public LocalDateTime getEtaAtualizadoEm() {
        return etaAtualizadoEm;
    }

    public Double getLatitudeMotorista() {
        return latitudeMotorista;
    }

    public Double getLongitudeMotorista() {
        return longitudeMotorista;
    }

    public LocalDateTime getLocalizacaoAtualizadaEm() {
        return localizacaoAtualizadaEm;
    }

    public TrackingStateResponse getTrackingState() {
        return trackingState;
    }

    public List<ViagemOperationalAlertResponse> getAvisosOperacionais() {
        return avisosOperacionais;
    }
}
