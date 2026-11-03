package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import org.springframework.stereotype.Component;

import java.time.OffsetDateTime;
import java.util.Locale;

@Component
public class CompanyInviteCodePolicy {

    public Integer normalizeMaxUses(Integer maxUses) {
        if (maxUses == null || maxUses <= 0) {
            return null;
        }
        return maxUses;
    }

    public String normalizeCode(String rawCode) {
        if (rawCode == null) {
            return "";
        }
        return rawCode.trim().toUpperCase(Locale.ROOT);
    }

    public boolean isUsable(CompanyInviteCode inviteCode) {
        return effectiveStatus(inviteCode) == CompanyInviteCodeStatus.ACTIVE;
    }

    public CompanyInviteCodeStatus effectiveStatus(CompanyInviteCode inviteCode) {
        if (inviteCode.getStatus() == CompanyInviteCodeStatus.CANCELLED) {
            return CompanyInviteCodeStatus.CANCELLED;
        }
        if (inviteCode.getExpiresAt() != null && inviteCode.getExpiresAt().isBefore(OffsetDateTime.now())) {
            return CompanyInviteCodeStatus.EXPIRED;
        }
        if (inviteCode.getMaxUses() != null && inviteCode.getUseCount() >= inviteCode.getMaxUses()) {
            return CompanyInviteCodeStatus.USED_UP;
        }
        return CompanyInviteCodeStatus.ACTIVE;
    }
}
