package com.vanrides.vanrides.model;

import com.vanrides.vanrides.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import org.hibernate.annotations.Immutable;

import java.time.OffsetDateTime;

@Entity
@Immutable
@Table(name = "login_identity")
public class LoginIdentity {

    @Id
    @Column(name = "normalized_email", nullable = false, length = 254, updatable = false)
    private String normalizedEmail;

    @Enumerated(EnumType.STRING)
    @Column(name = "user_role", nullable = false, length = 32, updatable = false)
    private UserRole userRole;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Column(name = "created_at", nullable = false, updatable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false, updatable = false)
    private OffsetDateTime updatedAt;

    protected LoginIdentity() {
    }

    public String getNormalizedEmail() {
        return normalizedEmail;
    }

    public UserRole getUserRole() {
        return userRole;
    }

    public Long getUserId() {
        return userId;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }
}
