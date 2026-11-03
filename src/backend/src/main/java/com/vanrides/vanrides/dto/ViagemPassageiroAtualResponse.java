package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;

public class ViagemPassageiroAtualResponse {

    private final Long id;
    private final Long servicoId;
    private final String rotaNome;
    private final String servicoNome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalDate dataOperacao;
    private final LocalTime horarioPartida;
    private final ViagemStatus status;
    private final Integer rotaVersao;
    private final Integer etaMinutosAtePassageiro;
    private final LocalDateTime etaAtualizadoEm;
    private final AdminMotoristaSummaryResponse motorista;
    private final ViagemParadaResponse paradaDoPassageiro;
    private final ViagemRotaGeograficaResponse rotaGeografica;
    private final TrackingStateResponse trackingState;

    public ViagemPassageiroAtualResponse(Long id,
                                         String rotaNome,
                                         LocalDate dataOperacao,
                                         LocalTime horarioPartida,
                                         ViagemStatus status,
                                         Integer rotaVersao,
                                         Integer etaMinutosAtePassageiro,
                                         LocalDateTime etaAtualizadoEm,
                                         AdminMotoristaSummaryResponse motorista,
                                         ViagemParadaResponse paradaDoPassageiro,
                                         ViagemRotaGeograficaResponse rotaGeografica) {
        this(id, null, rotaNome, null, null, dataOperacao, horarioPartida, status, rotaVersao,
                etaMinutosAtePassageiro, etaAtualizadoEm, motorista, paradaDoPassageiro, rotaGeografica,
                null);
    }

    public ViagemPassageiroAtualResponse(Long id,
                                         Long servicoId,
                                         String rotaNome,
                                         String servicoNome,
                                         TipoOperacaoServico tipoOperacao,
                                         LocalDate dataOperacao,
                                         LocalTime horarioPartida,
                                         ViagemStatus status,
                                         Integer rotaVersao,
                                         Integer etaMinutosAtePassageiro,
                                         LocalDateTime etaAtualizadoEm,
                                         AdminMotoristaSummaryResponse motorista,
                                         ViagemParadaResponse paradaDoPassageiro,
                                         ViagemRotaGeograficaResponse rotaGeografica) {
        this(id, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao, horarioPartida,
                status, rotaVersao, etaMinutosAtePassageiro, etaAtualizadoEm, motorista,
                paradaDoPassageiro, rotaGeografica, null);
    }

    public ViagemPassageiroAtualResponse(Long id,
                                         Long servicoId,
                                         String rotaNome,
                                         String servicoNome,
                                         TipoOperacaoServico tipoOperacao,
                                         LocalDate dataOperacao,
                                         LocalTime horarioPartida,
                                         ViagemStatus status,
                                         Integer rotaVersao,
                                         Integer etaMinutosAtePassageiro,
                                         LocalDateTime etaAtualizadoEm,
                                         AdminMotoristaSummaryResponse motorista,
                                         ViagemParadaResponse paradaDoPassageiro,
                                         ViagemRotaGeograficaResponse rotaGeografica,
                                         TrackingStateResponse trackingState) {
        this.id = id;
        this.servicoId = servicoId;
        this.rotaNome = rotaNome;
        this.servicoNome = servicoNome;
        this.tipoOperacao = tipoOperacao;
        this.dataOperacao = dataOperacao;
        this.horarioPartida = horarioPartida;
        this.status = status;
        this.rotaVersao = rotaVersao;
        this.etaMinutosAtePassageiro = etaMinutosAtePassageiro;
        this.etaAtualizadoEm = etaAtualizadoEm;
        this.motorista = motorista;
        this.paradaDoPassageiro = paradaDoPassageiro;
        this.rotaGeografica = rotaGeografica;
        this.trackingState = trackingState == null ? TrackingStateResponse.unavailable() : trackingState;
    }

    public Long getId() {
        return id;
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

    public Integer getEtaMinutosAtePassageiro() {
        return etaMinutosAtePassageiro;
    }

    public LocalDateTime getEtaAtualizadoEm() {
        return etaAtualizadoEm;
    }

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public ViagemParadaResponse getParadaDoPassageiro() {
        return paradaDoPassageiro;
    }

    public ViagemRotaGeograficaResponse getRotaGeografica() {
        return rotaGeografica;
    }

    public TrackingStateResponse getTrackingState() {
        return trackingState;
    }
}
