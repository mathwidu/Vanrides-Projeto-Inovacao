package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.CompanyInviteCode;
import com.vanrides.vanrides.model.CompanyInviteCodeStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

public interface CompanyInviteCodeRepository extends JpaRepository<CompanyInviteCode, Long> {

    Optional<CompanyInviteCode> findByCode(String code);

    Optional<CompanyInviteCode> findByIdAndCompanyId(Long id, Long companyId);

    List<CompanyInviteCode> findAllByCompanyIdOrderByCreatedAtDesc(Long companyId);

    List<CompanyInviteCode> findAllByStatusAndExpiresAtBefore(CompanyInviteCodeStatus status, OffsetDateTime expiresAt);
}
