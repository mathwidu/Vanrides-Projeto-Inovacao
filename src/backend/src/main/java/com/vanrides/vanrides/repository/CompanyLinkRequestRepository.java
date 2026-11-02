package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.CompanyLinkRequest;
import com.vanrides.vanrides.model.CompanyLinkRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CompanyLinkRequestRepository extends JpaRepository<CompanyLinkRequest, Long> {

    Optional<CompanyLinkRequest> findFirstByPassageiroIdAndStatusOrderByRequestedAtDesc(Long passageiroId, CompanyLinkRequestStatus status);

    Optional<CompanyLinkRequest> findFirstByPassageiroIdOrderByRequestedAtDesc(Long passageiroId);

    List<CompanyLinkRequest> findAllByCompanyIdAndStatusOrderByRequestedAtAsc(Long companyId, CompanyLinkRequestStatus status);

    Optional<CompanyLinkRequest> findByIdAndCompanyId(Long id, Long companyId);

    List<CompanyLinkRequest> findAllByPassageiroIdAndStatus(Long passageiroId, CompanyLinkRequestStatus status);
}
