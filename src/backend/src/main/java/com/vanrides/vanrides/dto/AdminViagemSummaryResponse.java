package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.model.ViagemStatus;
import com.vanrides.vanrides.model.TipoOperacaoServico;

import java.time.LocalDate;
import java.time.LocalTime;

public class AdminViagemSummaryResponse {

    private final Long id;
    private final Long rotaBaseId;
    private final Long servicoId;
    private final String rotaNome;
    private final String servicoNome;
    private final TipoOperacaoServico tipoOperacao;
    private final LocalDate dataOperacao;
    private final LocalTime horarioPartida;
    private final ViagemStatus status;
    private final AdminMotoristaSummaryResponse motorista;
    private final int quantidadeParadas;
    private final AdminViagemManifestStatusResponse manifesto;

    public AdminViagemSummaryResponse(Long id,
                                      Long rotaBaseId,
                                      String rotaNome,
                                      LocalDate dataOperacao,
                                      LocalTime horarioPartida,
                                      ViagemStatus status,
                                      AdminMotoristaSummaryResponse motorista,
                                      int quantidadeParadas) {
        this(id, rotaBaseId, null, rotaNome, null, null, dataOperacao, horarioPartida, status, motorista,
                quantidadeParadas, AdminViagemManifestStatusResponse.notApplicable());
    }

    public AdminViagemSummaryResponse(Long id,
                                      Long rotaBaseId,
                                      Long servicoId,
                                      String rotaNome,
                                      String servicoNome,
                                      TipoOperacaoServico tipoOperacao,
                                      LocalDate dataOperacao,
                                      LocalTime horarioPartida,
                                      ViagemStatus status,
                                      AdminMotoristaSummaryResponse motorista,
                                      int quantidadeParadas) {
        this(id, rotaBaseId, servicoId, rotaNome, servicoNome, tipoOperacao, dataOperacao, horarioPartida,
                status, motorista, quantidadeParadas, AdminViagemManifestStatusResponse.notApplicable());
    }

    public AdminViagemSummaryResponse(Long id,
                                      Long rotaBaseId,
                                      Long servicoId,
                                      String rotaNome,
                                      String servicoNome,
                                      TipoOperacaoServico tipoOperacao,
                                      LocalDate dataOperacao,
                                      LocalTime horarioPartida,
                                      ViagemStatus status,
                                      AdminMotoristaSummaryResponse motorista,
                                      int quantidadeParadas,
                                      AdminViagemManifestStatusResponse manifesto) {
        this.id = id;
        this.rotaBaseId = rotaBaseId;
        this.servicoId = servicoId;
        this.rotaNome = rotaNome;
        this.servicoNome = servicoNome;
        this.tipoOperacao = tipoOperacao;
        this.dataOperacao = dataOperacao;
        this.horarioPartida = horarioPartida;
        this.status = status;
        this.motorista = motorista;
        this.quantidadeParadas = quantidadeParadas;
        this.manifesto = manifesto == null ? AdminViagemManifestStatusResponse.notApplicable() : manifesto;
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

    public AdminMotoristaSummaryResponse getMotorista() {
        return motorista;
    }

    public int getQuantidadeParadas() {
        return quantidadeParadas;
    }

    public AdminViagemManifestStatusResponse getManifesto() {
        return manifesto;
    }
}
