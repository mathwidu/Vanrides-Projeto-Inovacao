package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.Guardian;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface GuardianRepository extends JpaRepository<Guardian, Long> {

    List<Guardian> findAllByCompanyIdOrderByNomeAsc(Long companyId);

    Optional<Guardian> findByIdAndCompanyId(Long id, Long companyId);

    Optional<Guardian> findByEmailAndCompanyId(String email, Long companyId);

    List<Guardian> findAllByEmail(String email);
}
