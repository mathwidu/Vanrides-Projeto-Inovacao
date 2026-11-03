package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.Motorista;
import org.junit.jupiter.api.Test;

import java.time.OffsetDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class EmailVerificationStateTest {

    @Test
    void shouldPreserveLastSentAtWhenMarkingAccountAsVerified() {
        Motorista account = new Motorista();
        OffsetDateTime lastSentAt = OffsetDateTime.parse("2026-07-17T10:15:30Z");
        OffsetDateTime verifiedAt = OffsetDateTime.parse("2026-07-18T11:20:45Z");
        account.setEmailVerificationLastSentAt(lastSentAt);

        EmailVerificationState.markVerified(account, verifiedAt);

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(account.getEmailVerificationLastSentAt()).isEqualTo(lastSentAt);
    }

    @Test
    void shouldKeepLastSentAtNullWhenVerifyingWithoutSendingEmail() {
        Motorista account = new Motorista();
        OffsetDateTime verifiedAt = OffsetDateTime.parse("2026-07-18T11:20:45Z");

        EmailVerificationState.markVerified(account, verifiedAt);

        assertThat(account.isEmailVerified()).isTrue();
        assertThat(account.getEmailVerifiedAt()).isEqualTo(verifiedAt);
        assertThat(account.getEmailVerificationLastSentAt()).isNull();
    }
}
