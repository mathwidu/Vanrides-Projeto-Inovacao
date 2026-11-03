package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.DriverCompanyMembership;
import com.vanrides.vanrides.model.DriverCompanyMembershipStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface DriverCompanyMembershipRepository extends JpaRepository<DriverCompanyMembership, Long> {

    Optional<DriverCompanyMembership> findFirstByMotoristaIdAndStatusOrderByCreatedAtDesc(
            Long motoristaId,
            DriverCompanyMembershipStatus status
    );

    List<DriverCompanyMembership> findAllByCompanyIdAndStatusOrderByCreatedAtDesc(
            Long companyId,
            DriverCompanyMembershipStatus status
    );
}
