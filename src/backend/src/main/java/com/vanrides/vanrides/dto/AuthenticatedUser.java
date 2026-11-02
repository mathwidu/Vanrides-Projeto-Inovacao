package com.vanrides.vanrides.dto;

import com.vanrides.vanrides.auth.UserRole;

public class AuthenticatedUser {
    private final Long userId;
    private final UserRole role;
    private final String email;
    private final String nome;
    private final Long motoristaId;
    private final Long companyId;

    public AuthenticatedUser(
            Long userId,
            String role,
            String email,
            String nome,
            Long motoristaId,
            Long companyId
    ) {
        this(userId, UserRole.fromValue(role), email, nome, motoristaId, companyId);
    }

    public AuthenticatedUser(
            Long userId,
            UserRole role,
            String email,
            String nome,
            Long motoristaId,
            Long companyId
    ) {
        this.userId = userId;
        this.role = role;
        this.email = email;
        this.nome = nome;
        this.motoristaId = motoristaId;
        this.companyId = companyId;
    }

    public Long getUserId() {
        return userId;
    }

    public String getRole() {
        return role.claimValue();
    }

    public UserRole getRoleEnum() {
        return role;
    }

    public String getEmail() {
        return email;
    }

    public String getNome() {
        return nome;
    }

    public Long getMotoristaId() {
        return motoristaId;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public boolean isDriver() {
        return role.isDriver();
    }

    public boolean isPassenger() {
        return role.isPassenger();
    }

    public boolean isCompanyAdmin() {
        return role.isCompanyAdmin();
    }

    public boolean isGuardian() {
        return role.isGuardian();
    }
}
