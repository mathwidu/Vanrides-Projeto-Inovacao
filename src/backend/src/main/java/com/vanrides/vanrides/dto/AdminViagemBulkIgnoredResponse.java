package com.vanrides.vanrides.dto;

public class AdminViagemBulkIgnoredResponse {

    private final Long servicoId;
    private final String servicoNome;
    private final String motivo;
    private final String mensagem;

    public AdminViagemBulkIgnoredResponse(Long servicoId,
                                          String servicoNome,
                                          String motivo,
                                          String mensagem) {
        this.servicoId = servicoId;
        this.servicoNome = servicoNome;
        this.motivo = motivo;
        this.mensagem = mensagem;
    }

    public Long getServicoId() {
        return servicoId;
    }

    public String getServicoNome() {
        return servicoNome;
    }

    public String getMotivo() {
        return motivo;
    }

    public String getMensagem() {
        return mensagem;
    }
}
