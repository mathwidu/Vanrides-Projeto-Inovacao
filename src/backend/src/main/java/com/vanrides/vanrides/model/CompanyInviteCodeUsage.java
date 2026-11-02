package com.vanrides.vanrides.model;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

import java.time.OffsetDateTime;

@Entity
@Table(name = "company_invite_code_usage")
public class CompanyInviteCodeUsage {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "invite_code_id", nullable = false)
    private CompanyInviteCode inviteCode;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "passageiro_id", nullable = false)
    private Passageiro passageiro;

    @Column(name = "used_at", nullable = false)
    private OffsetDateTime usedAt;

    @PrePersist
    void onCreate() {
        if (usedAt == null) {
            usedAt = OffsetDateTime.now();
        }
    }

    public Long getId() {
        return id;
    }

    public CompanyInviteCode getInviteCode() {
        return inviteCode;
    }

    public void setInviteCode(CompanyInviteCode inviteCode) {
        this.inviteCode = inviteCode;
    }

    public Passageiro getPassageiro() {
        return passageiro;
    }

    public void setPassageiro(Passageiro passageiro) {
        this.passageiro = passageiro;
    }

    public OffsetDateTime getUsedAt() {
        return usedAt;
    }

    public void setUsedAt(OffsetDateTime usedAt) {
        this.usedAt = usedAt;
    }
}
