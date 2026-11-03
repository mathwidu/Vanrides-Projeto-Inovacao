package com.vanrides.vanrides.dto;

public class ViagemOperationalAlertResponse {

    private final String tipo;
    private final String severidade;
    private final String titulo;
    private final String mensagem;
    private final Long passageiroId;
    private final Long paradaId;
    private final Integer paradaOrdem;

    public ViagemOperationalAlertResponse(String tipo,
                                          String severidade,
                                          String titulo,
                                          String mensagem,
                                          Long passageiroId,
                                          Long paradaId,
                                          Integer paradaOrdem) {
        this.tipo = tipo;
        this.severidade = severidade;
        this.titulo = titulo;
        this.mensagem = mensagem;
        this.passageiroId = passageiroId;
        this.paradaId = paradaId;
        this.paradaOrdem = paradaOrdem;
    }

    public String getTipo() {
        return tipo;
    }

    public String getSeveridade() {
        return severidade;
    }

    public String getTitulo() {
        return titulo;
    }

    public String getMensagem() {
        return mensagem;
    }

    public Long getPassageiroId() {
        return passageiroId;
    }

    public Long getParadaId() {
        return paradaId;
    }

    public Integer getParadaOrdem() {
        return paradaOrdem;
    }
}
