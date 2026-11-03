package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.model.SupportTicket;
import com.vanrides.vanrides.model.SupportTicketStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface SupportTicketRepository extends JpaRepository<SupportTicket, Long> {

    List<SupportTicket> findAllByCompanyIdOrderByUpdatedAtDesc(Long companyId);

    List<SupportTicket> findAllByCompanyIdAndStatusOrderByUpdatedAtDesc(Long companyId, SupportTicketStatus status);

    Optional<SupportTicket> findByIdAndCompanyId(Long id, Long companyId);

    List<SupportTicket> findAllByCompanyIdAndCreatedByRoleAndCreatedByUserIdOrderByUpdatedAtDesc(
            Long companyId,
            UserRole createdByRole,
            Long createdByUserId
    );

    Optional<SupportTicket> findByIdAndCompanyIdAndCreatedByRoleAndCreatedByUserId(
            Long id,
            Long companyId,
            UserRole createdByRole,
            Long createdByUserId
    );
}
