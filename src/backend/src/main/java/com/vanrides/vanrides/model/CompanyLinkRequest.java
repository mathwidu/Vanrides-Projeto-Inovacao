package com.vanrides.vanrides.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "company_link_request")
public class CompanyLinkRequest {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "company_id", nullable = false)
    private Company company;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyLinkRequestStatus status;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private CompanyLinkRequestSource source;

    @Column(name = "requested_at", nullable = false)
    private OffsetDateTime requestedAt;

    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    @Column(name = "reviewed_at")
    private OffsetDateTime reviewedAt;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "reviewed_by_company_admin_id")
    private CompanyAdmin reviewedBy;

    @Column(name = "review_notes", length = 255)
    private String reviewNotes;

    @PrePersist
    void onCreate() {
        OffsetDateTime now = OffsetDateTime.now();
        requestedAt = now;
        updatedAt = now;
        if (status == null) {
            status = CompanyLinkRequestStatus.PENDING;
        }
    }

    @PreUpdate
    void onUpdate() {
        updatedAt = OffsetDateTime.now();
    }

    public Long getId() {
        return id;
    }

    public Passageiro getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Passageiro passageiro) {
        this.passageiro = passageiro;
    }

    public Company getCompany() {
        return company;
    }

    public void setCompany(Company company) {
        this.company = company;
    }

    public CompanyLinkRequestStatus getStatus() {
        return status;
    }

    public void setStatus(CompanyLinkRequestStatus status) {
        this.status = status;
    }

    public CompanyLinkRequestSource getSource() {
        return source;
    }

    public void setSource(CompanyLinkRequestSource source) {
        this.source = source;
    }

    public OffsetDateTime getRequestedAt() {
        return requestedAt;
    }

    public OffsetDateTime getUpdatedAt() {
        return updatedAt;
    }

    public OffsetDateTime getReviewedAt() {
        return reviewedAt;
    }

    public void setReviewedAt(OffsetDateTime reviewedAt) {
        this.reviewedAt = reviewedAt;
    }

    public CompanyAdmin getReviewedBy() {
        return reviewedBy;
    }

    public void setReviewedBy(CompanyAdmin reviewedBy) {
        this.reviewedBy = reviewedBy;
    }

    public String getReviewNotes() {
        return reviewNotes;
    }

    public void setReviewNotes(String reviewNotes) {
        this.reviewNotes = reviewNotes;
    }
}
