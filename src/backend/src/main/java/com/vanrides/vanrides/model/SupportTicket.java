package com.vanrides.vanrides.model;

import com.vanrides.vanrides.auth.UserRole;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "support_ticket", indexes = {
        @Index(name = "idx_support_ticket_company_id", columnList = "company_id"),
        @Index(name = "idx_support_ticket_company_status", columnList = "company_id,status"),
        @Index(name = "idx_support_ticket_created_by", columnList = "company_id,created_by_role,created_by_user_id"),
        @Index(name = "idx_support_ticket_updated_at", columnList = "updated_at")
})
public class SupportTicket {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_passageiro_id")
    private Passageiro createdByPassenger;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "created_by_guardian_id")
    private Guardian createdByGuardian;

    @Enumerated(EnumType.STRING)
    @Column(name = "created_by_role", nullable = false, length = 32)
    private UserRole createdByRole;

    @Column(name = "created_by_user_id", nullable = false)
    private Long createdByUserId;

    @Column(name = "created_by_name", nullable = false, length = 160)
    private String createdByName;

    @Column(nullable = false, length = 64)
    private String category;

    @Column(nullable = false, length = 160)
    private String subject;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private SupportTicketStatus status;

    @Column(name = "created_at", nullable = false)
    private OffsetDateTime createdAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "resolved_at")
    private OffsetDateTime resolvedAt;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        if (status == null) {
            status = SupportTicketStatus.OPEN;
        }
        if (createdAt == null) {
            createdAt = now;
        }
        updatedAt = now;
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public void setId(Long id) {
        this.id = id;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public Passageiro getCreatedByPassenger() {
        return createdByPassenger;
    }

    public void setCreatedByPassenger(Passageiro createdByPassenger) {
        this.createdByPassenger = createdByPassenger;
    }

    public Guardian getCreatedByGuardian() {
        return createdByGuardian;
    }

    public void setCreatedByGuardian(Guardian createdByGuardian) {
        this.createdByGuardian = createdByGuardian;
    }

    public UserRole getCreatedByRole() {
        return createdByRole;
    }

    public void setCreatedByRole(UserRole createdByRole) {
        this.createdByRole = createdByRole;
    }

    public Long getCreatedByUserId() {
        return createdByUserId;
    }

    public void setCreatedByUserId(Long createdByUserId) {
        this.createdByUserId = createdByUserId;
    }

    public String getCreatedByName() {
        return createdByName;
    }

    public void setCreatedByName(String createdByName) {
        this.createdByName = createdByName;
    }

    public String getCategory() {
        return category;
    }

    public void setCategory(String category) {
        this.category = category;
    }

    public String getSubject() {
        return subject;
    }

    public void setSubject(String subject) {
        this.subject = subject;
    }

    public SupportTicketStatus getStatus() {
        return status;
    }

    public void setStatus(SupportTicketStatus status) {
        this.status = status;
    }

    public OffsetDateTime getCreatedAt() {
        return createdAt;
    }

    public void setCreatedAt(OffsetDateTime createdAt) {
        this.createdAt = createdAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public void setUpdatedAt(OffsetDateTime updatedAt) {
        this.updatedAt = updatedAt;
    }

    public OffsetDateTime getResolvedAt() {
        return resolvedAt;
    }

    public void setResolvedAt(OffsetDateTime resolvedAt) {
        this.resolvedAt = resolvedAt;
    }
}
