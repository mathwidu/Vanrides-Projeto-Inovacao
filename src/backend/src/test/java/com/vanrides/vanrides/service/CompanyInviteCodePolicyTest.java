package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class CompanyInviteCodePolicyTest {

    private final CompanyInviteCodePolicy policy = new CompanyInviteCodePolicy();

    @Test
    void normalizesInviteCodeInput() {
        assertThat(policy.normalizeCode(" inv-2026-abcd ")).isEqualTo("INV-2026-ABCD");
        assertThat(policy.normalizeCode(null)).isEmpty();
    }

    @Test
    void normalizesMaxUses() {
        assertThat(policy.normalizeMaxUses(null)).isNull();
        assertThat(policy.normalizeMaxUses(0)).isNull();
        assertThat(policy.normalizeMaxUses(-3)).isNull();
        assertThat(policy.normalizeMaxUses(4)).isEqualTo(4);
    }

    @Test
    void resolvesEffectiveStatusForInviteLifecycle() {
        assertThat(policy.effectiveStatus(invite(CompanyInviteCodeStatus.CANCELLED, null, null, 0)))
                .isEqualTo(CompanyInviteCodeStatus.CANCELLED);
        assertThat(policy.effectiveStatus(invite(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now().minusMinutes(1), null, 0)))
                .isEqualTo(CompanyInviteCodeStatus.EXPIRED);
        assertThat(policy.effectiveStatus(invite(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now().plusDays(1), 2, 2)))
                .isEqualTo(CompanyInviteCodeStatus.USED_UP);
        assertThat(policy.effectiveStatus(invite(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now().plusDays(1), 2, 1)))
                .isEqualTo(CompanyInviteCodeStatus.ACTIVE);
    }

    @Test
    void marksOnlyActiveEffectiveStatusAsUsable() {
        assertThat(policy.isUsable(invite(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now().plusDays(1), 1, 0)))
                .isTrue();
        assertThat(policy.isUsable(invite(CompanyInviteCodeStatus.ACTIVE, OffsetDateTime.now().minusMinutes(1), null, 0)))
                .isFalse();
    }

    private CompanyInviteCode invite(CompanyInviteCodeStatus status,
                                     OffsetDateTime expiresAt,
                                     Integer maxUses,
                                     int useCount) {
        CompanyInviteCode inviteCode = new CompanyInviteCode();
        inviteCode.setStatus(status);
        inviteCode.setExpiresAt(expiresAt);
        inviteCode.setMaxUses(maxUses);
        inviteCode.setUseCount(useCount);
        return inviteCode;
    }
}
