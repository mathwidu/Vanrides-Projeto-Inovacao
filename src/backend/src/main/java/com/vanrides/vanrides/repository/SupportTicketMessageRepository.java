package com.vanrides.vanrides.repository;

import com.vanrides.vanrides.model.SupportTicketMessage;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SupportTicketMessageRepository extends JpaRepository<SupportTicketMessage, Long> {

    List<SupportTicketMessage> findAllByTicketIdOrderByCreatedAtAsc(Long ticketId);
}
