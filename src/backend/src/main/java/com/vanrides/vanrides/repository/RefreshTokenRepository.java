package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.RefreshToken;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;

import java.util.Optional;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {
    Optional<RefreshToken> findByTokenHashAndRevokedFalse(String tokenHash);

    @Modifying
    @Query("""
            update RefreshToken token
               set token.revoked = true
             where token.userId = :userId
               and token.userRole = :userRole
               and token.revoked = false
            """)
    int revokeAllByUserIdAndUserRole(Long userId, String userRole);
}
