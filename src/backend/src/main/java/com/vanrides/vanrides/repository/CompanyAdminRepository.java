package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.CompanyAdmin;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface CompanyAdminRepository extends JpaRepository<CompanyAdmin, Long> {

    Optional<CompanyAdmin> findByEmail(String email);

    boolean existsByCompanyId(Long companyId);

    Optional<CompanyAdmin> findByCompanyId(Long companyId);
}
