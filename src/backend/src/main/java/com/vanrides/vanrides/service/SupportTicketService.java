package com.vanrides.vanrides.service;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.SupportTicketCreateRequest;
import com.vanrides.vanrides.dto.SupportTicketMessageCreateRequest;
import com.vanrides.vanrides.dto.SupportTicketMessageResponse;
import com.vanrides.vanrides.dto.SupportTicketResponse;
import com.vanrides.vanrides.dto.SupportTicketStatusUpdateRequest;
import com.vanrides.vanrides.dto.SupportTicketSummaryResponse;
import com.vanrides.vanrides.model.Guardian;
import com.vanrides.vanrides.model.Passageiro;
import com.vanrides.vanrides.model.SupportTicket;
import com.vanrides.vanrides.model.SupportTicketMessage;
import com.vanrides.vanrides.model.SupportTicketStatus;
import com.vanrides.vanrides.repository.GuardianRepository;
import com.vanrides.vanrides.repository.PassageiroRepository;
import com.vanrides.vanrides.repository.SupportTicketMessageRepository;
import com.vanrides.vanrides.repository.SupportTicketRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Locale;

@Service
public class SupportTicketService {

    @Autowired
    private SupportTicketRepository supportTicketRepository;

    @Autowired
    private SupportTicketMessageRepository supportTicketMessageRepository;

    @Autowired
    private PassageiroRepository passageiroRepository;

    @Autowired
    private GuardianRepository guardianRepository;

    public SupportTicketResponse createTicket(UserRole role,
                                              Long userId,
                                              Long companyId,
                                              SupportTicketCreateRequest request) {
        Actor actor = resolveTicketCreator(role, userId, companyId);
        String category = requiredText(request != null ? request.category() : null, "category", 64)
                .toUpperCase(Locale.ROOT);
        String subject = requiredText(request != null ? request.subject() : null, "subject", 160);
        String messageText = requiredText(request != null ? request.message() : null, "message", 4000);

        SupportTicket ticket = new SupportTicket();
        ticket.setCompany(actor.company());
        ticket.setCreatedByRole(role);
        ticket.setCreatedByUserId(userId);
        ticket.setCreatedByName(actor.name());
        ticket.setCategory(category);
        ticket.setSubject(subject);
        ticket.setStatus(SupportTicketStatus.OPEN);
        if (role == UserRole.PASSENGER) {
            ticket.setCreatedByPassenger(actor.passenger());
        } else {
            ticket.setCreatedByGuardian(actor.guardian());
        }

        SupportTicket savedTicket = supportTicketRepository.save(ticket);
        SupportTicketMessage message = saveMessage(savedTicket, role, userId, actor.name(), messageText);
        return toResponse(savedTicket, List.of(message));
    }

    public List<SupportTicketSummaryResponse> listOwnTickets(UserRole role, Long userId, Long companyId) {
        requireCustomerRole(role);
        return supportTicketRepository
                .findAllByCompanyIdAndCreatedByRoleAndCreatedByUserIdOrderByUpdatedAtDesc(companyId, role, userId)
                .stream()
                .map(this::toSummary)
                .toList();
    }

