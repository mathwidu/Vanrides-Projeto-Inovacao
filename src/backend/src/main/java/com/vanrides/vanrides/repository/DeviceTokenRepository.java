package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.DeviceToken;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

public interface DeviceTokenRepository extends JpaRepository<DeviceToken, Long> {

    @Modifying
    @Query(value = """
            INSERT INTO device_token (
                token_hash, token_value, platform, user_id, user_role, user_email,
                company_id, active, last_registered_at, invalidated_at,
                created_at, updated_at
            ) VALUES (
                :tokenHash, :tokenValue, :platform, :userId, :userRole, :userEmail,
                :companyId, TRUE, :registeredAt, NULL, :registeredAt, :registeredAt
            )
            ON CONFLICT ON CONSTRAINT uk_device_token_hash DO NOTHING
            """, nativeQuery = true)
    int insertRegistrationIfAbsent(
            @Param("tokenHash") String tokenHash,
            @Param("tokenValue") String tokenValue,
            @Param("platform") String platform,
            @Param("userId") Long userId,
            @Param("userRole") String userRole,
            @Param("userEmail") String userEmail,
            @Param("companyId") Long companyId,
            @Param("registeredAt") Instant registeredAt);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT token FROM DeviceToken token WHERE token.tokenHash = :tokenHash")
    Optional<DeviceToken> findByTokenHashForUpdate(@Param("tokenHash") String tokenHash);

    Optional<DeviceToken> findByTokenHashAndUserIdAndUserRole(String tokenHash, Long userId, String userRole);

    List<DeviceToken> findAllByUserIdAndUserRoleAndActiveTrue(Long userId, String userRole);
}
