package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.CompanyInviteCodeUsage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface CompanyInviteCodeUsageRepository extends JpaRepository<CompanyInviteCodeUsage, Long> {

    List<CompanyInviteCodeUsage> findAllByInviteCodeIdOrderByUsedAtDesc(Long inviteCodeId);
}