    public SupportTicketResponse getOwnTicket(Long ticketId, UserRole role, Long userId, Long companyId) {
        requireCustomerRole(role);
        SupportTicket ticket = supportTicketRepository
                .findByIdAndCompanyIdAndCreatedByRoleAndCreatedByUserId(ticketId, companyId, role, userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado."));
        return toResponse(ticket, supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    public List<SupportTicketSummaryResponse> listCompanyTickets(Long companyId, String status) {
        SupportTicketStatus parsedStatus = parseOptionalStatus(status);
        List<SupportTicket> tickets = parsedStatus == null
                ? supportTicketRepository.findAllByCompanyIdOrderByUpdatedAtDesc(companyId)
                : supportTicketRepository.findAllByCompanyIdAndStatusOrderByUpdatedAtDesc(companyId, parsedStatus);
        return tickets.stream().map(this::toSummary).toList();
    }

    public SupportTicketResponse getCompanyTicket(Long ticketId, Long companyId) {
        SupportTicket ticket = getCompanyTicketOrThrow(ticketId, companyId);
        return toResponse(ticket, supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(ticket.getId()));
    }

    public SupportTicketResponse replyAsCompanyAdmin(Long ticketId,
                                                     Long companyId,
                                                     Long adminId,
                                                     SupportTicketMessageCreateRequest request) {
        String messageText = requiredText(request != null ? request.message() : null, "message", 4000);
        SupportTicket ticket = getCompanyTicketOrThrow(ticketId, companyId);
        SupportTicketStatus targetStatus = parseOptionalStatus(request != null ? request.status() : null);
        if (targetStatus == null && ticket.getStatus() == SupportTicketStatus.OPEN) {
            targetStatus = SupportTicketStatus.IN_PROGRESS;
        }
        if (targetStatus != null) {
            applyStatus(ticket, targetStatus);
        }
        SupportTicket savedTicket = supportTicketRepository.save(ticket);
        SupportTicketMessage message = saveMessage(
                savedTicket,
                UserRole.COMPANY_ADMIN,
                adminId,
                "Administrador",
                messageText
        );
        return toResponse(savedTicket, List.of(message));
    }

    public SupportTicketResponse updateCompanyTicketStatus(Long ticketId,
                                                           Long companyId,
                                                           SupportTicketStatusUpdateRequest request) {
        SupportTicketStatus status = parseRequiredStatus(request != null ? request.status() : null);
        SupportTicket ticket = getCompanyTicketOrThrow(ticketId, companyId);
        applyStatus(ticket, status);
        SupportTicket savedTicket = supportTicketRepository.save(ticket);
        return toResponse(savedTicket, supportTicketMessageRepository.findAllByTicketIdOrderByCreatedAtAsc(savedTicket.getId()));
    }

    private Actor resolveTicketCreator(UserRole role, Long userId, Long companyId) {
        requireCustomerRole(role);
        if (role == UserRole.PASSENGER) {
            Passageiro passageiro = passageiroRepository.findByIdAndCompanyId(userId, companyId)
                    .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Passageiro não encontrado na empresa atual."));
            return new Actor(passageiro.getCompany(), passageiro, null, passageiro.getNome());
        }
        Guardian guardian = guardianRepository.findByIdAndCompanyId(userId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Responsável não encontrado na empresa atual."));
        return new Actor(guardian.getCompany(), null, guardian, guardian.getNome());
    }

    private void requireCustomerRole(UserRole role) {
        if (role != UserRole.PASSENGER && role != UserRole.GUARDIAN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Papel sem permissão para esta operação.");
        }
    }

    private SupportTicket getCompanyTicketOrThrow(Long ticketId, Long companyId) {
        return supportTicketRepository.findByIdAndCompanyId(ticketId, companyId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Ticket não encontrado na empresa atual."));
    }

    private SupportTicketMessage saveMessage(SupportTicket ticket,
                                             UserRole authorRole,
                                             Long authorUserId,
                                             String authorName,
                                             String messageText) {
        SupportTicketMessage message = new SupportTicketMessage();
        message.setTicket(ticket);
        message.setAuthorRole(authorRole);
        message.setAuthorUserId(authorUserId);
        message.setAuthorName(authorName);
        message.setMessage(messageText);
        return supportTicketMessageRepository.save(message);
    }

    private void applyStatus(SupportTicket ticket, SupportTicketStatus status) {
        ticket.setStatus(status);
        if (status == SupportTicketStatus.RESOLVED && ticket.getResolvedAt() == null) {
            ticket.setResolvedAt(OffsetDateTime.now());
        }
        if (status != SupportTicketStatus.RESOLVED) {
            ticket.setResolvedAt(null);
        }
    }

    private String requiredText(String value, String fieldName, int maxLength) {
        String trimmed = value != null ? value.trim() : "";
        if (trimmed.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " é obrigatório.");
        }
        if (trimmed.length() > maxLength) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, fieldName + " excede o limite de caracteres.");
        }
        return trimmed;
    }

    private SupportTicketStatus parseOptionalStatus(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return parseRequiredStatus(value);
    }

    private SupportTicketStatus parseRequiredStatus(String value) {
        String normalized = value != null ? value.trim().toUpperCase(Locale.ROOT) : "";
        if (normalized.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "status é obrigatório.");
        }
        try {
            return SupportTicketStatus.valueOf(normalized);
        } catch (IllegalArgumentException exception) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Status de ticket inválido.");
        }
    }

    private SupportTicketSummaryResponse toSummary(SupportTicket ticket) {
        return new SupportTicketSummaryResponse(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getCategory(),
                ticket.getSubject(),
                ticket.getCreatedByRole().name(),
                ticket.getCreatedByUserId(),
                ticket.getCreatedByName(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt()
        );
    }

    private SupportTicketResponse toResponse(SupportTicket ticket, List<SupportTicketMessage> messages) {
        return new SupportTicketResponse(
                ticket.getId(),
                ticket.getStatus().name(),
                ticket.getCategory(),
                ticket.getSubject(),
                ticket.getCreatedByRole().name(),
                ticket.getCreatedByUserId(),
                ticket.getCreatedByName(),
                ticket.getCreatedAt(),
                ticket.getUpdatedAt(),
                ticket.getResolvedAt(),
                messages.stream().map(this::toMessageResponse).toList()
        );
    }

    private SupportTicketMessageResponse toMessageResponse(SupportTicketMessage message) {
        return new SupportTicketMessageResponse(
                message.getId(),
                message.getAuthorRole().name(),
                message.getAuthorUserId(),
                message.getAuthorName(),
                message.getMessage(),
                message.getCreatedAt()
        );
    }

    private record Actor(com.vanrides.vanrides.model.Company company,
                         Passageiro passenger,
                         Guardian guardian,
                         String name) {
    }
}
