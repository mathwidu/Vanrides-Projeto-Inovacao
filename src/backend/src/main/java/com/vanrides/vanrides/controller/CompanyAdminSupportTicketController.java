package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.SupportTicketMessageCreateRequest;
import com.vanrides.vanrides.dto.SupportTicketResponse;
import com.vanrides.vanrides.dto.SupportTicketStatusUpdateRequest;
import com.vanrides.vanrides.dto.SupportTicketSummaryResponse;
import com.vanrides.vanrides.service.SupportTicketService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/company-admins/support/tickets")
public class CompanyAdminSupportTicketController {

    @Autowired
    private SupportTicketService supportTicketService;

    @GetMapping
    public ResponseEntity<List<SupportTicketSummaryResponse>> listCompanyTickets(@RequestParam(required = false) String status,
                                                                                 HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(supportTicketService.listCompanyTickets(getRequiredCompanyId(request), status));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getCompanyTicket(@PathVariable Long id, HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(supportTicketService.getCompanyTicket(id, getRequiredCompanyId(request)));
    }

    @PostMapping("/{id}/responses")
    public ResponseEntity<SupportTicketResponse> replyAsCompanyAdmin(@PathVariable Long id,
                                                                     @RequestBody(required = false) SupportTicketMessageCreateRequest body,
                                                                     HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(
                supportTicketService.replyAsCompanyAdmin(
                        id,
                        getRequiredCompanyId(request),
                        AuthorizationGuard.getRequiredUserId(request),
                        body
                )
        );
    }

    @PostMapping("/{id}/status")
    public ResponseEntity<SupportTicketResponse> updateCompanyTicketStatus(@PathVariable Long id,
                                                                           @RequestBody(required = false) SupportTicketStatusUpdateRequest body,
                                                                           HttpServletRequest request) {
        AuthorizationGuard.requireAnyRole(request, UserRole.COMPANY_ADMIN);
        return ResponseEntity.ok(supportTicketService.updateCompanyTicketStatus(id, getRequiredCompanyId(request), body));
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
