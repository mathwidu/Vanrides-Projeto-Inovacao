package com.vanrides.vanrides.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;

import java.time.Instant;

@Entity
@Table(
        name = "device_token",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_device_token_hash", columnNames = "token_hash")
        },
        indexes = {
                @Index(name = "idx_device_token_user", columnList = "user_role,user_id"),
                @Index(name = "idx_device_token_company_id", columnList = "company_id"),
                @Index(name = "idx_device_token_active", columnList = "active")
        }
)
public class DeviceToken {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "token_hash", nullable = false, length = 128)
    private String tokenHash;

    @Column(name = "token_value", nullable = false, length = 1024)
    private String tokenValue;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private DeviceTokenPlatform platform;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "user_role", nullable = false, length = 32)
    private String userRole;

    @Column(name = "user_email", length = 255)
    private String userEmail;

    @Column(name = "company_id")
    private Long companyId;

    @Column(nullable = false)
    private boolean active = true;

    @Column(name = "last_registered_at", nullable = false)
    private Instant lastRegisteredAt;

    @Column(name = "invalidated_at")
    private Instant invalidatedAt;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @PrePersist
    public void prePersist() {
        Instant now = Instant.now();
        createdAt = now;
        updatedAt = now;
        if (lastRegisteredAt == null) {
            lastRegisteredAt = now;
        }
    }

    @PreUpdate
    public void preUpdate() {
        updatedAt = Instant.now();
    }

    public Long getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public void setTokenHash(String tokenHash) {
        this.tokenHash = tokenHash;
    }

    public String getTokenValue() {
        return tokenValue;
    }

    public void setTokenValue(String tokenValue) {
        this.tokenValue = tokenValue;
    }

    public DeviceTokenPlatform getPlatform() {
        return platform;
    }

    public void setPlatform(DeviceTokenPlatform platform) {
        this.platform = platform;
    }

    public Long getUserId() {
        return userId;
    }

    public void setUserId(Long userId) {
        this.userId = userId;
    }

    public String getUserRole() {
        return userRole;
    }

    public void setUserRole(String userRole) {
        this.userRole = userRole;
    }

    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
    }

    public Long getCompanyId() {
        return companyId;
    }

    public void setCompanyId(Long companyId) {
        this.companyId = companyId;
    }

    public boolean isActive() {
        return active;
    }

    public void setActive(boolean active) {
        this.active = active;
    }

    public Instant getLastRegisteredAt() {
        return lastRegisteredAt;
    }

    public void setLastRegisteredAt(Instant lastRegisteredAt) {
        this.lastRegisteredAt = lastRegisteredAt;
    }

    public Instant getInvalidatedAt() {
        return invalidatedAt;
    }

    public void setInvalidatedAt(Instant invalidatedAt) {
        this.invalidatedAt = invalidatedAt;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }

    public Instant getUpdatedAt() {
        return updatedAt;
    }
}
