package com.vanrides.vanrides.dto;

public record AdminViagemManifestPassengerResponse(
        Long passageiroId,
        String nome,
        boolean menorDeIdade,
        Integer ordemAtual,
        Integer ordemNova
) {
}
