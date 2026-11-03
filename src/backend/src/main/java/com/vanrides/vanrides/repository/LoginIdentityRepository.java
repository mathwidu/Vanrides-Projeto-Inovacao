package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.LoginIdentity;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface LoginIdentityRepository extends Repository<LoginIdentity, String> {

    Optional<LoginIdentity> findById(String normalizedEmail);

    @Query(value = """
            SELECT registry.*
            FROM login_identity registry
            WHERE (registry.normalized_email COLLATE "C") =
                  (CAST(:normalizedEmail AS TEXT) COLLATE "C")
              AND (registry.user_role COLLATE "C") =
                  (CAST(:userRole AS TEXT) COLLATE "C")
              AND registry.user_id = :userId
            FOR KEY SHARE
            """, nativeQuery = true)
    Optional<LoginIdentity> findExactOwnerForKeyShare(
            @Param("normalizedEmail") String normalizedEmail,
            @Param("userRole") String userRole,
            @Param("userId") Long userId);
}
