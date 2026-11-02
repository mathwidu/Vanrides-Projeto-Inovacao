package com.vanrides.vanrides.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.time.LocalDate;

@JsonIgnoreProperties(ignoreUnknown = true)
public record AccountProfileUpdateRequest(
        String nome,
        String telefone,
        LocalDate dataNascimento,
        String fotoPerfilUrl
) {
}
