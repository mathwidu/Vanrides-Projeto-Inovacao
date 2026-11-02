package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.DriverCompanyLinkRequest;
import com.vanrides.vanrides.model.DriverCompanyLinkRequestStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverCompanyLinkRequestRepository extends JpaRepository<DriverCompanyLinkRequest, Long> {

    Optional<DriverCompanyLinkRequest> findFirstByMotoristaIdAndStatusOrderByRequestedAtDesc(
            Long motoristaId,
            DriverCompanyLinkRequestStatus status
    );

    Optional<DriverCompanyLinkRequest> findFirstByMotoristaIdOrderByRequestedAtDesc(Long motoristaId);

    List<DriverCompanyLinkRequest> findAllByCompanyIdAndStatusOrderByRequestedAtAsc(
            Long companyId,
            DriverCompanyLinkRequestStatus status
    );

    Optional<DriverCompanyLinkRequest> findByIdAndCompanyId(Long id, Long companyId);

    List<DriverCompanyLinkRequest> findAllByMotoristaIdAndStatus(
            Long motoristaId,
            DriverCompanyLinkRequestStatus status
    );
}
