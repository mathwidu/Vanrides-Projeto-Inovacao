package com.vanrides.vanrides.model;

import java.time.OffsetDateTime;

public interface EmailVerifiableAccount {

    boolean isEmailVerified();

    void setEmailVerified(boolean emailVerified);

    OffsetDateTime getEmailVerifiedAt();

    void setEmailVerifiedAt(OffsetDateTime emailVerifiedAt);

    OffsetDateTime getEmailVerificationLastSentAt();

    void setEmailVerificationLastSentAt(OffsetDateTime emailVerificationLastSentAt);
}
