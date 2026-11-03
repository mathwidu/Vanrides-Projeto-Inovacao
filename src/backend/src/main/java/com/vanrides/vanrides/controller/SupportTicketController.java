package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.AuthorizationGuard;
import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.SupportTicketCreateRequest;
import com.vanrides.vanrides.dto.SupportTicketResponse;
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
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;

@RestController
@RequestMapping("/support/tickets")
public class SupportTicketController {

    @Autowired
    private SupportTicketService supportTicketService;

    @PostMapping
    public ResponseEntity<SupportTicketResponse> createTicket(@RequestBody(required = false) SupportTicketCreateRequest body,
                                                              HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER, UserRole.GUARDIAN);
        return ResponseEntity.status(HttpStatus.CREATED).body(
                supportTicketService.createTicket(
                        role,
                        AuthorizationGuard.getRequiredUserId(request),
                        getRequiredCompanyId(request),
                        body
                )
        );
    }

    @GetMapping
    public ResponseEntity<List<SupportTicketSummaryResponse>> listOwnTickets(HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER, UserRole.GUARDIAN);
        return ResponseEntity.ok(
                supportTicketService.listOwnTickets(
                        role,
                        AuthorizationGuard.getRequiredUserId(request),
                        getRequiredCompanyId(request)
                )
        );
    }

    @GetMapping("/{id}")
    public ResponseEntity<SupportTicketResponse> getOwnTicket(@PathVariable Long id, HttpServletRequest request) {
        UserRole role = AuthorizationGuard.getRequiredRole(request);
        AuthorizationGuard.requireAnyRole(request, UserRole.PASSENGER, UserRole.GUARDIAN);
        return ResponseEntity.ok(
                supportTicketService.getOwnTicket(
                        id,
                        role,
                        AuthorizationGuard.getRequiredUserId(request),
                        getRequiredCompanyId(request)
                )
        );
    }

    private Long getRequiredCompanyId(HttpServletRequest request) {
        Object attribute = request.getAttribute("userCompanyId");
        if (attribute instanceof Number number) {
            return number.longValue();
        }
        throw new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Contexto de empresa ausente");
    }
}
