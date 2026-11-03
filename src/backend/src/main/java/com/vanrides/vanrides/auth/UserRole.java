package com.vanrides.vanrides.auth;

import java.text.Normalizer;
import java.util.Locale;

public enum UserRole {
    COMPANY_ADMIN("Administrador"),
    DRIVER("Motorista"),
    PASSENGER("Passageiro"),
    GUARDIAN("Responsável");

    private final String displayName;

    UserRole(String displayName) {
        this.displayName = displayName;
    }

    public String claimValue() {
        return name();
    }

    public String displayName() {
        return displayName;
    }

    public boolean isDriver() {
        return this == DRIVER;
    }

    public boolean isPassenger() {
        return this == PASSENGER;
    }

    public boolean isCompanyAdmin() {
        return this == COMPANY_ADMIN;
    }

    public boolean isGuardian() {
        return this == GUARDIAN;
    }

    public static UserRole fromValue(String rawValue) {
        if (rawValue == null || rawValue.isBlank()) {
            throw new IllegalArgumentException("Papel do usuário ausente");
        }

        return switch (normalize(rawValue)) {
            case "COMPANY_ADMIN", "ADMIN", "EMPRESA_ADMIN" -> COMPANY_ADMIN;
            case "DRIVER", "MOTORISTA" -> DRIVER;
            case "PASSENGER", "PASSAGEIRO" -> PASSENGER;
            case "GUARDIAN", "RESPONSAVEL" -> GUARDIAN;
            default -> throw new IllegalArgumentException("Papel do usuário inválido: " + rawValue);
        };
    }

    private static String normalize(String value) {
        String normalized = Normalizer.normalize(value, Normalizer.Form.NFD)
                .replaceAll("\\p{M}", "");
        return normalized.trim()
                .replace('-', '_')
                .replace(' ', '_')
                .toUpperCase(Locale.ROOT);
    }
}
