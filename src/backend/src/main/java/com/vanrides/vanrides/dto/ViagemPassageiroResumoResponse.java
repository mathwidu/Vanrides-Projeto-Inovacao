package com.vanrides.vanrides.dto;

public class ViagemPassageiroResumoResponse {

    private final Long id;
    private final String nome;
    private final boolean menorDeIdade;

    public ViagemPassageiroResumoResponse(Long id, String nome, boolean menorDeIdade) {
        this.id = id;
        this.nome = nome;
        this.menorDeIdade = menorDeIdade;
    }

    public Long getId() {
        return id;
    }

    public String getNome() {
        return nome;
    }

    public boolean isMenorDeIdade() {
        return menorDeIdade;
    }
}
