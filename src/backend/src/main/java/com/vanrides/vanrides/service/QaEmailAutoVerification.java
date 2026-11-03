package com.vanrides.vanrides.service;

import com.vanrides.vanrides.model.EmailVerifiableAccount;

import java.time.OffsetDateTime;
import java.time.ZoneOffset;

public final class QaEmailAutoVerification {

    private QaEmailAutoVerification() {
    }

    public static boolean shouldAutoVerify(String email, String configuredDomains) {
        if (email == null || email.isBlank() || configuredDomains == null || configuredDomains.isBlank()) {
            return false;
        }

        String normalizedEmail = email.trim().toLowerCase();
        for (String rawDomain : configuredDomains.split(",")) {
            String domain = rawDomain.trim().toLowerCase().replaceFirst("^@", "");
            if (!domain.isBlank() && normalizedEmail.endsWith("@" + domain)) {
                return true;
            }
        }
        return false;
    }

    public static void markVerified(EmailVerifiableAccount account) {
        EmailVerificationState.markVerified(account, OffsetDateTime.now(ZoneOffset.UTC));
    }
}
