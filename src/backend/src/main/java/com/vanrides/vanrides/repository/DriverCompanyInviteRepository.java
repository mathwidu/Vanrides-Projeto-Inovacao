package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.DriverCompanyInvite;
import com.vanrides.vanrides.model.DriverCompanyInviteStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface DriverCompanyInviteRepository extends JpaRepository<DriverCompanyInvite, Long> {

    Optional<DriverCompanyInvite> findByTokenHash(String tokenHash);

    Optional<DriverCompanyInvite> findByIdAndCompanyId(Long id, Long companyId);

    List<DriverCompanyInvite> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<DriverCompanyInvite> findAllByStatusAndExpiresAtBefore(
            DriverCompanyInviteStatus status,
            OffsetDateTime expiresAt
    );
}
