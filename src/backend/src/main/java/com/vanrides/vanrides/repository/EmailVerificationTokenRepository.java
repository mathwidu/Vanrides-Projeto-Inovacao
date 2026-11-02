package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.EmailVerificationToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface EmailVerificationTokenRepository extends JpaRepository<EmailVerificationToken, Long> {

    @Query("""
            SELECT token.id AS id,
                   token.userRole AS userRole,
                   token.userId AS userId,
                   token.emailSnapshot AS emailSnapshot,
                   token.tokenHash AS tokenHash
            FROM EmailVerificationToken token
            WHERE token.tokenHash = :tokenHash
              AND token.consumedAt IS NULL
            """)
    Optional<ConfirmationCandidate> findConfirmationCandidateByTokenHash(
            @Param("tokenHash") String tokenHash);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT token
            FROM EmailVerificationToken token
            WHERE token.id = :tokenId
              AND token.consumedAt IS NULL
            """)
    Optional<EmailVerificationToken> findUnconsumedByIdForUpdate(
            @Param("tokenId") Long tokenId);

    List<EmailVerificationToken> findAllByUserRoleAndUserIdAndConsumedAtIsNull(String userRole, Long userId);

    interface ConfirmationCandidate {
        Long getId();

        String getUserRole();

        Long getUserId();

        String getEmailSnapshot();

        String getTokenHash();
    }
}
