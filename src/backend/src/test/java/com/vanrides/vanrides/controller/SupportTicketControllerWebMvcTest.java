package com.vanrides.vanrides.controller;

import com.vanrides.vanrides.auth.UserRole;
import com.vanrides.vanrides.dto.AuthenticatedUser;
import com.vanrides.vanrides.dto.SupportTicketMessageResponse;
import com.vanrides.vanrides.dto.SupportTicketResponse;
import com.vanrides.vanrides.dto.SupportTicketSummaryResponse;
import com.vanrides.vanrides.service.AuthorizationService;
import com.vanrides.vanrides.service.SupportTicketService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.Base64;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest({SupportTicketController.class, CompanyAdminSupportTicketController.class})
class SupportTicketControllerWebMvcTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private SupportTicketService supportTicketService;

    @MockitoBean
    private AuthorizationService authorizationService;

    @Test
    void shouldCreatePassengerTicket() throws Exception {
        when(authorizationService.authenticate(basicHeader("passageiro@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(21L, "PASSENGER", "passageiro@vanrides.dev", "Passageiro", null, 66L));
        when(supportTicketService.createTicket(eq(UserRole.PASSENGER), eq(21L), eq(66L), any()))
                .thenReturn(ticketResponse("OPEN"));

        mockMvc.perform(post("/support/tickets")
                        .servletPath("/support/tickets")
                        .header("Authorization", basicHeader("passageiro@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "category":"ATRASO",
                                  "subject":"Van atrasada",
                                  "message":"A van ainda nao passou."
                                }
                                """))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(100))
                .andExpect(jsonPath("$.status").value("OPEN"))
                .andExpect(jsonPath("$.messages[0].authorRole").value("PASSENGER"));
    }

    @Test
    void shouldListOwnTickets() throws Exception {
        when(authorizationService.authenticate(basicHeader("responsavel@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(31L, "GUARDIAN", "responsavel@vanrides.dev", "Responsavel", null, 66L));
        when(supportTicketService.listOwnTickets(UserRole.GUARDIAN, 31L, 66L))
                .thenReturn(List.of(ticketSummary("OPEN")));

        mockMvc.perform(get("/support/tickets")
                        .servletPath("/support/tickets")
                        .header("Authorization", basicHeader("responsavel@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].subject").value("Van atrasada"));
    }

    @Test
    void shouldForbidDriverFromCreatingTicket() throws Exception {
        when(authorizationService.authenticate(basicHeader("driver@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(7L, "DRIVER", "driver@vanrides.dev", "Motorista", null, 66L));

        mockMvc.perform(post("/support/tickets")
                        .servletPath("/support/tickets")
                        .header("Authorization", basicHeader("driver@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"category":"ROTA","subject":"Duvida","message":"Mensagem"}
                                """))
                .andExpect(status().isForbidden());

        verify(supportTicketService, never()).createTicket(any(), any(), any(), any());
    }

    @Test
    void shouldListCompanyInbox() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin@vanrides.dev", "Admin", null, 66L));
        when(supportTicketService.listCompanyTickets(66L, "OPEN"))
                .thenReturn(List.of(ticketSummary("OPEN")));

        mockMvc.perform(get("/company-admins/support/tickets")
                        .servletPath("/company-admins/support/tickets")
                        .param("status", "OPEN")
                        .header("Authorization", basicHeader("admin@vanrides.dev", "Secret123")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].status").value("OPEN"))
                .andExpect(jsonPath("$[0].createdByRole").value("PASSENGER"));
    }

    @Test
    void shouldReplyAsCompanyAdmin() throws Exception {
        when(authorizationService.authenticate(basicHeader("admin.reply@vanrides.dev", "Secret123")))
                .thenReturn(new AuthenticatedUser(90L, "COMPANY_ADMIN", "admin.reply@vanrides.dev", "Admin", null, 66L));
        when(supportTicketService.replyAsCompanyAdmin(eq(100L), eq(66L), eq(90L), any()))
                .thenReturn(ticketResponse("RESOLVED"));

        mockMvc.perform(post("/company-admins/support/tickets/100/responses")
                        .servletPath("/company-admins/support/tickets/100/responses")
                        .header("Authorization", basicHeader("admin.reply@vanrides.dev", "Secret123"))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {
                                  "message":"Resolvido com o motorista.",
                                  "status":"RESOLVED"
                                }
                                """))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("RESOLVED"));
    }

    private SupportTicketResponse ticketResponse(String status) {
        return new SupportTicketResponse(
                100L,
                status,
                "ATRASO",
                "Van atrasada",
                "PASSENGER",
                21L,
                "Passageiro",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T12:05:00Z"),
                "RESOLVED".equals(status) ? OffsetDateTime.parse("2026-06-18T12:05:00Z") : null,
                List.of(new SupportTicketMessageResponse(
                        501L,
                        "PASSENGER",
                        21L,
                        "Passageiro",
                        "A van ainda nao passou.",
                        OffsetDateTime.parse("2026-06-18T12:00:00Z")
                ))
        );
    }

    private SupportTicketSummaryResponse ticketSummary(String status) {
        return new SupportTicketSummaryResponse(
                100L,
                status,
                "ATRASO",
                "Van atrasada",
                "PASSENGER",
                21L,
                "Passageiro",
                OffsetDateTime.parse("2026-06-18T12:00:00Z"),
                OffsetDateTime.parse("2026-06-18T12:05:00Z"),
                null
        );
    }

    private String basicHeader(String email, String senha) {
        String raw = email + ":" + senha;
        return "Basic " + Base64.getEncoder().encodeToString(raw.getBytes(StandardCharsets.UTF_8));
    }
}
