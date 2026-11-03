package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicPassengerRegistrationRequest(
        String nome,
        String email,
        String telefone,
        String senha,
        String endereco,
        String bairro,
        String cidade,
        String estado,
        String cep,
        Double latitude,
        Double longitude,
        LocalDate dataNascimento
) {
}
