package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record PublicDriverRegistrationRequest(
        String nome,
        String email,
        String telefone,
        String senha,
        LocalDate dataNascimento
) {
}
