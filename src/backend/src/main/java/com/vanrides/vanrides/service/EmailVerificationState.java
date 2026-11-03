package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.EmailVerifiableAccount;

import java.time.OffsetDateTime;

public final class EmailVerificationState {

    private EmailVerificationState() {
    }

    public static void markPending(EmailVerifiableAccount account) {
        account.setEmailVerified(false);
        account.setEmailVerifiedAt(null);
    }

    public static void markEmailSent(EmailVerifiableAccount account, OffsetDateTime sentAt) {
        account.setEmailVerificationLastSentAt(sentAt);
    }

    public static void markVerified(EmailVerifiableAccount account, OffsetDateTime verifiedAt) {
        account.setEmailVerified(true);
        account.setEmailVerifiedAt(verifiedAt);
    }
}
